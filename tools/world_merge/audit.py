"""Static audits of a 1.12 schematic: format, IDs, tile entities, gravity, fluids, routes and access.

These are offline geometric checks. They find concrete candidates (with coordinates) for review;
they do not replace a native server walk, which is the only proof of collision/physics behavior.
Results use schematic-local coordinates (x, y, z) unless stated otherwise.
"""
from collections import Counter, deque
import re

from . import blocks as B
from . import nbt

SAMPLE_LIMIT = 40


def _sample(items, limit=SAMPLE_LIMIT):
    items = list(items)
    return {'count': len(items), 'sample': items if limit is None else items[:limit]}


class Grid:
    """Fast per-cell lookups built once per schematic."""

    def __init__(self, schem):
        self.s = schem
        self.w, self.h, self.l = schem.width, schem.height, schem.length
        self.wl = self.w * self.l
        self.blocks = schem.blocks
        self.data = schem.data
        self.ext = schem.add  # None or high nibble per cell
        self._ext_cells = None

    def ext_cells(self):
        if self._ext_cells is None:
            self._ext_cells = [] if self.ext is None else [m.start() for m in re.finditer(rb'[^\x00]', self.ext)]
        return self._ext_cells

    def mask(self, lut):
        """bytearray of 0/1 per cell using a 256-entry table on the low ID byte.

        Extended (AddBlocks) cells are non-vanilla and are always treated as solid, i.e. they
        become 1 in floor masks and 0 in passable masks.
        """
        out = bytearray(self.blocks.translate(lut))
        if self.ext is not None:
            solid = 1 if lut is B.FLOOR_TABLE else 0
            for i in self.ext_cells():
                out[i] = solid
        return out

    def bid(self, i):
        return self.blocks[i] | ((self.ext[i] << 8) if self.ext is not None else 0)

    def idx(self, x, y, z):
        return (y * self.l + z) * self.w + x

    def xyz(self, i):
        x = i % self.w
        r = i // self.w
        return (x, r // self.l, r % self.l)

    def inside(self, x, y, z):
        return 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l

    def cells_with(self, ids):
        """Indices whose (low-byte, non-extended) block ID is in ``ids``."""
        if not ids:
            return []
        lut = bytes(1 if i in ids else 0 for i in range(256))
        m = self.mask(lut)
        found = [x.start() for x in re.finditer(b'\x01', m)]
        if self.ext is not None:
            ext = set(self.ext_cells())
            found = [i for i in found if i not in ext]
        return found


def _shift_and(a, b, shift):
    """Bytewise AND of 0/1 arrays a and b shifted by ``shift`` cells (b[i + shift])."""
    n = len(a)
    if shift >= n:
        return bytearray(n)
    shifted = bytes(b[shift:]) + bytes(shift)
    ia = int.from_bytes(a, 'little')
    ib = int.from_bytes(shifted, 'little')
    return bytearray((ia & ib).to_bytes(n, 'little'))


# Format and identities --------------------------------------------------------------------
def audit_format(schem):
    g = Grid(schem)
    counts = Counter(schem.blocks)
    ids = Counter()
    for low, n in counts.items():
        ids[low] += n
    for i in g.ext_cells():
        ids[g.blocks[i]] -= 1
        ids[g.bid(i)] += 1
    ids = Counter({k: v for k, v in ids.items() if v})
    bad_data = sum(n for value, n in Counter(schem.data).items() if value > 15)
    non_vanilla = sorted(k for k in ids if k not in B.VANILLA_IDS)
    wl = g.wl
    occupied = [y for y in range(schem.height) if schem.blocks[y * wl:(y + 1) * wl].count(0) != wl]
    return {
        'occupied_y_range': [occupied[0], occupied[-1]] if occupied else None,
        'dimensions': {'width': schem.width, 'height': schem.height, 'length': schem.length},
        'cells': schem.cells,
        'non_air': schem.cells - ids.get(0, 0),
        'block_ids': {str(k): v for k, v in sorted(ids.items())},
        'non_vanilla_ids': non_vanilla,
        'extended_id_cells': len(g.ext_cells()),
        'data_values_over_15': bad_data,
        'we_origin': schem.we_origin, 'we_offset': schem.we_offset,
        'entities': len(schem.entity_tags),
        'warnings': list(schem.warnings),
    }


def audit_tiles(schem):
    g = Grid(schem)
    issues = []
    types = Counter()
    by_pos = {}
    containers = []
    for n, tag in enumerate(schem.tile_tags):
        raw_id = tag.scalar('id', '') if 'id' in tag else ''
        tid = B.normalize_tile_id(raw_id)
        types[tid] += 1
        pos = []
        for axis in 'xyz':
            t = tag.get(axis)
            pos.append(t.value if t is not None and t.type in (nbt.INT, nbt.SHORT, nbt.BYTE) else None)
        pos = tuple(pos)
        if None in pos:
            issues.append({'kind': 'tile_missing_coordinates', 'index': n, 'id': raw_id})
            continue
        if not g.inside(*pos):
            issues.append({'kind': 'tile_out_of_bounds', 'index': n, 'id': raw_id, 'pos': pos})
            continue
        if pos in by_pos:
            issues.append({'kind': 'tile_duplicate_position', 'index': n, 'id': raw_id, 'pos': pos,
                           'first_index': by_pos[pos]})
            continue
        by_pos[pos] = n
        bid = g.bid(g.idx(*pos))
        expected = B.TILE_BLOCKS.get(tid)
        if expected is None:
            issues.append({'kind': 'tile_unknown_id', 'index': n, 'id': raw_id, 'pos': pos, 'block': bid})
        elif bid not in expected:
            issues.append({'kind': 'tile_block_mismatch', 'index': n, 'id': raw_id, 'pos': pos,
                           'block': bid, 'expected_blocks': sorted(expected)})
        if tid in B.CONTAINER_TILES:
            items = tag.get('Items')
            count = len(items.value) if items is not None and items.type == nbt.LIST else 0
            containers.append({'pos': pos, 'id': tid, 'item_stacks': count,
                               'loot_table': tag.scalar('LootTable') if 'LootTable' in tag else None})
        if tid == 'command_block':
            cmd = tag.get('Command')
            # Never execute or echo embedded commands; report only their size.
            issues.append({'kind': 'command_block_present', 'pos': pos,
                           'command_chars': len(cmd.value) if cmd is not None and cmd.type == nbt.STRING else 0})
        if tid == 'mob_spawner':
            issues.append({'kind': 'spawner_present', 'pos': pos})
    missing = []
    for i in g.cells_with(B.EXPECTS_TILE):
        p = g.xyz(i)
        if p not in by_pos:
            missing.append({'pos': p, 'block': g.bid(i)})
    for i in g.cells_with(B.COMMAND_BLOCKS):
        p = g.xyz(i)
        if p not in by_pos:
            issues.append({'kind': 'command_block_present', 'pos': p, 'command_chars': 0})
    return {'types': dict(sorted(types.items())), 'issues': issues,
            'blocks_missing_tile': _sample(missing), 'containers': containers}


# Gravity -----------------------------------------------------------------------------------
def audit_gravity(schem, limit=SAMPLE_LIMIT):
    g = Grid(schem)
    unsupported, floor_edge = [], []
    for i in g.cells_with(B.GRAVITY):
        x, y, z = g.xyz(i)
        if y == 0:
            floor_edge.append((x, y, z))
            continue
        below = g.bid(i - g.wl)
        if below in B.FALL_THROUGH:
            unsupported.append({'pos': (x, y, z), 'block': g.bid(i), 'data': g.data[i], 'below': below})
    return {'unsupported': _sample(unsupported, limit),
            'on_schematic_floor': _sample(floor_edge, limit),
            'note': 'Blocks on the bottom layer depend on the terrain at the chosen paste height.'}


# Fluids ------------------------------------------------------------------------------------
# Liquids flow into cells that do not block movement, except doors, signs, ladders, reeds and
# portals (BlockDynamicLiquid.isBlocked in 1.12).
FLUID_ENTERABLE = frozenset(B.NO_COLLISION - {63, 68, 65, 83, 90, 119, 209, 217})
_FLUID_ENTER_LUT = B.table(lambda i: i in FLUID_ENTERABLE)


def audit_fluids(schem, component_limit=200):
    g = Grid(schem)
    fluid_cells = g.cells_with(B.FLUIDS)
    fluid_set = set(fluid_cells)
    seen = set()
    enter = g.mask(_FLUID_ENTER_LUT)
    comps = []
    w, h, l, wl = g.w, g.h, g.l, g.wl
    for start in fluid_cells:
        if start in seen:
            continue
        kind = 'water' if g.blocks[start] in B.WATER else 'lava'
        family = B.WATER if kind == 'water' else B.LAVA
        queue = deque([start])
        seen.add(start)
        cells = sources = flowing = 0
        mins = [w, h, l]
        maxs = [-1, -1, -1]
        edges = set()
        leaks, edge_flows = [], []
        top_y = -1
        while queue:
            i = queue.popleft()
            x, y, z = g.xyz(i)
            cells += 1
            bid, dv = g.blocks[i], g.data[i]
            is_source = dv == 0
            if is_source:
                sources += 1
            else:
                flowing += 1
            for a, v in enumerate((x, y, z)):
                mins[a] = min(mins[a], v)
                maxs[a] = max(maxs[a], v)
            top_y = max(top_y, y)
            if x == 0: edges.add('-x')
            if x == w - 1: edges.add('+x')
            if z == 0: edges.add('-z')
            if z == l - 1: edges.add('+z')
            if y == 0: edges.add('-y')
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0), (0, 1, 0)):
                nx, ny, nz = x + dx, y + dy, z + dz
                if not g.inside(nx, ny, nz):
                    continue
                j = g.idx(nx, ny, nz)
                if j in fluid_set:
                    if j not in seen and g.blocks[j] in family:
                        seen.add(j)
                        queue.append(j)
                    continue
                if dy == 1 or not enter[j]:
                    continue
                entry = {'from': (x, y, z), 'into': (nx, ny, nz), 'into_block': g.bid(j),
                         'direction': 'down' if dy == -1 else 'side'}
                # A flowing block with level 7 cannot spread sideways; falling (>=8) spreads down.
                if is_source:
                    leaks.append(entry)
                elif not (dy == 0 and (dv & 7) == 7):
                    edge_flows.append(entry)
        side_edges = sorted(e for e in edges if e != '-y')
        if leaks:
            classification = 'leak_candidate'
        elif side_edges:
            classification = 'open_boundary'
        elif edge_flows:
            classification = 'authored_flow'
        else:
            classification = 'contained'
        comps.append({'fluid': kind, 'cells': cells, 'sources': sources, 'flowing': flowing,
                      'bbox': [mins, maxs], 'top_y': top_y, 'schematic_edges': sorted(edges),
                      'classification': classification,
                      'source_outlets': _sample(leaks, 12), 'flowing_outlets': _sample(edge_flows, 12)})
    comps.sort(key=lambda c: -c['cells'])
    summary = Counter(c['classification'] for c in comps)
    return {'components': len(comps), 'by_classification': dict(summary),
            'fluid_cells': len(fluid_cells), 'details': comps[:component_limit],
            'note': ('open_boundary: fluid reaches a side edge (e.g. sea meant to continue into '
                     'world water) and needs matching terrain; authored_flow: only flowing blocks '
                     'have outlets (waterfalls/streams, verify natively); leak_candidate: a source '
                     'or still block borders an enterable cell and will spread on update.')}


