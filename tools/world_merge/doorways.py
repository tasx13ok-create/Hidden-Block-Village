"""Propose the smallest doorway openings that connect closed walk regions to the main route.

For every walk region (weak component) that is not the main route and contains a container or
is a large floor, search outward at body height for the cheapest straight-through path to a
main-route cell: each step moves one block horizontally on an existing solid floor, and the cost
is the number of solid blocks in the 2-high body space that would have to become air. Cells
holding tile entities, liquids, or command/spawner blocks are never cleared. The result is a
PROPOSED edit plan (expect/set pairs) for human review and ``apply``; geometry intent such as
"this was a deliberate crate stack" cannot be inferred, so nothing is applied automatically.
"""
import heapq
import re

from . import blocks as B
from .audit import RouteGraph

MAX_COST = 4
# Glass, panes, trapdoors, bars and fences are cheap to open; masonry and wood are not.
SOFT = frozenset({20, 95, 102, 160, 96, 167, 101, 85, 113, 188, 189, 190, 191, 192})
MAX_STEPS = 8


def _solid_body(g, rg, x, y, z):
    """Solid cells occupying the body space at feet (x,y,z), or None if not clearable."""
    cells = []
    for dy in (0, 1):
        if y + dy >= g.h:
            continue
        i = g.idx(x, y + dy, z)
        if rg.passable[i]:
            continue
        bid = g.bid(i)
        if bid in B.FLUIDS or bid in B.COMMAND_BLOCKS or bid in B.SPAWNERS or bid in B.EXPECTS_TILE:
            return None
        cells.append((x, y + dy, z))
    return cells


def propose(schem, main_rank=None, min_stand=16, max_cost=MAX_COST, max_steps=MAX_STEPS, door=True):
    rg = RouteGraph(schem)
    g = rg.g
    comps = sorted(rg.weak_components(), key=lambda c: -sum(1 for i in c if rg.stand[i]))
    if main_rank is None:
        # Skip a ground plane that spans the whole footprint at low height, if present.
        # Only when a separate elevated region exists (sky-island templates).
        main_rank = 0
        top = comps[:3]
        lows = [n for n, c in enumerate(top)
                if max(g.xyz(i)[1] for i in c) < 40 and any(g.xyz(i)[0] in (0, g.w - 1) for i in c)]
        highs = [n for n, c in enumerate(top) if n not in lows and min(g.xyz(i)[1] for i in c) >= 40]
        if lows and highs:
            main_rank = highs[0]
    rank = {}
    for n, c in enumerate(comps):
        for i in c:
            rank[i] = n
    tiles = schem.tile_at()
    containers = {p for p, tags in tiles.items()
                  if B.normalize_tile_id(tags[0].scalar('id', '')) in B.CONTAINER_TILES}
    floor_ok = lambda x, y, z: y > 0 and B.FLOOR_TABLE[g.blocks[g.idx(x, y - 1, z)]]
    proposals, skipped, covered = [], [], set()
    for n, comp in enumerate(comps):
        if n == main_rank:
            continue
        stand = [i for i in comp if rg.stand[i]]
        pts = [g.xyz(i) for i in stand]
        held = sorted(p for p in containers
                      if any(abs(p[0] - q[0]) <= 1 and abs(p[2] - q[2]) <= 1 and abs(p[1] - q[1]) <= 1 for q in pts))
        if not held and len(stand) < min_stand:
            continue
        if not held or set(held) <= covered:
            continue  # only chest-holding regions not already opened by another proposal
        # Dijkstra over feet positions at the region's own heights.
        dist, heap, prev = {}, [], {}
        for p in pts:
            dist[p] = (0, 0)
            heapq.heappush(heap, (0, 0, p))
        goal = None
        while heap:
            cost, steps, p = heapq.heappop(heap)
            if dist.get(p) != (cost, steps):
                continue
            x, y, z = p
            if steps and g.inside(x, y, z) and rg.stand[g.idx(x, y, z)] and rank.get(g.idx(x, y, z)) == main_rank:
                goal = p
                break
            if steps >= max_steps:
                continue
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y, z + dz)
                if not g.inside(*q) or not floor_ok(*q):
                    continue
                body = _solid_body(g, rg, *q)
                if body is None:
                    continue
                c2 = cost + sum(1 if g.bid(g.idx(*c)) in SOFT else 3 for c in body)
                if c2 > max_cost * 3:
                    continue
                key = (c2, steps + 1)
                if q not in dist or key < dist[q]:
                    dist[q] = key
                    prev[q] = p
                    heapq.heappush(heap, (c2, steps + 1, q))
        if goal is None:
            skipped.append({'region_rank': n, 'stand': len(stand), 'containers': held,
                            'why': 'no straight opening within %d blocks / %d solid cells' % (max_steps, max_cost)})
            continue
        path, cur = [], goal
        while cur in prev:
            path.append(cur)
            cur = prev[cur]
        edits = []
        steps = list(reversed(path))
        start = prev.get(steps[0]) if steps else None
        for k, q in enumerate(steps):
            body = _solid_body(g, rg, *q) or []
            before = start if k == 0 else steps[k - 1]
            dx, dz = q[0] - before[0], q[2] - before[2]
            facing = {(1, 0): 0, (0, 1): 1, (-1, 0): 2, (0, -1): 3}[(dx, dz)]
            full = len(body) == 2 and door and not any(e['set']['block'] == 64 for e in edits)
            for c in body:
                b, d = schem.get(*c)
                if full:
                    target = (64, facing) if c[1] == q[1] else (64, 8)
                    why = 'oak door in place of the glass sealing closed region %d (containers %s)' % (n, held)
                else:
                    target = (0, 0)
                    why = 'clear blocked walkway cell for closed region %d (containers %s)' % (n, held)
                edits.append({'id': 'door-%d-%d-%d' % c, 'pos': list(c), 'expect': {'block': b, 'data': d},
                              'set': {'block': target[0], 'data': target[1]}, 'reason': why})
        covered |= set(held)
        proposals.append({'region_rank': n, 'stand': len(stand), 'containers': held, 'cost': len(edits),
                          'path': [list(p) for p in reversed(path)], 'edits': edits})
    return {'main_region_rank': main_rank, 'main_sample': list(g.xyz(next(i for i in comps[main_rank] if rg.stand[i]))),
            'proposals': proposals, 'skipped': skipped, 'status': 'PROPOSED_FOR_REVIEW'}


def plan(schem, result, input_sha256=None):
    edits = [e for p in result['proposals'] for e in p['edits']]
    out = {'schema': 1, 'status': 'PROPOSED_FOR_REVIEW', 'edits': edits}
    if input_sha256:
        out['input_sha256'] = input_sha256
    return out
