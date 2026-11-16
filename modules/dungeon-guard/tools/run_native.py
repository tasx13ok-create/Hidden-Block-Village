#!/usr/bin/env python3
"""Run private tests in a newly created, loopback-only Forge 1.12.2 server.

Requires an official Forge 14.23.5.2859 installer output as --server-template,
a Java 8 JDK/JRE, and freshly built production/native-test JARs. The script
accepts the Minecraft EULA only in its newly generated disposable test folder.
It never accepts an existing world or live server directory as its output.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
FORGE = 'forge-1.12.2-14.23.5.2859.jar'
FORGE_SHA256 = 'bd8314729d787e35164476d75afb337afb9783917e4f6fa1f77f049adba22f2d'
MINECRAFT = 'minecraft_server.1.12.2.jar'
MINECRAFT_SHA1 = '886945bfb2b978778c3a0288fd7fab09d315b25f'


def digest(path, algorithm='sha256'):
    h = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-template', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--mod', type=Path, required=True)
    parser.add_argument('--tests', type=Path, required=True)
    parser.add_argument('--extra-mod', type=Path, action='append', default=[],
                        help='Explicit upstream mod JAR for an isolated actual-mod fixture; repeat for each JAR.')
    parser.add_argument('--timeout', type=int, default=180)
    parser.add_argument('--result-name', default='native-result.json')
    parser.add_argument('--pass-marker', default='DUNGEON_GUARD_NATIVE_PASS')
    args = parser.parse_args()
    if Path(args.result_name).name != args.result_name:
        parser.error('--result-name must be a filename')
    if not 10 <= args.timeout <= 600:
        parser.error('--timeout must be between 10 and 600 seconds')
    template = args.server_template.resolve()
    assert digest(template / FORGE) == FORGE_SHA256, 'Unexpected Forge bytes'
    assert digest(template / MINECRAFT, 'sha1') == MINECRAFT_SHA1, 'Unexpected Minecraft bytes'
    java = args.java_home.resolve() / 'bin' / ('java.exe' if __import__('os').name == 'nt' else 'java')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
    assert 'version "1.8.' in version.stderr, 'Native tests require Java 8'
    staged_artifacts = [args.mod, args.tests] + args.extra_mod
    for file in staged_artifacts:
        assert file.is_file() and file.suffix == '.jar', 'Missing built artifact: ' + str(file)
    assert len({file.name for file in staged_artifacts}) == len(staged_artifacts), 'Staged mod JAR filenames must be unique'
    artifact_digests = [digest(file) for file in staged_artifacts]
    run = ROOT / 'build' / 'native-runs' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8])
    run.mkdir(parents=True, exist_ok=False)
    shutil.copy2(template / FORGE, run / FORGE)
    shutil.copy2(template / MINECRAFT, run / MINECRAFT)
    shutil.copytree(template / 'libraries', run / 'libraries')
    (run / 'mods').mkdir()
    for file, expected in zip(staged_artifacts, artifact_digests):
        shutil.copy2(file, run / 'mods' / file.name)
        assert digest(run / 'mods' / file.name) == expected, 'Artifact changed while staging: ' + file.name
    (run / 'DUNGEON_GUARD_DISPOSABLE_TEST_WORLD').write_text(
        'Created by Dungeon Guard native-test runner in a fresh disposable directory.\n', encoding='utf-8')
    (run / 'eula.txt').write_text('# Disposable automated test fixture only.\neula=true\n', encoding='utf-8')
    (run / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        'level-name=disposable-test-world\nlevel-type=FLAT\ngenerate-structures=false\n'
        'allow-nether=false\nspawn-protection=0\nview-distance=3\n'
        'max-tick-time=60000\nspawn-monsters=false\nspawn-animals=false\n'
        'motd=Private Dungeon Guard acceptance fixture\n', encoding='utf-8')
    started = time.monotonic()
    timed_out = False
    with (run / 'console.log').open('wb') as log:
        process = subprocess.Popen([str(java), '-Xms256M', '-Xmx1G', '-Dlog4j2.formatMsgNoLookups=true',
                                    '-Ddungeonguard.runNativeChecks=true',
                                    '-jar', FORGE, 'nogui'], cwd=run, stdin=subprocess.PIPE,
                                   stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()
            code = process.wait(timeout=15)
    console = (run / 'console.log').read_text(encoding='utf-8', errors='replace')
    result_path = run / args.result_name
    native_result = json.loads(result_path.read_text(encoding='utf-8')) if result_path.is_file() else None
    passed = (not timed_out and code == 0 and args.pass_marker in console and native_result is not None
              and native_result.get('success') is True
              and type(native_result.get('passed')) is int and native_result['passed'] > 0
              and type(native_result.get('failed')) is int and native_result['failed'] == 0)
    report = {'schema': 1, 'passed': passed, 'run_directory': str(run),
              'minecraft': '1.12.2', 'forge': '14.23.5.2859', 'java_version': version.stderr.strip(),
              'mod': {'filename': args.mod.name, 'sha256': artifact_digests[0]},
              'test_mod': {'filename': args.tests.name, 'sha256': artifact_digests[1]},
              'extra_mods': [{'filename': file.name, 'sha256': expected}
                             for file, expected in zip(args.extra_mod, artifact_digests[2:])],
              'seconds': round(time.monotonic() - started, 3), 'exit_code': code,
              'timed_out': timed_out, 'native_result': native_result,
              'acceptance_scope': 'Fresh local Forge server; not the user\'s Magma modpack or saved map.'}
    (run / 'runner-result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
