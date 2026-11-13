"""Dry-run or build a new private CustomNPCs data overlay; never modify a world."""
import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path
import re
import zipfile

from tools.server_intake.intake import pack, recover, save
from .formats import MAX_BYTES, FormatError, check, fields, numeric, read_nbt, read_snbt, scalar, semantic_hash, tag, write_nbt, write_snbt

MAX_TOTAL = 512 * 1024**2
ADAPTER_CUSTOMNPCS_SHA256 = '9002f091bd06dd08bbb0c93b57a9279729b6716813094954a5d57cef101b4e0b'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def check_paths(rows):
    try:
        pack.check_names({'path': path, 'kind': 'file'} for path in rows)
    except pack.PackError as error:
        raise FormatError(str(error)) from error


def read_bytes(path):
    with pack.stable_reader(path) as stream:
        data = stream.read(MAX_BYTES + 1)
    check(len(data) <= MAX_BYTES, 'NPC file size limit exceeded')
    return data


def classify(path):
    parts = path.split('/')
    check(parts[:2] in (['server', 'customnpcs'], ['world', 'customnpcs']), 'Unsupported bundle root: ' + path)
    tail = parts[2:]
    check(bool(tail), 'NPC file has no relative path')
    check(tail[0] != 'playerdata', 'Friend playerdata is forbidden; preserve local player progress separately')
    if tail[0] == 'clones' and len(tail) == 3 and tail[1].isdigit() and tail[2].endswith('.json'):
        return 'clone'
    if parts[0] == 'world' and tail[0] in ('dialogs', 'quests') and len(tail) >= 3 and tail[-1].endswith('.json'):
        return tail[0][:-1]
    if parts[0] == 'world' and tail == ['factions.dat']:
        return 'factions'
    if tail[0] == 'assets':
        return 'asset'
    if tail[0] == 'scripts' or tail[-1] in ('global.dat', 'recipes.dat', 'spawns.dat'):
        return 'opaque'
    raise FormatError('Unsupported CustomNPCs file form: ' + path)


def parse_file(path, data):
    kind = classify(path)
    row = {'path': path, 'kind': kind, 'data': data, 'sha256': sha(data), 'bytes': len(data), 'node': None}
    if kind in ('clone', 'dialog', 'quest'):
        row['node'] = read_snbt(data)
        row['format'] = 'typed_snbt'
    elif kind == 'factions':
        row['node'], row['root_name'], row['compressed'] = read_nbt(data)
        row['format'] = 'gzip_nbt' if row['compressed'] else 'raw_nbt'
        check(set(fields(row['node'])) <= {'NPCFactions', 'lastID'}, 'Unsupported faction root fields')
        listing = fields(row['node']).get('NPCFactions')
        check(listing is not None and listing['type'] == 'list' and listing['value']['element'] in ('compound', 'end'), 'Unsupported faction list schema')
    if row['node'] is not None:
        row['semantic_sha256'] = semantic_hash(row['node'])
    return row


def definition_field(row):
    if row['kind'] == 'dialog':
        return 'DialogId'
    body = fields(row['node'])
    check(not ('Id' in body and 'QuestId' in body), 'Ambiguous quest ID fields')
    return 'Id' if 'Id' in body else 'QuestId'


def scan(root, world=None, friend=False):
    root = pack.no_links(root)
    result, skipped = {}, Counter()
    if friend:
        roots = [(root, '')]
    else:
        pack.safe_relative(world, basename=True)
        roots = [(root / 'customnpcs', 'server/customnpcs'), (root / world / 'customnpcs', 'world/customnpcs')]
    total = 0
    for base, prefix in roots:
        if not base.exists():
            continue
        pack.no_links(base)
        for path in sorted(base.rglob('*')):
            pack.no_links(path)
            if not path.is_file():
                continue
            relative = path.relative_to(base).as_posix()
            if friend and relative == 'npc-bundle.json':
                continue
            logical = prefix + '/' + relative if prefix else relative
            pack.safe_relative(logical)
            if not friend and ('playerdata' in relative.split('/') or relative.endswith('.dat_old')):
                skipped['private_player_or_rollback_files'] += 1
                continue
            data = read_bytes(path)
            total += len(data)
            check(total <= MAX_TOTAL, 'NPC bundle total size limit exceeded')
            check(len(result) < 10000, 'NPC bundle file-count limit exceeded')
            result[logical] = parse_file(logical, data)
    check_paths(result)
    return result, dict(skipped)


