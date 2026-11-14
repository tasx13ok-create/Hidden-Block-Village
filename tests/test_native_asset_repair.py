"""Original synthetic fixtures only. Never checks in supplied mod JSON or art."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

TOOL=Path(__file__).resolve().parents[1]/"tools/native_asset_repair.py"
spec=importlib.util.spec_from_file_location("native_asset_repair",TOOL)
repair=importlib.util.module_from_spec(spec);spec.loader.exec_module(repair)


def model(parent="unavailable_native_parent"):
    return {"parent":parent,"textures":{"base":"sample:paint","0":"#base"},
            "elements":[{"from":[0,0,0],"to":[1,2,3],"shade":False,
                         "faces":{"north":{"uv":[0,1,2,3],"texture":"#0"}}}],
            "display":{"gui":{"rotation":[1,2,3],"translation":[0,0,0],"scale":[1,1,1]}}}


class Fixture:
    def __init__(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.mods=self.root/"mods";self.mods.mkdir();self.workspace=self.root/"work";self.workspace.mkdir()
        self.member="assets/sample/models/custom/wand.json"
        self.values={self.member:repair.json_bytes(model()),
                     "assets/sample/models/item/wand.json":repair.json_bytes({"parent":"sample:custom/wand"}),
                     "assets/sample/textures/paint.png":b"original synthetic opaque fixture; pixels not inspected"}
        self.native=self.mods/"synthetic-original.jar";self.write(self.native,self.values)
        self.vanilla=self.root/"synthetic-vanilla.jar";self.write(self.vanilla,{
            "assets/minecraft/models/item/generated.json":b'{"parent":"builtin/generated"}',
            "assets/minecraft/models/block/cube_all.json":b'{"textures":{"all":"minecraft:blocks/stone"}}'})
        self.options=self.root/"options.txt";self.options.write_bytes(b"resourcePacks:[]\n")
        self.log=self.root/"client.log";self.log.write_bytes(b"java.io.FileNotFoundException: minecraft:models/unavailable_native_parent.json\n")
    def write(self,path,values):
        with zipfile.ZipFile(path,"w") as z:
            for name,value in values.items():z.writestr(name,value)
    def row(self,path,label,kind="mod"):
        return {"label":label,"path":str(path),"sha256":repair.file_sha(path),"kind":kind}
    def rows(self):return [self.row(self.native,"native"),self.row(self.vanilla,"vanilla","vanilla")]
    def pin(self,path):return {"path":str(path),"sha256":repair.file_sha(path)}
    def manifest(self):
        return {"schema":1,"workspace":str(self.workspace),"mods_directory":str(self.mods),"archives":self.rows(),
                "options":self.pin(self.options),"log":self.pin(self.log),"registry":None,
                "candidates":[{"archive":"native","entry":self.member,"sha256":repair.sha(self.values[self.member])}]}
    def save_manifest(self,value):
        path=self.root/"manifest.json";path.write_bytes(repair.json_bytes(value));return path
    def change_model(self,obj):
        self.values[self.member]=repair.json_bytes(obj);self.write(self.native,self.values)
    def close(self):self.temp.cleanup()


class ParentByteTests(unittest.TestCase):
    def test_first_middle_last_root_only_and_unicode(self):
        for raw in [b'{"parent":"bad","x":[1,2]}',b'{"x":1,"parent":"bad","y":2}',
                    b'{"x":1,"parent":"bad"}',b'{"parent":"bad"}',
                    b'\xef\xbb\xbf{ "credit":"\xc3\xa9", "parent":"bad", "nested":{"parent":"keep"} }',
                    b'{"p\\u0061rent":"bad","str":"comma, } \\" done","nested":[{"x":1}]}']:
            with self.subTest(raw=raw):
                original=repair.document(raw);after,span=repair.remove_parent_bytes(raw)
                expected=dict(original);expected.pop("parent")
                self.assertEqual(expected,repair.document(after));self.assertEqual(after,raw[:span[0]]+raw[span[1]:])
    def test_duplicate_and_nonfinite_are_refused(self):
        for raw in [b'{"parent":"a","parent":"b"}',b'{"parent":"a","x":{"a":1,"a":2}}',
                    b'{"parent":"a","x":NaN}',b'{"parent":"a","x":1e9999}']:
            with self.subTest(raw=raw),self.assertRaises((repair.Refuse,ValueError)):repair.remove_parent_bytes(raw)
    def test_nonparent_fields_and_geometry_digest_preserved(self):
        obj=model();raw=repair.json_bytes(obj);after,_=repair.remove_parent_bytes(raw);new=repair.document(after)
        for key in ("elements","textures","display"):self.assertEqual(repair.canonical(obj[key]),repair.canonical(new[key]))
    def test_old_exported_case_and_builtin_contracts(self):
        self.assertEqual("minecraft:fire sword",repair.resource("Fire Sword"))
        self.assertEqual("minecraft: sword killer b",repair.resource(" sword killer b"))
        for value in ["minecraft:builtin/generated","minecraft:builtin/entity","minecraft:builtin/future", "forge:item-layer","minecraft:item/generated"]:
            self.assertTrue(repair.special_parent(value))
        for value in ["a:../b","a:one/../two","a:back\\slash","a:b:c", "a:/root"]:
            with self.assertRaises(repair.Refuse):repair.resource(value)


class CandidateTests(unittest.TestCase):
    def setUp(self):self.f=Fixture()
    def tearDown(self):self.f.close()
    def evaluate(self):
        sources=repair.Sources(self.f.rows())
        try:return repair.evaluate(sources,self.f.manifest()["candidates"][0],{"minecraft:unavailable_native_parent"})
        finally:sources.close()
    def test_self_contained_original_repair_without_pixel_read(self):
        before=repair.file_sha(self.f.native);patched,proof=self.evaluate()
        self.assertNotIn("parent",repair.document(patched));self.assertTrue(proof["retained_byte_ranges_unchanged"])
        self.assertEqual(before,repair.file_sha(self.f.native));self.assertEqual(1,len(proof["texture_closure"]))
        self.assertFalse(proof["texture_closure"][0]["pixel_payload_read_or_copied"])
    def test_missing_or_inherited_textures_skip(self):
        for transform,reason in [(lambda m:m.pop("textures"),"no_own_nonempty_textures"),
                                 (lambda m:m["textures"].clear(),"no_own_nonempty_textures"),
                                 (lambda m:m["elements"][0]["faces"]["north"].update(texture="#inherited"),"inherited_or_undefined_texture_key")]:
            obj=model();transform(obj);self.f.change_model(obj)
            with self.assertRaises(repair.Skip) as caught:self.evaluate()
            self.assertEqual(reason,caught.exception.reason)
    def test_alias_cycle_missing_png_or_ambiguous_texture_skip(self):
        obj=model();obj["textures"]={"0":"#other","other":"#0"};self.f.change_model(obj)
        with self.assertRaises(repair.Skip) as caught:self.evaluate()
        self.assertEqual("texture_alias_cycle",caught.exception.reason)
        obj=model();obj["textures"]["base"]="sample:absent";self.f.change_model(obj)
        with self.assertRaises(repair.Skip) as caught:self.evaluate()
        self.assertEqual("missing_resource",caught.exception.reason)
    def test_custom_loader_override_and_empty_geometry_skip(self):
        for field,value,reason in [("loader","foreign:custom","custom_loader_model"),("overrides",[{"model":"sample:other"}],"override_model_contract_retained"),("elements",[],"no_own_nonempty_geometry")]:
            obj=model();obj[field]=value;self.f.change_model(obj)
            with self.assertRaises(repair.Skip) as caught:self.evaluate()
            self.assertEqual(reason,caught.exception.reason)
    def test_available_or_case_ambiguous_parent_is_never_deleted(self):
        self.f.values["assets/minecraft/models/unavailable_native_parent.json"]=b"{}";self.f.write(self.f.native,self.f.values)
        with self.assertRaises(repair.Skip) as caught:self.evaluate()
        self.assertEqual("parent_available_or_case_ambiguous",caught.exception.reason)
    def test_builtin_parent_is_never_a_missing_file_repair(self):
        self.f.change_model(model("builtin/generated"))
        with self.assertRaises(repair.Skip) as caught:self.evaluate()
        self.assertEqual("builtin_or_virtual_parent_retained",caught.exception.reason)
    def test_not_in_actual_log_or_hash_drift_is_refused(self):
        sources=repair.Sources(self.f.rows())
        try:
            candidate=self.f.manifest()["candidates"][0]
            with self.assertRaises(repair.Skip) as caught:repair.evaluate(sources,candidate,set())
            self.assertEqual("parent_not_log_proven_missing",caught.exception.reason)
            candidate["sha256"]="0"*64
            with self.assertRaises(repair.Refuse):repair.evaluate(sources,candidate,{"minecraft:unavailable_native_parent"})
        finally:sources.close()


class BuildTests(unittest.TestCase):
    def setUp(self):self.f=Fixture()
    def tearDown(self):self.f.close()
    def test_deterministic_private_pack_no_native_artwork_entries(self):
        manifest=self.f.save_manifest(self.f.manifest());a=self.f.workspace/"one";b=self.f.workspace/"two"
        first=repair.build(manifest,a);second=repair.build(manifest,b)
        self.assertEqual(first["pack_sha256"],second["pack_sha256"]);self.assertEqual(1,first["patched_count"])
        self.assertFalse(first["activation_allowed"]);self.assertFalse(first["registry_proof"]["verified"])
        with zipfile.ZipFile(a/"PRIVATE-ORIGINAL-MODEL-PARENT-REPAIR.zip") as z:
            self.assertEqual([self.f.member,"pack.mcmeta"],sorted(z.namelist()));self.assertIsNone(z.testzip())
        with self.assertRaises(repair.Refuse):repair.build(manifest,a)
    def test_actual_registry_export_is_pinned_and_separate_from_model_names(self):
        registry=self.f.root/"registry.json";registry.write_bytes(repair.json_bytes({"schema":1,"kind":"actual_runtime_export", "provider_sha256":{"native":repair.file_sha(self.f.native)},"items":["sample:wand"],"blocks":[]}))
        manifest=self.f.manifest();manifest["registry"]=self.f.pin(registry)
        result=repair.build(self.f.save_manifest(manifest),self.f.workspace/"registered")
        self.assertTrue(result["registry_proof"]["verified"]);self.assertTrue(result["registry_proof"]["all_patched_models_reached"])
        self.assertEqual(["sample:wand"],result["registry_proof"]["affected_items"])
        self.assertFalse(result["activation_allowed"])
    def test_active_pack_incomplete_mods_or_changed_source_has_no_output(self):
        baseline=self.f.manifest();self.f.options.write_bytes(b'resourcePacks:["foreign.zip"]\n')
        changed=copy.deepcopy(baseline);changed["options"]=self.f.pin(self.f.options)
        with self.assertRaises(repair.Refuse):repair.build(self.f.save_manifest(changed),self.f.workspace/"active")
        self.assertFalse((self.f.workspace/"active").exists())
        self.f.options.write_bytes(b"resourcePacks:[]\n");self.f.write(self.f.mods/"extra.jar",{})
        with self.assertRaises(repair.Refuse):repair.build(self.f.save_manifest(baseline),self.f.workspace/"incomplete")
        self.assertFalse((self.f.workspace/"incomplete").exists())
    def test_output_outside_disposable_workspace_is_refused(self):
        with self.assertRaises(repair.Refuse):repair.build(self.f.save_manifest(self.f.manifest()),self.f.root/"not-work")
    def test_duplicate_source_members_or_owners_are_rejected(self):
        duplicate=self.f.mods/"duplicate.jar";self.f.write(duplicate,{self.f.member:self.f.values[self.f.member]})
        rows=self.f.rows()+[self.f.row(duplicate,"duplicate")]
        sources=repair.Sources(rows)
        try:
            with self.assertRaises(repair.Skip) as caught:sources.read(self.f.member)
            self.assertEqual("ambiguous_resource_owner",caught.exception.reason)
        finally:sources.close()


class SharedContextTests(unittest.TestCase):
    def setUp(self):self.f=Fixture()
    def tearDown(self):self.f.close()
    def setup_shared(self,bad_second=False,override=False):
        base=model();base["textures"]={};self.f.change_model(base)
        first={"parent":"sample:custom/wand","textures":{"0":"sample:paint"}}
        second={"parent":"sample:custom/wand","textures":{} if bad_second else {"0":"sample:paint"}}
        if override:first["overrides"]=[{"predicate":{"pull":1},"model":"sample:item/other"}]
        self.f.values["assets/sample/models/item/wand.json"]=repair.json_bytes(first)
        self.f.values["assets/sample/models/item/other.json"]=repair.json_bytes(second)
        self.f.write(self.f.native,self.f.values)
        definitions=[]
        for entry,raw in self.f.values.items():
            if "/models/" not in entry:continue
            obj=repair.document(raw);identifier=repair.model_id(entry)
            definitions.append({"kind":"model","id":identifier,"entry":entry,"provider":"native","provider_sha256":repair.file_sha(self.f.native),"original_json_sha256":repair.sha(raw),"custom_loader":False,"parent":repair.resource(obj["parent"]),"textures":obj.get("textures",{}),"elements":len(obj.get("elements",[])),"overrides":[repair.resource(x["model"]) for x in obj.get("overrides",[])],"string_references":[]})
        graph={"scope":"ACTUAL_GSON_RESOURCELOCATION_AND_NATIVE_BLOCKSTATE_METADATA_GRAPH","archives":[{"label":x["label"],"sha256":x["sha256"]} for x in self.f.rows()],"source_archives_unchanged":True,"definitions":definitions,"errors":[]}
        path=self.f.root/"synthetic-graph.json";path.write_bytes(repair.json_bytes(graph));return path
    def run_context(self,path):
        sources=repair.Sources(self.f.rows())
        try:
            graph=repair.ContextGraph(sources,self.f.pin(path),self.f.rows())
            return repair.evaluate(sources,self.f.manifest()["candidates"][0],{"minecraft:unavailable_native_parent"},graph)
        finally:sources.close()
    def test_two_real_child_maps_close_without_inventing_target_textures(self):
        path=self.setup_shared();patched,proof=self.run_context(path)
        self.assertEqual({},repair.document(patched)["textures"]);self.assertEqual(2,len(proof["incoming_contexts"]))
        self.assertTrue(all(not x["texture_fields_written_or_changed"] for x in proof["incoming_contexts"]))
        self.assertFalse(proof["actual_native_callback_selector_binding_proven"])
    def test_one_incomplete_shared_child_holds_the_entire_parent(self):
        path=self.setup_shared(bad_second=True)
        with self.assertRaises(repair.Skip) as caught:self.run_context(path)
        self.assertEqual("no_own_nonempty_textures",caught.exception.reason)
    def test_missing_texture_in_override_cannot_hide_behind_good_default(self):
        path=self.setup_shared(bad_second=True,override=True)
        with self.assertRaises(repair.Skip):self.run_context(path)
    def test_reverse_graph_shared_parent_cycle_is_rejected(self):
        path=self.setup_shared();graph=repair.document(path.read_bytes());node=next(x for x in graph["definitions"] if x["id"]=="sample:item/other")
        node["parent"]="sample:item/other";path.write_bytes(repair.json_bytes(graph))
        # It is no longer a referencing child and cannot be silently considered
        # verified; an explicit cycle context is independently rejected.
        sources=repair.Sources(self.f.rows())
        try:
            context=repair.ContextGraph(sources,self.f.pin(path),self.f.rows())
            with self.assertRaises(repair.Skip):context.chain("sample:item/other","sample:custom/wand")
        finally:sources.close()
    def test_ambiguous_incoming_owner_is_not_a_priority_guess(self):
        path=self.setup_shared();graph=repair.document(path.read_bytes());graph["definitions"].append(dict(graph["definitions"][1]));path.write_bytes(repair.json_bytes(graph))
        with self.assertRaises(repair.Skip) as caught:self.run_context(path)
        self.assertEqual("unknown_or_ambiguous_incoming_model",caught.exception.reason)


if __name__=="__main__":unittest.main()
