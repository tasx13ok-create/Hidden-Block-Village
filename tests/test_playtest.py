"""Synthetic local playtest assembly; never invokes Java, a launcher, or Minecraft."""
import copy
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from playtest import assemble_local as pt
from world_merge import nbt


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode())
    return path


def json_file(path, value):
    return write(path, json.dumps(value, sort_keys=True) + "\n")


def annotation_class(modid, version):
    strings = ["Fixture", "java/lang/Object", "RuntimeVisibleAnnotations", "Lnet/minecraftforge/fml/common/Mod;", "modid", modid, "version", version]
    cp = [b"\x01" + struct.pack(">H", len(s.encode())) + s.encode() for s in strings]
    cp += [b"\x07\x00\x01", b"\x07\x00\x02"]
    annotation = struct.pack(">HHH", 1, 4, 2) + b"\x00\x05s\x00\x06\x00\x07s\x00\x08"
    return b"\xca\xfe\xba\xbe\x00\x00\x00\x34" + struct.pack(">H", len(cp) + 1) + b"".join(cp) + struct.pack(">HHHHHHH", 0x21, 9, 10, 0, 0, 0, 1) + struct.pack(">HI", 3, len(annotation)) + annotation


def jar(path, modid=None, prefix="village/vale/"):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        if modid:archive.writestr(prefix + "Fixture.class", annotation_class(modid, "0.1.0"))
        else:archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFixture-Name: " + path.name + "\n")
    return path


def row(name, modids, origin="baseline", digest=None):
    return {"file": name + ".jar", "source": "/source/" + name + ".jar", "mod_ids": dict.fromkeys(modids, "1"), "origin": origin,
            "bytes": 3, "sha256": digest or hashlib.sha256(name.encode()).hexdigest()}


class RosterTests(unittest.TestCase):
    def test_preserves_original_picture_owner_by_default(self):
        original, replacement = row("original", ["hvdungeonpictures"]), row("standards", ["hvdungeonpictures"], "vale")
        selected, omitted, replacements = pt.resolve_roster([original], [replacement])
        self.assertEqual(selected, [original]);self.assertEqual(omitted[0]["file"], "standards.jar");self.assertFalse(replacements)

    def test_picture_replacement_requires_dedicated_switch_and_records_both(self):
        original, replacement = row("original", ["hvdungeonpictures"]), row("standards", ["hvdungeonpictures"], "vale")
        with self.assertRaises(pt.PlaytestError):pt.resolve_roster([original], [replacement], {"hvdungeonpictures": "vale"})
        selected, omitted, replacements = pt.resolve_roster([original], [replacement], replace_pictures=True)
        self.assertEqual(selected, [replacement]);self.assertEqual(replacements[0]["removed"], original);self.assertEqual(replacements[0]["selected"], replacement)

    def test_other_duplicate_fails_without_explicit_choice(self):
        original, replacement = row("old", ["same"]), row("new", ["same"], "vale")
        with self.assertRaises(pt.PlaytestError):pt.resolve_roster([original], [replacement])
        self.assertEqual(pt.resolve_roster([original], [replacement], {"same": "baseline"})[0], [original])
        self.assertEqual(pt.resolve_roster([original], [replacement], {"same": "vale"})[0], [replacement])

    def test_multi_owner_jar_cannot_lose_unreplaced_registry(self):
        with self.assertRaises(pt.PlaytestError):pt.resolve_roster([row("old", ["same", "other"])], [row("new", ["same"], "vale")], {"same": "vale"})

    def test_duplicate_baseline_and_output_name_are_refused(self):
        with self.assertRaises(pt.PlaytestError):pt.resolve_roster([row("one", ["same"]), row("two", ["same"])], [])
        with self.assertRaises(pt.PlaytestError):pt.resolve_roster([row("same", ["one"])], [row("SAME", ["two"], "vale")])

    def test_identical_support_is_reused_with_omission_receipt(self):
        original = row("original", ["jei"]);addition = row("support", ["jei"], "support", original["sha256"])
        selected, omitted, _ = pt.resolve_roster([original], [addition])
        self.assertEqual(selected, [original]);self.assertIn("identical", omitted[0]["reason"])


class CandidateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.base = Path(self.temp.name)
        patcher = mock.patch.object(pt, "CANDIDATE_DRIVE", None);patcher.start();self.addCleanup(patcher.stop)
        self.root = self.base / "repo";self.root.mkdir();self.report = self.root / "build/source-build-report.json"
        results, loaded = {}, {}
        for component in sorted(pt.CLIENT_COMPONENTS | set(pt.SERVER_IDS)):
            module = "vale-compatibility-helpers" if component in {"client-lifecycle", "damage-compat", "server-bridge"} else component
            mid = pt.SERVER_IDS.get(component, component.replace("-", "_"))
            original = jar(self.root / "modules" / module / "build/libs" / (component + ".jar"), mid, pt.builds.CLASS_PREFIXES[component][0])
            checked = self.root / "build/source-builds/fixture/artifacts" / module / "runtime_mod" / original.name
            write(checked, original.read_bytes());write(self.root / "modules" / module / "build.gradle", "version='0.1.0'\n")
            artifact = {"path": str(original), "copied_path": checked.relative_to(self.root).as_posix(), "role": "runtime_mod", "component": component, "version": "0.1.0", **pt.identity(checked)}
            results.setdefault(module, {"status": "PASS", "version": "0.1.0", "artifacts": []})["artifacts"].append(artifact)
            if component in pt.SERVER_IDS:loaded[mid] = {"version": "0.1.0", "file": checked.name, "sha256": artifact["sha256"]}
        write(self.root / "tools/run_native_checks.py", "# synthetic private fixture\n")
        for module, directory, _, _, _, _ in pt.native.HELPERS:write(self.root / "modules" / module / directory / "Fixture.java", "class Fixture {}\n")
        snapshot = pt.builds.source_snapshot(self.root);snapshot_path = self.root / "build/source-builds/fixture/source-inputs.json";json_file(snapshot_path, snapshot)
        report = {"schema": 1, "passed": True, "results": results, "source_snapshot": {"sha256": snapshot["sha256"], "path": snapshot_path.relative_to(self.root).as_posix()}}
        json_file(self.report, report)
        self.native_path = self.root / "build/native-check-report.json"
        qa = {"schema": 1, "passed": True, "status": "PASS", "minecraft_process_invoked": True, "source_build_report": pt.identity(self.report),
              "private_fixture_inputs": pt.native.private_snapshot(self.root), "production_mod_ids": list(pt.SERVER_IDS.values()), "expected_loaded_mods": loaded, "assertions": 123}
        json_file(self.native_path, qa)
        self.support = self.base / "support";pins = {}
        for name in pt.SUPPORT_PINS:
            path = jar(self.support / name, "jei" if name.startswith("jei") else None);pins[name] = pt.identity(path)["sha256"]
        patcher = mock.patch.object(pt, "SUPPORT_PINS", pins);patcher.start();self.addCleanup(patcher.stop)
        self.config = self.base / "config";write(self.config / "vale.cfg", "B:enabled=true\n");write(self.config / "credentials.json", '{"accessToken":"private-fixture-value"}')
        self.world = self.base / "world";self.make_world();write(self.world / "session.lock", b"lock");write(self.world / "region/r.0.0.mca", b"unchanged-region-fixture")
        self.java = self.base / "jdk8";write(self.java / "release", 'JAVA_VERSION="1.8.0_512"\n');write(self.java / "bin/java.exe", b"never-run");write(self.java / "bin/javaw.exe", b"never-run")
        self.server = self.base / "forge";forge = jar(self.server / pt.native.FORGE_FILE);self.server_jar = write(self.server / "minecraft_server.1.12.2.jar", b"synthetic-vanilla-server")
        write(self.server / "libraries/lib.jar", b"library-copy");write(self.server / "eula.txt", "eula=false\n")
        patcher = mock.patch.object(pt.native, "FORGE_SHA256", pt.identity(forge)["sha256"]);patcher.start();self.addCleanup(patcher.stop)
        self.install = self.base / "install";client = write(self.install / "versions/1.12.2/1.12.2.jar", b"synthetic-vanilla-client")
        json_file(self.install / "versions/1.12.2/1.12.2.json", {"id": "1.12.2", "libraries": [], "downloads": {"server": {"sha1": pt.sha1(self.server_jar)}, "client": {"sha1": pt.sha1(client)}}})
        json_file(self.install / "versions/forge-14.23.5.2859/forge-14.23.5.2859.json", {"id": "forge-14.23.5.2859", "inheritsFrom": "1.12.2", "libraries": []})
        write(self.install / "natives/forge-14.23.5.2859/native.dll", b"native-fixture");json_file(self.install / "assets/indexes/1.12.json", {"objects": {}})

    def make_world(self, version=1343, mods=("valeorders", "hvdungeonpictures")):
        listing = nbt.Tag(nbt.LIST, [nbt.compound({"ModId": nbt.Tag(nbt.STRING, mid)}) for mid in mods], nbt.COMPOUND)
        write(self.world / "level.dat", nbt.dumps(nbt.compound({"Data": nbt.compound({"DataVersion": nbt.Tag(nbt.INT, version)}), "FML": nbt.compound({"ModList": listing})})))

    def args(self, *extra):
        return pt.parser().parse_args(["plan", "--root", str(self.root), "--destination", str(self.base / "candidate"), "--world", str(self.world), "--config", str(self.config), "--support-mods", str(self.support), "--java-home", str(self.java), "--install", str(self.install), "--forge-server", str(self.server), *extra])

    def test_exact_evidence_and_doc_churn_are_accepted(self):
        proof = pt.evidence(self.root);self.assertEqual(proof["native_assertions"], 123)
        write(self.root / "docs/changed.md", "irrelevant documentation churn")
        self.assertEqual(pt.evidence(self.root)["source_sha256"], proof["source_sha256"])

    def baseline(self):
        directory = self.base / "baseline";entries = []
        for path in sorted(self.support.iterdir()):write(directory / path.name, path.read_bytes())
        jar(directory / "original-pictures.jar", "hvdungeonpictures")
        for index in range(45):jar(directory / ("original-%02d.jar" % index), "original_%02d" % index)
        for path in sorted(directory.iterdir()):entries.append({"file": path.name, **pt.identity(path)})
        json_file(self.root / "design/reference-mod-inventory.json", {"schema": "vale.reference-mod-inventory/v1", "jar_count": 49, "jars": entries})
        return directory

    def test_full_49_baseline_preserves_picture_owner_and_singleplayer_world(self):
        baseline = self.baseline();plan = pt.make_plan(self.args("--variant", "baseline", "--baseline-mods", str(baseline)))
        self.assertEqual(len(plan["client_mods"]), 57);self.assertFalse(plan["server_mods"]);self.assertIsNone(plan["loopback_server_port"])
        self.assertNotIn("--server", plan["client_launch_recipe"]["argv"])
        self.assertTrue(any(row["destination"] == "client/saves/playtest-world/level.dat" for row in plan["copies"]))
        pictures = [row for row in plan["client_mods"] if "hvdungeonpictures" in row["mod_ids"]]
        self.assertEqual(pictures[0]["origin"], "baseline");self.assertFalse(plan["replacements"])
        self.assertEqual(sum(row["origin"] == "baseline" for row in plan["client_mods"]), 49)

    def test_explicit_baseline_picture_swap_is_exact_and_original_stays_untouched(self):
        baseline = self.baseline();before = pt.identity(baseline / "original-pictures.jar")
        plan = pt.make_plan(self.args("--variant", "baseline", "--baseline-mods", str(baseline), "--replace-standard-pictures"))
        self.assertEqual(plan["replacements"][0]["removed"]["sha256"], before["sha256"])
        self.assertEqual(plan["replacements"][0]["selected"]["component"], "village-standards")
        self.assertEqual(pt.identity(baseline / "original-pictures.jar"), before)

    def test_baseline_requires_all_pinned_49_bytes(self):
        baseline = self.baseline();(baseline / "original-00.jar").unlink()
        with self.assertRaisesRegex(pt.PlaytestError, "pinned 49"):pt.make_plan(self.args("--variant", "baseline", "--baseline-mods", str(baseline)))

    def test_server_archive_49_plus_optifine_is_58_and_provenance_is_explicit(self):
        baseline = self.baseline();(baseline / "OptiFine_1.12.2_HD_U_G5.jar").unlink();jar(baseline / "marketblocks-fixture.jar", "marketblocks")
        rows = [{"path": "mods/" + path.name, "category": "mods", **pt.identity(path)} for path in sorted(baseline.iterdir())]
        receipt = {"schema": 1, "scope": "STATIC_SERVER_INTAKE_ONLY", "crc_verified": True, "output_readback_verified": True,
                   "source_unmodified": True, "jar_counts": {"mods": 49}, "jars": rows}
        json_file(self.root / "release/evidence/server-intake-20261113.json", receipt)
        plan = pt.make_plan(self.args("--variant", "baseline", "--baseline-mods", str(baseline), "--baseline-source", "server-archive"))
        self.assertEqual(len(plan["client_mods"]), 58);self.assertEqual(sum(row["origin"] == "baseline" for row in plan["client_mods"]), 49)
        self.assertEqual(plan["baseline_inventory"]["selection"], "server-archive")
        self.assertEqual([row["origin"] for row in plan["client_mods"] if row["file"].startswith("OptiFine")], ["support"])
        with self.assertRaisesRegex(pt.PlaytestError, "pinned 49"):
            pt.make_plan(self.args("--variant", "baseline", "--baseline-mods", str(baseline)))

    def test_source_or_added_source_refuses_old_receipt(self):
        write(self.root / "modules/vale-orders/src/main/java/New.java", "class New {}")
        with self.assertRaisesRegex(pt.PlaytestError, "source differs"):pt.evidence(self.root)

    def test_private_fixture_churn_is_refused(self):
        write(self.root / "modules/vale-orders/recoverychecks/Fixture.java", "class Changed {}")
        with self.assertRaisesRegex(pt.PlaytestError, "fixture sources differ"):pt.evidence(self.root)

    def test_different_native_build_binding_is_refused(self):
        data = json.loads(self.native_path.read_text());data["source_build_report"]["sha256"] = "0" * 64;json_file(self.native_path, data)
        with self.assertRaisesRegex(pt.PlaytestError, "exact source build"):pt.evidence(self.root)

    def test_changed_artifact_is_refused(self):
        data = json.loads(self.report.read_text());row = data["results"]["vale-orders"]["artifacts"][0];write(self.root / row["copied_path"], b"tampered")
        with self.assertRaisesRegex(pt.PlaytestError, "Input changed"):pt.evidence(self.root)

    def test_plan_and_real_copy_preserve_sources_and_exclude_auth(self):
        before = {str(p): pt.identity(p) for directory in [self.config, self.world] for p, _ in pt.files_under(directory)}
        plan = pt.make_plan(self.args());self.assertEqual(len(plan["client_mods"]), 12);self.assertEqual(len(plan["server_mods"]), 9)
        self.assertEqual(plan["loopback_server_port"], 25578);self.assertFalse(Path(plan["destination"]).exists())
        receipt = pt.assemble(plan, lambda: pt.evidence(self.root));target = Path(plan["destination"])
        self.assertTrue(receipt["passed"]);self.assertFalse(receipt["minecraft_invoked"])
        self.assertEqual((target / "server/playtest-world/region/r.0.0.mca").read_bytes(), b"unchanged-region-fixture")
        self.assertFalse((target / "server/playtest-world/session.lock").exists());self.assertFalse((target / "client/config/credentials.json").exists())
        self.assertEqual((target / "client/config/vale.cfg").read_bytes(), (self.config / "vale.cfg").read_bytes())
        properties = (target / "server/server.properties").read_text();self.assertIn("server-ip=127.0.0.1", properties);self.assertIn("enable-rcon=false", properties);self.assertNotIn("rcon.password", properties)
        self.assertTrue(all(pt.identity(Path(p)) == value for p, value in before.items()))
        self.assertTrue((target / "PRIVATE-PLAYTEST-RECEIPT.json").is_file())

    def test_occupied_destination_and_overlap_are_refused(self):
        (self.base / "candidate").mkdir()
        with self.assertRaisesRegex(pt.PlaytestError, "new and nonexistent"):pt.make_plan(self.args())
        with self.assertRaisesRegex(pt.PlaytestError, "overlap"):pt.safe_destination(self.world / "nested", [self.world])

    def test_world_newer_version_and_missing_registry_owners_refused(self):
        self.make_world(4000)
        with self.assertRaisesRegex(pt.PlaytestError, "newer"):pt.make_plan(self.args())
        self.make_world(mods=("narutomod",))
        with self.assertRaisesRegex(pt.PlaytestError, "missing mod owners"):pt.make_plan(self.args())
        plan = pt.make_plan(self.args("--allow-unverified-world"));self.assertEqual(plan["missing_world_mod_ids"], ["narutomod"])

    def test_java8_and_unprivileged_port_enforced(self):
        with self.assertRaisesRegex(pt.PlaytestError, "unprivileged"):pt.make_plan(self.args("--server-port", "80"))
        write(self.java / "release", 'JAVA_VERSION="25.0.5"\n')
        with self.assertRaisesRegex(pt.PlaytestError, "Java 8"):pt.make_plan(self.args())

    def test_actual_forge_null_rules_metadata_is_allowed_and_verified(self):
        library = write(self.install / "libraries/fixture/library.jar", b"pinned-library")
        forge = {"id": "forge-14.23.5.2859", "inheritsFrom": "1.12.2", "libraries": [{"name": "fixture:library:1", "rules": None,
                 "downloads": {"artifact": {"path": "fixture/library.jar", "sha1": pt.sha1(library)}}}]}
        json_file(self.install / "versions/forge-14.23.5.2859/forge-14.23.5.2859.json", forge)
        plan = pt.make_plan(self.args());argv = plan["client_launch_recipe"]["argv"];self.assertIn(str(library), argv[argv.index("-cp") + 1])

    def test_support_pin_changes_refused(self):
        write(self.support / next(iter(pt.SUPPORT_PINS)), b"changed")
        with self.assertRaisesRegex(pt.PlaytestError, "Input changed"):pt.make_plan(self.args())

    def test_input_changes_mid_copy_leave_failed_copy_without_source_mutation(self):
        plan = pt.make_plan(self.args());calls = []
        def changing_evidence():
            calls.append(1)
            if len(calls) == 2:raise pt.PlaytestError("build advanced during copying")
        with self.assertRaisesRegex(pt.PlaytestError, "build advanced"):pt.assemble(plan, changing_evidence)
        self.assertTrue((Path(plan["destination"]) / "ASSEMBLY-FAILED.json").is_file())
        self.assertFalse((Path(plan["destination"]) / "PRIVATE-PLAYTEST-RECEIPT.json").exists())

    def test_added_world_file_during_copy_refuses_completion(self):
        plan = pt.make_plan(self.args());write(self.world / "new-runtime-file.dat", b"changed-directory-membership")
        with self.assertRaisesRegex(pt.PlaytestError, "membership changed"):pt.assemble(plan)
        self.assertTrue((Path(plan["destination"]) / "ASSEMBLY-FAILED.json").exists())

    def test_config_credentials_are_omitted_without_printing_values(self):
        path = write(self.config / "ordinary.cfg", "S:api_key=private-key-fixture\n")
        reason = pt.config_omission(path, "ordinary.cfg");self.assertIn("credential", reason);self.assertNotIn("private-key", reason)
        self.assertIsNone(pt.config_omission(write(self.config / "empty.cfg", "rcon.password=\n"), "empty.cfg"))

    def test_symlink_inputs_are_refused(self):
        link = self.config / "linked.cfg"
        try:link.symlink_to(self.config / "vale.cfg")
        except OSError:self.skipTest("Creating symlinks is unavailable")
        with self.assertRaises(pt.pack.PackError):pt.files_under(self.config)


if __name__ == "__main__":
    unittest.main()
