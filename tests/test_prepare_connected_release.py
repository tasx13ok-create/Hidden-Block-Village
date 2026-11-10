"""Checked preparation fixtures; no real world, Windows process, or game run."""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import assemble_release as ar
import package_bootstrap as bp
import prepare_connected_release as pr
import release_pack as rp


def jar(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, raw in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, raw)
    return output.getvalue()


def mod(mid, version, payload=b"fixture"):
    return jar({"mcmod.info": rp.canonical({"modList": [{"modid": mid, "version": version}]}),
                "assets/example/fixture.bin": payload})


def ident(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


class ConnectedPreparationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "complete-server"
        self.inputs = self.root / "reviewed-inputs"
        self.inputs.mkdir()
        self.export = self.root / "Vale-Export"
        self.contract_path = self.inputs / "connected-contract.json"
        self.transfer_path = self.inputs / "artifact-transfer.json"
        self.inventory_path = self.inputs / "source-inventory.json"
        self.old_core = mod("valeorders", "0.3.6")
        self.new_core = mod("valeorders", "0.3.8", os.urandom(300000))
        self.encounters = mod("valeencounters", "0.3.6")
        self.external_client = mod("clientfixture", "1.0")
        self.prior_retired = {"mods/legacy-naruto.jar": "c" * 64, "plugins/CrazyCrates/Crates/Basic.yml": "d" * 64}
        self.prior_plan = ("release=synthetic036\nexpected.count=2\nexpected.0.path=mods/core036.jar\nexpected.0.sha256=" + ident(self.old_core)["sha256"] +
                           "\nexpected.1.path=mods/encounters.jar\nexpected.1.sha256=" + ident(self.encounters)["sha256"] +
                           "\nretired.count=2\nretired.0.path=mods/legacy-naruto.jar\nretired.0.sha256=" + "c" * 64 +
                           "\nretired.1.path=plugins/CrazyCrates/Crates/Basic.yml\nretired.1.sha256=" + "d" * 64 + "\n").encode()
        self.launcher_entries = {bp.MANIFEST: b"Manifest-Version: 1.0\r\nMain-Class: village.deployment.ServerBootstrap\r\nImplementation-Title: Retained upstream fixture\r\n\r\n",
                                 "upstream/Retained.bin": b"Exact untouched upstream payload", bp.RESOURCE: self.prior_plan,
                                 bp.MAIN_CLASS_PATH: b"old owned wrapper fixture"}
        self.launcher = jar(self.launcher_entries)
        self.wrapper = jar({bp.MANIFEST: b"Manifest-Version: 1.0\r\n\r\n",
                            bp.MAIN_CLASS_PATH: b"\xca\xfe\xba\xbe\x00\x00\x00\x34synthetic-owned-main"})
        self.manifest = {"name": "Synthetic client", "version": "0.3.6", "manifestType": "minecraftModpack", "manifestVersion": 1,
                         "minecraft": {"version": "1.12.2", "modLoaders": [{"id": "forge-14.23.5.2859", "primary": True}]},
                         "overrides": "overrides", "files": []}
        self.old_server_note = b"Historical server acceptance claim; not a new result.\n"
        self.old_client_notes = {"START-HERE.txt": b"Historical Core 0.3.3 client instructions.\n",
                                 "CLIENT-INSTALL.txt": b"Historical Core 0.3.6 installation instructions.\n"}
        self.client = jar({"manifest.json": rp.canonical(self.manifest),
                           "overrides/mods/core036.jar": self.old_core,
                           "overrides/mods/encounters.jar": self.encounters,
                           "overrides/mods/client-only.jar": self.external_client,
                           "overrides/optionsshaders.txt": b"shaderPack=Solas.zip\n",
                           "CLIENT-PINS.json": b"old acceptance must move aside", **self.old_client_notes})
        self.original_files = {
            "START-HERE.txt": self.old_server_note,
            "mods/core036.jar": self.old_core, "mods/encounters.jar": self.encounters,
            "server.jar": self.launcher, "Magma-fixture-server.jar": self.launcher,
            "plugins/CrazyCrates.jar": b"bounded synthetic plugin fixture",
            "plugins/CrazyCrates/config.yml": b"enabled: true\n",
            "plugins/CrazyCrates/Crates/Vale.yml": b"Crate:\n  Name: Vale\n",
            "config/vale.cfg": b"enabled=true\n", "server.properties": b"level-name=Naruto.test\n",
            "Naruto.test/level.dat": b"synthetic saved NBT substitute, not a real world",
            "Naruto.test/region/r.0.0.mca": bytes(range(256)) * 20,
            "Naruto.test/DIM-1/region/r.0.0.mca": b"retained nether fixture",
            "Naruto.test/playerdata/player.dat": b"retained private player fixture",
            "plugins/other/player-state.dat": b"unselected plugin state",
            "client-install/Client.zip": self.client,
            "release/OLD-ACCEPTANCE.json": b"historical only",
            "release/release-plan.properties": self.prior_plan,
        }
        for name, raw in self.original_files.items():
            p = self.source / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(raw)
        (self.source / "Naruto.test/empty-directory").mkdir()
        self.before = rp.audit(self.source)
        self.inventory = {"schema": "vale.connected-source-inventory/v1", "entries": self.before["entries"]}
        self.inventory_path.write_bytes(rp.canonical(self.inventory))
        self.transfer = {"schema": "vale.artifact-transfer/v1", "source_commit": "a" * 40,
                         "source_input_sha256": "b" * 64, "part_max_bytes": pr.PART_LIMIT, "artifacts": []}
        for filename, raw, module, role in (("core038.jar", self.new_core, "vale-orders", "runtime_mod"),
                                             ("wrapper.jar", self.wrapper, "server-bootstrap", "bootstrap_library")):
            parts = []
            for index, offset in enumerate(range(0, len(raw), pr.PART_LIMIT)):
                chunk = raw[offset:offset + pr.PART_LIMIT]
                name = filename + ".part%03d" % index
                (self.inputs / name).write_bytes(chunk)
                parts.append({"file": name, **ident(chunk)})
            self.transfer["artifacts"].append({"module": module, "role": role, "filename": filename, **ident(raw), "parts": parts})
        self.transfer_path.write_bytes(rp.canonical(self.transfer))
        self.contract = {
            "schema": pr.SCHEMA, "candidate_id": "synthetic-connected038",
            "source_checkpoint": {"id": "git:" + "a" * 40, "sha256": "b" * 64},
            "source_inventory": {"file": self.inventory_path.name, **rp.hash_file(self.inventory_path),
                                 "inventory_sha256": self.before["inventory_sha256"],
                                 "payload_inventory_sha256": self.before["payload_inventory_sha256"]},
            "world": {"path": "Naruto.test", **ar.world_identity(self.before["entries"], "Naruto.test")},
            "source_server_mods": [self.roster("mods/core036.jar", self.old_core, "valeorders", "0.3.6"),
                                   self.roster("mods/encounters.jar", self.encounters, "valeencounters", "0.3.6")],
            "source_plugins": [{"path": "plugins/CrazyCrates.jar", **ident(self.original_files["plugins/CrazyCrates.jar"])}],
            "source_client": {"path": "client-install/Client.zip", **ident(self.client),
                              "mods": [self.roster("overrides/mods/core036.jar", self.old_core, "valeorders", "0.3.6"),
                                       self.roster("overrides/mods/encounters.jar", self.encounters, "valeencounters", "0.3.6"),
                                       self.roster("overrides/mods/client-only.jar", self.external_client, "clientfixture", "1.0")],
                              "manifest": ident(rp.canonical(self.manifest))},
            "transfer": {"file": self.transfer_path.name, **rp.hash_file(self.transfer_path)},
            "server_changes": [{"old": "mods/core036.jar", "new": {"path": "mods/core038.jar", "artifact": "core038.jar"}}],
            "server_mods": [self.roster("mods/core038.jar", self.new_core, "valeorders", "0.3.8"),
                            self.roster("mods/encounters.jar", self.encounters, "valeencounters", "0.3.6")],
            "client_changes": [{"old": "overrides/mods/core036.jar", "new": {"path": "overrides/mods/core038.jar", "artifact": "core038.jar"}}],
            "client_mods": [self.roster("overrides/mods/core038.jar", self.new_core, "valeorders", "0.3.8"),
                            self.roster("overrides/mods/encounters.jar", self.encounters, "valeencounters", "0.3.6"),
                            self.roster("overrides/mods/client-only.jar", self.external_client, "clientfixture", "1.0")],
            "client_version": "0.3.8", "client_forge": "14.23.5.2859",
            "bootstrap": {"aliases": ["server.jar", "Magma-fixture-server.jar"], "wrapper_artifact": "wrapper.jar",
                          "input": {"kind": "source", "path": "Magma-fixture-server.jar", **ident(self.launcher)}},
            "prior_bootstrap_plan": {"path": "release/release-plan.properties", **ident(self.prior_plan)},
            "required_configs": ["config/vale.cfg", "plugins/CrazyCrates/config.yml", "plugins/CrazyCrates/Crates/Vale.yml"],
        }

    @staticmethod
    def roster(path, raw, mid, version):
        return {"path": path, **ident(raw), "mods": {mid: version}}

    def write_contract(self):
        self.contract_path.write_bytes(rp.canonical(self.contract))
        return rp.hash_file(self.contract_path)["sha256"]

    def write_transfer(self):
        self.transfer_path.write_bytes(rp.canonical(self.transfer))
        self.contract["transfer"].update(rp.hash_file(self.transfer_path))

    def run_prepare(self, **kwargs):
        fingerprint = self.write_contract()
        return pr.prepare(self.source, self.contract_path, fingerprint, self.export, **kwargs)

    def assert_source_unchanged(self):
        self.assertEqual(rp.audit(self.source)["entries"], self.before["entries"])

    def refuse(self, code):
        with self.assertRaises(rp.PackError) as caught:
            self.run_prepare()
        self.assertEqual(caught.exception.code, code, str(caught.exception))
        self.assert_source_unchanged()

    def installation_notes(self):
        new_notes = {"server": {"START-HERE.txt": b"Core 0.3.8 candidate. Preserve Naruto.test. Runtime acceptance NOT_RUN.\n"},
                     "client": {"START-HERE.txt": b"Import this matching Core 0.3.8 candidate client. Acceptance NOT_RUN.\n",
                                "CLIENT-INSTALL.txt": b"Minecraft 1.12.2 and Forge 14.23.5.2859; use the included overrides.\n"}}
        old_notes = {"server": {"START-HERE.txt": self.old_server_note}, "client": self.old_client_notes}
        self.contract["installation_notes"] = {"server": [], "client": []}
        for side, rows in new_notes.items():
            for path, raw in rows.items():
                filename = side + "-" + path
                (self.inputs / filename).write_bytes(raw)
                self.contract["installation_notes"][side].append({
                    "old": {"path": path, **ident(old_notes[side][path])},
                    "new": {"path": path, "file": filename, **ident(raw)}})
        return new_notes

    def test_complete_preparation_preserves_originals_and_pins_actual_outputs(self):
        result = self.run_prepare()
        self.assertEqual(result["status"], ar.STATUS)
        self.assertEqual(result["runtime_acceptance"], "NOT_RUN")
        self.assert_source_unchanged()
        candidate = Path(result["output"])
        preparation = Path(result["preparation"])
        self.assertFalse((preparation / "INCOMPLETE.json").exists())
        self.assertFalse((candidate / "INCOMPLETE.json").exists())
        self.assertEqual((preparation / "artifacts/core038.jar").read_bytes(), self.new_core)
        self.assertEqual((candidate / "server/mods/core038.jar").read_bytes(), self.new_core)
        self.assertTrue((candidate / "server/Naruto.test/empty-directory").is_dir())
        self.assertFalse((candidate / "server/mods/core036.jar").exists())
        self.assertEqual((candidate / "server/Naruto.test/playerdata/player.dat").read_bytes(), self.original_files["Naruto.test/playerdata/player.dat"])
        self.assertEqual((candidate / "Client-Candidate.zip").read_bytes(), (preparation / "Client-Prepared.zip").read_bytes())
        with zipfile.ZipFile(candidate / "Client-Candidate.zip") as z:
            self.assertNotIn("CLIENT-PINS.json", z.namelist())
            self.assertEqual(z.read("overrides/optionsshaders.txt"), b"shaderPack=Solas.zip\n")
            self.assertEqual(json.loads(z.read("manifest.json"))["version"], "0.3.8")
            self.assertEqual(z.read("overrides/mods/core038.jar"), self.new_core)
            for path, raw in self.old_client_notes.items():
                self.assertEqual(z.read(path), raw)
        self.assertEqual((candidate / "server/START-HERE.txt").read_bytes(), self.old_server_note)
        assembly = json.loads((candidate / "ASSEMBLY.json").read_bytes())
        self.assertNotIn("prior_installation_notes_preserved_outside_candidate", assembly)
        self.assertEqual((candidate / "server/server.jar").read_bytes(), (candidate / "server/Magma-fixture-server.jar").read_bytes())
        with zipfile.ZipFile(candidate / "server/server.jar") as z:
            self.assertEqual(z.read("upstream/Retained.bin"), b"Exact untouched upstream payload")
            embedded = bp.validate_plan(z.read(bp.RESOURCE), self.contract["bootstrap"]["aliases"])
        self.assertEqual(embedded["expected"]["client-install/Client.zip"], rp.hash_file(candidate / "Client-Candidate.zip")["sha256"])
        self.assertIn("plugins/CrazyCrates.jar", embedded["expected"])
        self.assertIn("plugins/CrazyCrates/config.yml", embedded["expected"])
        self.assertEqual(embedded["retired"], {**self.prior_retired, "mods/core036.jar": ident(self.old_core)["sha256"]})
        self.assertTrue((candidate / "prior-evidence/server/release/OLD-ACCEPTANCE.json").is_file())
        self.assertTrue((candidate / "prior-evidence/client/CLIENT-PINS.json").is_file())

    def test_installation_notes_preserve_originals_and_bind_client_wrapper_and_inventories(self):
        notes = self.installation_notes()
        result = self.run_prepare()
        candidate, preparation = Path(result["output"]), Path(result["preparation"])
        self.assert_source_unchanged()
        self.assertEqual(result["runtime_acceptance"], "NOT_RUN")
        self.assertEqual((candidate / "server/START-HERE.txt").read_bytes(), notes["server"]["START-HERE.txt"])
        self.assertEqual((candidate / "prior-evidence/server/START-HERE.txt").read_bytes(), self.old_server_note)
        client_bytes = (candidate / "Client-Candidate.zip").read_bytes()
        self.assertEqual(client_bytes, (preparation / "Client-Prepared.zip").read_bytes())
        self.assertEqual(client_bytes, (candidate / "server/client-install/Client.zip").read_bytes())
        with zipfile.ZipFile(io.BytesIO(client_bytes)) as z:
            for path, raw in notes["client"].items():
                self.assertEqual(z.read(path), raw)
                self.assertEqual((candidate / "prior-evidence/client" / path).read_bytes(), self.old_client_notes[path])
                self.assertEqual((preparation / "prior-client-evidence" / path).read_bytes(), self.old_client_notes[path])
            self.assertFalse(any(path.startswith("prior-evidence/") for path in z.namelist()))
        for side, rows in notes.items():
            for path, raw in rows.items():
                self.assertEqual((preparation / "installation-notes" / side / path).read_bytes(), raw)
        with zipfile.ZipFile(candidate / "server/server.jar") as z:
            embedded = bp.validate_plan(z.read(bp.RESOURCE), self.contract["bootstrap"]["aliases"])
        self.assertEqual((candidate / "server/server.jar").read_bytes(), (candidate / "server/Magma-fixture-server.jar").read_bytes())
        self.assertEqual(embedded["expected"]["START-HERE.txt"], ident(notes["server"]["START-HERE.txt"])["sha256"])
        self.assertEqual(embedded["expected"]["client-install/Client.zip"], ident(client_bytes)["sha256"])
        self.assertEqual(embedded["retired"], {**self.prior_retired, "mods/core036.jar": ident(self.old_core)["sha256"]})
        final_inventory = rp.audit(candidate / "server")
        self.assertEqual(ar.world_identity(self.before["entries"], "Naruto.test"),
                         ar.world_identity(final_inventory["entries"], "Naruto.test"))
        self.assertEqual(final_inventory["entries"], json.loads((candidate / "candidate-inventory.json").read_bytes())["entries"])
        self.assertEqual(final_inventory["entries"], rp.audit(Path(result["archive"]))["entries"])
        client_inventory = json.loads((candidate / "client-inventory.json").read_bytes())
        for path, raw in notes["client"].items():
            self.assertEqual(ar.short_identity(ar.files(client_inventory)[path]), ident(raw))
        assembly = json.loads((candidate / "ASSEMBLY.json").read_bytes())
        preserved = assembly["prior_installation_notes_preserved_outside_candidate"]
        self.assertEqual(preserved["server"], [{"path": "START-HERE.txt", **ident(self.old_server_note)}])
        self.assertEqual(preserved["client"], [{"path": path, **ident(raw)} for path, raw in self.old_client_notes.items()])
        self.assertEqual(assembly["payload_inventory_sha256"], final_inventory["payload_inventory_sha256"])
        with zipfile.ZipFile(result["archive"]) as z:
            self.assertEqual(z.read("START-HERE.txt"), notes["server"]["START-HERE.txt"])
            self.assertFalse(any(path.startswith("prior-evidence/") for path in z.namelist()))
        # A completed note-bearing handoff is no more resumable than any other.
        before_outputs = rp.audit(candidate)["entries"], rp.audit(preparation)["entries"]
        self.refuse("OUTPUT_CONFLICT")
        self.assertEqual(before_outputs, (rp.audit(candidate)["entries"], rp.audit(preparation)["entries"]))

    def test_installation_notes_update_a_prior_note_pin_without_losing_other_pins(self):
        notes = self.installation_notes()
        self.prior_plan = self.prior_plan.replace(b"expected.count=2", b"expected.count=3") + (
            "expected.2.path=START-HERE.txt\nexpected.2.sha256=" + ident(self.old_server_note)["sha256"] + "\n").encode()
        self.launcher_entries[bp.RESOURCE] = self.prior_plan
        self.launcher = jar(self.launcher_entries)
        for alias in self.contract["bootstrap"]["aliases"]:
            (self.source / alias).write_bytes(self.launcher)
        (self.source / "release/release-plan.properties").write_bytes(self.prior_plan)
        self.contract["bootstrap"]["input"].update(ident(self.launcher))
        self.contract["prior_bootstrap_plan"].update(ident(self.prior_plan))
        self.refresh_reviewed_source()
        result = self.run_prepare()
        with zipfile.ZipFile(Path(result["output"]) / "server/server.jar") as z:
            embedded = bp.validate_plan(z.read(bp.RESOURCE), self.contract["bootstrap"]["aliases"])
        self.assertEqual(embedded["expected"]["START-HERE.txt"], ident(notes["server"]["START-HERE.txt"])["sha256"])
        self.assertEqual(embedded["expected"]["mods/encounters.jar"], ident(self.encounters)["sha256"])
        self.assertEqual(embedded["retired"], {**self.prior_retired, "mods/core036.jar": ident(self.old_core)["sha256"]})
        self.assert_source_unchanged()

    def test_installation_note_old_and_new_pins_are_exact_before_output(self):
        self.installation_notes()
        original = copy.deepcopy(self.contract["installation_notes"])
        for side in ("server", "client"):
            for version in ("old", "new"):
                for field in ("bytes", "sha256"):
                    with self.subTest(side=side, version=version, field=field):
                        self.contract["installation_notes"] = copy.deepcopy(original)
                        row = self.contract["installation_notes"][side][0][version]
                        row[field] = row[field] + 1 if field == "bytes" else "0" * 64
                        self.refuse("PIN_MISMATCH")
                        self.assertFalse(self.export.exists())

    def test_installation_note_scope_is_exact_and_replacement_only(self):
        self.installation_notes()
        original = copy.deepcopy(self.contract["installation_notes"])
        forbidden = (("server", "CLIENT-INSTALL.txt"), ("server", "README.txt"),
                     ("server", "Naruto.test/level.dat"), ("server", "config/vale.cfg"),
                     ("server", "mods/core036.jar"), ("client", "overrides/START-HERE.txt"),
                     ("client", "manifest.json"), ("client", "overrides/mods/core036.jar"),
                     ("client", "start-here.txt"))
        for side, path in forbidden:
            with self.subTest(side=side, path=path):
                self.contract["installation_notes"] = copy.deepcopy(original)
                row = self.contract["installation_notes"][side][0]
                row["old"]["path"] = row["new"]["path"] = path
                self.refuse("CHANGE_SCOPE")
                self.assertFalse(self.export.exists())
        for side in ("server", "client"):
            for missing in ("old", "new"):
                with self.subTest(side=side, missing=missing):
                    self.contract["installation_notes"] = copy.deepcopy(original)
                    self.contract["installation_notes"][side][0][missing] = None
                    self.refuse("CHANGE_SCOPE")
            self.contract["installation_notes"] = copy.deepcopy(original)
            self.contract["installation_notes"][side].append(copy.deepcopy(original[side][0]))
            self.refuse("DUPLICATE_CHANGE")
        self.contract["installation_notes"] = copy.deepcopy(original)
        self.contract["installation_notes"]["client"][0]["new"]["path"] = "CLIENT-INSTALL.txt"
        self.refuse("CHANGE_SCOPE")
        self.assertFalse(self.export.exists())

    def test_installation_notes_do_not_extend_jar_transfer_or_mod_change_scope(self):
        self.installation_notes()
        self.contract["server_changes"][0]["old"] = "START-HERE.txt"
        self.refuse("CHANGE_SCOPE")
        self.contract["server_changes"][0]["old"] = "mods/core036.jar"
        self.transfer["artifacts"][0]["role"] = "installation_note"
        self.write_transfer()
        self.refuse("INVALID_TRANSFER")
        self.assertFalse(self.export.exists())

    def test_changed_source_fails_without_learning_new_inventory(self):
        p = self.source / "Naruto.test/playerdata/player.dat"
        original = p.read_bytes()
        p.write_bytes(b"a real player saved different progress")
        with self.assertRaises(rp.PackError) as caught:
            self.run_prepare()
        self.assertEqual(caught.exception.code, "SOURCE_INVENTORY_MISMATCH")
        self.assertEqual(p.read_bytes(), b"a real player saved different progress")
        self.assertFalse(self.export.exists())
        p.write_bytes(original)
        self.assert_source_unchanged()

    def test_contract_digest_is_required_and_not_inferred(self):
        self.write_contract()
        with self.assertRaises(rp.PackError) as caught:
            pr.prepare(self.source, self.contract_path, "0" * 64, self.export)
        self.assertEqual(caught.exception.code, "CONTRACT_MISMATCH")
        self.assertFalse(self.export.exists())
        self.assert_source_unchanged()

    def test_corrupt_part_fails_before_preparation_output(self):
        part = self.inputs / self.transfer["artifacts"][0]["parts"][1]["file"]
        part.write_bytes(b"interrupted transfer")
        self.refuse("TRANSFER_MISMATCH")
        self.assertFalse(self.export.exists())

    def test_final_hash_checked_even_when_every_part_is_valid(self):
        self.transfer["artifacts"][0]["sha256"] = "0" * 64
        self.write_transfer()
        self.refuse("TRANSFER_MISMATCH")
        preparation = self.export / (self.contract["candidate_id"] + "-preparation")
        self.assertTrue((preparation / "INCOMPLETE.json").is_file())
        self.assertFalse((preparation / "artifacts/core038.jar").exists())
        self.assertFalse((self.export / self.contract["candidate_id"]).exists())

    def test_duplicate_part_names_and_unsafe_scope_are_rejected(self):
        self.transfer["artifacts"][0]["parts"][1]["file"] = self.transfer["artifacts"][0]["parts"][0]["file"]
        self.write_transfer()
        self.refuse("PATH_COLLISION")
        self.assertFalse(self.export.exists())

    def test_world_replacement_cannot_be_smuggled_into_contract(self):
        self.contract["server_changes"][0]["old"] = "Naruto.test/level.dat"
        self.refuse("CHANGE_SCOPE")
        self.assertFalse(self.export.exists())

    def test_unknown_plugin_is_rejected_even_with_a_complete_source_inventory(self):
        self.contract["source_plugins"] = []
        self.refuse("PLUGIN_ROSTER")
        self.assertFalse(self.export.exists())

    def test_original_client_and_final_metadata_rosters_must_match(self):
        self.contract["source_client"]["mods"][0]["mods"]["valeorders"] = "invented"
        self.refuse("MOD_IDENTITY")
        self.assertFalse((self.export / self.contract["candidate_id"]).exists())

    def test_client_server_must_use_exact_same_mod_bytes(self):
        self.contract["client_changes"] = []
        self.contract["client_mods"] = copy.deepcopy(self.contract["source_client"]["mods"])
        self.refuse("CLIENT_MISMATCH")
        self.assertFalse((self.export / self.contract["candidate_id"]).exists())

    def test_crazycrates_must_be_explicitly_in_required_configs(self):
        self.contract["required_configs"].remove("plugins/CrazyCrates/config.yml")
        self.refuse("CONFIG_PIN")
        self.assertFalse(self.export.exists())

    def test_existing_output_is_never_resumed_or_overwritten(self):
        existing = self.export / self.contract["candidate_id"]
        existing.mkdir(parents=True)
        sentinel = existing / "keep.txt"
        sentinel.write_bytes(b"do not overwrite")
        self.refuse("OUTPUT_CONFLICT")
        self.assertEqual(sentinel.read_bytes(), b"do not overwrite")
        self.assertFalse((self.export / (self.contract["candidate_id"] + "-preparation")).exists())

    def test_output_cannot_be_inside_original_source(self):
        self.export = self.source / "Vale-Export"
        self.refuse("OUTPUT_SEPARATION")
        self.assertFalse(self.export.exists())

    def test_external_launcher_requires_exact_explicit_identity(self):
        external = self.root / "original-upstream.jar"
        upstream = jar({bp.MANIFEST: self.launcher_entries[bp.MANIFEST].replace(bp.OWN_MAIN.encode(), bp.FORGE_MAIN.encode()),
                        "upstream/Retained.bin": b"Exact untouched upstream payload"})
        external.write_bytes(upstream)
        self.contract["bootstrap"]["input"] = {"kind": "external", **ident(upstream)}
        result = self.run_prepare(launcher=external)
        self.assertEqual(result["status"], ar.STATUS)
        self.assertEqual(external.read_bytes(), upstream)
        self.assert_source_unchanged()

    def refresh_reviewed_source(self):
        self.before = rp.audit(self.source)
        self.inventory = {"schema": "vale.connected-source-inventory/v1", "entries": self.before["entries"]}
        self.inventory_path.write_bytes(rp.canonical(self.inventory))
        self.contract["source_inventory"].update(**rp.hash_file(self.inventory_path),
            inventory_sha256=self.before["inventory_sha256"], payload_inventory_sha256=self.before["payload_inventory_sha256"])

    def test_new_mod_cannot_reintroduce_a_historically_retired_path(self):
        self.contract["server_changes"][0]["new"]["path"] = "mods/legacy-naruto.jar"
        self.contract["server_mods"][0]["path"] = "mods/legacy-naruto.jar"
        self.refuse("PATH_COLLISION")
        self.assertFalse((self.export / self.contract["candidate_id"]).exists())

    def test_prior_plan_must_match_actual_wrapper_resource(self):
        changed = self.prior_plan.replace(b"synthetic036", b"different036")
        (self.source / "release/release-plan.properties").write_bytes(changed)
        self.contract["prior_bootstrap_plan"].update(ident(changed))
        self.refresh_reviewed_source()
        self.refuse("BOOTSTRAP_PLAN")
        self.assertFalse(self.export.exists())

    def test_prior_release_id_cannot_be_reused(self):
        self.contract["candidate_id"] = "synthetic036"
        self.refuse("REUSED_RELEASE_ID")
        self.assertFalse(self.export.exists())

    def test_historical_retired_crate_must_already_be_absent(self):
        p = self.source / "plugins/CrazyCrates/Crates/Basic.yml"
        p.write_bytes(b"historical crate reintroduced")
        self.refresh_reviewed_source()
        self.refuse("RETIRED_PRESENT")
        self.assertFalse(self.export.exists())

    def file_link_inventory(self):
        self.inventory_path = self.inputs / "source-inventory.jsonl"
        header = {"type": "header", "version": 2, "operation": "scan_tree", "sourceRootId": "fixture-source",
                  "sourcePath": "work/complete-server", "destinationRootId": "fixture-destination",
                  "destinationPath": "Vale-Export/evidence/source-inventory.jsonl", "hashedFiles": True}
        rows = [header]
        for entry in reversed(self.before["entries"]):
            row = {"type": entry["kind"], "path": entry["path"]}
            if entry["kind"] == "file":
                row.update(size=entry["bytes"], sha256=entry["sha256"], lastModified=1700000000000)
            rows.append(row)
        rows.append({"type": "footer", "files": self.before["file_count"], "directories": self.before["directory_count"],
                     "totalBytes": self.before["total_file_bytes"], "complete": True})
        return rows

    def write_file_link_inventory(self, rows):
        self.inventory_path.write_bytes(b"".join(rp.canonical(r) for r in rows))
        self.contract["source_inventory"].update(file=self.inventory_path.name, **rp.hash_file(self.inventory_path))

    def test_exact_complete_file_link_scan_is_normalized_without_new_approval(self):
        self.write_file_link_inventory(self.file_link_inventory())
        result = self.run_prepare()
        self.assertEqual(result["status"], ar.STATUS)
        self.assertEqual((Path(result["preparation"]) / "reviewed-source-inventory.jsonl").read_bytes(), self.inventory_path.read_bytes())
        self.assert_source_unchanged()

    def test_truncated_or_inconsistent_file_link_scan_is_rejected(self):
        rows = self.file_link_inventory()
        for field, wrong in (("complete", False), ("files", rows[-1]["files"] - 1), ("totalBytes", 1)):
            with self.subTest(field=field):
                changed = copy.deepcopy(rows)
                changed[-1][field] = wrong
                self.write_file_link_inventory(changed)
                self.refuse("INVALID_INVENTORY")
        self.assertFalse(self.export.exists())


if __name__ == "__main__":
    unittest.main()
