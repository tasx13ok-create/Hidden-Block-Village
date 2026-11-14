"""Create a new standalone server folder and a root-layout host-upload ZIP.

Consumes the verified private setup archive. It does not modify an input, host
settings, world data, or any running game. The generated server remains private.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import time
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--setup-archive', type=Path, required=True)
    p.add_argument('--setup-receipt', type=Path, required=True)
    p.add_argument('--directory', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--receipt', type=Path, required=True)
    p.add_argument('--core-sha256', required=True)
    a = p.parse_args()
    for output in (a.directory, a.output, a.receipt):
        if output.exists():
            raise FileExistsError('Preserve existing output: ' + str(output))
    prior = json.loads(a.setup_receipt.read_text(encoding='utf-8-sig'))
    before = a.setup_archive.stat()
    if not prior['crc_and_all_file_sha256_readback_passed'] or before.st_size != prior['bytes']:
        raise ValueError('A verified completed full setup archive is required')
    if a.setup_archive.name != prior['archive']:
        raise ValueError('Setup receipt names another archive')
    a.directory.mkdir(parents=True, exist_ok=False)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    tick = time.monotonic()
    with zipfile.ZipFile(a.setup_archive) as source, zipfile.ZipFile(a.output, 'x', compression=zipfile.ZIP_STORED, allowZip64=True) as output:
        names = source.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Ambiguous source ZIP members')
        pins = {r['path']: r for r in json.loads(source.read('PRIVATE-CONTENTS.json'))}
        selected = sorted(n for n in pins if n.startswith('server/'))
        if len(selected) < 100 or not any(n.startswith('server/Naruto.test/region/') for n in selected):
            raise ValueError('Copied Naruto world is missing')
        for name in selected:
            relative = name[len('server/'):]
            rel = PurePosixPath(relative)
            if rel.is_absolute() or '..' in rel.parts or '\\' in relative or ':' in relative:
                raise ValueError('Unsafe source member: ' + name)
            dest = a.directory.joinpath(*rel.parts)
            dest.parent.mkdir(parents=True, exist_ok=True)
            h = hashlib.sha256()
            size = 0
            with source.open(name) as src, dest.open('xb') as folder, output.open(relative, 'w', force_zip64=True) as archive:
                for block in iter(lambda: src.read(4 * 1024 * 1024), b''):
                    h.update(block)
                    size += len(block)
                    folder.write(block)
                    archive.write(block)
            pin = pins[name]
            if size != pin['bytes'] or h.hexdigest() != pin['sha256']:
                raise ValueError('Source entry changed: ' + name)
            rows.append({'path': relative, 'bytes': size, 'sha256': h.hexdigest()})
            if time.monotonic() - tick > 8:
                print(json.dumps({'phase': 'BUILDING_STANDALONE_BISECT_SERVER', 'files': len(rows)}), flush=True)
                tick = time.monotonic()
        by_path = {r['path']: r for r in rows}
        if by_path.get('mods/Naruto-Core-0.3.16.jar', {}).get('sha256') != a.core_sha256:
            raise ValueError('Server does not contain the checked Dodge-fixed Core')
        for needed in ('server.properties', 'Naruto.test/level.dat', 'Magma-1.12.2-8cc35ed-server.jar', 'plugins/NoBreak-1.4-1.12-compat.jar'):
            if needed not in by_path:
                raise ValueError('Required server input missing: ' + needed)
        if sum(r['path'].startswith('mods/') and r['path'].endswith('.jar') for r in rows) != 53:
            raise ValueError('Server mod roster changed')
        readme = '''HIDDEN BLOCK VILLAGE - BISECT SERVER UPLOAD

This ZIP contains the server files directly at its root. No wrapper folder and
no client ZIP need to be moved around after extraction.

1. Stop the Bisect server. Upload this ZIP through its file manager and extract
   it into the server's root directory, where server.properties belongs.
2. Keep/use Magma 1.12.2, 64-bit Java 8 and the included
   Magma-1.12.2-8cc35ed-server.jar as the server runtime.
3. Start the server. The supplied host settings, Naruto.test world, player data,
   plugin data and original world spawn are preserved.

PLAYERS: Import the SEPARATE Hidden Block Village.zip in CurseForge, sign in
with their own Minecraft account, then join the host's server address.
Client: Minecraft1.12.2 / Forge14.23.5.2859 / Java8, initially4GB memory.
Do not import this SERVER ZIP as a CurseForge client.

Core0.3.16 includes the real-client Dodge capability fix; client HUD is0.20.1.
Original mod artwork is retained. NoBreak is server-only and starts disabled
so ordinary building works. Existing original-mod asset defects remain; this
is a tested current build, not complete campaign/hosting acceptance.

This server upload contains private world/player records. Give players only
the separate clean client ZIP. Do not publish this server archive publicly.
'''
        generated = {'BISECT-START-HERE.txt': readme.encode('utf-8'),
                     'HBV-SERVER-CONTENTS.json': (json.dumps(rows, indent=2) + '\n').encode('utf-8')}
        for name, data in generated.items():
            (a.directory / name).write_bytes(data)
            output.writestr(name, data)
    after = a.setup_archive.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ValueError('Input archive changed while exporting')
    with zipfile.ZipFile(a.output) as z:
        if any(name.startswith(('server/', 'client/')) for name in z.namelist()):
            raise ValueError('Server ZIP contains a wrapper directory')
        for row in rows:
            h = hashlib.sha256()
            with z.open(row['path']) as stream:
                for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
                    h.update(block)
            if h.hexdigest() != row['sha256']:
                raise ValueError('Server ZIP byte/CRC readback failed: ' + row['path'])
        for name, data in generated.items():
            if z.read(name) != data:
                raise ValueError('Generated instructions/manifest readback failed')
    h = hashlib.sha256()
    with a.output.open('rb') as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            h.update(block)
    receipt = {'schema': 1, 'scope': 'STANDALONE_BISECT_SERVER_FOLDER_AND_ROOT_ZIP',
               'archive': a.output.name, 'bytes': a.output.stat().st_size, 'sha256': h.hexdigest(),
               'source_verified_setup_sha256': prior['sha256'], 'server_mods': 53,
               'server_files': len(rows), 'root_layout_verified': True,
               'all_member_crc_and_sha256_readback_passed': True,
               'includes_private_world_and_player_data': True,
               'host_settings_world_and_plugin_data_preserved': True,
               'original_inputs_and_live_session_modified': False,
               'actual_hosted_acceptance_claimed': False}
    a.receipt.parent.mkdir(parents=True, exist_ok=True)
    with a.receipt.open('x', encoding='utf-8', newline='\n') as f:
        json.dump(receipt, f, indent=2)
        f.write('\n')
    a.output.with_suffix('.zip.sha256').write_bytes((h.hexdigest() + '  ' + a.output.name + '\n').encode('utf-8'))
    print(json.dumps(receipt, indent=2), flush=True)


if __name__ == '__main__':
    main()