# Routes ------------------------------------------------------------------------------------
_SAFE_PASS = B.table(lambda i: (i in B.NO_COLLISION or i in B.OPENABLE or i in B.WATER)
                     and i not in B.HAZARDS)
_WATER_LUT = B.table(lambda i: i in B.WATER)
_CLIMB_LUT = B.table(lambda i: i in B.CLIMBABLE)
_HALF_STEP = frozenset(B.STAIRS | {44, 126, 182, 205})
MAX_SAFE_DROP = 3


class RouteGraph:
    """Implicit walk/climb/swim graph over a schematic.

    Nodes are feet cells: *stand* (solid-topped floor below, two passable cells), *climb*
    (ladder/vine in the body cell) or *swim* (water body cell). Moves: walk on level, step up one
    (needs headroom unless onto/off a stair or slab), drop up to three, climb vertically, swim in
    six directions and climb out of water onto an adjacent ledge.
    """

    def __init__(self, schem, open_sky=True):
        g = self.g = Grid(schem)
        n, wl = schem.cells, g.wl
        self.passable = g.mask(_SAFE_PASS)
        floor = g.mask(B.FLOOR_TABLE)
        # stand[i] = floor[i - wl] & pass[i] & pass[i + wl]
        body_head = _shift_and(self.passable, self.passable, wl)
        if open_sky:  # cells above the schematic top are assumed to be open air
            top = (g.h - 1) * wl
            body_head[top:] = self.passable[top:]
        floor_below = bytes(wl) + bytes(floor[:n - wl])
        self.stand = _shift_and(body_head, bytearray(floor_below), 0)
        water = g.mask(_WATER_LUT)
        climb = g.mask(_CLIMB_LUT)
        self.swim = water
        self.climb = climb
        self.node = bytearray((int.from_bytes(self.stand, 'little') | int.from_bytes(water, 'little')
                               | int.from_bytes(climb, 'little')).to_bytes(n, 'little'))

    def floor_block(self, x, y, z):
        if y == 0:
            return None
        return self.g.bid(self.g.idx(x, y - 1, z))

    def pas(self, x, y, z):
        g = self.g
        if not g.inside(x, y, z):
            return y >= g.h and 0 <= x < g.w and 0 <= z < g.l  # open sky above
        return bool(self.passable[g.idx(x, y, z)])

    def neighbors(self, i):
        g = self.g
        x, y, z = g.xyz(i)
        stand, swim, climb = self.stand, self.swim, self.climb
        here_half = self.floor_block(x, y, z) in _HALF_STEP
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < g.w and 0 <= nz < g.l):
                continue
            j = g.idx(nx, y, nz)
            if stand[j] or swim[j] or climb[j]:
                yield j
                continue
            # step up one
            if y + 1 < g.h:
                k = j + g.wl
                if stand[k] and (here_half or self.floor_block(nx, y + 1, nz) in _HALF_STEP
                                 or self.pas(x, y + 2, z)):
                    yield k
                    continue
            # drop: enter neighbor column at body height, fall until a node
            if self.passable[j] and self.pas(nx, y + 1, nz):
                for d in range(1, MAX_SAFE_DROP + 1):
                    ty = y - d
                    if ty < 0:
                        break
                    k = g.idx(nx, ty, nz)
                    if stand[k] or swim[k] or climb[k]:
                        yield k
                        break
                    if not self.passable[k]:
                        break
        # vertical
        if climb[i] or swim[i]:
            if y + 1 < g.h:
                k = i + g.wl
                if self.node[k]:
                    yield k
        if y > 0:
            k = i - g.wl
            if (climb[i] or swim[i] or climb[k] or swim[k]) and self.node[k]:
                yield k

    def bfs(self, seeds, limit=None):
        seen = set()
        queue = deque()
        for s in seeds:
            if self.node[s] and s not in seen:
                seen.add(s)
                queue.append(s)
        while queue:
            i = queue.popleft()
            for j in self.neighbors(i):
                if j not in seen:
                    seen.add(j)
                    queue.append(j)
                    if limit and len(seen) >= limit:
                        return seen
        return seen


