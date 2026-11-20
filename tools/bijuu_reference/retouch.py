"""Small controlled ink and highlight passes on the reference's pictured marks."""
from math import cos, pi, sin
from PIL import Image, ImageChops, ImageDraw, ImageFilter


def ellipse(cx,cy,rx,ry,start=0,end=360):
    return [(cx+rx*cos((start+(end-start)*i/64)*pi/180),
             cy+ry*sin((start+(end-start)*i/64)*pi/180)) for i in range(65)]


# Sample a color from the motif itself, then strengthen the existing contour.
# Each tuple is a normalized sample point, line weight, and reference trace.
# The source color gate prevents an edge trace from drawing through a broad panel.
CHEST_TRACES = {
    "shukaku": (
        ((.607,.50),.029,ellipse(.625,.562,.092,.085,15,365)),
        ((.543,.69),.030,((.447,.60),(.472,.657),(.529,.697),(.608,.722),(.683,.704))),
    ),
    "matatabi": (
        ((.241,.706),.025,((.202,.739),(.222,.722),(.272,.716),(.292,.676),(.301,.637))),
        ((.760,.716),.022,((.730,.747),(.777,.718),(.801,.675),(.806,.643))),
    ),
    "isobu": (
        ((.555,.651),.023,((.394,.66),(.492,.658),(.548,.646),(.653,.639))),
        ((.619,.756),.020,((.424,.782),(.503,.768),(.576,.761),(.669,.762))),
    ),
    "son_goku": (
        ((.242,.433),.022,((.174,.513),(.195,.476),(.232,.444),(.258,.399),(.267,.380))),
        ((.735,.477),.020,((.686,.367),(.706,.405),(.721,.445),(.754,.475),(.776,.508))),
    ),
    "kokuo": (
        ((.322,.667),.028,ellipse(.429,.675,.127,.121,85,275)),
        ((.447,.418),.021,((.294,.347),(.345,.371),(.407,.394),(.458,.415),(.507,.411))),
    ),
    "saiken": (
        ((.589,.279),.022,((.587,.204),(.583,.263),(.570,.313),(.561,.369),(.548,.401))),
        ((.466,.549),.022,((.481,.440),(.454,.478),(.454,.521),(.466,.549),(.478,.580))),
    ),
    "chomei": (
        ((.507,.494),.020,((.485,.390),(.501,.457),(.509,.508),(.512,.579),(.513,.619))),
        ((.584,.669),.017,((.563,.620),(.583,.669),(.588,.714),(.585,.754))),
    ),
    "gyuki": (
        ((.531,.343),.024,((.351,.401),(.384,.366),(.455,.345),(.531,.343),(.591,.362))),
        ((.595,.611),.023,((.615,.543),(.619,.577),(.595,.611),(.568,.633),(.506,.638))),
    ),
    "kurama": (
        ((.444,.440),.025,ellipse(.433,.590,.190,.148,205,346)),
        ((.432,.528),.021,ellipse(.433,.592,.082,.072,195,365)),
        ((.420,.371),.024,((.409,.256),(.418,.308),(.420,.355),(.420,.397),(.427,.433))),
    ),
}


def reinforce_stroke(surface,dx,weight,path):
    w,h=surface.size
    ix,iy=dx
    shade=surface.getpixel((min(w-1,round(ix*w)),min(h-1,round(iy*h))))[:3]
    points=[(round(x*w),round(y*h)) for x,y in path]
    radius=max(1,round(weight*w))
    # Keep this an edge polish rather than a second guessed emblem. The
    # sampled vicinity sets a luminance/chroma limit on any drawn pixels.
    mask=Image.new("L",surface.size)
    ImageDraw.Draw(mask).line(points,fill=144,width=radius,joint="curve")
    mask=mask.filter(ImageFilter.GaussianBlur(max(.8,radius*.13)))
    if not (box:=mask.getbbox()):
        return
    current=surface.crop(box)
    ink=Image.new("RGB",current.size,shade)
    diff=ImageChops.difference(current.convert("RGB"),ink)
    red,green,blue=diff.split()
    distance=ImageChops.lighter(red,ImageChops.lighter(green,blue))
    gate=distance.point(lambda v: max(0,255-v*4))
    alpha=ImageChops.multiply(mask.crop(box),gate)
    surface.paste(ink.convert("RGBA"),box,alpha)


def finish_chest(surface,key):
    for sample,weight,path in CHEST_TRACES[key]:
        reinforce_stroke(surface,sample,weight,path)
    return surface
