"""Map the matched vanilla art into the supplied HVBijuu runtime atlas."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageOps

from material import rgba_map
from outfit import DESIGNS, FACE_ORDER, PART_NAMES, uv_rects

BASE_SHA = "98671ca366b3f00099b8562306d64a27462c0875496f39bedfc083064b1c223b"
PREFIX = "assets/hvbijuu"
NEAREST = Image.Resampling.NEAREST
ATLAS_UNITS = 256
ATLAS_SIZE = 4096
ATLAS_SCALE = ATLAS_SIZE // ATLAS_UNITS


class Band:
    def __init__(self, start, end):
        self.x,self.y,self.height,self.end = 0,start,0,end

    def box(self,w,h,d):
        out=[]
        for W,H in ((d,h),(d,h),(w,d),(w,d),(w,h),(w,h)):
            if self.x+W>ATLAS_UNITS:
                self.x,self.y,self.height=0,self.y+self.height+1,0
            if self.y+H>self.end:
                raise ValueError("Native model band overflow")
            out.append((self.x,self.y,self.x+W,self.y+H))
            self.x+=W+1
            self.height=max(self.height,H)
        return out


def png(im):
    buf=io.BytesIO()
    im.save(buf,format="PNG")
    return buf.getvalue()


def crop_face(image,part,side,mirror=False):
    if mirror:
        side={"east":"west","west":"east"}.get(side,side)
    x,y,w,h=uv_rects(part)[side]
    scale=image.width//64
    face=image.crop(tuple(t*scale for t in (x,y,x+w,y+h)))
    return ImageOps.mirror(face) if mirror else face


def put_box(atlas,rects,faces):
    for bounds,image in zip(rects,faces):
        x,y,X,Y=(k*ATLAS_SCALE for k in bounds)
        atlas.paste(image.resize((X-x,Y-y),NEAREST),(x,y))


def sampled_faces(source,part,mirror=False):
    return [crop_face(source,part,side,mirror) for side in FACE_ORDER]


def make_atlas(index,layers,palette,specs,stock):
    atlas=Image.new("RGBA",(ATLAS_SIZE,ATLAS_SIZE))
    one,two=layers
    joint=Image.alpha_composite(two,one)
    colors={k:v+ (255,) for k,v in palette.items()}
    head,chest,leg,boot=[Band(y,160) for y in (80,100,132,145)]
    put_box(atlas,head.box(8,8,8),sampled_faces(one,"head"))
    # Kurama and Matatabi already have ear slots. The drawing has a squared
    # hood on both, so those slots intentionally sample transparent pixels.
    put_box(atlas,chest.box(8,12,4),sampled_faces(joint,"body"))
    for mirror in (False,True):
        put_box(atlas,chest.box(4,12,4),sampled_faces(one,"arm",mirror))
        chest.box(4,2,4)
        chest.box(4,3,1)
        slices=[]
        for i,band,height in ((1,leg,7),(0,boot,5)):
            for side in FACE_ORDER:
                face=crop_face(layers[i],"leg",side,mirror)
                if side not in ("top","bottom"):
                    scale=one.width//64
                    y=0 if i else 7*scale
                    face=face.crop((0,y,face.width,y+scale*height))
                elif not face.getbbox():
                    face=Image.new("RGBA",face.size,colors["S"])
                slices.append(face)
            put_box(atlas,band.box(4,height,4),slices)
            band.box(*( (4,3,1) if i else (4,2,5) ))
            slices=[]
    starts=(190,204,224,240,256)
    extras=[Band(x,y) for x,y in zip(starts,starts[1:])]
    for spec in specs:
        if index not in spec.get("beasts",range(9)) or index in spec.get("exceptBeasts",()):
            continue
        slot=spec["slot"]
        uv=extras[slot].box(spec["w"],spec["h"],spec["d"])
        if spec["kind"] in ("bracer","thigh"):
            continue
        bone=spec["bone"]
        part=("head" if bone=="head" else "body" if bone=="body" else
              "arm" if bone.endswith("_arm") else "leg")
        source=joint if part in ("leg","body") else one
        faces=sampled_faces(source,part,bone.startswith("left_"))
        # The stock panel sockets take the same cloth and markings as their
        # matching worn face. Stock geometry and animation are never rewritten.
        for i,face in enumerate(faces):
            if slot==1 and part=="arm" and FACE_ORDER[i] not in ("top","bottom"):
                faces[i]=face.crop((0,0,face.width,max(1,face.height//4)))
            if slot==0 and FACE_ORDER[i] in ("front","east","west","back"):
                faces[i]=face.crop((0,0,face.width,max(1,face.height//3)))
            if slot==3 and FACE_ORDER[i] in ("front","east","west","back"):
                faces[i]=face.crop((0,7*one.width//64,face.width,face.height))
            if not faces[i].getbbox():
                faces[i]=Image.new("RGBA",face.size,colors["S"])
        put_box(atlas,uv,faces)
    # The stock tail band already contains the beast-specific marks: scales,
    # rings, chakra strokes, cap shading and the cage textures. Keep the
    # authored pattern and opacity while bringing it into the outfit palette.
    tail=match_tail_palette(stock,palette)
    height=30*ATLAS_SCALE
    atlas.paste(tail.resize((ATLAS_SIZE,height),NEAREST),(0,160*ATLAS_SCALE))
    return atlas


def match_tail_palette(stock,palette):
    scale=stock.width//ATLAS_UNITS
    band=stock.crop((0,160*scale,stock.width,190*scale)).convert("RGBA")
    rgb=band.convert("RGB")
    light=ImageOps.grayscale(rgb)
    # Order the sampled colors by luminance. The palette roles can have
    # different tonal ordering between beasts, so do not assume A is bright.
    colors=tuple(sorted(palette.values(),key=lambda c: 299*c[0]+587*c[1]+114*c[2]))
    target=ImageOps.colorize(light,colors[0],colors[-1],colors[len(colors)//2])
    toned=Image.blend(rgb,target,.24)
    toned.putalpha(band.getchannel("A"))
    return toned

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(vanilla,base,output):
    if sha(base)!=BASE_SHA:
        raise ValueError("Unexpected Bijuu Armor JAR baseline")
    record=json.loads((vanilla/"palette-and-validation.json").read_bytes())
    assets={}
    with zipfile.ZipFile(base) as original:
        assert original.testzip() is None
        specs=json.loads(original.read(f"{PREFIX}/models/armor_layers.json"))
        for index,key in enumerate(DESIGNS):
            layers=[]
            for i in (1,2):
                path=vanilla/f"textures/models/armor/{key}_layer_{i}.png"
                assert sha(path)==record["sets"][key]["layers"][i-1]["color"]
                with Image.open(path) as image:
                    assert image.size==(4096,2048)
                    layers.append(image.convert("RGBA").resize((512,256),NEAREST))
            palette={char:tuple(bytes.fromhex(row["color"][1:])) for char,row in
                     zip("OSMTAG",record["sets"][key]["colors"].values())}
            for state in (("", "_cage") if key=="kurama" else ("",)):
                atlas_path=f"{PREFIX}/textures/armor/{key}{state}"
                stock=Image.open(io.BytesIO(original.read(atlas_path+".png")))
                atlas=make_atlas(index,layers,palette,specs,stock)
                # Keep the new color projection in its final 4K atlas. Smooth
                # channel gradients operate at the earlier material-map scale.
                maps=rgba_map(atlas.resize((1024,1024),NEAREST),key)
                normal,specular=(image.resize(atlas.size,NEAREST) for image in maps)
                for suffix,texture in (("",atlas),("_n",normal),("_s",specular)):
                    name=atlas_path+suffix+".png"
                    if name in original.namelist():
                        assets[name]=png(texture)
            for part in PART_NAMES:
                with Image.open(vanilla/f"textures/items/{key}_{part}.png") as icon:
                    assets[f"{PREFIX}/textures/items/{key}_{part}.png"]=png(icon.resize((128,128),NEAREST))
                    assets[f"{PREFIX}/textures/items/gui/{key}_{part}.png"]=png(icon.resize((64,64),NEAREST))
            print(f"Packed {key} at 4096x4096",flush=True)

        entries=original.namelist()
        assert len(entries)==len(set(entries))
        assert set(assets)<=set(entries) and all(name.endswith(".png") for name in assets)
        with zipfile.ZipFile(output,"x",compression=zipfile.ZIP_DEFLATED) as jar:
            for name in entries:
                info=zipfile.ZipInfo(name,(2026,11,19,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                jar.writestr(info,assets[name] if name in assets else original.read(name))
        with zipfile.ZipFile(output) as jar:
            assert jar.testzip() is None and jar.namelist()==entries
            assert all(jar.read(n)==(assets[n] if n in assets else original.read(n)) for n in entries)
    print(f"{len(assets)} texture-only replacements validated")
    print(output,sha(output))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("vanilla",type=Path)
    parser.add_argument("original_jar",type=Path)
    parser.add_argument("output",type=Path)
    args=parser.parse_args()
    build(args.vanilla,args.original_jar,args.output)


if __name__=="__main__":
    main()
