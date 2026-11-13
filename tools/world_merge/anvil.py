"""Paste a schematic into a *copied* Minecraft 1.12 Anvil world, with a rollback snapshot.

Safety model:

* ``copy_world`` makes a new candidate directory (refuses an existing destination or nesting),
  hashes every source and copied file, and writes ``WORLD-MERGE-CANDIDATE.json`` in the copy.
* ``paste_into_world_copy`` refuses any directory without that marker, so the recovered source
  world can never be edited by this tool. Before a region file changes, its original bytes are
  saved under ``<copy>/../<copy-name>.rollback/<n>/``; ``rollback`` restores them exactly.
* Region files are rewritten to a temporary file and atomically replaced. Untouched chunks are
  copied as their original compressed bytes; edited chunks keep every unknown tag (Forge
  capabilities, entities outside the volume, ...).
* The paste replaces blocks, block data, Add nibbles and tile entities inside the volume, drops
  pending block ticks there and sets ``LightPopulated=0`` so the game relights the chunk.
  Lighting, mob AI and fluid physics still require a native load to verify.
"""
import hashlib
import io
import json
import math
import os
from pathlib import Path
import shutil
import struct
import time

from . import nbt
from .schematic import sha256_file

MARKER = 'WORLD-MERGE-CANDIDATE.json'
SECTOR = 4096
DATA_VERSION_1122 = 1343
DIMENSION_DIRS = {'overworld': 'region', 'nether': 'DIM-1/region', 'end': 'DIM1/region'}


class WorldError(ValueError):
    pass


# World copy ----------------------------------------------------------------------------------
def _is_within(child, parent):
    try:
        child.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def copy_world(source, dest):
    source, dest = Path(source), Path(dest)
    if not (source / 'level.dat').is_file():
        raise WorldError('%s is not a world directory (no level.dat)' % source)
    if dest.exists():
        raise FileExistsError('Destination %s already exists; candidates are always new' % dest)
    if _is_within(dest, source) or _is_within(source, dest):
        raise WorldError('Source and destination must not contain each other')
    files = []
    for path in sorted(p for p in source.rglob('*') if p.is_file()):
        if path.is_symlink():
            raise WorldError('Refusing to follow symlink %s' % path)
        files.append(path)
    dest.mkdir(parents=True)
    rows = []
    for path in files:
        rel = path.relative_to(source)
        if rel.name == MARKER:
            continue
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        src_hash, dst_hash = sha256_file(path), sha256_file(target)
        if src_hash != dst_hash:
            raise WorldError('Copy verification failed for %s' % rel)
        rows.append({'path': rel.as_posix(), 'bytes': target.stat().st_size, 'sha256': src_hash})
    manifest = {'schema': 1, 'source': str(source), 'files': len(rows),
                'bytes': sum(r['bytes'] for r in rows),
                'manifest_sha256': hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest(),
                'created_unix': int(time.time()), 'pastes': []}
    (dest / MARKER).write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    manifest['file_rows'] = rows
    return manifest


# Region IO -----------------------------------------------------------------------------------
class Region:
    """In-memory region: raw (compression, payload) per chunk slot plus timestamps."""

    def __init__(self, path):
        self.path = Path(path)
        self.chunks = {}
        self.timestamps = [0] * 1024
        if self.path.exists():
            self._read()

    def _read(self):
        raw = self.path.read_bytes()
        if not raw:
            return
        if len(raw) < 2 * SECTOR:
            raise WorldError('%s is shorter than a region header' % self.path.name)
        locations = struct.unpack('>1024I', raw[:SECTOR])
        self.timestamps = list(struct.unpack('>1024I', raw[SECTOR:2 * SECTOR]))
        for slot, loc in enumerate(locations):
            offset, count = loc >> 8, loc & 0xFF
            if not loc:
                continue
            start = offset * SECTOR
            if offset < 2 or start + 5 > len(raw):
                raise WorldError('%s slot %d points outside the file' % (self.path.name, slot))
            length = struct.unpack('>I', raw[start:start + 4])[0]
            if length < 1 or start + 4 + length > len(raw) or length > count * SECTOR:
                raise WorldError('%s slot %d has an invalid length' % (self.path.name, slot))
            self.chunks[slot] = (raw[start + 4], raw[start + 5:start + 4 + length])

    def load_chunk(self, slot):
        if slot not in self.chunks:
            return None
        comp, payload = self.chunks[slot]
        if comp not in (1, 2):
            raise WorldError('Unsupported chunk compression %d' % comp)
        return nbt.loads(payload)

    def store_chunk(self, slot, name, root):
        self.chunks[slot] = (2, nbt.dumps(root, name, compression='zlib'))
        self.timestamps[slot] = int(time.time())

    def to_bytes(self):
        out = io.BytesIO()
        locations = [0] * 1024
        body = io.BytesIO()
        sector = 2
        for slot in sorted(self.chunks):
            comp, payload = self.chunks[slot]
            blob = struct.pack('>IB', len(payload) + 1, comp) + payload
            pad = (-len(blob)) % SECTOR
            count = (len(blob) + pad) // SECTOR
            if count > 255:
                raise WorldError('Chunk in slot %d is too large for the region format' % slot)
            locations[slot] = (sector << 8) | count
            body.write(blob + b'\x00' * pad)
            sector += count
        out.write(struct.pack('>1024I', *locations))
        out.write(struct.pack('>1024I', *self.timestamps))
        out.write(body.getvalue())
        return out.getvalue()

    def save_atomic(self):
        tmp = self.path.with_suffix('.mca.tmp')
        tmp.write_bytes(self.to_bytes())
        os.replace(tmp, self.path)


