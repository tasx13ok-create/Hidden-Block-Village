"""Minecraft 1.12 numeric block registry facts used by the audits.

Only behavior relevant to navigation, support, fluids and tile identity is modelled. Unknown or
extended (AddBlocks) IDs are treated conservatively as solid, non-vanilla blocks and reported.
"""

# Vanilla 1.12.2 assigns 0-252 and 255; 253/254 are unused.
VANILLA_IDS = frozenset(list(range(0, 253)) + [255])

AIR = 0
WATER = frozenset({8, 9})
LAVA = frozenset({10, 11})
FLUIDS = WATER | LAVA
FLOWING = frozenset({8, 10})
GRAVITY = frozenset({12, 13, 122, 145, 252})  # sand, gravel, dragon egg, anvil, concrete powder
CLIMBABLE = frozenset({65, 106})  # ladder, vine
DOORS = frozenset({64, 71, 193, 194, 195, 196, 197})
IRON_DOORS = frozenset({71})
FENCE_GATES = frozenset({107, 183, 184, 185, 186, 187})
TRAPDOORS = frozenset({96, 167})
STAIRS = frozenset({53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180, 203})
COMMAND_BLOCKS = frozenset({137, 210, 211})
SPAWNERS = frozenset({52})
HAZARDS = frozenset({10, 11, 51, 81, 30})

# Blocks with no (or negligible) collision: a player's body can occupy them.
NO_COLLISION = frozenset({
    0, 6, 27, 28, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 65, 66, 68, 69, 70, 72, 75, 76,
    77, 78, 83, 90, 104, 105, 106, 115, 119, 131, 132, 141, 142, 143, 147, 148, 157, 171, 175,
    176, 177, 207, 209, 217,
})
# Openable by a normal player without redstone. Iron doors/trapdoors are deliberately excluded.
OPENABLE = frozenset({64, 193, 194, 195, 196, 197, 96, 107, 183, 184, 185, 186, 187})
# Taller than a jump (1.5 blocks): cannot be stepped onto as a floor.
TALL = frozenset({85, 113, 139, 188, 189, 190, 191, 192, 107, 183, 184, 185, 186, 187})

# Blocks that game logic lets fall through for BlockFalling (air, fire, liquids).
FALL_THROUGH = frozenset({0, 51}) | FLUIDS

TILE_BLOCKS = {
    'chest': {54, 146}, 'furnace': {61, 62}, 'flower_pot': {140}, 'sign': {63, 68},
    'mob_spawner': {52}, 'command_block': {137, 210, 211}, 'skull': {144}, 'banner': {176, 177},
    'dispenser': {23}, 'dropper': {158}, 'hopper': {154}, 'brewing_stand': {117},
    'enchanting_table': {116}, 'beacon': {138}, 'jukebox': {84}, 'noteblock': {25},
    'comparator': {149, 150}, 'daylight_detector': {151, 178}, 'ender_chest': {130},
    'end_portal': {119}, 'end_gateway': {209}, 'piston': {36}, 'structure_block': {255},
    'bed': {26}, 'shulker_box': set(range(219, 235)),
}
# Pre-1.11 tile identifiers that may survive in old schematics.
LEGACY_TILE_NAMES = {
    'chest': 'chest', 'furnace': 'furnace', 'flowerpot': 'flower_pot', 'sign': 'sign',
    'mobspawner': 'mob_spawner', 'control': 'command_block', 'skull': 'skull', 'banner': 'banner',
    'trap': 'dispenser', 'dropper': 'dropper', 'hopper': 'hopper', 'cauldron': 'brewing_stand',
    'enchanttable': 'enchanting_table', 'beacon': 'beacon', 'recordplayer': 'jukebox',
    'music': 'noteblock', 'comparator': 'comparator', 'dldetector': 'daylight_detector',
    'enderchest': 'ender_chest', 'airportal': 'end_portal', 'piston': 'piston',
    'structure': 'structure_block', 'bed': 'bed', 'endgateway': 'end_gateway',
}
# Blocks whose authored content lives in a tile entity; a missing tile loses that content.
EXPECTS_TILE = frozenset({54, 146, 61, 62, 23, 158, 154, 117, 63, 68, 52, 137, 210, 211, 140,
                          144, 176, 177, 26}) | frozenset(range(219, 235))
CONTAINER_TILES = frozenset({'chest', 'furnace', 'dispenser', 'dropper', 'hopper',
                             'brewing_stand', 'shulker_box'})


def normalize_tile_id(raw):
    text = str(raw or '').strip()
    if ':' in text:
        text = text.split(':', 1)[1]
    key = text.lower()
    if key in TILE_BLOCKS:
        return key
    return LEGACY_TILE_NAMES.get(key.replace('_', ''), key)


def table(predicate):
    """256-entry translate table: 1 where predicate(low id) holds, else 0."""
    return bytes(1 if predicate(i) else 0 for i in range(256))


PASSABLE_TABLE = table(lambda i: i in NO_COLLISION or i in OPENABLE or i in FLUIDS)
FLOOR_TABLE = table(lambda i: i not in NO_COLLISION and i not in OPENABLE and i not in FLUIDS
                    and i not in TALL and i not in HAZARDS)
