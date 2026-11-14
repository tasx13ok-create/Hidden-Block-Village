"""Apply the owner's near-infinite movement stamina server profile.

The server-only Naruto Stamina plugin maintains food/saturation/exhaustion.
Legacy hunger settings are also reduced where honored; Dodge cost becomes zero.
Jutsu chakra is separate. Servers apply this package at their next start.
"""
import argparse
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import zipfile

VALUES = {
    'jump-walk-exhaustion': '0.00000005',
    'jump-sprint-exhaustion': '0.0000002',
    'combat-exhaustion': '0.0000001',
    'regen-exhaustion': '0.000006',
    'swim-multiplier': '0.00000001',
    'sprint-multiplier': '0.0000001',
    'other-multiplier': '0.0',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def patch(root):
    inputs = {}
    for relative in ('config/valeorders.cfg', 'spigot.yml'):
        path = root / relative
        if not path.is_file() or path.is_symlink():
            raise ValueError('Required regular config missing: ' + str(path))
        inputs[relative] = path.read_bytes()
    core = inputs['config/valeorders.cfg'].decode('utf-8-sig')
    core, count = re.subn(r'(?m)^(\s*I:dodgeChakraCost=)\d+\s*$', r'\g<1>0', core)
    if count != 1:
        raise ValueError('Expected exactly one configured Dodge cost')
    spigot = inputs['spigot.yml'].decode('utf-8-sig')
    block = re.search(r'(?m)^    hunger:\r?\n(?P<values>(?:[ \t]{6}[^\n]*\r?\n)+)', spigot)
    if block is None:
        raise ValueError('Expected the standard default-world hunger block')
    values = block.group('values')
    for key, value in VALUES.items():
        if not Decimal(value).is_finite() or Decimal(value) < 0:
            raise ValueError('Invalid finite stamina setting')
        values, count = re.subn(r'(?m)^([ \t]{6}' + re.escape(key) + r':[ \t]*)[^\r\n]+', lambda match: match.group(1) + value, values)
        if count != 1:
            raise ValueError('Missing/ambiguous native hunger option: ' + key)
    spigot = spigot[:block.start('values')] + values + spigot[block.end('values'):]
    outputs = {'config/valeorders.cfg': core.encode('utf-8'), 'spigot.yml': spigot.encode('utf-8')}
    for relative, data in outputs.items():
        path = root / relative
        path.write_bytes(data)
        if path.read_bytes() != data:
            raise ValueError('Configuration write/readback mismatch')
    return outputs, [{'path': key, 'before_sha256': sha(inputs[key]), 'after_sha256': sha(data)} for key, data in outputs.items()]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server', type=Path, action='append', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--receipt', type=Path, required=True)
    p.add_argument('--plugin', type=Path, required=True)
    p.add_argument('--plugin-sha256', required=True)
    a = p.parse_args()
    if a.output.exists() or a.receipt.exists():
        raise FileExistsError('Preserve existing output')
    plugin = a.plugin.read_bytes()
    if sha(plugin) != a.plugin_sha256 or a.plugin.name != 'Naruto-Stamina-1.0.0.jar':
        raise ValueError('Exact reviewed Naruto Stamina artifact required')
    with zipfile.ZipFile(a.plugin) as z:
        if z.testzip() is not None or b'NarutoStamina' not in z.read('plugin.yml'):
            raise ValueError('Verified Naruto Stamina plugin required')
        if any(int.from_bytes(z.read(n)[6:8], 'big') != 52 for n in z.namelist() if n.endswith('.class')):
            raise ValueError('Plugin must use Java8 bytecode')
    all_changes = []
    primary = None
    for root in a.server:
        payload, changes = patch(root)
        dest = root / 'plugins' / a.plugin.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(plugin)
        if dest.read_bytes() != plugin:
            raise ValueError('Plugin deployment readback failed')
        enabled = root / 'plugins/Naruto-Stamina/config.yml'
        enabled.parent.mkdir(parents=True, exist_ok=True)
        enabled.write_bytes(b'enabled: true\n')
        manifest = root / 'HBV-SERVER-CONTENTS.json'
        if manifest.is_file():
            rows = json.loads(manifest.read_text(encoding='utf-8'))
            by_path = {row['path']: row for row in rows}
            for relative, data in dict(payload, **{'plugins/' + a.plugin.name: plugin, 'plugins/Naruto-Stamina/config.yml': b'enabled: true\n'}).items():
                by_path[relative] = {'path': relative, 'bytes': len(data), 'sha256': sha(data)}
            manifest.write_bytes((json.dumps(list(by_path.values()), indent=2) + '\n').encode('utf-8'))
        if primary is None:
            primary = payload
        all_changes.append({'root_name': root.name, 'files': changes})
    primary['plugins/' + a.plugin.name] = plugin
    primary['plugins/Naruto-Stamina/config.yml'] = b'enabled: true\n'
    notes = b'''HIDDEN BLOCK VILLAGE - FINAL STAMINA SETTINGS
Upload this tiny ZIP after the main server ZIP or all eight browser parts.
Unarchive into the SAME server root: plugins plus two config files.
Start/restart the server to apply. No client reinstall or world reupload needed.

Dodge costs0. The included server plugin maintains hunger/saturation/exhaustion
for practically unlimited movement stamina. Legacy hunger settings are also low.
Jutsu chakra, attack damage and movement recovery timing are unchanged.
'''
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.output, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, data in primary.items():
            z.writestr(name, data)
        z.writestr('FINAL-STAMINA-START-HERE.txt', notes)
    with zipfile.ZipFile(a.output) as z:
        if z.testzip() is not None or any(z.read(name) != data for name, data in primary.items()):
            raise ValueError('Settings ZIP failed CRC/byte readback')
    result = {'schema': 1, 'scope': 'FINAL_NEAR_INFINITE_MOVEMENT_STAMINA_CONFIG',
              'archive': a.output.name, 'bytes': a.output.stat().st_size,
              'sha256': sha(a.output.read_bytes()), 'dodge_cost': 0,
              'native_exhaustion_values': VALUES, 'standard_drain_multiplier': '0.000001',
              'runtime_stamina_enforced_by': a.plugin.name, 'plugin_sha256': sha(plugin),
              'legacy_multiplier_runtime_support_not_assumed': True,
              'jutsu_chakra_changed': False, 'world_or_player_data_written': False,
              'client_reinstall_required': False, 'server_restart_required': True,
              'extract_after_base_server_archives': True, 'crc_and_byte_readback_passed': True,
              'changed_configs': all_changes}
    a.receipt.parent.mkdir(parents=True, exist_ok=True)
    a.receipt.write_bytes((json.dumps(result, indent=2) + '\n').encode('utf-8'))
    print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    main()
