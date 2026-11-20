"""Apply the fitted armor palette to the nine classic player skins."""
import io
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image
from fit import SURFACE, tint


def recolor_skins(skins, reference, output):
    with ZipFile(skins) as src, ZipFile(reference) as ref, ZipFile(output,"x",ZIP_DEFLATED) as dst:
        for beast in SURFACE:
            name=beast+".png"
            original=Image.open(io.BytesIO(src.read(name))).convert("RGBA")
            assert original.size==(64,64)
            tail=Image.open(io.BytesIO(ref.read("Naruto_Canon_Tails_v3/textures/"+name)))
            result=tint(original,tail,beast)
            assert result.getchannel("A").tobytes()==original.getchannel("A").tobytes()
            data=io.BytesIO()
            result.save(data,format="PNG")
            dst.writestr(name,data.getvalue())


if __name__=="__main__":
    recolor_skins(*map(Path,sys.argv[1:]))
