#!/usr/bin/env python3
"""Explicit headless parser verification, never Minecraft/Forge main or live activation.

Do not execute during the owner's inspection window. Requires coordinator release.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from prepare_manifest import classpath


def digest(path):
    h=hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda:stream.read(65536),b""):h.update(block)
    return h.hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("input","prepared-api","recipe","java-home","workspace","output"):p.add_argument("--"+name,type=Path,required=True)
    p.add_argument("--expected-api-sha256",required=True)
    p.add_argument("--mode",choices=["verify","graph"],default="verify")
    a=p.parse_args();workspace=a.workspace.resolve();out=a.output.resolve()
    if out.exists() or workspace not in out.parents:p.error("Use a NEW proof directory inside disposable workspace")
    if digest(a.prepared_api)!=a.expected_api_sha256:p.error("Prepared API identity mismatch")
    # Prepared API precedes all dependency jars; ModelBlock's protection domain
    # is independently checked by the Java helper to reject a hidden stub.
    dependencies=[a.prepared_api.resolve()]+[path.resolve() for path in classpath(a.recipe) if path.is_file() and path.suffix.lower()==".jar"]
    dependencies=list(dict.fromkeys(dependencies));out.mkdir(parents=True);classes=out/"classes";classes.mkdir()
    separator=";" if os.name=="nt" else ":";cp=separator.join(map(str,dependencies))
    java=a.java_home.resolve()/"bin/java.exe" if os.name=="nt" else a.java_home.resolve()/"bin/java"
    javac=a.java_home.resolve()/"bin/javac.exe" if os.name=="nt" else a.java_home.resolve()/"bin/javac"
    source=Path(__file__).resolve().with_name("NativeModelProof.java")
    graph_source=source.with_name("NativeResourceGraph.java")
    # Contain any library/compiler logging in the private proof directory too.
    flags={"creationflags":getattr(subprocess,"CREATE_NO_WINDOW",0),"cwd":str(out)}
    with (out/"PRIVATE-NATIVE-COMPILE.log").open("xb") as log:
        subprocess.run([str(javac),"-J-Xmx256m","-encoding","UTF-8","-source","8","-target","8","-cp",cp,"-d",str(classes),str(source),str(graph_source)],stdout=log,stderr=subprocess.STDOUT,check=True,**flags)
    report=out/("SAFE-NATIVE-RESOURCE-GRAPH.json" if a.mode=="graph" else "SAFE-NATIVE-MODEL-PROOF.json")
    main="village.assetproof.NativeResourceGraph" if a.mode=="graph" else "village.assetproof.NativeModelProof"
    with (out/"PRIVATE-NATIVE-RUN.log").open("xb") as log:
        subprocess.run([str(java),"-Xmx256m","-Djava.awt.headless=true","-cp",str(classes)+separator+cp,main,str(a.input.resolve()),str(report),a.expected_api_sha256],stdout=log,stderr=subprocess.STDOUT,check=True,**flags)
    value=json.loads(report.read_text(encoding="utf-8"))
    print(json.dumps({"passed":value.get("passed"),"models":value.get("models"),"graph_complete":value.get("complete"),"report_sha256":digest(report),"real_client_or_model_bake":False,"activation_allowed":False}))


if __name__=="__main__":main()
