"""Verify every vertical corner after native projection and the final 8K resize."""
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import sys
from PIL import Image
from native import Band
from outfit import DESIGNS,FACE_ORDER


def cuboids():
    head,chest,leg,boot=(Band(y,160) for y in (80,100,132,145))
    yield head.box(8,8,8)
    yield chest.box(8,12,4)
    for _ in range(2):
        yield chest.box(4,12,4)
        chest.box(4,2,4)
        chest.box(4,3,1)
        yield leg.box(4,7,4)
        leg.box(4,3,1)
        yield boot.box(4,5,4)
        boot.box(4,2,5)


def check(archive):
    count=0
    with ZipFile(archive) as z:
        for key in DESIGNS:
            with Image.open(BytesIO(z.read(f"assets/hvbijuu/textures/armor/{key}.png"))) as atlas:
                assert atlas.size==(8192,8192)
                for group in cuboids():
                    faces={side:atlas.crop(tuple(32*n for n in rect)) for side,rect in zip(FACE_ORDER,group)}
                    for a,b in (("front","east"),("east","back"),("back","west"),("west","front")):
                        left,right=faces[a],faces[b]
                        assert left.crop((left.width-1,0,left.width,left.height)).tobytes()==right.crop((0,0,1,right.height)).tobytes(),(key,a,b)
                        count+=1
    print(f"{count} vertical corner pairs match at every pixel.")


if __name__=="__main__":
    check(Path(sys.argv[1]))
