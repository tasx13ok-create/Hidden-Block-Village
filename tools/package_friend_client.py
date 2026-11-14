"""Export a receipt-bound client for a fresh friend profile; never copy accounts/worlds."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import zipfile


OMIT = {
    'config/fieldhud-loadouts.json', 'config/field-controls/profiles.json.previous',
    'config/jei/worldSettings.cfg', 'config/xaeropatreon.txt', 'patchouli_data.json',
}
ROOTS = {'config', 'resourcepacks', 'shaderpacks', 'armourers_workshop', 'customnpcs'}
OPTIONS = {'options.txt', 'optionsof.txt', 'optionsshaders.txt'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_path(value):
    p = PurePosixPath(value)
    if '\\' in value or p.is_absolute() or '..' in p.parts or ':' in value:
        raise ValueError('Unsafe relative path: ' + value)
    return p


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--fallback-resource-root', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--receipt-output', type=Path, required=True)
    parser.add_argument('--core-version', required=True)
    args = parser.parse_args()
    for path in [args.output, args.receipt_output, args.output.with_suffix('.zip.sha256')]:
        if path.exists():
            raise FileExistsError(path)
    raw_receipt = args.receipt.read_bytes()
    receipt = json.loads(raw_receipt.decode('utf-8-sig'))
    expected = {row['file']: row for row in receipt['mods']}
    if len(expected) != len(receipt['mods']) or len(expected) != 58:
        raise ValueError('Expected the exact full 58-JAR client roster')
    actual = {p.name for p in (args.client / 'mods').iterdir() if p.is_file()}
    if actual != set(expected):
        raise ValueError('Client mod roster differs from its receipt')
    payload, origins, omitted = {}, {}, []

    def add_pinned(relative, row, roots):
        safe_path(relative)
        for index, root in enumerate(roots):
            path = root.joinpath(*PurePosixPath(relative).parts)
            if not path.is_file() or path.is_symlink():
                continue
            data = path.read_bytes()
            if len(data) == row['bytes'] and digest(data) == row['sha256']:
                key = 'overrides/' + relative
                if key.casefold() in {name.casefold() for name in payload}:
                    raise ValueError('Duplicate archive path: ' + key)
                payload[key] = data
                origins[key] = 'primary' if index == 0 else 'hash-matched-frozen-resource'
                return
        raise ValueError('No exact receipt-pinned input for ' + relative)

    for name, row in sorted(expected.items()):
        add_pinned('mods/' + name, row, [args.client])
        with zipfile.ZipFile(io.BytesIO(payload['overrides/mods/' + name])) as jar:
            if jar.testzip() is not None:
                raise ValueError('Broken mod archive: ' + name)
    core_path = 'overrides/mods/vale-orders-' + args.core_version + '.jar'
    with zipfile.ZipFile(io.BytesIO(payload[core_path])) as jar:
        info = json.loads(jar.read('mcmod.info'))
        if not any(m.get('modid') == 'valeorders' and m.get('version') == args.core_version for m in info):
            raise ValueError('Core embedded metadata/version mismatch')
    for row in receipt['resources']:
        relative = row['path']
        p = safe_path(relative)
        if relative in OMIT or relative.startswith('config/field-controls/backups/'):
            omitted.append(relative)
            continue
        if p.parts[0] not in ROOTS and relative not in OPTIONS:
            raise ValueError('Unreviewed resource location: ' + relative)
        add_pinned(relative, row, [args.client] + args.fallback_resource_root)

    options = payload['overrides/options.txt'].decode('utf-8-sig')
    options = '\n'.join('lastServer:' if line.startswith('lastServer:') else line
                        for line in options.splitlines()) + '\n'
    payload['overrides/options.txt'] = options.encode('utf-8')
    if 'key_key.valeorders.dodge:47' not in options or 'key_key.fieldhud.jutsu_focus:33' not in options:
        raise ValueError('Expected Dodge V and combat focus F bindings')
    shaders = payload['overrides/optionsshaders.txt'].decode('utf-8-sig')
    selected_shader = next(line.split('=', 1)[1] for line in shaders.splitlines() if line.startswith('shaderPack='))
    if 'overrides/shaderpacks/' + selected_shader not in payload:
        raise ValueError('Selected shader is absent')
    if 'overrides/resourcepacks/Vale-Reference-Flat-Icons-v3.zip' not in payload:
        raise ValueError('Selected icon pack is absent')

    manifest = {
        'minecraft': {'version': '1.12.2', 'modLoaders': [{'id': 'forge-14.23.5.2859', 'primary': True}]},
        'manifestType': 'minecraftModpack', 'manifestVersion': 1,
        'name': 'Naruto Complete Client - Friend Test', 'version': args.core_version + '-test.1',
        'author': 'Hidden Block Village', 'files': [], 'overrides': 'overrides',
    }
    payload['manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    readme = '''NARUTO COMPLETE CLIENT - CORE {version} FRIEND TEST

INSTALL
1. In CurseForge's Minecraft section, choose Import and select this ZIP.
   Import the ZIP itself, without unpacking it or wrapping it in another ZIP.
2. Use Minecraft 1.12.2 / Forge 14.23.5.2859 and 64-bit Java 8.
   Start with 4 GB of Minecraft memory; 6 GB is optional if the PC has room.
   Add -Dfml.readTimeout=120 to the profile's extra Java arguments for the
   same cold-world timeout tolerance used by the current local preview.
3. Launch and sign in with YOUR OWN Minecraft account.
4. Multiplayer > Add Server: enter the address supplied by the host.
   The host needs the matching Core {version} server/mod set.

Manual alternative: create a fresh Forge 14.23.5.2859 profile for Minecraft
1.12.2, open its game directory and copy the CONTENTS of overrides into it.
Use the same Java/memory settings above. Keep old profiles separate.

CONTROLS
V: Dodge. Hold F for three seconds: switch normal/combat hotbar.
E: inventory. F7/F8: Naruto settings/controls. F12: swap hands.
F6/F9/F10 retain the displaced legacy action bindings. Rebind in Settings.
BSL shaders and the resource pack are included and selected. If the friend's
PC struggles, choose Shaders OFF in Video Settings; the gameplay mods remain.

WHAT IS INCLUDED
All 58 client mod JARs from the pinned Core {version} preview, matching
configuration, Naruto inventory/HUD, current items/worn armor/resources,
Dodge/combat controls, OptiFine, BSL shaders, minimap and armor-library assets.
All mod bytes are included locally; this is not only a remote-download list.
The launcher installs the Minecraft/Forge runtime normally.

TEST STATUS
Core {version} passed all 11 component builds / 36 artifacts and 3,649
disposable Forge native assertions. This ZIP's roster, hashes, nested JARs,
settings references and all archive bytes have been checked. It is a test
snapshot. Remaining Claude features, full-pack gameplay/Creative checks and
hosted Bisect acceptance were not complete at packaging. It does not claim
the entire planned game or five campaigns are finished.

This is the client download. The Naruto world, server plugins, player unlocks
and issued/owned equipment come from the host's matching server. No personal
world save, account/session, logs, server list, map history or saved player
loadout is included. A friend's new character does not inherit the owner's
test equipment. Ask the host to provide the intended test setup in-game.

If reporting a problem, include logs/latest.log from your new profile and
describe the action. Keep account/session files private.

Import instructions: https://support.curseforge.com/support/solutions/articles/9000197912
'''.format(version=args.core_version)
    payload['START-HERE.txt'] = readme.encode('utf-8')
    payload['MODLIST.txt'] = ('\n'.join(sorted(expected)) + '\n').encode('utf-8')
    pins = {'schema': 1, 'scope': 'FRIEND_CLIENT_TEST_DOWNLOAD', 'core_version': args.core_version,
            'source_receipt_sha256': digest(raw_receipt), 'mod_count': len(expected),
            'minecraft': '1.12.2', 'forge': '14.23.5.2859', 'java_major': 8,
            'full_pack_gameplay_accepted': False, 'bisect_verified': False,
            'mods': sorted(receipt['mods'], key=lambda r: r['file']),
            'files': [{'path': k, 'bytes': len(v), 'sha256': digest(v)} for k, v in sorted(payload.items())]}
    payload['CLIENT-PINS.json'] = (json.dumps(pins, indent=2) + '\n').encode('utf-8')
    forbidden = {'essential', 'logs', 'saves', 'screenshots', 'local', 'xaero', 'crash-reports'}
    for name in payload:
        parts = PurePosixPath(name).parts
        if (len(parts) > 1 and parts[0] == 'overrides' and parts[1] in forbidden) or 'PRIVATE-' in name:
            raise ValueError('Private runtime path reached payload: ' + name)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in sorted(payload.items()):
            archive.writestr(name, data)
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None or set(archive.namelist()) != set(payload):
            raise ValueError('Archive integrity/roster failure')
        for name, data in payload.items():
            if archive.read(name) != data:
                raise ValueError('Archive readback mismatch: ' + name)
    summary = {'schema': 1, 'scope': 'FRIEND_CLIENT_ARCHIVE_INTEGRITY_NOT_GAMEPLAY_ACCEPTANCE',
               'archive': args.output.name, 'bytes': args.output.stat().st_size,
               'sha256': digest(args.output.read_bytes()), 'core_version': args.core_version,
               'mods': len(expected), 'entries': len(payload), 'crc_and_byte_readback_passed': True,
               'selected_shader': selected_shader, 'omitted_profile_resources': sorted(omitted),
               'frozen_resource_fallbacks': sorted(k for k,v in origins.items() if v != 'primary'),
               'source_receipt_sha256': digest(raw_receipt),
               'source_snapshot_sha256': receipt['checked']['source_snapshot_sha256'],
               'gameplay_test_performed': False, 'bisect_test_performed': False,
               'private_runtime_and_account_directories_included': False}
    args.receipt_output.parent.mkdir(parents=True, exist_ok=True)
    args.receipt_output.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    args.output.with_suffix('.zip.sha256').write_text(summary['sha256'] + '  ' + args.output.name + '\n', encoding='ascii')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
