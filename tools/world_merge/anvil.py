"""Paste a schematic into a *copied* Minecraft 1.12 Anvil world, with a rollback snapshot.

Safety model:

* ``copy_world`` makes a new candidate directory (refuses an existing destination or nesting),
  hashes every source and copied file, and writes ``WORLD-MERGE-CANDIDATE.json`` in the copy.
* ``paste_into_world_copy`` validates schema-2 source/candidate identity and rejects reparse
  aliases. Before any region changes, all outputs/backups and a recovery journal are durably
  staged under ``<copy>/../<copy-name>.rollback/<n>-<transaction-id>/``.
* Region files are rewritten to unique temporary files and atomically replaced. Untouched chunks are
  copied as their original compressed bytes; edited chunks keep every unknown tag (Forge
  capabilities, entities outside the volume, ...).
* The paste replaces blocks, block data, Add nibbles and tile entities at replaced cells, drops
  pending block ticks only there and sets ``LightPopulated=0`` to request native relighting.
  Lighting, mob AI and fluid physics still require a native load to verify.
"""
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import shutil
import stat
import struct
import time
import uuid
import zlib
from contextlib import contextmanager

from . import nbt
from .schematic import sha256_file

MARKER = 'WORLD-MERGE-CANDIDATE.json'
LOCK = '.WORLD-MERGE.lock'
SECTOR = 4096
MAX_REGION_BYTES = 512 * 1024 * 1024
MAX_CHUNK_NBT_BYTES = 32 * 1024 * 1024
DATA_VERSION_1122 = 1343
DIMENSION_DIRS = {'overworld': 'region', 'nether': 'DIM-1/region', 'end': 'DIM1/region'}


class WorldError(ValueError):
    pass


def _safe_path(path):
    """Check lexical ancestors before resolving: Windows junctions are not is_symlink()."""
    path = Path(os.path.abspath(path))
    for part in [*reversed(path.parents), path]:
        try:
            info = part.lstat()
        except FileNotFoundError:
            continue
        if stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 0x400:
            raise WorldError('Refusing symlink/junction/reparse path %s' % part)
    return path


def _same_path(first, second):
    return os.path.normcase(os.path.abspath(first)) == os.path.normcase(os.path.abspath(second))


def _sync_dir(path):
    # Windows does not expose directory fsync through Python. File contents are fsynced;
    # rename durability across a machine power failure still depends on the filesystem.
    if os.name != 'nt':
        fd = os.open(str(path), os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)


def _atomic_write(path, data):
    path = _safe_path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    _safe_path(path.parent)
    temp = path.parent / ('.%s.%s.tmp' % (path.name, uuid.uuid4().hex))
    try:
        with open(temp, 'xb') as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        _safe_path(path)
        _safe_path(temp)
        os.replace(temp, path)
        _sync_dir(path.parent)
    finally:
        if temp.exists():
            _safe_path(temp).unlink()


def _atomic_json(path, data):
    _atomic_write(path, (json.dumps(data, indent=2) + '\n').encode('utf-8'))


@contextmanager
def _world_lock(world):
    """OS lock, released on process exit; a stale file does not block crash recovery."""
    path = _safe_path(world / LOCK)
    with open(path, 'a+b') as handle:
        if path.stat().st_nlink != 1:
            raise WorldError('Refusing hard-linked lock file')
        if path.stat().st_size == 0:
            handle.write(b'0')
            handle.flush()
        handle.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            raise WorldError('Another world-merge operation holds the candidate lock') from exc
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == 'nt':
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def _rollback_root(world):
    return _safe_path(world.parent / ('%s.rollback' % world.name))


