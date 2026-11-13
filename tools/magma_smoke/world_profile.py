"""Explicit copied-world acceptance profile. Never edits an input world or runtime.

Only a pinned FROZEN receipt, complete verified copy, reviewed script inventory
and zero missing saved registry names can seal a candidate for native execution.
"""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import time

from tools.server_intake.intake import pack, save
from tools.world_merge import anvil, nbt
from tools.npc_intake.formats import read_snbt
from . import smoke

PENDING = 'COPIED_WORLD_REVIEW_PENDING'
SEALED = 'COPIED_WORLD_REVIEWED_ONLY'
LEVEL, PORT = 'Naruto.test', 25581
PREP = 'PRIVATE-COPIED-WORLD-PREP.json'
READY = 'PRIVATE-COPIED-WORLD-READY.json'
LAST = 'PRIVATE-COPIED-WORLD-LAST-BOOT.json'
REVIEW = 'PRIVATE-WORLD-SCRIPT-REVIEW.json'
ROOT_CONFIGS = ('addon_jutsu.json', 'AHZNB`S TWEAKS CONFIG.cfg', 'ahznbcursemarkaddon.cfg', 'anbubingobook.cfg',
                'appleskin.cfg', 'BijuUnleashing.cfg', 'bloop_entities.json', 'bloop_jutsu.json', 'cosmeticarmorreworked.cfg',
                'CustomNpcs.cfg', 'damageindicatorsmod.cfg', 'forge.cfg', 'forgeChunkLoading.cfg', 'ftblib.cfg', 'hvbijuu.cfg',
                'kabutoaddon.cfg', 'marketblocks.cfg', 'mixinbooter.cfg', 'narutomod.cfg', 'nmee.cfg', 'patchouli.cfg',
                'stg.cfg', 'travelersbackpack.cfg', 'VariedCommodities.cfg', 'Waystones.cfg')
DEPENDENCY_PREFIXES = ('customnpcs/', 'armourers_workshop/', 'plugins/CrazyCrates/', 'config/armourers_workshop/',
                       'config/fluidlogged_api/', 'config/ftbquests/', 'config/Warps/', 'config/worldedit/', 'config/xaero/')
SECRET = re.compile(r'''(?im)^\s*[A-Za-z0-9_.: -]*(?:password|token|secret|api.?key)[A-Za-z0-9_.: -]*\s*[=:]\s*["']?([^\r\n]*)''')


def require(ok, message):
    smoke.require(ok, message)


def profile(memory='2G', port=PORT):
    require(memory in ('2G', '4G'), 'Copied-world memory must be 2G or 4G')
    require(port in (PORT, 25583, 25585, 25586, 25589), 'Unsupported version-pinned copied-world port')
    return dict(smoke.PROPERTIES, **{'level-name': LEVEL, 'level-type': 'DEFAULT', 'generator-settings': '', 'server-port': str(port)})


def tree_rows(root):
    root = pack.no_links(root)
    result = []
    for path in sorted(root.rglob('*')):
        pack.no_links(path)
        if path.is_file():
            result.append(dict(path=path.relative_to(root).as_posix(), **pack.hash_file(path)))
    pack.check_names(dict(path=row['path'], kind='file') for row in result)
    return result


def frozen_receipt(path, expected_sha256):
    value, identity = pack.read_json(path)
    require(identity['sha256'] == expected_sha256, 'FROZEN receipt pin differs')
    require(value.get('status') == 'FROZEN_FOR_READ_ONLY_COPY', 'World is not explicitly FROZEN')
    require(len(value['files']) == value['file_count'] and sum(row['bytes'] for row in value['files']) == value['bytes'], 'FROZEN counts differ')
    digest = hashlib.sha256(json.dumps(value['files'], sort_keys=True).encode('utf-8')).hexdigest()
    require(digest == value['manifest_sha256'], 'FROZEN tree digest differs')
    pack.check_names(dict(path=row['path'], kind='file') for row in value['files'])
    return value, identity


def verify_rows(root, rows):
    root = pack.no_links(root)
    expected = {row['path']: row for row in rows}
    actual = {path.relative_to(root).as_posix() for path in root.rglob('*') if path.is_file()}
    require(actual == set(expected), 'World file set differs from its sealed snapshot')
    for relative, row in expected.items():
        pack.safe_relative(relative)
        require(pack.hash_file(root / relative) == {key: row[key] for key in ('bytes', 'sha256')}, 'World file differs: ' + relative)


