"""Synthetic JAR packaging checks. No fixtures claim to run Magma/Minecraft."""
import hashlib
import io
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import package_bootstrap as pb
import release_pack as rp


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def jar(entries, compression=zipfile.ZIP_DEFLATED):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=compression) as archive:
        for name, raw in entries.items():
            archive.writestr(name, raw)
    return stream.getvalue()


class BootstrapPackageTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "Magma-local.jar"
        self.wrapper = self.root / "owned-wrapper.jar"
        self.plan = self.root / "vale-release.properties"
        self.output = self.root / "new-launcher.jar"
        self.original_entries = {
            "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nMain-Class: net.minecraftforge.fml.relauncher.ServerLaunchWrapper\r\nImplementation-Title: Original unchanged runtime\r\nClass-Path: libraries/existing.jar\r\n\r\n",
            "net/minecraftforge/fml/relauncher/ServerLaunchWrapper.class": b"synthetic upstream class bytes retained exactly",
            "org/magmafoundation/example.bin": bytes(range(256)) * 31,
            "META-INF/services/example": b"original.provider.Name\n",
            "licenses/UPSTREAM.txt": b"original upstream notice\n",
            "libraries/embedded.jar": b"nested jar untouched by this tool",
        }
        self.wrapper_entries = {
            "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\n\r\n",
            "village/deployment/ServerBootstrap.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x34synthetic-owned-main",
            "village/deployment/ServerBootstrap$Move.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x34synthetic-owned-inner",
        }
        self.source.write_bytes(jar(self.original_entries))
        self.wrapper.write_bytes(jar(self.wrapper_entries))
        self.plan.write_text("release=candidate037\nexpected.count=1\nexpected.0.path=mods/core.jar\nexpected.0.sha256=" + "a" * 64 + "\nretired.count=0\n", encoding="utf-8")

    def run_package(self, **changes):
        args = dict(input_jar=self.source, input_sha256=sha(self.source.read_bytes()),
                    wrapper_jar=self.wrapper, wrapper_sha256=sha(self.wrapper.read_bytes()),
                    plan_path=self.plan, plan_sha256=sha(self.plan.read_bytes()), output=self.output,
                    aliases=["Magma-1.12.2-8cc35ed-server.jar"])
        args.update(changes)
        return pb.package(**args)

    def fail(self, code, **changes):
        originals = {p: p.read_bytes() for p in (self.source, self.wrapper, self.plan)}
        with self.assertRaises(rp.PackError) as caught:
            self.run_package(**changes)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(originals, {p: p.read_bytes() for p in originals})
        self.assertFalse(self.output.exists())

    def test_preserves_all_unowned_payloads_and_updates_only_owned_fields(self):
        originals = {p: p.read_bytes() for p in (self.source, self.wrapper, self.plan)}
        result = self.run_package()
        self.assertEqual(result["status"], pb.STATUS)
        self.assertEqual(result["runtime_acceptance"], "NOT_RUN")
        with zipfile.ZipFile(self.output) as output:
            self.assertEqual(set(output.namelist()), set(self.original_entries) | set(self.wrapper_entries) | {pb.RESOURCE})
            for name, raw in self.original_entries.items():
                if name != pb.MANIFEST:
                    self.assertEqual(output.read(name), raw)
            new_manifest = output.read(pb.MANIFEST)
            self.assertEqual(new_manifest, self.original_entries[pb.MANIFEST].replace(pb.FORGE_MAIN.encode(), pb.OWN_MAIN.encode()))
            for name, raw in self.wrapper_entries.items():
                if name != pb.MANIFEST:
                    self.assertEqual(output.read(name), raw)
            self.assertEqual(output.read(pb.RESOURCE), self.plan.read_bytes())
            self.assertIsNone(output.testzip())
        receipt, _ = rp.read_json(Path(result["receipt"]))
        self.assertEqual(receipt["upstream_preservation"], "ALL_UNOWNED_MEMBER_PAYLOAD_HASHES_MATCH")
        self.assertEqual(receipt["archive"]["sha256"], sha(self.output.read_bytes()))
        self.assertEqual(originals, {p: p.read_bytes() for p in originals})

    def test_wrapped_main_class_manifest_is_handled_without_other_changes(self):
        manifest = self.original_entries[pb.MANIFEST].replace(pb.FORGE_MAIN.encode(), b"net.minecraftforge.fml.relauncher.\r\n ServerLaunchWrapper")
        updated, old = pb.update_manifest(manifest)
        self.assertEqual(old, pb.FORGE_MAIN)
        self.assertIn(b"Main-Class: village.deployment.ServerBootstrap\r\n", updated)
        self.assertIn(b"Implementation-Title: Original unchanged runtime\r\n", updated)

    def test_stale_input_wrapper_or_plan_hash_is_rejected(self):
        self.fail("INPUT_MISMATCH", input_sha256="0" * 64)
        self.fail("INPUT_MISMATCH", wrapper_sha256="0" * 64)
        self.fail("INPUT_MISMATCH", plan_sha256="0" * 64)

    def test_no_existing_jar_or_receipt_is_overwritten(self):
        self.output.write_bytes(b"keep existing output")
        with self.assertRaises(rp.PackError) as caught:
            self.run_package()
        self.assertEqual(caught.exception.code, "OUTPUT_CONFLICT")
        self.assertEqual(self.output.read_bytes(), b"keep existing output")
        self.output.unlink()
        receipt = self.output.with_name(self.output.name + ".receipt.json")
        receipt.write_text("keep receipt")
        self.fail("OUTPUT_CONFLICT")
        self.assertEqual(receipt.read_text(), "keep receipt")

    def test_signature_files_are_rejected_not_stripped(self):
        for filename in ("META-INF/UPSTREAM.SF", "META-INF/upstream.rsa", "META-INF/SIG-OWNER", "META-INF/owner.EC"):
            with self.subTest(filename=filename):
                self.source.write_bytes(jar({**self.original_entries, filename: b"signature"}))
                self.fail("SIGNED_JAR")

    def test_digest_attributes_and_signature_version_are_rejected(self):
        for extra in (b"\r\nName: example.class\r\nSHA-256-Digest: abc\r\n\r\n", b"Signature-Version: 1.0\r\n\r\n"):
            entries = dict(self.original_entries)
            entries[pb.MANIFEST] += extra
            self.source.write_bytes(jar(entries))
            self.fail("SIGNED_JAR")

    def test_unknown_main_class_or_duplicate_manifest_attribute_rejected(self):
        entries = dict(self.original_entries)
        entries[pb.MANIFEST] = entries[pb.MANIFEST].replace(pb.FORGE_MAIN.encode(), b"unknown.Launcher")
        self.source.write_bytes(jar(entries))
        self.fail("UNSUPPORTED_MAIN")
        entries[pb.MANIFEST] = self.original_entries[pb.MANIFEST].replace(b"\r\nImplementation-Title", b"\r\nmain-class: duplicate\r\nImplementation-Title")
        self.source.write_bytes(jar(entries))
        self.fail("INVALID_MANIFEST")

    def test_wrapper_cannot_include_unowned_code_or_newer_bytecode(self):
        self.wrapper.write_bytes(jar({**self.wrapper_entries, "upstream/Unowned.class": b"bad"}))
        self.fail("INVALID_WRAPPER")
        entries = dict(self.wrapper_entries)
        entries[pb.MAIN_CLASS_PATH] = b"\xca\xfe\xba\xbe\x00\x00\x00\x3dnewer-java"
        self.wrapper.write_bytes(jar(entries))
        self.fail("INVALID_WRAPPER")

    def test_repacking_owned_bootstrap_requires_new_release_id(self):
        first = self.run_package()
        packaged = self.output
        replacement = self.root / "second-launcher.jar"
        with self.assertRaises(rp.PackError) as caught:
            self.run_package(input_jar=packaged, input_sha256=first["sha256"], output=replacement)
        self.assertEqual(caught.exception.code, "REUSED_RELEASE_ID")
        self.assertFalse(replacement.exists())
        self.plan.write_text(self.plan.read_text().replace("candidate037", "candidate037_rebuild2"))
        second = self.run_package(input_jar=packaged, input_sha256=first["sha256"], output=replacement)
        self.assertEqual(second["status"], pb.STATUS)
        self.assertEqual(sha(packaged.read_bytes()), first["sha256"])

    def test_superseded_owned_inner_classes_are_removed_explicitly(self):
        entries = dict(self.original_entries)
        entries[pb.MANIFEST] = entries[pb.MANIFEST].replace(pb.FORGE_MAIN.encode(), pb.OWN_MAIN.encode())
        entries[pb.RESOURCE] = self.plan.read_bytes().replace(b"candidate037", b"old036")
        entries[pb.MAIN_CLASS_PATH] = b"old-owned-class"
        entries["village/deployment/ServerBootstrap$Old.class"] = b"old-owned-inner"
        self.source.write_bytes(jar(entries))
        result = self.run_package()
        with zipfile.ZipFile(self.output) as output:
            self.assertNotIn("village/deployment/ServerBootstrap$Old.class", output.namelist())
        receipt, _ = rp.read_json(Path(result["receipt"]))
        self.assertEqual(receipt["removed_previous_owned_classes"], ["village/deployment/ServerBootstrap$Old.class"])

    def test_plan_cannot_self_hash_launcher_alias_or_plan(self):
        original = self.plan.read_text()
        for name in ("server.jar", "Magma-1.12.2-8cc35ed-server.jar", "MAGMA-LOCAL.jar", pb.RESOURCE, "release/release-plan.properties"):
            with self.subTest(name=name):
                self.plan.write_text(original.replace("mods/core.jar", name))
                self.fail("SELF_REFERENCE")

    def test_plan_duplicate_keys_paths_and_unsafe_retirement_rejected(self):
        original = self.plan.read_text()
        self.plan.write_text(original + "release=another\n")
        self.fail("INVALID_PLAN")
        self.plan.write_text(original.replace("expected.count=1", "expected.count=2") + "expected.1.path=MODS/CORE.jar\nexpected.1.sha256=" + "b" * 64 + "\n")
        self.fail("PATH_COLLISION")
        self.plan.write_text(original.replace("retired.count=0", "retired.count=1") + "retired.0.path=Naruto.test/level.dat\nretired.0.sha256=" + "b" * 64 + "\n")
        self.fail("INVALID_PLAN")

    def test_unsafe_and_duplicate_zip_paths_fail_before_output(self):
        for filename in ("../escaped.class", "C:/absolute.class", "mods/CON.jar"):
            self.source.write_bytes(jar({**self.original_entries, filename: b"unsafe"}))
            self.fail("UNSAFE_PATH")
        stream = io.BytesIO(jar(self.original_entries))
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(stream, "a") as archive:
                archive.writestr("licenses/UPSTREAM.txt", b"duplicate")
        self.source.write_bytes(stream.getvalue())
        self.fail("PATH_COLLISION")

    def test_zip_symlink_and_crc_corruption_are_rejected(self):
        stream = io.BytesIO(jar(self.original_entries))
        with zipfile.ZipFile(stream, "a") as archive:
            info = zipfile.ZipInfo("linked")
            info.create_system = 3
            info.external_attr = 0o120777 << 16
            archive.writestr(info, b"/somewhere")
        self.source.write_bytes(stream.getvalue())
        self.fail("UNSUPPORTED_FILE")
        data = bytearray(jar(self.original_entries, compression=zipfile.ZIP_STORED))
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            info = archive.getinfo("licenses/UPSTREAM.txt")
            filename_len, extra_len = struct.unpack_from("<HH", data, info.header_offset + 26)
            offset = info.header_offset + 30 + filename_len + extra_len
        data[offset] ^= 1
        self.source.write_bytes(data)
        with self.assertRaises(zipfile.BadZipFile):
            self.run_package()
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
