"""Assemble, never launch, the exact 58-JAR graphical 0.3.12 compatibility client."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import uuid

from tools.server_intake.intake import pack, save
from tools.server_intake.inventory import jar_identity
from . import release_profile as release, smoke, world_profile as world

CHECKPOINT = 'd59ddd24f64cf404032fb9e497896bb5d3d26358'
PUBLIC_RECEIPT = 'release/evidence/core0312-verification.json'
SHARED = {'vale-orders', 'vale-encounters', 'vale-rack', 'vale-residents', 'vale-world-compat', 'village-standards'}
CLIENT_ONLY = {'field-hud', 'field-controls', 'client-lifecycle'}
SERVER_ONLY = {'residentserverbridge', 'valedamagecompat', 'valeserverbridge'}
OPTIFINE = '3b0006797771feb97f2d0d2908ae7c9a78cefb2e730af1c2e85d08a839ba271e'
SECRET_PATH = re.compile(r'(^|[/_. -])(auth|accounts?|credentials?|secrets?|tokens?|rcon|playerdata|saves|logs)([/_. -]|$)', re.I)


def require(ok, message):
    smoke.require(ok, message)


def offline_uuid(name):
    require(re.fullmatch(r'[A-Za-z0-9_]{3,16}', name) is not None, 'Invalid synthetic Minecraft username')
    raw = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode('utf-8')).digest())
    raw[6], raw[8] = (raw[6] & 15) | 48, (raw[8] & 63) | 128
    return uuid.UUID(bytes=bytes(raw)).hex


def client_recipe(source, client, username='ValeFullPack'):
    result = copy.deepcopy(source)
    argv = result['argv']
    require(argv.count('--accessToken') == 1 and argv[argv.index('--accessToken') + 1] == '0', 'Only a synthetic offline access token may be reused')
    require('--clientToken' not in argv and '--xuid' not in argv, 'Account-bearing launch options are forbidden')
    if '--userProperties' in argv:
        require(argv[argv.index('--userProperties') + 1] == '{}', 'Nonempty user properties are forbidden')
    require(argv[argv.index('--userType') + 1] == 'legacy', 'Offline legacy identity required')
    for key, value in (('--username', username), ('--uuid', offline_uuid(username)), ('--gameDir', str(client)), ('--server', '127.0.0.1'), ('--port', str(release.PORT))):
        require(argv.count(key) == 1, 'Unexpected launch option count: ' + key)
        argv[argv.index(key) + 1] = value
    require(sum(arg.startswith('-Xmx') for arg in argv) == 1, 'Exactly one maximum heap option required')
    result['argv'] = ['-Xmx4G' if arg.startswith('-Xmx') else arg for arg in argv]
    result['cwd'] = str(client)
    result['authentication'] = 'Synthetic ValeFullPack offline identity, accessToken0; no account credentials'
    return result


def check_external_inputs(recipe):
    for row in recipe['external_inputs']:
        path = pack.no_links(row['path'])
        require(not SECRET_PATH.search(path.name), 'Credential/account external input forbidden')
        require(pack.hash_file(path) == {key: row[key] for key in ('bytes', 'sha256')}, 'External launcher input changed')
    argv = recipe['argv']
    require(Path(argv[0]).name.lower() == 'javaw.exe', 'Reviewed Windows graphical Java executable required')
    classpath = argv[argv.index('-cp') + 1].split(';')
    pinned = {str(pack.no_links(row['path'])) for row in recipe['external_inputs']}
    require(all(str(pack.no_links(path)) in pinned for path in classpath), 'Unpinned classpath input')
    assets = pack.no_links(argv[argv.index('--assetsDir') + 1])
    index_name = argv[argv.index('--assetIndex') + 1]
    require(index_name == '1.12', 'Unexpected Minecraft assets index')
    index = assets / 'indexes/1.12.json'
    require(str(pack.no_links(index)) in pinned, 'Asset index is not pinned')
    data = json.loads(index.read_text(encoding='utf-8'))
    checked = []
    for value in data['objects'].values():
        digest = value['hash']
        require(re.fullmatch('[0-9a-f]{40}', digest) is not None, 'Invalid asset object hash')
        path = pack.no_links(assets / 'objects' / digest[:2] / digest)
        with pack.stable_reader(path) as stream:
            sha1, size = hashlib.sha1(), 0
            for chunk in iter(lambda: stream.read(1024**2), b''):
                sha1.update(chunk)
                size += len(chunk)
        require(sha1.hexdigest() == digest and size == value['size'], 'Missing or changed vanilla asset object')
        checked.append({'sha1': digest, 'bytes': size})
    return {'external_files_verified': len(recipe['external_inputs']), 'vanilla_asset_entries_verified': len(checked),
            'vanilla_asset_manifest_sha256': pack.digest(checked)}


def published_proof(repo):
    process = subprocess.run(['git', 'show', CHECKPOINT + ':' + PUBLIC_RECEIPT], cwd=str(repo), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    require(len(process.stdout) <= 4 * 1024**2, 'Oversize public verification receipt')
    value = json.loads(process.stdout)
    require(value['source_snapshot_sha256'] == release.SOURCE_SHA256 and value['source_build_passed'] is True, 'Published source proof differs')
    require(value['source_report']['sha256'] == release.BUILD_SHA256 and value['native']['sha256'] == release.NATIVE_SHA256,
            'Published build/native proof differs')
    require(value['native']['passed'] is True and value['native']['assertions'] == 2058 and release.CORE_SHA256 in json.dumps(value), 'Published Core/native scope differs')
    return value, hashlib.sha256(process.stdout).hexdigest()


def eligible_dependency(path):
    if SECRET_PATH.search(path) or any(part in ('sessions', 'players', 'playerdata', 'craftscripts') for part in path.split('/')):
        return False
    if Path(path).suffix.lower() in ('.js', '.py', '.lua'):
        return False
    return (path in {'config/' + name for name in world.ROOT_CONFIGS}
            or path.startswith(('config/armourers_workshop/', 'config/fluidlogged_api/', 'config/ftbquests/', 'config/xaero/', 'customnpcs/assets/', 'armourers_workshop/')))


def disable_inventory_tabs(data):
    """Apply exactly one reviewed client option; reject unknown/ambiguous forms."""
    pattern = rb'(?m)^InventoryGuiEnabled=true(?=\r?$)'
    require(len(re.findall(pattern, data)) == 1, 'Expected exactly one enabled CustomNPCs inventory-tab option')
    require(len(re.findall(rb'(?m)^InventoryGuiEnabled=', data)) == 1, 'Duplicate inventory-tab option')
    return re.sub(pattern, b'InventoryGuiEnabled=false', data)


def inventory_tab_variant(server, donor, recovery, destination, repo):
    """New same-JAR profile with a recorded, reversible client GUI workaround."""
    destination = pack.no_links(destination)
    result = assemble(server, donor, recovery, destination, repo)
    receipt_path = destination / 'PRIVATE-FULL-CLIENT-RECEIPT.json'
    config = destination / 'client/config/CustomNpcs.cfg'
    before = config.read_bytes()
    # Validate before creating a backup or changing any assembled file.
    after = disable_inventory_tabs(before)
    backup = destination / 'retained-original-config/CustomNpcs.cfg'
    backup.parent.mkdir()
    with backup.open('xb') as stream:
        stream.write(before)
    with config.open('wb') as stream:
        stream.write(after)
    require(config.read_bytes() == after and backup.read_bytes() == before, 'GUI workaround readback failed')
    receipt = {'schema': 1, 'scope': 'PRIVATE_0312_FULL58_CLIENT_GUI_WORKAROUND_NOT_ACCEPTANCE',
               'status': 'ASSEMBLED_NOT_LAUNCHED', 'base_assembly_receipt': pack.hash_file(receipt_path),
               'change': {'path': 'config/CustomNpcs.cfg', 'option': 'InventoryGuiEnabled', 'before_value': True,
                          'after_value': False, 'before': pack.hash_file(backup), 'after': pack.hash_file(config)},
               'mods': result['mods'], 'jar_count': len(result['mods']),
               'launch_recipe': pack.hash_file(destination / 'PRIVATE-CLIENT-LAUNCH-RECIPE.json'),
               'rationale': 'Bypasses CustomNPCs inventory tab registration and its observed off-thread quest-screen call; not a final thread-dispatch fix',
               'minecraft_invoked': False, 'network_joined': False}
    save(destination / 'PRIVATE-INVENTORY-TAB-VARIANT.json', receipt)
    return receipt


def assemble(server, donor, recovery, destination, repo, username='ValeFullPack'):
    server, donor, recovery, destination, repo = map(pack.no_links, (server, donor, recovery, destination, repo))
    require(not destination.exists(), 'Graphical profile must be new; existing previews are never replaced')
    for source in (server, donor, recovery):
        require(source not in destination.parents and destination not in source.parents and source != destination, 'Client overlaps an input')
    upgrade = json.loads((server / 'PRIVATE-MAGMA-ENHANCEMENT.json').read_text(encoding='utf-8'))
    require(release.world_port(server, upgrade) == release.PORT, 'Wrong matching server profile')
    last = json.loads((server / world.LAST).read_text(encoding='utf-8'))
    require(last['clean_stop'] is True, 'Matching server must have a clean stopped acceptance run')
    proof, proof_id = published_proof(repo)
    source, source_id = pack.read_json(donor / 'PRIVATE-PLAYTEST-RECEIPT.json')
    require(source['evidence']['build'] == upgrade['source_build_report'] and source['evidence']['native'] == upgrade['native_report'], 'Donor client is not bound to this frozen server build')
    client = destination / 'client'
    recipe = client_recipe(source['client_launch_recipe'], client, username)
    verification = check_external_inputs(recipe)
    java = source['java8']
    for label in ('java', 'javaw'):
        require(pack.hash_file(Path(java[label]['path'])) == {key: java[label][key] for key in ('bytes', 'sha256')}, 'Java executable changed')
    require(pack.hash_file(Path(java['home']) / 'release') == java['release'], 'Java release identity changed')
    require(str(pack.no_links(recipe['argv'][0])) == str(pack.no_links(java['javaw']['path'])), 'Unexpected graphical executable')
    inventory = json.loads((recovery / 'PRIVATE-STATIC-INVENTORY.json').read_text(encoding='utf-8'))
    original = [row for row in inventory['jars'] if row['category'] == 'mods']
    require(len(original) == 49, 'Exact 49-mod source inventory required')
    rows = []
    for row in original:
        if row['path'] == smoke.OLD_PICTURES:
            continue
        rows.append({'source': recovery / 'server' / row['path'], 'file': Path(row['path']).name, 'origin': 'original49', 'sha256': row['sha256']})
    chosen = [row for row in source['client_mods'] if row.get('component') in SHARED | CLIENT_ONLY]
    require(len(chosen) == 9 and {row['component'] for row in chosen} == SHARED | CLIENT_ONLY, 'Exact nine graphical components required')
    server_by_component = {row['component']: row for row in upgrade['added']}
    for row in chosen:
        if row['component'] in SHARED:
            require(row['sha256'] == server_by_component[row['component']]['sha256'], 'Shared client/server artifact differs')
        rows.append({'source': donor / 'client/mods' / row['file'], 'file': row['file'], 'origin': 'vale-client',
                     'component': row['component'], 'sha256': row['sha256']})
    optic = next(row for row in source['client_mods'] if row['sha256'] == OPTIFINE)
    rows.append({'source': donor / 'client/mods' / optic['file'], 'file': optic['file'], 'origin': 'optifine', 'sha256': OPTIFINE})
    require(len(rows) == 58 and len({row['file'].casefold() for row in rows}) == 58, 'Expected 58 unique client JARs')
    result = {'schema': 1, 'scope': 'PRIVATE_0312_FULL58_GRAPHICAL_ASSEMBLY_ONLY', 'status': 'IN_PROGRESS',
              'published_checkpoint': CHECKPOINT, 'published_receipt_sha256': proof_id, 'donor_receipt': source_id,
              'source_build_report': upgrade['source_build_report'], 'native_report': upgrade['native_report'],
              'core_sha256': release.CORE_SHA256, 'client_launch_recipe': recipe, 'java8': java,
              'mods': [], 'dependencies': [], 'six_shared_components': sorted(SHARED), 'verification': verification,
              'synthetic_username': username, 'synthetic_offline_uuid': offline_uuid(username), 'memory_cap': '4G',
              'canonicalEquipmentCatalog': False, 'JER_diyData': False, 'server_port': release.PORT,
              'minecraft_invoked': False, 'network_joined': False, 'auth_files_copied': False, 'world_copied': False}
    destination.mkdir(parents=True)
    try:
        owners = {}
        for row in rows:
            identity = smoke.copy_verified(row['source'], client / 'mods' / row['file'], row['sha256'])
            metadata = jar_identity(client / 'mods' / row['file'], row['file'])
            require(not metadata['warnings'], 'JAR metadata inspection failed')
            ids = {}
            for declaration in metadata['mod_declarations']:
                modid = declaration['modid']
                require(modid not in owners, 'Duplicate client mod ID: ' + modid)
                require(modid not in SERVER_ONLY and not declaration.get('serverSideOnly'), 'Server-only bridge supplied to graphical client')
                owners[modid], ids[modid] = row['file'], declaration.get('version')
            result['mods'].append(dict(file=row['file'], origin=row['origin'], component=row.get('component'), mod_ids=ids, **identity))
        deps = json.loads((server / 'PRIVATE-WORLD-DEPENDENCIES.json').read_text(encoding='utf-8'))
        paths = sorted({row['path'] for row in deps['files'] if eligible_dependency(row['path'])} | {'config/valeorders.cfg'})
        for relative in paths:
            source_path = server / relative
            if source_path.suffix.lower() in ('.cfg', '.json', '.yml', '.yaml', '.txt', '.properties'):
                require(not world.credential_flags(source_path.read_bytes()), 'Credential-like configuration field requires review')
            identity = smoke.copy_verified(source_path, client / relative)
            result['dependencies'].append(dict(path=relative, **identity))
        require(re.findall(r'B:canonicalEquipmentCatalog\s*=\s*(true|false)', (client / 'config/valeorders.cfg').read_text()) == ['false'], 'Client legacy catalog setting differs')
        generated = {'config/jeresources/jeresources.cfg': 'general {\n    B:diyData=false\n}\n',
                     'options.txt': 'renderDistance:8\nparticles:0\nguiScale:0\nfullscreen:false\ngamma:0.6\nenableVsync:true\nautoJump:false\n',
                     'optionsshaders.txt': 'shaderPack=OFF\n'}
        for relative, text in generated.items():
            target = client / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('x', encoding='utf-8', newline='\n') as stream:
                stream.write(text)
        result['generated'] = [dict(path=path, **pack.hash_file(client / path)) for path in generated]
        result['mod_ids_count'] = len(owners)
        result['status'] = 'ASSEMBLED_NOT_LAUNCHED'
        save(destination / 'PRIVATE-FULL-CLIENT-RECEIPT.json', result)
        save(destination / 'PRIVATE-CLIENT-LAUNCH-RECIPE.json', recipe)
        (destination / 'START-HERE.txt').write_text('ASSEMBLED ONLY — DO NOT LAUNCH UNTIL ROOT REVIEWS\n58 JARs; Core0.3.12; synthetic ValeFullPack; loopback25583; max4GB.\nReview PRIVATE-FULL-CLIENT-RECEIPT.json and use the explicit argv recipe.\nNo credentials or saved worlds were copied.\n', encoding='utf-8')
        return result
    except BaseException as error:
        result.update(status='FAILED_PRESERVED', error_type=type(error).__name__, error=str(error))
        save(destination / 'PRIVATE-FULL-CLIENT-RECEIPT.json', result)
        raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for argument in ('server', 'donor', 'recovery', 'destination', 'repo'):
        parser.add_argument('--' + argument, type=Path, required=True)
    parser.add_argument('--inventory-tab-workaround', action='store_true', help='New profile only: disable the verified unsafe CustomNPCs inventory tabs')
    args = parser.parse_args(argv)
    action = inventory_tab_variant if args.inventory_tab_workaround else assemble
    result = action(args.server, args.donor, args.recovery, args.destination, args.repo)
    print(json.dumps({'status': result['status'], 'jars': len(result['mods']), 'minecraft_invoked': False,
                      'inventory_tab_workaround': args.inventory_tab_workaround}, indent=2))


if __name__ == '__main__':
    main()