def definitions(rows):
    result = {}
    for path, row in rows.items():
        kind, node = row['kind'], row['node']
        values = []
        if kind in ('dialog', 'quest'):
            value = scalar(fields(node).get(definition_field(row)))
            check(isinstance(value, int) and not isinstance(value, bool) and value >= 0, 'Missing nonnegative definition ID: ' + path)
            check(Path(path).stem == str(value), 'Definition filename must equal its internal ID: ' + path)
            values = [(kind + ':' + str(value), node)]
        elif kind == 'clone':
            values = [('clone:' + path, node)]
        elif kind == 'factions':
            for faction in fields(node)['NPCFactions']['value']['items']:
                value = scalar(fields(faction).get('Slot'))
                check(isinstance(value, int) and value >= 0, 'Missing faction Slot ID')
                values.append(('faction:' + str(value), faction))
        for identity, value in values:
            check(identity not in result, 'Duplicate definition identity: ' + identity)
            result[identity] = {'path': path, 'node': value, 'semantic_sha256': semantic_hash(value)}
    return result


def references(node):
    """Version-bounded structural references. Script/command text remains opaque."""
    refs, opaque = [], []
    def visit(value, trail=()):
        if value['type'] == 'compound':
            body = fields(value)
            for key, child in body.items():
                item = scalar(child)
                target = None
                if key in ('FactionID', 'OptionFactions1', 'OptionFactions2'):
                    target = 'faction'
                elif key in ('DialogQuest', 'MailQuest', 'NextQuestId'):
                    target = 'quest'
                elif re.fullmatch(r'Availability(Dialog|Quest|Faction)[234]?Id', key):
                    selector = key[:-2]
                    if scalar(body.get(selector)) not in (0, None):
                        target = re.search(r'(Dialog|Quest|Faction)', key).group(1).lower()
                elif key == 'Dialog' and (scalar(body.get('OptionType')) == 1 or 'NPCDialogOptions' in trail):
                    target = 'dialog'
                elif key == 'DialogId' and 'NPCDialogOptions' in trail:
                    target = 'dialog'
                if target and isinstance(item, int) and item >= 0:
                    refs.append({'kind': target, 'id': item, 'trail': list(trail + (key,)), 'node': child})
                elif target and not isinstance(item, int):
                    opaque.append({'trail': list(trail + (key,)), 'reason': 'unsupported_reference_value_type'})
                if key == 'AttackFactions':
                    if child['type'] in ('int_array', 'long_array'):
                        for index, number in enumerate(child['value']):
                            if number >= 0:
                                refs.append({'kind': 'faction', 'id': number, 'trail': list(trail + (key, index)), 'array': child, 'index': index})
                    elif child['type'] == 'list':
                        for index, element in enumerate(child['value']['items']):
                            number = scalar(element)
                            if isinstance(number, int) and number >= 0:
                                refs.append({'kind': 'faction', 'id': number, 'trail': list(trail + (key, index)), 'node': element})
                            else:
                                opaque.append({'trail': list(trail + (key, index)), 'reason': 'unsupported_attack_faction_form'})
                    else:
                        opaque.append({'trail': list(trail + (key,)), 'reason': 'unsupported_attack_faction_container'})
                if key in ('LinkedNpcName', 'CloneName', 'CompleterNpc', 'SkinUrl', 'DialogCommand', 'QuestCommand', 'Command', 'Script', 'ScriptScript') and item not in ('', None):
                    opaque.append({'trail': list(trail + (key,)), 'reason': 'uninterpreted_' + key})
                if key in ('QuestDialogs', 'QuestFactionPoints', 'QuestLocation', 'QuestLocation2', 'QuestLocation3'):
                    if (child['type'] == 'list' and child['value']['items']) or (child['type'] == 'compound' and fields(child)) or item not in ('', None, -1):
                        opaque.append({'trail': list(trail + (key,)), 'reason': 'unsupported_type_specific_quest_objective'})
                if re.fullmatch(r'(Dialog|Quest|Faction).*Id', key) and key not in ('DialogId', 'QuestId', 'NextQuestId') and target is None:
                    opaque.append({'trail': list(trail + (key,)), 'reason': 'unsupported_id_reference_field'})
                if key == 'Scripts' and child['type'] == 'list' and child['value']['items']:
                    opaque.append({'trail': list(trail + (key,)), 'reason': 'inline_script_list'})
                elif key == 'Scripts' and child['type'] != 'list':
                    opaque.append({'trail': list(trail + (key,)), 'reason': 'unsupported_script_container'})
                visit(child, trail + (key,))
        elif value['type'] == 'list':
            for index, child in enumerate(value['value']['items']):
                visit(child, trail + (index,))
    visit(node)
    return refs, opaque


