"""Cut only the original painted lens interiors, in helmet-front coordinates."""
from PIL import Image, ImageDraw

# Rectangles trace the hard steps in the existing artwork. Coordinates are on
# a 160-unit front view; the inset leaves the painted border and chin fittings.
PANES = {
    "shukaku": [(32,65,138,86),(7,86,152,111),(55,111,109,124)],
    "matatabi": [(25,62,137,83),(9,83,152,103),(31,103,129,108),(48,108,109,148)],
    "isobu": [(10,84,60,106),(104,84,149,106)],
    "son_goku": [(24,69,61,84),(103,69,139,84),(24,84,139,94),(13,94,145,108),(36,108,126,115),(58,115,105,128)],
    "kokuo": [(23,66,58,83),(102,66,137,83),(23,83,137,93),(19,93,141,113),(53,113,106,126)],
    "saiken": [(20,59,140,83),(12,83,147,109),(54,109,105,126)],
    "chomei": [(26,61,139,82),(14,82,149,99),(59,99,106,124)],
    "gyuki": [(33,63,130,80),(24,80,139,88),(16,88,143,108),(58,108,106,151)],
    "kurama": [(26,60,61,73),(103,60,135,73),(26,73,135,84),(15,84,145,108),(55,108,106,124)],
}


def open_visor(atlas, beast):
    unit=atlas.width//256
    box=(36*unit,80*unit,44*unit,88*unit)
    front=atlas.crop(box)
    alpha=front.getchannel("A")
    draw=ImageDraw.Draw(alpha)
    for rect in PANES[beast]:
        x,y,right,bottom=(round(value*front.width/160) for value in rect)
        draw.rectangle((x,y,right-1,bottom-1),fill=0)
    front.putalpha(alpha)
    atlas.paste(front,box)
