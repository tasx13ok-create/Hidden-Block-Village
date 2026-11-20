"""Verify the master-to-game correspondence on every visible base panel."""
from io import BytesIO
from pathlib import Path
import sys
from zipfile import ZipFile
from PIL import Image
from front_compare import front_cells
from front_master import FrontMaster
from outfit import DESIGNS


def verify(guide,output):
    master=FrontMaster(guide)
    with ZipFile(output) as jar:
        for key in DESIGNS:
            head=master.panel(key,"head",(512,512))
            body=master.panel(key,"body",(512,768))
            expected=[head,body]
            for opposite in (False,True):
                arm=master.panel(key,"arm",(256,768),opposite)
                leg=master.panel(key,"leg",(256,768),opposite)
                expected.extend((arm,leg.crop((0,0,256,448)),leg.crop((0,448,256,768))))
            with Image.open(BytesIO(jar.read(f"assets/hvbijuu/textures/armor/{key}.png"))) as atlas:
                assert atlas.size==(8192,8192)
                for (bounds,_),tile in zip(front_cells(),expected):
                    x,y,X,Y=bounds
                    base_size=((X-x)*16,(Y-y)*16)
                    target_size=((X-x)*32,(Y-y)*32)
                    target=tile.resize(base_size,Image.Resampling.NEAREST).resize(target_size,Image.Resampling.NEAREST)
                    actual=atlas.crop((x*32,y*32,X*32,Y*32))
                    assert actual.tobytes()==target.tobytes(),(key,bounds)
    print("72 of 72 visible base panels restored exactly from registered master crops.")


if __name__=="__main__":
    verify(*map(Path,sys.argv[1:]))