# Chunk editing -------------------------------------------------------------------------------
def _nibble_get(arr, i):
    b = arr[i >> 1]
    return (b >> 4) & 0xF if i & 1 else b & 0xF


def _nibble_set(arr, i, v):
    j = i >> 1
    arr[j] = (arr[j] & 0x0F) | ((v & 0xF) << 4) if i & 1 else (arr[j] & 0xF0) | (v & 0xF)


def new_chunk(cx, cz):
    level = nbt.compound({
        'xPos': nbt.Tag(nbt.INT, cx), 'zPos': nbt.Tag(nbt.INT, cz),
        'LastUpdate': nbt.Tag(nbt.LONG, 0), 'InhabitedTime': nbt.Tag(nbt.LONG, 0),
        'TerrainPopulated': nbt.Tag(nbt.BYTE, 1), 'LightPopulated': nbt.Tag(nbt.BYTE, 0),
        'V': nbt.Tag(nbt.BYTE, 1),
        'Biomes': nbt.Tag(nbt.BYTE_ARRAY, bytearray([1] * 256)),
        'HeightMap': nbt.Tag(nbt.INT_ARRAY, [0] * 256),
        'Sections': nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
        'Entities': nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
        'TileEntities': nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
    })
    return nbt.compound({'DataVersion': nbt.Tag(nbt.INT, DATA_VERSION_1122), 'Level': level})


def _section(level, sy, create):
    sections = level['Sections']
    for sec in sections.value:
        if sec.scalar('Y') == sy:
            return sec
    if not create:
        return None
    sec = nbt.compound({'Y': nbt.Tag(nbt.BYTE, sy), 'Blocks': nbt.Tag(nbt.BYTE_ARRAY, bytearray(4096)),
                        'Data': nbt.Tag(nbt.BYTE_ARRAY, bytearray(2048)),
                        'BlockLight': nbt.Tag(nbt.BYTE_ARRAY, bytearray(2048)),
                        'SkyLight': nbt.Tag(nbt.BYTE_ARRAY, bytearray(b'\xff' * 2048))})
    sections.value.append(sec)
    sections.elem = nbt.COMPOUND
    return sec


