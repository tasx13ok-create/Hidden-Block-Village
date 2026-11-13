"""Original exact-UV SVG armor authoring/export. Does not read or edit existing artwork."""
from pathlib import Path
import json,hashlib,zipfile,xml.etree.ElementTree as ET,html,sys
from PIL import Image,ImageDraw
import numpy as np

DRAFT=Path(__file__).resolve().parent
ROOT=Path(sys.argv[1]).resolve()
# Output must be a new destination; existing candidates are preserved.
ROOT.mkdir(parents=True,exist_ok=False)
MASTER=ROOT/'masters';ASSETS=ROOT/'resourcepack/assets/valeorders/textures/models/armor'
MASTER.mkdir(exist_ok=True);ASSETS.mkdir(parents=True,exist_ok=True)
PALETTE={'cloth':'#172a3b','shadow':'#101a2a','seam':'#344d60','ivory':'#dee5df','light':'#f2f2de','steel':'#9baeb5','edge':'#637d8b','blue':'#65cde8','blue_dark':'#3584ae','bandage':'#c2c7b4','ink':'#283945','sole':'#172027'}
PARTS={'head':(0,0,8,8,8),'headwear':(32,0,8,8,8),'body':(16,16,8,12,4),'arm':(40,16,4,12,4),'leg':(0,16,4,12,4)}

def rects(part):
 u,v,w,h,d=PARTS[part]
 return {'east':(u+d+w,v+d,d,h),'west':(u,v+d,d,h),'top':(u+d,v,w,d),'bottom':(u+d+w,v,w,d),'front':(u+d,v+d,w,h),'back':(u+d+w+d,v+d,w,h)}

class Art:
 def __init__(self):self.shapes=[];self.lines=[]
 def rect(self,x,y,w,h,color):
  assert all((float(v)*4).is_integer() for v in(x,y,w,h)) and w>0 and h>0
  assert x>=0 and y>=0 and x+w<=64 and y+h<=32
  self.shapes.append((x,y,w,h,PALETTE.get(color,color)))
 def line(self,points,color,width=.25):
  assert all(0<=x<=64 and 0<=y<=32 for x,y in points)
  self.lines.append((points,PALETTE.get(color,color),width))
 def svg(self):
  source='<svg xmlns="http://www.w3.org/2000/svg" width="256" height="128" viewBox="0 0 64 32" shape-rendering="crispEdges">\n'
  source+='<metadata>Original HBV code-native SVG authoring. Exact native ModelBiped64x32 UVs. No source PNG or icon is resized/copied.</metadata>\n'
  for x,y,w,h,color in self.shapes:source+=f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{color}"/>\n'
  for points,color,width in self.lines:source+='<polyline points="'+' '.join(str(x)+','+str(y)for x,y in points)+'" fill="none" stroke="'+color+'" stroke-width="'+str(width)+'" stroke-linecap="square" stroke-linejoin="miter"/>\n'
  return source+'</svg>\n'
 def export(self,name):
  svg=self.svg();(MASTER/(name+'.svg')).write_text(svg,encoding='utf-8')
  # Rasterize only these authored integer rectangles; no existing raster image is edited.
  im=Image.new('RGBA',(256,128));draw=ImageDraw.Draw(im)
  for node in ET.fromstring(svg):
   a=node.attrib
   if node.tag.endswith('rect'):
    x=round(float(a['x'])*4);y=round(float(a['y'])*4);w=round(float(a['width'])*4);h=round(float(a['height'])*4);draw.rectangle((x,y,x+w-1,y+h-1),fill=a['fill'])
   elif node.tag.endswith('polyline'):
    points=[tuple(round(float(v)*4)for v in p.split(','))for p in a['points'].split()];draw.line(points,fill=a['stroke'],width=max(1,round(float(a['stroke-width'])*4)))
  im.save(ASSETS/(name+'.png'));return im

outer=Art();inner=Art()
# Head island only: continuous two-pixel headband, face and crown transparent.
for side,(x,y,w,h) in rects('head').items():
 if side not in('top','bottom'):
  outer.rect(x,y,w,2,'cloth');outer.rect(x,y,w,1,'seam')