def remap(rows, mapping):
    rows = copy.deepcopy(rows)
    maps = mapping.get('id_remap', {})
    for kind, values in maps.items():
        check(kind in ('dialog', 'quest', 'faction') and isinstance(values, dict), 'Unsupported ID remap kind')
        for old, new in values.items():
            check(str(int(old)) == old and int(old) >= 0 and isinstance(new, int) and not isinstance(new, bool) and 0 <= new <= 2**31-1, 'ID remap must use nonnegative integer IDs')
    result = {}
    for path, row in rows.items():
        node, kind = row['node'], row['kind']
        if node is not None:
            refs, unused = references(node)
            for reference in refs:
                new = maps.get(reference['kind'], {}).get(str(reference['id']))
                if new is not None:
                    if 'node' in reference:
                        reference['node'].update(numeric(reference['node']['type'], new))
                    else:
                        reference['array']['value'][reference['index']] = new
            if kind in ('dialog', 'quest'):
                key = definition_field(row)
                id_node = fields(node)[key]
                new = maps.get(kind, {}).get(str(scalar(id_node)))
                if new is not None:
                    id_node.update(numeric(id_node['type'], new))
                    path = str(Path(path).with_name(str(new) + '.json')).replace('\\', '/')
            elif kind == 'factions':
                for faction in fields(node)['NPCFactions']['value']['items']:
                    id_node = fields(faction)['Slot']
                    new = maps.get('faction', {}).get(str(scalar(id_node)))
                    if new is not None:
                        id_node.update(numeric(id_node['type'], new))
            if semantic_hash(node) != row.get('semantic_sha256'):
                row['data'] = write_nbt(node, row['root_name'], row['compressed']) if kind == 'factions' else (write_snbt(node) + '\n').encode('utf-8')
                row['sha256'], row['semantic_sha256'], row['bytes'] = sha(row['data']), semantic_hash(node), len(row['data'])
        check(path not in result, 'Remap creates a duplicate output path')
        row['path'] = path
        result[path] = row
    check_paths(result)
    definitions(result)
    return result


