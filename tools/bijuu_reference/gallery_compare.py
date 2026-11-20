"""Pair the original art with centered crops from the isolated Forge gallery."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw
from outfit import DESIGNS
from proof import crop_reference, font


def compare(reference, shots, output):
    sheet=Image.new("RGB",(1800,1332),"#141922")
    draw=ImageDraw.Draw(sheet)
    for i,(key,design) in enumerate(DESIGNS.items()):
        x,y=(i%3)*600,(i//3)*444
        draw.text((x+14,y+8),design.title,font=font(26,True),fill="white")
        left=crop_reference(reference,i).resize((240,300),Image.Resampling.NEAREST)
        with Image.open(shots/f"front-{key}.png") as shot:
            w,h=shot.size
            crop=shot.crop((round(w*.395),round(h*.36),round(w*.605),round(h*.90)))
            crop.thumbnail((335,350),Image.Resampling.LANCZOS)
        sheet.paste(left,(x+12,y+68))
        sheet.paste(crop,(x+258+(330-crop.width)//2,y+48))
        draw.text((x+14,y+399),"Original",font=font(16),fill="white")
        draw.text((x+280,y+399),"In game · BSL",font=font(16),fill="white")
    sheet.save(output)


if __name__=="__main__":
    compare(Image.open(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]))
