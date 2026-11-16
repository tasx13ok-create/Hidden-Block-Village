"""Package one current CLIENT mods/ folder from a checked sealed client archive.

The optional newly built Core must differ only in the explicitly reviewed client
renderer. Server gameplay, original mod JARs, configs and resource packs are not
changed. Generated runtime archives stay private and outside the repository.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client-archive', required=True, type=Path)
    p.add_argument('--client-receipt', required=True, type=Path)
    p.add_argument('--core', required=True, type=Path)
    p.add_argument('--core-build-report', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    p.add_argument('--receipt', required=True, type=Path)
    a = p.parse_args()
    if a.output.exists() or a.receipt.exists():
        raise FileExistsError('Preserve existing outputs')
    receipt = json.loads(a.client_receipt.read_text(encoding='utf-8-sig'))
    source = a.client_archive.read_bytes()
    if (len(source) != receipt['bytes'] or sha(source) != receipt['sha256']
            or not receipt['crc_and_byte_readback_passed']):
        raise ValueError('Exact checked sealed client ZIP required')
    report = json.loads(a.core_build_report.read_text(encoding='utf-8-sig'))
    core_result = report['results']['vale-orders']
    if (not report['passed'] or report['source_changed_during_build']
            or core_result['status'] != 'PASS' or core_result['version'] != '0.3.16'):
        raise ValueError('Completed exact-version Core build required')
    declared = [r for r in core_result['artifacts'] if r['role'] == 'runtime_mod']
    core = a.core.read_bytes()
    if len(declared) != 1 or declared[0]['sha256'] != sha(core):
        raise ValueError('Core must match the successful build runtime artifact')
    members = {}
    with zipfile.ZipFile(io.BytesIO(source)) as z:
        if len(z.namelist()) != len(set(z.namelist())):
            raise ValueError('Duplicate client ZIP entries')
        for name in z.namelist():
            if name.startswith('overrides/mods/') and name.endswith('.jar'):
                relative = name[len('overrides/'):]
                if len(Path(relative).parts) != 2 or ':' in relative or '\\' in relative:
                    raise ValueError('Only direct client mod JARs are allowed')
                members[relative] = z.read(name)
    if len(members) != 58 or 'mods/Naruto-HUD-0.20.1.jar' not in members:
        raise ValueError('Current complete 58-JAR client with HUD0.20.1 required')
    core_name = 'mods/Naruto-Core-0.3.16.jar'
    prior = members[core_name]
    with zipfile.ZipFile(io.BytesIO(prior)) as before, zipfile.ZipFile(io.BytesIO(core)) as after:
        if set(before.namelist()) != set(after.namelist()):
            raise ValueError('Core runtime inventory changed')
        changed = sorted(n for n in before.namelist() if before.read(n) != after.read(n))
        if not changed or any(not n.startswith('village/vale/client/ArmorCoreFx')
                              or not n.endswith('.class') for n in changed):
            raise ValueError('Only the reviewed chest-renderer correction may differ')
    members[core_name] = core
    for name, raw in members.items():
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            if z.testzip() is not None:
                raise ValueError('Invalid nested JAR: ' + name)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.output, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=1) as z:
        for name, raw in sorted(members.items()):
            z.writestr(name, raw)
    with zipfile.ZipFile(a.output) as z:
        if len(z.namelist()) != 58 or set(z.namelist()) != set(members):
            raise ValueError('Client mods replacement inventory mismatch')
        for name, raw in members.items():
            if sha(z.read(name)) != sha(raw):
                raise ValueError('Client mods replacement byte/CRC readback failed')
    output = a.output.read_bytes()
    value = {
        'schema': 1, 'scope': 'SINGLE_CLIENT_MODS_FOLDER_REPLACEMENT',
        'file': a.output.name, 'bytes': len(output), 'sha256': sha(output),
        'mods': 58, 'hud_version': '0.20.1', 'core_version': '0.3.16',
        'core_sha256': sha(core), 'previous_core_sha256': sha(prior),
        'core_changed_entries': changed,
        'all_nonclient_core_bytes_identical': True,
        'base_client_archive_sha256': receipt['sha256'],
        'core_build_report_sha256': sha(a.core_build_report.read_bytes()),
        'all_other_mod_jars_unchanged': True,
        'all_nested_jar_and_zip_crc_sha256_checks_passed': True,
        'client_profile_or_live_session_modified': False,
        'resource_pack_or_options_modified': False,
        'host_server_update_required_for_renderer_only_delta': False,
        'fresh_graphical_or_hosted_acceptance_claimed': False,
    }
    a.receipt.parent.mkdir(parents=True, exist_ok=True)
    a.receipt.write_bytes((json.dumps(value, indent=2) + '\n').encode('utf-8'))
    print(json.dumps(value, indent=2))


if __name__ == '__main__':
    main()