def _candidate(world):
    world = _safe_path(world)
    marker = _safe_path(world / MARKER)
    if not marker.is_file():
        raise WorldError('%s is not a candidate made by copy-world; refusing to edit it' % world)
    manifest = json.loads(marker.read_text(encoding='utf-8'))
    if manifest.get('schema') != 2 or not re.fullmatch(r'[0-9a-f]{32}', str(manifest.get('candidate_id', ''))):
        raise WorldError('Legacy/invalid candidate marker; make a new verified copy-world candidate')
    if not isinstance(manifest.get('candidate'), str) or not _same_path(manifest['candidate'], world):
        raise WorldError('Candidate marker belongs to another directory')
    source = _safe_path(manifest.get('source', ''))
    if not Path(manifest.get('source', '')).is_absolute() or _is_within(world, source) or _is_within(source, world):
        raise WorldError('Candidate/source isolation is invalid')
    rollback_root = _rollback_root(world)
    if _is_within(rollback_root, source) or _is_within(source, rollback_root):
        raise WorldError('Rollback directory overlaps the original source')
    if not isinstance(manifest.get('pastes'), list):
        raise WorldError('Invalid candidate paste history')
    return world, manifest


def _region_path(world, relative):
    if not isinstance(relative, str) or not re.fullmatch(r'(?:region|DIM-1/region|DIM1/region)/r\.-?\d+\.-?\d+\.mca', relative):
        raise WorldError('Invalid recorded region path %r' % relative)
    path = _safe_path(world / relative)
    if not _is_within(path, world):
        raise WorldError('Region escapes its candidate')
    return path


def _transaction_dir(world, record):
    number, identity = record.get('paste'), record.get('transaction_id')
    if not isinstance(number, int) or isinstance(number, bool) or number < 1 or not re.fullmatch(r'[0-9a-f]{32}', str(identity)):
        raise WorldError('Invalid transaction identity')
    return _safe_path(_rollback_root(world) / ('%d-%s' % (number, identity)))


def _hash_or_none(path):
    path = _safe_path(path)
    if not path.exists():
        return None
    if not path.is_file():
        raise WorldError('Expected a regular file: %s' % path)
    return sha256_file(path)