def runtime_mods(inventory_path, baseline_root=None):
    if inventory_path is None:
        return {}
    value = json.loads(read_bytes(inventory_path).decode('utf-8'))
    result = {}
    for jar in value.get('jars', []):
        pack.safe_relative(jar['path'])
        if baseline_root is not None:
            check(pack.hash_file(Path(baseline_root) / jar['path'])['sha256'] == jar['sha256'], 'Runtime inventory differs from the actual baseline JAR')
            with pack.stable_reader(Path(baseline_root) / jar['path']) as stream, zipfile.ZipFile(stream) as archive:
                entries = archive.infolist()
                check(len(entries) <= 200000, 'Resource catalog JAR entry limit exceeded')
                for entry in entries:
                    parts = entry.filename.split('/')
                    if not entry.is_dir() and len(parts) >= 3 and parts[0] == 'assets':
                        result['resource:' + parts[1] + ':' + '/'.join(parts[2:])] = jar['sha256']
        result['jar:' + jar['path']] = jar['sha256']
        for declaration in jar.get('mod_declarations', []):
            if declaration.get('modid'):
                check(declaration['modid'] not in result or result[declaration['modid']] == jar['sha256'], 'Different JAR payloads declare the same mod ID')
                result[declaration['modid']] = jar['sha256']
    return result


