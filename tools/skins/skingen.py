"""Original shinobi skin generator: 64x64 Minecraft skins (classic arms) with shading + outer layer."""
import random, sys
from PIL import Image

def C(h): h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))+(255,)
def shade(c,f): return tuple(max(0,min(255,int(v*f))) for v in c[:3])+(c[3],)

class Skin:
    def __init__(s,seed): s.im=Image.new("RGBA",(64,64),(0,0,0,0)); s.r=random.Random(seed)
    def px(s,x,y,c): s.im.putpixel((x,y),c)
    def rect(s,x,y,w,h,c,noise=0.06):
        for yy in range(y,y+h):
            for xx in range(x,x+w):
                f=1+(s.r.random()-0.5)*2*noise
                s.px(xx,yy,shade(c,f))
    def get(s,x,y): return s.im.getpixel((x,y))

# Face rectangles (x,y,w,h) per part. Inner layer then outer layer offsets.
HEAD={'top':(8,0,8,8),'bottom':(16,0,8,8),'right':(0,8,8,8),'front':(8,8,8,8),'left':(16,8,8,8),'back':(24,8,8,8)}
def part(ox,oy,w,h,d):  # generic box UV
    return {'top':(ox+d,oy,w,d),'bottom':(ox+d+w,oy,w,d),'right':(ox,oy+d,d,h),'front':(ox+d,oy+d,w,h),'left':(ox+d+w,oy+d,d,h),'back':(ox+2*d+w,oy+d,w,h)}
BODY=part(16,16,8,12,4); RARM=part(40,16,4,12,4); LARM=part(32,48,4,12,4); RLEG=part(0,16,4,12,4); LLEG=part(16,48,4,12,4)
HAT={k:(x+32,y,w,h) for k,(x,y,w,h) in HEAD.items()}
JACKET=part(16,32,8,12,4); RSLV=part(40,32,4,12,4); LSLV=part(48,48,4,12,4); RPANT=part(0,32,4,12,4); LPANT=part(0,48,4,12,4)

SIDE={'top':1.12,'front':1.0,'right':0.86,'left':0.86,'back':0.78,'bottom':0.6}
def box(s,P,c,noise=0.06):
    for k,(x,y,w,h) in P.items(): s.rect(x,y,w,h,shade(c,SIDE[k]),noise)
def bands(s,P,rows,c,faces=('front','right','left','back')):
    for k in faces:
        x,y,w,h=P[k]
        for r in rows:
            if 0<=r<h: s.rect(x,y+r,w,1,shade(c,SIDE[k]),0.03)
def vgrad(s,P,top,bot,faces=('front','right','left','back')):
    for k in faces:
        x,y,w,h=P[k]
        for r in range(h):
            t=r/max(1,h-1); c=tuple(int(top[i]+(bot[i]-top[i])*t) for i in range(3))+(255,)
            s.rect(x,y+r,w,1,shade(c,SIDE[k]),0.05)

def face(s,skin,hair,eye,iris,brow=None,mark=None,mouth=None):
    x,y,_,_=HEAD['front']
    s.rect(x,y,8,8,skin,0.03)
    for i in range(8): s.px(x+i,y+7,shade(skin,0.88))         # jaw shadow
    s.rect(x,y,8,2,hair,0.08)                                  # hairline
    s.px(x,y+2,hair); s.px(x+7,y+2,hair); s.px(x+3,y+2,shade(hair,0.9))
    for ex in (1,5):                                           # eyes: white, iris, highlight
        s.px(x+ex,y+4,C('#f4f4f4')); s.px(x+ex+1,y+4,iris); s.px(x+ex+1,y+3,shade(iris,0.6))
        s.px(x+ex,y+3,eye)
    s.px(x+2,y+4,shade(iris,1.25)) if False else None
    b=brow or shade(hair,0.7)
    for ex in (1,5): s.px(x+ex,y+2,b); s.px(x+ex+1,y+2,b)
    s.px(x+3,y+5,shade(skin,0.9)); s.px(x+4,y+5,shade(skin,0.9))  # nose shadow
    m=mouth or shade(skin,0.62); s.px(x+3,y+6,m); s.px(x+4,y+6,m)
    if mark: mark(s,x,y)
    for k in ('right','left','back','top','bottom'):
        hx,hy,w,h=HEAD[k]; s.rect(hx,hy,w,h,shade(skin,SIDE[k]),0.03)
    for k in ('right','left','back','top'):
        hx,hy,w,h=HEAD[k]; rows=h if k in('back','top') else 3
        s.rect(hx,hy,w,rows,shade(hair,SIDE[k]),0.09)
        if k in('right','left'): s.rect(hx+(0 if k=='left' else 5),hy+3,3,3,shade(hair,SIDE[k]*0.95),0.09)

def spiky_hat(s,hair,spikes=10,length=3):
    for k in ('top','right','left','back'):
        x,y,w,h=HAT[k]
        for i in range(w):
            depth=s.r.randint(1,length) if k!='top' else h
            for j in range(depth if k!='top' else h):
                if k=='top' or s.r.random()<0.85: s.px(x+i,y+j,shade(hair,SIDE[k]*(1.05-0.06*j)))
    x,y,w,h=HAT['front']
    for i in range(w):
        for j in range(s.r.choice((1,1,2))): s.px(x+i,y+j,shade(hair,1.08-0.1*j))

def headband(s,cloth,plate,symbol):
    x,y,w,h=HAT['front']
    for i in range(8): s.px(x+i,y+2,shade(cloth,1.0))
    for i in range(2,6): s.px(x+i,y+2,plate); s.px(x+i,y+1,shade(plate,1.15)) if s.get(x+i,y+1)[3]==0 else None
    symbol(s,x,y)
    for k in ('right','left','back'):
        hx,hy,_,_=HAT[k]
        for i in range(8): s.px(hx+i,hy+2,shade(cloth,SIDE[k]))
    bx,by,_,_=HAT['back']; s.px(bx+3,by+3,cloth); s.px(bx+4,by+3,cloth); s.px(bx+3,by+4,shade(cloth,.85)); s.px(bx+5,by+4,shade(cloth,.85))

def hands(s,P,skin):
    for k in ('front','right','left','back'):
        x,y,w,h=P[k]; s.rect(x,y+h-2,w,2,shade(skin,SIDE[k]),0.03)
    x,y,w,h=P['bottom']; s.rect(x,y,w,h,shade(skin,0.7),0.03)

def shoes(s,P,c):
    for k in ('front','right','left','back'):
        x,y,w,h=P[k]; s.rect(x,y+h-2,w,2,shade(c,SIDE[k]),0.05)
    x,y,w,h=P['bottom']; s.rect(x,y,w,h,shade(c,0.5),0.03)

def wraps(s,P,c,start,end):
    for k in ('front','right','left','back'):
        x,y,w,h=P[k]
        for r in range(start,min(end,h)):
            for i in range(w): s.px(x+i,y+r,shade(c,SIDE[k]*(0.92 if (i+r)%3==0 else 1.0)))

Skin.save=lambda s,path: s.im.save(path)
