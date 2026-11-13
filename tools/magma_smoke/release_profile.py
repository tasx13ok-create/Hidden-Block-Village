"""Explicit immutable 0.3.12 upgrade profile; old 0.3.11 guards stay pinned."""
import copy
import json
from pathlib import Path

from tools.server_intake.intake import pack, save
from . import smoke

RELEASE = '0.3.12'
PORT = 25583
CORE_SHA256 = '7c8c91e0677a2e54cccc52459479bf57587fbd790347558c0786ee3e5d0fa8c8'
SOURCE_SHA256 = '58958edc9eb2a776a250ef8e0a73a97e26e8c479e7cd45433ad24a952c71ccc1'
BUILD_SHA256 = '7bf23439d3d63fb43a05e8fe352ec9c2ab7ebbdfee0101456f4b7723b0c2f35b'
NATIVE_SHA256 = 'c95e7c2165fa1892cdd46ade6d3a490bebba5963488a32f83567be5c18cd3c26'


def world_port(candidate, upgrade):
    release = upgrade.get('profile_release', '0.3.11')
    if release == '0.3.11':
        smoke.require(upgrade['core_sha256'] == smoke.CORE_SHA256, '0.3.11 Core pin changed')
        return 25581
    smoke.require(release == RELEASE, 'Unsupported explicit release profile')
    smoke.require(upgrade['core_sha256'] == CORE_SHA256 and upgrade['source_snapshot_sha256'] == SOURCE_SHA256, '0.3.12 source/Core pin changed')
    smoke.require(upgrade['source_build_report']['sha256'] == BUILD_SHA256 and upgrade['native_report']['sha256'] == NATIVE_SHA256, '0.3.12 receipt pin changed')
    smoke.require(pack.hash_file(candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json') == upgrade['source_build_report'], 'Frozen build receipt changed')
    smoke.require(pack.hash_file(candidate / 'PRIVATE-NATIVE-REPORT.json') == upgrade['native_report'], 'Frozen native receipt changed')
    return PORT


def selected_artifacts(frozen_inputs, baseline_upgrade):
    frozen_inputs = pack.no_links(frozen_inputs)
    build, bid = pack.read_json(frozen_inputs / 'build/source-build-report.json')
    qa, qid = pack.read_json(frozen_inputs / 'build/native-check-report.json')
    smoke.require(bid['sha256'] == BUILD_SHA256 and qid['sha256'] == NATIVE_SHA256, 'Different frozen 0.3.12 receipts')
    smoke.require(build.get('passed') is True and all(row.get('status') == 'PASS' for row in build['results'].values()), 'Passing source build required')
    smoke.require(build['source_snapshot']['sha256'] == SOURCE_SHA256, 'Wrong source snapshot')
    smoke.require(qa.get('passed') is True and qa.get('status') == 'PASS' and qa.get('source_build_report') == bid and qa.get('assertions') == 2058, 'Exact 2058-check native binding required')
    artifacts = {}
    for module, result in build['results'].items():
        for row in result['artifacts']:
            if row['role'] == 'runtime_mod':
                component = row.get('component', module)
                smoke.require(component not in artifacts, 'Duplicate runtime component')
                artifacts[component] = row
    selected = []
    smoke.require(len(baseline_upgrade['added']) == 9, 'Nine baseline components required')
    for prior in baseline_upgrade['added']:
        component, modid = prior['component'], prior['mod_id']
        smoke.require(component in artifacts, 'Missing tested replacement component')
        row = artifacts[component]
        path = pack.no_links(frozen_inputs / pack.safe_relative(row['copied_path']))
        smoke.require(frozen_inputs / 'build/source-builds' in path.parents, 'Artifact outside frozen copies')
        identity = pack.hash_file(path)
        smoke.require(identity == {key: row[key] for key in ('bytes', 'sha256')} and qa['expected_loaded_mods'][modid]['sha256'] == row['sha256'], 'Artifact/native identity differs')
        if component == 'vale-orders':
            smoke.require(row['sha256'] == CORE_SHA256, 'Wrong 0.3.12 Core')
        else:
            smoke.require(row['sha256'] == prior['sha256'], 'Non-Core component changed; registry-reference review must be repeated')
        selected.append(dict(component=component, mod_id=modid, source=path, **identity))
    return selected, build, bid, qa, qid


def prepare(runtime, source_world, frozen_receipt, receipt_sha256, candidate, frozen_inputs, memory='2G'):
    from . import world_profile as world
    runtime, candidate = map(pack.no_links, (runtime, candidate))
    baseline = json.loads((runtime / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
    smoke.require(baseline.get('profile_release', '0.3.11') == '0.3.11', 'Upgrade source must be the frozen 0.3.11 runtime')
    selected, build, bid, qa, qid = selected_artifacts(frozen_inputs, baseline)
    result = world.prepare(runtime, source_world, frozen_receipt, receipt_sha256, candidate, memory)
    try:
        for relative in ('PRIVATE-MAGMA-ENHANCEMENT.json', 'PRIVATE-SOURCE-BUILD-REPORT.json', 'PRIVATE-NATIVE-REPORT.json'):
            (candidate / relative).rename(candidate / relative.replace('PRIVATE-', 'PRIVATE-BASELINE-0311-', 1))
        removed = []
        for prior in baseline['added']:
            retained = 'retained-vale-0311/' + prior['path']
            target = candidate / retained
            target.parent.mkdir(parents=True, exist_ok=True)
            (candidate / prior['path']).rename(target)
            removed.append(dict(prior, retained_path=retained))
        upgrade = copy.deepcopy(baseline)
        upgrade.update(profile_release=RELEASE, source_snapshot_sha256=SOURCE_SHA256, core_sha256=CORE_SHA256,
                       source_build_report=bid, native_report=qid, native_assertions=qa['assertions'], port=PORT, added=[],
                       baseline_0311_components_retained=removed, registry_reference_scope='Unchanged legacy owners and eight identical non-Core components; no source valeorders registry names.')
        upgrade['registry_reference_level_sha256'] = pack.hash_file(runtime / 'magma-smoke-flat/level.dat')['sha256']
        upgrade['registry_reference_core_sha256'] = baseline['core_sha256']
        for row in selected:
            path = 'mods/' + row['source'].name
            identity = smoke.copy_verified(row['source'], candidate / path, row['sha256'])
            upgrade['added'].append(dict(path=path, component=row['component'], mod_id=row['mod_id'], **identity))
        smoke.copy_verified(Path(frozen_inputs) / 'build/source-build-report.json', candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json', bid['sha256'])
        smoke.copy_verified(Path(frozen_inputs) / 'build/native-check-report.json', candidate / 'PRIVATE-NATIVE-REPORT.json', qid['sha256'])
        save(candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json', upgrade)
        values = world.profile(memory, port=PORT)
        (candidate / 'server.properties').write_text(''.join(key + '=' + value + '\n' for key, value in values.items()), encoding='ascii')
        result.update(profile_release=RELEASE, port=PORT, source_snapshot_sha256=SOURCE_SHA256, core_sha256=CORE_SHA256)
        save(candidate / world.PREP, result)
        world_port(candidate, upgrade)
        return result
    except BaseException as error:
        result.update(status='FAILED_PRESERVED', error_type=type(error).__name__, error=str(error))
        save(candidate / world.PREP, result)
        raise


def reuse_script_review(candidate, donor):
    from . import world_profile as world
    candidate, donor = map(pack.no_links, (candidate, donor))
    target_prep = json.loads((candidate / world.PREP).read_text(encoding='utf-8'))
    donor_prep = json.loads((donor / world.PREP).read_text(encoding='utf-8'))
    smoke.require(target_prep['files'] == donor_prep['files'] and target_prep['source_manifest_sha256'] == donor_prep['source_manifest_sha256'], 'Cannot reuse a review for different world bytes')
    review, identity = pack.read_json(donor / world.REVIEW)
    smoke.require(review.get('status') == 'NO_EXECUTABLE_SCRIPT_BODY_FOUND' and not review['script_bodies'] and not review['parse_errors'], 'Only complete script-absence review may be reused')
    smoke.require(review['region_manifest_sha256'] == pack.digest([row for row in target_prep['files'] if row['path'].endswith('.mca')]), 'Different region identities')
    world.verify_rows(candidate / world.LEVEL, target_prep['files'])
    review.update(world_path=str(candidate / world.LEVEL), reused_from_review_sha256=identity['sha256'],
                  review_mode='Verified reuse: byte-identical frozen world/region sources; NPC export sources rechecked at sealing.')
    save(candidate / world.REVIEW, review)
    return review
