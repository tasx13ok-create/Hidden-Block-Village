import sys,glob,os
from PIL import Image, ImageDraw
src=sys.argv[1]; out=sys.argv[2]
def face(im,x,y,w,h): return im.crop((x,y,x+w,y+h))
def front(im):
    c=Image.new("RGBA",(16,32),(0,0,0,0))
    layers=[((8,8,8,8),(4,0)),((20,20,8,12),(4,8)),((44,20,4,12),(0,8)),((36,52,4,12),(12,8)),((4,20,4,12),(4,20)),((20,52,4,12),(8,20)),
            ((40,8,8,8),(4,0)),((20,36,8,12),(4,8)),((44,36,4,12),(0,8)),((52,52,4,12),(12,8)),((4,36,4,12),(4,20)),((4,52,4,12),(8,20))]
    for (x,y,w,h),(dx,dy) in layers: c.alpha_composite(face(im,x,y,w,h),(dx,dy))
    return c
def back(im):
    c=Image.new("RGBA",(16,32),(0,0,0,0))
    layers=[((24,8,8,8),(4,0)),((32,20,8,12),(4,8)),((52,20,4,12),(12,8)),((44,52,4,12),(0,8)),((12,20,4,12),(8,20)),((28,52,4,12),(4,20)),
            ((56,8,8,8),(4,0)),((32,36,8,12),(4,8)),((52,36,4,12),(12,8)),((60,52,4,12),(0,8)),((12,36,4,12),(8,20)),((12,52,4,12),(4,20))]
    for (x,y,w,h),(dx,dy) in layers: c.alpha_composite(face(im,x,y,w,h),(dx,dy))
    return c
fs=sorted(glob.glob(src+"/*.png")); S=10
sheet=Image.new("RGBA",(len(fs)*(16*S*2+40)+20,32*S+60),(40,44,52,255)); d=ImageDraw.Draw(sheet)
for i,f in enumerate(fs):
    im=Image.open(f).convert("RGBA"); x=20+i*(16*S*2+40)
    sheet.alpha_composite(front(im).resize((16*S,32*S),Image.NEAREST),(x,40))
    sheet.alpha_composite(back(im).resize((16*S,32*S),Image.NEAREST),(x+16*S+10,40))
    d.text((x,12),os.path.basename(f)[:-4].replace("_"," "),fill=(255,255,255))
sheet.save(out)