# Forehead plate sits exactly on the native front face, never the headwear shell.
outer.rect(9,8,6,2,'edge');outer.rect(10,8,4,2,'steel');outer.rect(11,8,2,1,'light');outer.rect(9,9,1,1,'blue');outer.rect(14,9,1,1,'blue')
# White Cloud-inspired utility vest; the design is a gameplay adaptation, not a named canon outfit.
for face,(x,y,w,h) in rects('body').items():
 outer.rect(x,y,w,h,'ivory' if face in('front','back','top')else'steel')
 if face in('front','back'):
  outer.rect(x,y,w,1,'light');outer.rect(x,y+10,w,2,'cloth')
  outer.rect(x,y+3,1,7,'edge');outer.rect(x+w-1,y+3,1,7,'edge')
  outer.rect(x+2,y+1,w-4,1,'cloth')
  if face=='front':
   outer.rect(x+3,y+2,2,7,'seam');outer.rect(x+3,y+2,1,7,'cloth')
   outer.rect(x+1,y+4,2,3,'light');outer.rect(x+5,y+4,2,3,'light')
   outer.rect(x+1,y+7,2,1,'steel');outer.rect(x+5,y+7,2,1,'steel')
   for dx,dy in[(1,2),(2,3),(1,4),(2,5),(1,6)]:outer.rect(x+dx,y+dy,1,1,'blue')
   for dx,dy in[(6,2),(5,3),(6,4),(5,5),(6,6)]:outer.rect(x+dx,y+dy,1,1,'blue')
   outer.rect(x+3,y+10,2,1,'steel');outer.rect(x+3,y+11,2,1,'blue_dark')
  else:
   outer.rect(x+2,y+3,4,4,'edge');outer.rect(x+3,y+3,2,1,'steel');outer.rect(x+2,y+4,3,1,'blue');outer.rect(x+3,y+5,3,1,'blue');outer.rect(x+3,y+6,2,1,'steel')
   outer.rect(x+1,y+8,6,1,'steel')
 elif face in('east','west'):
  outer.rect(x,y+2,w,7,'cloth');outer.rect(x,y+2,1,7,'blue_dark');outer.rect(x,y+9,w,1,'edge')
# Shared arm island is mirrored by native left-arm model. Keep all mirrored motifs symmetrical.
for face,(x,y,w,h) in rects('arm').items():
 outer.rect(x,y,w,h,'cloth')
 if face in('east','west','front','back'):
  outer.rect(x,y,w,3,'ivory');outer.rect(x,y,w,1,'light');outer.rect(x,y+3,w,1,'steel')
  outer.rect(x+1,y+4,2,1,'blue');outer.rect(x+1,y+5,1,1,'blue_dark')
  outer.rect(x,y+7,w,3,'bandage');outer.rect(x,y+8,w,1,'steel');outer.rect(x+1,y+10,2,1,'blue_dark')
# Native boots use the leg island on outer atlas; only lower shin/straps are opaque.
for face,(x,y,w,h) in rects('leg').items():
 if face in('front','back','east','west'):
  outer.rect(x,y+7,w,3,'cloth');outer.rect(x,y+7,w,1,'bandage');outer.rect(x+1,y+8,2,1,'blue_dark')
  outer.rect(x,y+11,w,1,'sole');outer.rect(x,y+10,1,1,'cloth');outer.rect(x+w-1,y+10,1,1,'cloth')
 elif face=='bottom':outer.rect(x,y,w,h,'sole')
# Leggings atlas: cloth legs and waist only. Chest/arms/head remain transparent.
for face,(x,y,w,h) in rects('leg').items():
 inner.rect(x,y,w,h,'cloth'if face in('front','top')else'shadow')
 if face in('front','back','east','west'):
  inner.rect(x,y,w,1,'seam');inner.rect(x,y+5,w,2,'bandage');inner.rect(x+1,y+5,2,1,'steel');inner.rect(x,y+8,1,4,'seam')
for face,(x,y,w,h)in rects('body').items():
 if face in('front','back','east','west'):inner.rect(x,y+9,w,3,'cloth');inner.rect(x,y+9,w,1,'seam')


# V3: fresh quarter-UV-unit vector detailing, not bitmap upscaling.
# Original three wind-cloud curls engraved on the forehead plate.
for points in [[(10.5,8.75),(10.75,8.5),(11.25,8.5),(11.5,8.75),(11.25,9)],[(11.5,9.25),(11.75,9),(12.25,9),(12.5,9.25),(12.25,9.5)],[(12.5,8.75),(12.75,8.5),(13.25,8.5),(13.5,8.75),(13.25,9)]]:outer.line(points,'ink',.25)
outer.line([(21.75,20),(24,22.25),(26.25,20)],'cloth',.75)
outer.line([(21.75,20.25),(24,22.5),(26.25,20.25)],'steel',.25)
# Pouch edges, folded flaps and restrained hand stitches.
for x in(21,25):
 outer.rect(x,24,2,3,'ivory');outer.rect(x+.25,24.25,1.5,.5,'steel');outer.rect(x+.75,24.75,.5,.25,'ink')
 outer.line([(x,24),(x,27),(x+2,27),(x+2,24)],'edge',.25)
 for yy in(25.25,26,26.75):outer.rect(x+.25,yy,.25,.25,'light');outer.rect(x+1.5,yy,.25,.25,'light')
for xx in(20.75,21.5,22.25,23,23.75,24.5,25.25,26,26.75):outer.rect(xx,29.5,.25,.25,'edge')
# The back now carries a stitched cloth clan patch rather than a square machine panel.
outer.rect(33,22.5,6,5,'ivory');outer.line([(33.25,22.75),(38.75,22.75),(38.75,27.25),(33.25,27.25),(33.25,22.75)],'steel',.25)
for points in [[(33.75,24),(34.25,23.5),(35.25,23.5),(35.75,24),(35.25,24.5)],[(35.25,25.5),(35.75,25),(36.75,25),(37.25,25.5),(36.75,26)],[(36.25,24),(36.75,23.5),(37.75,23.5),(38.25,24),(37.75,24.5)]]:outer.line(points,'blue_dark',.25)
for face,(x,y,w,h)in rects('arm').items():
 if face in('front','back','east','west'):
  for offset in(0,1.25,2.5):outer.line([(x+.25,y+7.25+offset*.5),(x+w-.25,y+8.5+offset*.5)],'edge',.25)
  outer.line([(x+.25,y+.75),(x+w-.25,y+.75)],'steel',.25)

