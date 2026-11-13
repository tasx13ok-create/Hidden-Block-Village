"""Orchestration guards using synthetic artifacts; these do not run Minecraft."""

import argparse
import contextlib
import copy
import io
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import run_native_checks as native


class NativeOrchestrationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.artifacts = self.root / "build/source-builds/synthetic/artifacts"
        self.report_path = self.root / "build/source-build-report.json"
        self.report = {"schema": 1, "passed": True, "results": {},
                       "artifacts_directory": self.artifacts.relative_to(self.root).as_posix()}
        self.versions = {"vale-orders": "0.3.9", "village-standards": "0.3.1", "vale-rack": "0.1.2"}
        for module, component, mod_id in native.PRODUCTION:
            version = self.versions.get(module, "1.0")
            result = self.report["results"].setdefault(module, {"status": "PASS", "version": version, "artifacts": []})
            result["artifacts"].append(self.artifact(module, component, mod_id, version, "runtime_mod"))
            if module in self.versions:
                result["artifacts"].append(self.artifact(module, component, mod_id, version, "development_api"))
        source = self.root / "modules/vale-orders/src/main/java/Main.java"
        source.parent.mkdir(parents=True)
        source.write_text("class Main {}\n", encoding="utf-8")
        api = self.root / "modules/vale-orders/build/rfg/recompiled_minecraft-1.12.2.jar"
        self.jar(api, {"net/minecraft/world/World.class": self.bytecode(),
                       "net/minecraft/server/MinecraftServer.class": self.bytecode()})
        self.report["prepared_minecraft_api"] = {"path": str(api), "sha256": native.pack.hash_file(api)["sha256"]}
        self.report["source_snapshot"] = native.builds.source_snapshot(self.root)
        self.write_report()

    @staticmethod
    def bytecode():
        return b"\xca\xfe\xba\xbe\x00\x00\x00\x34"

    @staticmethod
    def jar(path, entries):
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            for name, value in sorted(entries.items()):
                archive.writestr(name, value)

    def artifact(self, module, component, mod_id, version, role):
        name = component + "-" + role + ".jar"
        original = self.root / "modules" / module / "build" / name
        prefix = native.builds.CLASS_PREFIXES[component][0]
        self.jar(original, {prefix + "Fixture.class": self.bytecode(),
                            "mcmod.info": json.dumps([{"modid": mod_id, "version": version}])})
        copied = self.artifacts / module / role / name
        copied.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, copied)
        return {"path": str(original), "copied_path": copied.relative_to(self.root).as_posix(),
                "role": role, "component": component, "version": version, **native.pack.hash_file(original)}

    def write_report(self):
        self.report_path.parent.mkdir(parents=True, exist_ok=True)
        self.report_path.write_text(json.dumps(self.report), encoding="utf-8")

    def inputs(self):
        return native.load_build_inputs(self.report_path, self.root)

    def test_complete_report_selects_exact_server_roster_and_current_apis(self):
        inputs = self.inputs()
        self.assertEqual({row["mod_id"] for row in inputs["runtime"]}, {row[2] for row in native.PRODUCTION})
        self.assertEqual(len(inputs["runtime"]), 9)
        self.assertEqual(set(inputs["apis"]), {"vale-orders", "vale-rack", "village-standards"})
        native.recheck_inputs(inputs, self.report_path, self.root)

    def test_failed_report_or_changed_source_is_not_current_build_evidence(self):
        self.report["passed"] = False
        self.write_report()
        with self.assertRaisesRegex(native.NativeError, "successful source"):
            self.inputs()
        self.report["passed"] = True
        self.write_report()
        (self.root / "modules/vale-orders/src/main/java/Main.java").write_text("class Main { int changed; }\n", encoding="utf-8")
        with self.assertRaisesRegex(native.NativeError, "Current source differs"):
            self.inputs()

    def test_changed_original_or_copied_artifacts_are_rejected(self):
        row = self.report["results"]["vale-orders"]["artifacts"][0]
        for name in ("path", "copied_path"):
            path = self.root / row[name]
            original = path.read_bytes()
            path.write_bytes(original + b"changed")
            with self.subTest(location=name), self.assertRaisesRegex(native.NativeError, "Artifact bytes differ"):
                self.inputs()
            path.write_bytes(original)

    def test_duplicate_runtime_declarations_and_outside_copies_are_rejected(self):
        rows = self.report["results"]["vale-orders"]["artifacts"]
        rows.append(copy.deepcopy(rows[0]))
        self.write_report()
        with self.assertRaisesRegex(native.NativeError, "Expected one runtime_mod"):
            self.inputs()
        rows.pop()
        rows[0]["copied_path"] = "modules/vale-orders/build/elsewhere.jar"
        self.write_report()
        with self.assertRaisesRegex(native.NativeError, "Unexpected checked component copy"):
            self.inputs()

    def test_declared_version_cannot_replace_actual_mod_identity(self):
        row = self.report["results"]["vale-orders"]["artifacts"][0]
        row["version"] = "0.3.8"
        self.write_report()
        with self.assertRaisesRegex(native.NativeError, "Production mod identity differs"):
            self.inputs()

    def test_prepared_api_and_post_build_identity_changes_are_rejected(self):
        inputs = self.inputs()
        api = inputs["minecraft_api"]
        with zipfile.ZipFile(api, "a") as archive:
            archive.writestr("changed", b"changed")
        with self.assertRaisesRegex(native.NativeError, "Prepared Minecraft API hash differs"):
            self.inputs()
        with self.assertRaisesRegex(native.NativeError, "Prepared Minecraft API changed"):
            native.recheck_inputs(inputs, self.report_path, self.root)

    def test_private_helper_requires_current_callback_class_and_only_own_classes(self):
        source = self.root / "modules/vale-orders/recoverychecks/village/vale/arsenal"
        source.mkdir(parents=True)
        for name in ("RecoveryNativeChecks", "ScrollCastNativeChecks039", "ReedEscapeNativeChecks039"):
            (source / (name + ".java")).write_text("class " + name + " {}", encoding="utf-8")
        path = self.root / "helper.jar"
        entries = {"mcmod.info": '[{"modid":"valerecoverychecks","version":"0.3.9"}]',
                   "village/vale/arsenal/RecoveryNativeChecks.class": self.bytecode()}
        self.jar(path, entries)
        with self.assertRaisesRegex(native.NativeError, "new Core 0.3.9"):
            native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.9", self.root)
        entries["village/vale/arsenal/ScrollCastNativeChecks039.class"] = self.bytecode()
        self.jar(path, entries)
        with self.assertRaisesRegex(native.NativeError, "ReedEscapeNativeChecks039"):
            native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.9", self.root)
        entries["village/vale/arsenal/ReedEscapeNativeChecks039.class"] = self.bytecode()
        self.jar(path, entries)
        native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.9", self.root)
        entries["net/minecraft/world/World.class"] = self.bytecode()
        self.jar(path, entries)
        with self.assertRaisesRegex(native.NativeError, "Unexpected bundled class"):
            native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.9", self.root)

    def test_unreviewed_installer_is_refused_before_execution(self):
        path = self.root / "not-forge.jar"
        path.write_bytes(b"not a reviewed Forge installer")
        with self.assertRaisesRegex(native.NativeError, "Artifact bytes differ"):
            native.obtain_installer(path, self.root, self.root, [])
        self.assertFalse((self.root / "forge-installer.jar").exists())

    def test_native_result_requires_current_fixture_and_exact_loaded_bytes(self):
        expected = {mid: {"version": "0.3.9" if mid == "valeorders" else "1.0", "file": mid + ".jar", "sha256": "a" * 64}
                    for _, _, mid in native.PRODUCTION}
        expected.update({name: {"version": version, "file": name + ".jar", "sha256": "b" * 64}
                         for name, version in (("valerecoverychecks", "0.3.9"), ("valestandardschecks", "0.3.1"))})
        good = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 1, "checks": list(native.REQUIRED_CHECKS),
                "minecraft_version": "1.12.2", "forge_version": native.FORGE_VERSION,
                "loaded_fixture_mods": expected, "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "native.log"
        log.write_text(native.PASS_MARKER + " 1 assertions\n", encoding="utf-8")
        native.validate_native_result(good, expected, log)
        invalid = []
        for key, value in (("fixture", "native-recovery-0.3.8"), ("passed", False), ("passed", "true"),
                           ("assertions", 0), ("assertions", True), ("checks", []), ("checks", [native.NEW_CHECK]),
                           ("checks", [native.REED_ESCAPE_CHECK]), ("minecraft_version", "1.12.1"),
                           ("forge_version", "14.23.5.2860"), ("core_sha256", "c" * 64)):
            changed = copy.deepcopy(good)
            changed[key] = value
            invalid.append(changed)
        for field, value in (("file", "stale-core.jar"), ("version", "0.3.8"), ("sha256", "d" * 64)):
            changed = copy.deepcopy(good)
            changed["loaded_fixture_mods"]["valeorders"][field] = value
            invalid.append(changed)
        missing = copy.deepcopy(good)
        del missing["loaded_fixture_mods"]["valerack"]
        invalid.append(missing)
        extra = copy.deepcopy(good)
        extra["loaded_fixture_mods"]["unreviewedmod"] = expected["valeorders"]
        invalid.append(extra)
        for index, value in enumerate(invalid):
            with self.subTest(case=index), self.assertRaises(native.NativeError):
                native.validate_native_result(value, expected, log)
        log.write_text("Process exited, but the fixture did not report success.\n", encoding="utf-8")
        with self.assertRaisesRegex(native.NativeError, "success marker"):
            native.validate_native_result(good, expected, log)

    def test_core0310_requires_actual_academy_acceptance_label(self):
        expected = {"valeorders": {"version": "0.3.10", "file": "core.jar", "sha256": "a" * 64}}
        result = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 100,
                  "checks": list(native.REQUIRED_CHECKS), "minecraft_version": "1.12.2",
                  "forge_version": native.FORGE_VERSION, "loaded_fixture_mods": expected,
                  "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "academy.log"
        log.write_text(native.PASS_MARKER + " 100 assertions\n", encoding="utf-8")
        with self.assertRaisesRegex(native.NativeError, "academy acceptance"):
            native.validate_native_result(result, expected, log)
        result["checks"].append("Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect")
        native.validate_native_result(result, expected, log)

    def test_core0311_cannot_pass_using_only_older_fixture_results(self):
        expected = {"valeorders": {"version": "0.3.11", "file": "core.jar", "sha256": "a" * 64}}
        older = list(native.REQUIRED_CHECKS) + ["Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect"]
        additions = ["Native six issued weapon combos, cooled contacts, final cancellation and persistent recovery",
                     "Native five distinct nature techniques, protected temporary Earth cover and effect settlement",
                     "Native Lightning Armor acquisition, mode, movement, accepted counters, owned trial and mastery"]
        result = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 1400,
                  "checks": older + additions, "minecraft_version": "1.12.2",
                  "forge_version": native.FORGE_VERSION, "loaded_fixture_mods": expected,
                  "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "core0311.log"
        log.write_text(native.PASS_MARKER + " 1400 assertions\n", encoding="utf-8")
        native.validate_native_result(result, expected, log)
        for missing in additions:
            with self.subTest(missing=missing), self.assertRaisesRegex(native.NativeError, "0.3.11 acceptance"):
                altered = copy.deepcopy(result)
                altered["checks"].remove(missing)
                native.validate_native_result(altered, expected, log)

    def test_core0312_requires_hand_technique_native_result(self):
        expected = {"valeorders": {"version": "0.3.12", "file": "core.jar", "sha256": "a" * 64}}
        checks = list(native.REQUIRED_CHECKS) + [
            "Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect",
            "Native six issued weapon combos, cooled contacts, final cancellation and persistent recovery",
            "Native five distinct nature techniques, protected temporary Earth cover and effect settlement",
            "Native Lightning Armor acquisition, mode, movement, accepted counters, owned trial and mastery"]
        result = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 1704,
                  "checks": checks, "minecraft_version": "1.12.2", "forge_version": native.FORGE_VERSION,
                  "loaded_fixture_mods": expected, "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "core0312.log"
        log.write_text(native.PASS_MARKER + " 1704 assertions\n", encoding="utf-8")
        with self.assertRaisesRegex(native.NativeError, "0.3.12 hand-technique"):
            native.validate_native_result(result, expected, log)
        result["checks"].append("Native hand-technique carrier lifecycle, cosmetic spawn failure charge, observed final-cancellation/absorption contacts and cleanup")
        native.validate_native_result(result, expected, log)

    def test_core0313_requires_new_weapon_and_expedition_acceptance(self):
        expected = {"valeorders": {"version": "0.3.13", "file": "core.jar", "sha256": "a" * 64}}
        checks = list(native.REQUIRED_CHECKS) + [
            "Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect",
            "Native six issued weapon combos, cooled contacts, final cancellation and persistent recovery",
            "Native five distinct nature techniques, protected temporary Earth cover and effect settlement",
            "Native Lightning Armor acquisition, mode, movement, accepted counters, owned trial and mastery",
            "Native hand-technique carrier lifecycle, cosmetic spawn failure charge, observed final-cancellation/absorption contacts and cleanup"]
        additions = ["Native 27 weapon presentation lifecycles, bounded carriers and cosmetic-failure gameplay isolation",
            "Native expedition adapter: synthetic measured route, nonce/protection, owner/run actors, defend/escort/skiff, rewards and restart identity"]
        result = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 2305,
            "checks": checks + additions, "minecraft_version": "1.12.2", "forge_version": native.FORGE_VERSION,
            "loaded_fixture_mods": expected, "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "core0313.log"
        log.write_text(native.PASS_MARKER + " 2305 assertions\n", encoding="utf-8")
        native.validate_native_result(result, expected, log)
        for missing in additions:
            altered = copy.deepcopy(result)
            altered["checks"].remove(missing)
            with self.subTest(missing=missing), self.assertRaisesRegex(native.NativeError, "0.3.13"):
                native.validate_native_result(altered, expected, log)

    def test_core0314_requires_real_dodge_hotbar_and_lifecycle_labels(self):
        expected = {"valeorders": {"version": "0.3.14", "file": "core.jar", "sha256": "a" * 64}}
        older = list(native.REQUIRED_CHECKS) + [
            "Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect",
            "Native six issued weapon combos, cooled contacts, final cancellation and persistent recovery",
            "Native five distinct nature techniques, protected temporary Earth cover and effect settlement",
            "Native Lightning Armor acquisition, mode, movement, accepted counters, owned trial and mastery",
            "Native hand-technique carrier lifecycle, cosmetic spawn failure charge, observed final-cancellation/absorption contacts and cleanup",
            "Native 27 weapon presentation lifecycles, bounded carriers and cosmetic-failure gameplay isolation",
            "Native expedition adapter: synthetic measured route, nonce/protection, owner/run actors, defend/escort/skiff, rewards and restart identity"]
        additions = [
            "Native combat hotbar: physical loadouts, cursor transactions, ownership, charge cancellation and death",
            "Native six mastered techniques cast from the switched physical combat hotbar",
            "Native four-way paid dodge, collision/protection, saved cooldown/air use, grace and lifecycle",
            "Native combat hotbar final-canceled death, keepInventory, native drops and end-clone ownership"]
        result = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 2306,
                  "checks": older + additions, "minecraft_version": "1.12.2", "forge_version": native.FORGE_VERSION,
                  "loaded_fixture_mods": expected, "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "core0314.log"
        log.write_text(native.PASS_MARKER + " 2306 assertions\n", encoding="utf-8")
        native.validate_native_result(result, expected, log)
        for missing in additions:
            altered = copy.deepcopy(result)
            altered["checks"].remove(missing)
            with self.subTest(missing=missing), self.assertRaisesRegex(native.NativeError, "0.3.14"):
                native.validate_native_result(altered, expected, log)

    def test_core0315_requires_worn_armor_and_all_prior_acceptance(self):
        expected = {"valeorders": {"version": "0.3.15", "file": "core.jar", "sha256": "a" * 64}}
        checks = list(native.REQUIRED_CHECKS) + [
            "Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect",
            "Native six issued weapon combos, cooled contacts, final cancellation and persistent recovery",
            "Native five distinct nature techniques, protected temporary Earth cover and effect settlement",
            "Native Lightning Armor acquisition, mode, movement, accepted counters, owned trial and mastery",
            "Native hand-technique carrier lifecycle, cosmetic spawn failure charge, observed final-cancellation/absorption contacts and cleanup",
            "Native 27 weapon presentation lifecycles, bounded carriers and cosmetic-failure gameplay isolation",
            "Native expedition adapter: synthetic measured route, nonce/protection, owner/run actors, defend/escort/skiff, rewards and restart identity",
            "Native combat hotbar: physical loadouts, cursor transactions, ownership, charge cancellation and death",
            "Native six mastered techniques cast from the switched physical combat hotbar",
            "Native four-way paid dodge, collision/protection, saved cooldown/air use, grace and lifecycle",
            "Native combat hotbar final-canceled death, keepInventory, native drops and end-clone ownership",
            "Native worn armor registered getters, conditional Lightning and packaged assets"]
        result = {"schema": 1, "fixture": native.FIXTURE, "passed": True, "assertions": 3000,
                  "checks": checks, "minecraft_version": "1.12.2", "forge_version": native.FORGE_VERSION,
                  "loaded_fixture_mods": expected, "core_sha256": expected["valeorders"]["sha256"]}
        log = self.root / "core0315.log"
        log.write_text(native.PASS_MARKER + " 3000 assertions\n", encoding="utf-8")
        native.validate_native_result(result, expected, log)
        for missing in checks:
            altered = copy.deepcopy(result)
            altered["checks"].remove(missing)
            with self.subTest(missing=missing), self.assertRaises(native.NativeError):
                native.validate_native_result(altered, expected, log)

    def test_core0315_private_jar_requires_worn_and_prior_helper_classes(self):
        names = ["village/vale/arsenal/RecoveryNativeChecks", "village/vale/arsenal/ScrollCastNativeChecks039",
                 "village/vale/arsenal/ReedEscapeNativeChecks039", "village/vale/AcademyNativeChecks",
                 "village/vale/LightningNativeChecks", "village/vale/nature/NatureNativeChecks",
                 "village/vale/melee/MeleeNativeChecks", "village/vale/arsenal/HandTechniqueNativeChecks",
                 "village/vale/arsenal/WeaponPulseNativeChecks", "village/vale/expedition/GateNativeChecks",
                 "village/vale/DodgeNativeChecks", "village/valerack/CombatHotbarNativeChecks",
                 "village/valerack/CombatHotbarLifecycleNativeChecks", "village/vale/arsenal/CombatAbilityNativeChecks",
                 "village/vale/WornArmorNativeChecks"]
        for name in names:
            path = self.root / "modules/vale-orders/recoverychecks" / (name + ".java")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("// original synthetic fixture", encoding="utf-8")
        entries = {name + ".class": self.bytecode() for name in names}
        entries["mcmod.info"] = '[{"modid":"valerecoverychecks","version":"0.3.15"}]'
        path = self.root / "worn-helper.jar"
        self.jar(path, entries)
        native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.15", self.root)
        # Every historical required helper remains mandatory at15 too.
        for missing in names[1:]:
            changed = dict(entries)
            del changed[missing + ".class"]
            self.jar(path, changed)
            with self.subTest(missing=missing), self.assertRaises(native.NativeError):
                native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.15", self.root)
        # The preceding14 contract still has no worn-helper requirement.
        entries.pop("village/vale/WornArmorNativeChecks.class")
        entries["mcmod.info"] = '[{"modid":"valerecoverychecks","version":"0.3.14"}]'
        self.jar(path, entries)
        native.verify_private_jar(path, "vale-orders", "recoverychecks", "valerecoverychecks", "0.3.14", self.root)

    def test_nonzero_process_exit_cannot_pass(self):
        steps = []
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(native.NativeError, "exited with status 7"):
            native.run_process([sys.executable, "-c", "raise SystemExit(7)"], self.root,
                               self.root / "failed.log", 10, steps, "synthetic failure")
        self.assertEqual(steps[0]["exit_code"], 7)
        self.assertFalse(steps[0]["timed_out"])

    def test_windows_taskkill_requires_a_live_parent(self):
        live = mock.Mock(pid=12345)
        live.poll.side_effect = [None, None, -9]
        exited = mock.Mock(pid=54321)
        exited.poll.return_value = 0
        with mock.patch.object(native.os, "name", "nt"), mock.patch.object(native.subprocess, "run") as taskkill:
            native.kill_process_tree(live)
            taskkill.assert_called_once_with(["taskkill", "/PID", "12345", "/T", "/F"],
                                            stdout=native.subprocess.DEVNULL, stderr=native.subprocess.DEVNULL,
                                            timeout=15, check=True)
            live.kill.assert_called_once()
            taskkill.reset_mock()
            native.kill_process_tree(exited)
            taskkill.assert_not_called()
            exited.kill.assert_not_called()

    @unittest.skipIf(os.name == "nt", "POSIX process-group cleanup; native source CI runs on Linux")
    def test_timeout_kills_process_group_including_spawned_child(self):
        script = ("import subprocess,sys,time; "
                  "child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); "
                  "print(child.pid,flush=True); time.sleep(60)")
        log = self.root / "timeout.log"
        steps = []
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(native.NativeError, "exceeded its process timeout"):
            native.run_process([sys.executable, "-c", script], self.root, log, 1.0, steps, "synthetic timeout")
        self.assertTrue(steps[0]["timed_out"])
        pid = int(log.read_text().strip())
        status = Path("/proc") / str(pid) / "status"
        if status.exists():
            # A killed orphan may briefly await reaping by the container's init.
            self.assertIn("State:\tZ", status.read_text())
        else:
            with self.assertRaises(ProcessLookupError):
                os.kill(pid, 0)

    def test_failed_native_receipt_is_retained_and_disposable_runtime_removed(self):
        inputs = self.inputs()
        controller = self.root / "tools/run_native_checks.py"
        controller.parent.mkdir()
        controller.write_text("# synthetic orchestration fixture\n", encoding="utf-8")
        helpers = []
        for module, source_dir, _, _, basename, mod_id in native.HELPERS:
            source = self.root / "modules" / module / source_dir / "Private.java"
            source.parent.mkdir(parents=True)
            source.write_text("class Private {}", encoding="utf-8")
            helper = self.root / (basename + ".jar")
            helper.write_bytes(b"synthetic private artifact")
            helpers.append({"mod_id": mod_id, "version": self.versions[module], "file": helper.name,
                            "path": helper, **native.pack.hash_file(helper)})
        args = argparse.Namespace(build_report=self.report_path, java8_home=self.root, installer=self.root / "installer.jar",
                                  gradle_arg=[], gradle_launcher=None, server_timeout=30)
        runtime_paths = []
        forge_bytes = b"synthetic Forge runtime placeholder"

        def process(command, cwd, log, timeout, steps, label):
            if label.startswith("verify java"):
                log.write_text('openjdk version "' + ('1.8.0"' if label.endswith("java8") else '25.0.1"'), encoding="utf-8")
            elif label == "install official Forge":
                runtime = Path(command[-1])
                runtime_paths.append(runtime)
                (runtime / native.FORGE_FILE).write_bytes(forge_bytes)
                log.write_text("Synthetic installation\n", encoding="utf-8")
            elif label == "run private Core and Standards fixture":
                (Path(cwd) / native.RESULT_FILE).write_text(json.dumps({"schema": 1, "fixture": native.FIXTURE,
                                                                       "passed": False, "failure": "synthetic native assertion failure"}), encoding="utf-8")
                log.write_text("VALE_RECOVERY039_NATIVE_FAIL\n", encoding="utf-8")
            else:
                self.fail("Unexpected orchestration phase: " + label)

        with mock.patch.object(native, "java_executable", return_value=self.root / "java"), \
                mock.patch.object(native, "build_helpers", return_value=helpers), \
                mock.patch.object(native, "obtain_installer", return_value=args.installer), \
                mock.patch.object(native, "run_process", side_effect=process), \
                mock.patch.object(native, "FORGE_SHA256", native.hashlib.sha256(forge_bytes).hexdigest()), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = native.run(args, self.root)
        self.assertEqual(code, 1)
        self.assertEqual(len(runtime_paths), 1)
        self.assertFalse(runtime_paths[0].exists())
        report = json.loads((self.root / "build/native-check-report.json").read_text())
        self.assertFalse(report["passed"])
        self.assertTrue(report["runtime_removed"])
        self.assertIn("synthetic native assertion failure", report["error"])
        evidence = self.root / "build/native-checks"
        self.assertEqual(len(list(evidence.glob("*/" + native.RESULT_FILE))), 1)
        self.assertFalse(list(evidence.rglob("*.jar")))
        self.assertFalse(list(evidence.rglob("level.dat")))
        native.recheck_inputs(inputs, self.report_path, self.root)


if __name__ == "__main__":
    unittest.main()
