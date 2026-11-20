"""Directly cut the six visible biped panels from the private orthographic master."""
import hashlib
from pathlib import Path
from PIL import Image
from outfit import DESIGNS

# Coordinates on a 760-pixel-wide registration proof. Each row gives head,
# shoulder and waist boundaries, then the two outside edges at the boots.
ROWS = (
    (117,183,10,72, 92,122,180,210,68,157,119,182,233),
    (346,414,10,74, 320,349,410,439,69,157,346,413,232),
    (576,642,10,72, 550,580,636,667,69,157,577,641,233),
    (117,183,257,322, 90,122,179,211,316,405,119,182,482),
    (346,414,260,322, 319,348,412,439,318,407,347,413,482),
    (577,643,261,322, 550,582,637,665,318,405,577,642,481),
    (116,183,510,570, 91,122,179,210,569,654,118,183,735),
    (346,414,510,570, 320,350,411,439,569,654,347,412,735),
    (576,643,509,571, 550,580,638,669,569,654,577,642,735),
)


class FrontMaster:
    def __init__(self,path):
        self.image=Image.open(path).convert("RGBA")
        if self.image.size!=(1263,1246):
            raise ValueError("Expected the registered 1263 by 1246 generated master")
        self.sha=hashlib.sha256(Path(path).read_bytes()).hexdigest()

    def panel(self,key,part,size,opposite=False):
        hl,hr,ht,hb,al,cl,cr,ar,st,wa,ll,lr,sole=ROWS[list(DESIGNS).index(key)]
        mid=(ll+lr)/2
        boxes={"head":(hl,ht,hr,hb),"body":(cl,hb,cr,wa),
               "arm":(cr,st,ar,wa) if opposite else (al,st,cl,wa),
               "leg":(mid,wa,lr,sole) if opposite else (ll,wa,mid,sole)}
        bounds=tuple(round(c*self.image.width/760) for c in boxes[part])
        # Interior panels carry their exact original RGB. Transparent background
        # is cut away by the registered bounds, then the biped supplies the edge.
        return self.image.crop(bounds).convert("RGB").resize(size,Image.Resampling.NEAREST).convert("RGBA")
