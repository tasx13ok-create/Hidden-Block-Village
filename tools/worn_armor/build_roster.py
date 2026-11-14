"""Original nine-nature shinobi garment families, rendered from exact native UV vector sources."""
from pathlib import Path
import re,json,hashlib,collections,sys
from uv_art import Art,rects
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parents[1];OUT=Path(__import__('sys').argv[1]).resolve();OUT.mkdir(parents=True,exist_ok=False)
# Primary, dark cloth, trim, pale wrap, nature inlay; deliberately distinct material languages.
PALETTES={
 'ember':('#aa5035','#252a30','#d88d55','#d3baa0','#f5bd55'),
 'current':('#447b85','#162b3d','#79a9ac','#b9d4cb','#77dbdb'),
 'gale':('#81916b','#253f42','#b3c197','#d7d6b5','#c3edce'),
 'flint':('#a58b68','#403b36','#cfbb92','#e1d3b6','#e4b975'),
 'bloom':('#57836a','#243a31','#9eaf7d','#cfd8b6','#b9e286'),
 'rime':('#b3d0d3','#253446','#6f94ad','#e5e7d8','#a6e7ee'),
 'storm':('#756a9d','#252f47','#a2aacd','#d2d5dc','#a1d8ed'),
 'dusk':('#4d4c6c','#1d2533','#858098','#b2bbc5','#ad98d1'),
 'dawn':('#d5bd78','#35434b','#f0db9c','#e4ddc5','#fae7b7')}
# Original compact elemental clan stitchmarks. They are not copied reference logos.
MOTIFS={
 'ember':[[(0,5),(1,3),(1,1),(3,3),(4,0),(5,3),(4,5),(2,5),(2,3)]],
 'current':[[(0,1),(1,0),(2,0),(3,1),(4,1),(5,0)],[(0,3),(1,2),(2,2),(3,3),(4,3),(5,2)],[(0,5),(1,4),(2,4),(3,5),(4,5)]],
 'gale':[[(0,1),(4,1),(5,0)],[(0,3),(3,3),(4,2),(3,2)],[(0,5),(4,5),(5,4)]],
 'flint':[[(0,0),(5,0),(5,5),(0,5),(0,0)],[(0,3),(5,3)],[(2,0),(2,3)],[(3,3),(3,5)]],
 'bloom':[[(0,5),(2,3),(2,1),(4,0),(5,1),(4,3),(2,3)],[(2,3),(4,4)],[(1,2),(0,1)]],
 'rime':[[(2.5,0),(5,2.5),(2.5,5),(0,2.5),(2.5,0)],[(0,2.5),(5,2.5)],[(2.5,0),(2.5,5)]],
 'storm':[[(4,0),(1,2.5),(3,2.5),(1,5),(5,2),(3,2),(4,0)]],
 'dusk':[[(5,0),(2,0),(0,2),(0,4),(2,5),(5,5),(3,4),(2,2),(3,1),(5,0)]],
 'dawn':[[(2.5,0),(3,2),(5,2.5),(3,3),(2.5,5),(2,3),(0,2.5),(2,2),(2.5,0)]]}

def tint(hex,offset):
 c=[int(hex[i:i+2],16)for i in(1,3,5)];return '#'+''.join('%02x'%max(0,min(255,v+offset))for v in c)
def palette(ward,key):
 base,cloth,trim,wrap,glow=PALETTES[ward];seed=int(hashlib.sha256(key.encode()).hexdigest()[:8],16)
 # Family-level material variation; no salted pixels/watermarks used to fake unique artwork.
 return tuple(tint(c,(-6+(seed%5)*3)if i in(0,1)else 0)for i,c in enumerate((base,cloth,trim,wrap,glow)))
def mark(a,ward,x,y,w,h,color,stroke=.25):
 for line in MOTIFS[ward]:a.line([(x+px*w/5,y+py*h/5)for px,py in line],color,stroke)
def stitches(a,x,y,w,h,c):
 for xx in [x+.5+i*.75 for i in range(max(0,int((w-1)/.75)))]:a.rect(xx,y+h-.5,.25,.25,c)