def make_plan(baseline, incoming, manifest, mapping, mods):
    check(manifest.get('schema') == 1 and mapping.get('schema', 1) == 1, 'Unsupported bundle/mapping schema')
    check(set(mapping) <= {'schema', 'id_remap', 'definitions', 'paths', 'opaque_acknowledgements'}, 'Unknown mapping fields')
    issues, warnings, operations = [], [], []
    required = manifest.get('required_mods')
    check(isinstance(required, dict), 'npc-bundle.json must include required_mods with exact JAR hashes')
    if 'customnpcs' not in required:
        issues.append({'code': 'MISSING_CUSTOMNPCS_IDENTITY_IN_FRIEND_MANIFEST'})
    for modid, identity in required.items():
        if mods.get(modid) != identity:
            issues.append({'code': 'MOD_IDENTITY_MISMATCH', 'modid': modid})
    if mods.get('customnpcs') != ADAPTER_CUSTOMNPCS_SHA256:
        issues.append({'code': 'UNSUPPORTED_CUSTOMNPCS_RUNTIME', 'expected_sha256': ADAPTER_CUSTOMNPCS_SHA256})
    original_definitions = definitions(incoming)
    original_incoming_manifest = {path: row['sha256'] for path, row in incoming.items()}
    incoming = remap(incoming, mapping)
    definitions(baseline)
    final = dict(baseline)
    base_defs, incoming_defs = definitions(baseline), definitions(incoming)
    acknowledged = mapping.get('opaque_acknowledgements', {})
    for path, row in incoming.items():
        old = final.get(path)
        if row['kind'] == 'factions':
            original = old['node'] if old else tag('compound', {'NPCFactions': tag('list', {'element': 'compound', 'items': []}), 'lastID': tag('int', -1)})
            node = copy.deepcopy(original)
            current = {scalar(fields(f)['Slot']): f for f in fields(node)['NPCFactions']['value']['items']}
            for faction in fields(row['node'])['NPCFactions']['value']['items']:
                identity = 'faction:' + str(scalar(fields(faction)['Slot']))
                prior = base_defs.get(identity)
                decision = mapping.get('definitions', {}).get(identity)
                if prior and prior['semantic_sha256'] != semantic_hash(faction) and decision not in ('incoming', 'baseline'):
                    issues.append({'code': 'ID_CONFLICT', 'identity': identity, 'path': path})
                    continue
                if decision != 'baseline':
                    current[scalar(fields(faction)['Slot'])] = faction
            fields(node)['NPCFactions'] = tag('list', {'element': 'compound', 'items': [current[key] for key in sorted(current)]})
            last = max([scalar(fields(node).get('lastID')) or 0, scalar(fields(row['node']).get('lastID')) or 0] + list(current))
            fields(node)['lastID'] = tag('int', last)
            row = dict(row, node=node, data=write_nbt(node, old['root_name'] if old else row['root_name'], old['compressed'] if old else row['compressed']))
            row.update(sha256=sha(row['data']), bytes=len(row['data']), semantic_sha256=semantic_hash(node))
        elif row['kind'] in ('dialog', 'quest'):
            identity = next(key for key, definition in incoming_defs.items() if definition['path'] == path)
            prior = base_defs.get(identity)
            if prior and prior['semantic_sha256'] != row['semantic_sha256']:
                decision = mapping.get('definitions', {}).get(identity)
                if decision not in ('incoming', 'baseline'):
                    issues.append({'code': 'ID_CONFLICT', 'identity': identity, 'path': path})
                    continue
                if decision == 'baseline':
                    continue
                old_path = prior['path']
                if old_path != path:
                    final.pop(old_path)
        semantic_equal = old is not None and row['node'] is not None and old.get('semantic_sha256') == row.get('semantic_sha256')
        if old and old['sha256'] != row['sha256'] and not semantic_equal and row['kind'] not in ('dialog', 'quest', 'factions'):
            decision = mapping.get('paths', {}).get(path)
            if decision not in ('incoming', 'baseline'):
                issues.append({'code': 'PATH_CONFLICT', 'path': path})
                continue
            if decision == 'baseline':
                continue
        if row['kind'] == 'opaque' and acknowledged.get(path) != row['sha256']:
            issues.append({'code': 'OPAQUE_FILE_REQUIRES_HASH_ACKNOWLEDGEMENT', 'path': path, 'sha256': row['sha256']})
        if row['node'] is not None:
            refs, opaque = references(row['node'])
            if opaque and acknowledged.get(path) != row['sha256']:
                issues.append({'code': 'OPAQUE_REFERENCES_REQUIRE_HASH_ACKNOWLEDGEMENT', 'path': path, 'sha256': row['sha256'], 'forms': opaque})
        final[path] = row
        operations.append({'path': path, 'action': 'unchanged' if old and old['sha256'] == row['sha256'] else 'replace' if old else 'add', 'sha256': row['sha256']})
    check_paths(final)
    final_defs = definitions(final)
    resources = {key[9:] for key in mods if key.startswith('resource:')}
    for asset_path, asset in final.items():
        parts = asset_path.split('/')
        if asset['kind'] == 'asset' and len(parts) >= 5:
            resources.add(parts[3] + ':' + '/'.join(parts[4:]))
    baseline_dangling = set()
    for path, row in baseline.items():
        if row['node'] is not None:
            for reference in references(row['node'])[0]:
                identity = reference['kind'] + ':' + str(reference['id'])
                if identity not in base_defs:
                    baseline_dangling.add((path, tuple(reference['trail']), identity))
    for path, row in final.items():
        if row['node'] is None:
            continue
        for reference in references(row['node'])[0]:
            identity = reference['kind'] + ':' + str(reference['id'])
            if identity not in final_defs:
                item = {'code': 'UNRESOLVED_REFERENCE', 'path': path, 'identity': identity, 'trail': reference['trail']}
                (warnings if (path, tuple(reference['trail']), identity) in baseline_dangling else issues).append(item)
        def namespaces(node):
            if node['type'] == 'compound':
                for key, child in fields(node).items():
                    text = scalar(child)
                    if key == 'id' and isinstance(text, str) and ':' in text:
                        namespace = text.split(':', 1)[0]
                        if namespace not in ('minecraft', 'forge') and namespace not in mods and path in incoming:
                            issues.append({'code': 'MISSING_ITEM_OR_ENTITY_NAMESPACE', 'path': path, 'namespace': namespace})
                    if key in ('Texture', 'CloakTexture', 'GlowTexture') and isinstance(text, str) and text and path in incoming:
                        resource = text if ':' in text else 'minecraft:' + text
                        if resource not in resources:
                            issues.append({'code': 'UNRESOLVED_TEXTURE_RESOURCE', 'path': path, 'resource': resource})
                    namespaces(child)
            elif node['type'] == 'list':
                for child in node['value']['items']:
                    namespaces(child)
        namespaces(row['node'])
    public_counts = dict(Counter(row['kind'] for row in final.values()))
    report = {'schema': 1, 'scope': 'PRIVATE_CUSTOMNPCS_DATA_OVERLAY_ONLY', 'status': 'BLOCKED' if issues else 'READY_FOR_CANDIDATE',
              'baseline_files': len(baseline), 'incoming_files': len(incoming), 'candidate_files': len(final), 'candidate_counts': public_counts,
              'incoming_definition_count': len(original_definitions), 'operations': operations, 'issues': issues, 'warnings': warnings,
              'baseline_manifest': {path: row['sha256'] for path, row in baseline.items()},
              'incoming_original_manifest': original_incoming_manifest,
              'candidate_manifest': {path: row['sha256'] for path, row in final.items()},
              'baseline_manifest_sha256': semantic_hash({path: row['sha256'] for path, row in baseline.items()}),
              'incoming_manifest_sha256': semantic_hash({path: row['sha256'] for path, row in incoming.items()}),
              'candidate_manifest_sha256': semantic_hash({path: row['sha256'] for path, row in final.items()}),
              'mapping_sha256': semantic_hash(mapping), 'scripts_executed': False, 'world_modified': False, 'placed_entities_merged': False}
    return report, final


