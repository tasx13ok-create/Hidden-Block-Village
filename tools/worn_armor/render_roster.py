"""Render every authored entry using actual ModelBiped bone UV contracts; never native acceptance."""
from pathlib import Path
import json,hashlib
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from render_hero import Renderer,BONES,SKIN
ROOT=Path(__file__).resolve().parent
R=Path(__import__('sys').argv[1]).resolve();M=json.loads((R/'manifest.json').read_text(encoding='utf-8'));OUT=R/'renders';OUT.mkdir(exist_ok=False)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18);small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',13)
views=[('FRONT',0,False),('3/4',38,False),('BACK',180,False),('WALK',-32,True)]
checks=[];thumbs=[]
for index,e in enumerate(M['entries']):
 textures=[np.array(Image.open(R/p).convert('RGBA'))for p in e['layers']]
 sheet=Image.new('RGB',(1440,600),(24,31,41));d=ImageDraw.Draw(sheet);d.text((15,8),e['title']+' | '+e['key'],font=font,fill='white');records=[]
 for i,(label,yaw,walk)in enumerate(views):
  rr=Renderer(yaw,walk)
  for bone in BONES:
   if bone!='headwear':rr.draw(bone,0,SKIN,0)
  if e['category']=='ward_set':
   for bone in('body','right_leg','left_leg'):rr.draw(bone,3,textures[1],.5)
   for bone in('body','right_arm','left_arm'):rr.draw(bone,2,textures[0],1)
   rr.draw('head',1,textures[0],1);rr.draw('headwear',1,textures[0],1.5)
   for bone in('right_leg','left_leg'):rr.draw(bone,4,textures[0],1)
   expected=[1,2,3,4]
  else:
   slot=e['slot'];sid={'HEAD':1,'CHEST':2,'LEGS':3,'FEET':4}[slot];bones={'HEAD':['head'],'CHEST':['body','right_arm','left_arm'],'LEGS':['body','right_leg','left_leg'],'FEET':['right_leg','left_leg']}[slot]
   for bone in bones:rr.draw(bone,sid,textures[0],.5 if slot=='LEGS'else 1)
   if slot=='HEAD':rr.draw('headwear',sid,textures[0],1.5)
   expected=[sid]
  counts={str(n):int(np.count_nonzero(rr.parts==n))for n in expected}
  assert rr.invalid==0 and all(n>0 for n in counts.values()),(e['key'],label,counts,rr.invalid)
  im=Image.fromarray(rr.image);sheet.paste(im,(i*360,40));d.text((i*360+18,563),label,font=small,fill=(144,218,231));records.append({'view':label,'visible_pixels':counts,'invalid_uv_samples':rr.invalid})
 path=OUT/(e['key']+'.png');sheet.save(path);checks.append({'key':e['key'],'category':e['category'],'views':records,'image_sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
 # Small front/back row previews only; full images remain alongside.
 thumb=Image.new('RGB',(288,160),(24,31,41));thumb.paste(sheet.crop((0,40,360,560)).resize((90,130)),(15,5));thumb.paste(sheet.crop((720,40,1080,560)).resize((90,130)),(120,5));ImageDraw.Draw(thumb).text((4,140),e['key'][:37],font=small,fill='white');thumbs.append(thumb)
 if (index+1)%25==0:print('Rendered',index+1,flush=True)
boards=[]
for n in range(0,len(thumbs),30):
 subset=thumbs[n:n+30];board=Image.new('RGB',(1440,80+160*((len(subset)+4)//5)),(24,31,41));bd=ImageDraw.Draw(board);bd.text((15,10),'Original worn UV drafts | entry '+str(n+1)+'–'+str(n+len(subset)),font=font,fill='white');bd.text((15,38),'Offline native geometry simulation. Front/back thumbnails; full four-view entry sheets alongside. Not native client acceptance.',font=small,fill=(180,201,212))
 for i,thumb in enumerate(subset):board.paste(thumb,((i%5)*288,80+(i//5)*160))
 path=R/('coverage-sheet-%02d.png'%(n//30+1));board.save(path);boards.append(str(path.relative_to(R)))
(R/'render-qa.json').write_text(json.dumps({'scope':'OFFLINE_NATIVE_GEOMETRY_UV_SOFTWARE_RENDER_NOT_MINECRAFT','entries':checks,'entry_count':len(checks),'views':len(checks)*4,'logical_uv':[64,32],'actual_png':[256,128],'preview_boards':boards,'native_visual_acceptance':False},indent=2)+'\n',encoding='utf-8')
print('PASS',len(checks),'entries',len(checks)*4,'views',len(boards),'boards',flush=True)