# World copy ----------------------------------------------------------------------------------
def _is_within(child, parent):
    try:
        child.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def copy_world(source, dest):
    source, dest = Path(source), Path(dest)
    if dest.exists():
        raise FileExistsError('Destination %s already exists; candidates are always new' % dest)
    source, dest = _safe_path(source), _safe_path(dest)
    if not (source / 'level.dat').is_file():
        raise WorldError('%s is not a world directory (no level.dat)' % source)
    if _is_within(dest, source) or _is_within(source, dest):
        raise WorldError('Source and destination must not contain each other')
    rollback_root = _rollback_root(dest)
    if _is_within(rollback_root, source) or _is_within(source, rollback_root):
        raise WorldError('Rollback directory must not overlap the source')
    files = []
    for directory, dirs, names in os.walk(source, followlinks=False):
        for name in dirs:
            _safe_path(Path(directory) / name)
        for name in names:
            path = _safe_path(Path(directory) / name)
            if not stat.S_ISREG(path.stat().st_mode):
                raise WorldError('Refusing non-regular source file %s' % path)
            files.append(path)
    dest.mkdir(parents=True)
    rows = []
    for path in sorted(files):
        rel = path.relative_to(source)
        if len(rel.parts) == 1 and rel.name in (MARKER, LOCK):
            continue
        target = _safe_path(dest / rel)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        src_hash, dst_hash = sha256_file(path), sha256_file(target)
        if src_hash != dst_hash:
            raise WorldError('Copy verification failed for %s' % rel)
        rows.append({'path': rel.as_posix(), 'bytes': target.stat().st_size, 'sha256': src_hash})
    manifest = {'schema': 2, 'candidate_id': uuid.uuid4().hex, 'candidate': str(dest),
                'source': str(source), 'files': len(rows),
                'bytes': sum(r['bytes'] for r in rows),
                'manifest_sha256': hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest(),
                'created_unix': int(time.time()), 'pastes': [], 'pending': None,
                'source_consistency': 'per-file hash verification; source must be offline/quiescent'}
    _atomic_json(dest / MARKER, manifest)
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
        with open(_safe_path(self.path), 'rb') as handle:
            raw = handle.read(MAX_REGION_BYTES + 1)
        if len(raw) > MAX_REGION_BYTES:
            raise WorldError('Region exceeds the %d-byte offline safety budget' % MAX_REGION_BYTES)
        if not raw:
            return
        if len(raw) < 2 * SECTOR:
            raise WorldError('%s is shorter than a region header' % self.path.name)
        locations = struct.unpack('>1024I', raw[:SECTOR])
        self.timestamps = list(struct.unpack('>1024I', raw[SECTOR:2 * SECTOR]))
        occupied = {0, 1}
        for slot, loc in enumerate(locations):
            offset, count = loc >> 8, loc & 0xFF
            if not loc:
                continue
            start = offset * SECTOR
            if offset < 2 or start + 5 > len(raw):
                raise WorldError('%s slot %d points outside the file' % (self.path.name, slot))
            length = struct.unpack('>I', raw[start:start + 4])[0]
            if length < 1 or start + 4 + length > len(raw) or length + 4 > count * SECTOR:
                raise WorldError('%s slot %d has an invalid length' % (self.path.name, slot))
            sectors = set(range(offset, offset + count))
            if occupied.intersection(sectors):
                raise WorldError('%s slot %d overlaps another chunk/header' % (self.path.name, slot))
            occupied.update(sectors)
            self.chunks[slot] = (raw[start + 4], raw[start + 5:start + 4 + length])

    def load_chunk(self, slot):
        if slot not in self.chunks:
            return None
        comp, payload = self.chunks[slot]
        if comp not in (1, 2):
            raise WorldError('Unsupported chunk compression %d' % comp)
        if comp == 1 and not payload.startswith(b'\x1f\x8b'):
            raise WorldError('Chunk gzip header disagrees with region compression')
        if comp == 2 and not (len(payload) >= 2 and payload[0] & 0x0F == 8 and payload[0] >> 4 <= 7 and ((payload[0] << 8) | payload[1]) % 31 == 0):
            raise WorldError('Chunk zlib header disagrees with region compression')
        return nbt.loads(payload, max_bytes=MAX_CHUNK_NBT_BYTES)

    def store_chunk(self, slot, name, root):
        raw = nbt.dumps(root, name, compression=None)
        if len(raw) > MAX_CHUNK_NBT_BYTES:
            raise WorldError('Edited chunk exceeds the offline NBT safety budget')
        self.chunks[slot] = (2, zlib.compress(raw))
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
            if sector * SECTOR > MAX_REGION_BYTES:
                raise WorldError('Edited region exceeds the offline safety budget')
        out.write(struct.pack('>1024I', *locations))
        out.write(struct.pack('>1024I', *self.timestamps))
        out.write(body.getvalue())
        return out.getvalue()

    def save_atomic(self):
        _atomic_write(self.path, self.to_bytes())


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
    def replaced(x, y, z):
        if not inside(x, y, z):
            return False
        if clear_air:
            return True
        return schem.block_id(schem.index(x - mx, y - my, z - mz)) != 0
    removed_tiles = 0
    tiles = level.get('TileEntities')
    if tiles is None:
        tiles = level.value['TileEntities'] = nbt.Tag(nbt.LIST, [], nbt.COMPOUND)
    kept = []
    for t in tiles.value:
        pos = tuple(t.scalar(a) for a in 'xyz')
        if replaced(*pos):
            removed_tiles += 1
        else:
            kept.append(t)
    added = [t for t in tile_tags_world if replaced(*t[0])]
    kept.extend(tag for _, tag in added)
    tiles.value = kept
    tiles.elem = nbt.COMPOUND
    ticks = level.get('TileTicks')
    dropped_ticks = 0
    if ticks is not None:
        keep = [t for t in ticks.value if not replaced(*(t.scalar(a) for a in 'xyz'))]
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
        add = sec['Add'].value if 'Add' in sec else None
        for ci in range(4095, -1, -1):
            # Conservative occupancy height only. Exact mod/native light opacity needs a game load.
            if blocks[ci] or (add is not None and _nibble_get(add, ci)):
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


