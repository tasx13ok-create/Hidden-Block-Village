"""Perspective-correct surface extraction and edge finishing for the armor art."""
from PIL import Image, ImageFilter


def to_unit_quad(points):
    """Map a unit square to TL, TR, BR, BL in the matched source image."""
    (x0,y0),(x1,y1),(x2,y2),(x3,y3)=points
    dx1,dx2=x1-x2,x3-x2
    dy1,dy2=y1-y2,y3-y2
    residual_x=x0-x1+x2-x3
    residual_y=y0-y1+y2-y3
    det=dx1*dy2-dx2*dy1
    if abs(det)<1e-9:
        raise ValueError(f"Degenerate reference plane {points!r}")
    g=(residual_x*dy2-dx2*residual_y)/det
    h=(dx1*residual_y-residual_x*dy1)/det
    return x1-x0+g*x1,x3-x0+h*x3,x0,y1-y0+g*y1,y3-y0+h*y3,y0,g,h


def perspective_coefficients(points,size):
    width,height=size
    a,b,c,d,e,f,g,h=to_unit_quad(points)
    return a/width,b/height,c,d/width,e/height,f,g/width,h/height


def render_plane(reference,points,size):
    # Input landmarks are TL, BL, BR, TR. Render directly at the face's UV
    # extent so the low-resolution photo blocks aren't copied as giant pixels.
    tl,bl,br,tr=points
    coeffs=perspective_coefficients((tl,tr,br,bl),size)
    plane=reference.transform(size,Image.Transform.PERSPECTIVE,coeffs,Image.Resampling.BICUBIC)
    strength=max(.8,min(size)*.014)
    return plane.filter(ImageFilter.UnsharpMask(radius=strength,percent=38,threshold=7)).convert("RGBA")


def resize_albedo(image,size):
    """Use a smooth color filter but keep transparent cuts on their exact UV."""
    if image.size==size:
        return image
    alpha=image.getchannel("A").resize(size,Image.Resampling.NEAREST)
    rgb=image.convert("RGB").resize(size,Image.Resampling.LANCZOS)
    solid=Image.composite(rgb,Image.new("RGB",size),alpha)
    solid.putalpha(alpha)
    return solid


def clean_thumbnail(image,size):
    # The alternate views are tiny. Filter only the source-scale noise before
    # projecting a larger face, then recover contrast on the broad design.
    tile=image.convert("RGB").filter(ImageFilter.GaussianBlur(.28))
    tile=tile.resize(size,Image.Resampling.BICUBIC)
    tile=tile.filter(ImageFilter.UnsharpMask(radius=max(1,min(size)*.025),percent=34,threshold=9))
    return tile.convert("RGBA")


def align_border(side,front,west):
    """Pin the touching edge texel so a traced sleeve doesn't shift on a corner."""
    fx=0 if west else front.width-1
    sx=side.width-1 if west else 0
    side.paste(front.crop((fx,0,fx+1,front.height)),(sx,0))
    return side
