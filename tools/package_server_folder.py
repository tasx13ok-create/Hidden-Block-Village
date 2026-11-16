"""Package one checked server folder for replacement through a hosting panel.

The ZIP retains its top-level folder (for example mods/), so extract it in the
server root. Runtime files stay private; only the tool and safe receipt belong
in Git. This does not perform hosted acceptance or modify the source server.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def digest(stream):
    value = hashlib.sha256()
    for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
        value.update(block)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-root', required=True, type=Path)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--folder')
    selection.add_argument('--root-file', action='append',
                           help='An explicitly selected server-root file; repeat for several files')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--receipt', required=True, type=Path)
    args = parser.parse_args()
    root = args.server_root.resolve(strict=True)
    for destination in (args.output, args.receipt):
        if destination.exists() or destination.resolve().is_relative_to(root):
            raise ValueError('Preserve existing outputs and keep output outside the server')
    inventory = json.loads((root / 'HBV-SERVER-CONTENTS.json').read_text(encoding='utf-8-sig'))
    if args.folder:
        if Path(args.folder).name != args.folder or args.folder in ('.', '..'):
            raise ValueError('A single direct child folder is required')
        folder = root / args.folder
        if not folder.is_dir() or folder.is_symlink() or folder.resolve().parent != root:
            raise ValueError('Source must be a real direct child of the server root')
        prefix = args.folder + '/'
        expected = {row['path']: row for row in inventory if row['path'].startswith(prefix)}
        paths = sorted(folder.rglob('*'))
        if any(path.is_symlink() or not path.resolve().is_relative_to(folder.resolve()) for path in paths):
            raise ValueError('Linked or escaped source path')
        files = {path.relative_to(root).as_posix(): path for path in paths if path.is_file()}
    else:
        names = args.root_file
        if len(set(names)) != len(names) or any(
                not name or Path(name).name != name or name in ('.', '..') for name in names):
            raise ValueError('Unique direct server-root file names are required')
        expected = {row['path']: row for row in inventory if row['path'] in names}
        files = {name: root / name for name in sorted(names)}
        if any(not path.is_file() or path.is_symlink() or path.resolve().parent != root
               for path in files.values()):
            raise ValueError('Root files must be real direct children of the server root')
    if not files or set(files) != set(expected):
        raise ValueError('Folder inventory differs from the accepted server manifest')
    snapshots = {}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'x', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=1, allowZip64=True) as archive:
        for name, path in files.items():
            before = path.stat()
            with path.open('rb') as stream:
                sha = digest(stream)
            if before.st_size != expected[name]['bytes'] or sha != expected[name]['sha256']:
                raise ValueError('Source file differs from accepted manifest: ' + name)
            snapshots[name] = (before.st_size, before.st_mtime_ns)
            archive.write(path, name)
    if args.output.stat().st_size >= 450_000_000:
        raise ValueError('Folder ZIP exceeds the conservative browser upload bound')
    with zipfile.ZipFile(args.output) as archive:
        if len(archive.namelist()) != len(files) or set(archive.namelist()) != set(files):
            raise ValueError('ZIP member inventory mismatch')
        for name in archive.namelist():
            with archive.open(name) as stream:
                sha = digest(stream)  # Reading through EOF also verifies the ZIP CRC.
            if sha != expected[name]['sha256']:
                raise ValueError('ZIP member readback differs: ' + name)
    for name, path in files.items():
        after = path.stat()
        if (after.st_size, after.st_mtime_ns) != snapshots[name]:
            raise ValueError('Source changed during packaging: ' + name)
    with args.output.open('rb') as stream:
        sha = digest(stream)
    receipt = {
        'schema': 1, 'scope': ('SINGLE_SERVER_FOLDER_REPLACEMENT' if args.folder
                              else 'SERVER_ROOT_FILES_REPLACEMENT'),
        'file': args.output.name, 'folder': args.folder,
        'bytes': args.output.stat().st_size, 'sha256': sha,
        'files': len(files), 'jars': sum(name.lower().endswith('.jar') for name in files),
        'source_manifest_exact_match': True, 'all_crc_and_sha256_readbacks_passed': True,
        'root_layout_preserved': True, 'below_browser_upload_bytes': 450_000_000,
        'source_and_live_server_modified': False,
        'instructions': ('Stop server; replace old folder; extract ZIP in server root. Keep stopped until all replacements are installed.'
                         if args.folder else 'Stop server; extract ZIP in server root and replace the selected files.'),
        'hosted_acceptance_performed': False,
    }
    if not args.folder:
        receipt['root_files'] = sorted(files)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_bytes((json.dumps(receipt, indent=2) + '\n').encode('utf-8'))
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
