"""Re-ink the boundaries of the colors already present on each reference surface."""
import cv2
import numpy as np
from PIL import Image

def cel_surface(image):
    rgb=np.array(image.convert("RGB"))
    h,w=rgb.shape[:2]
    if min(h,w)<3:
        return image
    # Compute the regions at a common working density. A bilateral pass
    # removes compression speckles while keeping narrow dark seams separate.
    scale=min(1,208/max(w,h))
    small=cv2.resize(rgb,(max(2,round(w*scale)),max(2,round(h*scale))),interpolation=cv2.INTER_AREA)
    smooth=cv2.bilateralFilter(small,7,22,3)
    palette_image=Image.fromarray(smooth).quantize(colors=18,method=Image.Quantize.MEDIANCUT,
                                                  dither=Image.Dither.NONE)
    labels=np.asarray(palette_image)
    lab=cv2.cvtColor(smooth,cv2.COLOR_RGB2LAB).astype(np.float32)
    # Use actual pixels nearest the perceptual center of each region.
    colors=[]
    centers=[]
    for index in np.unique(labels):
        mask=labels==index
        pixels=lab[mask]
        center=np.median(pixels,axis=0)
        nearest=np.argmin(np.sum((pixels-center)**2,axis=1))
        centers.append(center)
        colors.append(smooth[mask][nearest])
    # Derive contours from continuous color distances. This places each
    # line between source samples rather than tracing their square grid.
    best=np.full((h,w),np.inf,dtype=np.float32)
    winner=np.zeros((h,w),np.uint8)
    for index,center in enumerate(centers):
        distance=np.sum((lab-center)**2,axis=2)
        field=cv2.resize(distance,(w,h),interpolation=cv2.INTER_CUBIC)
        closer=field<best
        best[closer]=field[closer]
        winner[closer]=index
    paint=np.asarray(colors,dtype=np.uint8)[winner]
    # Single-pixel edge coverage remains visible on the smaller native UVs.
    paint=cv2.GaussianBlur(paint,(3,3),.48)
    result=Image.fromarray(paint).convert("RGBA")
    result.putalpha(image.getchannel("A"))
    return result
