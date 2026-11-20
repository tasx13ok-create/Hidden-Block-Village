"""Build the owner's traced 4K textures, shader maps, icons and comparison sheets."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageChops, ImageDraw

from material import rgba_map
from outfit import DESIGNS, PART_NAMES, uv_rects, sample_colors
from proof import comparison, helmet_sheet, icon_sheet, make_icon
from trace import WORK_SCALE, UV_WIDTH, biped_work

UV_SIZE=(UV_WIDTH,UV_WIDTH//2)
NEAREST=Image.Resampling.NEAREST


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def paint_mask(layer):
    allowed=Image.new("L",(64*WORK_SCALE,32*WORK_SCALE))
    for part in ("head","hat","body","arm","leg"):
        for side,(x,y,w,h) in uv_rects(part).items():
            cap=side in ("top","bottom")
            if part in ("head","hat","arm") and layer==1:
                continue
            if part=="body":
                if cap and layer!=(0 if side=="top" else 1):
                    continue
                if not cap:
                    if layer==0:
                        h=9
                    else:
                        y,h=y+9,3
            if part=="leg":
                if cap and layer!=(1 if side=="top" else 0):
                    continue
                if not cap:
                    if layer==1:
                        h=7
                    else:
                        y,h=y+7,5
            allowed.paste(255,tuple(s*WORK_SCALE for s in (x,y,x+w,y+h)))
    return allowed


def verify_layer(layer,num):
    alpha=layer.getchannel("A")
    assert [n for n,count in enumerate(alpha.histogram()) if count]==[0,255]
    assert not ImageChops.subtract(alpha,paint_mask(num)).getbbox()
    for color in layer.split()[:3]:
        assert not ImageChops.multiply(color,ImageChops.invert(alpha)).getbbox()


def verify_helmet_front(image,key):
    x,y,w,h=uv_rects("head")["front"]
    face=image.getchannel("A").crop(
        tuple(value*WORK_SCALE for value in (x,y,x+w,y+h)))
    bounds=ImageChops.invert(face).getbbox()
    if key=="isobu":
        assert bounds is None, "Do not cut a hole in Isobu's central guard"
    else:
        assert bounds and bounds[0]+bounds[2]==face.width, (
            f"{key} lower opening is off the helmet centerline", bounds)


def verify_worn_seams(layers):
    joined=Image.alpha_composite(layers[1],layers[0])
    for part in ("body","arm","leg"):
        faces={}
        for side in ("front","east","west"):
            x,y,w,h=uv_rects(part)[side]
            tile=joined.crop(tuple(n*WORK_SCALE for n in (x,y,x+w,y+h)))
            faces[side]=tile
        front,east,west=(faces[side] for side in ("front","east","west"))
        assert front.crop((0,0,1,front.height)).tobytes()==west.crop(
            (west.width-1,0,west.width,west.height)).tobytes(),f"{part} west seam"
        assert front.crop((front.width-1,0,front.width,front.height)).tobytes()==east.crop(
            (0,0,1,east.height)).tobytes(),f"{part} east seam"


def archive(out):
    files=sorted(path for path in out.rglob("*") if path.is_file())
    target=out.with_suffix(".zip")
    with zipfile.ZipFile(target,"x",compression=zipfile.ZIP_DEFLATED) as packaged:
        for path in files:
            name=path.relative_to(out).as_posix()
            entry=zipfile.ZipInfo(name,(2026,11,19,0,0,0))
            entry.compress_type=zipfile.ZIP_DEFLATED
            packaged.writestr(entry,path.read_bytes())
    with zipfile.ZipFile(target) as packaged:
        assert packaged.testzip() is None and len(packaged.infolist())==len(files)
        assert all(packaged.read(path.relative_to(out).as_posix())==path.read_bytes() for path in files)
    print(f"{len(files)} validated archive entries")
    print(target,sha(target))


def build(reference_path,out,master_path=None):
    from front_master import FrontMaster
    master=FrontMaster(master_path) if master_path else None
    reference=Image.open(reference_path).convert("RGB")
    if reference.size!=(828,1792):
        raise ValueError("Expected the owner's 828x1792 reference. The crop coordinates are image-specific.")
    palettes=sample_colors(reference)
    out.mkdir(parents=True,exist_ok=False)
    texture_dir=out/"textures"
    armor=texture_dir/"models"/"armor"
    items=texture_dir/"items"
    proofs=out/"previews"
    for folder in (armor,items,proofs):
        folder.mkdir(parents=True)
    all_layers,all_icons,report={},{},{}
    from trace import opposite_front
    native_faces=out/"native-limb-faces"
    native_faces.mkdir()
    seen=set()
    for key,design in DESIGNS.items():
        layers=biped_work(reference,key,palettes[key],master)
        for part in ("arm","leg"):
            panel=master.panel(key,part,(256,768),True) if master else opposite_front(reference,list(DESIGNS).index(key),key,part)
            panel.save(native_faces/f"{key}_{part}.png")
        if master:
            x,y,w,h=uv_rects("head")["front"]
            assert layers[0].crop(tuple(n*WORK_SCALE for n in (x,y,x+w,y+h))).tobytes()==master.panel(key,"head",(w*WORK_SCALE,h*WORK_SCALE)).tobytes()
        else:
            verify_helmet_front(layers[0],key)
        verify_worn_seams(layers)
        all_layers[key]=[im.resize((1024,512),NEAREST) for im in layers]
        all_icons[key]=[]
        layer_receipts=[]
        for i,image in enumerate(layers,1):
            verify_layer(image,i-1)
            normal,spec=rgba_map(image.resize((1024,512),NEAREST),key)
            files={}
            for suffix,texture in (("",image),("_n",normal),("_s",spec)):
                scaled=texture.resize(UV_SIZE,NEAREST)
                target=armor/f"{key}_layer_{i}{suffix}.png"
                scaled.save(target)
                with Image.open(target) as test:
                    assert test.size==UV_SIZE and test.mode=="RGBA"
                files[suffix or "color"]=sha(target)
            layer_receipts.append(files)
        for slot,part in enumerate(PART_NAMES):
            sprite=make_icon(layers,slot)
            assert sprite.size==(32,32) and sprite.mode=="RGBA"
            assert set(p[3] for _,p in sprite.getcolors(1024))=={0,255}
            assert len(sprite.resize((16,16),NEAREST).getcolors())>=4
            contents=sprite.tobytes()
            assert contents not in seen
            seen.add(contents)
            sprite.save(items/f"{key}_{part}.png")
            all_icons[key].append(sprite)
        roles=("Outline","Main shadow","Main","Dark marking","Marking","Bright detail")
        report[key]=dict(description=design.notes,layers=layer_receipts,colors={
            role:dict(coordinate=pt,color="#%02x%02x%02x"%rgb)
            for role,(pt,rgb) in zip(roles,palettes[key].values())})
        print(f"Painted and checked {key}",flush=True)
    comparison(reference,all_layers,all_icons,palettes,proofs)
    icon_sheet(all_icons,proofs)
    helmet_sheet(reference,all_layers,proofs)
    (out/"palette-and-validation.json").write_text(json.dumps({
        "front_master_sha256":master.sha if master else None, "reference_sha256":sha(reference_path), "reference_size":list(reference.size),
        "layout":"Minecraft 1.12.2 ModelBiped 64x32 grid at 4096x2048",
        "atlas_count":18,"shader_map_count":36,"unique_icon_count":len(seen),"sets":report,
        "preview_scope":"Offline vanilla geometry. Hidden faces continue the pictured colors."
    },indent=2)+"\n",encoding="utf-8")
    (out/"START-HERE.txt").write_text(
        "TAILED BEAST ARMOR | 4K ART MATCH\n\n"
        "Nine sets with 4096x2048 layer_1 and layer_2 color textures, matching LabPBR "
        "normal and specular maps, and 32x32 inventory icons.\n"
        "previews/Compare-all-nine.png shows the reference crop next to each outfit.\n"
        "previews/Helmet-alignment.png compares a straight-on helmet with an angled view.\n"
        "The original drawing supplies the markings, visible edges and colors. Details on "
        "hidden faces continue the colors and patterns from the pictured views.\n\n"
        "These are the vanilla-layout texture files. The separate Art-Match JAR adapts "
        "them to the supplied Bijuu Armor mod's square atlas. Back up the existing JAR "
        "outside the mods folder, close Minecraft and place only one Bijuu Armor JAR in mods.\n\n"
        "The albedo works without shaders. For BSL with OptiFine, enable Normal Map and "
        "Specular Map and select the LabPBR material format with Advanced Materials enabled. "
        "The height map is flat. Parallax is unnecessary.\n",encoding="utf-8")
    archive(out)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference",type=Path)
    parser.add_argument("output",type=Path)
    parser.add_argument("--front-master",type=Path)
    args=parser.parse_args()
    build(args.reference,args.output,args.front_master)


if __name__=="__main__":
    main()
