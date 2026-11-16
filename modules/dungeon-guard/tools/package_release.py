#!/usr/bin/env python3
"""Package the verified runtime, banner, setup guide and exact acceptance receipt.

Source remains in the project's Git repository. No dependency, private fixture,
world save or full repository is copied into the installation archive.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

from verify_artifact import VERSION, verify


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--verification', type=Path, required=True)
    parser.add_argument('--source-url', required=True,
                        help='Public HTTPS link to the exact committed module source')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    artifact = verify(args.jar)
    receipt = json.loads(args.verification.read_text(encoding='utf-8'))
    if receipt.get('status') != 'PASS' or receipt.get('production_artifact', {}).get('sha256') != artifact['sha256']:
        parser.error('The passing receipt must identify this exact production JAR')
    if not args.source_url.startswith('https://github.com/tasx13ok-create/Hidden-Block-Village/tree/'):
        parser.error('Use the project source link with its exact commit')
    ref = args.source_url.split('/tree/', 1)[1].split('/', 1)[0]
    if len(ref) != 40 or any(c not in '0123456789abcdef' for c in ref):
        parser.error('The source link must use a full immutable Git commit')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    jar_out = output / ('Dungeon-Guard-1.12.2-' + VERSION + '.jar')
    zip_out = output / ('Dungeon-Guard-1.12.2-' + VERSION + '-Full-Package.zip')
    manifest_out = output / 'release-receipt.json'
    if any(p.exists() for p in (jar_out, zip_out, manifest_out)):
        parser.error('Choose an output directory without an existing release')
    files = {'mods/' + jar_out.name: args.jar.read_bytes()}
    for name in ('README.md', 'CONFIG-REFERENCE.md', 'START-HERE.txt', 'ARTWORK.md', 'CHANGELOG.md'):
        files[name] = (ROOT / name).read_bytes()
    files['art/dungeon-area-banner.png'] = (ROOT / 'src/main/resources/assets/dungeonguard/textures/gui/area_banner.png').read_bytes()
    files['verification/VERIFICATION.json'] = args.verification.read_bytes()
    files['SOURCE-CODE.txt'] = (
        'Original source, build tools and tests for this exact release:\n'
        + args.source_url + '\n\n'
        'Download or clone the repository, then follow modules/dungeon-guard/BUILD.md.\n'
        'This installation ZIP does not duplicate the source repository or include upstream mods.\n'
    ).encode('utf-8')
    file_records = [{'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                    for name, data in sorted(files.items())]
    package_manifest = {'schema': 1, 'version': VERSION, 'production_artifact': artifact,
                        'source_url': args.source_url, 'files': file_records}
    files['CONTENTS.json'] = (json.dumps(package_manifest, indent=2) + '\n').encode('utf-8')
    with zipfile.ZipFile(zip_out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    with zipfile.ZipFile(zip_out) as archive:
        assert archive.testzip() is None, 'Package integrity failure'
        assert archive.read('mods/' + jar_out.name) == args.jar.read_bytes(), 'Packaged JAR mismatch'
    shutil.copy2(args.jar, jar_out)
    result = {'schema': 1, 'version': VERSION, 'production_artifact': artifact,
              'source_url': args.source_url, 'package': {'file': zip_out.name,
              'bytes': zip_out.stat().st_size, 'sha256': hashlib.sha256(zip_out.read_bytes()).hexdigest(),
              'entries': len(files), 'source_repository_duplicated': False}}
    manifest_out.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
