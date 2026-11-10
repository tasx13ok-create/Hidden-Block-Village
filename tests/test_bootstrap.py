"""Exercise the actual Java 8 deployment wrapper against disposable folders.

No Minecraft, production world, remote service, or real server is launched.
Set VALE_JAVA8_HOME to run these checks; CI supplies that toolchain explicitly.
"""

import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "modules/server-bootstrap/src/main/java/village/deployment/ServerBootstrap.java"


def sha(value):
    return hashlib.sha256(value).hexdigest()


class BootstrapChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        home = os.environ.get("VALE_JAVA8_HOME") or os.environ.get("JAVA8_HOME")
        if not home:
            raise unittest.SkipTest("VALE_JAVA8_HOME is required for real Java 8 bootstrap execution")
        suffix = ".exe" if os.name == "nt" else ""
        cls.java = Path(home) / "bin" / ("java" + suffix)
        cls.javac = Path(home) / "bin" / ("javac" + suffix)
        version = subprocess.run([str(cls.java), "-version"], capture_output=True, text=True, check=True)
        if 'version "1.8.' not in version.stderr:
            raise RuntimeError("VALE_JAVA8_HOME does not select Java 8")
        cls.compiled = tempfile.TemporaryDirectory(prefix="vale-bootstrap-classes-")
        cls.classes = Path(cls.compiled.name)
        subprocess.run([str(cls.javac), "-encoding", "UTF-8", "-d", str(cls.classes), str(SOURCE)],
                       check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.compiled.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="vale-bootstrap-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.expected = {"mods/core.jar": b"accepted-current-mod", "config/dialogue.json": b"editable-config"}
        self.retired = {"mods/old.jar": b"audited-old-mod"}
        for path, data in {**self.expected, **self.retired}.items():
            self.write(path, data)
        self.write("Naruto.test/region/r.0.0.mca", b"preserved-world-fixture")
        self.write("Naruto.test/playerdata/player.dat", b"preserved-player-fixture")
        self.write_jar()

    def write(self, path, data):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

    def write_jar(self, extra=""):
        lines = ["release=fixture037", "expected.count=" + str(len(self.expected)),
                 "retired.count=" + str(len(self.retired))]
        for kind, values in (("expected", self.expected), ("retired", self.retired)):
            for index, (path, data) in enumerate(values.items()):
                lines.extend(["%s.%s.path=%s" % (kind, index, path),
                              "%s.%s.sha256=%s" % (kind, index, sha(data))])
        self.plan = ("\n".join(lines) + "\n" + extra).encode("utf-8")
        with zipfile.ZipFile(self.root / "server.jar", "w") as z:
            z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMain-Class: village.deployment.ServerBootstrap\r\n\r\n")
            z.writestr("vale-release.properties", self.plan)
            for path in self.classes.rglob("*.class"):
                z.write(path, path.relative_to(self.classes).as_posix())

    def run_wrapper(self, success=True, cwd=None):
        result = subprocess.run([str(self.java), "-jar", str(self.root / "server.jar"),
                                 "--vale-upgrade-check-only"], cwd=cwd or self.root,
                                capture_output=True, text=True, timeout=20)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result.stdout + result.stderr

    def test_different_working_directory_fails_before_changes(self):
        other = self.root / "different-server"
        other.mkdir()
        self.assertIn("from its own folder", self.run_wrapper(False, cwd=other))
        self.assertEqual((self.root / "mods/old.jar").read_bytes(), self.retired["mods/old.jar"])
        self.assertFalse((self.root / "vale-update-state").exists())
        self.assertEqual(list(other.iterdir()), [])

    def test_installs_and_restarts_without_touching_world_or_backups(self):
        self.run_wrapper()
        backup = self.root / "vale-update-backup/fixture037/mods/old.jar"
        self.assertEqual(backup.read_bytes(), self.retired["mods/old.jar"])
        self.assertFalse((self.root / "mods/old.jar").exists())
        state = self.root / "vale-update-state/fixture037.done"
        marker = state.read_bytes()
        self.assertIn(sha(self.plan).encode(), marker)
        self.assertIn("revalidated", self.run_wrapper())
        self.assertEqual(state.read_bytes(), marker)
        self.assertEqual(backup.read_bytes(), self.retired["mods/old.jar"])
        self.assertEqual((self.root / "Naruto.test/region/r.0.0.mca").read_bytes(), b"preserved-world-fixture")
        self.assertEqual((self.root / "Naruto.test/playerdata/player.dat").read_bytes(), b"preserved-player-fixture")

    def test_changed_runtime_is_rejected_after_successful_install(self):
        self.run_wrapper()
        self.write("mods/core.jar", b"different-mod")
        self.assertIn("missing or changed", self.run_wrapper(False))

    def test_missing_runtime_is_rejected_after_successful_install(self):
        self.run_wrapper()
        (self.root / "mods/core.jar").unlink()
        self.assertIn("missing or changed", self.run_wrapper(False))

    def test_extra_runtime_is_rejected_on_restart(self):
        self.run_wrapper()
        self.write("plugins/update/extra.jar", b"extra")
        self.assertIn("extra runtime JAR", self.run_wrapper(False))

    @unittest.skipIf(os.name == "nt", "Case-sensitive fixture requires Linux")
    def test_case_duplicate_runtime_is_rejected(self):
        self.write("mods/CORE.jar", self.expected["mods/core.jar"])
        self.assertIn("Case-colliding", self.run_wrapper(False))

    def test_reintroduced_retired_mod_is_rejected_on_restart(self):
        self.run_wrapper()
        self.write("mods/old.jar", self.retired["mods/old.jar"])
        self.assertIn("extra runtime JAR", self.run_wrapper(False))
        self.assertTrue((self.root / "mods/old.jar").exists())

    def test_owner_can_edit_dialogue_after_install(self):
        self.run_wrapper()
        self.write("config/dialogue.json", b"owner-edited-dialogue")
        self.run_wrapper()
        self.assertEqual((self.root / "config/dialogue.json").read_bytes(), b"owner-edited-dialogue")

    def test_changed_plan_cannot_reuse_installed_marker(self):
        self.run_wrapper()
        self.write_jar("# changed manifest\n")
        self.assertIn("marker is invalid", self.run_wrapper(False))

    def test_invalid_input_leaves_all_retired_files_in_place(self):
        self.write("mods/core.jar", b"wrong-input")
        self.run_wrapper(False)
        self.assertEqual((self.root / "mods/old.jar").read_bytes(), self.retired["mods/old.jar"])
        self.assertFalse((self.root / "vale-update-state").exists())

    def test_unknown_mod_fails_before_any_retirement(self):
        self.write("mods/extra.jar", b"unknown")
        self.run_wrapper(False)
        self.assertTrue((self.root / "mods/old.jar").exists())

    def test_changed_rollback_destination_is_never_overwritten(self):
        self.write("vale-update-backup/fixture037/mods/old.jar", b"different-backup")
        self.run_wrapper(False)
        self.assertEqual((self.root / "vale-update-backup/fixture037/mods/old.jar").read_bytes(), b"different-backup")
        self.assertTrue((self.root / "mods/old.jar").exists())

    def test_interrupted_retirement_checks_preexisting_backup(self):
        (self.root / "mods/old.jar").unlink()
        self.write("vale-update-backup/fixture037/mods/old.jar", b"corrupt-backup")
        self.assertIn("backup differs", self.run_wrapper(False))
        self.assertFalse((self.root / "vale-update-state/fixture037.done").exists())

    def test_valid_interrupted_retirement_can_finish(self):
        (self.root / "mods/old.jar").unlink()
        self.write("vale-update-backup/fixture037/mods/old.jar", self.retired["mods/old.jar"])
        self.run_wrapper()

    def test_cannot_retire_world_file(self):
        self.retired = {"Naruto.test/region/r.0.0.mca": b"preserved-world-fixture"}
        self.write_jar()
        self.assertIn("Invalid retirement path", self.run_wrapper(False))
        self.assertEqual((self.root / "Naruto.test/region/r.0.0.mca").read_bytes(), b"preserved-world-fixture")

    def test_runtime_symlink_is_rejected(self):
        self.run_wrapper()
        target = self.root / "outside.jar"
        target.write_bytes(b"outside")
        try:
            (self.root / "mods/link.jar").symlink_to(target)
        except (OSError, NotImplementedError) as exc:
            self.skipTest("Symlink creation unavailable on this host: " + str(exc))
        self.assertIn("Symlink", self.run_wrapper(False))


if __name__ == "__main__":
    unittest.main()
