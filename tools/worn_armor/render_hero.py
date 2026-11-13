"""Offline exact native-box UV QA, not a Minecraft screenshot or shader acceptance."""
from pathlib import Path
import math,json,hashlib,sys
import numpy as np
from PIL import Image,ImageDraw,ImageFont
DRAFT=Path(__file__).resolve().parent
R=Path(sys.argv[1]).resolve() if __name__=='__main__' and len(sys.argv)>1 else DRAFT.parents[1]/'artwork/worn-armor-v1/hero'
# Rendering is offline contract QA, separate from native visual acceptance.
T={}
if __name__=='__main__':
 T={i:np.array(Image.open(R/f'resourcepack/assets/valeorders/textures/models/armor/storm_layer_{i}.png').convert('RGBA'))for i in(1,2)}
FACES=[[5,1,2,6],[0,4,7,3],[5,4,0,1],[2,3,7,6],[1,0,3,2],[4,5,6,7]]

def rot(x=0,y=0,z=0):
 cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
 return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def modelbox(pos,size,offset,delta=0,mirror=False):
 x,y,z=pos;dx,dy,dz=size;f=x+dx+delta;f1=y+dy+delta;f2=z+dz+delta;x-=delta;y-=delta;z-=delta
 if mirror:x,f=f,x
 vertices=np.array([[x,y,z],[f,y,z],[f,f1,z],[x,f1,z],[x,y,f2],[f,y,f2],[f,f1,f2],[x,f1,f2]],dtype=float)
 u,v=offset;uvs=[(u+dz+dx,v+dz,u+dz+dx+dz,v+dz+dy),(u,v+dz,u+dz,v+dz+dy),(u+dz,v,u+dz+dx,v+dz),(u+dz+dx,v+dz,u+dz+dx+dx,v),(u+dz,v+dz,u+dz+dx,v+dz+dy),(u+dz+dx+dz,v+dz,u+dz+dx+dz+dx,v+dz+dy)]
 for indices,(u1,v1,u2,v2)in zip(FACES,uvs):
  uv=np.array([[u2,v1],[u1,v1],[u1,v2],[u2,v2]],dtype=float);q=vertices[indices]
  if mirror:q=q[::-1];uv=uv[::-1]
  yield q,uv

BONES={'head':((-4,-8,-4),(8,8,8),(0,0),(0,0,0),False),'body':((-4,0,-2),(8,12,4),(16,16),(0,0,0),False),'right_arm':((-3,-2,-2),(4,12,4),(40,16),(-5,2,0),False),'left_arm':((-1,-2,-2),(4,12,4),(40,16),(5,2,0),True),'right_leg':((-2,0,-2),(4,12,4),(0,16),(-1.9,12,0),False),'left_leg':((-2,0,-2),(4,12,4),(0,16),(1.9,12,0),True)}

BONES['headwear']=((-4,-8,-4),(8,8,8),(32,0),(0,0,0),False)

# Neutral original mannequin colors help expose cutouts; no character skin pixels are copied.
SKIN=np.full((32,64,4),(91,101,111,255),dtype=np.uint8)
SKIN[8:16,8:16]=(187,174,151,255);SKIN[11:12,10:11]=(42,48,55,255);SKIN[11:12,13:14]=(42,48,55,255)
SKIN[20:32,20:28]=(73,82,92,255)

