"""Trace the pictured costume panels directly into the vanilla armor skin UV."""
from dataclasses import dataclass

from PIL import Image, ImageDraw, ImageOps

from landmarks import HELMET_CHEEK, HELMET_FRONT, HERO_LIMBS
from outfit import DESIGNS, ORIGINS, uv_rects

WORK_SCALE = 8   # intermediate 512x256. Retains the source pixels on the armor.
UV_WIDTH = 4096
N = Image.Resampling.NEAREST

@dataclass(frozen=True)
class Crop:
    head: tuple[int, int, int, int]
    torso: tuple[int, int, int, int]
    arm: tuple[int, int, int, int]
    leg: tuple[int, int, int, int]


# Each cell refers to the first small model under the hero of the same set.
# Only the outfit is sampled. The surrounding creature and scenery stay out.
CROPS = {
    "shukaku": Crop((28,6,47,24),(29,25,45,49),(22,24,29,47),(29,49,37,73)),
    "matatabi": Crop((28,6,47,24),(30,25,45,49),(22,24,29,47),(28,49,36,74)),
    "isobu": Crop((26,6,46,24),(28,25,44,49),(21,24,28,47),(27,49,36,74)),
    "son_goku": Crop((27,6,47,24),(29,25,45,49),(21,25,29,47),(28,49,36,73)),
    "kokuo": Crop((26,7,46,24),(28,25,44,49),(21,24,28,47),(27,49,35,73)),
    "saiken": Crop((27,8,47,26),(29,27,45,52),(22,26,29,50),(28,52,36,75)),
    "chomei": Crop((28,10,48,28),(30,29,46,53),(22,28,30,51),(29,53,37,77)),
    "gyuki": Crop((26,10,46,27),(28,28,44,54),(22,28,28,51),(27,54,35,77)),
    "kurama": Crop((27,10,47,28),(29,29,45,53),(22,28,29,51),(29,53,37,77)),
}

THUMBNAILS=[(4+166*i,803) for i in range(5)]+[(4+166*i,1175) for i in range(4)]


def thumbnail(reference,index,box,version=0):
    x,y=THUMBNAILS[index]
    u,v,U,V=box
    # Three source poses per panel. The first is the primary matching face.
    dx=42*version
    return reference.crop((x+u+dx,y+v,x+U+dx,y+V)).convert("RGBA")


def as_color(palette,key):
    return palette[key][1]+(255,)


def rescale(image,size):
    return image.resize(size,N)


HERO_BODY = {
    "shukaku": ((64,60),(63,111),(98,114),(101,61)),
    "matatabi": ((64,55),(63,110),(103,114),(103,60)),
    "isobu": ((63,55),(62,111),(102,114),(102,60)),
    "son_goku": ((62,55),(62,111),(103,115),(103,59)),
    "kokuo": ((64,56),(62,109),(104,113),(103,59)),
    "saiken": ((59,59),(59,112),(100,113),(101,60)),
    "chomei": ((63,59),(62,112),(102,113),(102,61)),
    "gyuki": ((64,61),(62,114),(103,115),(103,64)),
    "kurama": ((65,60),(63,113),(103,114),(104,63)),
}


def hero_face(reference,index,points,size):
    x,y=ORIGINS[index]
    coords=tuple(float(c) for px,py in points for c in (px+x,py+y))
    return reference.transform(size,Image.Transform.QUAD,coords,N).convert("RGBA")


def paint_head(reference,index,key,side,faces):
    if side in ("west", "east"):
        cheek=hero_face(reference,index,HELMET_CHEEK[key],(36,48))
        return cheek if side=="west" else ImageOps.mirror(cheek)
    if side!="front":
        return faces[side]
    main=hero_face(reference,index,HELMET_FRONT[key],(40,48))
    # The lower opening belongs on the model centerline. Keep its corners
    # narrow so the face plate shading and any central guard stay intact.
    width,height=main.size
    center=width//2
    if key=="isobu":
        # The reference has an uninterrupted central shell guard.
        return main
    half=5 if key=="kokuo" else 4
    top=height-11
    end=height-4
    ImageDraw.Draw(main).polygon(
        [(center-half,top+2), (center-half+1,top),
         (center+half-2,top), (center+half-1,top+2),
         (center+half-1,end-2), (center+half-2,end),
         (center-half+1,end), (center-half,end-2)],fill=(0,0,0,0))
    return main


