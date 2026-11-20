"""Assemble unretouched framebuffer captures of equipped players and moving tails."""
import argparse
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

ROSTER=("Shukaku","Matatabi","Isobu","Son Goku","Kokuo","Saiken","Chomei","Gyuki","Kurama")
def lettering(size):
    return ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf",size)

def tile(path,size,key):
    with Image.open(path) as im:
        w,h=im.size
        # Keep the full fan in frame at the widest turn, with a closer crop
        # for compact sets. Maintain identical scale across each triptych.
        fraction={"shukaku":.53,"kurama":.90,"gyuki":.80}.get(key,.68)
        height=round(h*fraction)
        width=round(height*4/3)
        cy=min(h-height//2,round(h*.585))
        cx=w//2
        return im.crop((cx-width//2,cy-height//2,cx-width//2+width,cy-height//2+height)).convert("RGB").resize(size,Image.Resampling.LANCZOS)

def assemble(shots,out):
    out.mkdir(parents=True,exist_ok=True)
    cards=[]
    sequences=[]
    shoulders=Image.new("RGB",(990,900),"#111923")
    for title in ROSTER:
        key=title.lower().replace(" ","_")
        card=Image.new("RGB",(1500,450),"#111923")
        d=ImageDraw.Draw(card)
        d.text((16,4),title,font=lettering(30),fill="white")
        for j,view in enumerate(("front","side","back")):
            card.paste(tile(shots/f"{view}-{key}.png",(492,369),key),(j*500+4,45))
            d.text((j*500+14,417),view.title(),font=lettering(19),fill="#e1e9ef")
        card.save(out/f"{key}-views.png")
        cards.append(card)
        shoulder=shots/f"shoulders-{key}.png"
        if shoulder.exists():
            with Image.open(shoulder) as im:
                w,h=im.size
                tile_crop=im.crop((round(w*.425),round(h*.405),round(w*.575),round(h*.67)))
                tile_crop.thumbnail((320,264),Image.Resampling.LANCZOS)
                i=len(cards)-1
                sx,sy=i%3*330,i//3*300
                shoulders.paste(tile_crop,(sx+(330-tile_crop.width)//2,sy+32))
                ImageDraw.Draw(shoulders).text((sx+14,sy+4),title,font=lettering(20),fill="white")
        frames=[tile(p,(448,336),key) for p in sorted(shots.glob(f"motion-{key}-*.png"))]
        assert len(frames)==15,(key,len(frames))
        frames[0].save(out/f"{key}-motion.webp",save_all=True,append_images=frames[1:],duration=200,loop=0,quality=88)
        sequences.append(frames)
    still=Image.new("RGB",(1800,1350),"#111923")
    for i,card in enumerate(cards):
        # Two triptychs across and five rows keep all nine labels readable.
        still.paste(card.resize((900,270),Image.Resampling.LANCZOS),(i%2*900,i//2*270))
    still.save(out/"All-nine-views.png")
    if (shots/"shoulders-kurama.png").exists():
        shoulders.save(out/"Shoulder-details.png")
    animation=[]
    for frame in range(15):
        board=Image.new("RGB",(1080,855),"#111923")
        d=ImageDraw.Draw(board)
        for i,title in enumerate(ROSTER):
            x,y=i%3*360,i//3*285
            board.paste(sequences[i][frame].resize((352,264),Image.Resampling.LANCZOS),(x+4,y+20))
            d.text((x+9,y),title,font=lettering(15),fill="white")
        animation.append(board)
    animation[0].save(out/"All-nine-motion.webp",save_all=True,append_images=animation[1:],duration=200,loop=0,quality=85)


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("shots",type=Path)
    parser.add_argument("out",type=Path)
    args=parser.parse_args()
    assemble(args.shots,args.out)
