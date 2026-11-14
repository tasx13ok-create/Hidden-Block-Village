"""Disk-backed Alpha arrays and a deterministic streaming NBT writer.

No Python object-per-cell grid or all-five volume allocation is used. Coordinates
are local, inclusive. Invalid writes fail instead of silently clipping structures.
"""
import gzip
import hashlib
import math
import mmap
import os
from pathlib import Path
import struct
from collections import Counter


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def safe_new_directory(path):
    p = Path(path).absolute()
    if p.exists() or p.is_symlink():
        raise ValueError('Destination already exists: %s' % p)
    for parent in [p, *p.parents]:
        if parent.is_symlink() or (hasattr(parent, 'is_junction') and parent.is_junction()):
            raise ValueError('Linked destination ancestor: %s' % parent)
    if len(p.parts) < 3:
        raise ValueError('Destination must be a new named subdirectory')
    p.mkdir(parents=True)
    return p.resolve()


class Volume:
    def __init__(self, directory, dimensions=(500, 250, 500)):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=False)
        self.dimensions = tuple(dimensions)
        self.width, self.height, self.length = self.dimensions
        self.cells = math.prod(self.dimensions)
        if min(self.dimensions) <= 0 or self.cells > 67108864:
            raise ValueError('Unsafe volume size')
        self.handles = []
        self.arrays = []
        for name in ('Blocks', 'Data', 'High'):
            f = (self.directory / (name + '.bin')).open('w+b')
            f.truncate(self.cells)
            self.handles.append(f)
            self.arrays.append(mmap.mmap(f.fileno(), self.cells))
        self.blocks, self.data, self.high = self.arrays
        self.layers = Counter()
        self.used = set()
        self.has_high = False

    def _bounds(self, x0, y0, z0, x1, y1, z1):
        if any(type(a) is not int for a in (x0,y0,z0,x1,y1,z1)):
            raise ValueError('Coordinates must be integers')
        if not (0 <= x0 <= x1 < self.width and 0 <= y0 <= y1 < self.height
                and 0 <= z0 <= z1 < self.length):
            raise ValueError('Write outside volume: %r' % ((x0,y0,z0,x1,y1,z1),))

    def box(self, x0,y0,z0,x1,y1,z1,block,meta=0,layer='geometry'):
        self._bounds(x0,y0,z0,x1,y1,z1)
        if not (type(block) is int and 0 <= block <= 4095 and type(meta) is int and 0 <= meta <= 15):
            raise ValueError('Invalid Alpha block or metadata')
        n = x1-x0+1
        b,d,h = bytes([block & 255])*n, bytes([meta])*n, bytes([block >> 8])*n
        stride = self.width*self.length
        for y in range(y0,y1+1):
            for z in range(z0,z1+1):
                i = y*stride + z*self.width + x0
                self.blocks[i:i+n], self.data[i:i+n], self.high[i:i+n] = b,d,h
        self.layers[layer] += n*(y1-y0+1)*(z1-z0+1)
        self.used.add((block,meta))
        self.has_high |= block > 255

    def set(self,x,y,z,block,meta=0,layer='geometry'):
        self.box(x,y,z,x,y,z,block,meta,layer)

    def cylinder(self,cx,cz,r,y0,y1,block,meta=0,inner=0,layer='geometry'):
        r = int(r)
        for z in range(cz-r,cz+r+1):
            dx = math.isqrt(max(0,r*r-(z-cz)**2))
            if inner > 0 and abs(z-cz) < inner:
                gap = math.ceil(math.sqrt(inner*inner-(z-cz)**2))
                if dx >= gap:
                    self.box(cx-dx,y0,z,cx-gap,y1,z,block,meta,layer)
                    self.box(cx+gap,y0,z,cx+dx,y1,z,block,meta,layer)
            else:
                self.box(cx-dx,y0,z,cx+dx,y1,z,block,meta,layer)

    def line(self,x0,y0,z0,x1,y1,z1,width,block,meta=0,layer='geometry'):
        steps = max(abs(x1-x0),abs(y1-y0),abs(z1-z0),1)
        lo, hi = (width-1)//2, width//2
        for i in range(steps+1):
            t = i/steps
            x,y,z = (round(a+(b-a)*t) for a,b in ((x0,x1),(y0,y1),(z0,z1)))
            self.box(x-lo,y,z-lo,x+hi,y,z+hi,block,meta,layer)

    def export(self,path,world_min):
        path = Path(path)
        if path.exists():
            raise ValueError('Export already exists')
        def named(f,kind,name,payload):
            name = name.encode('utf-8')
            f.write(bytes([kind])+struct.pack('>H',len(name))+name+payload)
        with path.open('xb') as raw:
            with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0,compresslevel=6) as f:
                f.write(b'\x0a\x00\x09Schematic')
                for k,v in zip(('Width','Height','Length'),self.dimensions):
                    named(f,2,k,struct.pack('>h',v))
                named(f,8,'Materials',b'\x00\x05Alpha')
                for base,vec in (('WEOrigin',world_min),('WEOffset',(0,0,0))):
                    for axis,value in zip('XYZ',vec):
                        named(f,3,base+axis,struct.pack('>i',value))
                for name,arr in (('Blocks',self.blocks),('Data',self.data)):
                    named(f,7,name,struct.pack('>i',self.cells))
                    for offset in range(0,self.cells,1<<20):
                        f.write(arr[offset:offset+(1<<20)])
                if self.has_high:
                    named(f,7,'AddBlocks',struct.pack('>i',(self.cells+1)//2))
                    for offset in range(0,self.cells,1<<20):
                        b = self.high[offset:offset+(1<<20)]
                        packed = bytearray((len(b)+1)//2)
                        for j in range(0,len(b),2):
                            packed[j//2] = b[j] | ((b[j+1] if j+1<len(b) else 0)<<4)
                        f.write(packed)
                for key in ('Entities','TileEntities'):
                    named(f,9,key,b'\x0a\x00\x00\x00\x00')
                f.write(b'\x00')
            raw.flush()
            os.fsync(raw.fileno())
        return sha256(path)

    def close(self):
        for a in self.arrays:
            a.flush()
            a.close()
        for f in self.handles:
            f.close()

    def __enter__(self):
        return self

    def __exit__(self,*args):
        self.close()