def _begin_transaction(world, manifest, number, kind):
    record = {'schema': 2, 'candidate_id': manifest['candidate_id'], 'paste': number,
              'transaction_id': uuid.uuid4().hex, 'kind': kind, 'status': 'prepared',
              'regions': [], 'unix': int(time.time())}
    folder = _transaction_dir(world, record)
    folder.mkdir(parents=True, exist_ok=False)
    record['rollback_dir'] = str(folder)
    return record


def _stage_region(world, record, relative, payload, expected_before, chunks=0):
    path = _region_path(world, relative)
    before = path.read_bytes() if path.exists() else None
    before_hash = hashlib.sha256(before).hexdigest() if before is not None else None
    if before_hash != expected_before:
        raise WorldError('Candidate changed during staging: %s' % relative)
    folder = _transaction_dir(world, record)
    if before is not None:
        _atomic_write(folder / 'before' / relative, before)
    after_hash = None
    if payload is not None:
        _atomic_write(folder / 'after' / relative, payload)
        after_hash = hashlib.sha256(payload).hexdigest()
    record['regions'].append({'file': relative, 'sha256_before': before_hash,
                              'sha256_after': after_hash, 'chunks': chunks})


def _validate_record(world, manifest, record, require_after=True):
    if record.get('schema') != 2 or record.get('candidate_id') != manifest['candidate_id'] or record.get('kind') not in ('paste', 'rollback'):
        raise WorldError('Invalid transaction provenance')
    folder = _transaction_dir(world, record)
    if not isinstance(record.get('rollback_dir'), str) or not _same_path(record['rollback_dir'], folder):
        raise WorldError('Transaction snapshot directory escapes its candidate')
    regions = record.get('regions')
    if not isinstance(regions, list) or len(regions) > 1024:
        raise WorldError('Invalid region inventory')
    seen = set()
    for reg in regions:
        relative = reg.get('file')
        _region_path(world, relative)
        if relative in seen:
            raise WorldError('Duplicate transaction region')
        seen.add(relative)
        for role, field in (('before', 'sha256_before'), ('after', 'sha256_after')):
            digest = reg.get(field)
            if digest is None:
                continue
            if not isinstance(digest, str) or not re.fullmatch(r'[0-9a-f]{64}', digest):
                raise WorldError('Invalid snapshot hash')
            if role == 'after' and not require_after:
                continue
            saved = _safe_path(folder / role / relative)
            if not saved.is_file() or sha256_file(saved) != digest:
                raise WorldError('Rollback/staging snapshot is damaged: %s' % saved)
    return folder


def _write_region(world, relative, payload):
    path = _region_path(world, relative)
    if payload is None:
        if path.exists():
            path.unlink()
            _sync_dir(path.parent)
    else:
        _atomic_write(path, payload)


def _restore_transaction(world, manifest, record):
    folder = _validate_record(world, manifest, record, require_after=False)
    # Preflight every file before restoring the first. A live game/user edit is never
    # silently overwritten; both before and after states permit idempotent crash recovery.
    for reg in record['regions']:
        if _hash_or_none(_region_path(world, reg['file'])) not in (reg['sha256_before'], reg['sha256_after']):
            raise WorldError('Candidate changed outside its pending transaction: %s' % reg['file'])
    for reg in reversed(record['regions']):
        data = None if reg['sha256_before'] is None else (folder / 'before' / reg['file']).read_bytes()
        _write_region(world, reg['file'], data)


