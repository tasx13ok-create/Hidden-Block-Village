"""Bounded, dependency-free reader/writer for Minecraft Java NBT (as used by 1.12 schematics/regions).

The reader is defensive: schematics and world files come from outside the repository and may be
truncated or hostile. Limits on decompressed size, nesting depth, list/array lengths and string
lengths prevent memory exhaustion; any violation raises ``NBTError`` instead of allocating.

Values are represented as ``Tag`` objects so that a load/save round trip preserves the exact tag
types (an ``int`` stored as TAG_Short stays a short) and the original key order.
"""
import gzip
import io
import struct
import zlib

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
TYPE_NAMES = {END: 'end', BYTE: 'byte', SHORT: 'short', INT: 'int', LONG: 'long', FLOAT: 'float',
              DOUBLE: 'double', BYTE_ARRAY: 'byte_array', STRING: 'string', LIST: 'list',
              COMPOUND: 'compound', INT_ARRAY: 'int_array', LONG_ARRAY: 'long_array'}
_SCALAR = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}

DEFAULT_MAX_BYTES = 512 * 1024 * 1024
MAX_DEPTH = 512


class NBTError(ValueError):
    """Malformed, truncated or over-limit NBT input."""


class Tag:
    __slots__ = ('type', 'value', 'elem')

    def __init__(self, tag_type, value, elem=None):
        self.type = tag_type
        self.value = value
        self.elem = elem  # element type for TAG_List

    def __repr__(self):
        return 'Tag(%s, %r)' % (TYPE_NAMES.get(self.type, self.type), self.value)

    def __eq__(self, other):
        return (isinstance(other, Tag) and self.type == other.type and self.value == other.value
                and (self.type != LIST or self.elem == other.elem or not self.value))

    # Compound convenience helpers -------------------------------------------------------------
    def get(self, key, default=None):
        if self.type != COMPOUND:
            raise NBTError('get() on non-compound tag')
        return self.value.get(key, default)

    def __getitem__(self, key):
        return self.value[key]

    def __contains__(self, key):
        return self.type == COMPOUND and key in self.value

    def scalar(self, key, default=None):
        tag = self.get(key)
        if tag is None:
            return default
        if tag.type not in _SCALAR and tag.type != STRING:
            raise NBTError('Tag %s is not a scalar' % key)
        return tag.value


def compound(items=None):
    return Tag(COMPOUND, dict(items or {}))


def to_plain(tag):
    """Convert to plain Python (for JSON reports). Byte arrays become their length."""
    if tag.type == COMPOUND:
        return {k: to_plain(v) for k, v in tag.value.items()}
    if tag.type == LIST:
        return [to_plain(v) for v in tag.value]
    if tag.type == BYTE_ARRAY:
        return {'byte_array_length': len(tag.value)}
    if tag.type in (INT_ARRAY, LONG_ARRAY):
        return list(tag.value)
    return tag.value


class _Reader:
    def __init__(self, data, max_bytes):
        self.data = data
        self.pos = 0
        self.max_bytes = max_bytes

    def take(self, count):
        if count < 0 or self.pos + count > len(self.data):
            raise NBTError('Truncated NBT at offset %d (wanted %d bytes)' % (self.pos, count))
        chunk = self.data[self.pos:self.pos + count]
        self.pos += count
        return chunk

    def unpack(self, fmt, size):
        return struct.unpack(fmt, self.take(size))[0]

    def length(self, item_size):
        count = self.unpack('>i', 4)
        if count < 0:
            raise NBTError('Negative length %d at offset %d' % (count, self.pos - 4))
        if count * item_size > len(self.data) - self.pos:
            raise NBTError('Length %d exceeds remaining input at offset %d' % (count, self.pos - 4))
        return count

    def string(self):
        size = self.unpack('>H', 2)
        raw = self.take(size)
        try:
            # Java "modified UTF-8"; standard decoding covers everything except the rare
            # encoded NUL / surrogate pairs, which we keep losslessly via surrogatepass.
            return raw.decode('utf-8', 'surrogatepass')
        except UnicodeDecodeError:
            raise NBTError('Invalid string encoding at offset %d' % (self.pos - size))

    def payload(self, tag_type, depth):
        if depth > MAX_DEPTH:
            raise NBTError('NBT nesting exceeds %d' % MAX_DEPTH)
        if tag_type in _SCALAR:
            fmt = _SCALAR[tag_type]
            return Tag(tag_type, self.unpack(fmt, struct.calcsize(fmt)))
        if tag_type == BYTE_ARRAY:
            return Tag(tag_type, bytearray(self.take(self.length(1))))
        if tag_type == STRING:
            return Tag(tag_type, self.string())
        if tag_type == INT_ARRAY:
            n = self.length(4)
            return Tag(tag_type, list(struct.unpack('>%di' % n, self.take(4 * n))))
        if tag_type == LONG_ARRAY:
            n = self.length(8)
            return Tag(tag_type, list(struct.unpack('>%dq' % n, self.take(8 * n))))
        if tag_type == LIST:
            elem = self.unpack('>b', 1)
            if elem < 0 or elem > LONG_ARRAY:
                raise NBTError('Invalid list element type %d' % elem)
            n = self.length(1 if elem != END else 0)
            if elem == END and n:
                raise NBTError('Non-empty list of TAG_End')
            return Tag(LIST, [self.payload(elem, depth + 1) for _ in range(n)], elem)
        if tag_type == COMPOUND:
            items = {}
            while True:
                child = self.unpack('>b', 1)
                if child == END:
                    return Tag(COMPOUND, items)
                if child < 0 or child > LONG_ARRAY:
                    raise NBTError('Invalid tag type %d at offset %d' % (child, self.pos - 1))
                name = self.string()
                if name in items:
                    raise NBTError('Duplicate compound key %r' % name)
                items[name] = self.payload(child, depth + 1)
        raise NBTError('Invalid tag type %d' % tag_type)


