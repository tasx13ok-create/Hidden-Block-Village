"""Bounded typed SNBT and binary NBT readers/writers for original-data preservation."""
import gzip
import hashlib
import io
import json
import math
import re
import struct

MAX_BYTES = 16 * 1024**2
MAX_DEPTH = 64
MAX_NODES = 100000
KINDS = ('end', 'byte', 'short', 'int', 'long', 'float', 'double', 'byte_array', 'string', 'list', 'compound', 'int_array', 'long_array')
SUFFIX = {'b': 'byte', 's': 'short', 'l': 'long', 'f': 'float', 'd': 'double'}
NUMBERS = {'byte': ('b', -128, 127), 'short': ('h', -32768, 32767), 'int': ('i', -2**31, 2**31-1), 'long': ('q', -2**63, 2**63-1), 'float': ('f', None, None), 'double': ('d', None, None)}


class FormatError(ValueError):
    pass


def check(ok, message):
    if not ok:
        raise FormatError(message)


def tag(kind, value):
    return {'type': kind, 'value': value}


def semantic_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')).hexdigest()


def scalar(value):
    return value['value'] if value and value['type'] not in ('compound', 'list') else None


def fields(value):
    return value['value'] if value['type'] == 'compound' else {}


def numeric(kind, value):
    if kind in ('float', 'double'):
        check(math.isfinite(value), 'Nonfinite NBT numbers are unsupported')
        if kind == 'float':
            value = struct.unpack('>f', struct.pack('>f', value))[0]
        return tag(kind, value)
    limits = NUMBERS[kind]
    check(limits[1] <= value <= limits[2], 'NBT integer is outside its declared type')
    return tag(kind, value)


class SnbtReader:
    def __init__(self, text):
        check(len(text.encode('utf-8')) <= MAX_BYTES, 'SNBT size limit exceeded')
        self.text, self.pos, self.nodes = text, 0, 0

    def ws(self):
        while self.pos < len(self.text) and self.text[self.pos].isspace():
            self.pos += 1

    def peek(self):
        self.ws()
        return self.text[self.pos:self.pos+1]

    def expect(self, text):
        self.ws()
        check(self.text.startswith(text, self.pos), 'Unexpected SNBT token at offset %d' % self.pos)
        self.pos += len(text)

    def string(self):
        self.ws()
        if self.peek() == '"':
            try:
                result, end = json.JSONDecoder().raw_decode(self.text, self.pos)
            except ValueError as error:
                raise FormatError('Invalid quoted SNBT string at offset %d' % self.pos) from error
            check(isinstance(result, str), 'SNBT string expected')
            try:
                result.encode('utf-8')
            except UnicodeError as error:
                raise FormatError('Unpaired Unicode surrogate in SNBT string') from error
            self.pos = end
            return result
        # CustomNPCs export format uses JSON quoted strings; single-quoted SNBT
        # is explicitly unsupported instead of interpreting JavaScript escapes.
        check(self.peek() != "'", 'Single-quoted SNBT strings are unsupported')
        start = self.pos
        while self.pos < len(self.text) and self.text[self.pos] not in ':,{}[] \t\r\n':
            self.pos += 1
        check(self.pos > start, 'SNBT identifier expected')
        return self.text[start:self.pos]

    def read(self, depth=0):
        check(depth <= MAX_DEPTH, 'SNBT nesting limit exceeded')
        self.nodes += 1
        check(self.nodes <= MAX_NODES, 'SNBT node-count limit exceeded')
        first = self.peek()
        if first == '{':
            self.expect('{')
            result = {}
            while self.peek() != '}':
                key = self.string()
                check(key not in result, 'Duplicate compound keys are unsupported')
                self.expect(':')
                result[key] = self.read(depth + 1)
                if self.peek() != ',':
                    break
                self.expect(',')
            self.expect('}')
            return tag('compound', result)
        if first == '[':
            self.expect('[')
            self.ws()
            array = None
            if self.text[self.pos:self.pos+2].upper() in ('B;', 'I;', 'L;'):
                array = {'B': 'byte', 'I': 'int', 'L': 'long'}[self.text[self.pos].upper()]
                self.pos += 2
            result = []
            while self.peek() != ']':
                result.append(self.read(depth + 1))
                if self.peek() != ',':
                    break
                self.expect(',')
            self.expect(']')
            if array:
                check(all(item['type'] == array for item in result), 'Typed array element type differs')
                return tag(array + '_array', [item['value'] for item in result])
            element = result[0]['type'] if result else 'end'
            check(all(item['type'] == element for item in result), 'Heterogeneous NBT lists are unsupported')
            return tag('list', {'element': element, 'items': result})
        if first == '"':
            return tag('string', self.string())
        raw = self.string()
        match = re.fullmatch(r'([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)([bBsSlLfFdD]?)', raw)
        if match:
            digits, suffix = match.groups()
            kind = SUFFIX.get(suffix.lower(), 'double' if any(c in digits for c in '.eE') else 'int')
            try:
                value = float(digits) if kind in ('float', 'double') else int(digits)
                return numeric(kind, value)
            except (ValueError, OverflowError, struct.error) as error:
                raise FormatError('Invalid typed number') from error
        if raw.lower() in ('true', 'false'):
            return tag('byte', int(raw.lower() == 'true'))
        return tag('string', raw)


