"""Double the finished armor texel grid while keeping normalized UVs and pixel values exact."""
import hashlib
import io
from pathlib import Path
import sys
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image

def export(source, target, native):
    source_digest=hashlib.sha256(source.read_bytes()).hexdigest()
    enlarged=0
    with ZipFile(source) as src, ZipFile(target,"x",ZIP_DEFLATED) as dst:
        for name in src.namelist():
            if not native and not name.startswith("textures/"):
                continue
            raw=src.read(name)
            if name.endswith(".png") and ("/textures/armor/" in name or name.startswith("textures/models/armor/")):
                with Image.open(io.BytesIO(raw)) as original:
                    assert original.width==4096
                    doubled=original.resize((8192,original.height*2),Image.Resampling.NEAREST)
                    buf=io.BytesIO()
                    doubled.save(buf,format="PNG",compress_level=3)
                    raw=buf.getvalue()
                    del doubled
                    with Image.open(io.BytesIO(raw)) as check:
                        assert check.resize(original.size,Image.Resampling.NEAREST).tobytes()==original.tobytes()
                enlarged+=1
            dst.writestr(name,raw)
        if not native:
            dst.writestr("START-HERE.txt","8K vanilla-layout texture sources and matching item icons. Source ZIP SHA-256 "+source_digest+"\n")
    with ZipFile(target) as check:
        assert check.testzip() is None
    print(target.name,enlarged,"maps",hashlib.sha256(target.read_bytes()).hexdigest(),flush=True)

if __name__=="__main__":
    source,target=map(Path,sys.argv[1:])
    export(source,target,source.suffix.lower()==".jar")