def _load_pending(world, manifest):
    pending = manifest.get('pending')
    if not pending:
        return None
    folder = _transaction_dir(world, pending)
    record = json.loads(_safe_path(folder / 'PASTE-RECEIPT.json').read_text(encoding='utf-8'))
    if record.get('transaction_id') != pending.get('transaction_id') or record.get('paste') != pending.get('paste'):
        raise WorldError('Pending journal identity mismatch')
    _validate_record(world, manifest, record, require_after=False)
    return record


def _recover_pending_locked(world, manifest):
    record = _load_pending(world, manifest)
    if record is None:
        return None
    _restore_transaction(world, manifest, record)
    manifest['pending'] = None
    manifest['last_operation'] = {'transaction_id': record['transaction_id'], 'status': 'recovered_rollback'}
    _atomic_json(world / MARKER, manifest)
    return record


def recover_pending(world_dir):
    """Idempotently undo an interrupted command using its durable pre-mutation journal."""
    world, _ = _candidate(world_dir)
    with _world_lock(world):
        world, manifest = _candidate(world)
        record = _recover_pending_locked(world, manifest)
        return {'recovered': record is not None, 'transaction_id': record['transaction_id'] if record else None}


def _commit_transaction(world, manifest, record, pastes_after):
    folder = _validate_record(world, manifest, record)
    for reg in record['regions']:
        if _hash_or_none(_region_path(world, reg['file'])) != reg['sha256_before']:
            raise WorldError('Candidate changed before commit: %s' % reg['file'])
    # Every backup/output is complete and hashed before either journal or world is mutated.
    _atomic_json(folder / 'PASTE-RECEIPT.json', record)
    prior = dict(manifest)
    manifest['pending'] = {'paste': record['paste'], 'transaction_id': record['transaction_id']}
    _atomic_json(world / MARKER, manifest)
    try:
        for reg in record['regions']:
            payload = None if reg['sha256_after'] is None else (folder / 'after' / reg['file']).read_bytes()
            _write_region(world, reg['file'], payload)
            if _hash_or_none(_region_path(world, reg['file'])) != reg['sha256_after']:
                raise WorldError('Committed region hash mismatch: %s' % reg['file'])
        manifest['pastes'] = pastes_after
        manifest['pending'] = None
        manifest['last_operation'] = {'transaction_id': record['transaction_id'], 'status': 'committed'}
        _atomic_json(world / MARKER, manifest)
    except Exception as original:
        try:
            _restore_transaction(world, prior, record)
            _atomic_json(world / MARKER, prior)
        except Exception as recovery:
            raise WorldError('World transaction failed; recovery remains pending in %s: %s' % (folder, recovery)) from original
        raise
    # KeyboardInterrupt/process death intentionally leaves pending journal for the next call.


