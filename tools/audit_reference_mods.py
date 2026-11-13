#!/usr/bin/env python3
"""Inventory supplied reference JARs without extracting or redistributing content."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import zipfile


def inspect_jar(data):
    result = {'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        bad = jar.testzip()
        if bad is not None:
            raise ValueError('JAR CRC failure: ' + bad)
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate entries inside a reference JAR')
        result['crc_verified'] = True
        result['classes'] = sum(n.endswith('.class') for n in names)
        result['resources'] = {suffix: sum(n.lower().endswith(suffix) for n in names)
                               for suffix in ('.png', '.ogg', '.json', '.obj', '.js', '.aw')}
        result['license_entry_names'] = [n for n in names if
            PurePosixPath(n).name.lower().split('.')[0] in ('license', 'licence', 'copying', 'notice')]
        metadata = []
        for name in ('mcmod.info', 'META-INF/mcmod.info'):
            if name not in names:
                continue
            if jar.getinfo(name).file_size > 2 * 1024 * 1024:
                raise ValueError('Oversized mod metadata')
            try:
                parsed = json.loads(jar.read(name).decode('utf-8-sig'))
            except (ValueError, UnicodeError):
                result['metadata_parse_error'] = True
                continue
            if not isinstance(parsed, (dict, list)):
                result['metadata_parse_error'] = True
                continue
            rows = parsed if isinstance(parsed, list) else parsed.get('modList', [parsed])
            if not isinstance(rows, list):
                result['metadata_parse_error'] = True
                continue
            for row in rows:
                if isinstance(row, dict):
                    metadata.append({key: row[key] for key in
                        ('modid', 'name', 'version', 'mcversion', 'dependencies', 'requiredMods', 'dependants', 'url')
                        if key in row})
        result['declared_mods'] = metadata
        result['license_status'] = 'NOT_REVIEWED_NO_REDISTRIBUTION_PERMISSION_INFERRED'
    return result


def audit(path):
    path = Path(path)
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    rows, other, seen = [], [], set()
    with zipfile.ZipFile(path) as archive:
        for entry in sorted(archive.infolist(), key=lambda e: e.filename.casefold()):
            if entry.is_dir():
                continue
            name = PurePosixPath(entry.filename.replace('\\', '/'))
            if name.is_absolute() or '..' in name.parts or ':' in entry.filename:
                raise ValueError('Unsafe archive path')
            if not name.name.lower().endswith('.jar'):
                other.append(entry.filename)
                continue
            if name.name.casefold() in seen:
                raise ValueError('Ambiguous duplicate mod filename: ' + name.name)
            seen.add(name.name.casefold())
            if entry.file_size > 256 * 1024 * 1024:
                raise ValueError('Reference JAR exceeds inventory bound')
            rows.append({'file': name.name, 'archive_entry': entry.filename,
                         **inspect_jar(archive.read(entry))})
    return {'schema': 'vale.reference-mod-inventory/v1', 'archive_name': path.name,
            'archive_bytes': path.stat().st_size, 'archive_sha256': digest.hexdigest(),
            'jar_count': len(rows), 'jars': rows, 'other_entries': other,
            'scope': 'Metadata and integrity inventory only; not a feature-parity audit or license clearance.',
            'third_party_payloads_published': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.archive)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps({'jars': result['jar_count'], 'archive_sha256': result['archive_sha256'],
                      'files': [row['file'] for row in result['jars']]}, indent=2))


if __name__ == '__main__':
    main()
