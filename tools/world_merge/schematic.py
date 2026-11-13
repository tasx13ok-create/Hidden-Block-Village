"""MCEdit/WorldEdit 6 ``.schematic`` (Materials=Alpha) model with lossless save.

Index order is ``(y * Length + z) * Width + x``. Block IDs are 12-bit: the low byte is in
``Blocks`` and the optional nibble-packed ``AddBlocks`` holds the high four bits, using
WorldEdit's layout (even index -> low nibble, odd index -> high nibble of ``AddBlocks[i >> 1]``).

WorldEdit metadata (``WEOrigin*`` = the clipboard region's minimum corner when copied;
``WEOffset*`` = minimum corner minus the player's copy position) is surfaced unchanged; the
placement module turns it into explicit paste bounds.
"""
import hashlib
from pathlib import Path

from . import nbt

MAX_CELLS = 64 * 1024 * 1024
_LO_NIBBLE = bytes(i & 0x0F for i in range(256))
_HI_NIBBLE = bytes(i >> 4 for i in range(256))


class SchematicError(ValueError):
    """The file is not a usable 1.12 schematic."""


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b''):
            digest.update(chunk)
    return digest.hexdigest()


def expand_add_blocks(add, cells):
    """Return one high-nibble byte per cell (bytearray), or None if every value is zero."""
    needed = (cells + 1) // 2
    if len(add) < needed:
        raise SchematicError('AddBlocks has %d bytes; %d cells need at least %d' % (len(add), cells, needed))
    packed = bytes(add[:needed])
    if not packed.strip(b'\x00'):
        return None
    out = bytearray(needed * 2)
    out[0::2] = packed.translate(_LO_NIBBLE)
    out[1::2] = packed.translate(_HI_NIBBLE)
    del out[cells:]
    return out


def pack_add_blocks(high, cells):
    # WorldEdit writes (cells >> 1) + 1 bytes; the trailing byte is padding for even counts.
    padded = bytes(high) + b'\x00' * (2 - cells % 2)
    lo, hi = padded[0::2], padded[1::2]
    return bytearray((a & 0x0F) | ((b & 0x0F) << 4) for a, b in zip(lo, hi))


