"""Find and verify dungeon-local anchors used by content/expeditions/gates.json.

``suggest`` proposes candidate cells from real geometry (boundary arrival clusters, open flat
areas that can hold a court/arena, reachable containers). ``verify`` checks proposed
``local_pos`` values against the route graph: each anchor must be a route node, and (with an
entrance) reachable. ``fill`` writes a NEW gates file with only verified positions applied.
Candidates are hints for a human pick; a verified anchor is still not a native walkthrough.
"""
from collections import deque
import copy
import json
from pathlib import Path
import re

from . import blocks as B
from . import nbt
from .audit import RouteGraph


def _flat_regions(rg, cells, min_cells):
    g = rg.g
    remaining = set(cells)
    regions = []
    while remaining:
        start = remaining.pop()
        x0, y, z0 = g.xyz(start)
        queue, members = deque([start]), [start]
        while queue:
            i = queue.popleft()
            x, _, z = g.xyz(i)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if 0 <= nx < g.w and 0 <= nz < g.l:
                    j = g.idx(nx, y, nz)
                    if j in remaining:
                        remaining.discard(j)
                        members.append(j)
                        queue.append(j)
        if len(members) >= min_cells:
            pts = [g.xyz(i) for i in members]
            xs, zs = [p[0] for p in pts], [p[2] for p in pts]
            cx, cz = sum(xs) / len(xs), sum(zs) / len(zs)
            centre = min(pts, key=lambda p: (p[0] - cx) ** 2 + (p[2] - cz) ** 2)
            regions.append({'y': y, 'cells': len(members), 'bbox_xz': [[min(xs), min(zs)], [max(xs), max(zs)]],
                            'centre_cell': list(centre)})
    regions.sort(key=lambda r: -r['cells'])
    return regions


def suggest(schem, entrances=None, top=12, min_area=48):
    rg = RouteGraph(schem)
    g = rg.g
    stand = [m.start() for m in re.finditer(b'\x01', rg.stand)]
    edge = [i for i in stand if g.xyz(i)[0] in (0, g.w - 1) or g.xyz(i)[2] in (0, g.l - 1)]
    arrivals = []
    for region in _flat_regions(rg, edge, 1)[:top]:
        arrivals.append(dict(region, kind='arrival_candidate'))
    if entrances:
        seeds = [g.idx(*p) for p in entrances if g.inside(*p) and rg.node[g.idx(*p)]]
    else:
        seeds = edge
    reached = rg.bfs(seeds)
    reached_stand = [i for i in stand if i in reached]
    open_areas = []
    for region in _flat_regions(rg, reached_stand, min_area)[:top]:
        x0, z0 = region['bbox_xz'][0]
        x1, z1 = region['bbox_xz'][1]
        region['span'] = [x1 - x0 + 1, z1 - z0 + 1]
        region['use'] = 'arena' if min(region['span']) >= 30 else 'court' if min(region['span']) >= 12 else 'room'
        open_areas.append(region)
    containers = []
    for tag in schem.tile_tags:
        tid = B.normalize_tile_id(tag.scalar('id', '') if 'id' in tag else '')
        if tid not in B.CONTAINER_TILES:
            continue
        try:
            p = tuple(int(tag.scalar(a)) for a in 'xyz')
        except (TypeError, ValueError, nbt.NBTError):
            continue
        if not g.inside(*p):
            continue
        near = None
        for dy in (0, 1, -1):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (p[0] + dx, p[1] + dy, p[2] + dz)
                if g.inside(*q) and g.idx(*q) in reached:
                    near = list(q)
                    break
            if near:
                break
        containers.append({'pos': list(p), 'id': tid, 'stand_next_to_it': near, 'reachable': near is not None})
    return {'seed_mode': 'explicit' if entrances else 'boundary_auto', 'reached_nodes': len(reached),
            'arrival_candidates': arrivals, 'open_areas': open_areas, 'containers': containers,
            'note': 'Candidates come from geometry only. Pick anchors by looking at them, then run verify.'}


def verify(schem, gates, dungeon, entrances=None, measured=None):
    """Check every anchor of ``dungeon``; ``measured`` overrides local_pos (anchor -> [x,y,z])."""
    rg = RouteGraph(schem)
    g = rg.g
    reached = None
    if entrances:
        seeds = [g.idx(*p) for p in entrances if g.inside(*p) and rg.node[g.idx(*p)]]
        reached = rg.bfs(seeds) if seeds else set()
    rows = []
    for aid, anchor in sorted(gates.get('anchors', {}).items()):
        if anchor.get('dungeon') != dungeon:
            continue
        pos = (measured or {}).get(aid, anchor.get('local_pos'))
        row = {'anchor': aid, 'kind': anchor.get('kind'), 'pos': pos}
        if pos is None:
            row['status'] = 'unmeasured'
        elif not (isinstance(pos, (list, tuple)) and len(pos) == 3 and all(isinstance(v, int) for v in pos)):
            row['status'] = 'malformed'
        elif not g.inside(*pos):
            row['status'] = 'outside_schematic'
        elif not rg.node[g.idx(*pos)]:
            row['status'] = 'not_a_route_node'
        elif reached is not None and g.idx(*pos) not in reached:
            row['status'] = 'unreachable_from_entrance'
        else:
            row['status'] = 'ok' if reached is not None else 'route_node_unchecked_reachability'
        rows.append(row)
    return rows


def fill(gates_path, output_path, rows):
    """Write a new gates file applying only anchors that verified 'ok'."""
    gates_path, output_path = Path(gates_path), Path(output_path)
    if output_path.exists() or output_path.resolve() == gates_path.resolve():
        raise FileExistsError('fill writes a new file; refusing %s' % output_path)
    data = json.loads(gates_path.read_text(encoding='utf-8'))
    out = copy.deepcopy(data)
    applied = []
    for row in rows:
        if row['status'] == 'ok':
            out['anchors'][row['anchor']]['local_pos'] = list(row['pos'])
            applied.append(row['anchor'])
    output_path.write_text(json.dumps(out, indent=2) + '\n', encoding='utf-8')
    return applied
