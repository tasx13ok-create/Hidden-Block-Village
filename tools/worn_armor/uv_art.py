from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image,ImageDraw

PARTS={'head':(0,0,8,8,8),'headwear':(32,0,8,8,8),'body':(16,16,8,12,4),'arm':(40,16,4,12,4),'leg':(0,16,4,12,4)}
def rects(part):
 u,v,w,h,d=PARTS[part]
 return {'east':(u+d+w,v+d,d,h),'west':(u,v+d,d,h),'top':(u+d,v,w,d),'bottom':(u+d+w,v,w,d),'front':(u+d,v+d,w,h),'back':(u+d+w+d,v+d,w,h)}

class Art:
 def __init__(self,metadata='Original native-UV garment vector source'):self.shapes=[];self.lines=[];self.metadata=metadata
 def rect(self,x,y,w,h,color):
  if w<=0 or h<=0:return
  assert x>=0 and y>=0 and x+w<=64 and y+h<=32,(x,y,w,h)
  self.shapes.append((x,y,w,h,color))
 def line(self,points,color,width=.25):
  assert all(0<=x<=64 and 0<=y<=32 for x,y in points)
  self.lines.append((points,color,width))
 def svg(self):
  import html
  s='<svg xmlns="http://www.w3.org/2000/svg" width="256" height="128" viewBox="0 0 64 32" shape-rendering="crispEdges"><metadata>'+html.escape(self.metadata)+'</metadata>\n'
  for x,y,w,h,c in self.shapes:s+=f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{c}"/>\n'
  for ps,c,w in self.lines:s+='<polyline points="'+' '.join(str(x)+','+str(y)for x,y in ps)+'" fill="none" stroke="'+c+'" stroke-width="'+str(w)+'"/>\n'
  return s+'</svg>\n'
 def save(self,svg,png):
  svg=Path(svg);png=Path(png);svg.parent.mkdir(parents=True,exist_ok=True);png.parent.mkdir(parents=True,exist_ok=True);text=self.svg();svg.write_text(text,encoding='utf-8')
  im=Image.new('RGBA',(256,128));d=ImageDraw.Draw(im)
  for n in ET.fromstring(text):
   a=n.attrib
   if n.tag.endswith('rect'):
    x=round(float(a['x'])*4);y=round(float(a['y'])*4);w=round(float(a['width'])*4);h=round(float(a['height'])*4);d.rectangle((x,y,x+w-1,y+h-1),fill=a['fill'])
   elif n.tag.endswith('polyline'):
    ps=[tuple(round(float(v)*4)for v in p.split(','))for p in a['points'].split()];d.line(ps,fill=a['stroke'],width=max(1,round(float(a['stroke-width'])*4)))
  im.save(png);return im
