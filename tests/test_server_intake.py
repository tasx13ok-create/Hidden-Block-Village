"""Hostile archive fixtures and byte-preservation checks; no server is executed."""
import json
from pathlib import Path
import stat
import struct
import sys
import tempfile
import unittest
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.server_intake.intake import recover, preflight
from tools.server_intake.inventory import ClassReader, jar_identity, manifest_fields, public_summary


class IntakeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def archive(self, entries):
        source = self.root / "source.zip"
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(source, "w") as archive:
                for name, content in entries:
                    archive.writestr(name, content)
        return source

    def test_case_conflicts_preserve_both_and_require_choice(self):
        source = self.archive([("plugins/Locations.yml", b"first"), ("plugins/locations.yml", b"")])
        report = recover(source, self.root / "recovery")
        self.assertEqual(report["unresolved_collision_groups"], 1)
        self.assertFalse(report["collisions"][0]["content_equivalent"])
        self.assertEqual([(self.root / "recovery" / row["destination"]).read_bytes() for row in report["rows"]], [b"first", b""])
        self.assertTrue(report["crc_verified"] and report["output_readback_verified"])

    def test_exact_duplicates_are_preserved_and_equivalence_proven(self):
        source = self.archive([("same", b"same bytes"), ("same", b"same bytes")])
        report = recover(source, self.root / "recovery")
        self.assertEqual(len({row["destination"] for row in report["rows"]}), 2)
        self.assertTrue(report["collisions"][0]["content_equivalent"])
        self.assertEqual(report["unresolved_collision_groups"], 0)

    def test_parent_file_conflicts_preserve_payload_in_either_order(self):
        for index, entries in enumerate(([('a', b'one'), ('a/b', b'two')], [('a/b', b'two'), ('a', b'one')])):
            source = self.archive(entries)
            target = self.root / str(index)
            report = recover(source, target)
            self.assertEqual([ (target / row["destination"]).read_bytes() for row in report["rows"]], [entry[1] for entry in entries])
            self.assertEqual(report["unresolved_collision_groups"], 1)

    def test_parent_case_aliases_are_explicit(self):
        source = self.archive([("Plugins/a", b"a"), ("plugins/b", b"b")])
        report = recover(source, self.root / "recovery")
        self.assertTrue(report["prefix_aliases"])
        self.assertEqual(report["status"], "RECOVERED_REVIEW_REQUIRED")
        self.assertEqual(report["rows"][1]["destination"], "server/Plugins/b")

    def test_hostile_names_fail_before_destination_exists(self):
        for index, name in enumerate(("../escape", "/absolute", "C:/drive", "con", "file:stream", "a/../b", "a./b", "a//b", "a//")):
            with self.subTest(name=name):
                source = self.archive([(name, b"bad")])
                destination = self.root / ("must-not-exist-%d" % index)
                with self.assertRaises(ValueError):
                    recover(source, destination)
                self.assertFalse(destination.exists())
        info = zipfile.ZipInfo("safe")
        info.filename = "a\\b"  # ZipInfo constructor normalizes backslashes on Windows.
        with self.assertRaises(ValueError):
            preflight([info])

    def test_symlinks_and_encryption_and_limits_are_rejected(self):
        info = zipfile.ZipInfo("link")
        info.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaises(ValueError):
            preflight([info])
        info.external_attr = 0
        info.flag_bits = 1
        with self.assertRaises(ValueError):
            preflight([info])
        info.flag_bits = 0
        with self.assertRaises(ValueError):
            preflight([info], max_entries=0)
        info.file_size = 10
        with self.assertRaises(ValueError):
            preflight([info], max_bytes=9)

    def test_existing_destination_never_changes(self):
        source = self.archive([("a", b"a")])
        target = self.root / "existing"
        target.mkdir()
        (target / "keep").write_bytes(b"original")
        with self.assertRaises(ValueError):
            recover(source, target)
        self.assertEqual(list(target.iterdir()), [target / "keep"])

    def test_bad_crc_retains_failed_receipt_and_never_claims_success(self):
        source = self.archive([("a", b"unique payload")])
        data = source.read_bytes().replace(b"unique payload", b"broken payload", 1)
        source.write_bytes(data)
        target = self.root / "recovery"
        with self.assertRaises(zipfile.BadZipFile):
            recover(source, target)
        report = json.loads((target / "PRIVATE-SERVER-INTAKE.json").read_text())
        self.assertEqual(report["status"], "FAILED_PRESERVED")
        self.assertFalse(report["server_executed"])

    def test_directory_with_hidden_payload_is_rejected(self):
        source = self.archive([("directory/", b"hidden bytes")])
        with self.assertRaises(ValueError):
            recover(source, self.root / "recovery")
        self.assertFalse((self.root / "recovery").exists())

    def test_mod_annotation_boolean_is_parsed_without_loading_class(self):
        # Minimal classfile: constant-pool strings and integer true, one class annotation.
        values = ["RuntimeVisibleAnnotations", "Lnet/minecraftforge/fml/common/Mod;", "modid", "client_only_fixture", "clientSideOnly"]
        cp = b"".join(b"\x01" + struct.pack(">H", len(value)) + value.encode() for value in values)
        cp += b"\x03" + struct.pack(">i", 1)
        annotation = struct.pack(">HHH", 1, 2, 2) + struct.pack(">HBH", 3, ord("s"), 4) + struct.pack(">HBH", 5, ord("Z"), 6)
        data = b"\xca\xfe\xba\xbe" + struct.pack(">HHH", 0, 52, 7) + cp
        data += b"\x00" * 12 + struct.pack(">HHI", 1, 1, len(annotation)) + annotation
        parsed = ClassReader(data).inspect()
        self.assertEqual(parsed["major"], 52)
        self.assertEqual(parsed["mod_declarations"][0], {"modid": "client_only_fixture", "clientSideOnly": True})
        source = self.archive([("Fixture.class", data), ("plugin.yml", b"name: SafeFixture\nversion: '1.0'\nmain: example.Plugin\nprivate-token: hidden\n")])
        result = jar_identity(source, "mods/fixture.jar")
        self.assertTrue(result["declared_client_only"])
        self.assertEqual(result["class_majors"], {52: 1})
        self.assertNotIn("private-token", result["plugin"])

    def test_manifest_uses_main_section_and_allowlisted_fields(self):
        fields = manifest_fields(b"Main-Class: example.\n Main\nSecret: hidden\n\nName: signed.class\nMain-Class: wrong\n")
        self.assertEqual(fields, {"Main-Class": "example.Main"})

    def test_public_summary_omits_private_paths_and_config_payloads(self):
        source = self.archive([("private-player-name.dat", b"private credential token")])
        target = self.root / "recovery"
        report = recover(source, target)
        inventory = {"archive_sha256": report["archive_sha256"], "counts": {}, "jars": [],
                     "java_releases": [], "java_native_binaries": [], "runtime_classpaths": [],
                     "runtime_inputs": [{"secret": "private credential token"}], "source": str(source)}
        (target / "PRIVATE-STATIC-INVENTORY.json").write_text(json.dumps(inventory), encoding="utf-8")
        public = json.dumps(public_summary(target))
        for secret in ("private-player-name", "private credential token", str(source), str(target)):
            self.assertNotIn(secret, public)
        self.assertIn(report["archive_sha256"], public)


if __name__ == "__main__":
    unittest.main()
