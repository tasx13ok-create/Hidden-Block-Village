"""Real staging fixtures and guardrails; no server execution in the unit suite."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.server_intake.intake import recover
from tools.magma_smoke import smoke
from tools.magma_smoke import world_profile as world
from tools.magma_smoke import release_profile as release
from tools.magma_smoke import all_mods_client as graphical
from tools.magma_smoke import live_console


def jar(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as archive:
        for name, data in entries:
            archive.writestr(name, data)
    return output.getvalue()


class MagmaSmokeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.magma = jar([('org/bukkit/Bukkit.class', b'fixture class metadata')])
        self.identity = hashlib.sha256(self.magma).hexdigest()
        self.pin = patch.object(smoke, 'MAGMA_SHA256', self.identity)
        self.pin.start()
        self.addCleanup(self.pin.stop)

    def fixture_recovery(self, include_script=True):
        source = self.root / 'server.zip'
        with zipfile.ZipFile(source, 'w') as archive:
            for name, data in ((smoke.MAGMA, self.magma), ('mods/owner.jar', jar([])), ('mods/client.jar', jar([])),
                               ('plugins/CrazyCrates-1.10.2.2.jar', jar([])), ('libraries/example/runtime.jar', jar([])), (smoke.OLD_PICTURES, jar([])),
                               ('server.properties', b'rcon.password=private-original\n'), ('ops.json', b'private-player'),
                               ('Naruto.test/level.dat', b'private-world'), ('customnpcs/scripts/unsafe.js', b'throw dangerous()'),
                               ('plugins/messages.yml', b'private config a'), ('plugins/Messages.yml', b'private config b'),
                               ('org/bukkit/Bukkit.class', b'fixture class metadata')):
                if include_script or name != 'customnpcs/scripts/unsafe.js':
                    archive.writestr(name, data)
        root = self.root / 'recovery'
        recover(source, root)
        inventory = {'jars': [{'path': 'mods/owner.jar', 'category': 'mods', 'declared_client_only': False},
                              {'path': 'mods/client.jar', 'category': 'mods', 'declared_client_only': True}]}
        (root / 'PRIVATE-STATIC-INVENTORY.json').write_text(json.dumps(inventory))
        return root

    def test_staging_preserves_registry_owner_and_omits_world_secrets_scripts_and_configs(self):
        root = self.fixture_recovery()
        candidate = self.root / 'candidate'
        report = smoke.stage(root, candidate, [])
        self.assertTrue((candidate / 'mods/owner.jar').is_file())
        self.assertFalse((candidate / 'mods/client.jar').exists())
        self.assertTrue((candidate / 'plugins/CrazyCrates-1.10.2.2.jar').is_file())
        for path in ('Naruto.test', 'customnpcs/scripts', 'ops.json', 'plugins/messages.yml', 'plugins/Messages.yml', 'org'):
            self.assertFalse((candidate / path).exists())
        self.assertNotIn('private-original', (candidate / 'server.properties').read_text())
        self.assertEqual(report['static_classpath']['byte_identical_overlap'], 1)
        self.assertEqual(smoke.validate_profile(candidate), candidate)
        self.assertEqual((root / 'server/server.properties').read_bytes(), b'rcon.password=private-original\n')

    def test_existing_candidate_never_overwritten(self):
        root = self.fixture_recovery()
        candidate = self.root / 'existing'
        candidate.mkdir()
        (candidate / 'keep').write_bytes(b'original')
        with self.assertRaises(ValueError):
            smoke.stage(root, candidate, [])
        self.assertEqual((candidate / 'keep').read_bytes(), b'original')

    def test_changed_source_is_a_preserved_failed_stage(self):
        root = self.fixture_recovery()
        (root / 'server/mods/owner.jar').write_bytes(b'changed')
        candidate = self.root / 'candidate'
        with self.assertRaises(ValueError):
            smoke.stage(root, candidate, [])
        report = json.loads((candidate / 'PRIVATE-MAGMA-STAGE.json').read_text())
        self.assertEqual(report['status'], 'FAILED_PRESERVED')
        self.assertFalse(report['server_started'])

    def test_dependency_pin_is_checked_and_cannot_overwrite_archived_payload(self):
        root = self.fixture_recovery()
        dep = self.root / 'dependency.jar'
        dep.write_bytes(jar([]))
        plan = [{'target': 'minecraft_server.1.12.2.jar', 'source_path': str(dep), 'sha1': '0' * 40}]
        with self.assertRaises(ValueError):
            smoke.stage(root, self.root / 'bad-hash', plan)
        plan[0]['target'] = 'libraries/example/runtime.jar'
        with self.assertRaises(ValueError):
            smoke.stage(root, self.root / 'overwrite', plan)
        plan[0]['target'] = '../outside'
        with self.assertRaises((ValueError, smoke.pack.PackError)):
            smoke.stage(root, self.root / 'traversal', plan)

    def test_profile_guard_rejects_network_or_world_changes(self):
        root = self.fixture_recovery()
        candidate = self.root / 'candidate'
        smoke.stage(root, candidate, [])
        original = (candidate / 'server.properties').read_text()
        for old, new in (('server-ip=127.0.0.1', 'server-ip=0.0.0.0'), ('server-port=25579', 'server-port=25565'),
                         ('enable-rcon=false', 'enable-rcon=true'), ('level-name=magma-smoke-flat', 'level-name=Naruto.test')):
            (candidate / 'server.properties').write_text(original.replace(old, new))
            with self.assertRaises(ValueError):
                smoke.validate_profile(candidate)
        (candidate / 'server.properties').write_text(original)
        (candidate / 'Naruto.test').mkdir()
        with self.assertRaises(ValueError):
            smoke.validate_profile(candidate)

    def test_only_windows_java8_and_fixed_memory_command_are_accepted(self):
        java = self.root / 'java8'
        (java / 'bin').mkdir(parents=True)
        (java / 'bin/java.exe').write_bytes(b'MZfixture-no-execution')
        (java / 'release').write_text('JAVA_VERSION="1.8.0_512"\nOS_NAME="Windows"\n')
        command = smoke.java_command(java, self.root / 'candidate')
        self.assertIn('-Xmx2G', command)
        self.assertIn('-Xms512M', command)
        self.assertEqual(command[-1], 'nogui')
        (java / 'release').write_text('JAVA_VERSION="1.8.0_252"\nOS_NAME="Linux"\n')
        with self.assertRaises(ValueError):
            smoke.java_command(java, self.root / 'candidate')
        (java / 'release').write_text('JAVA_VERSION="21.0.1"\nOS_NAME="Windows"\n')
        with self.assertRaises(ValueError):
            smoke.java_command(java, self.root / 'candidate')

    def test_unapproved_downloads_are_rejected_without_network(self):
        for url in ('file:///C:/secret', 'http://repo.maven.apache.org/a.jar', 'https://evil.example/a.jar', 'https://user:password@repo.maven.apache.org/a.jar'):
            with patch.object(smoke.urllib.request, 'urlopen') as request:
                with self.assertRaises(ValueError):
                    smoke.approved_download(url, self.root / 'out.jar', '0' * 40)
                request.assert_not_called()

    def fixture_build(self):
        root = self.root / 'source'
        build, expected, core = {'passed': True, 'results': {}}, {}, None
        for module, component, modid in smoke.native.PRODUCTION:
            path = root / ('build/source-builds/fixture/' + component + '.jar')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(jar([('fixture.txt', component.encode())]))
            identity = smoke.pack.hash_file(path)
            row = dict(role='runtime_mod', component=component, copied_path=path.relative_to(root).as_posix(), **identity)
            build['results'].setdefault(module, {'status': 'PASS', 'artifacts': []})['artifacts'].append(row)
            expected[modid] = identity
            if component == 'vale-orders':
                core = identity['sha256']
        build_path, native_path = root / 'build.json', root / 'native.json'
        build_path.write_text(json.dumps(build))
        qa = {'passed': True, 'status': 'PASS', 'source_build_report': smoke.pack.hash_file(build_path), 'expected_loaded_mods': expected, 'assertions': 1704}
        native_path.write_text(json.dumps(qa))
        return root, build_path, native_path, core

    def test_enhanced_candidate_uses_nine_bound_artifacts_and_retains_duplicate_owner(self):
        recovery = self.fixture_recovery()
        root, build, qa, core = self.fixture_build()
        candidate = self.root / 'enhanced'
        with patch.object(smoke, 'CORE_SHA256', core):
            report = smoke.enhance(recovery, candidate, [], root, build, qa)
        self.assertEqual(len(report['added']), 9)
        self.assertEqual(smoke.pack.hash_file(candidate / 'PRIVATE-SOURCE-BUILD-REPORT.json'), report['source_build_report'])
        self.assertEqual(smoke.pack.hash_file(candidate / 'PRIVATE-NATIVE-REPORT.json'), report['native_report'])
        self.assertEqual(len(report['removed_from_active']), 1)
        self.assertFalse((candidate / smoke.OLD_PICTURES).exists())
        self.assertTrue((candidate / ('retained-originals/' + smoke.OLD_PICTURES)).exists())
        self.assertEqual(smoke.properties((candidate / 'server.properties').read_text())['server-port'], '25580')
        with patch.object(smoke, 'CORE_SHA256', core):
            self.assertEqual(smoke.validate_profile(candidate), candidate)
        (candidate / 'config/valeorders.cfg').write_text('adventure {\nB:canonicalEquipmentCatalog=true\n}\n')
        with patch.object(smoke, 'CORE_SHA256', core), self.assertRaises(ValueError):
            smoke.validate_profile(candidate)

    def test_enhancement_refuses_unbound_native_receipt_before_creating_candidate(self):
        recovery = self.fixture_recovery()
        root, build, qa, core = self.fixture_build()
        value = json.loads(qa.read_text())
        value['source_build_report']['sha256'] = '0' * 64
        qa.write_text(json.dumps(value))
        candidate = self.root / 'enhanced'
        with patch.object(smoke, 'CORE_SHA256', core), self.assertRaises(ValueError):
            smoke.enhance(recovery, candidate, [], root, build, qa)
        self.assertFalse(candidate.exists())

    def test_log_observer_separates_nonfatal_recipe_error_from_lifecycle_failure(self):
        text = 'Parsing error loading recipe\nDone (6.190s)! For help, type help\nStopping server\nSaving chunks for level test'
        self.assertEqual(smoke.observe_log(text), {'done': True, 'stopping': True, 'saved': True, 'fatal': False})
        self.assertTrue(smoke.observe_log('Encountered an unexpected exception')['fatal'])
        self.assertFalse(smoke.observe_log('Loading mods')['done'])

    def fixture_frozen_world(self, name='frozen', ids=('minecraft:air',)):
        source = self.root / name
        source.mkdir()
        n = world.nbt
        rows = [n.compound({'K': n.Tag(n.STRING, identity), 'V': n.Tag(n.INT, index)}) for index, identity in enumerate(ids)]
        root = n.compound({'FML': n.compound({'Registries': n.compound({'minecraft:blocks': n.compound({'ids': n.Tag(n.LIST, rows, n.COMPOUND)})})}), 'Data': n.compound()})
        (source / 'level.dat').write_bytes(n.dumps(root))
        files = world.tree_rows(source)
        receipt = self.root / (name + '.frozen.json')
        value = {'status': 'FROZEN_FOR_READ_ONLY_COPY', 'world_path': str(source), 'files': files, 'file_count': len(files), 'bytes': sum(row['bytes'] for row in files),
                 'manifest_sha256': hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()}
        receipt.write_text(json.dumps(value))
        return source, receipt, smoke.pack.hash_file(receipt)['sha256']

    def test_frozen_receipt_and_missing_registry_checks_are_fail_closed(self):
        source, receipt, identity = self.fixture_frozen_world(ids=('minecraft:air', 'legacy:required'))
        with self.assertRaises(ValueError):
            world.frozen_receipt(receipt, '0' * 64)
        runtime, unused, ignored = self.fixture_frozen_world('runtime-world')
        review = world.registry_review(source / 'level.dat', runtime / 'level.dat')
        self.assertEqual(review['missing_saved_names'], 1)
        self.assertFalse(review['mapping_discard_authorized'])
        self.assertEqual(review['registries'][0]['missing'], ['legacy:required'])

    def test_copied_world_stays_unbootable_until_script_registry_and_source_seal(self):
        recovery = self.fixture_recovery(include_script=False)
        root, build, qa, core = self.fixture_build()
        runtime = self.root / 'runtime'
        source, frozen, identity = self.fixture_frozen_world()
        with patch.object(smoke, 'CORE_SHA256', core):
            smoke.enhance(recovery, runtime, [], root, build, qa)
            candidate = self.root / 'copied-world'
            result = world.prepare(runtime, source, frozen, identity, candidate)
            self.assertEqual(result['status'], 'COPIED_VERIFIED_PENDING_STATIC_REVIEW')
            with self.assertRaises(ValueError):
                smoke.validate_profile(candidate)
            review = world.review_scripts(candidate, recovery)
            self.assertEqual(review['status'], 'NO_EXECUTABLE_SCRIPT_BODY_FOUND')
            sealed = world.seal(candidate, recovery, candidate / world.REVIEW, source / 'level.dat')
            self.assertEqual(sealed['missing_saved_registry_names'], 0)
            self.assertEqual(smoke.validate_profile(candidate), candidate)
            (candidate / world.LEVEL / 'level.dat').write_bytes(b'changed-world')
            with self.assertRaises(ValueError):
                smoke.validate_profile(candidate)
            self.assertEqual(smoke.pack.hash_file(source / 'level.dat')['sha256'], result['files'][0]['sha256'])

    def test_original_nonempty_script_review_blocks_native_world_seal(self):
        recovery = self.fixture_recovery()
        root, build, qa, core = self.fixture_build()
        runtime = self.root / 'runtime'
        source, frozen, identity = self.fixture_frozen_world()
        with patch.object(smoke, 'CORE_SHA256', core):
            smoke.enhance(recovery, runtime, [], root, build, qa)
            candidate = self.root / 'copied-world'
            world.prepare(runtime, source, frozen, identity, candidate)
            review = world.review_scripts(candidate, recovery)
            self.assertEqual(review['status'], 'REVIEW_REQUIRED')
            self.assertEqual(len(review['script_bodies']), 1)
            with self.assertRaises(ValueError):
                world.seal(candidate, recovery, candidate / world.REVIEW, source / 'level.dat')
            self.assertEqual((candidate / smoke.MARKER).read_text().strip(), world.PENDING)

    def test_private_dependency_secret_fields_are_reported_without_values(self):
        flags = world.credential_flags(b'S:apiToken=private-test-secret\nS:password=""\nB:useToken=false\n')
        self.assertEqual(len(flags), 1)
        self.assertNotIn('private-test-secret', json.dumps(flags))

    def test_inline_placed_tile_script_is_found_without_execution(self):
        recovery = self.fixture_recovery(include_script=False)
        candidate = self.root / 'review-only'
        (candidate / world.LEVEL / 'region').mkdir(parents=True)
        (candidate / smoke.MARKER).write_text(world.PENDING + '\n')
        n = world.nbt
        tile = n.compound({'id': n.Tag(n.STRING, 'customnpcs:scripted'), 'Scripts': n.Tag(n.LIST, [n.compound({'Script': n.Tag(n.STRING, 'throw never_execute()')})], n.COMPOUND)})
        chunk = n.compound({'Level': n.compound({'Entities': n.Tag(n.LIST, [], n.COMPOUND), 'TileEntities': n.Tag(n.LIST, [tile], n.COMPOUND)})})
        path = candidate / world.LEVEL / 'region/r.0.0.mca'
        region = world.anvil.Region(path)
        region.store_chunk(0, '', chunk)
        path.write_bytes(region.to_bytes())
        rows = world.tree_rows(candidate / world.LEVEL)
        (candidate / world.PREP).write_text(json.dumps({'files': rows, 'source_manifest_sha256': 'fixture'}))
        review = world.review_scripts(candidate, recovery)
        self.assertEqual(review['region_files'], 1)
        self.assertEqual(review['chunks'], 1)
        self.assertEqual(review['status'], 'REVIEW_REQUIRED')
        self.assertEqual(len(review['script_bodies']), 1)
        self.assertFalse(review['scripts_executed'])

    def test_release312_upgrade_uses_frozen_receipts_new_port_and_retains0311(self):
        recovery = self.fixture_recovery(include_script=False)
        source_root, old_build, old_native, old_core = self.fixture_build()
        source_world, frozen, frozen_id = self.fixture_frozen_world()
        frozen_inputs = self.root / 'frozen312'
        build = json.loads(old_build.read_text())
        build['source_snapshot'] = {'sha256': 'fixture-source-312'}
        new_core = None
        for result in build['results'].values():
            for row in result['artifacts']:
                target = frozen_inputs / row['copied_path']
                target.parent.mkdir(parents=True, exist_ok=True)
                data = (source_root / row['copied_path']).read_bytes()
                if row['component'] == 'vale-orders':
                    data = jar([('fixture.txt', b'new312-vfx')])
                target.write_bytes(data)
                row.update(smoke.pack.hash_file(target))
                if row['component'] == 'vale-orders':
                    new_core = row['sha256']
        build_path = frozen_inputs / 'build/source-build-report.json'
        build_path.parent.mkdir(exist_ok=True)
        build_path.write_text(json.dumps(build))
        qa = json.loads(old_native.read_text())
        qa['source_build_report'] = smoke.pack.hash_file(build_path)
        qa['assertions'] = 2058
        qa['expected_loaded_mods']['valeorders']['sha256'] = new_core
        qa_path = frozen_inputs / 'build/native-check-report.json'
        qa_path.write_text(json.dumps(qa))
        with patch.object(smoke, 'CORE_SHA256', old_core), patch.object(release, 'CORE_SHA256', new_core), patch.object(release, 'SOURCE_SHA256', 'fixture-source-312'), patch.object(release, 'BUILD_SHA256', smoke.pack.hash_file(build_path)['sha256']), patch.object(release, 'NATIVE_SHA256', smoke.pack.hash_file(qa_path)['sha256']):
            runtime = self.root / 'runtime'
            smoke.enhance(recovery, runtime, [], source_root, old_build, old_native)
            reference = runtime / 'magma-smoke-flat/level.dat'
            reference.parent.mkdir()
            reference.write_bytes((source_world / 'level.dat').read_bytes())
            donor = self.root / 'donor'
            world.prepare(runtime, source_world, frozen, frozen_id, donor)
            world.review_scripts(donor, recovery)
            candidate = self.root / 'new312'
            prep = release.prepare(runtime, source_world, frozen, frozen_id, candidate, frozen_inputs)
            self.assertEqual(prep['port'], 25583)
            release.reuse_script_review(candidate, donor)
            sealed = world.seal(candidate, recovery, candidate / world.REVIEW, reference)
            self.assertEqual(sealed['profile_release'], '0.3.12')
            self.assertEqual(smoke.validate_profile(candidate), candidate)
            self.assertEqual(len(list((candidate / 'retained-vale-0311/mods').glob('*.jar'))), 9)
            self.assertEqual(smoke.pack.hash_file(runtime / 'mods/vale-orders.jar')['sha256'], old_core)
            properties = (candidate / 'server.properties').read_text()
            (candidate / 'server.properties').write_text(properties.replace('25583', '25582'))
            with self.assertRaises(ValueError):
                smoke.validate_profile(candidate)

    def test_unknown_release_or_changed_core_pin_is_not_a_guard_fallback(self):
        with self.assertRaises(ValueError):
            release.world_port(self.root, {'profile_release': '0.3.13', 'core_sha256': 'anything'})
        with self.assertRaises(ValueError):
            release.world_port(self.root, {'profile_release': '0.3.12', 'core_sha256': 'wrong', 'source_snapshot_sha256': release.SOURCE_SHA256})

    def test_script_review_reuse_requires_identical_frozen_file_rows(self):
        for name, digest in (('donor', 'old'), ('new', 'changed')):
            root = self.root / name
            root.mkdir()
            (root / world.PREP).write_text(json.dumps({'files': [{'path': 'level.dat', 'sha256': digest, 'bytes': 1}], 'source_manifest_sha256': digest}))
        with self.assertRaises(ValueError):
            release.reuse_script_review(self.root / 'new', self.root / 'donor')

    def test_graphical_recipe_is_synthetic_distinct_capped_and_does_not_mutate_donor(self):
        recipe = {'argv': ['javaw.exe', '-Xms1G', '-Xmx5G', '--accessToken', '0', '--userType', 'legacy', '--username', 'OldPreview', '--uuid', 'old',
                           '--gameDir', 'old-dir', '--server', '127.0.0.1', '--port', '25582'], 'cwd': 'old-dir', 'external_inputs': []}
        changed = graphical.client_recipe(recipe, self.root / 'new-client')
        self.assertIn('-Xmx4G', changed['argv'])
        self.assertNotIn('-Xmx5G', changed['argv'])
        self.assertIn('ValeFullPack', changed['argv'])
        self.assertIn('25583', changed['argv'])
        self.assertIn('OldPreview', recipe['argv'])
        identity = graphical.uuid.UUID(hex=changed['argv'][changed['argv'].index('--uuid') + 1])
        self.assertEqual(identity.version, 3)
        self.assertNotEqual(graphical.offline_uuid('ValeFullPack'), graphical.offline_uuid('OldPreview'))

    def test_graphical_recipe_rejects_account_token_and_invalid_username(self):
        with self.assertRaises(ValueError):
            graphical.client_recipe({'argv': ['--accessToken', 'real-token']}, self.root)
        with self.assertRaises(ValueError):
            graphical.offline_uuid('../invalid-name')

    def test_graphical_dependency_allowlist_excludes_world_scripts_and_account_state(self):
        for path in ('server.properties', 'ops.json', 'world/playerdata/user.dat', 'config/accounts.json', 'config/worldedit/sessions/user.json', 'customnpcs/clones/1/private.json', 'customnpcs/scripts/code.js'):
            self.assertFalse(graphical.eligible_dependency(path))
        self.assertTrue(graphical.eligible_dependency('config/CustomNpcs.cfg'))
        self.assertTrue(graphical.eligible_dependency('customnpcs/assets/customnpcs/textures/skin.png'))

    def test_live_console_preserves_command_order_without_shell_execution(self):
        value = {'commands': ['list', 'op ValeFullPack', 'save-all', 'stop']}
        self.assertEqual(live_console.commands(value), value['commands'])

    def test_inventory_tab_workaround_preserves_other_bytes_and_line_endings(self):
        before = b'# private unrelated config\r\nEnableScripting=true\r\nInventoryGuiEnabled=true\r\nOpsOnly=false\r\n'
        after = graphical.disable_inventory_tabs(before)
        self.assertEqual(after, before.replace(b'InventoryGuiEnabled=true', b'InventoryGuiEnabled=false'))
        self.assertIn(b'EnableScripting=true\r\n', after)

    def test_inventory_tab_workaround_rejects_ambiguous_disabled_or_unknown_forms(self):
        for data in (b'', b'InventoryGuiEnabled=false\n', b'InventoryGuiEnabled=true\nInventoryGuiEnabled=false\n',
                     b'InventoryGuiEnabled=true\nInventoryGuiEnabled=true\n', b'# InventoryGuiEnabled=true\n',
                     b'InventoryGuiEnabled=true extra\n'):
            with self.assertRaises(ValueError):
                graphical.disable_inventory_tabs(data)

    def test_live_console_rejects_mapping_discard_multiline_and_invalid_requests(self):
        for value in ([], {'commands': 'stop'}, {'commands': ['list\nstop']}, {'commands': ['/fml   confirm']}, {'commands': ['yes']}):
            with self.assertRaises(ValueError):
                live_console.commands(value)


if __name__ == '__main__':
    unittest.main()
