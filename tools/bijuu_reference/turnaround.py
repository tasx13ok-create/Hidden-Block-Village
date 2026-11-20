"""Register the side and rear drawings and join them to the approved fronts."""
from PIL import Image, ImageOps
from outfit import DESIGNS, uv_rects

# Side head/arm/leg x spans, rear head/torso/outer sleeve/legs, and vertical
# boundaries on the 900-pixel registration proof, in roster order.
REGISTRATION=(
 ((50,118,65,109,65,102),(181,246,186,240,157,271,184,244),(27,87,180,264)),
 ((351,415,364,402,365,401),(478,542,484,536,456,568,479,538),(27,88,180,264)),
 ((645,710,657,697,657,694),(772,839,777,832,749,861,775,836),(28,88,179,264)),
 ((50,118,66,107,65,104),(181,246,185,240,155,271,183,246),(308,370,463,550)),
 ((351,414,361,403,362,399),(477,542,483,536,455,567,478,540),(307,370,464,550)),
 ((644,711,660,697,659,697),(772,838,778,832,749,861,775,837),(306,370,463,550)),
 ((50,118,63,107,63,103),(179,247,185,241,155,271,183,245),(590,652,747,841)),
 ((350,416,359,403,360,399),(476,544,482,536,451,569,477,540),(590,652,747,841)),
 ((644,712,657,700,658,698),(772,840,778,833,746,863,775,838),(590,652,748,841)),
)
N=Image.Resampling.NEAREST


def stitch(side,left,right):
    # Carry several columns of each neighboring face around the corner. The
    # exact outside column also survives the native atlas's fourfold reduction.
    w,h=side.size
    margin=max(4,w//8)
    for i in range(margin):
        weight=(1-i/margin)**2
        for col,edge,step in ((i,left,-1),(w-1-i,right,1)):
            x=(edge.width-1 if step<0 else 0)+step*i
            a=edge.crop((x,0,x+1,h))
            b=side.crop((col,0,col+1,h))
            side.paste(Image.blend(b,a,weight),(col,0))
    return side


class Turnaround:
    def __init__(self,path):
        self.image=Image.open(path).convert("RGB")
        if self.image.size!=(1263,1246):
            raise ValueError("Unexpected reference dimensions")

    def faces(self,key,part,front,opposite=False):
        sx,bx,ys=REGISTRATION[list(DESIGNS).index(key)]
        hl,hr,al,ar,ll,lr=sx
        bl,br,cl,cr,ol,orr,pl,pr=bx
        crown,neck,waist,sole=ys
        # The rear view shows the wearer's right arm on the right of the page.
        back_x={"head":(bl,br),"body":(cl,cr),"arm":(ol,cl) if opposite else (cr,orr),
                "leg":(pl,(pl+pr)/2) if opposite else ((pl+pr)/2,pr)}[part]
        side_x={"head":(hl,hr),"arm":(al,ar),"leg":(ll,lr),
                "body":(cl,cl+(cr-cl)*.23)}[part]
        top,bottom={"head":(crown,neck),"body":(neck,waist),"arm":(neck,waist),"leg":(waist,sole)}[part]
        def tile(span,size):
            rect=(span[0],top,span[1],bottom)
            return self.image.crop(tuple(round(v*self.image.width/900) for v in rect)).resize(size,N).convert("RGBA")
        back=tile(back_x,front.size)
        width=uv_rects(part)["east"][2]*64
        profile=tile(side_x,(width,front.height))
        west=stitch(ImageOps.mirror(profile),back,front)
        east=stitch(profile.copy(),front,back)
        return {"back":back,"west":west,"east":east}


def place(layers,part,faces):
    for side,tile in faces.items():
        x,y,w,h=uv_rects(part)[side]
        x,y,h=x*64,y*64,h*64
        cut,first,second={"body":(9*64,0,1),"leg":(7*64,1,0)}.get(part,(h,0,0))
        layers[first].paste(tile.crop((0,0,tile.width,cut)),(x,y))
        if cut<h:
            layers[second].paste(tile.crop((0,cut,tile.width,h)),(x,y+cut))
