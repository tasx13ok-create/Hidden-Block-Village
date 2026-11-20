"""Merge the owner-supplied tail hierarchies and their ModelBox UVs into a private armor candidate."""
import io
import json
from pathlib import Path
import os
import struct
import subprocess
import sys
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image
from fit import fit_sockets, tint

BEASTS = "shukaku matatabi isobu son_goku kokuo saiken chomei gyuki kurama".split()


def encode(model):
    buf=io.BytesIO()
    def write(fmt,*values):
        buf.write(struct.pack(">"+fmt,*values))
    nodes=model["nodes"]
    indices={node["id"]:i for i,node in enumerate(nodes)}
    write("i",len(nodes))
    for i,node in enumerate(nodes):
        parent=indices.get(node["parent"],-1)
        assert parent<i and (i==0)==(parent<0)
        write("i6fi",parent,*node["pivot"],*node["rotation"],len(node["boxes"]))
        for box in node["boxes"]:
            write("2i3f3i",*box["uv"],*box["from"],*box["size"])
    write("i",len(model["appendages"]))
    for arm in model["appendages"]:
        path=arm["nodes"]
        write("i",len(path))
        for node in path:
            write("i",indices[node])
    return buf.getvalue()


def merge(armor,reference,out,jdk,asm,forge=None):
    source=Path(__file__).parent
    with tempfile.TemporaryDirectory() as work,ZipFile(reference) as ref,ZipFile(armor) as original:
        root=Path(work)
        specs=json.loads(original.read("assets/hvbijuu/models/armor_layers.json"))
        fitted=all(s["w"]==6 and s["d"]==6 for s in specs if s["kind"]=="shoulder")
        fit_sources=[source/"ShellFitPatch.java",source/"ShellClearance.java"] if fitted else []
        if fitted:
            if not forge:
                raise ValueError("Provide the Forge 1.12.2 universal JAR for the fitted player coverage hook")
            fit_sources.append(source/"ArmorCoverage.java")
        compile_path=str(asm) if not forge else os.pathsep.join(map(str,(asm,forge)))
        subprocess.run([jdk/"javac","--release","8","-cp",compile_path,"-d",work,source/"TailPatch.java",source/"ReferenceTails.java",*fit_sources],check=True)
        updates={p.relative_to(root).as_posix():p.read_bytes() for p in (root/"village").rglob("*.class")}
        if fitted:
            name="village/bijuu/ArmorShapes.class"
            target=root/name
            target.write_bytes(original.read(name))
            subprocess.run([jdk/"java","-cp",work+os.pathsep+str(asm),"ShellFitPatch",target],check=True)
            updates[name]=target.read_bytes()
        for cls,idx in [("ClientProxy$BeastModel",-1),("ModelKuramaArmor",8),("ModelShukakuArmor",0),("ModelMatatabiArmor",1)]:
            name="village/bijuu/"+cls+".class"
            target=root/name
            target.write_bytes(original.read(name))
            subprocess.run([jdk/"java","-cp",work+os.pathsep+str(asm),"TailPatch",target,str(idx)],check=True)
            updates[name]=target.read_bytes()
        for idx,key in enumerate(BEASTS):
            prefix="Naruto_Canon_Tails_v3/"
            model=json.loads(ref.read(prefix+"geometry/"+key+".json"))
            fit_sockets(model)
            updates[f"assets/hvbijuu/tails/{idx}.bin"]=encode(model)
            texture=Image.open(io.BytesIO(ref.read(prefix+model["texture"]["file"]))).convert("RGBA")
            for name in original.namelist():
                if name.startswith("assets/hvbijuu/textures/items/") and name.split("/")[-1].startswith(key+"_") and name.endswith(".png"):
                    buf=io.BytesIO()
                    tint(Image.open(io.BytesIO(original.read(name))),texture,key).save(buf,format="PNG")
                    updates[name]=buf.getvalue()
            for state in (("", "_cage") if idx==8 else ("",)):
                name="assets/hvbijuu/textures/armor/"+key+state+".png"
                atlas=Image.open(io.BytesIO(original.read(name))).convert("RGBA")
                assert not atlas.crop((0,0,*texture.size)).getbbox()
                atlas=tint(atlas,texture,key)
                atlas.paste(texture,(0,0))
                buf=io.BytesIO()
                atlas.save(buf,format="PNG")
                updates[name]=buf.getvalue()
        with ZipFile(out,"x",ZIP_DEFLATED) as result:
            for name in original.namelist():
                result.writestr(name,updates.pop(name) if name in updates else original.read(name))
            for name,data in updates.items():
                result.writestr(name,data)
    with ZipFile(out) as check:
        assert check.testzip() is None


if __name__=="__main__":
    merge(*map(Path,sys.argv[1:]))