def _parse_points(points):
    return [tuple(int(v) for v in p) for p in points or []]


def audit_routes(schem, entrances=None, targets=None, component_report=8):
    rg = RouteGraph(schem)
    g = rg.g
    stand_cells = [m.start() for m in re.finditer(b'\x01', rg.stand)]
    entrances = _parse_points(entrances)
    notes = []
    invalid_entrances = []
    seeds = []
    if entrances:
        for p in entrances:
            if not g.inside(*p) or not rg.node[g.idx(*p)]:
                invalid_entrances.append(p)
            else:
                seeds.append(g.idx(*p))
        seed_mode = 'explicit'
    else:
        seed_mode = 'boundary_auto'
        for i in stand_cells:
            x, y, z = g.xyz(i)
            if x in (0, g.w - 1) or z in (0, g.l - 1):
                seeds.append(i)
        notes.append('No entrances given: seeded from every standable cell on a side boundary. '
                     'Pass --entrance with the authored arrival point(s) for a meaningful route.')
    reached = rg.bfs(seeds)
    # Interaction reach for containers: a reached node within 2 horizontal and -2..+1 vertical.
    def reachable_from_route(p):
        x, y, z = p
        for dy in (-1, 0, 1, -2, 2):
            for dx in (-2, -1, 0, 1, 2):
                for dz in (-2, -1, 0, 1, 2):
                    q = (x + dx, y + dy, z + dz)
                    if g.inside(*q) and g.idx(*q) in reached:
                        return True
        return False

    unreachable_tiles = []
    tile_total = 0
    for tag in schem.tile_tags:
        tid = B.normalize_tile_id(tag.scalar('id', '') if 'id' in tag else '')
        if tid not in B.CONTAINER_TILES:
            continue
        try:
            p = tuple(tag.scalar(a) for a in 'xyz')
        except nbt.NBTError:
            continue
        if None in p or not g.inside(*p):
            continue
        tile_total += 1
        if not reachable_from_route(p):
            unreachable_tiles.append({'pos': p, 'id': tid})
    target_results = []
    for p in _parse_points(targets):
        if not g.inside(*p) or not rg.node[g.idx(*p)]:
            target_results.append({'pos': p, 'reachable': False, 'problem': 'not_a_route_node'})
        else:
            target_results.append({'pos': p, 'reachable': g.idx(*p) in reached})
    # Isolated standable regions of meaningful size (possible stranded rooms).
    islands = []
    remaining = set(stand_cells) - reached
    budget = 0
    while remaining and len(islands) < 2000 and budget < 2_000_000:
        start = remaining.pop()
        comp = rg.bfs([start])
        budget += len(comp)
        remaining -= comp
        stand_count = sum(1 for c in comp if rg.stand[c])
        if stand_count >= 16:
            xs = [g.xyz(c) for c in comp]
            islands.append({'stand_cells': stand_count,
                            'bbox': [[min(p[a] for p in xs) for a in range(3)],
                                     [max(p[a] for p in xs) for a in range(3)]],
                            'example': xs[0]})
    islands.sort(key=lambda c: -c['stand_cells'])
    reached_stand = sum(1 for i in reached if rg.stand[i])
    return {
        'seed_mode': seed_mode, 'seeds': len(seeds), 'invalid_entrances': invalid_entrances,
        'standable_cells': len(stand_cells), 'reached_nodes': len(reached),
        'reached_standable': reached_stand,
        'containers': tile_total, 'unreachable_containers': _sample(unreachable_tiles),
        'targets': target_results,
        'unreached_regions_16plus': {'count': len(islands), 'largest': islands[:component_report]},
        'model': ('walk, step +1 (headroom unless stair/slab), drop <=%d, ladder/vine climb, swim; '
                  'openable doors/gates passable; iron doors, lava, fire, cactus, cobweb blocked'
                  % MAX_SAFE_DROP),
        'notes': notes,
    }, rg, reached


