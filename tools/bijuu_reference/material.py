"""LabPBR surface maps from the pictured markings, with no parallax displacement."""
from PIL import Image, ImageChops, ImageFilter, ImageOps

# The darker cloth remains matte. Highlighted glaze and chakra have moderate
# reflectance. Emission is reserved for the bright marks already in the art.
SMOOTHNESS = {
    "shukaku": (27,95), "matatabi": (36,136), "isobu": (29,127),
    "son_goku": (24,115), "kokuo": (38,155), "saiken": (32,128),
    "chomei": (26,103), "gyuki": (30,128), "kurama": (27,145),
}
EMISSION = {"matatabi": 42, "son_goku": 30, "saiken": 15, "gyuki": 25, "kurama": 18}


def rgba_map(image,key):
    alpha=image.getchannel("A")
    light=ImageOps.grayscale(image)
    # Use local color variation as shallow tangential detail. Keep LabPBR
    # height constant at the surface, so shaders do not extrude or shift art.
    x_kernel=ImageFilter.Kernel((3,3),(-1,0,1,-2,0,2,-1,0,1),scale=14,offset=128)
    y_kernel=ImageFilter.Kernel((3,3),(1,2,1,0,0,0,-1,-2,-1),scale=14,offset=128)
    interior=alpha.filter(ImageFilter.MinFilter(3))
    neutral=Image.new("L",image.size,128)
    nx=Image.composite(light.filter(x_kernel),neutral,interior)
    ny=Image.composite(light.filter(y_kernel),neutral,interior)
    # Force an unshifted surface under all material shader profiles.
    white=Image.new("L",image.size,255)
    normal=Image.merge("RGBA",(nx,ny,white,white))
    lo,hi=SMOOTHNESS[key]
    low,high=light.getextrema()
    span=max(64,high-low)
    def measure(v):
        return min(1.0,v/span)
    red=ImageChops.multiply(light.point(lambda v: lo+int((hi-lo)*measure(v)**1.3)),alpha)
    green=ImageChops.multiply(light.point(lambda v: 9+int(19*measure(v))),alpha)
    rr,gg,bb,_=image.split()
    saturation=ImageChops.subtract(ImageChops.lighter(rr,ImageChops.lighter(gg,bb)),
                                   ImageChops.darker(rr,ImageChops.darker(gg,bb)))
    glow=light.point(lambda v: max(0,min(EMISSION.get(key,0),((v-126)//3))))
    saturated=saturation.point(lambda v: 255 if v>101 else 0)
    emission=ImageChops.multiply(ImageChops.multiply(glow,saturated),alpha)
    blue=Image.new("L",image.size)
    spec=Image.merge("RGBA",(red,green,blue,emission))
    return normal,spec
