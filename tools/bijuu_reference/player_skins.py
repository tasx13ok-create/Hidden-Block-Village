"""Export the mapped outfits as wearable Classic 64-square Java player skins."""
from io import BytesIO
from pathlib import Path
import sys
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image
from native import crop_face
from outfit import DESIGNS,uv_rects


def export(source,target):
    with ZipFile(target,"x",ZIP_DEFLATED) as out:
        for key in DESIGNS:
            folder=source/"textures/models/armor"
            with Image.open(folder/f"{key}_layer_1.png") as first, Image.open(folder/f"{key}_layer_2.png") as second:
                joined=Image.alpha_composite(second,first)
            skin=Image.new("RGBA",(64,64))
            for part in ("head","body","arm","leg"):
                for mirrored in ((False,True) if part in ("arm","leg") else (False,)):
                    # Classic left arm starts at 32,48 and left leg at 16,48.
                    dx,dy=((-8,32) if part=="arm" else (16,32)) if mirrored else (0,0)
                    for side,(x,y,w,h) in uv_rects(part).items():
                        tile=crop_face(joined,part,side,mirrored)
                        if mirrored and side=="front":
                            with Image.open(source/"native-limb-faces"/f"{key}_{part}.png") as distinct:
                                tile=distinct.convert("RGBA")
                        skin.paste(tile.resize((w,h),Image.Resampling.BOX),(x+dx,y+dy))
            data=BytesIO()
            skin.save(data,format="PNG")
            out.writestr(key+".png",data.getvalue())
    with ZipFile(target) as check:
        assert check.testzip() is None and len(check.namelist())==9


if __name__=="__main__":
    export(*map(Path,sys.argv[1:]))
