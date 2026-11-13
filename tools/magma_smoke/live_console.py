"""Guarded hidden live server with private JSON console requests; never starts a client."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time

from tools.server_intake.intake import pack, save
from . import smoke, world_profile as world


def commands(value):
    smoke.require(isinstance(value, dict), 'Console request must be an object')
    result = value.get('commands')
    smoke.require(isinstance(result, list) and 1 <= len(result) <= 32, 'Request needs 1..32 Minecraft command strings')
    for command in result:
        smoke.require(isinstance(command, str) and 0 < len(command) <= 2048 and '\n' not in command and '\r' not in command, 'Single-line Minecraft command required')
        normalized = ' '.join(command.strip().lstrip('/').lower().split())
        smoke.require(normalized not in ('y', 'yes') and not normalized.startswith('fml confirm'), 'Mapping confirmation/discard is forbidden')
    return result


def tail(path):
    with path.open('rb') as stream:
        stream.seek(max(0, path.stat().st_size - 262144))
        return stream.read().decode('utf-8', errors='replace')


def run(candidate, java_home, label):
    candidate = smoke.validate_profile(candidate)
    smoke.require((candidate / smoke.MARKER).read_text().strip() == world.SEALED, 'Live controller requires a reviewed copied world')
    smoke.require(re.fullmatch('[a-z0-9-]+', label) is not None, 'Unsafe live label')
    ready = json.loads((candidate / world.READY).read_text())
    log = candidate / (label + '.console.log')
    queue = candidate / (label + '-requests')
    smoke.require(not log.exists() and not queue.exists(), 'Earlier live session evidence must be preserved')
    queue.mkdir()
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', ready['port']))
    argv = smoke.java_command(java_home, candidate, ready['memory_limit'])
    state_path = candidate / 'PRIVATE-LIVE-CONSOLE-SESSION.json'
    result = {'schema': 1, 'label': label, 'status': 'STARTING', 'started_utc': smoke.utc(), 'controller_pid': os.getpid(),
              'port': ready['port'], 'bind': '127.0.0.1', 'memory_limit': ready['memory_limit'], 'request_directory': str(queue),
              'console_log': str(log), 'done': False, 'clean_stop': False, 'exit_code': None, 'forced_termination': False,
              'client_started_by_controller': False}
    process, handled, began, stop_time = None, set(), time.monotonic(), None
    try:
        with log.open('xb') as output:
            process = subprocess.Popen(argv, cwd=str(candidate), stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                       creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            result['server_pid'] = process.pid
            save(state_path, result)
            while process.poll() is None:
                observed = smoke.observe_log(tail(log))
                if observed['done'] and not result['done']:
                    result.update(done=True, status='RUNNING', ready_utc=smoke.utc())
                    save(state_path, result)
                    print(json.dumps({'status': 'RUNNING', 'server_pid': process.pid, 'port': ready['port']}), flush=True)
                for request in sorted(queue.glob('*.json')):
                    if request.name in handled:
                        continue
                    pack.no_links(request)
                    try:
                        value = json.loads(request.read_text(encoding='utf-8'))
                    except json.JSONDecodeError:
                        continue  # Producer may still be completing an atomic request.
                    handled.add(request.name)
                    try:
                        selected = commands(value)
                        for command in selected:
                            process.stdin.write((command + '\n').encode('utf-8'))
                            if command.strip().lstrip('/').lower() == 'stop':
                                stop_time = time.monotonic()
                                result['status'] = 'STOPPING'
                        process.stdin.flush()
                        save(queue / (request.stem + '.response'), {'status': 'SENT', 'commands': len(selected)})
                    except (ValueError, OSError) as error:
                        save(queue / (request.stem + '.response'), {'status': 'REJECTED', 'error': str(error)})
                if not result['done'] and time.monotonic() - began > 300 and stop_time is None:
                    process.stdin.write(b'stop\n')
                    process.stdin.flush()
                    stop_time = time.monotonic()
                if stop_time is not None and time.monotonic() - stop_time > 90:
                    process.kill()
                    result['forced_termination'] = True
                time.sleep(0.5)
            result['exit_code'] = process.returncode
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(b'stop\n')
                process.stdin.flush()
                process.wait(timeout=90)
            except (OSError, subprocess.TimeoutExpired):
                process.kill()
                process.wait(timeout=20)
                result['forced_termination'] = True
        if process is not None:
            result['exit_code'] = process.poll()
        observed = smoke.observe_log(tail(log)) if log.exists() else {}
        result['clean_stop'] = result['done'] and observed.get('stopping') and observed.get('saved') and result['exit_code'] == 0 and not result['forced_termination']
        result.update(status='STOPPED' if result['clean_stop'] else 'FAILED_PRESERVED', finished_utc=smoke.utc())
        result['log_sha256'] = pack.hash_file(log)['sha256'] if log.exists() else None
        if process is not None:
            world.record_boot(candidate, result)
        save(candidate / (label + '.receipt.json'), result)
        save(state_path, result)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--label', required=True)
    args = parser.parse_args(argv)
    run(args.candidate, args.java_home, args.label)


if __name__ == '__main__':
    main()
