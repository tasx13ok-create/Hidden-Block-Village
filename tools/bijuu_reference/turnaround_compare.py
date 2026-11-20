"""Create one six-view reference and in-game comparison per outfit, plus a full roster."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw
from front_master import ROWS
from turnaround import REGISTRATION
from outfit import DESIGNS
from proof import font


def crop_scaled(image,rect,width):
    return image.crop(tuple(round(v*image.width/width) for v in rect))


def write(front,turn,shots,out):
    out.mkdir(parents=True,exist_ok=True)
    overview=Image.new("RGB",(2100,1800),"#10161b")
    for i,(key,d) in enumerate(DESIGNS.items()):
        card=Image.new("RGB",(1200,1020),"#192129")
        draw=ImageDraw.Draw(card)
        draw.text((32,16),d.title,font=font(39,True),fill="white")
        hl,hr,ht,hb,al,cl,cr,ar,st,wa,ll,lr,sole=ROWS[i]
        sx,bx,ys=REGISTRATION[i]
        refs=[crop_scaled(front,(al-4,ht-4,ar+4,sole+3),760),
              crop_scaled(turn,(sx[0]-5,ys[0]-5,sx[1]+5,ys[3]+4),900),
              crop_scaled(turn,(bx[4]-6,ys[0]-5,bx[5]+6,ys[3]+4),900)]
        for n,view in enumerate(("front","side","back")):
            x=400*n
            draw.text((x+22,78),view.title()+" reference",font=font(21),fill="#d6e1e6")
            guide=refs[n].convert("RGBA")
            guide.thumbnail((370,365),Image.Resampling.LANCZOS)
            # Scale small source panels to the same figure height.
            ratio=365/guide.height
            guide=guide.resize((round(guide.width*ratio),365),Image.Resampling.LANCZOS)
            card.paste(guide,(x+(400-guide.width)//2,116),guide)
            with Image.open(shots/f"{view}-{key}.png") as raw:
                w,h=raw.size
                tile=raw.crop((round(w*.375),round(h*.355),round(w*.625),round(h*.905)))
                tile.thumbnail((382,424),Image.Resampling.LANCZOS)
            card.paste(tile,(x+(400-tile.width)//2,551))
            draw.text((x+22,510),"In game · "+view,font=font(21),fill="white")
        draw.text((24,983),"BSL daylight. Tails hidden in side and back views to show the armor.",font=font(17),fill="#ccd3d8")
        card.save(out/f"{key}.png")
        overview.paste(card.resize((700,595),Image.Resampling.LANCZOS),(i%3*700,i//3*600))
    overview.save(out/"All-nine.png")


if __name__=="__main__":
    write(Image.open(sys.argv[1]),Image.open(sys.argv[2]),Path(sys.argv[3]),Path(sys.argv[4]))
