import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('audit_reference_mods', Path(__file__).resolve().parents[1] / 'tools/audit_reference_mods.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class ReferenceAuditTests(unittest.TestCase):
    def jar(self, metadata):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('mcmod.info', json.dumps(metadata))
            archive.writestr('assets/example/textures/test.png', b'fixture')
            archive.writestr('LICENSE.txt', 'fixture notice, not permission')
        return buffer.getvalue()

    def test_exact_bytes_and_declared_version_remain_distinct_from_filename(self):
        data = self.jar([{'modid': 'example', 'version': '1.7'}])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'mods.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('mods/example-2.0.jar', data)
            result = audit.audit(path)
        self.assertEqual(1, result['jar_count'])
        row = result['jars'][0]
        self.assertEqual('example-2.0.jar', row['file'])
        self.assertEqual('1.7', row['declared_mods'][0]['version'])
        self.assertTrue(row['crc_verified'])
        self.assertEqual(1, row['resources']['.png'])
        self.assertNotIn('fixture notice', json.dumps(result))
        self.assertIn('NOT_REVIEWED', row['license_status'])

    def test_nonobject_mod_metadata_is_flagged_without_inventing_an_identity(self):
        for metadata in (42, {'modList': None}):
            with self.subTest(metadata=metadata):
                result = audit.inspect_jar(self.jar(metadata))
                self.assertTrue(result['metadata_parse_error'])
                self.assertEqual([], result['declared_mods'])

    def test_duplicate_mod_basename_and_path_traversal_are_rejected(self):
        for names in (('a/example.jar', 'b/EXAMPLE.jar'), ('../example.jar',)):
            with self.subTest(names=names), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'mods.zip'
                with zipfile.ZipFile(path, 'w') as archive:
                    for name in names:
                        archive.writestr(name, self.jar([]))
                with self.assertRaises(ValueError):
                    audit.audit(path)


if __name__ == '__main__':
    unittest.main()