def head(a,ward,p,kind,cut):
 base,cloth,trim,wrap,glow=p
 for face,(x,y,w,h)in rects('head').items():
  if kind=='hood':
   if face!='front':a.rect(x,y,w,h,cloth);stitches(a,x,y,w,h,trim)
   else:
    a.rect(x,y,w,1.5,cloth);a.rect(x,y+1.5,1,5,cloth);a.rect(x+w-1,y+1.5,1,5,cloth);a.rect(x,y+h-1,w,1,base)
  elif face not in('top','bottom'):
   a.rect(x,y+(.5 if kind=='band'else 2),w,2 if kind=='band'else 2.5,cloth)
 if kind in('band','hood'):
  y=8.5 if kind=='band'else 8.25;a.rect(9,y,6,1.5,trim);mark(a,ward,10.5,y+.25,3,1,cloth,.25)
 else:
  # Actual lens/head focus: fitted frame with two lens panes or a monocular watch optic.
  a.rect(9,10,6,2.5,trim)
  if cut%3==0:a.rect(9.5,10.5,1.75,1.5,glow);a.rect(12.75,10.5,1.75,1.5,glow);a.rect(11.5,10.75,1,1,cloth)
  elif cut%3==1:a.rect(12,10.25,2.5,2,glow);a.rect(12.5,10.75,1.5,1,cloth);a.rect(13,11,0.5,.5,glow)
  else:a.rect(9.5,10.5,5,1.5,glow);a.line([(10,11.5),(11,10.75),(12,11.5),(13,10.75),(14,11.5)],cloth,.25)
def chest(a,ward,p,kind,cut):
 base,cloth,trim,wrap,glow=p
 for face,(x,y,w,h)in rects('body').items():
  a.rect(x,y,w,h,cloth if kind=='coat'or face in('east','west','bottom')else base)
  if face in('front','back'):
   a.rect(x,y+10,w,2,cloth);a.rect(x,y+10,w,.5,trim)
   if face=='front':
    a.line([(x+1.75,y),(x+w/2,y+2),(x+w-1.75,y)],cloth if kind!='coat'else trim,.75)
    if cut%4==0:
     for px in(x+1,x+w-3):
      a.rect(px,y+4,2,3,base);a.rect(px,y+4,2,.5,trim);a.rect(px+.75,y+4.75,.5,.25,cloth);stitches(a,px,y+4,2,3,wrap)
     a.rect(x+3.5,y+3,.5,6,trim)
    elif cut%4==1:
     a.line([(x+.5,y+1.5),(x+w-1,y+8.5)],trim,1);a.line([(x+1,y+1.5),(x+w-.5,y+8.5)],glow,.25)
     a.rect(x+1,y+6,2,3,base);stitches(a,x+1,y+6,2,3,wrap)
    elif cut%4==2:
     for yy in(y+3,y+5,y+7):a.rect(x+.5,yy,w-1,1.5,base);a.line([(x+.5,yy),(x+w-.5,yy)],trim,.25)
     a.rect(x+3.5,y+2,.5,7,glow)
    else:
     a.rect(x+1,y+1,1,8,base);a.rect(x+w-2,y+1,1,8,base);a.line([(x+2,y+1),(x+3.5,y+4),(x+4.5,y+4),(x+w-2,y+1)],trim,.5)
     a.rect(x+3.5,y+5,1,4,trim);a.rect(x+3.75,y+5,.5,4,cloth)
    a.rect(x+3,y+10,2,1,trim);a.rect(x+3.5,y+10.25,1,.5,cloth)
   else:
    a.rect(x+1.25,y+2,5.5,5.5,cloth if kind!='coat'else base);mark(a,ward,x+1.75,y+2.5,4.5,4.5,glow,.25)
    a.line([(x+1,y+8.5),(x+w-1,y+8.5)],trim,.5)
   stitches(a,x,y,w,h,wrap)
  elif face in('east','west'):
   a.line([(x+.5,y+1),(x+.5,y+9)],trim,.5)
 for face,(x,y,w,h)in rects('arm').items():
  a.rect(x,y,w,h,cloth)
  if face in('front','back','east','west'):
   a.rect(x,y,w,2.5,base);a.rect(x,y+2.5,w,.5,trim)
   if cut%2==0:
    a.rect(x,y+7,w,3,wrap)
    for yy in(7.25,8.25,9.25):a.line([(x+.25,y+yy),(x+w-.25,y+yy+.5)],trim,.25)
   else:
    a.rect(x+.5,y+5,w-1,4,base);a.line([(x+.75,y+5.25),(x+w-.75,y+8.5)],glow,.25)
   a.rect(x+1,y+10,2,.5,glow)
def legs(a,ward,p,cut):
 base,cloth,trim,wrap,glow=p
 for face,(x,y,w,h)in rects('body').items():
  if face in('front','back','east','west'):a.rect(x,y+9,w,3,cloth);a.rect(x,y+9,w,.5,trim)
 for face,(x,y,w,h)in rects('leg').items():
  a.rect(x,y,w,h,cloth)
  if face in('front','back','east','west'):
   a.line([(x+.5,y+.5),(x+.5,y+h-.5)],trim,.25)
   if cut%2==0:a.rect(x,y+5,w,2,wrap);a.line([(x+.25,y+5.25),(x+w-.25,y+6.5)],trim,.25)
   else:a.rect(x+.5,y+4,w-1,3,base);a.rect(x+1,y+4.5,2,.5,glow)
   if face in('front','back'):mark(a,ward,x+1,y+1.25,2,2,trim,.25)
