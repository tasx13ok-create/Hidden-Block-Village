"""Synthetic package preservation checks; these fixtures do not run Minecraft."""
import copy
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

TOOLS = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(TOOLS))
import assemble_release as ar
import release_pack as rp


def encoded(value):
    return rp.canonical(value)


def archive_bytes(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    return output.getvalue()


def annotation_class(modid, version):
    strings = ["Fixture", "java/lang/Object", "RuntimeVisibleAnnotations",
               "Lnet/minecraftforge/fml/common/Mod;", "modid", modid, "version", version]
    cp = []
    for value in strings:
        raw = value.encode("utf-8")
        cp.append(b"\x01" + struct.pack(">H", len(raw)) + raw)
    cp += [b"\x07\x00\x01", b"\x07\x00\x02"]
    annotation = struct.pack(">HHH", 1, 4, 2) + b"\x00\x05s\x00\x06\x00\x07s\x00\x08"
    return (b"\xca\xfe\xba\xbe\x00\x00\x00\x34" + struct.pack(">H", len(cp) + 1) + b"".join(cp) +
            struct.pack(">HHHHHHH", 0x21, 9, 10, 0, 0, 0, 1) +
            struct.pack(">HI", 3, len(annotation)) + annotation)


def mod_jar(modid, version, annotation=False):
    if annotation:
        return archive_bytes({"Fixture.class": annotation_class(modid, version)})
    return archive_bytes({"mcmod.info": encoded([{"modid": modid, "version": version, "mcversion": "1.12.2"}]),
                          "assets/example/value.txt": (modid + version).encode()})


def ident(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


class AssemblyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.source = self.base / "source"
        self.source.mkdir()
        self.inputs = self.base / "inputs"
        self.inputs.mkdir()
        self.export = self.base / "Vale-Export"
        self.plan_path = self.inputs / "plan.json"
        self.old_core = mod_jar("valeorders", "0.3.6")
        self.new_core = mod_jar("valeorders", "0.3.7")
        self.old_encounters = mod_jar("valeencounters", "0.3.6", annotation=True)
        self.new_encounters = mod_jar("valeencounters", "0.3.7", annotation=True)
        self.server_only = mod_jar("serveronly", "1.0")
        self.client_only = mod_jar("clientonly", "1.0")
        self.original_config = b"enabled: true\n"
        self.new_config = b"enabled: true\nmenu: corrected\n"
        self.manifest = {"version": "0.3.6", "minecraft": {"version": "1.12.2", "modLoaders": [{"id": "forge-14.23.5.2859", "primary": True}]},
                         "manifestType": "minecraftModpack", "manifestVersion": 1, "overrides": "overrides", "files": []}
        self.old_server_note = b"Historical server instructions and acceptance claims.\n"
        self.old_client_notes = {"START-HERE.txt": b"Historical client start instructions.\n",
                                 "CLIENT-INSTALL.txt": b"Historical client install instructions.\n"}
        old_client = archive_bytes({
            "manifest.json": encoded(self.manifest),
            "CLIENT-PINS.json": encoded({"status": "OLD_READY_CLAIM"}),
            "overrides/mods/core036.jar": self.old_core,
            "overrides/mods/encounters036.jar": self.old_encounters,
            "overrides/mods/client-only.jar": self.client_only,
            "overrides/optionsshaders.txt": b"shaderPack=Solas.zip\n",
            **self.old_client_notes,
        })
        initial = {
            "START-HERE.txt": self.old_server_note,
            "mods/core036.jar": self.old_core, "mods/encounters036.jar": self.old_encounters,
            "mods/server-only.jar": self.server_only,
            "plugins/CrazyCrates/config.yml": self.original_config,
            "server.jar": b"old bootstrap", "Magma-test-server.jar": b"old bootstrap",
            "server.properties": b"level-name=Naruto.test\nlevel-type=DEFAULT\nlevel-seed=\n",
            "Naruto.test/level.dat": b"synthetic saved world, not valid runtime NBT",
            "Naruto.test/region/r.0.0.mca": bytes(range(256)) * 32,
            "Naruto.test/region/r.-1.2.mca": b"second unchanged region" * 111,
            "Naruto.test/DIM-1/region/r.0.0.mca": b"nether data",
            "Naruto.test/playerdata/example.dat": b"original inventory and progress",
            "client-install/Client.zip": old_client,
            "release/VERIFICATION.json": encoded({"passed": True, "old": True}),
            "release/PAYLOAD-SHA256.json": b"old hashes",
            "plugins/example-data/player.json": b"keep unrelated player records",
        }
        for name, raw in initial.items():
            target = self.source / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        (self.source / "Naruto.test/empty-preserved").mkdir()
        self.report = rp.audit(self.source)
        self.original = ar.files(self.report)
        world = ar.world_identity(self.report["entries"], "Naruto.test")
        self.server_roster = [
            self.roster("mods/core037.jar", self.new_core, "valeorders", "0.3.7"),
            self.roster("mods/encounters037.jar", self.new_encounters, "valeencounters", "0.3.7"),
            self.roster("mods/server-only.jar", self.server_only, "serveronly", "1.0"),
        ]
        self.client_roster = [
            self.roster("overrides/mods/core037.jar", self.new_core, "valeorders", "0.3.7"),
            self.roster("overrides/mods/encounters037.jar", self.new_encounters, "valeencounters", "0.3.7"),
            self.roster("overrides/mods/client-only.jar", self.client_only, "clientonly", "1.0"),
        ]
        properties = {"release": "fixture037", "expected.count": "4", "retired.count": "0"}
        for i, row in enumerate(self.server_roster + [{"path": "plugins/CrazyCrates/config.yml", **ident(self.new_config)}]):
            properties["expected.%d.path" % i] = row["path"]
            properties["expected.%d.sha256" % i] = row["sha256"]
        self.bootstrap_properties = ("\n".join(k + "=" + properties[k] for k in sorted(properties)) + "\n").encode()
        self.bootstrap = archive_bytes({"vale-release.properties": self.bootstrap_properties,
                                        "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nMain-Class: village.deployment.ServerBootstrap\r\n\r\n"})
        new_manifest = copy.deepcopy(self.manifest)
        new_manifest["version"] = "0.3.7"
        replacements = [
            self.change("mods/core036.jar", self.old_core, "mods/core037.jar", self.new_core, "core037.jar"),
            self.change("mods/encounters036.jar", self.old_encounters, "mods/encounters037.jar", self.new_encounters, "encounters037.jar"),
            self.change("server.jar", b"old bootstrap", "server.jar", self.bootstrap, "bootstrap.jar"),
            self.change("Magma-test-server.jar", b"old bootstrap", "Magma-test-server.jar", self.bootstrap, "bootstrap.jar"),
            self.change("plugins/CrazyCrates/config.yml", self.original_config, "plugins/CrazyCrates/config.yml", self.new_config, "config.yml"),
        ]
        client_changes = [
            self.change("overrides/mods/core036.jar", self.old_core, "overrides/mods/core037.jar", self.new_core, "core037.jar"),
            self.change("overrides/mods/encounters036.jar", self.old_encounters, "overrides/mods/encounters037.jar", self.new_encounters, "encounters037.jar"),
            self.change("manifest.json", encoded(self.manifest), "manifest.json", encoded(new_manifest), "manifest.json"),
        ]
        self.plan = {
            "schema": "vale.candidate-plan/v1", "candidate_id": "fixture037",
            "source_checkpoint": {"id": "synthetic-test-fixture", "sha256": "1" * 64},
            "input_payload_inventory_sha256": self.report["payload_inventory_sha256"],
            "world": {"path": "Naruto.test", **world}, "replacements": replacements,
            "server_mods": self.server_roster,
            "client": {"path": "client-install/Client.zip", "input_sha256": ident(old_client)["sha256"], "replacements": client_changes,
                       "mods": self.client_roster, "version": "0.3.7", "forge": "14.23.5.2859"},
            "bootstrap": {"paths": ["server.jar", "Magma-test-server.jar"], "resource_sha256": ident(self.bootstrap_properties)["sha256"]},
            "required_configs": ["plugins/CrazyCrates/config.yml"],
        }

    def change(self, old_path, old, new_path, new, filename):
        (self.inputs / filename).write_bytes(new)
        return {"old": {"path": old_path, **ident(old)}, "new": {"path": new_path, "file": filename, **ident(new)}}

    def roster(self, path, raw, modid, version):
        return {"path": path, **ident(raw), "mods": {modid: version}}

    def run_assembly(self, name=None, source=None):
        self.plan_path.write_bytes(encoded(self.plan))
        return ar.assemble(source or self.source, self.plan_path, self.export, name)

    def assert_failure(self, code, name=None):
        with self.assertRaises(rp.PackError) as caught:
            self.run_assembly(name)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(rp.audit(self.source)["inventory_sha256"], self.report["inventory_sha256"])

    def test_complete_candidate_preserves_world_and_unrelated_data(self):
        result = self.run_assembly()
        self.assertEqual(result["status"], "CANDIDATE_PENDING_ACCEPTANCE")
        self.assertEqual(result["runtime_acceptance"], "NOT_RUN")
        out = Path(result["output"])
        self.assertFalse((out / "INCOMPLETE.json").exists())
        self.assertEqual(rp.audit(self.source)["inventory_sha256"], self.report["inventory_sha256"])
        after = rp.audit(out / "server")
        self.assertEqual(ar.world_identity(after["entries"], "Naruto.test"), ar.world_identity(self.report["entries"], "Naruto.test"))
        self.assertEqual((out / "server/plugins/example-data/player.json").read_bytes(), b"keep unrelated player records")
        self.assertTrue((out / "server/Naruto.test/empty-preserved").is_dir())
        self.assertFalse((out / "server/release/VERIFICATION.json").exists())
        self.assertTrue((out / "prior-evidence/server/release/VERIFICATION.json").is_file())
        self.assertTrue((out / "prior-evidence/client/CLIENT-PINS.json").is_file())
        self.assertEqual((out / "server/START-HERE.txt").read_bytes(), self.old_server_note)
        zip_report = rp.audit(Path(result["archive"]))
        self.assertEqual(after["inventory_sha256"], zip_report["inventory_sha256"])
        self.assertEqual(zip_report["zip_crc_check"], "ALL_ENTRIES_PASSED")
        with zipfile.ZipFile(out / "server/client-install/Client.zip") as archive:
            self.assertNotIn("overrides/mods/core036.jar", archive.namelist())
            self.assertNotIn("CLIENT-PINS.json", archive.namelist())
            self.assertEqual(archive.read("overrides/mods/core037.jar"), self.new_core)
            self.assertEqual(archive.read("overrides/optionsshaders.txt"), b"shaderPack=Solas.zip\n")
            for path, raw in self.old_client_notes.items():
                self.assertEqual(archive.read(path), raw)

    def test_installation_note_changes_require_exact_bootstrap_pins(self):
        original_plan = copy.deepcopy(self.plan)
        for side in ("server", "client"):
            with self.subTest(side=side):
                self.plan = copy.deepcopy(original_plan)
                old = self.old_server_note if side == "server" else self.old_client_notes["START-HERE.txt"]
                change = self.change("START-HERE.txt", old, "START-HERE.txt", b"New candidate instructions.\n", side + "-note.txt")
                rows = self.plan["replacements"] if side == "server" else self.plan["client"]["replacements"]
                rows.append(change)
                # The old fixture launcher pins neither the server note nor Client.zip.
                name = side + "-missing-note-pin"
                self.assert_failure("BOOTSTRAP_PLAN", name)
                output = self.export / name
                self.assertTrue((output / "INCOMPLETE.json").is_file())
                self.assertFalse((output / "ASSEMBLY.json").exists())
                self.assertEqual((output / "prior-evidence" / side / "START-HERE.txt").read_bytes(), old)
                self.assertEqual(ar.world_identity(rp.audit(output / "server")["entries"], "Naruto.test"),
                                 ar.world_identity(self.report["entries"], "Naruto.test"))

    def test_installation_note_rename_removal_and_unknown_path_rejected_in_direct_plan(self):
        original_plan = copy.deepcopy(self.plan)
        base = self.change("START-HERE.txt", self.old_server_note, "START-HERE.txt", b"New candidate notes.\n", "notes.txt")
        rows = []
        renamed = copy.deepcopy(base)
        renamed["new"]["path"] = "config/renamed-note.txt"
        rows.append(renamed)
        removed = copy.deepcopy(base)
        removed["new"] = None
        rows.append(removed)
        unknown = copy.deepcopy(base)
        unknown["new"]["path"] = "README.txt"
        rows.append(unknown)
        for change in rows:
            with self.subTest(change=change):
                self.plan = copy.deepcopy(original_plan)
                self.plan["replacements"].append(change)
                self.assert_failure("CHANGE_SCOPE")
                self.assertFalse((self.export / self.plan["candidate_id"]).exists())

    def test_installation_note_stale_input_pin_and_size_limit_rejected_in_direct_plan(self):
        change = self.change("START-HERE.txt", self.old_server_note, "START-HERE.txt", b"Reviewed notes.\n", "notes.txt")
        self.plan["replacements"].append(change)
        (self.inputs / "notes.txt").write_bytes(b"Changed after review.\n")
        self.assert_failure("PIN_MISMATCH")
        self.assertFalse(self.export.exists())
        oversized = b"x" * (ar.MAX_INSTALLATION_NOTE_BYTES + 1)
        (self.inputs / "notes.txt").write_bytes(oversized)
        change["new"].update(ident(oversized))
        self.assert_failure("LIMIT")
        self.assertFalse(self.export.exists())

    def test_same_input_and_plan_produce_identical_zip_bytes(self):
        first = self.run_assembly("first")
        second = self.run_assembly("second")
        self.assertEqual(first["sha256"], second["sha256"])

    def test_zip_source_is_read_only_and_complete(self):
        source_zip = self.base / "original-server.zip"
        ar.write_zip(self.source, self.report, source_zip)
        identity = rp.hash_file(source_zip)
        result = self.run_assembly(source=source_zip)
        self.assertEqual(result["status"], ar.STATUS)
        self.assertEqual(identity, rp.hash_file(source_zip))

    def test_existing_output_is_never_replaced_or_resumed(self):
        self.export.mkdir()
        existing = self.export / "fixture037"
        existing.mkdir()
        sentinel = existing / "keep.txt"
        sentinel.write_text("untouched")
        self.assert_failure("OUTPUT_CONFLICT")
        self.assertEqual(sentinel.read_text(), "untouched")

    def test_incomplete_or_changed_source_rejected_before_output(self):
        self.plan["input_payload_inventory_sha256"] = "0" * 64
        self.assert_failure("INPUT_MISMATCH")
        self.assertFalse((self.export / "fixture037").exists())

    def test_world_inventory_must_match_every_file(self):
        self.plan["world"]["overworld_regions"] += 1
        self.assert_failure("WORLD_MISMATCH")
        self.assertFalse((self.export / "fixture037").exists())

    def test_wrong_source_client_hash_rejected(self):
        self.plan["client"]["input_sha256"] = "0" * 64
        self.assert_failure("CLIENT_MISMATCH")

    def test_corrupt_replacement_rejected_before_output(self):
        (self.inputs / "core037.jar").write_bytes(b"corrupted")
        self.assert_failure("PIN_MISMATCH")
        self.assertFalse((self.export / "fixture037").exists())

    def test_world_replacement_is_forbidden(self):
        self.plan["replacements"].append({"old": {"path": "Naruto.test/level.dat", **ar.short_identity(self.original["Naruto.test/level.dat"])}, "new": None})
        self.assert_failure("CHANGE_SCOPE")

    def test_java_properties_escaped_and_continued_overrides_are_rejected(self):
        path = self.inputs / "properties-fixture.txt"
        attacks = [
            b"level-name=Naruto.test\nl\\u0065vel-name=DifferentWorld\n",
            b"level-name=Naruto.test\nlev\\\n el-name=DifferentWorld\n",
            b"level-name=Naruto.test\n\\u006cevel-name=DifferentWorld\n",
            b"motd=continued text\\\n level-name=Naruto.test\n",
            b"level-name=Naruto.test \n",
            b"\xef\xbb\xbflevel-name=Naruto.test\n",
        ]
        for raw in attacks:
            with self.subTest(raw=raw):
                path.write_bytes(raw)
                with self.assertRaises(rp.PackError) as caught:
                    ar.check_world_selection(path, "Naruto.test")
                self.assertEqual(caught.exception.code, "WORLD_SELECTION")

    def test_java_properties_normal_escaped_motd_and_real_world_key_work(self):
        path = self.inputs / "properties-fixture.txt"
        valid = [
            b"motd=Hello\\n\\u00a76world\\!\nlevel-name=Naruto.test\nlevel-seed=\nlevel-type=DEFAULT\n",
            b"motd=two\\\n lines\nlevel-name=Naruto.test\n",
            b"l\\u0065vel-name=Naruto.\\u0074est\n",
            b"lev\\\n el-name : Naruto.test\n",
            b"# ignored\\\nlevel-name Naruto.test\n",
        ]
        for raw in valid:
            with self.subTest(raw=raw):
                path.write_bytes(raw)
                ar.check_world_selection(path, "Naruto.test")

    def test_reused_bootstrap_state_marker_requires_new_release(self):
        target = self.source / "vale-update-state/fixture037.done"
        target.parent.mkdir()
        target.write_text("VALE_RELEASE=fixture037\n")
        self.report = rp.audit(self.source)
        self.plan["input_payload_inventory_sha256"] = self.report["payload_inventory_sha256"]
        self.assert_failure("BOOTSTRAP_STATE")

    def test_duplicate_replacement_and_unsafe_path_rejected(self):
        saved = copy.deepcopy(self.plan)
        self.plan["replacements"].append(copy.deepcopy(self.plan["replacements"][0]))
        self.assert_failure("DUPLICATE_CHANGE")
        self.plan = saved
        self.plan["replacements"][0]["new"]["file"] = "../elsewhere.jar"
        self.assert_failure("UNSAFE_PATH")

    def test_case_colliding_destination_rejected(self):
        self.plan["replacements"][0]["new"]["path"] = "MODS/core037.jar"
        self.assert_failure("CHANGE_SCOPE")

    def test_extra_active_mod_is_rejected(self):
        self.plan["server_mods"] = self.plan["server_mods"][:-1]
        self.assert_failure("MOD_ROSTER")
        self.assertTrue((self.export / "fixture037/INCOMPLETE.json").exists())
        self.assertFalse((self.export / "fixture037/ASSEMBLY.json").exists())

    def test_duplicate_actual_mod_id_rejected(self):
        other = mod_jar("valeorders", "0.3.7")
        self.plan["replacements"].append(self.change("mods/server-only.jar", self.server_only, "mods/duplicate.jar", other, "duplicate.jar"))
        self.plan["server_mods"][-1] = self.roster("mods/duplicate.jar", other, "valeorders", "0.3.7")
        self.assert_failure("DUPLICATE_MOD")

    def test_mod_metadata_version_mismatch_is_rejected(self):
        self.plan["server_mods"][0]["mods"]["valeorders"] = "invented-version"
        self.assert_failure("MOD_IDENTITY")

    def test_shared_client_server_jar_mismatch_rejected(self):
        different = archive_bytes({"mcmod.info": encoded([{"modid": "valeorders", "version": "0.3.7"}]), "different.txt": b"changed behavior"})
        self.plan["client"]["replacements"][0] = self.change("overrides/mods/core036.jar", self.old_core, "overrides/mods/core037.jar", different, "client-core037.jar")
        self.plan["client"]["mods"][0] = self.roster("overrides/mods/core037.jar", different, "valeorders", "0.3.7")
        self.assert_failure("CLIENT_MISMATCH")

    def test_changed_configuration_must_be_bootstrap_pinned(self):
        self.plan["required_configs"] = []
        self.assert_failure("INVALID_PLAN")
        self.assertFalse((self.export / "fixture037").exists())

    def test_missing_bootstrap_configuration_pin_rejected(self):
        raw = self.bootstrap_properties.replace(b"expected.count=4", b"expected.count=3")
        raw = b"\n".join(line for line in raw.split(b"\n") if not line.startswith(b"expected.3."))
        rebuilt = archive_bytes({"vale-release.properties": raw, "META-INF/MANIFEST.MF": b"Main-Class: village.deployment.ServerBootstrap\r\n"})
        (self.inputs / "bootstrap.jar").write_bytes(rebuilt)
        for change in self.plan["replacements"]:
            if change["new"]["file"] == "bootstrap.jar":
                change["new"].update(ident(rebuilt))
        self.plan["bootstrap"]["resource_sha256"] = ident(raw)["sha256"]
        self.assert_failure("BOOTSTRAP_PLAN")

    def test_annotation_only_jar_ids_are_read_without_execution(self):
        self.assertEqual(ar.jar_mods(self.inputs / "encounters037.jar"), {"valeencounters": "0.3.7"})
        self.assertEqual(ar.ClassReader(annotation_class("fixture", "2")).mods(), {"fixture": "2"})

    def test_low_space_fails_before_candidate_directory(self):
        with mock.patch.object(rp.shutil, "disk_usage", return_value=type("Space", (), {"free": 0})()):
            self.assert_failure("INSUFFICIENT_SPACE")
        self.assertFalse((self.export / "fixture037").exists())

    def test_copy_corruption_leaves_incomplete_candidate_without_archive(self):
        real_copy = ar.copy_checked
        def corrupt(source, target, expected):
            real_copy(source, target, expected)
            if target.as_posix().endswith("Naruto.test/level.dat"):
                target.write_bytes(b"corrupted output")
        with mock.patch.object(ar, "copy_checked", side_effect=corrupt):
            self.assert_failure("WORLD_MISMATCH")
        self.assertTrue((self.export / "fixture037/INCOMPLETE.json").exists())
        self.assertFalse(list((self.export / "fixture037").glob("*-server-candidate.zip")))

    def test_source_mutation_during_copy_is_detected(self):
        real_copy_input = ar.copy_input
        def mutate(source, report, target):
            real_copy_input(source, report, target)
            if source == self.source:
                (self.source / "Naruto.test/region/r.0.0.mca").write_bytes(b"concurrent server save")
        with mock.patch.object(ar, "copy_input", side_effect=mutate):
            with self.assertRaises(rp.PackError) as caught:
                self.run_assembly()
        self.assertEqual(caught.exception.code, "SOURCE_CHANGED")
        self.assertTrue((self.export / "fixture037/INCOMPLETE.json").exists())
        self.assertFalse((self.export / "fixture037/ASSEMBLY.json").exists())


if __name__ == "__main__":
    unittest.main()