class Renderer:
 def __init__(self,yaw,walk=False):
  self.w=360;self.h=520;self.image=np.zeros((self.h,self.w,4),dtype=np.uint8);self.image[:]=(24,31,41,255);self.depth=np.full((self.h,self.w),np.inf);self.parts=np.full((self.h,self.w),-1);self.view=rot(y=math.radians(yaw));self.walk=walk;self.sample_count=0;self.invalid=0;self.obj=[]
 def draw(self,bone,slot,texture,inflate):
  pos,size,uv,pivot,mirror=BONES[bone];angles={'right_arm':(.4 if self.walk else 0,0,-.12),'left_arm':(-.4 if self.walk else 0,0,.12),'right_leg':(-.35 if self.walk else 0,0,0),'left_leg':(.35 if self.walk else 0,0,0)}.get(bone,(0,0,0));pose=rot(*angles)
  for face,(vertices,coords)in enumerate(modelbox(pos,size,uv,inflate,mirror)):
   world=vertices@pose.T+np.array(pivot);self.obj.append((world.copy(),coords.copy(),slot));v=world@self.view.T
   normal=np.cross(v[1]-v[0],v[2]-v[0]);normal/=np.linalg.norm(normal);light=np.array([-.4,-.7,-.8]);light/=np.linalg.norm(light);shade=.62+.38*max(0,float(np.dot(normal,light)))
   screen=np.column_stack((v[:,0]*12+self.w/2,v[:,1]*12+136,v[:,2]))
   for order in((0,1,2),(0,2,3)):self.triangle(screen[list(order)],coords[list(order)],texture,shade,slot)
 def triangle(self,v,uv,texture,shade,slot):
  x0=max(0,int(math.floor(min(v[:,0]))));x1=min(self.w-1,int(math.ceil(max(v[:,0]))));y0=max(0,int(math.floor(min(v[:,1]))));y1=min(self.h-1,int(math.ceil(max(v[:,1]))))
  if x1<x0 or y1<y0:return
  a,b,c=v;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(den)<1e-9:return
  xx,yy=np.meshgrid(np.arange(x0,x1+1)+.5,np.arange(y0,y1+1)+.5);w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1;inside=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)
  uu=w0*uv[0,0]+w1*uv[1,0]+w2*uv[2,0];vv=w0*uv[0,1]+w1*uv[1,1]+w2*uv[2,1];self.invalid+=int(np.count_nonzero(inside&((uu<-.001)|(uu>64.001)|(vv<-.001)|(vv>32.001))))
  tx=np.clip(np.floor(uu/64*texture.shape[1]).astype(int),0,texture.shape[1]-1);ty=np.clip(np.floor(vv/32*texture.shape[0]).astype(int),0,texture.shape[0]-1);pixels=texture[ty,tx];depth=w0*a[2]+w1*b[2]+w2*c[2];area=self.depth[y0:y1+1,x0:x1+1];visible=inside&(pixels[:,:,3]>0)&(depth<=area+1e-7);area[visible]=depth[visible];dest=self.image[y0:y1+1,x0:x1+1];shaded=pixels.copy();shaded[:,:,:3]=np.clip(pixels[:,:,:3]*shade,0,255).astype(np.uint8);dest[visible]=shaded[visible];self.parts[y0:y1+1,x0:x1+1][visible]=slot;self.sample_count+=int(np.count_nonzero(visible))
 def render(self):
  for bone in BONES:
   if bone!='headwear':self.draw(bone,0,SKIN,0)
  # Actual vanilla LayerBipedArmor slot/model split: leggings.5 first, then outer1.
  for bone in('body','right_leg','left_leg'):self.draw(bone,3,T[2],.5)
  for bone in('body','right_arm','left_arm'):self.draw(bone,2,T[1],1)
  self.draw('head',1,T[1],1);self.draw('headwear',1,T[1],1.5)
  for bone in('right_leg','left_leg'):self.draw(bone,4,T[1],1)
  counts={name:int(np.count_nonzero(self.parts==slot))for slot,name in[(1,'head'),(2,'chest'),(3,'leggings'),(4,'boots')]};assert all(x>0 for x in counts.values()) and self.invalid==0
  return Image.fromarray(self.image,'RGBA'),counts

def main():
 views=[('FRONT',0,False),('THREE-QUARTER',38,False),('BACK',180,False),('WALK UV CHECK',-32,True)];sheet=Image.new('RGB',(1440,650),(24,31,41));draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',17);small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',14);title=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',25)
 draw.text((18,10),'Lightning kit · original worn-armor UV draft',font=title,fill='white');draw.text((18,42),'Offline native-ModelBiped geometry/UV simulation — not a Minecraft screenshot. Existing electric-ribbon aura is separate.',font=small,fill=(181,201,212));proof=[]
 for i,(label,yaw,walk)in enumerate(views):
  renderer=Renderer(yaw,walk);im,counts=renderer.render();sheet.paste(im,(i*360,75));draw.text((i*360+12,592),label,font=font,fill=(131,218,237));proof.append({'view':label,'yaw':yaw,'walk':walk,'visible_pixels_by_slot':counts,'out_of_canvas_uv_samples':renderer.invalid})
  if i==0:
   obj=[];mtl=[];index=1
   for vertices,coords,slot in renderer.obj:
    obj.append('usemtl '+('mannequin'if slot==0 else'layer2'if slot==3 else'layer1'))
    for v in vertices:obj.append('v %.6f %.6f %.6f'%(v[0],-v[1],v[2]))
    for u,v in coords:obj.append('vt %.8f %.8f'%(u/64,1-v/32))
    obj.append('f '+' '.join('%d/%d'%(index+j,index+j)for j in range(4)));index+=4
   (R/'hero-native-contract.obj').write_text('mtllib hero-native-contract.mtl\n'+'\n'.join(obj)+'\n',encoding='utf-8')
   (R/'hero-native-contract.mtl').write_text('newmtl mannequin\nKd 0.35 0.4 0.45\n\nnewmtl layer1\nKd 1 1 1\nmap_Kd resourcepack/assets/valeorders/textures/models/armor/storm_layer_1.png\n\nnewmtl layer2\nKd 1 1 1\nmap_Kd resourcepack/assets/valeorders/textures/models/armor/storm_layer_2.png\n',encoding='utf-8')
 draw.text((18,625),'64×32 logical UV / 256×128 albedo · open-face headband · ivory Cloud-style utility vest · navy cloth · mirrored wraps / straps · no atlas rescaling',font=small,fill=(205,205,192));sheet.save(R/'hero-on-model-qa.png')
 (R/'render-qa.json').write_text(json.dumps({'schema':1,'scope':'OFFLINE_UV_CONTRACT_SOFTWARE_RENDER_NOT_NATIVE','source':'Prepared MCP stable39 ModelBox,ModelBiped,LayerBipedArmor functional coordinates','renderer':'render_hero.py','views':proof,'native_client_acceptance':False,'image_sha256':hashlib.sha256((R/'hero-on-model-qa.png').read_bytes()).hexdigest()},indent=2)+'\n',encoding='utf-8');print(json.dumps(proof,indent=2))

if __name__=="__main__":main()
