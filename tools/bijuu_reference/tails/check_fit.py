"""Check sockets in the shipped hierarchy and compare the shipped tail art to V3."""
import io
import json
import struct
import sys
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from merge import BEASTS


def contains(box, point):
    origin, size = box
    return all(start <= value <= start+length for start,length,value in zip(origin,size,point))


def verify(artifact, reference):
    with ZipFile(artifact) as jar, ZipFile(reference) as archive:
        for index,key in enumerate(BEASTS):
            data=io.BytesIO(jar.read(f"assets/hvbijuu/tails/{index}.bin"))

            def read(fmt):
                parser=struct.Struct(">"+fmt)
                return parser.unpack(data.read(parser.size))

            nodes=[]
            for i in range(read("i")[0]):
                parent,*pivot=read("i3f")
                read("3f")
                boxes=[]
                for j in range(read("i")[0]):
                    read("2i")
                    boxes.append((read("3f"),read("3i")))
                nodes.append((parent,pivot,boxes))
            assert nodes[0][:2]==(-1,[0,10,2.5])
            # Back surface of the fitted chest is z=2.44 in body space.
            assert any(contains(b,[0,0,-.06]) for b in nodes[0][2])
            for parent,pivot,boxes in nodes[1:]:
                assert any(contains(box,pivot) for box in nodes[parent][2]),(key,parent,pivot)
                assert any(contains(box,[0,0,0]) for box in boxes),(key,boxes)
            count=read("i")[0]
            assert count==index+1
            for i in range(count):
                read(str(read("i")[0])+"i")
            assert not data.read()
            prefix="Naruto_Canon_Tails_v3/"
            model=json.loads(archive.read(prefix+"geometry/"+key+".json"))
            texture=Image.open(io.BytesIO(archive.read(prefix+model["texture"]["file"]))).convert("RGBA")
            for suffix in (("", "_cage") if key=="kurama" else ("",)):
                atlas=Image.open(io.BytesIO(jar.read(f"assets/hvbijuu/textures/armor/{key}{suffix}.png")))
                assert atlas.size==(8192,8192)
                target=texture.resize((texture.width*2,texture.height*2),Image.Resampling.NEAREST)
                assert atlas.crop((0,0,*target.size)).convert("RGBA").tobytes()==target.tobytes()
            print(key,len(nodes)-1,"connected joints,",count,"appendages; V3 pigments exact")


if __name__=="__main__":
    verify(*map(Path,sys.argv[1:]))
