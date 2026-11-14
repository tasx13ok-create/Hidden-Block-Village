#!/usr/bin/env python3
"""Exercise the real-API checker with ORIGINAL synthetic fixtures and attacks.

This never copies native geometry, launches Minecraft, uses a GPU, or activates
a pack. Every negative must fail for its expected independent gate.
"""
import argparse
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile
from prepare_manifest import repair,classpath


def write_zip(path,members):
    with zipfile.ZipFile(path,"x") as z:
        for name,raw in members.items():z.writestr(name,raw)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("prepared-api","recipe","java-home","workspace","output"):p.add_argument("--"+name,type=Path,required=True)
    p.add_argument("--expected-api-sha256",required=True);a=p.parse_args()
    out=a.output.resolve();workspace=a.workspace.resolve()
    if out.exists() or workspace not in out.parents:p.error("NEW synthetic directory inside workspace required")
    out.mkdir(parents=True);mods=out/"mods";mods.mkdir();scripts=Path(__file__).resolve().parent
    member="assets/sample/models/custom/wand.json"
    shape={"parent":"missing_parent_fixture","textures":{},"elements":[{"from":[0,0,0],"to":[1,2,3],"faces":{"north":{"uv":[0,0,1,2],"texture":"#0"}}}],"display":{"gui":{"rotation":[1,2,3]}}}
    native=mods/"own-synthetic-models.jar"
    write_zip(native,{member:repair.json_bytes(shape),"assets/sample/models/item/wand.json":repair.json_bytes({"parent":"sample:custom/wand","textures":{"0":"sample:paint"}}),"assets/sample/models/item/other.json":repair.json_bytes({"parent":"sample:custom/wand","textures":{"0":"sample:paint"}}),"assets/sample/textures/paint.png":b"owned fixture metadata member; parser does not decode pixels"})
    vanilla=out/"own-synthetic-vanilla-resources.jar";write_zip(vanilla,{"assets/minecraft/models/item/generated.json":b'{"parent":"builtin/generated"}',"assets/minecraft/models/block/cube_all.json":b'{"parent":"block/cube"}'})
    options=out/"options.txt";options.write_bytes(b"resourcePacks:[]\n");log=out/"input.log";log.write_bytes(b"java.io.FileNotFoundException: minecraft:models/missing_parent_fixture.json\n")
    pin=lambda path:{"path":str(path),"sha256":repair.file_sha(path)}
    manifest={"schema":1,"workspace":str(out),"mods_directory":str(mods),"archives":[dict(pin(native),label="own-models",kind="mod"),dict(pin(vanilla),label="own-vanilla",kind="vanilla")],"options":pin(options),"log":pin(log),"candidates":[{"archive":"own-models","entry":member,"sha256":repair.sha(repair.json_bytes(shape))}],"registry":None}
    resource_manifest=out/"resource-manifest.json";resource_manifest.write_bytes(repair.json_bytes(manifest))
    graph_out=out/"native-graph";flags={"cwd":str(out),"creationflags":getattr(subprocess,"CREATE_NO_WINDOW",0)}
    driver=[sys.executable,str(scripts/"native_model_proof_driver.py"),"--mode","graph","--input",str(resource_manifest),"--prepared-api",str(a.prepared_api.resolve()),"--expected-api-sha256",a.expected_api_sha256,"--recipe",str(a.recipe.resolve()),"--java-home",str(a.java_home.resolve()),"--workspace",str(out),"--output",str(graph_out)]
    with (out/"PRIVATE-GRAPH-COMMAND.log").open("xb") as stream:subprocess.run(driver,stdout=stream,stderr=subprocess.STDOUT,check=True,**flags)
    graph=graph_out/"SAFE-NATIVE-RESOURCE-GRAPH.json";manifest["context_mode"]="all_incoming_wrappers";manifest["context_graph"]=pin(graph);resource_manifest.write_bytes(repair.json_bytes(manifest))
    candidate=out/"candidate";result=repair.build(resource_manifest,candidate)
    if result["patched_count"]!=1:raise AssertionError("Authored native-checker positive fixture must produce one repair")
    original_input=repair.document((candidate/"PRIVATE-NATIVE-PROOF-INPUT.json").read_bytes())
    dependencies=[a.prepared_api.resolve()]+[path.resolve() for path in classpath(a.recipe) if path.is_file() and path.suffix.lower()==".jar"]
    separator=";" if os.name=="nt" else ":";cp=separator.join(map(str,[graph_out/"classes"]+list(dict.fromkeys(dependencies))))
    java=a.java_home.resolve()/("bin/java.exe" if os.name=="nt" else "bin/java")
    def execute(label,value,expected=None,api=None):
        case=out/label;case.mkdir();input_path=case/"input.json";input_path.write_bytes(repair.json_bytes(value));report=case/"report.json"
        command=[str(java),"-Xmx256m","-Djava.awt.headless=true","-cp",cp,"village.assetproof.NativeModelProof",str(input_path),str(report),api or a.expected_api_sha256]
        with (case/"PRIVATE-NATIVE.log").open("xb") as stream:run=subprocess.run(command,stdout=stream,stderr=subprocess.STDOUT,**flags)
        text=(case/"PRIVATE-NATIVE.log").read_text(encoding="utf-8",errors="replace")
        if expected is None:
            if run.returncode!=0 or not report.is_file():raise AssertionError("Synthetic actual-API positive failed")
        elif run.returncode==0 or expected not in text or report.exists():raise AssertionError("Negative gate was not independently enforced: "+label)
        return {"case":label,"exit_code":run.returncode,"expected_gate":expected,"passed":True,"log_sha256":repair.file_sha(case/"PRIVATE-NATIVE.log")}
    cases=[execute("positive",original_input)]
    pack=Path(original_input["pack"])
    with zipfile.ZipFile(pack) as z:members={n:z.read(n) for n in z.namelist()}
    def changed_pack(label,changed):
        value=copy.deepcopy(original_input);target=out/(label+".zip");write_zip(target,changed);value["pack"]=str(target);value["pack_sha256"]=repair.file_sha(target)
        if member in changed:value["models"][0]["patched_json_sha256"]=repair.sha(changed[member])
        return value
    changed=dict(members);obj=repair.document(changed[member]);obj["elements"][0]["faces"]["north"]["uv"][0]=.25;changed[member]=repair.json_bytes(obj)
    cases.append(execute("uv-tamper",changed_pack("uv-tamper",changed),"typed positions/faces/UVs/rotations/shading unchanged"))
    changed=dict(members);obj=repair.document(changed[member]);obj["textures"]={"0":"sample:other"};changed[member]=repair.json_bytes(obj)
    cases.append(execute("texture-tamper",changed_pack("texture-tamper",changed),"all actual texture bindings unchanged"))
    changed=dict(members);changed["assets/sample/models/item/wand.json"]=b'{"textures":{"0":"sample:other"}}'
    cases.append(execute("hidden-child-override",changed_pack("hidden-child-override",changed),"pack has exactly approved model entries"))
    changed=dict(members);changed["assets/sample/textures/paint.png"]=b"unapproved pixel override"
    cases.append(execute("hidden-pixel-override",changed_pack("hidden-pixel-override",changed),"pack has exactly approved model entries"))
    value=copy.deepcopy(original_input);value["models"][0]["incoming_contexts"]=[]
    cases.append(execute("omitted-child-contexts",value,"actual face texture resolves without inherited parent"))
    value=copy.deepcopy(original_input);value["models"][0]["incoming_contexts"][0]["texture_closure"][0]["id"]="sample:wrong"
    cases.append(execute("wrong-declared-texture",value,"actual child-bound textures are proven existing original resources"))
    value=copy.deepcopy(original_input);value["models"][0]["provider_sha256"]="0"*64
    cases.append(execute("wrong-provider-pin",value,"exact pinned original provider before proof"))
    cases.append(execute("wrong-api-origin",original_input,"actual ModelBlock comes from the pinned real prepared API",api="0"*64))
    report={"schema":1,"scope":"ORIGINAL_SYNTHETIC_ATTACKS_AGAINST_ACTUAL_MODEL_BLOCK_CHECKER","passed":True,"positive_cases":1,"negative_cases":8,"actual_prepared_api_sha256":a.expected_api_sha256,"native_mod_geometry_or_art_used":False,"minecraft_client_launched":False,"runtime_or_profile_modified":False,"cases":cases}
    target=out/"SAFE-NATIVE-CHECKER-NEGATIVES.json";target.write_bytes(repair.json_bytes(report));print(json.dumps({"passed":True,"cases":9,"report_sha256":repair.file_sha(target)}))


if __name__=="__main__":main()