def paste_into_world_copy(schem, world_dir, world_min, source_path=None, clear_air=True,
                          dimension='overworld'):
    world, _ = _candidate(world_dir)
    with _world_lock(world):
        world, manifest = _candidate(world)
        _recover_pending_locked(world, manifest)
        mx, my, mz = world_min
        if my < 0 or my + schem.height - 1 > 255:
            raise WorldError('Paste Y range %d..%d is outside 0..255' % (my, my + schem.height - 1))
        if min(mx, mz) < -29999999 or max(mx + schem.width - 1, mz + schem.length - 1) > 29999999:
            raise WorldError('Paste exceeds Minecraft world coordinate bounds')
        if dimension not in DIMENSION_DIRS:
            raise WorldError('Unknown dimension %r' % dimension)
        region_dir = _safe_path(world / DIMENSION_DIRS[dimension])
        source_hash = sha256_file(_safe_path(source_path)) if source_path else None
        tiles = _world_tiles(schem, world_min)
        first_x, last_x = mx >> 4, (mx + schem.width - 1) >> 4
        first_z, last_z = mz >> 4, (mz + schem.length - 1) >> 4
        if (last_x - first_x + 1) * (last_z - first_z + 1) > 65536:
            raise WorldError('Paste exceeds the 65536-chunk offline transaction budget')
        chunks = [(cx, cz) for cx in range(first_x, last_x + 1) for cz in range(first_z, last_z + 1)]
        by_region = {}
        for cx, cz in chunks:
            by_region.setdefault((cx >> 5, cz >> 5), []).append((cx, cz))
        if len(by_region) > 1024:
            raise WorldError('Paste exceeds the 1024-region transaction budget')
        number = len(manifest['pastes']) + 1
        record = _begin_transaction(world, manifest, number, 'paste')
        totals = {'cells_changed': 0, 'tiles_removed': 0, 'tiles_added': 0,
                  'ticks_dropped': 0, 'entities_inside_left_untouched': 0, 'chunks_created': 0}
        # All chunks parse and all complete region outputs stage before a single candidate
        # region changes. A late malformed chunk therefore leaves the candidate untouched.
        for (rx, rz), members in sorted(by_region.items()):
            path = _safe_path(region_dir / ('r.%d.%d.mca' % (rx, rz)))
            before = _hash_or_none(path)
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
                for key, value in stats.items():
                    totals[key] += value
                region.store_chunk(slot, name, root)
            _stage_region(world, record, path.relative_to(world).as_posix(), region.to_bytes(), before, len(members))
        record.update({'schematic': Path(source_path).name if source_path else None,
                       'schematic_sha256': source_hash, 'world_min': list(world_min),
                       'world_max': [mx + schem.width - 1, my + schem.height - 1, mz + schem.length - 1],
                       'dimension': dimension, 'clear_air': clear_air, 'totals': totals,
                       'heightmap_scope': 'conservative non-air occupancy including Add; native light opacity/relighting not simulated',
                       'scope': 'offline candidate rewrite; lighting/fluids/mob AI require native verification'})
        completed = dict(record, status='committed')
        _commit_transaction(world, manifest, record, manifest['pastes'] + [completed])
        return completed


def rollback(world_dir, paste_no):
    """Atomically replace each region; a durable undo journal recovers interrupted commands.

    The command restores all selected regions only after every snapshot/current hash validates.
    On an ordinary late failure it restores the whole pre-command candidate. A process crash
    leaves a pending journal, which recover_pending (or the next command) safely undoes.
    """
    world, _ = _candidate(world_dir)
    with _world_lock(world):
        world, manifest = _candidate(world)
        pending = _recover_pending_locked(world, manifest)
        pastes = manifest['pastes']
        if not isinstance(paste_no, int) or isinstance(paste_no, bool) or not 1 <= paste_no <= len(pastes):
            if pending is not None and pending['paste'] == paste_no:
                return {'restored_to_before_paste': paste_no, 'remaining_pastes': len(pastes), 'recovered_pending': True}
            raise WorldError('No paste %r recorded' % paste_no)
        selected = pastes[paste_no - 1:]
        desired, current = {}, {}
        # Earliest selected before-state wins; latest selected after-state guards live edits.
        for record in selected:
            folder = _validate_record(world, manifest, record, require_after=False)
            for reg in record['regions']:
                desired.setdefault(reg['file'], (reg['sha256_before'], folder / 'before' / reg['file']))
                current[reg['file']] = reg['sha256_after']
        for relative, digest in current.items():
            if _hash_or_none(_region_path(world, relative)) != digest:
                raise WorldError('Candidate changed since paste; refusing rollback over edits: %s' % relative)
        undo = _begin_transaction(world, manifest, paste_no, 'rollback')
        for relative in sorted(desired):
            digest, saved = desired[relative]
            _stage_region(world, undo, relative, None if digest is None else saved.read_bytes(), current[relative])
        _commit_transaction(world, manifest, undo, pastes[:paste_no - 1])
        return {'restored_to_before_paste': paste_no, 'remaining_pastes': paste_no - 1}


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
