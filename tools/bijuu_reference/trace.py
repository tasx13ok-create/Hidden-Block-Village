"""Trace the pictured costume panels directly into the vanilla armor skin UV."""
from dataclasses import dataclass

from PIL import Image, ImageDraw, ImageOps

from ink import cel_surface
from retouch import finish_chest
from finish import align_border, clean_thumbnail, render_plane, resize_albedo
from landmarks import HELMET_CHEEK, HELMET_FRONT, HERO_LIMBS
from outfit import DESIGNS, ORIGINS, uv_rects


WORK_SCALE = 64  # 4096x2048 vanilla UV; source planes render at their final size.
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



# Horizontal center of the pictured chest motif near the collar and waist.
# Mesh rows undo the remaining camera lean without cropping either side.
CHEST_AXES = {
    "shukaku": (.63,.60), "matatabi": (.53,.53), "isobu": (.54,.56),
    "son_goku": (.47,.49), "kokuo": (.37,.41), "saiken": (.58,.58),
    "chomei": (.57,.59), "gyuki": (.46,.48), "kurama": (.38,.416),
}


def straighten_front(image, top, bottom):
    w,h=image.size
    a,b=top*w,bottom*w
    # Translate the entire seal at constant width. Only the outer cloth
    # absorbs perspective correction, so a round ring stays round.
    margin=round(w*.20)
    lt,lb=margin+a-w/2,margin+b-w/2
    rt,rb=w-margin+a-w/2,w-margin+b-w/2
    mesh=[((0,0,margin,h),(0,0,0,h,lb,h,lt,0)),
          ((margin,0,w-margin,h),(lt,0,lb,h,rb,h,rt,0)),
          ((w-margin,0,w,h),(rt,0,rb,h,w,h,w,0))]
    return image.transform(image.size,Image.Transform.MESH,mesh,Image.Resampling.BICUBIC)


def hero_face(reference,index,points,size):
    x,y=ORIGINS[index]
    return cel_surface(render_plane(reference,tuple((px+x,py+y) for px,py in points),size))


def paint_head(reference,index,key,side,faces,size):
    if side in ("west", "east"):
        cheek=hero_face(reference,index,HELMET_CHEEK[key],size)
        return cheek if side=="west" else ImageOps.mirror(cheek)
    if side!="front":
        return cel_surface(clean_thumbnail(faces[side],size))
    main=hero_face(reference,index,HELMET_FRONT[key],size)
    # The lower opening belongs on the model centerline. Keep its corners
    # narrow so the face plate shading and any central guard stay intact.
    width,height=main.size
    center=width//2
    if key=="isobu":
        # The reference has an uninterrupted central shell guard.
        return main
    half=width//8 if key=="kokuo" else width//10
    bevel=max(2,width//36)
    upper=round(height*37/48)
    lower=round(height*44/48)
    left,right=center-half,center+half-1
    ImageDraw.Draw(main).polygon(
        [(left,upper+bevel),(left+bevel,upper),(right-bevel,upper),(right,upper+bevel),
         (right,lower-bevel),(right-bevel,lower),(left+bevel,lower),(left,lower-bevel)],
        fill=(0,0,0,0))
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


def wrap_front_edges(faces, front, margin, side_width):
    """Use the pictured outer material instead of stretching a thumbnail row.

    The west UV meets the left edge of the front at its right boundary.
    The east UV meets the right edge at its left boundary.
    """
    width,height=front.size
    faces["front"]=front
    west=resize_albedo(ImageOps.mirror(front.crop((0,0,margin,height))),(side_width,height))
    east=resize_albedo(ImageOps.mirror(front.crop((width-margin,0,width,height))),(side_width,height))
    faces["west"]=align_border(west,front,west=True)
    faces["east"]=align_border(east,front,west=False)


# Opposite front surfaces in the original hero view (TL, BL, BR, TR).
# The native model has independent limb cells, unlike the vanilla atlas.
OPPOSITE_LIMBS = {
    "shukaku": (((103,58),(100,111),(117,108),(114,55)),((84,118),(78,161),(97,161),(99,115))),
    "matatabi": (((104,61),(105,111),(119,106),(114,58)),((85,117),(87,162),(104,161),(103,114))),
    "isobu": (((104,59),(103,112),(118,109),(114,53)),((85,116),(85,163),(102,162),(101,114))),
    "son_goku": (((104,59),(106,113),(119,109),(114,54)),((85,117),(87,161),(103,160),(102,114))),
    "kokuo": (((104,59),(105,112),(114,109),(110,55)),((84,114),(86,160),(100,160),(99,113))),
    "saiken": (((103,61),(103,109),(117,106),(114,56)),((83,118),(82,161),(100,162),(99,114))),
    "chomei": (((104,60),(106,112),(120,109),(113,56)),((85,117),(86,162),(103,162),(101,114))),
    "gyuki": (((104,62),(105,112),(119,109),(114,59)),((86,117),(87,162),(104,161),(101,115))),
    "kurama": (((104,63),(105,113),(115,110),(112,61)),((85,117),(84,162),(101,162),(101,114))),
}


def opposite_front(reference,index,key,part):
    points=OPPOSITE_LIMBS[key][0 if part=="arm" else 1]
    return hero_face(reference,index,points,(256,768))


def biped_work(reference,key,palette,master=None):
    index=list(DESIGNS).index(key)
    art=CROPS[key]
    sizes=(64*WORK_SCALE,32*WORK_SCALE)
    layers=[Image.new("RGBA",sizes),Image.new("RGBA",sizes)]
    for name,box in (("head",art.head),("body",art.torso),("arm",art.arm),("leg",art.leg)):
        faces=projected_faces(reference,index,box,palette)
        if name in ("arm","leg"):
            points=HERO_LIMBS[key][0 if name=="arm" else 1]
            limb=hero_face(reference,index,points,(4*WORK_SCALE,12*WORK_SCALE))
            if master:
                limb=master.panel(key,name,limb.size)
            wrap_front_edges(faces,limb,round(limb.width*9/24),4*WORK_SCALE)
        if name=="body":
            chest=hero_face(reference,index,HERO_BODY[key],(8*WORK_SCALE,12*WORK_SCALE))
            finish_chest(chest,key)
            chest=straighten_front(chest,*CHEST_AXES[key])
            if master:
                chest=master.panel(key,name,chest.size)
            wrap_front_edges(faces,chest,round(chest.width*7/36),4*WORK_SCALE)
        for side,(u,v,w,h) in uv_rects(name).items():
            x,y,W,H=(n*WORK_SCALE for n in (u,v,w,h))
            face=faces[side]
            cap=side in ("top","bottom")
            if name=="head" and master and side=="front":
                placed=master.panel(key,name,(W,H))
            elif name=="head":
                placed=paint_head(reference,index,key,side,faces,(W,H))
            elif face.size==(W,H):
                placed=face
            else:
                placed=cel_surface(clean_thumbnail(face,(W,H)))
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
    if key=="gyuki" and not master:
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
