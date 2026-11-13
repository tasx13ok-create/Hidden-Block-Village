"""Explicit immutable release profiles; old 0.3.11/0.3.12 pins stay fixed."""
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


def release_spec(release):
    if release == RELEASE:
        return dict(release=RELEASE, port=PORT, core=CORE_SHA256, source=SOURCE_SHA256, build=BUILD_SHA256,
                    native=NATIVE_SHA256, assertions=2058, changed={'vale-orders'}, absent={'valeorders'})
    smoke.require(release == '0.3.13', 'Unsupported explicit release profile')
    return dict(release='0.3.13', port=25585,
                core='c2db8c4d472aec9f38a3c1d7b44b4ac31c93dfd0bbfb6373908de1af8c049352',
                source='aacf8985dc408704cba269daa5a4ef488635030a5e25d938462fbed7f5c0d096',
                build='33df3e878588866bec95b8e8970655d3ed681457b11975aab77e58e885ac9732',
                native='79704422c5e655df53686822102c7461be3866573e94aee4f62a7fbece560818',
                assertions=2305, changed={'vale-orders', 'vale-residents'}, absent={'valeorders', 'valeresidents'})


def legacy_registry_scope(release, source_names, prior_rows, current_rows):
    spec = release_spec(release)
    smoke.require(not any(name.split(':', 1)[0] in spec['absent'] for name in source_names), 'Saved changed-owner namespace requires an exact new-runtime registry inventory')
    prior = {row['component']: row['sha256'] for row in prior_rows}
    current = {row['component']: row['sha256'] for row in current_rows}
    smoke.require(len(prior_rows) == len(prior) == len(current_rows) == len(current) == 9 and set(prior) == set(current), 'Registry-reference component roster changed')
    smoke.require(all(digest == prior[component] for component, digest in current.items() if component not in spec['changed']), 'Changed registry-reference owners')


def world_port(candidate, upgrade):
    release = upgrade.get('profile_release', '0.3.11')
    if release == '0.3.15':
        from tools.everything_runtime_0315 import validate_release_binding, WORLD_PORT
        validate_release_binding(candidate, upgrade)
        smoke.require(upgrade['port'] == WORLD_PORT, 'Everything0315 copied-world port changed')
        return WORLD_PORT
    if release == '0.3.14':
        from tools.everything_runtime import validate_release_binding, WORLD_PORT
        validate_release_binding(candidate, upgrade)
        smoke.require(upgrade['port'] == WORLD_PORT, 'Everything copied-world port changed')
        return WORLD_PORT
    if release == '0.3.11':
        smoke.require(upgrade['core_sha256'] == smoke.CORE_SHA256, '0.3.11 Core pin changed')
        return 25581
    spec = release_spec(release)
    smoke.require(upgrade['core_sha256'] == spec['core'] and upgrade['source_snapshot_sha256'] == spec['source'], 'Explicit release source/Core pin changed')
    smoke.require(upgrade['source_build_report']['sha256'] == spec['build'] and upgrade['native_report']['sha256'] == spec['native'], 'Explicit release receipt pin changed')
    smoke.require(pack.hash_file(candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json') == upgrade['source_build_report'], 'Frozen build receipt changed')
    smoke.require(pack.hash_file(candidate / 'PRIVATE-NATIVE-REPORT.json') == upgrade['native_report'], 'Frozen native receipt changed')
    native = json.loads((candidate / 'PRIVATE-NATIVE-REPORT.json').read_text(encoding='utf-8'))
    smoke.require(native.get('passed') is True and native.get('assertions') == spec['assertions'], 'Explicit native assertion binding changed')
    smoke.require(len(upgrade['added']) == 9 and {row['mod_id'] for row in upgrade['added']} == set(native['production_mod_ids']), 'Production roster differs from native proof')
    smoke.require(all(row['sha256'] == native['expected_loaded_mods'][row['mod_id']]['sha256'] for row in upgrade['added']), 'Added artifacts differ from pinned native proof')
    return spec['port']


def selected_artifacts(frozen_inputs, baseline_upgrade, profile_release=RELEASE):
    spec = release_spec(profile_release)
    frozen_inputs = pack.no_links(frozen_inputs)
    build, bid = pack.read_json(frozen_inputs / 'build/source-build-report.json')
    qa, qid = pack.read_json(frozen_inputs / 'build/native-check-report.json')
    smoke.require(bid['sha256'] == spec['build'] and qid['sha256'] == spec['native'], 'Different frozen release receipts')
    smoke.require(build.get('passed') is True and all(row.get('status') == 'PASS' for row in build['results'].values()), 'Passing source build required')
    smoke.require(build['source_snapshot']['sha256'] == spec['source'], 'Wrong source snapshot')
    smoke.require(qa.get('passed') is True and qa.get('status') == 'PASS' and qa.get('source_build_report') == bid and qa.get('assertions') == spec['assertions'], 'Exact native assertion/source binding required')
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
            smoke.require(row['sha256'] == spec['core'], 'Wrong explicit release Core')
        elif component not in spec['changed']:
            smoke.require(row['sha256'] == prior['sha256'], 'Non-Core component changed; registry-reference review must be repeated')
        selected.append(dict(component=component, mod_id=modid, source=path, **identity))
    return selected, build, bid, qa, qid


def prepare(runtime, source_world, frozen_receipt, receipt_sha256, candidate, frozen_inputs, memory='2G', profile_release=RELEASE):
    from . import world_profile as world
    runtime, candidate = map(pack.no_links, (runtime, candidate))
    baseline = json.loads((runtime / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
    smoke.require(baseline.get('profile_release', '0.3.11') == '0.3.11', 'Upgrade source must be the frozen 0.3.11 runtime')
    spec = release_spec(profile_release)
    selected, build, bid, qa, qid = selected_artifacts(frozen_inputs, baseline, profile_release)
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
        upgrade.update(profile_release=profile_release, source_snapshot_sha256=spec['source'], core_sha256=spec['core'],
                       source_build_report=bid, native_report=qid, native_assertions=qa['assertions'], port=spec['port'], added=[],
                       baseline_0311_components_retained=removed,
                       registry_reference_scope='Unchanged legacy owners; reviewed changed namespaces absent from source saved registry names: ' + ','.join(sorted(spec['absent'])))
        upgrade['registry_reference_level_sha256'] = pack.hash_file(runtime / 'magma-smoke-flat/level.dat')['sha256']
        upgrade['registry_reference_core_sha256'] = baseline['core_sha256']
        for row in selected:
            path = 'mods/' + row['source'].name
            identity = smoke.copy_verified(row['source'], candidate / path, row['sha256'])
            upgrade['added'].append(dict(path=path, component=row['component'], mod_id=row['mod_id'], **identity))
        smoke.copy_verified(Path(frozen_inputs) / 'build/source-build-report.json', candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json', bid['sha256'])
        smoke.copy_verified(Path(frozen_inputs) / 'build/native-check-report.json', candidate / 'PRIVATE-NATIVE-REPORT.json', qid['sha256'])
        save(candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json', upgrade)
        values = world.profile(memory, port=spec['port'])
        (candidate / 'server.properties').write_text(''.join(key + '=' + value + '\n' for key, value in values.items()), encoding='ascii')
        result.update(profile_release=profile_release, port=spec['port'], source_snapshot_sha256=spec['source'], core_sha256=spec['core'])
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
