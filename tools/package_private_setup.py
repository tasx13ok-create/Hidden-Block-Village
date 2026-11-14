"""Package a stopped private server/world and a separately sanitized client ZIP.

The resulting ZIP contains private player/world data. Never commit or publish it.
This tool checks archive integrity; it does not certify gameplay or licensing.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import socket
import time
import zipfile

OMIT = {'logs', 'crash-reports', 'retained-originals', 'creative0316-live-requests'}
SERVER_DIRS = {'config', 'configurations', 'customnpcs', 'plugins', 'armourers_workshop', 'libraries', 'mappings', 'mods', 'Naruto.test', 'patchouli_books'}
SERVER_FILES = {'server.properties', 'eula.txt', 'magma.yml', 'magma_libs.json', 'spigot.yml', 'bukkit.yml', 'commands.yml', 'help.yml', 'paper.yml', 'permissions.yml', 'ops.json', 'whitelist.json', 'banned-ips.json', 'banned-players.json', 'binpatches.pack.lzma', 'deobfuscation_data-1.12.2.lzma', 'HBV-MERGE-RECEIPT.json'}


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server', type=Path, required=True)
    p.add_argument('--client-archive', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--receipt', type=Path, required=True)
    p.add_argument('--port', type=int, required=True)
    p.add_argument('--hosted-settings', action='store_true', help='Preserve the supplied upload folder connection settings in this private archive')
    a = p.parse_args()
    if a.output.exists() or a.receipt.exists():
        raise FileExistsError('Preserve existing archives and receipts')
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', a.port))
    if not (a.server / 'Naruto.test/level.dat').is_file():
        raise ValueError('Expected a copied Naruto.test world')
    props = (a.server / 'server.properties').read_text(encoding='utf-8')
    if not a.hosted_settings and ('server-ip=127.0.0.1' not in props or 'online-mode=false' not in props):
        raise ValueError('This private preview must remain loopback only')
    with zipfile.ZipFile(a.client_archive) as z:
        if z.testzip() is not None or 'manifest.json' not in z.namelist():
            raise ValueError('Client upload is not a valid modpack archive')
    selected = []
    for path in sorted(a.server.rglob('*')):
        relative = path.relative_to(a.server)
        if relative.parts[0] in OMIT or relative.parts[0].startswith('PRIVATE-'):
            continue
        if relative.parts[0] not in SERVER_DIRS and not (len(relative.parts) == 1 and (relative.name in SERVER_FILES or path.suffix == '.jar')):
            continue
        if path.is_symlink() or any(parent.is_symlink() for parent in path.parents):
            raise ValueError('Refusing links: ' + str(relative))
        if path.is_file():
            selected.append(('server/' + relative.as_posix(), path))
    selected.append(('client/' + a.client_archive.name, a.client_archive))
    a.output.parent.mkdir(parents=True, exist_ok=True)
    records = []
    last = time.monotonic()
    with zipfile.ZipFile(a.output, 'x', compression=zipfile.ZIP_STORED, allowZip64=True) as z:
        for name, path in selected:
            before = path.stat()
            h = hashlib.sha256()
            with path.open('rb') as source, z.open(name, 'w', force_zip64=True) as destination:
                for block in iter(lambda: source.read(4 * 1024 * 1024), b''):
                    h.update(block)
                    destination.write(block)
            pin = h.hexdigest()
            after = path.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise ValueError('Input changed during packaging; retain incomplete output: ' + name)
            records.append({'path': name, 'bytes': before.st_size, 'sha256': pin})
            if time.monotonic() - last > 8:
                print(json.dumps({'phase': 'PACKAGING_PRIVATE_SETUP', 'files': len(records)}), flush=True)
                last = time.monotonic()
        z.writestr('PRIVATE-CONTENTS.json', json.dumps(records, indent=2) + '\n')
        z.writestr('START-HERE.txt', '''PRIVATE HIDDEN BLOCK VILLAGE SETUP - CORE 0.3.16
This archive includes the copied Naruto world and private player data.
Keep this full archive private. Share only the separate clean client upload.

CLIENT: Import client/*.zip in CurseForge. Minecraft1.12.2 / Forge14.23.5.2859,
64-bit Java8, 4GB memory. Sign in with your own Minecraft account.
SERVER: Set JAVA8_HOME to an installed 64-bit Java8 directory and run
server/START-PRIVATE-SERVER.cmd. This archive retains the supplied server's
connection settings. Use the host's address for multiplayer; for a local copy
set server-ip=127.0.0.1 and choose a free port in server.properties.
V Dodge; E inventory; F7/F8 Naruto settings; hold F for three seconds for combat hotbar.
NoBreak is server-only and disabled by default so normal building works.
Original mod icons/armor are active. Our own items/UI/effects remain installed.
The separate local creative preview uses admin-granted equipment/unlocks.
The uploaded server world and its original player records are preserved.

This package's integrity checks do not certify that the entire game, every
mission, every boss or hosted deployment has passed acceptance. Read the
matching repository release evidence for actual checks and remaining work.
''')
        z.writestr('server/START-PRIVATE-SERVER.cmd', '@echo off\r\ncd /d "%~dp0"\r\nif not defined JAVA8_HOME (echo Set JAVA8_HOME to your 64-bit Java8 directory first. & pause & exit /b 1)\r\n"%JAVA8_HOME%\\bin\\java.exe" -Xms512M -Xmx3G -Dfml.readTimeout=120 -jar Magma-1.12.2-8cc35ed-server.jar nogui\r\npause\r\n')
    with zipfile.ZipFile(a.output) as z:
        # Reading each member to EOF below checks its ZIP CRC as well as SHA256.
        for row in records:
            h = hashlib.sha256()
            with z.open(row['path']) as stream:
                for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
                    h.update(block)
            if h.hexdigest() != row['sha256']:
                raise ValueError('ZIP readback differs: ' + row['path'])
        for name in ('PRIVATE-CONTENTS.json', 'START-HERE.txt', 'server/START-PRIVATE-SERVER.cmd'):
            z.read(name)
    receipt = {'schema': 1, 'scope': 'PRIVATE_SETUP_ARCHIVE_INTEGRITY',
               'created_utc': datetime.now(timezone.utc).isoformat(),
               'archive': a.output.name, 'bytes': a.output.stat().st_size,
               'sha256': digest(a.output), 'files': len(records) + 3,
               'contains_private_world_and_player_data': True,
               'crc_and_all_file_sha256_readback_passed': True,
               'gameplay_acceptance_claimed': False, 'hosting_accepted': False}
    a.receipt.parent.mkdir(parents=True, exist_ok=True)
    with a.receipt.open('x', encoding='utf-8') as stream:
        json.dump(receipt, stream, indent=2)
        stream.write('\n')
    print(json.dumps(receipt, indent=2), flush=True)


if __name__ == '__main__':
    main()
