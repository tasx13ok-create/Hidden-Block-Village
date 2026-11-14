import contextlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest import mock
import zipfile


SPEC = importlib.util.spec_from_file_location("build_modules", Path(__file__).resolve().parents[1] / "tools" / "build_modules.py")
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class SourceBuildTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def jar(self, entries):
        path = self.root / "artifact.jar"
        with zipfile.ZipFile(path, "w") as archive:
            for name, value in entries.items():
                archive.writestr(name, value)
        return path

    @staticmethod
    def class_header(major=52):
        return b"\xca\xfe\xba\xbe\x00\x00" + struct.pack(">H", major)

    def test_dependencies_are_built_first_once(self):
        self.assertEqual(BUILD.build_plan(["vale-residents-server-bridge", "vale-encounters"]),
                         ["vale-orders", "vale-residents", "vale-residents-server-bridge", "vale-encounters"])

    def test_runtime_output_requires_java8_owned_classes(self):
        path = self.jar({"village/vale/Example.class": self.class_header(), "assets/valeorders/example.txt": "resource"})
        result = BUILD.inspect_artifact(path, "runtime_mod", "vale-orders")
        self.assertEqual(result["class_major_versions"], [52])
        self.assertTrue(result["zip_crc_verified"])

    def test_compile_api_cannot_be_bundled_in_companion(self):
        path = self.jar({"village/encounters/Actors.class": self.class_header(), "village/vale/EncounterRewards.class": self.class_header()})
        with self.assertRaisesRegex(BUILD.BuildError, "Unexpected bundled class"):
            BUILD.inspect_artifact(path, "runtime_mod", "vale-encounters")

    def test_native_probe_cannot_escape_into_core(self):
        path = self.jar({"village/vale/arsenal/RecoveryNativeChecks.class": self.class_header()})
        with self.assertRaisesRegex(BUILD.BuildError, "Private test class"):
            BUILD.inspect_artifact(path, "runtime_mod", "vale-orders")

    def test_client_adapter_cannot_include_server_adapter_classes(self):
        path = self.jar({"village/vale/clientcompat/SafeShutdown.class": self.class_header(),
                         "village/vale/serverbridge/ValeServerBridge.class": self.class_header()})
        with self.assertRaisesRegex(BUILD.BuildError, "Unexpected bundled class"):
            BUILD.inspect_artifact(path, "runtime_mod", "client-lifecycle")

    def test_newer_bytecode_is_rejected(self):
        path = self.jar({"village/vale/Example.class": self.class_header(69)})
        with self.assertRaisesRegex(BUILD.BuildError, "Java 8 class required"):
            BUILD.inspect_artifact(path, "runtime_mod", "vale-orders")

    def test_resource_overlay_does_not_count_as_runtime_mod(self):
        path = self.jar({"assets/hvdungeonpictures/example.txt": "resource"})
        self.assertEqual(BUILD.inspect_artifact(path, "resource_overlay", "village-standards")["class_count"], 0)
        with self.assertRaisesRegex(BUILD.BuildError, "No compiled classes"):
            BUILD.inspect_artifact(path, "runtime_mod", "village-standards")

    def test_runtime_mod_is_not_a_prepared_minecraft_api(self):
        path = self.jar({"village/vale/Example.class": self.class_header()})
        with self.assertRaisesRegex(BUILD.BuildError, "development API"):
            BUILD.prepared_api(path)

    def test_source_snapshot_changes_when_actual_source_changes(self):
        source = self.root / "modules" / "example" / "src" / "Main.java"
        source.parent.mkdir(parents=True)
        source.write_text("class Main {}", encoding="utf-8")
        first = BUILD.source_snapshot(self.root)
        source.write_text("class Main { int value; }", encoding="utf-8")
        self.assertNotEqual(first["sha256"], BUILD.source_snapshot(self.root)["sha256"])

    def test_packaged_expedition_content_is_a_source_input(self):
        first = BUILD.source_snapshot(self.root)
        content = self.root / "content" / "expeditions" / "gates.json"
        content.parent.mkdir(parents=True)
        content.write_text('{"content_version":"fixture"}', encoding="utf-8")
        second = BUILD.source_snapshot(self.root)
        self.assertNotEqual(first["sha256"], second["sha256"])
        self.assertIn("content/expeditions/gates.json", [row["path"] for row in second["files"]])
        content.write_text('{"content_version":"changed"}', encoding="utf-8")
        self.assertNotEqual(second["sha256"], BUILD.source_snapshot(self.root)["sha256"])

    def test_source_snapshot_excludes_generated_outputs_and_private_fixture(self):
        first = BUILD.source_snapshot(self.root)
        for area in ("build", "recoverychecks", "logs", "crash-reports", "run"):
            generated = self.root / "modules" / "example" / area / "temporary.log.gz"
            generated.parent.mkdir(parents=True)
            generated.write_text("temporary", encoding="utf-8")
        self.assertEqual(first["sha256"], BUILD.source_snapshot(self.root)["sha256"])

    def test_successful_process_cannot_reuse_stale_metadata(self):
        directory = self.root / "modules" / "server-bootstrap" / "build"
        directory.mkdir(parents=True)
        metadata = directory / "vale-build.json"
        metadata.write_text('{"schema":1,"minecraft_launched":false,"artifacts":[]}', encoding="utf-8")
        launcher = self.root / "launcher.py"
        launcher.write_text("", encoding="utf-8")
        with mock.patch.object(BUILD, "ROOT", self.root), mock.patch.object(BUILD.subprocess, "run", return_value=mock.Mock(returncode=0)), mock.patch.object(BUILD.subprocess, "check_output", side_effect=BUILD.subprocess.CalledProcessError(128, ["git"])), mock.patch.dict(BUILD.os.environ, {}, clear=True), contextlib.redirect_stdout(io.StringIO()):
            code = BUILD.main(["--modules", "server-bootstrap", "--gradle-launcher", str(launcher)])
        self.assertEqual(code, 1)
        report = json.loads((self.root / "build" / "source-build-report.json").read_text())
        self.assertEqual(report["results"]["server-bootstrap"]["status"], "FAIL")
        self.assertFalse(report["passed"])
        self.assertFalse(metadata.exists())

    def test_successful_build_report_locates_copied_artifacts_portably(self):
        directory = self.root / "modules/server-bootstrap/build"
        directory.mkdir(parents=True)
        launcher = self.root / "launcher.py"
        launcher.write_text("", encoding="utf-8")

        def build_fixture(*args, **kwargs):
            artifact = directory / "bootstrap.jar"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr("village/deployment/Bootstrap.class", self.class_header())
            (directory / "vale-build.json").write_text(json.dumps({
                "schema": 1, "module": "server-bootstrap", "version": "fixture",
                "minecraft_launched": False,
                "artifacts": [{"role": "bootstrap_library", "path": str(artifact)}],
            }), encoding="utf-8")
            return mock.Mock(returncode=0)

        with mock.patch.object(BUILD, "ROOT", self.root), mock.patch.object(BUILD.subprocess, "run", side_effect=build_fixture), mock.patch.object(BUILD.subprocess, "check_output", side_effect=BUILD.subprocess.CalledProcessError(128, ["git"])), mock.patch.dict(BUILD.os.environ, {}, clear=True), contextlib.redirect_stdout(io.StringIO()):
            code = BUILD.main(["--modules", "server-bootstrap", "--gradle-launcher", str(launcher)])
        self.assertEqual(0, code)
        report = json.loads((self.root / "build/source-build-report.json").read_text())
        result = report["results"]["server-bootstrap"]
        copied = result["artifacts"][0]
        for relative in (report["artifacts_directory"], report["source_snapshot"]["path"],
                         result["log"], copied["copied_path"]):
            self.assertNotIn("\\", relative)
            self.assertTrue((self.root / relative).exists())
        self.assertEqual(copied["sha256"], BUILD.sha256(self.root / copied["copied_path"]))


if __name__ == "__main__":
    unittest.main()
