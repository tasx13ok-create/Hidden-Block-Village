"""Verify the full shipped visor delta against the previous color-matched JAR."""
import io
from pathlib import Path
import sys
from zipfile import ZipFile
from PIL import Image, ImageChops, ImageDraw
from visors import PANES


def verify(previous, candidate):
    textures={f"assets/hvbijuu/textures/armor/{name}.png":name for name in (*PANES,"kurama_cage")}
    with ZipFile(previous) as old, ZipFile(candidate) as new:
        assert set(old.namelist())==set(new.namelist())
        for path in old.namelist():
            if path not in textures:
                if path not in ("village/bijuu/ArmorCoverage.class","village/bijuu/ArmorCoverage$Hidden.class"):
                    assert old.read(path)==new.read(path),path
                continue
            a=Image.open(io.BytesIO(old.read(path)))
            b=Image.open(io.BytesIO(new.read(path)))
            assert a.mode==b.mode=="RGBA" and a.size==b.size==(8192,8192)
            for band in "RGB":
                assert ImageChops.difference(a.getchannel(band),b.getchannel(band)).getbbox() is None,(path,band)
            source=a.getchannel("A")
            target=b.getchannel("A")
            region=(1152,2560,1408,2816)
            alpha=source.crop(region).resize((128,128),Image.Resampling.NEAREST)
            draw=ImageDraw.Draw(alpha)
            for rect in PANES[textures[path].removesuffix("_cage")]:
                x,y,right,bottom=(round(v*128/160) for v in rect)
                draw.rectangle((x,y,right-1,bottom-1),fill=0)
            expected=alpha.resize((256,256),Image.Resampling.NEAREST)
            source.paste(expected,region)
            assert ImageChops.difference(source,target).getbbox() is None,path
            print(textures[path],expected.histogram()[0],"transparent visor texels")


if __name__=="__main__":
    verify(*map(Path,sys.argv[1:]))
