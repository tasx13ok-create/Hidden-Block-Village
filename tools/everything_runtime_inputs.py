"""Fail-closed review of future0314 frozen inputs. Does not stage or launch a game."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO))
from tools.server_intake.intake import pack
from tools.server_intake.inventory import jar_identity

SERVER = {'vale-orders': 'valeorders', 'vale-encounters': 'valeencounters', 'vale-rack': 'valerack',
          'vale-residents': 'valeresidents', 'vale-residents-server-bridge': 'residentserverbridge',
          'vale-world-compat': 'valeworldcompat', 'damage-compat': 'valedamagecompat',
          'server-bridge': 'valeserverbridge', 'village-standards': 'hvdungeonpictures'}
SHARED = {'vale-orders', 'vale-encounters', 'vale-rack', 'vale-residents', 'vale-world-compat', 'village-standards'}
GRAPHICS = {'field-hud', 'field-controls', 'client-lifecycle'}


def need(ok, message):
    if not ok:
        raise ValueError(message)


def promoted_gui_patch(path):
    with zipfile.ZipFile(path) as archive:
        prefix = 'village/vale/clientcompat/'
        required = ['NpcGuiThreadTransformer', 'NpcGuiThreadDispatch', 'SupportedNpcSource', 'ClientLifecyclePlugin']
        need(all(prefix + name + '.class' in archive.namelist() for name in required), 'Whole-Runnable GUI compatibility patch is not promoted')
        # The visitor's exact invocation matcher lives in javac's anonymous inner classes.
        family = [name for name in archive.namelist() if name == prefix + 'NpcGuiThreadTransformer.class'
                  or name.startswith(prefix + 'NpcGuiThreadTransformer$') and name.endswith('.class')]
        transformer = b''.join(archive.read(name) for name in family)
        helper = archive.read(prefix + 'NpcGuiThreadDispatch.class')
        plugin = archive.read(prefix + 'ClientLifecyclePlugin.class')
        need(all(token in transformer for token in (b'onTabClicked', b'runTack', b'(Ljava/lang/Runnable;)V')), 'Unsupported GUI hook ABI')
        need(b'village.vale.clientcompat.NpcGuiThreadTransformer' in plugin, 'GUI transformer not registered in plugin')
        need(b'net/minecraft/client/Minecraft' in helper and (b'addScheduledTask' in helper or b'func_152344_a' in helper), 'GUI dispatch helper has no supported client task ABI')
    return {'whole_runnable_classes_present': True, 'plugin_registration_present': True,
            'runtime_hook_and_manual_gui_acceptance': False}


def validate(frozen_root, build_path, native_path, *, source_pin, build_pin, native_pin):
    """All pins must come from root's approved new build; no latest-output inference."""
    frozen_root, build_path, native_path = map(pack.no_links, (frozen_root, build_path, native_path))
    build, bid = pack.read_json(build_path)
    native, nid = pack.read_json(native_path)
    need(bid['sha256'] == build_pin and nid['sha256'] == native_pin, 'New approved receipt hashes are required')
    need(build.get('passed') is True and not build.get('source_changed_during_build')
         and all(row.get('status') == 'PASS' for row in build['results'].values()), 'Stable complete passing source build required')
    need(build['source_snapshot']['sha256'] == source_pin and native['source_snapshot']['sha256'] == source_pin, 'New source/native snapshots differ')
    need(native.get('passed') is True and native.get('status') == 'PASS' and native.get('assertions', 0) > 0
         and native['source_build_report'] == bid, 'Native report does not bind the exact passing build')
    need(set(native['production_mod_ids']) == set(SERVER.values()), 'Unexpected tested server component roster')
    rows = {}
    for module, result in build['results'].items():
        for row in result['artifacts']:
            if row['role'] != 'runtime_mod':
                continue
            component = row.get('component', module)
            need(component in set(SERVER) | GRAPHICS and component not in rows, 'Unknown/duplicate runtime component')
            path = pack.no_links(frozen_root / pack.safe_relative(row['copied_path']))
            need(frozen_root / 'build/source-builds' in path.parents, 'Runtime provider is outside frozen artifact copies')
            actual = pack.hash_file(path)
            need(actual == {key: row[key] for key in ('bytes', 'sha256')}, 'Frozen runtime bytes changed')
            if component in SERVER:
                need(native['expected_loaded_mods'][SERVER[component]]['sha256'] == row['sha256'], 'Runtime differs from actual native loaded proof')
            rows[component] = dict(provider=str(path), file=path.name, **actual)
    need(set(rows) == set(SERVER) | GRAPHICS, 'Need all nine server components and three graphical adapters')
    core = jar_identity(Path(rows['vale-orders']['provider']), rows['vale-orders']['file'])
    need(not core['warnings'] and any(d['modid'] == 'valeorders' and d.get('version') == '0.3.14' for d in core['mod_declarations']), 'Everything requires new Core0.3.14, not a relabeled prior build')
    patch = promoted_gui_patch(Path(rows['client-lifecycle']['provider']))
    return {'status': 'PINNED0314_INPUTS_REVIEWED_NOT_STAGED_NOT_LAUNCHED', 'source_snapshot_sha256': source_pin,
            'source_build_report': bid, 'native_report': nid, 'native_assertions': native['assertions'],
            'runtime_components': rows, 'server_components': sorted(SERVER),
            'client_components': sorted(SHARED | GRAPHICS), 'gui_patch': patch,
            'required_client_InventoryGuiEnabled': True, 'minecraft_launched': False,
            'saved0313_world_requires_exact0314_registry_preflight': True}
