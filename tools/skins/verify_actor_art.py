"""Inspect supplied originals and deterministically regenerate to a new private directory."""
from pathlib import Path
import argparse, hashlib, importlib.util, json, sys
from PIL import Image

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--project-root",type=Path,required=True)
parser.add_argument("--regenerate-to",type=Path,required=True)
parser.add_argument("--report",type=Path,required=True)
options=parser.parse_args()
ROOT=options.project_root.resolve()
if options.report.exists(): raise FileExistsError("Preserve prior reports; use a new report path")
TOOLS=ROOT/'tools/skins'
sys.path.insert(0,str(TOOLS))
import hdpaint
spec=importlib.util.spec_from_file_location('owned_hdcast',TOOLS/'hdcast.py')
cast=importlib.util.module_from_spec(spec)
spec.loader.exec_module(cast)
source=ROOT/'modules/vale-encounters/src/main/resources/assets/valeencounters/textures/entity'
generated=options.regenerate_to
paths=cast.generate(generated)
rows=[]
for out in paths:
    original=source/out.name
    with Image.open(original) as im, Image.open(out) as rendered:
        assert im.mode=='RGBA' and im.size==(256,256),out.name
        assert rendered.mode=='RGBA' and rendered.size==im.size
        assert im.tobytes()==rendered.tobytes(),('Generator pixels differ',out.name)
        faces=0
        for part in (hdpaint.HEAD,hdpaint.BODY,hdpaint.RARM,hdpaint.LARM,hdpaint.RLEG,hdpaint.LLEG):
            for x,y,w,h in part.values():
                rect=tuple(round(v*4) for v in (x,y,x+w,y+h))
                assert 0<=rect[0]<rect[2]<=256 and 0<=rect[1]<rect[3]<=256
                assert im.crop(rect).getchannel('A').getextrema()==(255,255),(out.name,rect,'base UV must stay opaque')
                faces+=1
        rows.append({'file':out.name,'sha256':hashlib.sha256(original.read_bytes()).hexdigest(),'pixel_sha256':hashlib.sha256(im.tobytes()).hexdigest(),'base_faces_opaque':faces,'generator_pixels_identical':True})
record={'scope':'HEADLESS_ORIGINAL_ART_AND_UV_RECT_INSPECTION','textures':rows,'model_uv_units':[64,64],'source_image_dimensions':[256,256],'imported_or_native_npc_assets_changed':False,'actual_gpu_rendered':False}
options.report.parent.mkdir(parents=True,exist_ok=True)
options.report.write_text(json.dumps(record,indent=2)+'\n')
print('ACTOR_ART_PASS textures='+str(len(rows))+' opaque_base_faces='+str(sum(r['base_faces_opaque'] for r in rows)))