# Doors, stairs and ladders ---------------------------------------------------------------
_DOOR_FACING = {0: (1, 0), 1: (0, 1), 2: (-1, 0), 3: (0, -1)}  # lower half bits 0-1: E,S,W,N
_STAIR_ASCEND = {0: (1, 0), 1: (-1, 0), 2: (0, 1), 3: (0, -1)}   # E,W,S,N
_LADDER_SUPPORT = {2: (0, 1), 3: (0, -1), 4: (1, 0), 5: (-1, 0)}  # facing N/S/W/E -> block behind


def audit_access(schem, route_graph=None):
    rg = route_graph or RouteGraph(schem)
    g = rg.g
    orphans, blocked, iron = [], [], []
    for i in g.cells_with(B.DOORS):
        x, y, z = g.xyz(i)
        bid, dv = g.bid(i), g.data[i]
        upper = bool(dv & 8)
        if upper:
            below = g.idx(x, y - 1, z) if y > 0 else None
            if below is None or g.bid(below) != bid or g.data[below] & 8:
                orphans.append({'pos': (x, y, z), 'block': bid, 'half': 'upper'})
            continue
        above = g.idx(x, y + 1, z) if y + 1 < g.h else None
        if above is None or g.bid(above) != bid or not g.data[above] & 8:
            orphans.append({'pos': (x, y, z), 'block': bid, 'half': 'lower'})
            continue
        dx, dz = _DOOR_FACING[dv & 3]
        sides = []
        for sign in (1, -1):
            sx, sz = x + sign * dx, z + sign * dz
            if not (0 <= sx < g.w and 0 <= sz < g.l):
                sides.append('edge')
                continue
            sides.append('open' if rg.pas(sx, y, sz) and rg.pas(sx, y + 1, sz) else 'blocked')
        if 'blocked' in sides:
            blocked.append({'pos': (x, y, z), 'block': bid, 'sides': sides})
        if bid in B.IRON_DOORS:
            iron.append({'pos': (x, y, z)})
    stairs_blocked = []
    for i in g.cells_with(B.STAIRS):
        dv = g.data[i]
        if dv & 4:
            continue  # upside-down stairs are ceilings/eaves
        x, y, z = g.xyz(i)
        dx, dz = _STAIR_ASCEND[dv & 3]
        lx, lz = x - dx, z - dz  # approach cell on the low side, same level as the stair
        if not (0 <= lx < g.w and 0 <= lz < g.l):
            continue
        if not rg.stand[g.idx(lx, y, lz)]:
            continue
        if not (rg.pas(x, y + 1, z) and rg.pas(x, y + 2, z)):
            stairs_blocked.append({'pos': (x, y, z), 'approach': (lx, y, lz)})
    ladders = []
    for i in g.cells_with({65}):
        x, y, z = g.xyz(i)
        off = _LADDER_SUPPORT.get(g.data[i])
        if off is None:
            ladders.append({'pos': (x, y, z), 'problem': 'invalid_facing', 'data': g.data[i]})
            continue
        sx, sz = x + off[0], z + off[1]
        if not (0 <= sx < g.w and 0 <= sz < g.l):
            ladders.append({'pos': (x, y, z), 'problem': 'support_outside_schematic'})
            continue
        if not B.FLOOR_TABLE[g.blocks[g.idx(sx, y, sz)]]:
            ladders.append({'pos': (x, y, z), 'problem': 'no_solid_support', 'support': (sx, y, sz)})
    return {'door_orphan_halves': _sample(orphans), 'doors_blocked': _sample(blocked),
            'iron_doors_need_redstone': _sample(iron), 'stairs_blocked_headroom': _sample(stairs_blocked),
            'ladder_problems': _sample(ladders)}


def full_audit(schem, entrances=None, targets=None):
    routes, rg, _ = audit_routes(schem, entrances, targets)
    return {
        'format': audit_format(schem),
        'tiles': audit_tiles(schem),
        'gravity': audit_gravity(schem),
        'fluids': audit_fluids(schem),
        'routes': routes,
        'access': audit_access(schem, rg),
    }
