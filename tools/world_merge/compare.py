"""Compare two schematic versions (e.g. v9 -> v17) under an *explicit* alignment.

Alignment is a translation ``delta`` so that ``new_local = old_local + delta``. It can be given
directly, derived from one anchor pair (an authored entrance in both files), or taken from both
files' WorldEdit origins when both were copied from the same world. A centred crop is never
assumed. ``suggest_alignments`` votes on deltas between same-type tile entities; it is a hint to
check, not proof that authored entrances align.
"""
from collections import Counter

from . import blocks as B


def delta_from_anchor(old_point, new_point):
    return tuple(n - o for o, n in zip(old_point, new_point))


def delta_from_we_origin(old, new):
    if old.we_origin is None or new.we_origin is None:
        raise ValueError('Both schematics need WEOrigin metadata for we-origin alignment')
    return tuple(o - n for o, n in zip(old.we_origin, new.we_origin))


def _tiles(schem):
    out = []
    for tag in schem.tile_tags:
        try:
            pos = tuple(int(tag.scalar(a)) for a in 'xyz')
        except (TypeError, ValueError):
            continue
        out.append((B.normalize_tile_id(tag.scalar('id', '') if 'id' in tag else ''), pos))
    return out


def suggest_alignments(old, new, top=5, max_pairs=200_000):
    votes = Counter()
    new_by_type = {}
    for tid, pos in _tiles(new):
        new_by_type.setdefault(tid, []).append(pos)
    pairs = 0
    for tid, pos in _tiles(old):
        for npos in new_by_type.get(tid, ()):
            votes[delta_from_anchor(pos, npos)] += 1
            pairs += 1
            if pairs >= max_pairs:
                break
    return [{'delta': list(d), 'tile_matches': n} for d, n in votes.most_common(top)]


def compare(old, new, delta):
    dx, dy, dz = delta
    # Overlap in old-local coordinates.
    x0, x1 = max(0, -dx), min(old.width, new.width - dx)
    y0, y1 = max(0, -dy), min(old.height, new.height - dy)
    z0, z1 = max(0, -dz), min(old.length, new.length - dz)
    stats = Counter()
    if x1 <= x0 or y1 <= y0 or z1 <= z0:
        overlap = None
    else:
        overlap = {'old_min': [x0, y0, z0], 'old_max': [x1 - 1, y1 - 1, z1 - 1]}
        span = x1 - x0
        for y in range(y0, y1):
            for z in range(z0, z1):
                ia = old.index(x0, y, z)
                ib = new.index(x0 + dx, y + dy, z + dz)
                ra, rb = old.blocks[ia:ia + span], new.blocks[ib:ib + span]
                da, db = old.data[ia:ia + span], new.data[ib:ib + span]
                if ra == rb and da == db:
                    nonair = span - ra.count(0)
                    stats['same_non_air'] += nonair
                    continue
                for a, b, c, d in zip(ra, rb, da, db):
                    if a == b:
                        if a:
                            stats['same_non_air' if c == d else 'same_block_different_data'] += 1
                    elif a == 0:
                        stats['added'] += 1
                    elif b == 0:
                        stats['removed'] += 1
                    else:
                        stats['replaced'] += 1
    old_nonair = old.cells - old.blocks.count(0)
    new_nonair = new.cells - new.blocks.count(0)
    compared = sum(stats.values())
    new_tiles = {pos: tid for tid, pos in _tiles(new)}
    tile_rows = Counter()
    unmatched = []
    for tid, pos in _tiles(old):
        target = tuple(p + d for p, d in zip(pos, delta))
        got = new_tiles.get(target)
        if got == tid:
            tile_rows['matched'] += 1
        elif got is None:
            tile_rows['missing_in_new'] += 1
            unmatched.append({'old': list(pos), 'id': tid})
        else:
            tile_rows['type_changed'] += 1
    return {
        'delta': list(delta),
        'old_size': [old.width, old.height, old.length], 'new_size': [new.width, new.height, new.length],
        'overlap': overlap,
        'old_non_air': old_nonair, 'new_non_air': new_nonair,
        'cells': dict(stats),
        'retained_fraction_of_old_non_air': round(stats['same_non_air'] / old_nonair, 4) if old_nonair else None,
        'compared_changed_cells': compared - stats['same_non_air'],
        'tiles': dict(tile_rows), 'old_tiles_unmatched': unmatched[:40],
    }
