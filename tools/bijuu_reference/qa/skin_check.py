"""Count conspicuous test-skin colors around the equipped player in BSL frames."""
from pathlib import Path
from collections import Counter
import argparse
import cv2
import numpy as np

def marked(frame,green=False):
    # Crop the player and all swinging limbs, excluding sky and distant grass.
    h,w=frame.shape[:2]
    area=frame[round(h*.35):round(h*.82),round(w*.354):round(w*.651)]
    hue,saturation,value=cv2.split(cv2.cvtColor(area,cv2.COLOR_BGR2HSV))
    if green:
        return (hue>=47)&(hue<=72)&(saturation>=160)&(value>=110)
    return (hue>=145)&(hue<=166)&(saturation>=115)&(value>=100)

def check(shots, neutral=None):
    totals=Counter()
    frames=Counter()
    persistent=Counter()
    control=cv2.imread(str(shots/"skin-control.png"))
    # The open control may be lit by sunset before the fixed daylight preset.
    b,g,r=cv2.split(control.astype(np.int16))
    positive=int(((r>1.6*g+25)&(b>1.3*g+18)&(r>.9*b)).sum())
    assert positive>1000,positive
    for path in sorted(shots.glob("*.png")):
        if path.stem=="skin-control":
            continue
        key=path.stem.split("-")[1]
        im=cv2.imread(str(path))
        fluorescent=marked(im,key in ("matatabi","gyuki"))
        totals[key]+=int(fluorescent.sum())
        if neutral and fluorescent.any():
            reference=cv2.imread(str(neutral/path.name))
            assert reference is not None and reference.shape==im.shape,path.name
            unchanged_hue=marked(reference,key in ("matatabi","gyuki"))
            persistent[key]+=int((fluorescent & unchanged_hue).sum())
        frames[key]+=1
    assert len(frames)==9 and set(frames.values())=={19},frames
    print("Positive-control pixels:",positive)
    for key in sorted(frames):
        print(key,frames[key],"frames,",totals[key],"test-color pixels")
    if neutral:
        print("Flagged pixels also present at identical coordinates with a neutral skin:",
              sum(persistent.values()),"of",sum(totals.values()))
        assert persistent==totals,(persistent,totals)
    return totals

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shots",type=Path)
    parser.add_argument("--neutral-control",type=Path)
    args=parser.parse_args()
    check(args.shots,args.neutral_control)
