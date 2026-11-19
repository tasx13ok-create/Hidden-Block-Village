"""Offline preview and matching 32-pixel icons from the exact vanilla UVs."""
import math

from PIL import Image, ImageDraw, ImageFont, ImageOps

from outfit import DESIGNS, FACE_ORDER, ORIGINS, PART_NAMES, SCALE, uv_rects

N = Image.Resampling.NEAREST
BG, CARD, INK, FAINT = "#16191f","#222731","#f4eade","#adb7c4"
# The left arms and legs use the mirrored right-hand UV in 1.12.2.
BODY = (
    ("head",(-4,-8,-4),(8,8,8),False),
    ("body",(-4,0,-2),(8,12,4),False),
    ("arm",(-8,0,-2),(4,12,4),False),
    ("arm",(4,0,-2),(4,12,4),True),
    ("leg",(-4,12,-2),(4,12,4),False),
    ("leg",(0,12,-2),(4,12,4),True),
)


def font(size, bold=False):
    filenames = ("DejaVuSans-Bold.ttf", "C:/Windows/Fonts/segoeuib.ttf") if bold else (
        "DejaVuSans.ttf", "C:/Windows/Fonts/segoeui.ttf")
    for name in filenames:
        try:
            return ImageFont.truetype(name,size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def text(d, where, value, size=16, color=INK, bold=False):
    d.text(where,value,font=font(size,bold),fill=color)


def affine_tile(canvas, face, p0, p1, p2):
    x,y=p0
    a,b=p1[0]-x,p1[1]-y
    c,d=p2[0]-x,p2[1]-y
    if (det:=a*d-b*c)<=.01:
        return
    left=max(0,math.floor(min(x,x+a,x+c,x+a+c)))
    top=max(0,math.floor(min(y,y+b,y+d,y+b+d)))
    right=min(canvas.width,math.ceil(max(x,x+a,x+c,x+a+c)))
    bottom=min(canvas.height,math.ceil(max(y,y+b,y+d,y+b+d)))
    if right<=left or bottom<=top:
        return
    w,h=face.size
    X,Y=left-x,top-y
    transform=(w*d/det,-w*c/det,w*(d*X-c*Y)/det,
               -h*b/det,h*a/det,h*(a*Y-b*X)/det)
    canvas.alpha_composite(face.transform((right-left,bottom-top),Image.Transform.AFFINE,transform,N),
                           (left,top))


def skin_texture(part, face, width, height):
    if part=="head":
        base="#a77c65" if face=="front" else "#674a40"
    elif part=="arm":
        base="#967861"
    else:
        base="#444753"
    texture=Image.new("RGBA",(width,height),base)
    if part=="head" and face=="front":
        d=ImageDraw.Draw(texture)
        d.rectangle((0,0,15,3),fill="#4b3534")
        for x in (3,10):
            d.rectangle((x,7,x+2,8),fill="#e0cfc1")
            d.point((x+1,8),fill="#533f46")
        d.rectangle((6,12,9,12),fill="#865c50")
    return texture


def render(layers, width, height, yaw=26, pitch=8, pixels=6, slot=None):
    theta,phi=math.radians(yaw),math.radians(pitch)
    c,s=math.cos(theta),math.sin(theta)
    cp,sp=math.cos(phi),math.sin(phi)
    center=8 if slot is None else (-4,5,13,21)[slot]
    def project(x,y,z):
        y-=center
        depth=s*x+c*z
        return (width/2+(x*c-z*s)*pixels,height/2+(y*cp-depth*sp)*pixels,depth*cp+y*sp)
    quads=[]
    for part,(x,y,z),(w,h,d),mirror in BODY:
        # The helmet, outer jacket and boots use the outer model at +1.
        # Leggings share the body and legs with +.5 inflation.
        if part=="head":
            passes=((0,0,1,"head"),(0,0,1.5,"hat"))
        elif part=="body":
            passes=((2,1,.5,part),(1,0,1,part))
        elif part=="arm":
            passes=((1,0,1,part),)
        else:
            passes=((2,1,.5,part),(3,0,1,part))
        requested=((None,None,0,part),)+passes if slot is None else passes
        for part_slot,layer,inflate,uv_part in requested:
            if slot is not None and slot!=part_slot:
                continue
            a,b,k=x-inflate,y-inflate,z-inflate
            X,Y,Z=x+w+inflate,y+h+inflate,z+d+inflate
            pts=[project(*p) for p in ((a,b,k),(X,b,k),(X,Y,k),(a,Y,k),(a,b,Z),(X,b,Z),(X,Y,Z),(a,Y,Z))]
            for name,(tl,tr,bl) in zip(FACE_ORDER,((1,5,2),(4,0,7),(4,5,0),(3,2,7),(0,1,3),(5,4,6))):
                opposite={"east":"west","west":"east"}.get(name,name)
                u,v,W,H=uv_rects(uv_part)[opposite if mirror else name]
                if layer is None:
                    face=skin_texture(part,name,W*SCALE,H*SCALE)
                else:
                    scale=layers[layer].width//64
                    face=layers[layer].crop((u*scale,v*scale,(u+W)*scale,(v+H)*scale))
                    if mirror:
                        face=ImageOps.mirror(face)
                    if not face.getbbox():
                        continue
                coords=[pts[j] for j in (tl,tr,bl)]
                quads.append(((coords[1][2]+coords[2][2])/2,face,coords))
    canvas=Image.new("RGBA",(width,height))
    for _,face,points in sorted(quads,key=lambda q:q[0],reverse=True):
        affine_tile(canvas,face,*(p[:2] for p in points))
    return canvas


def make_icon(layers,slot):
    view=render(layers,122,122,yaw=22,pitch=13,pixels=5,slot=slot)
    image=view.crop(view.getbbox())
    image.thumbnail((30,30),N)
    out=Image.new("RGBA",(32,32))
    out.alpha_composite(image,((32-image.width)//2,(32-image.height)//2))
    return out


def crop_reference(im,index):
    ox,oy=ORIGINS[index]
    return im.crop((ox+14,oy+4,ox+148,oy+170)).convert("RGBA")


def comparison(reference,finished,icons,palettes,out):
    sheet=Image.new("RGBA",(1450,1280),BG)
    d=ImageDraw.Draw(sheet)
    d.rectangle((36,36,43,113),fill="#ea913f")
    text(d,(61,29),"TAILED BEAST ARMOR",45,bold=True)
    text(d,(63,88),"OWNER REFERENCE / MATCHED 4K VANILLA UV TEXTURES",17,FAINT)
    for i,(key,design) in enumerate(DESIGNS.items()):
        x,y=36+i%3*468,140+i//3*371
        d.rounded_rectangle((x,y,x+442,y+342),radius=10,fill=CARD)
        text(d,(x+17,y+13),f"{i+1:02}  {design.title.upper()}",24,bold=True)
        ref=crop_reference(reference,i).resize((152,188),N)
        sheet.alpha_composite(ref,(x+18,y+69))
        text(d,(x+18,y+264),"YOUR ART",13,FAINT)
        model=render(finished[key],245,220,yaw=14,pitch=8,pixels=5.8)
        sheet.alpha_composite(model,(x+182,y+52))
        text(d,(x+194,y+264),"VANILLA ARMOR PREVIEW",13,FAINT)
        for j,image in enumerate(icons[key]):
            xx=x+192+j*46
            d.rectangle((xx,y+285,xx+34,y+320),fill="#575c65")
            sheet.alpha_composite(image,(xx+1,y+287))
        for j,(_,color) in enumerate(palettes[key].values()):
            xx=x+18+j*24
            d.rectangle((xx,y+295,xx+19,y+316),fill=color)
    sheet.save(out/"Compare-all-nine.png")

    for i,(key,design) in enumerate(DESIGNS.items()):
        sheet=Image.new("RGBA",(1260,470),BG)
        d=ImageDraw.Draw(sheet)
        text(d,(26,19),design.title.upper(),37,bold=True)
        text(d,(28,67),design.notes,17,FAINT)
        for j,(title,angle) in enumerate((("YOUR ART",None),("FRONT",0),("THREE-QUARTER",32),
                                               ("RIGHT",90),("BACK",180),("LEFT",-90))):
            x=26+j*204
            d.rounded_rectangle((x,113,x+185,412),radius=7,fill=CARD)
            if angle is None:
                view=crop_reference(reference,i).resize((169,209),N)
                sheet.alpha_composite(view,(x+8,156))
            else:
                view=render(finished[key],181,253,yaw=angle,pitch=8 if j==2 else 0,pixels=7)
                sheet.alpha_composite(view,(x+2,117))
            text(d,(x+11,385),title,12,FAINT)
        for j,(code,(_,color)) in enumerate(palettes[key].items()):
            x=27+j*94
            d.rectangle((x,430,x+19,449),fill=color)
            text(d,(x+25,430),"#%02X%02X%02X"%color,12,FAINT)
        sheet.save(out/f"{key}-comparison.png")


def icon_sheet(icons,out):
    sheet=Image.new("RGBA",(818,650),BG)
    d=ImageDraw.Draw(sheet)
    text(d,(22,20),"ARMOR ICONS",34,bold=True)
    text(d,(23,64),"Native 32 px  /  nearest 16 px hotbar proof",15,FAINT)
    for j,name in enumerate(PART_NAMES):
        text(d,(203+146*j,111),name.upper(),14,FAINT)
    for i,(key,design) in enumerate(DESIGNS.items()):
        y=141+i*52
        text(d,(23,y+9),design.title,18)
        for j,image in enumerate(icons[key]):
            x=205+j*146
            d.rectangle((x,y,x+35,y+35),fill="#8b8b8b")
            d.rectangle((x+48,y+8,x+67,y+27),fill="#45463d")
            sheet.alpha_composite(image,(x+2,y+2))
            sheet.alpha_composite(image.resize((16,16),N),(x+50,y+10))
    sheet.save(out/"Icons-32-and-16.png")
