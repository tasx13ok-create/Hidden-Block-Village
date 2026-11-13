"""Compile and check the original client GUI adapter without launching Minecraft.

All API/library/private NPC inputs are explicit existing local files. The tests
never package API doubles or private source classes into a gameplay artifact.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def identity(path):
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def run_checks(args):
    java, javac = args.java8_home / 'bin/java.exe', args.java8_home / 'bin/javac.exe'
    libraries = [args.asm_jar, args.forge_jar, args.launchwrapper_jar, args.guava_jar]
    for path in [java, javac, args.api_jar, args.npc_jar] + libraries:
        if not path.is_file() or path.is_symlink():
            raise ValueError('An explicit regular local compile/runtime input is required: ' + str(path))
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output.resolve()
    classes, checks, dispatch = [output / name for name in ('production-classes', 'bytecode-checks', 'dispatch-checks')]
    for path in (classes, checks, dispatch):
        path.mkdir()
    source = sorted((ROOT / 'modules/vale-compatibility-helpers/src/village/vale/clientcompat').glob('*.java'))
    before = [identity(path) for path in source]
    fixture = ROOT / 'tests/gui_thread_compat'
    commands = []

    def cp(paths):
        return os.pathsep.join(map(str, paths))

    def invoke(argv):
        result = subprocess.run(list(map(str, argv)), cwd=ROOT, capture_output=True, text=True, timeout=120)
        commands.append({'argv': list(map(str, argv)), 'exit_code': result.returncode,
                         'stdout': result.stdout, 'stderr': result.stderr})
        (output / 'PRIVATE-COMMANDS.json').write_text(json.dumps(commands, indent=2) + '\n', encoding='utf8')
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        print(result.stdout.strip())
        return result.stdout + result.stderr

    invoke([javac, '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-cp', cp([args.api_jar] + libraries), '-d', classes] + source)
    invoke([javac, '-source', '8', '-target', '8', '-cp', cp([classes] + libraries), '-d', checks,
            fixture / 'checks/village/vale/clientcompat/GuiThreadDraftChecks.java'])
    unsupported = output / 'unsupported-original-fixture.jar'
    with zipfile.ZipFile(unsupported, 'w') as archive:
        archive.writestr('original-test-only.txt', b'Not the supported source JAR')
    bytecode_result = invoke([java, '-Xverify:all', '-cp', cp([checks, classes] + libraries),
                             'village.vale.clientcompat.GuiThreadDraftChecks', args.npc_jar, unsupported])
    invoke([javac, '-source', '8', '-target', '8', '-cp', cp([classes] + libraries), '-d', dispatch]
           + sorted((fixture / 'api').rglob('*.java'))
           + [fixture / 'checks/village/vale/clientcompat/GuiThreadDispatchChecks.java'])
    dispatch_result = invoke([java, '-Xverify:all', '-cp', cp([dispatch, classes] + libraries),
                             'village.vale.clientcompat.GuiThreadDispatchChecks',
                             dispatch / 'micdoodle8/mods/galacticraft/api/client/tabs/InventoryTabQuests.class',
                             dispatch / 'micdoodle8/mods/galacticraft/api/client/tabs/InventoryTabFactions.class'])
    bytecode_count = re.search(r'GUI_THREAD_DRAFT_BYTECODE_PASS assertions=(\d+)', bytecode_result)
    dispatch_count = re.search(r'GUI_THREAD_DRAFT_DISPATCH_PASS assertions=(\d+)', dispatch_result)
    if not bytecode_count or not dispatch_count:
        raise RuntimeError('Standalone GUI check completion markers missing')
    after = [identity(path) for path in source]
    if before != after:
        raise RuntimeError('Production source changed during standalone GUI checks')
    receipt = {'scope': 'PRIVATE_ORIGINAL_CLIENT_LIFECYCLE_GUI_STANDALONE_CHECKS', 'passed': True,
               'production_source_files': after, 'inputs': [identity(path) for path in [java, javac, args.api_jar, args.npc_jar] + libraries],
               'commands': commands, 'bytecode_source_assertions': int(bytecode_count.group(1)), 'dispatch_integration_assertions': int(dispatch_count.group(1)),
               'minecraft_launched': False, 'third_party_jars_modified': False, 'manual_gui_acceptance': False}
    (output / 'PRIVATE-RECEIPT.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('java8-home', 'api-jar', 'forge-jar', 'asm-jar', 'launchwrapper-jar', 'guava-jar', 'npc-jar', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    result = run_checks(args)
    print(json.dumps({key: result[key] for key in ('passed', 'bytecode_source_assertions', 'dispatch_integration_assertions', 'minecraft_launched', 'manual_gui_acceptance')}))


if __name__ == '__main__':
    main()