def _paste_chunk(root, schem, world_min, cx, cz, clear_air, tile_tags_world):
    level = root['Level']
    mx, my, mz = world_min
    x0 = max(cx * 16, mx)
    x1 = min(cx * 16 + 15, mx + schem.width - 1)
    z0 = max(cz * 16, mz)
    z1 = min(cz * 16 + 15, mz + schem.length - 1)
    y0, y1 = my, my + schem.height - 1
    changed = 0
    for sy in range(y0 >> 4, (y1 >> 4) + 1):
        sec = _section(level, sy, create=True)
        blocks, data = sec['Blocks'].value, sec['Data'].value
        add = sec['Add'].value if 'Add' in sec else None
        for wy in range(max(y0, sy * 16), min(y1, sy * 16 + 15) + 1):
            ly = wy - my
            for wz in range(z0, z1 + 1):
                lz = wz - mz
                srow = (ly * schem.length + lz) * schem.width
                for wx in range(x0, x1 + 1):
                    lx = wx - mx
                    si = srow + lx
                    low = schem.blocks[si]
                    high = schem.add[si] if schem.add is not None else 0
                    if not clear_air and low == 0 and high == 0:
                        continue
                    ci = ((wy & 15) << 8) | ((wz & 15) << 4) | (wx & 15)
                    if high and add is None:
                        add = bytearray(2048)
                        sec.value['Add'] = nbt.Tag(nbt.BYTE_ARRAY, add)
                    if blocks[ci] != low or _nibble_get(data, ci) != schem.data[si] or \
                            (add is not None and _nibble_get(add, ci) != high):
                        changed += 1
                    blocks[ci] = low
                    _nibble_set(data, ci, schem.data[si])
                    if add is not None:
                        _nibble_set(add, ci, high)
    inside = lambda x, y, z: x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1
    removed_tiles = 0
    tiles = level.get('TileEntities')
    if tiles is None:
        tiles = level.value['TileEntities'] = nbt.Tag(nbt.LIST, [], nbt.COMPOUND)
    kept = []
    for t in tiles.value:
        pos = tuple(t.scalar(a) for a in 'xyz')
        if inside(*pos):
            removed_tiles += 1
        else:
            kept.append(t)
    added = [t for t in tile_tags_world if x0 <= t[0][0] <= x1 and z0 <= t[0][2] <= z1]
    kept.extend(tag for _, tag in added)
    tiles.value = kept
    tiles.elem = nbt.COMPOUND
    ticks = level.get('TileTicks')
    dropped_ticks = 0
    if ticks is not None:
        keep = [t for t in ticks.value if not inside(*(t.scalar(a) for a in 'xyz'))]
        dropped_ticks = len(ticks.value) - len(keep)
        ticks.value = keep
    entities = level.get('Entities')
    ent_inside = 0
    if entities is not None:
        for e in entities.value:
            pos = e.get('Pos')
            if pos is not None and len(pos.value) == 3:
                ex, ey, ez = (math.floor(v.value) for v in pos.value)
                ent_inside += inside(ex, ey, ez)
    level.value['LightPopulated'] = nbt.Tag(nbt.BYTE, 0)
    _recompute_heightmap(level)
    return {'cells_changed': changed, 'tiles_removed': removed_tiles, 'tiles_added': len(added),
            'ticks_dropped': dropped_ticks, 'entities_inside_left_untouched': ent_inside}


def _recompute_heightmap(level):
    hm = [0] * 256
    for sec in level['Sections'].value:
        sy = sec.scalar('Y')
        blocks = sec['Blocks'].value
        for ci in range(4095, -1, -1):
            if blocks[ci]:
                col = ci & 255
                top = sy * 16 + (ci >> 8) + 1
                if top > hm[col]:
                    hm[col] = top
    level.value['HeightMap'] = nbt.Tag(nbt.INT_ARRAY, hm)


def _world_tiles(schem, world_min):
    out = []
    mx, my, mz = world_min
    for tag in schem.tile_tags:
        copy = nbt.loads(nbt.dumps(tag, compression=None))[1]
        lx, ly, lz = (tag.scalar(a) if a in tag else None for a in 'xyz')
        if None in (lx, ly, lz) or not schem.in_bounds(lx, ly, lz):
            continue  # reported by the audit; never paste a tile outside the volume
        wx, wy, wz = lx + mx, ly + my, lz + mz
        for axis, v in zip('xyz', (wx, wy, wz)):
            copy.value[axis] = nbt.Tag(nbt.INT, v)
        out.append(((wx, wy, wz), copy))
    return out


