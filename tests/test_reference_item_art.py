"""Synthetic render-contract and source-integrity checks; no reference payloads."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import warnings
import zipfile
import zlib

SPEC = importlib.util.spec_from_file_location("reference_item_art", Path(__file__).resolve().parents[1] / "tools/reference_item_art.py")
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)

def png(red=20):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress((b"\0" + bytes([red, 50, 80, 255]) * 32) * 32)) + chunk(b"IEND", b"")

def encode(value):
    return json.dumps(value).encode()

def write_zip(path, members):
    with zipfile.ZipFile(path, "w") as archive:
        for key, data in members.items():
            archive.writestr(key, encode(data) if isinstance(data, dict) else data)

class Fixture:
    def __init__(self, root, extra=None):
        self.root = Path(root)
        self.mods = self.root / "mods"
        self.mods.mkdir()
        self.jar = self.mods / "synthetic.jar"
        self.mc = self.root / "minecraft.jar"
        self.models = {
            "assets/example/models/item/base.json": {"parent": "item/handheld", "textures": {"layer0": "example:items/shared"}, "display": {"firstperson_righthand": {"rotation": [0, 90, 4], "scale": [1, 1, 1]}}, "overrides": [{"predicate": {"pulling": 1, "pull": 0.65}, "model": "example:item/charged"}]},
            "assets/example/models/item/charged.json": {"parent": "item/generated", "textures": {"layer0": "example:items/shared"}},
            "assets/example/textures/items/shared.png": png(),
        }
        if extra: self.models.update(extra)
        self.mc_models = {
            "assets/minecraft/models/item/generated.json": {"parent": "builtin/generated", "display": {"gui": {"rotation": [0, 0, 0]}}},
            "assets/minecraft/models/item/handheld.json": {"parent": "item/generated", "display": {"thirdperson_righthand": {"rotation": [0, -90, 55]}}},
        }
        self.inventory = {"current_items": [], "references": [{"jar": self.jar.name, "sha256": "", "items": [{"model_id": "example:item/" + name, "path": "assets/example/models/item/" + name + ".json"} for name in ("base", "charged")]}], "counts": {"reference_item_model_entries": 2}}
        self.equipment = {"equipment": []}
        self.art = self.root / "own-icons.zip"
        self.art_members = {"example_item_base.png": png(40), "example_item_charged.png": png(80)}
        self.sync()

    def sync(self):
        write_zip(self.jar, self.models)
        write_zip(self.mc, self.mc_models)
        write_zip(self.art, self.art_members)
        self.inventory["references"][0]["sha256"] = m.file_digest(self.jar)
        self.provenance = {"archives": [{"name": self.art.name, "sha256": m.file_digest(self.art), "members": [{"path": name, "sha256": m.digest(data), "rgba_sha256": m.digest(data)} for name, data in self.art_members.items()]}]}

    def run(self, folder="pack"):
        return m.build(self.inventory, self.equipment, self.art, self.provenance, self.mods, self.mc, self.root / folder)

    def contracts(self, model="example:item/base"):
        sources = m.Sources()
        sources.add(self.jar)
        sources.add(self.mc)
        try: return m.sprite_contract(sources, model)
        finally: sources.close()

class ReferenceItemArtChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.f = Fixture(self.temp.name)

    def tearDown(self): self.temp.cleanup()

    def skip_contract(self, reason):
        self.f.sync()
        with self.assertRaises(m.Skip) as error: self.f.contracts()
        self.assertEqual(reason, error.exception.reason)

    def test_flat_pack_preserves_charged_state_display_and_source(self):
        before = {path: path.read_bytes() for path in (self.f.jar, self.f.mc, self.f.art)}
        report = self.f.run()
        self.assertEqual(2, report["applied_models"])
        self.assertEqual(5, report["pack"]["entries"])
        with zipfile.ZipFile(self.f.root / "pack" / report["pack"]["file"]) as archive:
            base = json.loads(archive.read("assets/example/models/item/base.json"))
            old = self.f.models["assets/example/models/item/base.json"]
            self.assertEqual(old["parent"], base["parent"])
            self.assertEqual(old["display"], base["display"])
            self.assertEqual(old["overrides"], base["overrides"])
            self.assertTrue(base["textures"]["layer0"].startswith("vale_reference_art:"))
            self.assertNotIn("assets/example/textures/items/shared.png", archive.namelist())
            self.assertEqual(2, len([n for n in archive.namelist() if n.endswith(".png")]))
            self.assertEqual(3, json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"])
        for path, value in before.items(): self.assertEqual(value, path.read_bytes())

    def test_reproducible_zip(self):
        self.assertEqual(self.f.run("one")["pack"]["sha256"], self.f.run("two")["pack"]["sha256"])

    def test_output_reuse_refused(self):
        self.f.run()
        with self.assertRaisesRegex(m.PackError, "new directory"): self.f.run()

    def test_changed_jar_refused(self):
        with self.f.jar.open("ab") as stream: stream.write(b"changed")
        with self.assertRaisesRegex(m.PackError, "hash mismatch"): self.f.run()

    def test_changed_art_refused(self):
        with self.f.art.open("ab") as stream: stream.write(b"changed")
        with self.assertRaisesRegex(m.PackError, "archive identity"): self.f.run()

    def test_changed_member_pin_refused(self):
        self.f.provenance["archives"][0]["members"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(m.PackError, "member hash"): self.f.run()

    def test_bad_art_member_missing_manifest_refused(self):
        self.f.provenance["archives"][0]["members"].pop()
        with self.assertRaisesRegex(m.PackError, "member manifest"): self.f.run()

    def test_missing_icon_skips_exact_row(self):
        self.f.art_members.pop("example_item_base.png")
        self.f.sync()
        report = self.f.run()
        self.assertEqual(1, report["applied_models"])
        self.assertEqual("missing_exact_owner_icon", report["rows"][0]["reason"])

    def test_no_flattened_filename_collisions(self):
        self.f.inventory["references"][0]["items"] += [{"model_id": "example:item_base", "path": "assets/example/models/item_base.json"}]
        self.f.inventory["counts"]["reference_item_model_entries"] = 3
        with self.assertRaisesRegex(m.PackError, "filename collision"): self.f.run()

    def test_duplicate_models_rejected(self):
        self.f.inventory["references"][0]["items"][1] = self.f.inventory["references"][0]["items"][0].copy()
        with self.assertRaisesRegex(m.PackError, "Duplicate reference"): self.f.run()

    def test_count_mismatch(self):
        self.f.inventory["counts"]["reference_item_model_entries"] = 3
        with self.assertRaisesRegex(m.PackError, "count mismatch"): self.f.run()

    def test_exact_current_model_preserved_without_namespace_loss(self):
        self.f.inventory["current_items"] = [{"id": "example:base"}]
        report = self.f.run()
        self.assertEqual(1, report["applied_models"])
        self.assertEqual("current_project_model_preserved", report["rows"][0]["reason"])
        self.assertEqual("applied", report["rows"][1]["status"])

    def test_parent_geometry_retained(self):
        self.f.mc_models["assets/minecraft/models/item/handheld.json"]["elements"] = []
        self.skip_contract("explicit_or_inherited_3d_geometry")

    def test_builtin_entity_retained(self):
        self.f.models["assets/example/models/item/base.json"]["parent"] = "builtin/entity"
        self.skip_contract("custom_builtin_renderer")

    def test_block_parent_retained(self):
        self.f.models["assets/example/models/item/base.json"]["parent"] = "block/cube_all"
        self.skip_contract("block_parent")

    def test_custom_loader_contract_retained(self):
        self.f.models["assets/example/models/item/base.json"]["loader"] = "example:renderer"
        self.skip_contract("custom_model_contract")

    def test_parent_cycle_retained(self):
        self.f.models["assets/example/models/item/base.json"]["parent"] = "example:item/base"
        self.skip_contract("parent_cycle_or_depth")

    def test_missing_parent_retained(self):
        self.f.models["assets/example/models/item/base.json"].pop("parent")
        self.skip_contract("missing_generated_parent")

    def test_multiple_layers_retained(self):
        self.f.models["assets/example/models/item/base.json"]["textures"]["layer1"] = "example:items/shared"
        self.skip_contract("multiple_or_missing_sprite_layers")

    def test_inherited_multiple_layers_retained(self):
        self.f.mc_models["assets/minecraft/models/item/generated.json"]["textures"] = {"layer1": "example:items/shared"}
        self.skip_contract("multiple_or_missing_sprite_layers")

    def test_animation_retained(self):
        self.f.models["assets/example/textures/items/shared.png.mcmeta"] = {"animation": {"frametime": 2}}
        self.skip_contract("animated_texture_retained")

    def test_alias_cycle_retained(self):
        self.f.models["assets/example/models/item/base.json"]["textures"] = {"layer0": "#other", "other": "#layer0"}
        self.skip_contract("texture_alias_cycle")

    def test_alias_resolves_and_original_keys_retained(self):
        self.f.models["assets/example/models/item/base.json"]["textures"] = {"layer0": "#other", "other": "example:items/shared"}
        self.f.sync()
        self.f.contracts()
        result = self.f.run()
        with zipfile.ZipFile(self.f.root / "pack" / result["pack"]["file"]) as archive:
            value = json.loads(archive.read("assets/example/models/item/base.json"))
            self.assertEqual("example:items/shared", value["textures"]["other"])

    def test_missing_charged_model_retained(self):
        self.f.models.pop("assets/example/models/item/charged.json")
        self.skip_contract("missing_source_resource")

    def test_case_mismatch_fails_closed(self):
        self.f.models["assets/example/textures/items/Shared.png"] = self.f.models.pop("assets/example/textures/items/shared.png")
        self.skip_contract("source_case_mismatch")

    def test_case_collision_fails_closed(self):
        self.f.models["assets/example/textures/items/Shared.png"] = png()
        self.skip_contract("source_case_collision")

    def test_two_owners_fail_closed(self):
        self.f.sync()
        sources = m.Sources()
        sources.add(self.f.jar, label="one")
        sources.add(self.f.jar, label="two")
        try:
            with self.assertRaises(m.Skip) as error: sources.read("assets/example/models/item/base.json")
            self.assertEqual("ambiguous_resource_owner", error.exception.reason)
        finally: sources.close()

    def test_duplicate_zip_member_rejected(self):
        path = self.f.root / "duplicate.jar"
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("assets/example/models/item/test.json", b"{}")
                archive.writestr("assets/example/models/item/test.json", b"{}")
        sources = m.Sources()
        try:
            with self.assertRaisesRegex(m.PackError, "Duplicate source"): sources.add(path)
        finally: sources.close()

    def test_invalid_model_json_retained(self):
        self.f.models["assets/example/models/item/base.json"] = b'{"parent":"item/generated","parent":"item/generated"}'
        self.skip_contract("invalid_model_json")

    def test_nonfinite_model_json_retained(self):
        self.f.models["assets/example/models/item/base.json"] = b'{"parent":"item/generated","display":{"x":NaN}}'
        self.skip_contract("invalid_model_json")

    def test_custom_item_renderer_retained(self):
        self.f.models["example/Item.class"] = b"synthetic-class-marker:getTileEntityItemStackRenderer"
        self.f.equipment["equipment"] = [{"jar": self.f.jar.name, "model_id": "example:item/base", "class_names": ["example/Item"]}]
        self.f.sync()
        self.assertEqual("direct_custom_item_renderer_retained", self.f.run()["rows"][0]["reason"])

    def test_path_traversal_and_invalid_references(self):
        for path in ("../unsafe", "/absolute", "assets//a", "assets/a/../b", "x\\y", "C:/p"):
            with self.subTest(path=path), self.assertRaises(m.PackError): m.path_ok(path)
        for value in ("example:../item/a", "example:item//a", "Example:item/a", None):
            with self.subTest(value=value), self.assertRaises(m.Skip): m.resource(value)

    def test_texture_metadata_preserved_on_owned_texture_only(self):
        self.f.models["assets/example/textures/items/shared.png.mcmeta"] = {"texture": {"blur": False, "clamp": True}}
        self.f.sync()
        result = self.f.run()
        with zipfile.ZipFile(self.f.root / "pack" / result["pack"]["file"]) as archive:
            names = [n for n in archive.namelist() if n.endswith(".png.mcmeta")]
            self.assertEqual(2, len(names))
            self.assertTrue(all(n.startswith("assets/vale_reference_art/") for n in names))
            self.assertEqual({"texture": {"blur": False, "clamp": True}}, json.loads(archive.read(names[0])))

    def test_shared_parent_model_retained(self):
        self.f.models["assets/example/models/item/customchild.json"] = {"parent": "example:item/base", "elements": []}
        self.f.sync()
        report = self.f.run()
        self.assertEqual("shared_parent_or_world_model_retained", report["rows"][0]["reason"])
        self.assertEqual("applied", report["rows"][1]["status"])

    def test_blockstate_item_reference_retained(self):
        self.f.models["assets/example/blockstates/synthetic.json"] = {"variants": {"normal": {"model": "example:item/base"}}}
        self.f.sync()
        self.assertEqual("shared_parent_or_world_model_retained", self.f.run()["rows"][0]["reason"])

    def test_lenient_blockstate_reference_retained(self):
        self.f.models["assets/example/blockstates/synthetic.json"] = b"{variants:{normal:{model:'example:item/base'}}}"
        self.f.sync()
        self.assertEqual("shared_parent_or_world_model_retained", self.f.run()["rows"][0]["reason"])

    def test_duplicate_key_block_dependency_retained(self):
        self.f.models["assets/example/models/block/synthetic.json"] = b'{"parent":"example:item/base","parent":"item/generated"}'
        self.f.sync()
        self.assertEqual("shared_parent_or_world_model_retained", self.f.run()["rows"][0]["reason"])

    def test_escaped_lenient_dependency_retained(self):
        self.f.models["assets/example/models/block/synthetic.json"] = b'{"parent":"example:item\\u002fbase"} trailing'
        self.f.sync()
        self.assertEqual("shared_parent_or_world_model_retained", self.f.run()["rows"][0]["reason"])

    def test_unreadable_dependency_fails_closed_by_namespace(self):
        self.f.models["assets/example/models/block/synthetic.json"] = b"\xff\xfe"
        self.f.sync()
        with self.assertRaisesRegex(m.PackError, "No supported"): self.f.run()
        self.assertFalse((self.f.root / "pack").exists())

    def test_state_targets_are_independent_not_shared_parents(self):
        self.assertEqual(2, self.f.run()["applied_models"])

    def test_art_case_collision_rejected(self):
        self.f.art_members["Example_item_base.png"] = png()
        self.f.sync()
        with self.assertRaisesRegex(m.PackError, "case-colliding art"): self.f.run()

    def test_metadata_only_uses_owned_path_and_no_world_files(self):
        report = self.f.run()
        with zipfile.ZipFile(self.f.root / "pack" / report["pack"]["file"]) as archive:
            names = archive.namelist()
            self.assertEqual(len(names), len({n.casefold() for n in names}))
            self.assertTrue(all(n == "pack.mcmeta" or n.startswith("assets/vale_reference_art/textures/items/") or n in {r["model_path"] for r in report["rows"] if r["status"] == "applied"} for n in names))
            self.assertFalse(any("/models/block/" in n or "/blockstates/" in n or "/armor/" in n or "/entity/" in n for n in names))


if __name__ == "__main__": unittest.main()
