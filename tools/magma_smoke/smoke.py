"""Stage a fresh flat-world Magma candidate, then observe bounded hidden boots.

Private receipts/logs stay in the candidate. This never copies recovered worlds,
credentials, global NPC scripts, archived startup scripts or plugin configs.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import zipfile

from tools.server_intake.intake import pack, save
from tools import run_native_checks as native

MAGMA = 'Magma-1.12.2-8cc35ed-server.jar'
MAGMA_SHA256 = '2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce'
MARKER = '.hidden-block-village-disposable-magma-smoke'
HOST, PORT, LEVEL = '127.0.0.1', 25579, 'magma-smoke-flat'
ENHANCED_PORT = 25580
CORE_SHA256 = 'a0f0374d160c99648fb29dbaf5b2c58da8898f3d8758cb61ba7628688da10609'
OLD_PICTURES = 'mods/Naruto-Dungeon-Pictures-1.12.2-1.0.0.jar'
ALLOWED_HOSTS = {'repo.maven.apache.org', 'hub.spigotmc.org', 'libraries.minecraft.net', 'piston-data.mojang.com'}
PROPERTIES = {'level-name': LEVEL, 'level-type': 'FLAT', 'generator-settings': '3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;',
              'server-ip': HOST, 'server-port': str(PORT), 'online-mode': 'false', 'enable-rcon': 'false', 'enable-query': 'false',
              'max-players': '1', 'view-distance': '3', 'max-tick-time': '180000', 'spawn-protection': '0',
              'difficulty': '1', 'motd': 'Private Magma flat-world compatibility smoke'}


class SmokeError(ValueError):
    pass


def require(ok, message):
    if not ok:
        raise SmokeError(message)


def utc():
    return datetime.now(timezone.utc).isoformat()


def properties(text):
    result = {}
    for line in text.splitlines():
        if line and not line.startswith(('#', '!')):
            require('=' in line, 'Unsupported properties line')
            key, value = line.split('=', 1)
            require(key not in result, 'Duplicate property key')
            result[key] = value
    return result


def validate_profile(candidate):
    candidate = pack.no_links(candidate)
    require((candidate / MARKER).read_text(encoding='ascii').strip() == 'DISPOSABLE_FLAT_WORLD_ONLY', 'Disposable marker missing')
    config = properties((candidate / 'server.properties').read_text(encoding='ascii'))
    enhanced_file = candidate / 'PRIVATE-MAGMA-ENHANCEMENT.json'
    expected = dict(PROPERTIES)
    if enhanced_file.exists():
        enhanced = json.loads(enhanced_file.read_text(encoding='utf-8'))
        require(enhanced.get('status') == 'STAGED_ENHANCED_NOT_STARTED', 'Unverified enhancement manifest')
        expected['server-port'] = str(ENHANCED_PORT)
        for artifact in enhanced['added']:
            require(pack.hash_file(candidate / artifact['path'])['sha256'] == artifact['sha256'], 'Enhanced artifact changed')
        require(not (candidate / OLD_PICTURES).exists(), 'Duplicate hvdungeonpictures owner is forbidden')
        for removed in enhanced['removed_from_active']:
            require(pack.hash_file(candidate / removed['retained_path'])['sha256'] == removed['sha256'], 'Retained original artifact changed')
        cfg = (candidate / 'config/valeorders.cfg').read_text(encoding='utf-8')
        require(re.findall(r'B:canonicalEquipmentCatalog\s*=\s*(true|false)', cfg) == ['false'], 'Legacy equipment compatibility setting changed')
    for key in ('level-name', 'level-type', 'server-ip', 'server-port', 'online-mode', 'enable-rcon', 'enable-query'):
        require(config.get(key) == expected[key], 'Unsafe or unexpected local property: ' + key)
    require(not (candidate / 'Naruto.test').exists(), 'Recovered world must not be present')
    require(not (candidate / 'customnpcs/scripts').exists(), 'Archived global NPC scripts must not be present')
    require(pack.hash_file(candidate / MAGMA)['sha256'] == MAGMA_SHA256, 'Magma identity changed')
    return candidate


def java_command(java_home, candidate):
    java_home = pack.no_links(java_home)
    release = properties((java_home / 'release').read_text(encoding='utf-8'))
    require(release.get('JAVA_VERSION', '').strip('"').startswith('1.8.'), 'Windows Java 8 required')
    require(release.get('OS_NAME', '').strip('"') == 'Windows', 'Archived Linux Java is forbidden')
    executable = java_home / 'bin/java.exe'
    with pack.stable_reader(executable) as stream:
        require(stream.read(2) == b'MZ', 'Windows PE Java binary required')
    return [str(executable), '-Xms512M', '-Xmx2G', '-Djava.awt.headless=true', '-jar', str(candidate / MAGMA), 'nogui']


def copy_verified(source, destination, expected=None, sha1=None):
    destination = pack.no_links(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with pack.stable_reader(source) as reader, destination.open('xb') as writer:
        identity = pack.hash_stream(reader, writer=writer, max_bytes=256 * 1024**2)
    require(expected is None or identity['sha256'] == expected, 'Staged source hash differs')
    require(pack.hash_file(destination) == identity, 'Staged readback differs')
    if sha1:
        with pack.stable_reader(destination) as stream:
            digest = hashlib.sha1(stream.read()).hexdigest()
        require(digest == sha1, 'Published SHA-1 differs')
    return identity


def approved_download(url, destination, sha1):
    parsed = urllib.parse.urlparse(url)
    require(parsed.scheme == 'https' and parsed.hostname in ALLOWED_HOSTS and not parsed.username and not parsed.password, 'Unapproved download endpoint')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=30) as response:
        final = urllib.parse.urlparse(response.geturl())
        require(final.scheme == 'https' and final.hostname in ALLOWED_HOSTS and not final.username and not final.password, 'Unapproved redirect endpoint')
        data = response.read(64 * 1024**2 + 1)
    require(len(data) <= 64 * 1024**2 and hashlib.sha1(data).hexdigest() == sha1, 'Download size/hash differs')
    with destination.open('xb') as stream:
        stream.write(data)
    require(zipfile.is_zipfile(destination), 'Dependency is not a JAR/ZIP')
    return pack.hash_file(destination)


def stage(recovery, destination, dependency_plan):
    recovery, destination = pack.no_links(recovery), pack.no_links(destination)
    require(not destination.exists(), 'Candidate must be new')
    require(recovery not in destination.parents and destination not in recovery.parents, 'Candidate and recovery must not overlap')
    receipt = json.loads((recovery / 'PRIVATE-SERVER-INTAKE.json').read_text(encoding='utf-8'))
    inventory = json.loads((recovery / 'PRIVATE-STATIC-INVENTORY.json').read_text(encoding='utf-8'))
    require(receipt['crc_verified'] and receipt['output_readback_verified'], 'Verified source recovery required')
    source_root = recovery / 'server'
    omissions = {item['path'] for item in inventory['jars'] if item['category'] == 'mods' and item['declared_client_only']}
    result = {'schema': 1, 'scope': 'PRIVATE_DISPOSABLE_MAGMA_BASELINE', 'started_utc': utc(), 'archive_sha256': receipt['archive_sha256'],
              'magma_sha256': MAGMA_SHA256, 'omissions': sorted(omissions), 'staged': [], 'dependencies': [],
              'recovered_world_copied': False, 'archived_global_npc_scripts_copied': False, 'archived_credentials_copied': False,
              'config_case_collision_policy': 'Neither root plugin collision payload staged; owner unresolved and no original plugin configs are used.',
              'exploded_classes_staged': False, 'server_started': False}
    destination.mkdir(parents=True)
    try:
        for row in receipt['rows']:
            if row['kind'] != 'file' or row['classification'] != 'primary':
                continue
            path = row['path']
            chosen = (path in (MAGMA, 'forge-1.12.2-14.23.5.2859.jar', 'magma_libs.json', 'binpatches.pack.lzma', 'deobfuscation_data-1.12.2.lzma')
                      or path.startswith(('libraries/', 'mappings/'))
                      or (path.startswith('mods/') and path.endswith('.jar') and path not in omissions)
                      or path == 'plugins/CrazyCrates-1.10.2.2.jar')
            if chosen:
                identity = copy_verified(recovery / row['destination'], destination / path, row['sha256'])
                result['staged'].append(dict(path=path, **identity))
        for dependency in dependency_plan:
            target = pack.safe_relative(dependency['target'])
            require(target.startswith('libraries/') or target == 'minecraft_server.1.12.2.jar', 'Dependency outside runtime classpath')
            require(not (destination / target).exists(), 'Dependency would overwrite an archived payload')
            if 'source_path' in dependency:
                identity = copy_verified(Path(dependency['source_path']), destination / target, sha1=dependency['sha1'])
            else:
                identity = approved_download(dependency['url'], destination / target, dependency['sha1'])
            result['dependencies'].append(dict(dependency, **identity))
        with zipfile.ZipFile(destination / MAGMA) as archive:
            embedded = {item.filename: item for item in archive.infolist()}
            exploded = [row for row in receipt['rows'] if row['path'].endswith('.class') and row['path'].startswith(('org/', 'net/', 'com/'))]
            overlap = [row for row in exploded if row['path'] in embedded]
            identical = sum(hashlib.sha256(archive.read(row['path'])).hexdigest() == row['sha256'] for row in overlap)
            result['static_classpath'] = {'embedded_bukkit_classes': sum(name.startswith('org/bukkit/') and name.endswith('.class') for name in embedded),
                                         'exploded_root_classes': len(exploded), 'overlap_with_magma': len(overlap), 'byte_identical_overlap': identical,
                                         'exploded_classes_not_on_jar_manifest_classpath': True}
        (destination / MARKER).write_text('DISPOSABLE_FLAT_WORLD_ONLY\n', encoding='ascii')
        (destination / 'eula.txt').write_text('eula=true\n', encoding='ascii')
        (destination / 'server.properties').write_text(''.join(key + '=' + value + '\n' for key, value in PROPERTIES.items()), encoding='ascii')
        validate_profile(destination)
        result['status'] = 'STAGED_NOT_STARTED'
        save(destination / 'PRIVATE-MAGMA-STAGE.json', result)
        return result
    except BaseException as error:
        result.update(status='FAILED_PRESERVED', error_type=type(error).__name__, error=str(error))
        save(destination / 'PRIVATE-MAGMA-STAGE.json', result)
        raise


def observe_log(text):
    return {'done': bool(re.search(r'Done \([0-9.,]+s\)!', text)), 'stopping': 'Stopping server' in text,
            'saved': 'Saving chunks' in text or 'All chunks are saved' in text,
            'fatal': any(word in text for word in ('Encountered an unexpected exception', 'Failed to start the minecraft server', 'A problem occurred running the Server launcher'))}


def enhance(recovery, destination, dependency_plan, source_root, build_report, native_report):
    """New candidate only; replace duplicate Standards owner and add tested nine."""
    source_root = pack.no_links(source_root)
    build, build_identity = pack.read_json(build_report)
    qa, native_identity = pack.read_json(native_report)
    require(build.get('passed') is True and all(row.get('status') == 'PASS' for row in build['results'].values()), 'Passing component build required')
    require(qa.get('passed') is True and qa.get('status') == 'PASS' and qa.get('source_build_report') == build_identity, 'Native acceptance must bind this exact build')
    selected = []
    for module, component, mod_id in native.PRODUCTION:
        rows = [row for row in build['results'][module]['artifacts'] if row['role'] == 'runtime_mod' and row.get('component', module) == component]
        require(len(rows) == 1, 'Missing/ambiguous tested server component: ' + component)
        row = rows[0]
        path = pack.no_links(source_root / pack.safe_relative(row['copied_path']))
        require(source_root / 'build/source-builds' in path.parents, 'Artifact outside frozen source-build copies')
        identity = pack.hash_file(path)
        require(identity == {key: row[key] for key in ('bytes', 'sha256')}, 'Frozen built artifact changed')
        require(qa['expected_loaded_mods'].get(mod_id, {}).get('sha256') == row['sha256'], 'Native artifact identity differs: ' + component)
        if component == 'vale-orders':
            require(row['sha256'] == CORE_SHA256, 'Core artifact differs from the requested tested checkpoint')
        selected.append({'component': component, 'mod_id': mod_id, 'source': path, **identity})
    require(len(selected) == 9, 'Exact nine-component server set required')
    stage(recovery, destination, dependency_plan)
    destination = pack.no_links(destination)
    result = {'schema': 1, 'scope': 'PRIVATE_ENHANCED_MAGMA_FRESH_FLAT_ONLY', 'status': 'IN_PROGRESS',
              'source_build_report': build_identity, 'native_report': native_identity, 'native_assertions': qa.get('assertions'),
              'core_sha256': CORE_SHA256, 'added': [], 'removed_from_active': [], 'port': ENHANCED_PORT,
              'canonicalEquipmentCatalog': False, 'recovered_world_copied': False}
    try:
        copy_verified(build_report, destination / 'PRIVATE-SOURCE-BUILD-REPORT.json', build_identity['sha256'])
        copy_verified(native_report, destination / 'PRIVATE-NATIVE-REPORT.json', native_identity['sha256'])
        old = destination / OLD_PICTURES
        identity = pack.hash_file(old)
        retained = 'retained-originals/' + OLD_PICTURES
        target = pack.no_links(destination / retained)
        target.parent.mkdir(parents=True, exist_ok=True)
        old.rename(target)
        result['removed_from_active'].append(dict(path=OLD_PICTURES, retained_path=retained, reason='Same hvdungeonpictures registry owner supplied by tested village-standards', **identity))
        for artifact in selected:
            path = 'mods/' + artifact['source'].name
            identity = copy_verified(artifact['source'], destination / path, artifact['sha256'])
            result['added'].append(dict(path=path, component=artifact['component'], mod_id=artifact['mod_id'], **identity))
        (destination / 'config').mkdir(exist_ok=True)
        (destination / 'config/valeorders.cfg').write_text('adventure {\n    B:canonicalEquipmentCatalog=false\n}\n', encoding='ascii')
        expected = dict(PROPERTIES, **{'server-port': str(ENHANCED_PORT)})
        (destination / 'server.properties').write_text(''.join(key + '=' + value + '\n' for key, value in expected.items()), encoding='ascii')
        result['status'] = 'STAGED_ENHANCED_NOT_STARTED'
        save(destination / 'PRIVATE-MAGMA-ENHANCEMENT.json', result)
        validate_profile(destination)
        return result
    except BaseException as error:
        result.update(status='FAILED_PRESERVED', error_type=type(error).__name__, error=str(error))
        save(destination / 'PRIVATE-MAGMA-ENHANCEMENT.json', result)
        raise


def boot(candidate, java_home, timeout=300, label='boot-01'):
    candidate = validate_profile(candidate)
    require(re.fullmatch(r'[a-z0-9-]+', label) is not None, 'Unsafe boot label')
    log = candidate / (label + '.console.log')
    require(not log.exists(), 'Earlier failure/run log must be preserved')
    port = int(properties((candidate / 'server.properties').read_text(encoding='ascii'))['server-port'])
    with socket.socket() as probe:
        probe.bind((HOST, port))
    command = java_command(java_home, candidate)
    result = {'label': label, 'started_utc': utc(), 'java_sha256': pack.hash_file(Path(command[0]))['sha256'],
              'memory_limit': '2G', 'port': port, 'bind': HOST, 'world': LEVEL, 'command': command,
              'done': False, 'clean_stop': False, 'exit_code': None, 'forced_termination': False}
    began = time.monotonic()
    proc = None
    try:
        with log.open('xb') as stream:
            proc = subprocess.Popen(command, cwd=str(candidate), stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT,
                                    creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            result['pid'] = proc.pid
            print(json.dumps({'phase': 'started', 'label': label, 'pid': proc.pid}), flush=True)
            while proc.poll() is None and time.monotonic() - began < timeout:
                text = log.read_text(encoding='utf-8', errors='replace')
                observed = observe_log(text)
                if observed['done']:
                    result['done'] = True
                    time.sleep(3)
                    proc.stdin.write(b'list\nsave-all\nstop\n')
                    proc.stdin.flush()
                    break
                time.sleep(1)
            if proc.poll() is None and not result['done']:
                proc.stdin.write(b'stop\n')
                proc.stdin.flush()
            try:
                proc.wait(timeout=90)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=20)
                result['forced_termination'] = True
            result['exit_code'] = proc.returncode
    finally:
        if proc is not None and proc.poll() is None:
            proc.kill()
            proc.wait(timeout=20)
            result['forced_termination'] = True
        if proc is not None:
            result['exit_code'] = proc.poll()
        text = log.read_text(encoding='utf-8', errors='replace') if log.exists() else ''
        observed = observe_log(text)
        result['done'] = result['done'] or observed['done']
        result['clean_stop'] = result['done'] and observed['stopping'] and observed['saved'] and result['exit_code'] == 0 and not result['forced_termination']
        result['log_sha256'] = pack.hash_file(log)['sha256'] if log.exists() else None
        result['elapsed_seconds'] = round(time.monotonic() - began, 3)
        result['finished_utc'] = utc()
        save(candidate / (label + '.receipt.json'), result)
    print(json.dumps({key: result[key] for key in ('label', 'done', 'clean_stop', 'exit_code', 'forced_termination', 'elapsed_seconds')}), flush=True)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    staging = sub.add_parser('stage')
    staging.add_argument('--recovery', type=Path, required=True)
    staging.add_argument('--candidate', type=Path, required=True)
    staging.add_argument('--dependencies', type=Path, required=True)
    upgrading = sub.add_parser('enhance')
    upgrading.add_argument('--recovery', type=Path, required=True)
    upgrading.add_argument('--candidate', type=Path, required=True)
    upgrading.add_argument('--dependencies', type=Path, required=True)
    upgrading.add_argument('--source-root', type=Path, required=True)
    upgrading.add_argument('--build-report', type=Path, required=True)
    upgrading.add_argument('--native-report', type=Path, required=True)
    running = sub.add_parser('boot')
    running.add_argument('--candidate', type=Path, required=True)
    running.add_argument('--java-home', type=Path, required=True)
    running.add_argument('--label', required=True)
    running.add_argument('--timeout', type=int, default=300)
    args = parser.parse_args(argv)
    if args.action == 'stage':
        result = stage(args.recovery, args.candidate, json.loads(args.dependencies.read_text(encoding='utf-8')))
        print(json.dumps({'status': result['status'], 'staged': len(result['staged']), 'omitted_client_only': len(result['omissions']), 'dependencies': len(result['dependencies'])}))
        return 0
    if args.action == 'enhance':
        result = enhance(args.recovery, args.candidate, json.loads(args.dependencies.read_text(encoding='utf-8')), args.source_root, args.build_report, args.native_report)
        print(json.dumps({'status': result['status'], 'added': len(result['added']), 'removed_from_active': len(result['removed_from_active']), 'port': result['port']}))
        return 0
    require(1 <= args.timeout <= 600, 'Boot timeout outside 1..600 seconds')
    result = boot(args.candidate, args.java_home, args.timeout, args.label)
    return 0 if result['clean_stop'] else 2