def boots(a,ward,p,cut):
 base,cloth,trim,wrap,glow=p
 for face,(x,y,w,h)in rects('leg').items():
  if face in('front','back','east','west'):
   a.rect(x,y+6,w,3.5,cloth);a.rect(x,y+6,w,1,wrap)
   a.line([(x+.25,y+6.25),(x+w-.25,y+6.75)],trim,.25)
   a.rect(x+.5,y+8,w-1,1.5,base);a.rect(x+1,y+8.5,2,.25,glow)
   a.rect(x,y+11,w,1,cloth);a.rect(x,y+10,1,1,cloth);a.rect(x+w-1,y+10,1,1,cloth)
  elif face=='bottom':a.rect(x,y,w,h,cloth)

focus=[];outfits=[]
for match in re.finditer(r'new Spec\("([a-z_]+)","([^"]+)",EntityEquipmentSlot\.([A-Z]+),"([a-z_]+)",Order\.([A-Z]+)\)',(REPO/'modules/vale-orders/src/main/java/village/vale/OriginalFocusGear.java').read_text(encoding='utf-8')):focus.append(dict(key=match[1],title=match[2],slot=match[3],role=match[4],ward=match[5].lower(),category='focus'))
for match in re.finditer(r'new Spec\("([a-z_]+)","([^"]+)",EntityEquipmentSlot\.([A-Z]+),Order\.([A-Z]+)\)',(REPO/'modules/vale-orders/src/main/java/village/vale/OriginalOutfits.java').read_text(encoding='utf-8')):outfits.append(dict(key=match[1],title=match[2],slot=match[3],role='garment',ward=match[4].lower(),category='outfits'))
assert len(focus)==76 and len(outfits)==79
entries=[]
for ward in PALETTES:
 if ward=='storm':continue
 p=palette(ward,ward);cut=list(PALETTES).index(ward);one=Art('Original '+ward+' native armor family; logical64x32, freshly authored256x128');two=Art('Original '+ward+' leggings nativeUV')
 head(one,ward,p,'band',cut);chest(one,ward,p,'vest',cut);boots(one,ward,p,cut);legs(two,ward,p,cut)
 for layer,art in((1,one),(2,two)):
  path=OUT/f'assets/valeorders/textures/models/armor/{ward}_layer_{layer}.png';art.save(OUT/f'masters/wards/{ward}_layer_{layer}.svg',path)
 entries.append({'key':ward,'category':'ward_set','ward':ward,'title':ward.title()+' nature family','pieces':4,'layers':[f'assets/valeorders/textures/models/armor/{ward}_layer_1.png',f'assets/valeorders/textures/models/armor/{ward}_layer_2.png'],'status':'AUTHORED_UV_DRAFT_PENDING_RENDER_AND_NATIVE_REVIEW','cut':cut%4})
for spec in focus+outfits:
 key=spec['key'];ward=spec['ward'];p=palette(ward,key.split('_')[1]);cut=int(hashlib.sha256((key+'|'+spec['role']).encode()).hexdigest()[:4],16)%4;a=Art('Original '+key+' slot-specific native'+spec['slot']+'UV; no icon/reference texture copied');slot=spec['slot']
 if slot=='HEAD':head(a,ward,p,'lens'if spec['category']=='focus'else'hood',cut)
 elif slot=='CHEST':chest(a,ward,p,'mantle'if spec['category']=='focus'else'coat',cut)
 elif slot=='LEGS':legs(a,ward,p,cut)
 elif slot=='FEET':boots(a,ward,p,cut)
 else:raise AssertionError(slot)
 layer=2 if slot=='LEGS'else 1;path=f'assets/valeorders/textures/models/armor/{spec["category"]}/{key}_layer_{layer}.png';a.save(OUT/f'masters/{spec["category"]}/{key}.svg',OUT/path);entries.append({**spec,'cut':cut,'pieces':1,'layers':[path],'status':'AUTHORED_UV_DRAFT_PENDING_RENDER_AND_NATIVE_REVIEW'})
for e in entries:
 e['files']=[{'path':x,'sha256':hashlib.sha256((OUT/x).read_bytes()).hexdigest()}for x in e['layers']]
summary={'ward_sets':8,'ward_equipment_pieces':32,'focus_pieces':76,'outfit_pieces':79,'assets':len(list(OUT.rglob('*.png'))),'distinct_png_bytes':len({x['sha256']for e in entries for x in e['files']}),'clauses':'Native UV authoring draft; family reuse/duplicates explicit, not a claim of unique mesh/canon or native acceptance.'}
(OUT/'manifest.json').write_text(json.dumps({'schema':1,'summary':summary,'entries':entries,'production_modified':False,'references_copied':False},indent=2)+'\n',encoding='utf-8');print(json.dumps(summary))
