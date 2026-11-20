"""Show the source figure next to the actual native front UVs on a level biped."""
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import sys

from PIL import Image, ImageDraw
from native import Band
from outfit import DESIGNS, FACE_ORDER, ORIGINS
from proof import font


def front_cells():
    head,chest,leg,boot=(Band(y,160) for y in (80,100,132,145))
    front=FACE_ORDER.index("front")
    yield head.box(8,8,8)[front],(4,0)
    yield chest.box(8,12,4)[front],(4,8)
    for arm_x,leg_x in ((0,4),(12,8)):
        yield chest.box(4,12,4)[front],(arm_x,8)
        chest.box(4,2,4)
        chest.box(4,3,1)
        yield leg.box(4,7,4)[front],(leg_x,20)
        leg.box(4,3,1)
        yield boot.box(4,5,4)[front],(leg_x,27)
        boot.box(4,2,5)


def compare(reference,jar,output):
    sheet=Image.new("RGB",(1620,1254),"#141922")
    draw=ImageDraw.Draw(sheet)
    with ZipFile(jar) as archive:
        for i,(key,design) in enumerate(DESIGNS.items()):
            x,y=i%3*540,i//3*418
            draw.text((x+14,y+6),design.title,font=font(25,True),fill="white")
            ox,oy=ORIGINS[i]
            ref=reference.crop((ox+17,oy,ox+145,oy+172)).resize((256,344),Image.Resampling.NEAREST)
            sheet.paste(ref,(x+12,y+40))
            raw=archive.read(f"assets/hvbijuu/textures/armor/{key}.png")
            with Image.open(BytesIO(raw)) as atlas:
                scale=atlas.width//256
                for box,(px,py) in front_cells():
                    tile=atlas.crop(tuple(n*scale for n in box)).convert("RGBA")
                    tile=tile.resize(((box[2]-box[0])*10,(box[3]-box[1])*10),Image.Resampling.LANCZOS)
                    sheet.paste(tile,(x+325+px*10,y+52+py*10),tile)
            draw.text((x+12,y+388),"Original",font=font(15),fill="white")
            draw.text((x+306,y+388),"Front texture placement",font=font(15),fill="white")
    sheet.save(output)


if __name__=="__main__":
    compare(Image.open(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]))
