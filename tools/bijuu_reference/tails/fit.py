"""Fit V3 sockets and tint existing outfit pixels from the matching V3 artwork."""
import numpy as np
from PIL import Image

# Reference colors identify the beast's primary surface amongst markings and tips.
SURFACE = {
    "shukaku": (191,154,94), "matatabi": (41,135,238), "isobu": (76,114,123),
    "son_goku": (163,46,41), "kokuo": (231,231,217), "saiken": (216,235,235),
    "chomei": (135,161,66), "gyuki": (163,110,124), "kurama": (215,113,35),
}


def fit_sockets(model):
    nodes = {n["id"]: n for n in model["nodes"]}
    for node in nodes.values():
        if node["parent"] == "root":
            x,y,z = node["pivot"]
            node["pivot"] = [max(-1.85,min(1.85,x)),max(-.85,min(.85,y)),min(.45,z)]
    for chain in model["appendages"]:
        for parent,child in zip(chain["nodes"],chain["nodes"][1:]):
            pivot=nodes[child]["pivot"]
            boxes=nodes[parent]["boxes"]
            central=[box for box in boxes if all(box["from"][i] <= pivot[i] <= box["from"][i]+box["size"][i] for i in (0,1))]
            if not central:
                raise ValueError("No material at joint "+child)
            end=max(box["from"][2]+box["size"][2] for box in central)
            pivot[2]=min(pivot[2],end-.25)


def tint(image, tail, beast):
    # Use the closest actual tail pigment for the midtone. Retain each area’s
    # light-to-dark structure and deep markings, including the clear UV gutter.
    target=np.array(SURFACE[beast], dtype=float)
    pixels=np.asarray(tail.convert("RGBA")).reshape(-1,4)
    colors=np.unique(pixels[pixels[:,3]>0,:3],axis=0).astype(float)
    mid=colors[np.argmin(((colors-target)**2).sum(axis=1))]
    rgba=image.convert("RGBA")
    bounds=rgba.getbbox()
    if bounds is None:
        return rgba
    crop=np.array(rgba.crop(bounds))
    weights=np.array([.2126,.7152,.0722],dtype=np.float32)
    for start in range(0,len(crop),96):
        stripe=crop[start:start+96]
        rgb=stripe[:,:,:3].astype(np.float32)
        # Peak brightness separates saturated cloth from the dark line work.
        level=.55*rgb.max(axis=2)+.45*(rgb@weights)
        stops=[0,30,69,120,170,220,255]
        ramp=np.array([[8,9,14],mid*.18,mid*.47,mid*.82,mid,mid*.55+255*.45,mid*.22+255*.78])
        if beast=="chomei":
            ramp[2]=[31,70,82]
        for c in range(3):
            dye=np.interp(level,stops,ramp[:,c])
            stripe[:,:,c]=np.where(stripe[:,:,3]>0,np.rint(.94*dye+.06*rgb[:,:,c]),rgb[:,:,c])
    rgba.paste(Image.fromarray(crop),bounds)
    return rgba