def prepare(runtime, source_world, frozen_path, receipt_sha256, candidate, memory='2G'):
    runtime, candidate, source_world = map(pack.no_links, (runtime, candidate, source_world))
    frozen, identity = frozen_receipt(frozen_path, receipt_sha256)
    require(pack.no_links(frozen['world_path']) == source_world, 'FROZEN receipt belongs to another world')
    smoke.validate_profile(runtime)
    require(not candidate.exists(), 'Copied-world candidate must be new')
    for source in (runtime, source_world):
        require(source not in candidate.parents and candidate not in source.parents, 'Candidate overlaps an input')
    source_stage = json.loads((runtime / 'PRIVATE-MAGMA-STAGE.json').read_text(encoding='utf-8'))
    source_upgrade = json.loads((runtime / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
    require(source_upgrade['core_sha256'] == smoke.CORE_SHA256 and len(source_upgrade['added']) == 9, 'Wrong enhanced runtime checkpoint')
    candidate.mkdir(parents=True)
    (candidate / smoke.MARKER).write_text(PENDING + '\n', encoding='ascii')
    result = {'schema': 1, 'status': 'COPY_IN_PROGRESS', 'source_world': str(source_world), 'source_runtime': str(runtime),
              'source_frozen_receipt': str(frozen_path), 'source_frozen_receipt_sha256': identity['sha256'],
              'source_manifest_sha256': frozen['manifest_sha256'], 'source_file_count': frozen['file_count'],
              'source_bytes': frozen['bytes'], 'files': [], 'memory_limit': memory, 'port': PORT, 'world_started': False}
    try:
        removed = {row['path']: row for row in source_upgrade['removed_from_active']}
        for row in source_stage['staged']:
            relative = removed[row['path']]['retained_path'] if row['path'] in removed else row['path']
            smoke.copy_verified(runtime / relative, candidate / relative, row['sha256'])
        for row in source_stage['dependencies']:
            source = runtime / row['target']
            if not source.exists() and row['target'] == 'minecraft_server.1.12.2.jar':
                source = runtime / 'libraries/minecraft_server.1.12.2.jar'
            smoke.copy_verified(source, candidate / row['target'], row['sha256'])
        for row in source_upgrade['added']:
            smoke.copy_verified(runtime / row['path'], candidate / row['path'], row['sha256'])
        for relative in ('PRIVATE-MAGMA-STAGE.json', 'PRIVATE-MAGMA-ENHANCEMENT.json', 'PRIVATE-SOURCE-BUILD-REPORT.json', 'PRIVATE-NATIVE-REPORT.json', 'config/valeorders.cfg', 'eula.txt'):
            smoke.copy_verified(runtime / relative, candidate / relative)
        values = profile(memory)
        (candidate / 'server.properties').write_text(''.join(key + '=' + value + '\n' for key, value in values.items()), encoding='ascii')
        output = candidate / LEVEL
        output.mkdir()
        last = 0
        for index, row in enumerate(frozen['files']):
            relative = pack.safe_relative(row['path'])
            actual = smoke.copy_verified(source_world / relative, output / relative, row['sha256'])
            require(actual['bytes'] == row['bytes'], 'Frozen source size differs')
            result['files'].append(row)
            now = time.monotonic()
            if now - last > 5:
                print(json.dumps({'phase': 'copy_frozen_world', 'verified_files': index + 1, 'total': len(frozen['files'])}), flush=True)
                last = now
        smoke.copy_verified(frozen_path, candidate / 'PRIVATE-WORLD-FROZEN-RECEIPT.json', identity['sha256'])
        result['status'] = 'COPIED_VERIFIED_PENDING_STATIC_REVIEW'
        save(candidate / PREP, result)
        return result
    except BaseException as error:
        result.update(status='FAILED_PRESERVED', error_type=type(error).__name__, error=str(error))
        save(candidate / PREP, result)
        raise


def saved_registries(level_dat):
    root = nbt.load(level_dat)[1]
    registry = root['FML']['Registries']
    result = {}
    for name, value in registry.value.items():
        require(value.type == nbt.COMPOUND and value.get('ids') is not None, 'Unsupported saved registry schema')
        records = value['ids']
        require(records.type == nbt.LIST, 'Unsupported saved registry ID list')
        names = [row.scalar('K') for row in records.value]
        require(all(isinstance(item, str) for item in names) and len(names) == len(set(names)), 'Invalid/duplicate saved registry names')
        result[name] = set(names)
    return result


def registry_review(source_level, runtime_level):
    source, runtime = saved_registries(source_level), saved_registries(runtime_level)
    rows = [{'registry': name, 'source_ids': len(ids), 'runtime_ids': len(runtime.get(name, set())), 'missing': sorted(ids - runtime.get(name, set()))}
            for name, ids in sorted(source.items())]
    return {'schema': 1, 'source_level_dat_sha256': pack.hash_file(source_level)['sha256'],
            'runtime_level_dat_sha256': pack.hash_file(runtime_level)['sha256'], 'registries': rows,
            'missing_saved_names': sum(len(row['missing']) for row in rows), 'mapping_discard_authorized': False}


def credential_flags(raw):
    result = []
    text = raw.decode('utf-8', errors='replace')
    for match in SECRET.finditer(text):
        value = match.group(1).strip().strip('"\'')
        if value.lower() not in ('', 'false', '0', 'none', 'null', 'changeme', 'disabled'):
            result.append({'chars': len(value), 'line': text.count('\n', 0, match.start()) + 1})
    return result


def review_scripts(candidate, recovery, *, region_paths=None):
    """Read every region's entity/tile NBT and both original NPC libraries."""
    candidate, recovery = map(pack.no_links, (candidate, recovery))
    require((candidate / smoke.MARKER).read_text(encoding='ascii').strip() == PENDING, 'Review only an unstarted pending world copy')
    prep = json.loads((candidate / PREP).read_text(encoding='utf-8'))
    world = candidate / LEVEL
    report = {'schema': 1, 'status': 'IN_PROGRESS', 'world_path': str(world),
              'world_source_manifest_sha256': prep['source_manifest_sha256'],
              'region_manifest_sha256': pack.digest([row for row in prep['files'] if row['path'].endswith('.mca')]),
              'global_source_archive_sha256': json.loads((recovery / 'PRIVATE-SERVER-INTAKE.json').read_text(encoding='utf-8'))['archive_sha256'],
              'region_files': 0, 'chunks': 0, 'entities': 0, 'tile_entities': 0, 'script_sources': [],
              'script_bodies': {}, 'parse_errors': [], 'scripts_executed': False}
    counts = Counter()
    def note(text, origin, key):
        if not text.strip():
            return
        digest = hashlib.sha256(text.encode('utf-8', errors='surrogatepass')).hexdigest()
        report['script_bodies'].setdefault(digest, {'sha256': digest, 'chars': len(text), 'text': text, 'origins': []})['origins'].append({'origin': origin, 'key': key})
    def compound(items, origin, context=False):
        for key, value in items:
            lower = key.lower()
            inner = context or lower in ('scripts', 'scriptlist', 'scriptdata')
            if 'script' in lower:
                counts[key] += 1
            is_string, body, kind = value
            if is_string and ((lower in ('script', 'scriptscript', 'scriptcontent', 'scriptcode', 'code') and context)
                              or lower.startswith('script') and lower not in ('scriptlanguage', 'scriptname', 'scriptengine')):
                note(body, origin, key)
            kind(body, origin, inner)
    def binary(node, origin, context=False):
        if isinstance(node, nbt.Tag) and node.type == nbt.COMPOUND:
            compound(((key, (value.type == nbt.STRING, value.value if value.type == nbt.STRING else value, binary)) for key, value in node.value.items()), origin, context)
        elif isinstance(node, nbt.Tag) and node.type == nbt.LIST:
            for value in node.value:
                if value.type == nbt.STRING and context:
                    note(value.value, origin, 'script-list-string')
                else:
                    binary(value, origin, context)
    def snbt(node, origin, context=False):
        if isinstance(node, dict) and node.get('type') == 'compound':
            compound(((key, (value['type'] == 'string', value['value'] if value['type'] == 'string' else value, snbt)) for key, value in node['value'].items()), origin, context)
        elif isinstance(node, dict) and node.get('type') == 'list':
            for value in node['value']['items']:
                if value['type'] == 'string' and context:
                    note(value['value'], origin, 'script-list-string')
                else:
                    snbt(value, origin, context)
    last = 0
    if region_paths is None:
        selected_regions = sorted(world.rglob('*.mca'))
    else:
        allowed = {row['path']: row for row in prep['files'] if row['path'].endswith('.mca')}
        require(len(region_paths) == len(set(region_paths)) and set(region_paths) <= set(allowed), 'Unreviewed or duplicate partial-region selector')
        selected_regions = []
        for relative in sorted(region_paths):
            path = pack.no_links(world / pack.safe_relative(relative))
            require(pack.hash_file(path) == {key: allowed[relative][key] for key in ('bytes', 'sha256')}, 'Partial-region bytes differ from frozen copy')
            selected_regions.append(path)
        report['partial_region_review_only'] = True
    for region_path in selected_regions:
        try:
            region = anvil.Region(region_path)
            for slot in region.chunks:
                root = region.load_chunk(slot)[1]
                origin = region_path.relative_to(world).as_posix() + ':' + str(slot)
                for category, counter in (('Entities', 'entities'), ('TileEntities', 'tile_entities')):
                    values = root['Level'].get(category)
                    if values:
                        report[counter] += len(values.value)
                        for value in values.value:
                            binary(value, origin)
                report['chunks'] += 1
            report['region_files'] += 1
        except Exception as error:
            report['parse_errors'].append({'path': region_path.relative_to(world).as_posix(), 'error': str(error)})
        now = time.monotonic()
        if now - last > 10:
            print(json.dumps({'phase': 'static_script_review', 'regions': report['region_files'], 'chunks': report['chunks'], 'script_bodies': len(report['script_bodies'])}), flush=True)
            last = now
    for base, prefix in ((world / 'customnpcs', 'world/customnpcs'), (recovery / 'server/customnpcs', 'server/customnpcs')):
        if not base.exists():
            continue
        for path in base.rglob('*'):
            if not path.is_file() or 'playerdata' in path.parts:
                continue
            origin = prefix + '/' + path.relative_to(base).as_posix()
            raw = path.read_bytes()
            if path.suffix in ('.js', '.py', '.lua'):
                note(raw.decode('utf-8'), origin, 'standalone-script')
            elif path.suffix == '.json':
                try:
                    snbt(read_snbt(raw), origin)
                except Exception as error:
                    report['parse_errors'].append({'path': origin, 'error': str(error)})
            report['script_sources'].append(dict(path=origin, **pack.hash_file(path)))
    report['script_metadata_counts'] = dict(counts)
    report['status'] = 'REVIEW_REQUIRED' if report['script_bodies'] or report['parse_errors'] else 'NO_EXECUTABLE_SCRIPT_BODY_FOUND'
    save(candidate / REVIEW, report)
    return report


def review_changed_regions(candidate, recovery, donor):
    """Reuse a complete script-absence proof only for identical region payloads."""
    candidate, recovery, donor = map(pack.no_links, (candidate, recovery, donor))
    prep = json.loads((candidate / PREP).read_text(encoding='utf-8'))
    old_prep = json.loads((donor / PREP).read_text(encoding='utf-8'))
    old_review, old_id = pack.read_json(donor / REVIEW)
    old_frozen, _ = frozen_receipt(donor / 'PRIVATE-WORLD-FROZEN-RECEIPT.json', old_prep['source_frozen_receipt_sha256'])
    old_ready = json.loads((donor / READY).read_text(encoding='utf-8'))
    require(old_frozen['files'] == old_prep['files'] and old_ready['script_review'] == old_id, 'Donor frozen/review seal identity differs')
    require(old_review['world_source_manifest_sha256'] == old_prep['source_manifest_sha256'], 'Donor proof belongs to another frozen world')
    require(old_review.get('status') == 'NO_EXECUTABLE_SCRIPT_BODY_FOUND' and not old_review['script_bodies'] and not old_review['parse_errors'], 'A complete donor no-script proof is required')
    old = {row['path']: row for row in old_prep['files'] if row['path'].endswith('.mca')}
    new = {row['path']: row for row in prep['files'] if row['path'].endswith('.mca')}
    require(old_review['region_files'] == len(old) and old_review['region_manifest_sha256'] == pack.digest(list(old.values())), 'Donor region proof coverage differs')
    require(set(old) <= set(new), 'Source regions were removed; require an explicit preservation review')
    verify_rows(candidate / LEVEL, prep['files'])
    changed = [name for name, row in new.items() if row != old.get(name)]
    old_counts = Counter(chunks=0, entities=0, tile_entities=0)
    old_world = pack.no_links(old_prep['source_world'])
    for name in changed:
        if name not in old:
            continue
        path = old_world / pack.safe_relative(name)
        require(pack.hash_file(path) == {key: old[name][key] for key in ('bytes', 'sha256')}, 'Original changed-region counter reference differs')
        region = anvil.Region(path)
        for slot in region.chunks:
            level = region.load_chunk(slot)[1]['Level']
            old_counts['chunks'] += 1
            for key, counter in (('Entities', 'entities'), ('TileEntities', 'tile_entities')):
                if key in level:
                    old_counts[counter] += len(level[key].value)
    result = review_scripts(candidate, recovery, region_paths=changed)
    parsed_counts = {key: result[key] for key in ('chunks', 'entities', 'tile_entities')}
    result.update(region_files=len(new), partial_region_review_only=False,
                  unchanged_region_payloads_reused=len(new) - len(changed), changed_regions_reparsed=changed,
                  donor_complete_review_sha256=old_id['sha256'],
                  review_mode='Complete hash-verified delta: unchanged region payloads reuse prior full no-script proof; changed regions and all global/world NPC exports parsed again.',
                  script_metadata_counts_scope='Changed regions and all current global/world NPC exports only; unchanged-region metadata stays in the donor proof.',
                  parsed_this_pass=parsed_counts)
    for key in ('chunks', 'entities', 'tile_entities'):
        result[key] = old_review[key] - old_counts[key] + result[key]
    require(all(result[key] >= 0 for key in ('chunks', 'entities', 'tile_entities')), 'Invalid donor/counter coverage')
    save(candidate / REVIEW, result)
    return result


def stage_dependencies(candidate, recovery):
    candidate, recovery = map(pack.no_links, (candidate, recovery))
    source = json.loads((recovery / 'PRIVATE-SERVER-INTAKE.json').read_text(encoding='utf-8'))
    selected = [row for row in source['rows'] if row['kind'] == 'file' and row['classification'] == 'primary'
                and (row['path'].startswith(DEPENDENCY_PREFIXES) or row['path'] in {'config/' + name for name in ROOT_CONFIGS})]
    result = {'schema': 1, 'archive_sha256': source['archive_sha256'], 'files': [], 'excluded_root_plugin_collisions': 4,
              'world_customnpcs_source': 'Unchanged newer frozen world; older server-world NPC data is not overlaid.'}
    for row in selected:
        raw_path = recovery / row['destination']
        if raw_path.suffix.lower() in ('.cfg', '.json', '.yml', '.yaml', '.txt', '.properties'):
            flags = credential_flags(raw_path.read_bytes())
            require(not flags, 'Credential-like nonempty configuration field requires private review: ' + row['path'])
        output = candidate / row['path']
        require(not output.exists(), 'Dependency would overwrite an existing candidate file: ' + row['path'])
        identity = smoke.copy_verified(raw_path, output, row['sha256'])
        result['files'].append(dict(path=row['path'], **identity))
    save(candidate / 'PRIVATE-WORLD-DEPENDENCIES.json', result)
    return result


def seal(candidate, recovery, script_review, runtime_level, memory='2G'):
    candidate = pack.no_links(candidate)
    require((candidate / smoke.MARKER).read_text(encoding='ascii').strip() == PENDING, 'Only a pending copied-world candidate may be sealed')
    prep = json.loads((candidate / PREP).read_text(encoding='utf-8'))
    require(prep['status'] == 'COPIED_VERIFIED_PENDING_STATIC_REVIEW', 'World copy did not finish verification')
    frozen, identity = frozen_receipt(candidate / 'PRIVATE-WORLD-FROZEN-RECEIPT.json', prep['source_frozen_receipt_sha256'])
    require(frozen['files'] == prep['files'], 'Copy rows differ from the pinned frozen receipt')
    verify_rows(candidate / LEVEL, prep['files'])
    review, review_id = pack.read_json(script_review)
    require(review.get('status') == 'NO_EXECUTABLE_SCRIPT_BODY_FOUND' and not review.get('script_bodies') and not review.get('parse_errors'), 'Original script bodies need explicit static review; no automatic execution is allowed')
    require(review.get('world_source_manifest_sha256') == prep['source_manifest_sha256'], 'Script review belongs to another world snapshot')
    require(pack.no_links(review.get('world_path', '')) == candidate / LEVEL, 'Script review belongs to another candidate')
    regions = [row for row in prep['files'] if row['path'].endswith('.mca')]
    require(review.get('region_files') == len(regions), 'Static script review did not cover every copied region')
    require(review.get('region_manifest_sha256') == pack.digest(regions), 'Static script review region identities differ')
    source_receipt = json.loads((Path(recovery) / 'PRIVATE-SERVER-INTAKE.json').read_text(encoding='utf-8'))
    require(review.get('global_source_archive_sha256') == source_receipt['archive_sha256'], 'Global NPC script review source differs')
    required_sources = {}
    for base, prefix in ((candidate / LEVEL / 'customnpcs', 'world/customnpcs'), (Path(recovery) / 'server/customnpcs', 'server/customnpcs')):
        if not base.exists():
            continue
        for path in base.rglob('*'):
            if path.is_file() and 'playerdata' not in path.parts:
                identity = pack.hash_file(path)
                required_sources[prefix + '/' + path.relative_to(base).as_posix()] = identity
                if path.suffix.lower() in ('.js', '.py', '.lua'):
                    require(not path.read_text(encoding='utf-8').strip(), 'Nonempty standalone script requires a dedicated reviewed-script profile')
    observed_sources = {row['path']: {key: row[key] for key in ('bytes', 'sha256')} for row in review.get('script_sources', [])}
    require(observed_sources == required_sources, 'Global/world NPC script/export review source set differs')
    from .release_profile import world_port
    upgrade = json.loads((candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
    port = world_port(candidate, upgrade)
    if upgrade.get('profile_release') in ('0.3.12', '0.3.13'):
        from .release_profile import legacy_registry_scope
        require(pack.hash_file(runtime_level)['sha256'] == upgrade['registry_reference_level_sha256'], 'Wrong pinned legacy registry reference')
        names = {name for ids in saved_registries(candidate / LEVEL / 'level.dat').values() for name in ids}
        prior = json.loads((candidate / 'PRIVATE-BASELINE-0311-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
        legacy_registry_scope(upgrade['profile_release'], names, prior['added'], upgrade['added'])
    elif upgrade.get('profile_release') in ('0.3.14', '0.3.15'):
        if upgrade['profile_release'] == '0.3.15':
            from tools.everything_runtime_0315 import validate_flat, binding
        else:
            from tools.everything_runtime import validate_flat, binding
        reference = pack.no_links(upgrade['registry_reference_runtime'])
        validate_flat(reference)
        require(pack.no_links(runtime_level) == reference / smoke.LEVEL / 'level.dat', 'Wrong Everything registry reference world')
        require(pack.hash_file(runtime_level)['sha256'] == upgrade['registry_reference_level_sha256'], 'Everything registry reference changed')
        require(binding(reference) == binding(candidate), 'Everything registry reference uses different tested production components')
    registries = registry_review(candidate / LEVEL / 'level.dat', runtime_level)
    registries['reference_scope'] = upgrade.get('registry_reference_scope', 'Exact already booted 0.3.11 enhanced runtime')
    save(candidate / 'PRIVATE-WORLD-REGISTRY-REVIEW.json', registries)
    require(registries['missing_saved_names'] == 0, 'Missing saved registry IDs: do not accept or discard mappings')
    dependencies = stage_dependencies(candidate, recovery)
    result = {'schema': 1, 'status': 'COPIED_WORLD_VERIFIED_NOT_STARTED', 'level': LEVEL, 'port': port, 'memory_limit': memory,
              'profile_release': upgrade.get('profile_release', '0.3.11'),
              'source_manifest_sha256': prep['source_manifest_sha256'], 'source_frozen_receipt_sha256': identity['sha256'],
              'initial_world_files': prep['files'], 'script_review': review_id, 'script_review_regions': review['region_files'],
              'script_review_chunks': review['chunks'], 'missing_saved_registry_names': 0, 'dependency_file_count': len(dependencies['files']),
              'mapping_discard_authorized': False, 'normal_client_joined': False, 'hosted_deployment': False}
    profile(memory, port=port)
    if pack.no_links(script_review) != candidate / REVIEW:
        smoke.copy_verified(script_review, candidate / REVIEW, review_id['sha256'])
    save(candidate / READY, result)
    (candidate / smoke.MARKER).write_text(SEALED + '\n', encoding='ascii')
    validate_world(candidate)
    return result


def validate_world(candidate):
    candidate = pack.no_links(candidate)
    ready = json.loads((candidate / READY).read_text(encoding='utf-8'))
    require(ready.get('status') == 'COPIED_WORLD_VERIFIED_NOT_STARTED' and ready.get('missing_saved_registry_names') == 0, 'Copied-world acceptance seal invalid')
    upgrade = json.loads((candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
    from .release_profile import world_port
    port = world_port(candidate, upgrade)
    require(ready['level'] == LEVEL and ready['port'] == port, 'Copied-world target changed')
    require(ready.get('profile_release', '0.3.11') == upgrade.get('profile_release', '0.3.11'), 'Release profile changed')
    expected = profile(ready['memory_limit'], port=port)
    actual = smoke.properties((candidate / 'server.properties').read_text(encoding='ascii'))
    for key in ('level-name', 'level-type', 'server-ip', 'server-port', 'online-mode', 'enable-rcon', 'enable-query'):
        require(actual.get(key) == expected[key], 'Unsafe copied-world setting: ' + key)
    require((candidate / LEVEL / 'level.dat').is_file(), 'Copied saved world is missing')
    require(pack.hash_file(candidate / smoke.MAGMA)['sha256'] == smoke.MAGMA_SHA256, 'Magma identity changed')
    require(len(upgrade['added']) == 9, 'Copied-world enhanced component count differs')
    for row in upgrade['added']:
        require(pack.hash_file(candidate / row['path'])['sha256'] == row['sha256'], 'Enhanced artifact changed')
    require(not (candidate / smoke.OLD_PICTURES).exists(), 'Duplicate registry owner is active')
    cfg = (candidate / 'config/valeorders.cfg').read_text(encoding='utf-8')
    require(re.findall(r'B:canonicalEquipmentCatalog\s*=\s*(true|false)', cfg) == ['false'], 'Legacy catalog setting changed')
    review_id = pack.hash_file(candidate / REVIEW)
    require(review_id == ready['script_review'], 'Static script review changed')
    review = json.loads((candidate / REVIEW).read_text(encoding='utf-8'))
    expected_global = {row['path'][7:]: {key: row[key] for key in ('bytes', 'sha256')} for row in review['script_sources'] if row['path'].startswith('server/')}
    actual_global = {}
    global_root = candidate / 'customnpcs'
    if global_root.exists():
        for path in global_root.rglob('*'):
            if path.is_file() and 'playerdata' not in path.parts:
                actual_global[path.relative_to(candidate).as_posix()] = pack.hash_file(path)
    require(actual_global == expected_global, 'Reviewed global NPC exports/assets/scripts changed')
    last = candidate / LAST
    if last.exists():
        prior = json.loads(last.read_text(encoding='utf-8'))
        require(prior.get('clean_stop') is True, 'Previous copied-world boot failed; preserve it and prepare a fresh candidate')
        verify_rows(candidate / LEVEL, prior['world_files_after'])
    else:
        verify_rows(candidate / LEVEL, ready['initial_world_files'])
    return candidate


def record_boot(candidate, receipt):
    candidate = pack.no_links(candidate)
    value = {'schema': 1, 'label': receipt['label'], 'clean_stop': receipt['clean_stop'], 'exit_code': receipt['exit_code'],
             'world_files_after': tree_rows(candidate / LEVEL), 'source_frozen_world_modified': False}
    save(candidate / (receipt['label'] + '.world-snapshot.json'), value)
    save(candidate / LAST, value)
