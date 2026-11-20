"""Compare the source poster, generated orthographic master and actual equipped armor."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw
from front_master import ROWS
from outfit import DESIGNS, ORIGINS
from proof import font


def compare(original,master,shots,destination):
    sheet=Image.new("RGB",(2250,1308),"#121820")
    draw=ImageDraw.Draw(sheet)
    for i,(key,design) in enumerate(DESIGNS.items()):
        x,y=i%3*750,i//3*436
        draw.text((x+15,y+6),design.title,font=font(25,True),fill="white")
        ox,oy=ORIGINS[i]
        reference=original.crop((ox+16,oy,ox+146,oy+173))
        hl,hr,ht,hb,al,cl,cr,ar,st,wa,ll,lr,sole=ROWS[i]
        factor=master.width/760
        guide=master.crop(tuple(round(v*factor) for v in (al-5,ht-6,ar+5,sole+5)))
        with Image.open(shots/f"front-{key}.png") as shot:
            w,h=shot.size
            game=shot.crop((round(w*.395),round(h*.36),round(w*.605),round(h*.90)))
        for j,(view,label) in enumerate(((reference,"Original"),(guide,"New reference"),(game,"In game · BSL"))):
            view=view.convert("RGBA")
            ratio=min(236/view.width,345/view.height)
            view=view.resize((round(view.width*ratio),round(view.height*ratio)),Image.Resampling.LANCZOS)
            offset=(x+j*246+(246-view.width)//2,y+42+(345-view.height)//2)
            sheet.paste(view,offset,view)
            draw.text((x+j*246+14,y+397),label,font=font(16),fill="white")
    sheet.save(destination)


if __name__=="__main__":
    compare(Image.open(sys.argv[1]),Image.open(sys.argv[2]),Path(sys.argv[3]),Path(sys.argv[4]))
