"""Make independent root-layout ZIPs below Bisect's browser upload limit.

These are ordinary ZIPs, not split/multipart volumes: extract every ZIP into
the same server root. Native server files are preserved byte for byte.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server-archive', type=Path, required=True)
    p.add_argument('--server-receipt', type=Path, required=True)
    p.add_argument('--directory', type=Path, required=True)
    p.add_argument('--receipt', type=Path, required=True)
    a = p.parse_args()
    if a.directory.exists() or a.receipt.exists():
        raise FileExistsError('Preserve existing output')
    prior = json.loads(a.server_receipt.read_text(encoding='utf-8-sig'))
    before = a.server_archive.stat()
    if not prior['root_layout_verified'] or not prior['all_member_crc_and_sha256_readback_passed'] or before.st_size != prior['bytes']:
        raise ValueError('Completed checked standalone server ZIP required')
    a.directory.mkdir(parents=True, exist_ok=False)
    limit = 450_000_000
    reserve = 2 * 1024 * 1024
    output = None
    parts = []
    expected = {}
    total = 0
    progress = time.monotonic()
    notes = '''HIDDEN BLOCK VILLAGE - BISECT BROWSER UPLOAD

These are INDEPENDENT complete ZIP files, each below450MB. They are not
multipart volumes: no joining, renaming, special extractor or .zip.001 needed.

1. Stop your Bisect server. Replace the old mods/plugins folders instead of
   mixing old JAR versions with this complete replacement (you have a backup).
2. Select ALL the PartNN.zip files together and drag them into the Files tab.
3. Right-click each uploaded ZIP > Unarchive into the SAME server root.
   After all parts are extracted, mods/config/plugins/Naruto.test belong at
   root, alongside server.properties. No extra server wrapper folder exists.
4. Keep/use Magma1.12.2, 64-bit Java8 and the included Magma server JAR, then
   start the server. Existing host settings/world/player data are retained.

PLAYERS import the separate Hidden Block Village.zip into CurseForge and
join your server address. Never give players these private SERVER parts.

Bisect documents a500MB browser limit:
https://help.bisecthosting.com/hc/en-us/articles/40124893034011-How-to-Move-Over-Existing-Minecraft-Server-Files
The large single server ZIP/folder is the alternative for SFTP drag-and-drop.
'''.encode('utf-8')
    try:
        with zipfile.ZipFile(a.server_archive) as source:
            names = source.namelist()
            if len(names) != len(set(names)) or any(n.startswith(('server/', 'client/')) for n in names):
                raise ValueError('Input is not an unambiguous root-layout server')
            for name in names:
                info = source.getinfo(name)
                if info.is_dir():
                    continue
                if info.file_size * 1.01 + reserve >= limit:
                    raise ValueError('One server file exceeds a safe browser part: ' + name)
                if output is None or output.fp.tell() + info.file_size * 1.01 + reserve >= limit:
                    if output is not None:
                        output.close()
                    path = a.directory / ('Hidden Block Village Server - Part %02d.zip' % (len(parts) + 1))
                    output = zipfile.ZipFile(path, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=1, allowZip64=True)
                    parts.append({'file': path.name, 'members': []})
                    print(json.dumps({'phase': 'WRITING_BROWSER_UPLOAD_PART', 'part': len(parts)}), flush=True)
                h = hashlib.sha256()
                with source.open(name) as reader, output.open(name, 'w', force_zip64=True) as writer:
                    if name == 'BISECT-START-HERE.txt':
                        writer.write(notes)
                        h.update(notes)
                    else:
                        for block in iter(lambda: reader.read(4 * 1024 * 1024), b''):
                            h.update(block)
                            writer.write(block)
                expected[name] = h.hexdigest()
                parts[-1]['members'].append(name)
                total += 1
                if time.monotonic() - progress > 8:
                    print(json.dumps({'phase': 'COMPRESSING_BROWSER_PARTS', 'files': total, 'parts': len(parts)}), flush=True)
                    progress = time.monotonic()
    finally:
        if output is not None:
            output.close()
    observed = set()
    for part in parts:
        path = a.directory / part['file']
        if not path.stat().st_size < limit:
            raise ValueError('Part exceeds the conservative browser bound')
        with zipfile.ZipFile(path) as z:
            if set(z.namelist()) != set(part['members']):
                raise ValueError('Part inventory mismatch')
            for name in z.namelist():
                if name in observed:
                    raise ValueError('Overlapping parts: ' + name)
                observed.add(name)
                h = hashlib.sha256()
                with z.open(name) as stream:
                    for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
                        h.update(block)
                if h.hexdigest() != expected[name]:
                    raise ValueError('Browser ZIP CRC/byte mismatch: ' + name)
        h = hashlib.sha256(path.read_bytes()).hexdigest()
        part.update(bytes=path.stat().st_size, sha256=h, files=len(part.pop('members')))
    if observed != set(expected):
        raise ValueError('Server entry missing from upload parts')
    after = a.server_archive.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ValueError('Server input changed')
    (a.directory / 'START-HERE.txt').write_bytes(notes)
    receipt = {'schema': 1, 'scope': 'INDEPENDENT_BISECT_BROWSER_UPLOAD_ZIPS',
               'source_server_sha256': prior['sha256'], 'parts': parts,
               'all_files_covered_once': True, 'root_layout_preserved': True,
               'all_crc_and_sha256_readbacks_passed': True,
               'each_part_below_bytes': limit, 'requires_zip_volume_joining': False,
               'server_payload_changed': False, 'operator_readme_updated': True,
               'contains_private_world_player_data': True,
               'live_session_or_original_inputs_modified': False}
    a.receipt.parent.mkdir(parents=True, exist_ok=True)
    a.receipt.write_bytes((json.dumps(receipt, indent=2) + '\n').encode('utf-8'))
    (a.directory / 'UPLOAD-PARTS.json').write_bytes((json.dumps(receipt, indent=2) + '\n').encode('utf-8'))
    print(json.dumps(receipt, indent=2), flush=True)


if __name__ == '__main__':
    main()