def projected_faces(reference,index,box,palette):
    front=thumbnail(reference,index,box)
    other=thumbnail(reference,index,box,1)
    rear=thumbnail(reference,index,box,2)
    w,h=front.size
    # Continue actual color and trim from the reference poses. The hidden
    # underside uses the sampled shadow; no added marks or ornament.
    n=max(2,w//3)
    west=front.crop((0,0,n,h))
    east=other.crop((w-n,0,w,h))
    top=front.crop((0,0,w,max(1,h//3)))
    bottom=Image.new("RGBA",(w,max(1,h//3)),as_color(palette,"S"))
    return dict(front=front,back=rear,west=west,east=east,top=top,bottom=bottom)


def biped_work(reference,key,palette):
    index=list(DESIGNS).index(key)
    art=CROPS[key]
    sizes=(64*WORK_SCALE,32*WORK_SCALE)
    layers=[Image.new("RGBA",sizes),Image.new("RGBA",sizes)]
    for name,box in (("head",art.head),("body",art.torso),("arm",art.arm),("leg",art.leg)):
        faces=projected_faces(reference,index,box,palette)
        if name in ("arm","leg"):
            points=HERO_LIMBS[key][0 if name=="arm" else 1]
            limb=hero_face(reference,index,points,(24,54))
            faces["front"]=limb
            # The outer edge of the larger pose still shows stitching and
            # marks that disappear when a two-pixel thumbnail strip is blown up.
            side_panel=limb.crop((0,0,10,limb.height))
            faces["west"]=side_panel
            faces["east"]=ImageOps.mirror(side_panel)
        for side,(u,v,w,h) in uv_rects(name).items():
            x,y,W,H=(n*WORK_SCALE for n in (u,v,w,h))
            face=faces[side]
            cap=side in ("top","bottom")
            if name=="head":
                face=paint_head(reference,index,key,side,faces)
            if name=="body" and side=="front":
                face=hero_face(reference,index,HERO_BODY[key],(36,54))
            placed=rescale(face,(W,H))
            if name=="body":
                ysplit=9*WORK_SCALE
                if cap:
                    layers[0 if side=="top" else 1].paste(placed,(x,y))
                else:
                    layers[0].paste(placed.crop((0,0,W,ysplit)),(x,y))
                    layers[1].paste(placed.crop((0,ysplit,W,H)),(x,y+ysplit))
            elif name=="leg":
                ysplit=7*WORK_SCALE
                if cap:
                    layers[1 if side=="top" else 0].paste(placed,(x,y))
                else:
                    layers[1].paste(placed.crop((0,0,W,ysplit)),(x,y))
                    layers[0].paste(placed.crop((0,ysplit,W,H)),(x,y+ysplit))
            else:
                layers[0].paste(placed,(x,y))
    if key=="gyuki":
        # Purple horn marks visible on the reference, contained within
        # the existing vanilla helmet overlay and its standard UV cells.
        rect=uv_rects("hat")["front"]
        x,y,W,H=(v*WORK_SCALE for v in rect)
        horn=Image.new("RGBA",(16,16))
        d=ImageDraw.Draw(horn)
        for points in ([(0,0),(2,2),(3,5),(4,6),(2,6),(0,4)],
                       [(15,0),(13,2),(12,5),(11,6),(13,6),(15,4)]):
            d.polygon(points,fill=as_color(palette,"A"))
        d.line((0,0,2,3),fill=as_color(palette,"G"))
        d.line((15,0,13,3),fill=as_color(palette,"G"))
        layers[0].paste(rescale(horn,(W,H)),(x,y))
    return layers
