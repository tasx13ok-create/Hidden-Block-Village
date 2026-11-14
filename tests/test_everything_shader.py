import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest
import warnings
import zipfile

from tools import everything_shader as profile


class ShaderChecks(unittest.TestCase):
    def zip(self, rows):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / 'shader.zip'
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(path, 'w') as archive:
                for name, data in rows:
                    if isinstance(name, str):
                        raw = zipfile.ZipInfo(name)
                        raw.filename = name  # Preserve hostile raw Windows separators in the fixture archive.
                        name = raw
                    archive.writestr(name, data)
        return path

    def test_safe_archive_is_crc_checked_without_extracting(self):
        path = self.zip([('shaders/a.fsh', b'#version 120\n'), ('shaders/lib/options.glsl', b'// original fixture')])
        report = profile.safe_zip(path)
        self.assertTrue(report['crc_verified'])
        self.assertEqual(len(report['files']), 2)
        self.assertEqual(len(list(path.parent.iterdir())), 1)

    def test_hostile_paths_duplicates_and_parent_collisions_are_rejected(self):
        for names in (['../x'], ['/x'], ['C:/x'], ['shaders\\x'], ['shaders/./x'], ['shaders//x'],
                      ['shaders/a', 'shaders/a'], ['Shaders/a', 'shaders/b'], ['shaders', 'shaders/a']):
            with self.subTest(names=names):
                with self.assertRaises(ValueError):
                    profile.safe_zip(self.zip([(name, b'x') for name in names]))

    def test_symlink_and_wrong_input_hash_are_rejected(self):
        info = zipfile.ZipInfo('shaders/link')
        info.create_system = 3
        info.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaises(ValueError):
            profile.safe_zip(self.zip([(info, b'elsewhere')]))
        with self.assertRaises(ValueError):
            profile.safe_zip(self.zip([('shaders/a', b'x')]), 'wrong')

    def test_unknown_duplicate_or_unsupported_shader_settings_fail(self):
        settings = '#define SHADOW\nconst int shadowMapResolution = 2048; //[512 1024 2048]\n'
        self.assertEqual(profile.validate_options(settings, {'SHADOW': 'true', 'shadowMapResolution': '2048'}),
                         {'SHADOW': 'true', 'shadowMapResolution': '2048'})
        for text, values in ((settings, {'unknown': 'true'}), (settings, {'SHADOW': '2'}),
                             (settings, {'shadowMapResolution': '16384'}), (settings + '#define SHADOW\n', {'SHADOW': 'true'})):
            with self.assertRaises(ValueError):
                profile.validate_options(text, values)


if __name__ == '__main__':
    unittest.main()