def read_snbt(data):
    try:
        reader = SnbtReader(data.decode('utf-8-sig'))
        value = reader.read()
        check(not reader.peek(), 'Trailing SNBT data')
        check(value['type'] == 'compound', 'Compound SNBT root required')
        return value
    except UnicodeError as error:
        raise FormatError('SNBT must be UTF-8') from error


def write_snbt(value):
    kind, body = value['type'], value['value']
    if kind == 'compound':
        return '{' + ','.join(json.dumps(key, ensure_ascii=False) + ':' + write_snbt(child) for key, child in sorted(body.items())) + '}'
    if kind == 'string':
        return json.dumps(body, ensure_ascii=False)
    if kind == 'list':
        return '[' + ','.join(write_snbt(child) for child in body['items']) + ']'
    if kind.endswith('_array'):
        base = kind[:-6]
        return '[' + {'byte': 'B', 'int': 'I', 'long': 'L'}[base] + ';' + ','.join(write_snbt(tag(base, item)) for item in body) + ']'
    if kind in NUMBERS:
        return repr(body) + {'byte': 'b', 'short': 's', 'long': 'L', 'float': 'f', 'double': 'd'}.get(kind, '')
    raise FormatError('Unsupported SNBT output type')


class NbtReader:
    def __init__(self, data):
        self.stream, self.nodes = io.BytesIO(data), 0

    def take(self, count):
        check(0 <= count <= MAX_BYTES, 'NBT length limit exceeded')
        result = self.stream.read(count)
        check(len(result) == count, 'Truncated NBT')
        return result

    def number(self, code):
        return struct.unpack('>' + code, self.take(struct.calcsize(code)))[0]

    def string(self):
        # NBT uses Java modified UTF-8. Unsupported surrogate/NUL encodings are
        # rejected by strict UTF-8 rather than silently damaging Unicode data.
        try:
            result = self.take(self.number('H')).decode('utf-8')
            check('\0' not in result, 'NBT NUL strings require a modified UTF-8 adapter')
            return result
        except UnicodeError as error:
            raise FormatError('Modified UTF-8 form requires a dedicated adapter') from error

    def payload(self, kind, depth=0):
        check(depth <= MAX_DEPTH, 'NBT nesting limit exceeded')
        self.nodes += 1
        check(self.nodes <= MAX_NODES, 'NBT node-count limit exceeded')
        if kind in NUMBERS:
            return numeric(kind, self.number(NUMBERS[kind][0]))
        if kind == 'string':
            return tag(kind, self.string())
        if kind == 'compound':
            result = {}
            while True:
                type_id = self.number('B')
                check(type_id < len(KINDS), 'Unknown NBT tag type')
                if type_id == 0:
                    break
                name = self.string()
                check(name not in result, 'Duplicate NBT keys are unsupported')
                result[name] = self.payload(KINDS[type_id], depth + 1)
            return tag(kind, result)
        if kind in ('list', 'byte_array', 'int_array', 'long_array'):
            if kind == 'list':
                type_id = self.number('B')
                check(type_id < len(KINDS), 'Unknown list tag type')
                element = KINDS[type_id]
            else:
                element = kind[:-6]
            count = self.number('i')
            check(0 <= count <= MAX_BYTES, 'Invalid NBT collection length')
            check(element != 'end' or count == 0, 'Nonempty END list')
            result = [self.payload(element, depth + 1) for _ in range(count)]
            return tag(kind, {'element': element, 'items': result} if kind == 'list' else [child['value'] for child in result])
        raise FormatError('Unsupported NBT type')


def read_nbt(data):
    compressed = data.startswith(b'\x1f\x8b')
    if compressed:
        try:
            with gzip.GzipFile(fileobj=io.BytesIO(data)) as stream:
                data = stream.read(MAX_BYTES + 1)
        except (OSError, EOFError) as error:
            raise FormatError('Invalid gzip NBT') from error
    check(len(data) <= MAX_BYTES, 'Expanded NBT size limit exceeded')
    reader = NbtReader(data)
    check(reader.number('B') == 10, 'Compound NBT root required')
    name = reader.string()
    value = reader.payload('compound')
    check(not reader.stream.read(1), 'Trailing binary NBT data')
    return value, name, compressed


def write_nbt(value, root_name='', compressed=True):
    def string(text):
        check('\0' not in text, 'NBT NUL strings require a modified UTF-8 adapter')
        try:
            raw = text.encode('utf-8')
        except UnicodeError as error:
            raise FormatError('NBT surrogate strings require a modified UTF-8 adapter') from error
        check(len(raw) <= 65535, 'NBT string too long')
        return struct.pack('>H', len(raw)) + raw
    def payload(node):
        kind, body = node['type'], node['value']
        if kind in NUMBERS:
            return struct.pack('>' + NUMBERS[kind][0], body)
        if kind == 'string':
            return string(body)
        if kind == 'compound':
            return b''.join(bytes([KINDS.index(child['type'])]) + string(key) + payload(child) for key, child in sorted(body.items())) + b'\0'
        if kind == 'list':
            return bytes([KINDS.index(body['element'])]) + struct.pack('>i', len(body['items'])) + b''.join(payload(child) for child in body['items'])
        if kind.endswith('_array'):
            return struct.pack('>i', len(body)) + b''.join(payload(tag(kind[:-6], item)) for item in body)
        raise FormatError('Unsupported binary NBT output')
    data = b'\x0a' + string(root_name) + payload(value)
    return gzip.compress(data, mtime=0) if compressed else data
