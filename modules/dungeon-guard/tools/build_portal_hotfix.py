#!/usr/bin/env python3
"""Build the narrow 1.4.3 portal fix from the exact inspected 1.4.2 artifact.

Compiles the original updated transformer with Java 8, preserves every other
implementation/resource entry, updates version metadata, and executes the
regressions against the assembled JAR. No upstream/runtime/test classes are
bundled. This is deliberately not a claim of a full Gradle or live server test.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
BASE_SHA256 = '6f25789a73f6d392c1333fb2f57fbe6147596244cbed375c00af66cd772e6329'
ASM_SHA256 = '7fbffbc1db3422e2101689fd88df8384b15817b52b9b2b267b9f6d2511dc198d'
VERSION = '1.4.3'
TRANSFORMER = 'village/dungeonguard/core/ForcedSpawnTransformer'
ENTRYPOINT = 'village/dungeonguard/DungeonGuard.class'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replace_version_constant(raw):
    """Replace only the shared version UTF8 constant, retaining executable code."""
    assert raw[:8] == b'\xca\xfe\xba\xbe\x00\x00\x00\x34'
    count = struct.unpack_from('>H', raw, 8)[0]
    result, cursor, index, replaced = bytearray(raw[:10]), 10, 1, 0
    fixed = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4,
             10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 18: 4}
    while index < count:
        tag = raw[cursor]
        start = cursor
        cursor += 1
        if tag == 1:
            length = struct.unpack_from('>H', raw, cursor)[0]
            cursor += 2
            value = raw[cursor:cursor + length]
            cursor += length
            if value == b'1.4.2':
                value = VERSION.encode('ascii')
                replaced += 1
            result.extend(bytes([tag]) + struct.pack('>H', len(value)) + value)
        else:
            assert tag in fixed, 'Unexpected Java 8 constant-pool tag'
            cursor += fixed[tag]
            result.extend(raw[start:cursor])
            if tag in (5, 6):
                index += 1
        index += 1
    assert replaced == 1, 'Expected one shared mod version constant'
    result.extend(raw[cursor:])
    return bytes(result)


def run(command, directory, log_name):
    result = subprocess.run(command, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True)
    (directory / log_name).write_text(result.stdout, encoding='utf-8')
    if result.returncode:
        raise RuntimeError('Command failed; retained log: ' + str(directory / log_name)
                           + '\n' + result.stdout[-6000:])
    return result.stdout


def fixture(java, classpath, directory, name, arguments=()):
    log = name.rsplit('.', 1)[-1] + ('-baseline' if arguments == ('--expect-bug',) else '') + '.log'
    text = run([str(java), '-Xverify:all', '-cp', classpath, name, *arguments], directory, log)
    lines = [line for line in text.splitlines() if line.startswith('{')]
    assert lines, 'Missing fixture result: ' + log
    result = json.loads(lines[-1])
    assert result.get('success') is True and result.get('failed') == 0 and result.get('passed', 0) > 0
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-jar', type=Path, required=True)
    parser.add_argument('--asm-jar', type=Path, required=True)
    parser.add_argument('--java8', type=Path, required=True)
    parser.add_argument('--magma-classes', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    base, asm, target = args.base_jar.resolve(), args.asm_jar.resolve(), args.output.resolve()
    assert digest(base) == BASE_SHA256, 'This patch is only for the inspected 1.4.2 bytes'
    assert digest(asm) == ASM_SHA256, 'Expected official ASM 5.2 bytes'
    assert args.magma_classes.is_file(), 'The pinned reconstructed Magma classes are required'
    assert not target.exists(), 'Preserve existing output; choose a new candidate filename'
    suffix = '.exe' if os.name == 'nt' else ''
    java = args.java8.resolve() / 'bin' / ('java' + suffix)
    javac = args.java8.resolve() / 'bin' / ('javac' + suffix)
    build = ROOT / 'build' / 'portal-hotfix'
    build.mkdir(parents=True, exist_ok=True)
    run_dir = Path(tempfile.mkdtemp(prefix='candidate-', dir=build))
    java_version = run([str(java), '-version'], run_dir, 'java-version.log').strip()
    javac_version = run([str(javac), '-version'], run_dir, 'javac-version.log').strip()
    assert 'version "1.8.' in java_version and 'javac 1.8.' in javac_version, 'Java 8 JDK is required'
    source = ROOT / 'src/main/java' / (TRANSFORMER + '.java')
    test_root = ROOT / 'src/transformerTest/java/village/dungeonguard/test'
    tests = [test_root / 'MagmaSpawnTravelChecks.java', test_root / 'MagmaSpawnReleaseChecks.java']
    watched = [source, *tests]
    source_hashes = {str(path.relative_to(ROOT)): digest(path) for path in watched}
    compiled, compiled_tests = run_dir / 'classes', run_dir / 'tests'
    compiled.mkdir(); compiled_tests.mkdir()
    cp = os.pathsep.join(map(str, (asm, base)))
    run([str(javac), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
         '-implicit:none', '-cp', cp, '-d', str(compiled), str(source)], run_dir, 'compile-production.log')
    run([str(javac), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
         '-implicit:none', '-cp', os.pathsep.join(map(str, (compiled, asm, base))),
         '-d', str(compiled_tests), *map(str, tests)], run_dir, 'compile-tests.log')
    baseline = fixture(java, os.pathsep.join(map(str, (compiled_tests, asm, base))), run_dir,
                       'village.dungeonguard.test.MagmaSpawnTravelChecks', ('--expect-bug',))

    replacements = {}
    for path in compiled.rglob('*.class'):
        name = path.relative_to(compiled).as_posix()
        assert name == TRANSFORMER + '.class' or name.startswith(TRANSFORMER + '$'), name
        replacements[name] = path.read_bytes()
    assert TRANSFORMER + '.class' in replacements
    changed_entries, unchanged_entries = [], []
    with zipfile.ZipFile(base) as original:
        assert original.testzip() is None
        assert set(replacements) <= set(original.namelist()), 'Unexpected new runtime helper classes'
        replacements[ENTRYPOINT] = replace_version_constant(original.read(ENTRYPOINT))
        manifest = original.read('META-INF/MANIFEST.MF').decode('utf-8')
        assert manifest.count('Implementation-Version: 1.4.2') == 1
        replacements['META-INF/MANIFEST.MF'] = manifest.replace(
            'Implementation-Version: 1.4.2', 'Implementation-Version: ' + VERSION).encode('utf-8')
        metadata = json.loads(original.read('mcmod.info'))
        assert len(metadata) == 1 and metadata[0]['version'] == '1.4.2'
        metadata[0]['version'] = VERSION
        replacements['mcmod.info'] = (json.dumps(metadata, indent=2) + '\n').encode('utf-8')
        for path in watched:
            assert digest(path) == source_hashes[str(path.relative_to(ROOT))], 'SOURCE_CHANGED; keep incomplete candidate'
        target.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(target, 'x') as output:
            for info in original.infolist():
                before = original.read(info.filename)
                after = replacements.get(info.filename, before)
                output.writestr(info, after)
                (changed_entries if before != after else unchanged_entries).append(info.filename)
    spec = importlib.util.spec_from_file_location('verify_artifact', ROOT / 'tools/verify_artifact.py')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    artifact = verifier.verify(target)
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(target) as output:
        assert original.namelist() == output.namelist(), 'Archive membership changed'
        for name in unchanged_entries:
            assert original.read(name) == output.read(name), name
    final_cp = os.pathsep.join(map(str, (compiled_tests, target, asm)))
    candidate = fixture(java, final_cp, run_dir, 'village.dungeonguard.test.MagmaSpawnTravelChecks')
    magma = fixture(java, final_cp, run_dir, 'village.dungeonguard.test.MagmaSpawnReleaseChecks',
                    (str(args.magma_classes.resolve()),))
    assert digest(base) == BASE_SHA256, 'Input artifact changed'
    for path in watched:
        assert digest(path) == source_hashes[str(path.relative_to(ROOT))], 'SOURCE_CHANGED after verification'
    receipt = {
        'schema': 1, 'version': VERSION, 'date': datetime.now(timezone.utc).isoformat(),
        'artifact': artifact, 'base_sha256': BASE_SHA256, 'asm_sha256': ASM_SHA256,
        'java': java_version, 'javac': javac_version,
        'source_sha256': source_hashes, 'changed_entries': changed_entries,
        'unchanged_entries_verified': len(unchanged_entries),
        'old_bug_reproduction': baseline, 'final_jar_jvm_regressions': candidate,
        'actual_magma_static_checks': magma,
        'all_unrelated_implementation_and_resources_byte_identical': True,
        'full_gradle_build': False, 'live_magma_server_test': False,
        'owner_server_or_world_changed': False,
        'scope': 'Narrow transformer hotfix compiled and executed on Java 8; actual pinned Magma bytecode inspected offline. No live server acceptance.'
    }
    (run_dir / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    receipt_path = target.with_suffix('.verification.json')
    with receipt_path.open('x', encoding='utf-8') as stream:
        json.dump(receipt, stream, indent=2); stream.write('\n')
    print(json.dumps({'artifact': artifact, 'receipt': str(receipt_path),
                      'changed_entries': changed_entries,
                      'baseline_reproduction_checks': baseline['passed'],
                      'final_jar_jvm_checks': candidate['passed'],
                      'actual_magma_static_checks': magma['passed'],
                      'live_server_test': False}, indent=2))


if __name__ == '__main__':
    main()
