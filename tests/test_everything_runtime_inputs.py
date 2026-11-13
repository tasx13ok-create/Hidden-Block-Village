import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from tools import everything_runtime_inputs as inputs


class InputChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        artifacts = []
        self.paths = {}
        for component in sorted(set(inputs.SERVER) | inputs.GRAPHICS):
            relative = 'build/source-builds/fixture/artifacts/' + component + '.jar'
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(path, 'w') as z:
                z.writestr('original-fixture.txt', component)
            self.paths[component] = path
            artifacts.append(dict(component=component, role='runtime_mod', copied_path=relative, **inputs.pack.hash_file(path)))
        self.build = {'passed': True, 'source_changed_during_build': [], 'source_snapshot': {'sha256': 'new-source'},
                      'results': {'fixture': {'status': 'PASS', 'artifacts': artifacts}}}
        self.bp, self.np = self.root / 'build.json', self.root / 'native.json'
        self.bp.write_text(json.dumps(self.build))
        expected = {inputs.SERVER[r['component']]: {'sha256': r['sha256']} for r in artifacts if r['component'] in inputs.SERVER}
        self.native = {'passed': True, 'status': 'PASS', 'assertions': 1, 'source_snapshot': {'sha256': 'new-source'},
                       'source_build_report': inputs.pack.hash_file(self.bp), 'production_mod_ids': list(inputs.SERVER.values()),
                       'expected_loaded_mods': expected}
        self.np.write_text(json.dumps(self.native))
        self.pins = dict(source_pin='new-source', build_pin=inputs.pack.hash_file(self.bp)['sha256'], native_pin=inputs.pack.hash_file(self.np)['sha256'])

    def review(self):
        with patch.object(inputs, 'jar_identity', return_value={'warnings': [], 'mod_declarations': [{'modid': 'valeorders', 'version': '0.3.14'}]}), \
             patch.object(inputs, 'promoted_gui_patch', return_value={'synthetic_fixture_gate': True}):
            return inputs.validate(self.root, self.bp, self.np, **self.pins)

    def test_exact_linked_graph_reviews_twelve_inputs_without_staging(self):
        result = self.review()
        self.assertEqual(len(result['runtime_components']), 12)
        self.assertFalse(result['minecraft_launched'])
        self.assertTrue(result['required_client_InventoryGuiEnabled'])

    def test_changed_physical_provider_is_rejected(self):
        self.paths['vale-residents'].write_bytes(b'changed after receipt')
        with self.assertRaises(ValueError):
            self.review()

    def test_old_or_changed_receipt_and_source_binding_fail(self):
        self.pins['source_pin'] = 'old-source'
        with self.assertRaises(ValueError):
            self.review()
        self.pins['source_pin'] = 'new-source'
        self.pins['native_pin'] = 'old-native'
        with self.assertRaises(ValueError):
            self.review()

    def test_native_loaded_artifact_must_match(self):
        self.native['expected_loaded_mods']['valeorders']['sha256'] = 'different-native-core'
        self.np.write_text(json.dumps(self.native))
        self.pins['native_pin'] = inputs.pack.hash_file(self.np)['sha256']
        with self.assertRaises(ValueError):
            self.review()

    def test_existing_lifecycle_without_promoted_patch_is_rejected(self):
        with self.assertRaises(ValueError):
            inputs.promoted_gui_patch(self.paths['client-lifecycle'])

    def test_explicit_0315_cannot_accept_0314_core(self):
        with patch.object(inputs, 'jar_identity', return_value={'warnings': [], 'mod_declarations': [{'modid': 'valeorders', 'version': '0.3.14'}]}):
            with self.assertRaisesRegex(ValueError, 'Core release'):
                inputs.validate(self.root, self.bp, self.np, release='0.3.15', **self.pins)

    def test_0315_rejects_quest_only_adapter_even_with_prior_patch_check(self):
        with patch.object(inputs, 'jar_identity', return_value={'warnings': [], 'mod_declarations': [{'modid': 'valeorders', 'version': '0.3.15'}]}), patch.object(inputs, 'promoted_gui_patch', return_value={}):
            with self.assertRaises(KeyError):
                inputs.validate(self.root, self.bp, self.np, release='0.3.15', **self.pins)

    def test_unknown_future_release_cannot_reuse_current_pins(self):
        with self.assertRaisesRegex(ValueError, 'Unsupported explicit'):
            inputs.validate(self.root, self.bp, self.np, release='0.3.99', **self.pins)


if __name__ == '__main__':
    unittest.main()
