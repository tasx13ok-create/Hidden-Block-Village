"""Reference sample coordinates and Minecraft 1.12.2 ModelBiped UV layout."""
from dataclasses import dataclass


SCALE = 2
FACE_ORDER = ("east", "west", "top", "bottom", "front", "back")
UV_BOXES = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}
PART_NAMES = ("helmet", "chestplate", "leggings", "boots")

@dataclass(frozen=True)
class Design:
    title: str
    sample: tuple[tuple[int, int], ...]
    notes: str

# Local coordinates within each of the nine hero outfit panels. Color roles in
# order: seam, shadow, main, darker detail, main detail, small bright detail.
# These positions sample the clothing, not the creature or its backdrop.
DESIGNS = {
    "shukaku": Design("Shukaku", ((74,37),(74,96),(94,98),(82,91),(93,73),(73,20)),
                      "Sand shell, navy cheek marks and a large spiral between descending lines"),
    "matatabi": Design("Matatabi", ((82,36),(100,108),(72,80),(70,30),(90,100),(92,13)),
                       "Midnight hood and bodysuit, blue wrists and cyan flame edges"),
    "isobu": Design("Isobu", ((79,144),(68,105),(79,68),(77,105),(82,95),(99,34)),
                    "Deep teal shell, divided face guard and a pale segmented belly plate"),
    "son_goku": Design("Son Goku", ((72,27),(55,72),(82,90),(76,78),(80,16),(89,13)),
                       "Near black armor, red lava seams and flame-edged brow, cuffs and soles"),
    "kokuo": Design("Kokuo", ((82,96),(94,17),(87,79),(87,72),(79,73),(82,79)),
                    "Pale lilac plates, purple collar and belt, and a round waist seal"),
    "saiken": Design("Saiken", ((82,30),(86,88),(71,80),(61,94),(92,19),(49,52)),
                     "Acid-green shell, black wrap bands, aqua slime drips and a hanging seal"),
    "chomei": Design("Chomei", ((63,81),(89,47),(51,68),(75,94),(78,83),(53,55)),
                     "Olive sleeves and greaves, ivory wing ribs and a forked pointed apron"),
    "gyuki": Design("Gyuki", ((77,43),(84,49),(82,102),(91,78),(79,83),(75,22)),
                    "Black plum armor, purple horns, central ring and violet leg stripes"),
    "kurama": Design("Kurama", ((71,91),(95,99),(67,85),(92,83),(80,17),(84,94)),
                     "Orange sleeves and leggings, gold brow and torso, charcoal ring seal"),
}
ORIGINS = [(4+166*i,630) for i in range(5)] + [(4+166*i,1005) for i in range(4)]


def sample_colors(reference):
    palettes = {}
    for (key, design), (ox, oy) in zip(DESIGNS.items(),ORIGINS):
        points = [(ox+x,oy+y) for x,y in design.sample]
        palettes[key] = dict(zip("OSMTAG", ((point,reference.getpixel(point)[:3]) for point in points)))
    return palettes


def uv_rects(part):
    u,v,w,h,d = UV_BOXES[part]
    return {
        "east": (u+d+w,v+d,d,h), "west": (u,v+d,d,h), "top": (u+d,v,w,d),
        "bottom": (u+d+w,v,w,d), "front": (u+d,v+d,w,h), "back": (u+2*d+w,v+d,w,h),
    }
