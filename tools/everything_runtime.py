"""Explicit new0314 production-only flat preflight and reviewed Everything upgrade.

No command launches a client. Boot remains a separate guarded Magma smoke action.
Private runtime distributions/worlds/receipts remain outside the repository.
"""
from pathlib import Path
import argparse
import json

from tools.server_intake.intake import pack, save
from tools import everything_runtime_inputs as inputs
from tools.magma_smoke import smoke, world_profile as world
from tools.magma_smoke import all_mods_client as graphical
from tools.server_intake.inventory import jar_identity

FLAT_MARKER = 'EVERYTHING0314_PRODUCTION_FLAT_ONLY'
FLAT_PORT, WORLD_PORT = 25588, 25586
INPUT_RECEIPT = 'PRIVATE-EVERYTHING-INPUTS.json'


def binding(candidate):
    value = json.loads((candidate / INPUT_RECEIPT).read_text(encoding='utf8'))
    checked = inputs.validate(Path(value['frozen_root']), candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json',
                              candidate / 'PRIVATE-NATIVE-REPORT.json', **value['approved_pins'])
    smoke.require(checked == value['checked'], 'Everything frozen source/native binding differs')
    return checked


def install_binding(candidate, frozen_root, build_report, native_report, pins):
    checked = inputs.validate(frozen_root, build_report, native_report, **pins)
    smoke.copy_verified(build_report, candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json')
    smoke.copy_verified(native_report, candidate / 'PRIVATE-NATIVE-REPORT.json')
    save(candidate / INPUT_RECEIPT, {'frozen_root': str(pack.no_links(frozen_root)), 'approved_pins': pins, 'checked': checked})
    return checked


def verify_added(candidate, upgrade, checked):
    expected = checked['runtime_components']
    smoke.require(len(upgrade['added']) == 9 and {row['component'] for row in upgrade['added']} == set(inputs.SERVER), 'Everything server component roster differs')
    for row in upgrade['added']:
        smoke.require(row['mod_id'] == inputs.SERVER[row['component']] and row['sha256'] == expected[row['component']]['sha256'], 'Everything server/native identity differs')
        smoke.require(pack.hash_file(candidate / row['path']) == {k: row[k] for k in ('bytes', 'sha256')}, 'Installed Everything component changed')
    smoke.require(upgrade['source_snapshot_sha256'] == checked['source_snapshot_sha256'], 'Everything source snapshot differs')
    smoke.require(upgrade['source_build_report'] == checked['source_build_report'] and upgrade['native_report'] == checked['native_report'], 'Everything receipt identity differs')
    stage_path = candidate / 'PRIVATE-MAGMA-STAGE.json'
    smoke.require(pack.hash_file(stage_path) == upgrade['original_stage_manifest'], 'Original stage manifest changed')
    stage = json.loads(stage_path.read_text())
    for row in stage['staged']:
        relative = 'retained-originals/' + row['path'] if row['path'] == smoke.OLD_PICTURES else row['path']
        smoke.require(pack.hash_file(candidate / relative) == {k: row[k] for k in ('bytes', 'sha256')}, 'Original runtime/archive owner changed')
    for row in stage['dependencies']:
        path = candidate / row['target']
        if not path.exists() and row['target'] == 'minecraft_server.1.12.2.jar':
            path = candidate / 'libraries/minecraft_server.1.12.2.jar'
        smoke.require(pack.hash_file(path) == {k: row[k] for k in ('bytes', 'sha256')}, 'Reviewed runtime classpath changed')
    smoke.require(len(list((candidate / 'mods').glob('*.jar'))) == 54, 'Expected exact54 server JARs')


def validate_release_binding(candidate, upgrade):
    checked = binding(candidate)
    smoke.require(upgrade.get('profile_release') == '0.3.14' and upgrade.get('core_sha256') == checked['runtime_components']['vale-orders']['sha256'], 'Everything Core release differs')
    verify_added(candidate, upgrade, checked)
    return checked


def validate_flat(candidate):
    candidate = pack.no_links(candidate)
    smoke.require((candidate / smoke.MARKER).read_text().strip() == FLAT_MARKER, 'Everything flat-only marker missing')
    upgrade = json.loads((candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text())
    validate_release_binding(candidate, upgrade)
    expected = dict(smoke.PROPERTIES, **{'server-port': str(FLAT_PORT)})
    actual = smoke.properties((candidate / 'server.properties').read_text())
    if 'generator-settings' in actual:
        actual['generator-settings'] = actual['generator-settings'].replace(chr(92) + ':', ':')
    smoke.require(all(actual.get(key) == value for key, value in expected.items()), 'Everything production-flat settings changed')
    smoke.require(not (candidate / world.LEVEL).exists(), 'A copied world is forbidden in the production-flat profile')
    smoke.require(pack.hash_file(candidate / smoke.MAGMA)['sha256'] == smoke.MAGMA_SHA256, 'Archived Magma changed')
    smoke.require(not (candidate / smoke.OLD_PICTURES).exists(), 'Duplicate pictures registry owner')
    smoke.require(len(list((candidate / 'mods').glob('*.jar'))) == 54, 'Expected exact54 production-flat server JARs')
    smoke.require('B:canonicalEquipmentCatalog=false' in (candidate / 'config/valeorders.cfg').read_text(), 'Legacy catalog setting changed')
    review = json.loads((candidate / 'PRIVATE-EMPTY-SCRIPT-DEPENDENCIES.json').read_text())
    for row in review['global_sources']:
        smoke.require(pack.hash_file(candidate / row['path']) == {k: row[k] for k in ('bytes', 'sha256')}, 'Reviewed global NPC dependency changed')
    return candidate


def stage_flat(candidate, recovery, baseline, frozen_root, build_report, native_report, pins):
    candidate, recovery, baseline = map(pack.no_links, (candidate, recovery, baseline))
    checked = inputs.validate(frozen_root, build_report, native_report, **pins)
    old_stage = json.loads((baseline / 'PRIVATE-MAGMA-STAGE.json').read_text())
    deps = []
    for row in old_stage['dependencies']:
        source = baseline / row['target']
        if not source.exists() and row['target'] == 'minecraft_server.1.12.2.jar':
            source = baseline / 'libraries/minecraft_server.1.12.2.jar'
        smoke.require(pack.hash_file(source)['sha256'] == row['sha256'], 'Reviewed baseline classpath changed')
        deps.append(dict(target=row['target'], source_path=str(source), sha1=row['sha1']))
    smoke.stage(recovery, candidate, deps)
    # Pending marker makes an interrupted upgrade unbootable by either flat guard.
    (candidate / smoke.MARKER).write_bytes(b'EVERYTHING0314_UPGRADE_PENDING\n')
    install_binding(candidate, frozen_root, build_report, native_report, pins)
    old = candidate / smoke.OLD_PICTURES
    retained = candidate / 'retained-originals' / smoke.OLD_PICTURES
    retained.parent.mkdir(parents=True)
    old.rename(retained)
    added = []
    for component, modid in inputs.SERVER.items():
        row = checked['runtime_components'][component]
        target = 'mods/' + row['file']
        actual = smoke.copy_verified(Path(row['provider']), candidate / target, row['sha256'])
        added.append(dict(component=component, mod_id=modid, path=target, **actual))
    original_dependencies = world.stage_dependencies(candidate, recovery)
    cfg = candidate / 'config/valeorders.cfg'
    cfg.write_bytes(b'adventure {\n    B:canonicalEquipmentCatalog=false\n}\n')
    global_sources = []
    for path in sorted((candidate / 'customnpcs').rglob('*')):
        if path.is_file():
            if path.suffix.lower() in ('.js', '.py', '.lua'):
                smoke.require(not path.read_text(encoding='utf8').strip(), 'Executable global script requires separate reviewed support')
            global_sources.append(dict(path=path.relative_to(candidate).as_posix(), **pack.hash_file(path)))
    # Use the already complete source script review for non-script export JSONs.
    prior_review = json.loads((baseline / world.REVIEW).read_text())
    smoke.require(prior_review['status'] == 'NO_EXECUTABLE_SCRIPT_BODY_FOUND' and not prior_review['script_bodies'] and not prior_review['parse_errors'], 'Reviewed no-script source dependencies required')
    expected = {row['path'][7:]: {k: row[k] for k in ('bytes', 'sha256')} for row in prior_review['script_sources'] if row['path'].startswith('server/')}
    smoke.require({row['path']: {k: row[k] for k in ('bytes', 'sha256')} for row in global_sources} == expected, 'Global NPC dependency identities differ from reviewed absence proof')
    save(candidate / 'PRIVATE-EMPTY-SCRIPT-DEPENDENCIES.json', {'global_sources': global_sources, 'review': pack.hash_file(baseline / world.REVIEW)})
    upgrade = {'schema': 1, 'status': 'STAGED_ENHANCED_NOT_STARTED', 'profile_release': '0.3.14',
               'source_snapshot_sha256': checked['source_snapshot_sha256'], 'core_sha256': checked['runtime_components']['vale-orders']['sha256'],
               'source_build_report': checked['source_build_report'], 'native_report': checked['native_report'],
               'native_assertions': checked['native_assertions'], 'port': FLAT_PORT, 'added': added,
               'original_stage_manifest': pack.hash_file(candidate / 'PRIVATE-MAGMA-STAGE.json'),
               'removed_from_active': [dict(path=smoke.OLD_PICTURES, retained_path='retained-originals/' + smoke.OLD_PICTURES, **pack.hash_file(retained))],
               'canonicalEquipmentCatalog': False, 'recovered_world_copied': False}
    save(candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json', upgrade)
    values = dict(smoke.PROPERTIES, **{'server-port': str(FLAT_PORT)})
    (candidate / 'server.properties').write_bytes(''.join(k + '=' + v + '\n' for k, v in values.items()).encode())
    (candidate / smoke.MARKER).write_bytes((FLAT_MARKER + '\n').encode())
    validate_flat(candidate)
    return upgrade


def install_copied_runtime(candidate, flat, recovery):
    candidate, flat, recovery = map(pack.no_links, (candidate, flat, recovery))
    validate_flat(flat)
    smoke.require((candidate / smoke.MARKER).read_text().strip() == world.PENDING, 'Only the verified pending new copied world may be completed')
    smoke.require(not (candidate / smoke.MAGMA).exists() and not (candidate / world.LAST).exists(), 'Preserve a prior installed/booted attempt')
    stage = json.loads((flat / 'PRIVATE-MAGMA-STAGE.json').read_text())
    upgraded = json.loads((flat / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text())
    for row in stage['staged']:
        relative = 'retained-originals/' + row['path'] if row['path'] == smoke.OLD_PICTURES else row['path']
        smoke.copy_verified(flat / relative, candidate / relative, row['sha256'])
    for row in stage['dependencies']:
        source = flat / row['target']
        if not source.exists() and row['target'] == 'minecraft_server.1.12.2.jar':
            source = flat / 'libraries/minecraft_server.1.12.2.jar'
        if not (candidate / row['target']).exists():
            smoke.copy_verified(source, candidate / row['target'], row['sha256'])
    for row in upgraded['added']:
        smoke.copy_verified(flat / row['path'], candidate / row['path'], row['sha256'])
    for name in ('PRIVATE-MAGMA-STAGE.json', 'PRIVATE-SOURCE-BUILD-REPORT.json', 'PRIVATE-NATIVE-REPORT.json', INPUT_RECEIPT, 'eula.txt'):
        smoke.copy_verified(flat / name, candidate / name)
    cfg = candidate / 'config/valeorders.cfg'
    cfg.parent.mkdir(exist_ok=True)
    cfg.write_bytes(b'adventure {\n    B:canonicalEquipmentCatalog=false\n}\n')
    upgrade = dict(upgraded, port=WORLD_PORT, recovered_world_copied=True,
                   registry_reference_scope='Exact Core0.3.14 production-only54-JAR Magma flat saved registry inventory',
                   registry_reference_level_sha256=pack.hash_file(flat / smoke.LEVEL / 'level.dat')['sha256'],
                   registry_reference_runtime=str(flat))
    save(candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json', upgrade)
    prep = json.loads((candidate / world.PREP).read_text())
    prep.update(source_runtime=str(flat), source_snapshot_sha256=upgrade['source_snapshot_sha256'], core_sha256=upgrade['core_sha256'], port=WORLD_PORT)
    save(candidate / world.PREP, prep)
    properties = world.profile('2G', port=WORLD_PORT)
    properties.update(**{'view-distance': '6', 'gamemode': '0', 'force-gamemode': 'false',
                         'motd': 'Hidden Block Village Everything0.3.14 private test'})
    (candidate / 'server.properties').write_bytes(''.join(k + '=' + v + '\n' for k, v in properties.items()).encode())
    return world.seal(candidate, recovery, candidate / world.REVIEW, flat / smoke.LEVEL / 'level.dat', '2G')


def assemble_client(root, frozen_root, build_report, native_report, pins, donor):
    root, donor = map(pack.no_links, (root, donor))
    checked = inputs.validate(frozen_root, build_report, native_report, **pins)
    client = root / 'client'
    smoke.require(client.is_dir() and not (client / 'mods').exists(), 'Only the new prepared, unassembled client may be completed')
    base, base_id = pack.read_json(donor / 'PRIVATE-FULL-CLIENT-RECEIPT.json')
    recipe = graphical.client_recipe(base['client_launch_recipe'], client, 'ValePlaytest')
    recipe['argv'][recipe['argv'].index('--port') + 1] = str(WORLD_PORT)
    recipe['authentication'] = 'Synthetic ValePlaytest offline identity, accessToken0; no account credentials'
    external = graphical.check_external_inputs(recipe)
    mods, owners = [], {}
    for prior in base['mods']:
        component = prior.get('component')
        if component in inputs.SHARED | inputs.GRAPHICS:
            row = checked['runtime_components'][component]
            provider, name = Path(row['provider']), row['file']
        else:
            row = prior
            provider, name = donor / 'client/mods' / prior['file'], prior['file']
        identity = smoke.copy_verified(provider, client / 'mods' / name, row['sha256'])
        smoke.require(identity['bytes'] == row['bytes'], 'Graphical provider size differs')
        metadata = jar_identity(client / 'mods' / name, name)
        smoke.require(not metadata['warnings'], 'Graphical JAR metadata inspection failed')
        ids = []
        for declaration in metadata['mod_declarations']:
            modid = declaration['modid']
            smoke.require(modid not in owners and modid not in graphical.SERVER_ONLY, 'Duplicate or server-only graphical mod')
            owners[modid] = name
            ids.append(modid)
        mods.append(dict(file=name, component=component, mod_ids=ids, **identity))
    smoke.require(len(mods) == 58 and len(owners) == 55, 'Expected exact58 graphical JARs/55 static IDs')
    # Only the previously reviewed client-relevant original dependencies, no worlds/players/scripts/auth.
    preparation = json.loads((root / 'PRIVATE-EVERYTHING-PREPARATION.json').read_text())
    source_manifest = root / 'PRIVATE-BASELINE0313-FROZEN-MANIFEST.json'
    smoke.require(pack.hash_file(source_manifest) == preparation['source_frozen_receipt'], 'Client baseline manifest changed')
    baseline = pack.no_links(json.loads(source_manifest.read_text())['source'])
    deps = json.loads((baseline / 'PRIVATE-WORLD-DEPENDENCIES.json').read_text())
    copied = []
    for row in deps['files']:
        relative = row['path']
        if not graphical.eligible_dependency(relative):
            continue
        source = baseline / relative
        if source.suffix.lower() in ('.cfg', '.json', '.yml', '.yaml', '.txt', '.properties'):
            smoke.require(not world.credential_flags(source.read_bytes()), 'Client configuration requires credential review')
        copied.append(dict(path=relative, **smoke.copy_verified(source, client / relative)))
    (client / 'config/valeorders.cfg').write_bytes(b'adventure {\n    B:canonicalEquipmentCatalog=false\n}\n')
    jer = client / 'config/jeresources/jeresources.cfg'
    jer.parent.mkdir(parents=True)
    jer.write_bytes(b'general {\n    B:diyData=false\n}\n')
    smoke.require((client / 'config/CustomNpcs.cfg').read_text().count('InventoryGuiEnabled=true') == 1, 'New verified GUI patch must restore the original inventory tabs')
    # Do not overwrite the previously committed private options/profile pair or shader configs.
    for name, option_key, profile_key in (
        ('PRIVATE-CONTROLS-TRANSACTION.json', 'options_sha256', 'profile_sha256'),
        ('PRIVATE-FOCUS-CONTROLS-TRANSACTION.json', 'options_after_sha256', 'profile_after_sha256')):
        smoke.require((root / name).is_file(), 'Transactional full-pack controls are required')
    latest = json.loads((root / 'PRIVATE-FOCUS-CONTROLS-TRANSACTION.json').read_text())
    smoke.require(pack.hash_file(client / 'options.txt')['sha256'] == latest['options_after_sha256']
                  and pack.hash_file(client / 'config/field-controls/profiles.json')['sha256'] == latest['profile_after_sha256'], 'Transactional full-pack bindings changed')
    result = {'schema': 1, 'scope': 'PRIVATE_EVERYTHING0314_FULL58_GRAPHICAL_ASSEMBLY', 'status': 'ASSEMBLED_NOT_LAUNCHED',
              'source_snapshot_sha256': checked['source_snapshot_sha256'], 'source_build_report': checked['source_build_report'],
              'native_report': checked['native_report'], 'native_assertions': checked['native_assertions'],
              'mods': mods, 'static_mod_ids': len(owners), 'dependencies': copied, 'external_verification': external,
              'donor_receipt': base_id, 'six_shared_components': sorted(inputs.SHARED), 'server_port': WORLD_PORT,
              'synthetic_name': 'ValePlaytest', 'synthetic_uuid': graphical.offline_uuid('ValePlaytest'),
              'max_memory': '4G', 'canonicalEquipmentCatalog': False, 'JER_diyData': False,
              'CustomNPCs_InventoryGuiEnabled': True, 'whole_runnable_patch_verified_in_artifact': True,
              'controls': latest, 'minecraft_launched': False, 'network_joined': False,
              'shader_compiled_in_game': False, 'manual_ui_or_dodge_acceptance': False}
    save(root / 'PRIVATE-CLIENT-LAUNCH-RECIPE.json', recipe)
    save(root / 'PRIVATE-FULL-CLIENT-RECEIPT.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    flat = sub.add_parser('stage-flat')
    for name in ('candidate', 'recovery', 'baseline', 'frozen-root', 'build-report', 'native-report'):
        flat.add_argument('--' + name, type=Path, required=True)
    for name in ('source-pin', 'build-pin', 'native-pin'):
        flat.add_argument('--' + name, required=True)
    copied = sub.add_parser('install-copied')
    for name in ('candidate', 'flat', 'recovery'):
        copied.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if args.action == 'stage-flat':
        pins = {key: getattr(args, key) for key in ('source_pin', 'build_pin', 'native_pin')}
        value = stage_flat(args.candidate, args.recovery, args.baseline, args.frozen_root, args.build_report, args.native_report, pins)
    else:
        value = install_copied_runtime(args.candidate, args.flat, args.recovery)
    print(json.dumps({'status': value['status'], 'port': value['port']}))


if __name__ == '__main__':
    main()