def _decompress(raw, max_bytes):
    if raw[:2] == b'\x1f\x8b':
        stream = gzip.GzipFile(fileobj=io.BytesIO(raw))
    elif raw[:1] == b'\x78':
        stream = None
        decomp = zlib.decompressobj()
        try:
            out = decomp.decompress(raw, max_bytes + 1)
        except zlib.error as exc:
            raise NBTError('Corrupt zlib stream: %s' % exc)
        if len(out) > max_bytes:
            raise NBTError('Decompressed NBT exceeds %d bytes' % max_bytes)
        return out
    else:
        return raw
    try:
        out = stream.read(max_bytes + 1)
    except (OSError, EOFError, zlib.error) as exc:
        raise NBTError('Corrupt gzip stream: %s' % exc)
    if len(out) > max_bytes:
        raise NBTError('Decompressed NBT exceeds %d bytes' % max_bytes)
    return out


def loads(raw, max_bytes=DEFAULT_MAX_BYTES):
    """Parse gzip/zlib/uncompressed NBT bytes. Returns (root_name, root_tag)."""
    data = _decompress(bytes(raw), max_bytes)
    reader = _Reader(data, max_bytes)
    root_type = reader.unpack('>b', 1)
    if root_type != COMPOUND:
        raise NBTError('Root tag must be a compound, got type %d' % root_type)
    name = reader.string()
    root = reader.payload(COMPOUND, 0)
    if reader.pos != len(data):
        raise NBTError('Trailing %d bytes after root tag' % (len(data) - reader.pos))
    return name, root


def load(path, max_bytes=DEFAULT_MAX_BYTES):
    with open(path, 'rb') as handle:
        raw = handle.read(max_bytes + 1)
    if len(raw) > max_bytes:
        raise NBTError('Input file exceeds %d bytes' % max_bytes)
    return loads(raw, max_bytes)


def _write_string(out, text):
    raw = text.encode('utf-8', 'surrogatepass')
    if len(raw) > 0xFFFF:
        raise NBTError('String too long for NBT')
    out.write(struct.pack('>H', len(raw)))
    out.write(raw)


def _write_payload(out, tag):
    t = tag.type
    if t in _SCALAR:
        out.write(struct.pack(_SCALAR[t], tag.value))
    elif t == BYTE_ARRAY:
        out.write(struct.pack('>i', len(tag.value)))
        out.write(bytes(tag.value))
    elif t == STRING:
        _write_string(out, tag.value)
    elif t == INT_ARRAY:
        out.write(struct.pack('>i%di' % len(tag.value), len(tag.value), *tag.value))
    elif t == LONG_ARRAY:
        out.write(struct.pack('>i%dq' % len(tag.value), len(tag.value), *tag.value))
    elif t == LIST:
        elem = tag.elem if tag.elem is not None else (tag.value[0].type if tag.value else END)
        for item in tag.value:
            if item.type != elem:
                raise NBTError('Mixed list element types')
        out.write(struct.pack('>bi', elem, len(tag.value)))
        for item in tag.value:
            _write_payload(out, item)
    elif t == COMPOUND:
        for key, child in tag.value.items():
            out.write(struct.pack('>b', child.type))
            _write_string(out, key)
            _write_payload(out, child)
        out.write(b'\x00')
    else:
        raise NBTError('Cannot write tag type %r' % t)


def dumps(root, name='', compression='gzip'):
    out = io.BytesIO()
    out.write(b'\x0a')
    _write_string(out, name)
    _write_payload(out, root)
    data = out.getvalue()
    if compression == 'gzip':
        # mtime=0 keeps output bytes reproducible for hash-based evidence.
        buf = io.BytesIO()
        with gzip.GzipFile(fileobj=buf, mode='wb', mtime=0) as gz:
            gz.write(data)
        return buf.getvalue()
    if compression == 'zlib':
        return zlib.compress(data)
    if compression is None:
        return data
    raise ValueError('Unknown compression %r' % compression)