def run(baseline_root, world, bundle, output, mapping=None, inventory_path=None, candidate=False):
    output, bundle = pack.no_links(output), pack.no_links(bundle)
    check(not output.exists(), 'Output must be new; existing previews/backups are preserved')
    baseline_root = pack.no_links(baseline_root)
    for source in (baseline_root, bundle):
        check(source != output and source not in output.parents and output not in source.parents, 'Output and source trees must not overlap')
    output.mkdir(parents=True)
    try:
        if bundle.is_file():
            recovery = recover(bundle, output / 'friend-zip', max_entries=10000, max_bytes=MAX_TOTAL)
            check(not recovery['collisions'] and not recovery['prefix_aliases'], 'Friend ZIP has aliases/conflicts; resolve its preserved map before NPC intake')
            bundle = output / 'friend-zip/server'
        manifest = json.loads(read_bytes(bundle / 'npc-bundle.json').decode('utf-8-sig'))
        baseline, skipped = scan(baseline_root, world=world)
        incoming, unused = scan(bundle, friend=True)
        check(bool(incoming), 'Friend bundle contains no supported NPC data')
        mapping_value = json.loads(read_bytes(mapping).decode('utf-8-sig')) if mapping else {'schema': 1}
        report, final = make_plan(baseline, incoming, manifest, mapping_value, runtime_mods(inventory_path, baseline_root))
        report['baseline_skipped'] = skipped
        report['candidate_written'] = False
        if candidate and not report['issues']:
            for folder, rows in (('backup', baseline), ('candidate', final)):
                for path, row in rows.items():
                    destination = pack.no_links(output / folder / path)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with destination.open('xb') as stream:
                        stream.write(row['data'])
                    check(pack.hash_file(destination)['sha256'] == row['sha256'], 'Candidate/backup readback differs')
            report['candidate_written'] = True
            report['status'] = 'CANDIDATE_OVERLAY_WRITTEN_NOT_STARTED'
            report['backup_verified'] = True
        save(output / 'PRIVATE-NPC-PLAN.json', report)
        return report
    except BaseException as error:
        save(output / 'PRIVATE-NPC-FAILED.json', {'status': 'FAILED_PRESERVED', 'error_type': type(error).__name__, 'error': str(error), 'scripts_executed': False, 'world_modified': False})
        raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--world', required=True)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--mapping', type=Path)
    parser.add_argument('--runtime-inventory', type=Path)
    parser.add_argument('--candidate', action='store_true')
    args = parser.parse_args(argv)
    report = run(args.baseline, args.world, args.bundle, args.output, args.mapping, args.runtime_inventory, args.candidate)
    print(json.dumps({key: report[key] for key in ('status', 'baseline_files', 'incoming_files', 'candidate_files', 'candidate_written')}, indent=2))
    print(json.dumps({'blocking_issues': len(report['issues']), 'warnings': len(report['warnings'])}))
    return 2 if report['issues'] else 0
