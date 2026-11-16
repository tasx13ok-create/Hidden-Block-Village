#!/usr/bin/env python3
"""Prepare a PRIVATE pinned manifest; never copy launch arguments or native bodies."""
import argparse
import importlib.util
import json
from pathlib import Path
import zipfile

HERE=Path(__file__).resolve().parents[1]/"native_asset_repair.py"
spec=importlib.util.spec_from_file_location("native_asset_repair",HERE)
repair=importlib.util.module_from_spec(spec);spec.loader.exec_module(repair)


def classpath(recipe):
    argv=json.loads(Path(recipe).read_text(encoding="utf-8"))["argv"]
    positions=[i for i,value in enumerate(argv) if value in ("-cp","-classpath","--class-path")]
    if len(positions)!=1 or positions[0]+1>=len(argv):raise repair.Refuse("Exactly one launch classpath required")
    # The inspected owner profile is Windows. No other argument is printed or saved.
    value=argv[positions[0]+1]
    if not isinstance(value,str):raise repair.Refuse("Classpath is not text")
    return [Path(path) for path in value.split(";") if path]


def prepare(profile,recipe,log,diagnostic,workspace,output):
    profile,workspace,output=Path(profile).resolve(),Path(workspace).resolve(),Path(output).resolve()
    if output.exists() or workspace not in output.parents:raise repair.Refuse("NEW private manifest inside workspace required")
    report=repair.document(Path(diagnostic).read_bytes(),2*1024*1024)
    if repair.file_sha(log)!=report["client_log_snapshot"]["sha256"]:raise repair.Refuse("Diagnostic log identity changed")
    jars=sorted((profile/"mods").glob("*.jar"));archives=[]
    for path in jars:archives.append({"label":path.name,"path":str(path),"sha256":repair.file_sha(path),"kind":"mod"})
    vanilla=[]
    for path in classpath(recipe):
        if not path.is_file() or path.suffix.lower()!=".jar":continue
        with zipfile.ZipFile(path) as z:
            names=set(z.namelist())
            if {"assets/minecraft/models/item/generated.json","assets/minecraft/models/block/cube_all.json"}<=names:vanilla.append(path)
    vanilla=sorted(set(path.resolve() for path in vanilla))
    if len(vanilla)!=1:raise repair.Refuse("Exactly one actual vanilla resource JAR must be identified")
    archives.append({"label":"vanilla-client-resources.jar","path":str(vanilla[0]),"sha256":repair.file_sha(vanilla[0]),"kind":"vanilla"})
    pins={row["label"]:row for row in archives};candidates={}
    for missing in report["model_files"]:
        for origin in missing["bad_parent_jsons"]:
            label,entry=origin["jar"],origin["json"]
            if pins[label]["sha256"]!=report["owning_jar_sha256"][label]:raise repair.Refuse("Original provider no longer matches diagnostic")
            with zipfile.ZipFile(pins[label]["path"]) as z:
                info=z.getinfo(entry)
                if info.file_size>repair.MAX_JSON:raise repair.Refuse("Oversize candidate JSON")
                raw=z.read(info)
            candidates[(label,entry)]={"archive":label,"entry":entry,"sha256":repair.sha(raw)}
    options=profile/"options.txt"
    manifest={"schema":1,"workspace":str(workspace),"mods_directory":str(profile/"mods"),"archives":archives,
              "options":{"path":str(options),"sha256":repair.file_sha(options)},
              "log":{"path":str(Path(log).resolve()),"sha256":repair.file_sha(log)},
              "candidates":[candidates[key] for key in sorted(candidates)],"registry":None}
    # Validation only reads pinned options/mod names/log; it does not load/bake a model.
    repair.complete_inputs(manifest)
    output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(repair.json_bytes(manifest))
    return {"archives":len(archives),"candidates":len(candidates),"registry_export_supplied":False,"runtime_modified":False}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("profile","recipe","log","diagnostic","workspace","output"):p.add_argument("--"+name,type=Path,required=True)
    a=p.parse_args()
    try:print(json.dumps(prepare(**vars(a))))
    except (repair.Refuse,ValueError,OSError,zipfile.BadZipFile) as error:p.exit(2,"Manifest refused: "+str(error)+"\n")


if __name__=="__main__":main()
