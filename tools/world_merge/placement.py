"""Explicit paste-placement arithmetic for WorldEdit 6 MCEdit schematics in a 1.12 world.

WorldEdit 6 writes ``WEOrigin`` = the copied region's minimum corner and ``WEOffset`` = that
minimum corner minus the player's position at ``//copy``. ``//paste`` (without ``-o``) puts the
minimum corner at ``paste_position + WEOffset``; ``//paste -o`` restores the authored location,
i.e. the minimum corner returns to ``WEOrigin``. A "blind" paste standing on the stored origin
therefore lands at ``WEOrigin + WEOffset``. All three are reported so nobody has to guess.
"""

WORLD_MIN_Y = 0
WORLD_MAX_Y = 255  # 1.12 build limit (inclusive)


def _add(a, b):
    return tuple(p + q for p, q in zip(a, b))


def _sub(a, b):
    return tuple(p - q for p, q in zip(a, b))


def bounds_for_min(schem, min_corner):
    size = (schem.width, schem.height, schem.length)
    max_corner = _add(min_corner, (size[0] - 1, size[1] - 1, size[2] - 1))
    fits = min_corner[1] >= WORLD_MIN_Y and max_corner[1] <= WORLD_MAX_Y
    return {
        'min': list(min_corner), 'max': list(max_corner),
        'fits_build_height': fits,
        'below_world_layers': max(0, WORLD_MIN_Y - min_corner[1]),
        'above_world_layers': max(0, max_corner[1] - WORLD_MAX_Y),
        'chunks': {'x': [min_corner[0] >> 4, max_corner[0] >> 4], 'z': [min_corner[2] >> 4, max_corner[2] >> 4]},
        'regions': {'x': [min_corner[0] >> 9, max_corner[0] >> 9], 'z': [min_corner[2] >> 9, max_corner[2] >> 9]},
    }


def min_from_anchor(local, world):
    """Minimum corner that puts schematic-local cell ``local`` at world cell ``world``."""
    return _sub(tuple(world), tuple(local))


def placement_report(schem, anchor_local=None, anchor_world=None, paste_at=None):
    origin, offset = schem.we_origin, schem.we_offset
    report = {'size': [schem.width, schem.height, schem.length], 'we_origin': origin, 'we_offset': offset}
    if schem.height > WORLD_MAX_Y - WORLD_MIN_Y + 1:
        report['error'] = 'Schematic is taller than the 1.12 world; no placement fits'
    lo = WORLD_MIN_Y
    hi = WORLD_MAX_Y - (schem.height - 1)
    report['valid_min_y_range'] = [lo, hi] if hi >= lo else None
    if offset is not None:
        report['valid_paste_y_range'] = [lo - offset[1], hi - offset[1]] if hi >= lo else None
    scenarios = {}
    if origin is not None:
        scenarios['authored_location (//paste -o)'] = bounds_for_min(schem, origin)
        if offset is not None:
            copy_point = _sub(origin, offset)
            report['authored_copy_position'] = list(copy_point)
            scenarios['blind_paste_standing_at_stored_origin'] = bounds_for_min(schem, _add(origin, offset))
    if paste_at is not None:
        if offset is None:
            report['paste_at_error'] = 'No WEOffset metadata; use an explicit anchor instead'
        else:
            scenarios['paste_at_%d_%d_%d' % tuple(paste_at)] = bounds_for_min(schem, _add(tuple(paste_at), offset))
    if anchor_local is not None and anchor_world is not None:
        m = min_from_anchor(anchor_local, anchor_world)
        entry = bounds_for_min(schem, m)
        entry['anchor'] = {'local': list(anchor_local), 'world': list(anchor_world)}
        if offset is not None:
            entry['equivalent_paste_position'] = list(_sub(m, offset))
        scenarios['explicit_anchor'] = entry
    report['scenarios'] = scenarios
    return report
