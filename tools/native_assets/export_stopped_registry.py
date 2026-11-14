#!/usr/bin/env python3
"""Export ONLY Forge item/block IDs from a pinned, stopped disposable fixture.

No original world, player file, live server, class or artwork is accessed. The
private fixture's saved FML registry snapshot is actual runtime evidence; model
binding selectors still require their own proof.
"""
import argparse
import importlib
import json
from pathlib import Path
import sys
from prepare_manifest import repair


def export(fixture,repo,workspace,output,expected_source,expected_result,expected_receipt):
    fixture,workspace,output=Path(fixture).resolve(),Path(workspace).resolve(),Path(output).resolve()
    if output.exists() or workspace not in output.parents:raise repair.Refuse("NEW private registry output inside workspace required")
    inputs=fixture/"PRIVATE-INPUTS.json";result=fixture/"PRIVATE-PROVIDER-RESULT.json";receipt=fixture/"PRIVATE-RUN-RECEIPT.json"
    if repair.file_sha(result)!=expected_result or repair.file_sha(receipt)!=expected_receipt:raise repair.Refuse("Exact completed native receipts required")
    guard=repair.document(inputs.read_bytes(),4*1024*1024);outcome=repair.document(result.read_bytes(),4*1024*1024);run=repair.document(receipt.read_bytes())
    if guard["source_hash"]!=expected_source or guard["original_world_or_player_used"] or not outcome["passed"] or run["exit_code"]!=0 or run["forced_termination"] or not run["native_inputs_unchanged_after"]:
        raise repair.Refuse("Known successful stopped disposable fixture required")
    runtimes=[p for p in fixture.iterdir() if p.is_dir() and p.name.startswith("provider-native") and (p/"PRIVATE-DISPOSABLE-PROVIDER-NATIVE.json").is_file()]
    if len(runtimes)!=1:raise repair.Refuse("Exactly one guarded native runtime required")
    runtime=runtimes[0]
    if not (runtime/".vale-recovery-test").is_file():raise repair.Refuse("Disposable recovery marker missing")
    rows=guard["unchanged_native_inputs"]+guard["selected_components"]
    providers={}
    for row in rows:
        path=runtime/"mods"/row["file"]
        if repair.file_sha(path)!=row["sha256"]:raise repair.Refuse("Stopped fixture mod bytes changed")
        providers[row["file"]]=row["sha256"]
    save=runtime/"vale-recovery-test/level.dat";before=repair.file_sha(save)
    # Reading the shared NBT module must not write a cache into that checkout.
    sys.dont_write_bytecode=True
    sys.path.insert(0,str(Path(repo).resolve()));nbt=importlib.import_module("tools.world_merge.nbt")
    loaded=nbt.load(save,max_bytes=8*1024*1024);root=loaded[1] if isinstance(loaded,tuple) else loaded
    registries=root["FML"]["Registries"]
    extracted={};summaries={}
    for kind in ("items","blocks"):
        registry=registries["minecraft:"+kind];names=[];numbers=set()
        for row in registry["ids"].value:
            if set(row.value)!={"K","V"} or row["K"].type!=nbt.STRING or row["V"].type!=nbt.INT:raise repair.Refuse("Unexpected actual Forge registry record schema")
            key,number=row["K"].value,row["V"].value
            if repair.resource(key)!=key or key in names or number in numbers:raise repair.Refuse("Invalid/duplicate actual registry entry")
            names.append(key);numbers.add(number)
        extracted[kind]=sorted(names)
        summaries[kind]={"ids":len(names),"blocked":len(registry["blocked"].value),"dummied":len(registry["dummied"].value)}
        if summaries[kind]["dummied"]:raise repair.Refuse("Dummy entries need explicit review before use as live registration evidence")
    if repair.file_sha(save)!=before:raise repair.Refuse("Saved registry file changed while reading")
    data={"schema":1,"kind":"actual_runtime_export","provider_sha256":providers,"items":extracted["items"],"blocks":extracted["blocks"]}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(repair.json_bytes(data))
    origin={"schema":1,"scope":"ACTUAL_STOPPED_SOURCE11_FORGE_REGISTRY_SNAPSHOT_ONLY","source_sha256":expected_source,"completed_native_result_sha256":expected_result,"completed_native_run_sha256":expected_receipt,"saved_level_dat_sha256":before,"export_sha256":repair.file_sha(output),"counts":summaries,"fixture_source_archives_unchanged":True,"world_file_written":False,"player_fields_exported":False,"model_bindings_proven":False,"runtime_or_client_launched":False}
    output.with_suffix(".origin.json").write_bytes(repair.json_bytes(origin))
    return origin


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("fixture","repo","workspace","output"):p.add_argument("--"+name,type=Path,required=True)
    for name in ("expected-source","expected-result","expected-receipt"):p.add_argument("--"+name,required=True)
    a=p.parse_args()
    try:print(json.dumps(export(**vars(a))))
    except (repair.Refuse,ValueError,OSError,KeyError) as error:p.exit(2,"Registry export refused: "+str(error)+"\n")


if __name__=="__main__":main()