def paste_into_world_copy(schem, world_dir, world_min, source_path=None, clear_air=True,
                          dimension='overworld'):
    world_dir = Path(world_dir)
    marker = world_dir / MARKER
    if not marker.is_file():
        raise WorldError('%s is not a candidate made by copy-world; refusing to edit it' % world_dir)
    mx, my, mz = world_min
    if my < 0 or my + schem.height - 1 > 255:
        raise WorldError('Paste Y range %d..%d is outside 0..255' % (my, my + schem.height - 1))
    region_dir = world_dir / DIMENSION_DIRS[dimension]
    region_dir.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(marker.read_text(encoding='utf-8'))
    paste_no = len(manifest.get('pastes', [])) + 1
    rollback_dir = world_dir.parent / ('%s.rollback' % world_dir.name) / str(paste_no)
    if rollback_dir.exists():
        raise FileExistsError('Rollback directory %s already exists' % rollback_dir)
    tiles = _world_tiles(schem, world_min)
    chunks = [(cx, cz) for cx in range(mx >> 4, ((mx + schem.width - 1) >> 4) + 1)
              for cz in range(mz >> 4, ((mz + schem.length - 1) >> 4) + 1)]
    by_region = {}
    for cx, cz in chunks:
        by_region.setdefault((cx >> 5, cz >> 5), []).append((cx, cz))
    regions_out, totals = [], {'cells_changed': 0, 'tiles_removed': 0, 'tiles_added': 0,
                               'ticks_dropped': 0, 'entities_inside_left_untouched': 0, 'chunks_created': 0}
    rollback_dir.mkdir(parents=True)
    for (rx, rz), members in sorted(by_region.items()):
        path = region_dir / ('r.%d.%d.mca' % (rx, rz))
        before = sha256_file(path) if path.exists() else None
        if path.exists():
            shutil.copy2(path, rollback_dir / path.name)
        region = Region(path)
        for cx, cz in members:
            slot = (cx & 31) + (cz & 31) * 32
            loaded = region.load_chunk(slot)
            if loaded is None:
                name, root = '', new_chunk(cx, cz)
                totals['chunks_created'] += 1
            else:
                name, root = loaded
            stats = _paste_chunk(root, schem, world_min, cx, cz, clear_air,
                                 [t for t in tiles if t[0][0] >> 4 == cx and t[0][2] >> 4 == cz])
            for k, v in stats.items():
                totals[k] += v
            region.store_chunk(slot, name, root)
        region.save_atomic()
        regions_out.append({'file': path.relative_to(world_dir).as_posix(), 'sha256_before': before,
                            'sha256_after': sha256_file(path), 'chunks': len(members)})
    record = {'paste': paste_no, 'schematic': Path(source_path).name if source_path else None,
              'schematic_sha256': sha256_file(source_path) if source_path else None,
              'world_min': list(world_min),
              'world_max': [mx + schem.width - 1, my + schem.height - 1, mz + schem.length - 1],
              'dimension': dimension, 'clear_air': clear_air, 'regions': regions_out, 'totals': totals,
              'rollback_dir': str(rollback_dir), 'unix': int(time.time())}
    (rollback_dir / 'PASTE-RECEIPT.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    manifest.setdefault('pastes', []).append(record)
    marker.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    record['scope'] = ('offline region rewrite; lighting/fluids/mob AI are recalculated by the game '
                       'and need a native server load to verify')
    return record


def rollback(world_dir, paste_no):
    """Restore the region bytes saved before paste ``paste_no`` (and every later paste)."""
    world_dir = Path(world_dir)
    marker = world_dir / MARKER
    if not marker.is_file():
        raise WorldError('Not a world-merge candidate: %s' % world_dir)
    manifest = json.loads(marker.read_text(encoding='utf-8'))
    pastes = manifest.get('pastes', [])
    if not 1 <= paste_no <= len(pastes):
        raise WorldError('No paste %d recorded' % paste_no)
    for record in reversed(pastes[paste_no - 1:]):
        snap = Path(record['rollback_dir'])
        for reg in record['regions']:
            target = world_dir / reg['file']
            saved = snap / Path(reg['file']).name
            if reg['sha256_before'] is None:
                if target.exists():
                    target.unlink()
            else:
                if sha256_file(saved) != reg['sha256_before']:
                    raise WorldError('Rollback snapshot %s is damaged' % saved)
                shutil.copy2(saved, target)
    manifest['pastes'] = pastes[:paste_no - 1]
    marker.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return {'restored_to_before_paste': paste_no, 'remaining_pastes': len(manifest['pastes'])}


def read_block(world_dir, x, y, z, dimension='overworld'):
    """(block_id, data) at a world position, for tests and post-paste spot checks."""
    region = Region(Path(world_dir) / DIMENSION_DIRS[dimension] / ('r.%d.%d.mca' % (x >> 9, z >> 9)))
    cx, cz = x >> 4, z >> 4
    loaded = region.load_chunk((cx & 31) + (cz & 31) * 32)
    if loaded is None:
        return None
    sec = _section(loaded[1]['Level'], y >> 4, create=False)
    if sec is None:
        return (0, 0)
    ci = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
    high = _nibble_get(sec['Add'].value, ci) if 'Add' in sec else 0
    return ((high << 8) | sec['Blocks'].value[ci], _nibble_get(sec['Data'].value, ci))


def chunk_tiles(world_dir, cx, cz, dimension='overworld'):
    region = Region(Path(world_dir) / DIMENSION_DIRS[dimension] / ('r.%d.%d.mca' % (cx >> 5, cz >> 5)))
    loaded = region.load_chunk((cx & 31) + (cz & 31) * 32)
    return [] if loaded is None else loaded[1]['Level']['TileEntities'].value

