"""Synthetic small-file tests only; no Minecraft package or runtime proof."""

import contextlib
import errno
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import zipfile


SPEC = importlib.util.spec_from_file_location(
    "release_pack", Path(__file__).resolve().parents[1] / "tools" / "release_pack.py")
rp = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(rp)


class StableReaderTests(unittest.TestCase):
    """Exercise real files plus the two Windows stat views that caused #038."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / "connected-contract.json"
        self.path.write_bytes(b"created")
        self.path.write_bytes(b"reviewed contract bytes after creation")

    @staticmethod
    def metadata(st, **changes):
        fields = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")
        values = {name: getattr(st, name) for name in fields}
        values["st_birthtime_ns"] = getattr(st, "st_birthtime_ns", st.st_ctime_ns)
        values["st_file_attributes"] = getattr(st, "st_file_attributes", 0)
        values.update(changes)
        return SimpleNamespace(**values)

    def assert_source_changed(self, operation):
        with self.assertRaises(rp.PackError) as caught:
            operation()
        self.assertEqual("SOURCE_CHANGED", caught.exception.code)

    def test_native_modified_json_and_executable_extensions_hash_exactly(self):
        # Windows path stat synthesizes execute bits; handle fstat does not.
        # These checks run on native Windows as well as POSIX CI.
        for suffix in (".json", ".cmd", ".bat", ".exe", ".com"):
            with self.subTest(suffix=suffix):
                path = self.root / ("unchanged" + suffix)
                path.write_bytes(b"created")
                payload = b"modified after creation: " + suffix.encode("ascii")
                path.write_bytes(payload)
                self.assertEqual({"bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()},
                                 rp.hash_file(path))

    def test_native_readonly_executable_is_read_without_changing_it(self):
        path = self.root / "readonly.cmd"
        payload = b"@echo off\r\n"
        path.write_bytes(payload)
        path.chmod(stat.S_IREAD)
        try:
            self.assertEqual(hashlib.sha256(payload).hexdigest(), rp.hash_file(path)["sha256"])
        finally:
            path.chmod(stat.S_IREAD | stat.S_IWRITE)

    def test_windows_creation_time_and_change_time_are_not_confused(self):
        real_fstat = os.fstat

        def handle_stat(fd):
            st = real_fstat(fd)
            return self.metadata(st, st_ctime_ns=st.st_ctime_ns + 123456789)

        expected = hashlib.sha256(self.path.read_bytes()).hexdigest()
        with mock.patch.object(rp, "WINDOWS", True), mock.patch.object(rp.os, "fstat", side_effect=handle_stat):
            self.assertEqual(expected, rp.hash_file(self.path)["sha256"])

    def test_windows_handle_change_time_change_during_read_is_rejected(self):
        real_fstat = os.fstat
        calls = []

        def handle_stat(fd):
            st = real_fstat(fd)
            calls.append(fd)
            return self.metadata(st, st_ctime_ns=st.st_ctime_ns + len(calls))

        with mock.patch.object(rp, "WINDOWS", True), mock.patch.object(rp.os, "fstat", side_effect=handle_stat):
            self.assert_source_changed(lambda: rp.hash_file(self.path))

    def test_windows_open_identity_retains_every_meaningful_field(self):
        before = self.metadata(self.path.stat())
        with mock.patch.object(rp, "WINDOWS", True):
            for field in ("st_dev", "st_ino", "st_size", "st_mtime_ns", "st_birthtime_ns"):
                with self.subTest(field=field):
                    after = self.metadata(before, **{field: getattr(before, field) + 1})
                    self.assertNotEqual(rp.opened_signature(before), rp.opened_signature(after))
            self.assertNotEqual(rp.opened_signature(before),
                                rp.opened_signature(self.metadata(before, st_mode=before.st_mode ^ stat.S_IWRITE)))
            self.assertEqual(rp.opened_signature(before),
                             rp.opened_signature(self.metadata(before, st_mode=before.st_mode ^ 0o111)))

    def test_windows_older_python_uses_matching_creation_ctime(self):
        before = self.metadata(self.path.stat())
        del before.st_birthtime_ns
        after = SimpleNamespace(**vars(before))
        after.st_mode ^= 0o111
        with mock.patch.object(rp, "WINDOWS", True):
            self.assertEqual(rp.opened_signature(before), rp.opened_signature(after))
            after.st_ctime_ns += 1
            self.assertNotEqual(rp.opened_signature(before), rp.opened_signature(after))

    def test_windows_different_opened_file_identity_is_rejected(self):
        real_fstat = os.fstat

        def wrong_file(fd):
            st = real_fstat(fd)
            return self.metadata(st, st_ino=st.st_ino + 1)

        with mock.patch.object(rp, "WINDOWS", True), mock.patch.object(rp.os, "fstat", side_effect=wrong_file):
            self.assert_source_changed(lambda: rp.hash_file(self.path))

    def test_native_content_change_during_read_is_rejected(self):
        with self.assertRaises(rp.PackError) as caught:
            with rp.stable_reader(self.path) as source:
                source.read()
                self.path.write_bytes(b"changed")
        self.assertEqual("SOURCE_CHANGED", caught.exception.code)

    def test_replaced_path_identity_after_read_is_rejected(self):
        real_stat = Path.stat
        read_finished = []

        def path_stat(path, *args, **kwargs):
            st = real_stat(path, *args, **kwargs)
            if path == self.path and read_finished:
                return self.metadata(st, st_ino=st.st_ino + 1)
            return st

        with mock.patch.object(Path, "stat", new=path_stat):
            with self.assertRaises(rp.PackError) as caught:
                with rp.stable_reader(self.path) as source:
                    source.read()
                    read_finished.append(True)
        self.assertEqual("SOURCE_CHANGED", caught.exception.code)

    def test_opened_reparse_handle_is_rejected(self):
        real_fstat = os.fstat

        def reparse_stat(fd):
            st = real_fstat(fd)
            return self.metadata(st, st_file_attributes=getattr(st, "st_file_attributes", 0) | 0x400)

        with mock.patch.object(rp.os, "fstat", side_effect=reparse_stat):
            with self.assertRaises(rp.PackError) as caught:
                rp.hash_file(self.path)
        self.assertEqual("UNSUPPORTED_FILE", caught.exception.code)

    def test_posix_cross_view_keeps_full_change_time_and_mode_checks(self):
        before = self.metadata(self.path.stat())
        with mock.patch.object(rp, "WINDOWS", False):
            for field in ("st_ctime_ns", "st_mode"):
                after = self.metadata(before, **{field: getattr(before, field) + 1})
                self.assertNotEqual(rp.opened_signature(before), rp.opened_signature(after))


class ReleasePackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.archive = self.root / "synthetic-fixture.zip"
        with zipfile.ZipFile(self.archive, "w", compression=zipfile.ZIP_STORED) as z:
            z.writestr("empty/", b"")
            z.writestr("mods/fixture.txt", b"Synthetic fixture only: not a Minecraft mod.\n")
            z.writestr("config/fixture.txt", b"sample=true\n")
            z.writestr("zero-byte", b"")
        self.report = rp.audit(self.archive)
        self.evidence = self.root / "evidence"
        self.evidence.mkdir()
        self.log = self.evidence / "synthetic-check.log"
        self.log.write_text("SYNTHETIC UNIT TEST ONLY. Minecraft was not executed.\n", encoding="utf-8")
        self.source_snapshot = self.root / "synthetic-source-snapshot.txt"
        self.source_snapshot.write_bytes(b"SYNTHETIC source snapshot for unit tests only.\n")
        self.source = {"id": "synthetic-fixture-only", "sha256": rp.hash_file(self.source_snapshot)["sha256"]}
        self.proof = self.evidence / "checks.json"
        self.proof_value = {
            "schema": "vale.validation-evidence/v1", "status": "PASSED",
            "payload_inventory_sha256": self.report["payload_inventory_sha256"],
            "source_checkpoint": self.source, "unresolved_requirements": [],
            "gates": [{"id": x, "status": "PASSED"} for x in rp.REQUIRED_GATES],
            "logs": [{"path": self.log.name, **rp.hash_file(self.log)}],
        }
        self.receipt = self.root / "synthetic-acceptance.json"
        self.receipt_value = {
            "schema": "vale.release-acceptance/v1", "status": "ACCEPTED",
            "scope": "local_client_and_server", "archive": self.report["archive"],
            "payload_inventory_sha256": self.report["payload_inventory_sha256"],
            "source_checkpoint": self.source, "unresolved_requirements": [],
            "source_snapshot": {"path": self.source_snapshot.name, **rp.hash_file(self.source_snapshot)},
            "required_gates": list(rp.REQUIRED_GATES), "evidence": [],
        }
        self.save_proof()

    def tearDown(self):
        self.temp.cleanup()

    def save_receipt(self):
        self.receipt.write_bytes(rp.canonical(self.receipt_value))

    def save_proof(self):
        self.proof.write_bytes(rp.canonical(self.proof_value))
        self.receipt_value["evidence"] = [{"path": "evidence/checks.json", **rp.hash_file(self.proof)}]
        self.save_receipt()

    def make_parts(self, name="parts"):
        directory = self.root / name
        rp.split(self.archive, self.receipt, directory, part_bytes=127)
        return directory / (self.archive.name + ".parts.json")

    def expect_pack_error(self, code, function, *args, **kwargs):
        with self.assertRaises(rp.PackError) as caught:
            function(*args, **kwargs)
        self.assertEqual(code, caught.exception.code)

    def test_audit_hashes_every_entry_without_readiness_claim(self):
        self.assertEqual(3, self.report["file_count"])
        self.assertEqual(1, self.report["directory_count"])
        self.assertEqual("NOT_EVALUATED", self.report["runtime_acceptance"])
        self.assertEqual("ALL_ENTRIES_PASSED", self.report["zip_crc_check"])
        self.assertEqual(hashlib.sha256(self.archive.read_bytes()).hexdigest(), self.report["archive"]["sha256"])
        self.assertEqual(rp.digest(self.report["entries"]), self.report["inventory_sha256"])
        self.assertEqual(self.report, rp.audit(self.archive))

    def test_directory_audit_and_declared_provenance(self):
        tree = self.root / "tree"
        (tree / "mods").mkdir(parents=True)
        (tree / "empty").mkdir()
        (tree / "mods" / "fixture.txt").write_bytes(b"sample")
        declaration = self.root / "declared.json"
        data = {
            "schema": "vale.pack-declaration/v1", "pack_role": "server",
            "release_id": "synthetic", "declared_version": "0.0-fixture",
            "source_checkpoint": "synthetic fixture only",
            "components": [{"path": "mods/fixture.txt", "id": "fixture",
                            "declared_version": "0.0-fixture", "side": "unknown",
                            "provenance": "Synthetic test input"}],
        }
        declaration.write_bytes(rp.canonical(data))
        result = rp.audit(tree, declaration)
        self.assertEqual(1, result["file_count"])
        self.assertEqual(2, result["directory_count"])
        self.assertEqual("DECLARED_NOT_RUNTIME_VERIFIED", result["declared"]["verification"])
        data["components"][0]["path"] = "absent.jar"
        declaration.write_bytes(rp.canonical(data))
        self.expect_pack_error("INVALID_DECLARATION", rp.audit, tree, declaration)

    def test_payload_binding_matches_zip_and_exact_extracted_files(self):
        tree = self.root / "extracted-synthetic-fixture"
        tree.mkdir()
        with zipfile.ZipFile(self.archive) as z:
            z.extractall(tree)  # This fixture's controlled names were created in setUp.
        extracted = rp.audit(tree)
        self.assertNotEqual(self.report["inventory_sha256"], extracted["inventory_sha256"])
        self.assertEqual(self.report["payload_inventory_sha256"], extracted["payload_inventory_sha256"])

    def test_changed_source_snapshot_rejected(self):
        self.source_snapshot.write_bytes(b"changed source")
        self.expect_pack_error("EVIDENCE_MISMATCH", rp.validate_acceptance, self.receipt, self.report)

    def test_audit_output_separation_and_no_clobber(self):
        tree = self.root / "tree"
        tree.mkdir()
        (tree / "data.txt").write_text("keep", encoding="utf-8")
        with contextlib.redirect_stderr(io.StringIO()):
            result = rp.main(["audit", str(tree), "--output", str(tree / "audit.json")])
        self.assertEqual(2, result)
        self.assertFalse((tree / "audit.json").exists())
        output = self.root / "inventory.json"
        rp.atomic_json(output, self.report)
        rp.atomic_json(output, self.report)
        before = output.read_bytes()
        self.expect_pack_error("OUTPUT_CONFLICT", rp.atomic_json, output, {"different": True})
        self.assertEqual(before, output.read_bytes())

    def test_strict_json_rejects_duplicates_nonfinite_and_wrong_root(self):
        for raw in [b'{"a":1,"a":2}', b'{"x":NaN}', b'{"x":Infinity}',
                    b'{"x":-Infinity}', b'{"x":1e999}', b'[]']:
            with self.subTest(raw=raw):
                self.expect_pack_error("INVALID_JSON", rp.parse_json, raw)

    def test_unsafe_zip_paths_and_collisions(self):
        cases = [(["../outside"], "UNSAFE_PATH"), (["/absolute"], "UNSAFE_PATH"),
                 (["a\\b"], "UNSAFE_PATH"), (["NUL.txt"], "UNSAFE_PATH"),
                 (["data."], "UNSAFE_PATH"), (["CON/file"], "UNSAFE_PATH"),
                 (["A.txt", "a.txt"], "PATH_COLLISION"),
                 (["Mods/a", "mods/b"], "PATH_COLLISION"),
                 (["a", "a/b"], "PATH_COLLISION"),
                 (["caf\u00e9/a", "cafe\u0301/b"], "PATH_COLLISION")]
        for i, (names, code) in enumerate(cases):
            path = self.root / ("unsafe-%d.zip" % i)
            with zipfile.ZipFile(path, "w") as z:
                for name in names:
                    z.writestr(name, b"x")
            with self.subTest(names=names):
                self.expect_pack_error(code, rp.audit, path)

    def test_crc_corruption_fails_audit(self):
        raw = bytearray(self.archive.read_bytes())
        with zipfile.ZipFile(self.archive) as z:
            info = z.getinfo("mods/fixture.txt")
            offset = info.header_offset + 30 + len(info.filename.encode()) + len(info.extra)
        raw[offset] ^= 1
        self.archive.write_bytes(raw)
        with self.assertRaises(zipfile.BadZipFile):
            rp.audit(self.archive)

    def test_zip_symlink_is_not_regular_content(self):
        path = self.root / "link.zip"
        with zipfile.ZipFile(path, "w") as z:
            info = zipfile.ZipInfo("shortcut")
            info.create_system = 3
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            z.writestr(info, "../outside")
        self.expect_pack_error("UNSUPPORTED_FILE", rp.audit, path)

    def test_tree_symlink_refused(self):
        tree = self.root / "tree"
        tree.mkdir()
        try:
            (tree / "link").symlink_to(self.archive)
        except (OSError, NotImplementedError):
            self.skipTest("Symlink creation unavailable on this host")
        self.expect_pack_error("SYMLINK", rp.audit, tree)

    def test_tree_mutation_detected(self):
        tree = self.root / "tree"
        tree.mkdir()
        (tree / "data").write_bytes(b"original")
        original_hash = rp.hash_file

        def change_after_hash(path):
            value = original_hash(path)
            if Path(path).name == "data":
                (tree / "later").write_bytes(b"changed")
            return value

        with mock.patch.object(rp, "hash_file", side_effect=change_after_hash):
            self.expect_pack_error("SOURCE_CHANGED", rp.audit, tree)

    def test_roundtrip_and_deterministic_transport(self):
        manifest = self.make_parts()
        second = self.make_parts("second-parts")
        self.assertEqual(manifest.read_bytes(), second.read_bytes())
        data = json.loads(manifest.read_text())
        for part in data["parts"]:
            self.assertEqual((manifest.parent / part["filename"]).read_bytes(),
                             (second.parent / part["filename"]).read_bytes())
        output = self.root / "joined"
        output.mkdir()
        result = rp.join(manifest, output, rp.hash_file(manifest)["sha256"])
        self.assertEqual("CREATED_VERIFIED", result["status"])
        self.assertEqual("NOT_RETESTED", result["runtime_acceptance"])
        self.assertEqual(self.archive.read_bytes(), (output / self.archive.name).read_bytes())
        self.assertEqual("EXISTING_IDENTICAL_VERIFIED", rp.join(manifest, output)["status"])

    def test_pending_and_wrong_archive_binding_rejected(self):
        edits = [lambda d: d.update(status="PENDING"),
                 lambda d: d["archive"].update(filename="renamed.zip"),
                 lambda d: d["archive"].update(sha256="b" * 64),
                 lambda d: d["archive"].update(bytes=1),
                 lambda d: d.update(payload_inventory_sha256="c" * 64),
                 lambda d: d.update(unresolved_requirements=["quartermaster"]) ]
        base = json.loads(json.dumps(self.receipt_value))
        for i, edit in enumerate(edits):
            self.receipt_value = json.loads(json.dumps(base))
            edit(self.receipt_value)
            self.save_receipt()
            destination = self.root / ("invalid-%d" % i)
            with self.subTest(i=i), self.assertRaises(rp.PackError):
                rp.split(self.archive, self.receipt, destination, 127)
            self.assertFalse(destination.exists())

    def test_stale_payload_and_source_proof_rejected(self):
        self.proof_value["payload_inventory_sha256"] = "d" * 64
        self.save_proof()
        self.expect_pack_error("STALE_EVIDENCE", rp.validate_acceptance, self.receipt, self.report)
        self.proof_value["payload_inventory_sha256"] = self.report["payload_inventory_sha256"]
        self.proof_value["source_checkpoint"] = {"id": "old-source", "sha256": "e" * 64}
        self.save_proof()
        self.expect_pack_error("STALE_EVIDENCE", rp.validate_acceptance, self.receipt, self.report)

    def test_pending_supplemental_gate_cannot_be_hidden_by_passed_flag(self):
        self.receipt_value["required_gates"].append("R14-quartermaster-supplemental")
        self.proof_value["gates"].append({"id": "R14-quartermaster-supplemental", "status": "PENDING"})
        self.save_proof()
        self.expect_pack_error("NOT_ACCEPTED", rp.validate_acceptance, self.receipt, self.report)

    def test_missing_required_gate_and_missing_log_rejected(self):
        self.proof_value["gates"] = [x for x in self.proof_value["gates"] if x["id"] != "R14"]
        self.save_proof()
        self.expect_pack_error("MISSING_EVIDENCE", rp.validate_acceptance, self.receipt, self.report)
        self.proof_value["logs"] = []
        self.save_proof()
        self.expect_pack_error("UNBOUND_RUNTIME", rp.validate_acceptance, self.receipt, self.report)

    def test_changed_evidence_and_changed_log_rejected(self):
        self.proof.write_bytes(self.proof.read_bytes() + b" ")
        self.expect_pack_error("EVIDENCE_MISMATCH", rp.validate_acceptance, self.receipt, self.report)
        self.save_proof()
        self.log.write_text("changed", encoding="utf-8")
        self.expect_pack_error("EVIDENCE_MISMATCH", rp.validate_acceptance, self.receipt, self.report)

    def test_reference_traversal_rejected(self):
        self.receipt_value["evidence"][0]["path"] = "../outside.json"
        self.save_receipt()
        self.expect_pack_error("UNSAFE_PATH", rp.validate_acceptance, self.receipt, self.report)

    def test_existing_split_directory_is_never_reused(self):
        destination = self.root / "parts"
        destination.mkdir()
        marker = destination / "keep.txt"
        marker.write_bytes(b"original")
        self.expect_pack_error("OUTPUT_CONFLICT", rp.split, self.archive, self.receipt, destination, 127)
        self.assertEqual(b"original", marker.read_bytes())

    def test_insufficient_disk_fails_before_new_directory(self):
        destination = self.root / "parts"
        with mock.patch.object(rp.shutil, "disk_usage", return_value=type("Usage", (), {"free": 0})()):
            self.expect_pack_error("INSUFFICIENT_SPACE", rp.split, self.archive, self.receipt, destination, 127)
        self.assertFalse(destination.exists())

    def test_interrupted_split_marked_and_never_joined(self):
        destination = self.root / "parts"
        original = rp.promote_no_clobber

        def fail_part(temp, output, expected):
            if ".part-" in output.name:
                raise OSError(errno.ENOSPC, "Synthetic disk-full test")
            return original(temp, output, expected)

        with mock.patch.object(rp, "promote_no_clobber", side_effect=fail_part):
            with self.assertRaises(OSError):
                rp.split(self.archive, self.receipt, destination, 127)
        self.assertTrue((destination / "INCOMPLETE.json").is_file())
        self.assertFalse((destination / (self.archive.name + ".parts.json")).exists())
        self.assertEqual(self.report["archive"]["sha256"], rp.hash_file(self.archive)["sha256"])
        self.expect_pack_error("OUTPUT_CONFLICT", rp.split, self.archive, self.receipt, destination, 127)

    def test_corrupt_or_missing_part_never_publishes_archive(self):
        manifest = self.make_parts()
        data = json.loads(manifest.read_text())
        part = manifest.parent / data["parts"][0]["filename"]
        output = self.root / "joined"
        output.mkdir()
        original = part.read_bytes()
        part.write_bytes(b"!" + original[1:])
        self.expect_pack_error("PART_MISMATCH", rp.join, manifest, output)
        self.assertEqual([], list(output.iterdir()))
        part.unlink()
        with self.assertRaises(FileNotFoundError):
            rp.join(manifest, output)
        self.assertEqual([], list(output.iterdir()))

    def test_join_refuses_different_existing_output(self):
        manifest = self.make_parts()
        output = self.root / "joined"
        output.mkdir()
        target = output / self.archive.name
        target.write_bytes(b"DO NOT OVERWRITE")
        self.expect_pack_error("OUTPUT_CONFLICT", rp.join, manifest, output)
        self.assertEqual(b"DO NOT OVERWRITE", target.read_bytes())

    def test_manifest_tampering_and_ordering_rejected(self):
        path = self.make_parts()
        base = json.loads(path.read_text())
        edits = [lambda d: d["parts"][0].update(filename="../escape"),
                 lambda d: d["parts"].reverse(),
                 lambda d: d["parts"][0].update(bytes=True),
                 lambda d: d["parts"][0].update(filename="wrong.zip.part-00001-of-00001"),
                 lambda d: d.update(part_bytes=rp.PART_LIMIT_EXCLUSIVE)]
        output = self.root / "joined"
        output.mkdir()
        for edit in edits:
            data = json.loads(json.dumps(base))
            edit(data)
            path.write_bytes(rp.canonical(data))
            with self.assertRaises(rp.PackError):
                rp.join(path, output)
            self.assertEqual([], list(output.iterdir()))
        path.write_bytes(rp.canonical(base))
        self.expect_pack_error("MANIFEST_MISMATCH", rp.join, path, output, "f" * 64)

    def test_complete_manifest_with_incomplete_marker_is_rejected(self):
        path = self.make_parts()
        (path.parent / "INCOMPLETE.json").write_bytes(b"{}")
        output = self.root / "joined"
        output.mkdir()
        self.expect_pack_error("INCOMPLETE_PARTS", rp.join, path, output)

    def test_cli_error_is_machine_readable_and_nonzero(self):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            result = rp.main(["join", str(self.root / "absent.json"), "--output-dir", str(self.root)])
        self.assertEqual(2, result)
        self.assertEqual("FAILED", json.loads(stderr.getvalue())["status"])


if __name__ == "__main__":
    unittest.main()
