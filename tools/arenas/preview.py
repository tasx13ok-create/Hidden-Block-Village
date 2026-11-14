"""Offline block projections. These are not Minecraft/client/shader screenshots."""
from pathlib import Path
import math


COLORS={0:(24,30,42),1:(114,119,123),2:(91,130,65),3:(114,86,62),4:(109,115,119),
        5:(158,124,77),7:(62,63,67),9:(42,107,153),11:(245,104,26),12:(214,197,136),
        17:(94,68,44),18:(60,113,59),20:(161,206,207),24:(210,188,136),
        35:(194,195,190),43:(144,144,141),44:(144,144,141),47:(135,95,53),
        49:(48,38,61),50:(255,210,90),53:(148,111,64),58:(155,111,56),
        67:(117,120,120),85:(128,89,55),87:(101,47,45),89:(232,203,110),
        95:(121,169,179),98:(125,134,135),101:(130,144,149),102:(144,193,199),
        109:(125,134,135),112:(66,37,45),113:(66,37,45),114:(66,37,45),
        126:(151,118,72),128:(202,184,138),133:(52,180,112),139:(114,118,113),
        152:(195,49,46),155:(222,218,209),156:(222,218,209),159:(164,119,85),
        168:(92,152,143),169:(196,233,211),170:(184,160,64),172:(163,102,69),
        173:(41,44,48),179:(175,95,49),201:(159,120,164),215:(95,35,38),
        216:(210,205,184),251:(158,158,152)}
DYES=[(211,211,204),(202,116,55),(155,85,165),(85,139,175),(212,184,62),(108,151,52),
      (185,107,133),(63,69,73),(136,143,145),(46,120,127),(102,65,153),(53,67,118),
      (107,78,53),(70,105,47),(157,56,47),(40,43,47)]


def make_previews(volume,record,directory):
    import numpy as np
    from PIL import Image,ImageDraw
    from .volume import sha256
    directory=Path(directory)
    w,h,l=volume.dimensions
    a=np.ndarray((h,l,w),dtype=np.uint8,buffer=volume.blocks)
    d=np.ndarray((h,l,w),dtype=np.uint8,buffer=volume.data)
    palette=np.array([COLORS.get(i,(146,141,133)) for i in range(256)],dtype=np.uint8)
    files=[]
    def color(ids,meta):
        c=palette[ids].copy()
        dyed=np.isin(ids,[35,95,159,160,171,251])
        c[dyed]=np.array(DYES,dtype=np.uint8)[meta[dyed]&15]
        return c
    def save(name,img,subtitle):
        canvas=Image.new('RGB',(img.width,img.height+82),(17,24,34))
        canvas.paste(img,(0,82))
        draw=ImageDraw.Draw(canvas)
        draw.text((16,12),record['title'],fill='white')
        draw.text((16,32),subtitle,fill=(178,210,220))
        draw.text((16,52),'OFFLINE BLOCK PROJECTION - native physics, lighting and client appearance unaccepted',fill=(244,190,113))
        path=directory/(name+'.png'); canvas.save(path)
        files.append({'path':path.name,'sha256':sha256(path),'scope':'OFFLINE_GENERATED_BLOCK_PROJECTION'})
    mask=a!=0
    heights=h-1-np.argmax(mask[::-1],axis=0)
    z,x=np.indices((l,w))
    top=color(a[heights,z,x],d[heights,z,x])
    shade=(0.7+0.3*heights/(h-1))[:,:,None]
    top=(top*shade).astype(np.uint8)
    img=Image.fromarray(top).resize((1000,1000),Image.Resampling.NEAREST)
    draw=ImageDraw.Draw(img)
    for i,(name,points) in enumerate(record['geometry'].get('routes',{}).items()):
        if not points: continue
        xy=[(p[0]*2,p[2]*2) for p in points]
        draw.line(xy,fill=['#ffe489','#75e5ff','#f8a2d7','#b9f290'][i%4],width=3)
        draw.text(xy[len(xy)//2],name,fill='white',stroke_width=1,stroke_fill='black')
    for name,p in record['anchors'].items():
        px,pz=p[0]*2,p[2]*2
        draw.ellipse((px-5,pz-5,px+5,pz+5),fill='#ff705a',outline='white')
        draw.text((px+7,pz),name,fill='white',stroke_width=1,stroke_fill='black')
    save('route-map',img,'500 x 500 plot / north at top / routes projected from standing-cell paths')
    save('top-down',Image.fromarray(top).resize((1000,1000),Image.Resampling.NEAREST),'Topmost occupied block at each column')
    for name,axis,index in [('interior-section',0,record['anchors']['prep'][2]),
                             ('boss-floor-section',0,record['anchors']['boss'][2]),
                             ('foundation-section',1,250)]:
        if axis==0:
            ids,meta=a[:,index,:],d[:,index,:]
        else:
            ids,meta=a[:,:,index],d[:,:,index]
        im=Image.fromarray(color(ids,meta)[::-1]).resize((1000,500),Image.Resampling.NEAREST)
        save(name,im,'Measured block section; air exposes interiors and supporting construction')
    # Orthographic elevated projections of actual top surfaces, sampled every 3
    # blocks for bounded preview cost. Silhouette heights come from full columns.
    for name,turn in [('southwest',0),('southeast',1),('northeast',2)]:
        hh=np.rot90(heights,turn)
        cc=np.rot90(top,turn)
        canvas=Image.new('RGB',(1100,790),(27,37,50))
        dr=ImageDraw.Draw(canvas)
        scale=1.05; rise=1.7; step=3
        for zz in range(0,l,step):
            for xx in range(0,w,step):
                yy=int(hh[zz,xx]); rgb=tuple(int(v) for v in cc[zz,xx])
                px=550+(xx-zz)*scale
                py=210+(xx+zz)*scale*.48-yy*rise
                q=step*scale
                # Side faces down to the lower sampled neighbor (not arbitrary
                # invented walls or a filled bounding box).
                neighbor=int(hh[min(zz+step,l-1),xx])
                if yy>neighbor:
                    drop=(yy-neighbor)*rise
                    dr.polygon([(px-q,py+q*.48),(px,py+q*.96),(px,py+q*.96+drop),(px-q,py+q*.48+drop)],fill=tuple(int(v*.65) for v in rgb))
                neighbor=int(hh[zz,min(xx+step,w-1)])
                if yy>neighbor:
                    drop=(yy-neighbor)*rise
                    dr.polygon([(px+q,py+q*.48),(px,py+q*.96),(px,py+q*.96+drop),(px+q,py+q*.48+drop)],fill=tuple(int(v*.8) for v in rgb))
                dr.polygon([(px,py),(px+q,py+q*.48),(px,py+q*.96),(px-q,py+q*.48)],fill=rgb)
        save('silhouette-'+name,canvas,'Elevated '+name+' / sampled actual geometry / neutral unlit block colors')
    del a,d
    return files