images={'storm_layer_1':outer.export('storm_layer_1'),'storm_layer_2':inner.export('storm_layer_2')}
uv={'schema':1,'canvas':[64,32],'asset_pixels':[256,128],'native_uv_normalization':[64,32],'authored_detail_step':.25,'layers':{'1':{'model_inflation':1.0,'slots':['HEAD','CHEST','FEET'],'headwear':'transparent','mirrored_left_arm_leg':True},'2':{'model_inflation':.5,'slots':['LEGS'],'body':'waist only','mirrored_left_leg':True}},'parts':{k:{'texture_offset':list(v[:2]),'box_size':list(v[2:]),'faces':{n:list(q)for n,q in rects(k).items()}}for k,v in PARTS.items()},'source_contract':'Prepared MCP stable39 ModelBiped/ModelBox/LayerBipedArmor; native getArmorTexture preserved'}
(ROOT/'uv-contract.json').write_text(json.dumps(uv,indent=2)+'\n',encoding='utf-8')
# A vector guide is engineering metadata; it is not included in the runtime textures.
colors=['#b5dfe8','#a1c79a','#e8ce96','#b5a9d5','#dbadad'];guide='<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 128 72"><rect width="128" height="72" fill="#202832"/><text x="2" y="4" fill="white" font-size="2.6">Native64x32 armor UV guide · preserve each island</text><g transform="translate(2 8)">'
for (part,faces),color in zip(((k,rects(k))for k in PARTS),colors):
 for face,(x,y,w,h)in faces.items():guide+=f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{color}" fill-opacity=".22" stroke="{color}" stroke-width=".12"/><text x="{x+.2}" y="{y+1.6}" fill="{color}" font-size="1.15">{part[0]}:{face[:2]}</text>'
guide+='</g><text x="69" y="12" fill="white" font-size="2.4">Hero kit</text>'
for i,(name,desc)in enumerate([('HEAD','Headband only; face stays open'),('CHEST','Ivory vest / navy cloth / blue inlay'),('LEGS','Waist and leg cloth; inner model0.5'),('FEET','Lower shin / straps / sole; outer1.0'),('UV','Left arm and leg are mirrored natively'),('LIGHT','Albedo only; existing ribbons separate')]):guide+=f'<text x="69" y="{17+i*5}" fill="{colors[i%5]}" font-size="1.9">{html.escape(name+": "+desc)}</text>'
guide+='</svg>'; (MASTER/'native-uv-guide.svg').write_text(guide,encoding='utf-8')
(ROOT/'resourcepack/pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':3,'description':'HBV original Lightning armor hero UV draft · native64x32'}},indent=2)+'\n',encoding='utf-8')
outputs=[]
for name,im in images.items():
 assert im.size==(256,128) and im.mode=='RGBA';hist=im.getchannel('A').histogram();assert hist[0]>0 and hist[255]>0
 outputs.append({'path':(ASSETS/(name+'.png')).relative_to(ROOT).as_posix(),'bytes':(ASSETS/(name+'.png')).stat().st_size,'sha256':hashlib.sha256((ASSETS/(name+'.png')).read_bytes()).hexdigest(),'size':[256,128],'native_resource_id':'valeorders:textures/models/armor/'+name+'.png','opaque_pixels':hist[255],'transparent_pixels':hist[0]})
with zipfile.ZipFile(ROOT/'Lightning-Hero-Worn-UV-Draft-v1.zip','x',zipfile.ZIP_DEFLATED)as z:
 for p in sorted((ROOT/'resourcepack').rglob('*')):
  if p.is_file():
   info=zipfile.ZipInfo(p.relative_to(ROOT/'resourcepack').as_posix(),(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
(ROOT/'hero-manifest.json').write_text(json.dumps({'schema':1,'status':'PRIVATE_AUTHORED_EXACT_UV_DRAFT_NOT_NATIVE_ACCEPTED','authorship':'Original quarter-unit SVG detailing at64x32 logical UVs, fresh256x128 raster export; no input bitmap edited or resized','built_by':'build_hero.py','generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'kit':'Lightning Chakra Armor','issued_items':['storm_helmet','storm_chestplate','storm_leggings','storm_boots'],'consumer':'Gear.WardArmor.getArmorTexture; nativeLayerBipedArmor','outputs':outputs,'emission_mask_wired':False,'existing_LightningLayers_untouched':True,'production_modules_changed':False},indent=2)+'\n',encoding='utf-8')
print('Authored2quarter-unitSVGtextures atnative64x32UVs / fresh256x128raster; newv3details complete')