class Schematic:
    def __init__(self, root, root_name='Schematic', source=None):
        self.root = root
        self.root_name = root_name
        self.source = source
        self.warnings = []
        self._parse()

    # Loading ----------------------------------------------------------------------------------
    @classmethod
    def load(cls, path, max_bytes=nbt.DEFAULT_MAX_BYTES):
        path = Path(path)
        try:
            name, root = nbt.load(path, max_bytes)
        except nbt.NBTError as exc:
            raise SchematicError('%s: %s' % (path.name, exc))
        return cls(root, name, source=path)

    def _int(self, key, types=(nbt.SHORT, nbt.INT)):
        tag = self.root.get(key)
        if tag is None:
            raise SchematicError('Missing %s' % key)
        if tag.type not in types:
            raise SchematicError('%s has unexpected tag type %s' % (key, nbt.TYPE_NAMES.get(tag.type)))
        return tag.value

    def _parse(self):
        root = self.root
        if 'BlockData' in root and 'Palette' in root:
            raise SchematicError('Sponge .schem format is not supported by this 1.12 tool')
        materials = root.get('Materials')
        if materials is not None and materials.value != 'Alpha':
            raise SchematicError('Unsupported Materials %r (expected Alpha)' % materials.value)
        if materials is None:
            self.warnings.append('Materials tag missing; assuming Alpha numeric IDs')
        w, h, l = (self._int(k) for k in ('Width', 'Height', 'Length'))
        # Short dimensions above 32767 are stored negative by Java writers; reject them.
        if min(w, h, l) <= 0:
            raise SchematicError('Non-positive dimensions %dx%dx%d' % (w, h, l))
        cells = w * h * l
        if cells > MAX_CELLS:
            raise SchematicError('Schematic has %d cells, above the %d limit' % (cells, MAX_CELLS))
        self.width, self.height, self.length, self.cells = w, h, l, cells
        blocks = root.get('Blocks')
        data = root.get('Data')
        for key, tag in (('Blocks', blocks), ('Data', data)):
            if tag is None or tag.type != nbt.BYTE_ARRAY:
                raise SchematicError('Missing byte array %s' % key)
            if len(tag.value) != cells:
                raise SchematicError('%s length %d != %d cells' % (key, len(tag.value), cells))
        self.blocks = blocks.value
        self.data = data.value
        add = root.get('AddBlocks')
        if add is not None and add.type != nbt.BYTE_ARRAY:
            raise SchematicError('AddBlocks is not a byte array')
        self.add = expand_add_blocks(add.value, cells) if add is not None else None
        if 'Add' in root and add is None:
            self.warnings.append('Legacy "Add" tag present but ignored; WorldEdit uses AddBlocks')
        self.tile_tags = self._list('TileEntities')
        self.entity_tags = self._list('Entities')
        self.we_origin = self._vector('WEOrigin')
        self.we_offset = self._vector('WEOffset')

    def _list(self, key):
        tag = self.root.get(key)
        if tag is None:
            return []
        if tag.type != nbt.LIST or (tag.value and tag.elem != nbt.COMPOUND):
            raise SchematicError('%s must be a list of compounds' % key)
        return tag.value

    def _vector(self, prefix):
        keys = [prefix + axis for axis in 'XYZ']
        present = [k in self.root for k in keys]
        if not any(present):
            return None
        if not all(present):
            self.warnings.append('Incomplete %s metadata ignored' % prefix)
            return None
        return tuple(self._int(k, (nbt.INT, nbt.SHORT)) for k in keys)

    # Cell access ------------------------------------------------------------------------------
    def index(self, x, y, z):
        if not (0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length):
            raise IndexError('(%d,%d,%d) outside %dx%dx%d' % (x, y, z, self.width, self.height, self.length))
        return (y * self.length + z) * self.width + x

    def coords(self, i):
        x = i % self.width
        rest = i // self.width
        return x, rest // self.length, rest % self.length

    def in_bounds(self, x, y, z):
        return 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length

    def block_id(self, i):
        high = self.add[i] if self.add is not None else 0
        return (high << 8) | self.blocks[i]

    def get(self, x, y, z):
        i = self.index(x, y, z)
        return self.block_id(i), self.data[i]

    def set_block(self, x, y, z, block, data=0):
        if not (0 <= block <= 0xFFF) or not (0 <= data <= 15):
            raise SchematicError('Block %r:%r out of 12-bit id / 4-bit data range' % (block, data))
        i = self.index(x, y, z)
        if block > 255 and self.add is None:
            self.add = bytearray(self.cells)
        if self.add is not None:
            self.add[i] = block >> 8
        self.blocks[i] = block & 0xFF
        self.data[i] = data

    def tile_at(self):
        """Map (x,y,z) -> list of tile compounds (lists expose duplicates)."""
        found = {}
        for tag in self.tile_tags:
            pos = tuple(tag.scalar(k) if k in tag else None for k in 'xyz')
            found.setdefault(pos, []).append(tag)
        return found

    def extended_cells(self):
        return 0 if self.add is None else self.cells - self.add.count(0)

    # Saving -----------------------------------------------------------------------------------
    def to_bytes(self):
        root = self.root
        root.value['Blocks'] = nbt.Tag(nbt.BYTE_ARRAY, self.blocks)
        root.value['Data'] = nbt.Tag(nbt.BYTE_ARRAY, self.data)
        # With no extended IDs an authored (all-zero) AddBlocks array is preserved byte-for-byte.
        if self.add is not None:
            root.value['AddBlocks'] = nbt.Tag(nbt.BYTE_ARRAY, pack_add_blocks(self.add, self.cells))
        return nbt.dumps(root, self.root_name)

    def save_new(self, path):
        """Write to a path that must not already exist (never overwrites inputs)."""
        path = Path(path)
        if path.exists():
            raise FileExistsError('Refusing to overwrite existing %s' % path)
        if self.source is not None and path.resolve() == Path(self.source).resolve():
            raise FileExistsError('Refusing to write over the input schematic')
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = self.to_bytes()
        with open(path, 'xb') as handle:
            handle.write(payload)
        return hashlib.sha256(payload).hexdigest()


def new_schematic(width, height, length, origin=None, offset=None):
    """Create an empty in-memory schematic (used by fixtures and synthetic candidates)."""
    cells = width * height * length
    items = {
        'Width': nbt.Tag(nbt.SHORT, width), 'Height': nbt.Tag(nbt.SHORT, height),
        'Length': nbt.Tag(nbt.SHORT, length), 'Materials': nbt.Tag(nbt.STRING, 'Alpha'),
        'Blocks': nbt.Tag(nbt.BYTE_ARRAY, bytearray(cells)),
        'Data': nbt.Tag(nbt.BYTE_ARRAY, bytearray(cells)),
        'Entities': nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
        'TileEntities': nbt.Tag(nbt.LIST, [], nbt.COMPOUND),
    }
    for prefix, vec in (('WEOrigin', origin), ('WEOffset', offset)):
        if vec is not None:
            for axis, value in zip('XYZ', vec):
                items[prefix + axis] = nbt.Tag(nbt.INT, value)
    return Schematic(nbt.compound(items))


def add_tile(schem, tile_id, x, y, z, extra=None):
    items = {'id': nbt.Tag(nbt.STRING, tile_id), 'x': nbt.Tag(nbt.INT, x),
             'y': nbt.Tag(nbt.INT, y), 'z': nbt.Tag(nbt.INT, z)}
    items.update(extra or {})
    tag = nbt.compound(items)
    schem.tile_tags.append(tag)
    schem.root.value['TileEntities'] = nbt.Tag(nbt.LIST, schem.tile_tags, nbt.COMPOUND)
    return tag
