"""Independent bounded readback of Alpha schematic output; no generator imports.

Large byte arrays are streamed from gzip into private temporary mmap files.
Offline collision is deliberately separate from native physics and lighting.
"""
from __future__ import annotations

from collections import Counter, deque
from contextlib import contextmanager
import gzip
import hashlib
import json
import math
import mmap
from pathlib import Path
import struct
import tempfile

from tools.world_merge.anvil import _safe_path
from tools.world_merge.blocks import (FLOOR_TABLE, NO_COLLISION, OPENABLE, FLUIDS,
    GRAVITY, FALL_THROUGH, EXPECTS_TILE, normalize_tile_id, TILE_BLOCKS, VANILLA_IDS, STAIRS)

MAX_BYTES = 536870912
MAX_CELLS = 67108864


class AuditError(ValueError):
    pass


def file_hash(path):
    value = hashlib.sha256()
    with open(path, 'rb') as handle:
        for part in iter(lambda: handle.read(1048576), b''):
            value.update(part)
    return value.hexdigest()


class Reader:
    """Small independent NBT decoder. Arrays never become Python object grids."""
    def __init__(self, stream, directory):
        self.stream, self.directory = stream, Path(directory)
        self.total = self.tags = 0
        self.arrays = {}

    def take(self, count):
        if count < 0 or self.total + count > MAX_BYTES:
            raise AuditError('NBT decompression budget exceeded')
        data = self.stream.read(count)
        self.total += len(data)
        if len(data) != count:
            raise AuditError('Truncated NBT')
        return data

    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.number('>H')).decode('utf-8')

    def payload(self, kind, name='', depth=0):
        self.tags += 1
        if self.tags > 1000000 or depth > 128:
            raise AuditError('NBT complexity budget exceeded')
        formats = {1:'>b', 2:'>h', 3:'>i', 4:'>q', 5:'>f', 6:'>d'}
        if kind in formats:
            return self.number(formats[kind])
        if kind == 8:
            return self.string()
        if kind in (7, 11, 12):
            count = self.number('>i')
            unit = {7:1, 11:4, 12:8}[kind]
            if count < 0 or count * unit > MAX_BYTES:
                raise AuditError('Invalid array size')
            if name in ('Blocks', 'Data', 'AddBlocks', 'ArenaLayers') and depth == 1:
                if name in self.arrays:
                    raise AuditError('Duplicate array ' + name)
                path = self.directory / (name + '.readback')
                with open(path, 'xb') as target:
                    left = count * unit
                    while left:
                        part = self.take(min(left, 1048576))
                        target.write(part)
                        left -= len(part)
                self.arrays[name] = (path, count)
                return {'array_length': count}
            if count * unit > 1048576:
                raise AuditError('Unexpected large auxiliary NBT array')
            raw = self.take(count * unit)
            if kind == 7:
                return raw
            return list(struct.unpack('>%d%s' % (count, 'i' if kind == 11 else 'q'), raw))
        if kind == 9:
            elem, count = self.number('>B'), self.number('>i')
            if count < 0 or count > 100000 or (elem == 0 and count):
                raise AuditError('Invalid list')
            return [self.payload(elem, depth=depth+1) for _ in range(count)]
        if kind == 10:
            out = {}
            while True:
                child = self.number('>B')
                if child == 0:
                    return out
                key = self.string()
                if key in out:
                    raise AuditError('Duplicate NBT key ' + key)
                out[key] = self.payload(child, key, depth+1)
        raise AuditError('Unsupported NBT type %s' % kind)


class Readback:
    def __init__(self, header, handles, maps):
        self.header, self.handles, self.maps = header, handles, maps
        self.width, self.height, self.length = [header[k] for k in ('Width','Height','Length')]
        self.cells = self.width*self.height*self.length
        self.blocks, self.data = maps['Blocks'], maps['Data']
        self.add = maps.get('AddBlocks')
        self.add_has_high = self.add is not None and any(
            self.add[start:start+1048576].strip(b'\x00')
            for start in range(0,len(self.add),1048576))
        self.owners = maps.get('ArenaLayers')
        names=header.get('ArenaLayerNames','{}')
        if not isinstance(names,str) or len(names)>16384:
            raise AuditError('Invalid/oversized layer name table')
        try:
            names=json.loads(names)
        except (ValueError,TypeError) as exc:
            raise AuditError('Invalid layer name JSON') from exc
        if not isinstance(names,dict) or len(names)>256:
            raise AuditError('Invalid layer name mapping')
        self.layer_names={}
        for key,value in names.items():
            if not key.isdigit() or not 0<=int(key)<=255 or not isinstance(value,str) or len(value)>100:
                raise AuditError('Invalid layer name entry')
            self.layer_names[int(key)]=value

    def index(self, x, y, z):
        if not (0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length):
            raise AuditError('Cell outside readback bounds: %s' % ((x,y,z),))
        return (y*self.length+z)*self.width+x

    def block(self, x, y, z):
        i = self.index(x,y,z)
        high = 0 if self.add is None else (self.add[i//2] >> (4*(i%2))) & 15
        return self.blocks[i] | high << 8

    def meta(self, x, y, z):
        return self.data[self.index(x,y,z)]


@contextmanager
def readback(path, scratch_parent=None):
    """Re-read written gzip/NBT bytes into independent temporary arrays."""
    path = _safe_path(path)
    with tempfile.TemporaryDirectory(prefix='arena-readback-', dir=scratch_parent or path.parent) as temp:
        with gzip.open(path,'rb') as handle:
            reader = Reader(handle,temp)
            if reader.number('>B') != 10 or reader.string() != 'Schematic':
                raise AuditError('Expected named Schematic compound')
            header = reader.payload(10)
            if handle.read(1):
                raise AuditError('Trailing NBT data')
        dims = [header.get(k,0) for k in ('Width','Height','Length')]
        cells = math.prod(dims)
        if min(dims) <= 0 or cells > MAX_CELLS:
            raise AuditError('Invalid schematic dimensions')
        for name in ('Blocks','Data'):
            if name not in reader.arrays or reader.arrays[name][1] != cells:
                raise AuditError('Wrong/missing ' + name + ' array length')
        if 'AddBlocks' in reader.arrays and reader.arrays['AddBlocks'][1] not in ((cells+1)//2, cells//2+1):
            raise AuditError('Wrong AddBlocks length')
        if 'ArenaLayers' in reader.arrays and reader.arrays['ArenaLayers'][1]!=cells:
            raise AuditError('Wrong ArenaLayers length')
        handles, maps = {}, {}
        try:
            for name,(array_path,_) in reader.arrays.items():
                handles[name] = open(array_path,'rb')
                maps[name] = mmap.mmap(handles[name].fileno(),0,access=mmap.ACCESS_READ)
            yield Readback(header,handles,maps)
        finally:
            for value in maps.values(): value.close()
            for value in handles.values(): value.close()


def _standing(volume, pos, headroom=3):
    x,y,z = map(int,pos)
    if y < 1 or y+headroom > volume.height:
        return 'outside standing envelope'
    try:
        floor = volume.block(x,y-1,z)
        if floor > 255 or not FLOOR_TABLE[floor]:
            return 'missing/unsafe support'
        for dy in range(headroom):
            block = volume.block(x,y+dy,z)
            if block not in NO_COLLISION and block not in OPENABLE:
                return 'blocked headroom at y=%d block=%d' % (y+dy,block)
    except AuditError:
        return 'outside volume'
    return None


def _sample_route(points):
    if not points:
        return
    yield tuple(map(int,points[0]))
    for a,b in zip(points,points[1:]):
        steps = max(abs(int(b[i])-int(a[i])) for i in range(3))
        for step in range(1,steps+1):
            yield tuple(round(a[i]+(b[i]-a[i])*step/steps) for i in range(3))


def audit_layer_checks(volume,row):
    """Independent sampled module occupancy, not a full structural analysis."""
    required=set(row.get('geometry',{}).get('structural_layers',[]))
    supplied=row.get('layer_checks',{})
    results={}
    for name in sorted(required|set(supplied)):
        spec=supplied.get(name,{})
        failures=[];witnesses=spec.get('surviving_witnesses',[])
        is_required=name in required or spec.get('required',False)
        if is_required and spec.get('written_cells',0)<=0:
            failures.append('required layer has no recorded writes')
        if is_required and not witnesses:
            failures.append('required layer has no surviving nonair occupancy witness')
        for witness in witnesses:
            pos=witness.get('position',[])
            try:
                if len(pos)!=3 or witness.get('block',0)==0:
                    raise AuditError('invalid/nonair witness required')
                actual=(volume.block(*pos),volume.meta(*pos))
                expected=(witness['block'],witness.get('meta',0))
                if actual!=expected:
                    failures.append('witness %s expected %s actual %s'%(pos,expected,actual))
            except (AuditError,KeyError,TypeError) as exc:
                failures.append('invalid witness: '+str(exc))
        results[name]={'status':'FAIL' if failures else 'PASS','required':is_required,
                      'witnesses_checked':len(witnesses),'failures':failures}
    return {'status':'FAIL' if not required or any(v['status']=='FAIL' for v in results.values()) else 'PASS',
            'layers':results,'scope':'sampled per-module surviving block/meta occupancy; not every-cell structural analysis'}


def audit_stairs(volume,geometry):
    spec=geometry.get('walking_stairs',{})
    failures=[];samples=spec.get('samples',[])
    if spec.get('count',0)<=0 or not samples:
        failures.append('No actual walking-stair occupancy samples')
    if spec.get('count',0)<len(samples): failures.append('Stair sample count exceeds authored count')
    for sample in samples:
        try:
            pos=sample['position'];block=sample['block'];meta=sample['meta']
            if block not in STAIRS or meta not in (0,1,2,3):
                failures.append('Walking tread must be normal-half facing stair')
            if (volume.block(*pos),volume.meta(*pos))!=(block,meta):
                failures.append('Stair output mismatch at '+str(pos))
        except (KeyError,TypeError,AuditError) as exc: failures.append('Invalid stair sample: '+str(exc))
    return {'status':'FAIL' if failures else 'PASS','samples_checked':len(samples),'authored_count':spec.get('count',0),
        'failures':failures,'scope':'exact normal-half stair ID/meta readback; native neighbor shapes/movement NOT_RUN'}


STADIUM_LAYERS=frozenset({'bowl_and_stands','landmark_masses','functional_interiors','roof_transitions','galleries',
    'stadium_structure','stadium_galleries','stadium_roof'})


def audit_stadium(volume):
    """Measure architecture itself, excluding terrain, routes and marker padding.

    This gate follows the owner's later clarification that the stadium itself,
    rather than just its export plot, must be 500×500×250.
    """
    import numpy as np
    failures=[]
    if volume.owners is None:
        return {'status':'FAIL','failures':['Missing independent ArenaLayers output array'],
                'scope':'architectural last-writer occupancy, not native support/playability'}
    owners=np.frombuffer(volume.owners,dtype=np.uint8).reshape(volume.height,volume.length,volume.width)
    blocks=np.frombuffer(volume.blocks,dtype=np.uint8).reshape(volume.height,volume.length,volume.width)
    labels=[key for key,name in volume.layer_names.items() if name in STADIUM_LAYERS]
    known=list(volume.layer_names)
    bands={key:0 for key in ('west','east','north','south')}
    planes={key:0 for key in ('min_x','max_x','min_y','max_y','min_z','max_z')}
    quadrants={key:0 for key in ('north_west','north_east','south_west','south_east')}
    minimum=[volume.width,volume.height,volume.length];maximum=[-1,-1,-1]
    total=upper=unmapped=0;by_y=[]
    for y in range(volume.height):
        unmapped+=int(np.count_nonzero((blocks[y]!=0)&~np.isin(owners[y],known)))
        mask=(blocks[y]!=0)&np.isin(owners[y],labels)
        count=int(np.count_nonzero(mask));by_y.append(count);total+=count
        if y>=volume.height-50: upper+=count
        z,x=np.nonzero(mask)
        if count:
            minimum=[min(minimum[0],int(x.min())),min(minimum[1],y),min(minimum[2],int(z.min()))]
            maximum=[max(maximum[0],int(x.max())),max(maximum[1],y),max(maximum[2],int(z.max()))]
        bands['west']+=int(np.count_nonzero(mask[:,:16]));bands['east']+=int(np.count_nonzero(mask[:,-16:]))
        bands['north']+=int(np.count_nonzero(mask[:16,:]));bands['south']+=int(np.count_nonzero(mask[-16:,:]))
        planes['min_x']+=int(np.count_nonzero(mask[:,0]));planes['max_x']+=int(np.count_nonzero(mask[:,-1]))
        planes['min_z']+=int(np.count_nonzero(mask[0,:]));planes['max_z']+=int(np.count_nonzero(mask[-1,:]))
        if y==0: planes['min_y']=count
        if y==volume.height-1: planes['max_y']=count
        mid_x,mid_z=volume.width//2,volume.length//2
        quadrants['north_west']+=int(np.count_nonzero(mask[:mid_z,:mid_x]))
        quadrants['north_east']+=int(np.count_nonzero(mask[:mid_z,mid_x:]))
        quadrants['south_west']+=int(np.count_nonzero(mask[mid_z:,:mid_x]))
        quadrants['south_east']+=int(np.count_nonzero(mask[mid_z:,mid_x:]))
    del owners,blocks
    if [volume.width,volume.height,volume.length]!=[500,250,500]: failures.append('Actual stadium export must be500x250x500')
    if minimum!=[0,0,0] or maximum!=[499,249,499]: failures.append('Architecture itself does not occupy full500x500x250bounds')
    if any(count<50000 for count in bands.values()): failures.append('Insufficient substantial architectural occupancy in outer16blockbands')
    if any(count<100000 for count in quadrants.values()): failures.append('Insufficient architectural occupancy in one or more outer quadrants')
    if any(count<500 for count in planes.values()): failures.append('Extreme planes contain marker-scale rather than substantial architecture')
    if upper<10000: failures.append('Upper50layers lack substantial architectural mass')
    if unmapped: failures.append('Nonair output cells have unknown layer labels')
    return {'status':'FAIL' if failures else 'PASS','failures':failures,'architecture_non_air':total,
        'occupied_min':minimum,'occupied_max':maximum,'outer_band_width':16,'outer_band_counts':bands,
        'outer_band_minimum':50000,'quadrant_counts':quadrants,'quadrant_minimum':100000,
        'extreme_plane_counts':planes,'extreme_plane_minimum':500,'upper50_non_air':upper,'upper50_minimum':10000,
        'unknown_layer_nonair':unmapped,'architecture_by_y':by_y,'included_layer_names':sorted(STADIUM_LAYERS),
        'scope':'actual exported nonair architecture with bounded last-writer layer labels; excludes terrain/foundation, supports-only, routes, fluids, anchors and markers; not native support/playability'}


def audit_arena(row, manifest_dir):
    relative = row.get('schematic', row.get('file'))
    path = _safe_path(Path(manifest_dir)/relative)
    if not path.is_relative_to(Path(manifest_dir).resolve()):
        raise AuditError('Schematic path escapes manifest directory')
    failures, warnings = [], []
    expected_hash = row['sha256']
    actual_hash = file_hash(path)
    if actual_hash != expected_hash:
        failures.append('schematic SHA256 differs from manifest')
    with readback(path) as v:
        dims = [v.width,v.height,v.length]
        if dims != [500,250,500]: failures.append('dimensions must be 500x250x500')
        if v.header.get('Materials') != 'Alpha': failures.append('Materials must be Alpha')
        world_min = row['world_min']
        world_max = [world_min[i]+dims[i]-1 for i in range(3)]
        if world_min[1] != 3 or world_max[1] != 252: failures.append('placement must occupy Y3..252')
        counts, metadata = Counter(), {}
        bounds_min, bounds_max = [v.width,v.height,v.length],[-1,-1,-1]
        gravity_bad, fluid_bad, tile_cells = [], [], set()
        gravity_failure_count = fluid_failure_count = 0
        solid_by_y = []
        try:
            import numpy as np
        except ImportError:
            raise AuditError('NumPy required for bounded vectorized full-volume audit; install private dependency')
        all_ids = np.frombuffer(v.blocks,dtype=np.uint8).reshape(v.height,v.length,v.width)
        all_meta = np.frombuffer(v.data,dtype=np.uint8).reshape(v.height,v.length,v.width)
        high = None
        if v.add is not None:
            packed = np.frombuffer(v.add,dtype=np.uint8)
            high = np.empty(v.cells,dtype=np.uint16)
            high[0::2] = packed[:(v.cells+1)//2] & 15
            high[1::2] = packed[:v.cells//2] >> 4
            high = high.reshape(v.height,v.length,v.width)
        for y in range(v.height):
            ids = all_ids[y].astype(np.uint16)
            if high is not None: ids |= high[y] << 8
            unique,ct = np.unique(ids,return_counts=True)
            counts.update({int(a):int(b) for a,b in zip(unique,ct)})
            for block in unique:
                metadata.setdefault(int(block),set()).update(map(int,np.unique(all_meta[y][ids==block])))
            zz,xx = np.nonzero(ids)
            solid_by_y.append(int(len(xx)))
            if len(xx):
                bounds_min = [min(bounds_min[0],int(xx.min())),min(bounds_min[1],y),min(bounds_min[2],int(zz.min()))]
                bounds_max = [max(bounds_max[0],int(xx.max())),max(bounds_max[1],y),max(bounds_max[2],int(zz.max()))]
            below = np.zeros_like(ids) if y==0 else all_ids[y-1].astype(np.uint16)
            if high is not None and y: below |= high[y-1]<<8
            gravity_mask=np.isin(ids,list(GRAVITY)) & np.isin(below,list(FALL_THROUGH))
            gravity_failure_count+=int(np.count_nonzero(gravity_mask))
            if len(gravity_bad)<100:
                zz_bad,xx_bad=np.nonzero(gravity_mask)
                gravity_bad.extend([[int(x),y,int(z)] for z,x in zip(zz_bad[:100-len(gravity_bad)],xx_bad[:100-len(gravity_bad)])])
            fluid_mask=np.isin(ids,list(FLUIDS))
            if fluid_mask.any():
                open_cells=np.isin(ids,list(NO_COLLISION))
                leak=np.isin(below,list(NO_COLLISION)) | (all_meta[y]>0)
                leak[:,1:] |= open_cells[:,:-1]
                leak[:,:-1] |= open_cells[:,1:]
                leak[1:,:] |= open_cells[:-1,:]
                leak[:-1,:] |= open_cells[1:,:]
                leak[0,:]=True;leak[-1,:]=True;leak[:,0]=True;leak[:,-1]=True
                bad=fluid_mask & leak
                fluid_failure_count+=int(np.count_nonzero(bad))
                if len(fluid_bad)<100:
                    zz_bad,xx_bad=np.nonzero(bad)
                    fluid_bad.extend([[int(x),y,int(z)] for z,x in zip(zz_bad[:100-len(fluid_bad)],xx_bad[:100-len(fluid_bad)])])
            for z,x in zip(*np.nonzero(np.isin(ids,list(EXPECTS_TILE)))):
                tile_cells.add((int(x),y,int(z)))
        del all_ids,all_meta
        if high is not None: del high,packed
        if gravity_bad: failures.append('unsupported gravity blocks')
        if fluid_bad: failures.append('uncontained or non-source fluid cells')
        if any(any(m>15 for m in values) for values in metadata.values()): failures.append('metadata outside nibble range')
        if any(block not in VANILLA_IDS for block in counts): failures.append('unresolved non-vanilla block IDs')
        supplied_palette=row.get('palette',{})
        missing_palette=[]
        for block,values in metadata.items():
            entry=supplied_palette.get(str(block))
            if entry is None or not set(values).issubset(set(entry.get('metadata',[]))):
                missing_palette.append(block)
        if missing_palette: failures.append('output palette/meta pairs absent from manifest palette')
        if bounds_max[1] != 249: failures.append('monument does not reach occupied local Y249')
        tiles = v.header.get('TileEntities',[])
        covered = set()
        for tile in tiles:
            pos=tuple(tile.get(a) for a in 'xyz')
            if pos in covered: failures.append('duplicate tile coordinates')
            covered.add(pos)
            if None in pos or v.block(*pos) not in TILE_BLOCKS.get(normalize_tile_id(tile.get('id')),set()):
                failures.append('tile identity does not match block')
        if tile_cells-covered: failures.append('blocks missing required tile payloads')
        if v.header.get('Entities',[]): failures.append('export contains unreviewed entities')
        anchors = row.get('anchors',{})
        anchors_bad = {name:issue for name,pos in anchors.items() if (issue:=_standing(v,pos))}
        if anchors_bad: failures.append('unsafe standing anchors')
        route_reports = {}
        walk_nodes=set()
        geometry = row.get('geometry',{})
        layer_report=audit_layer_checks(v,row)
        if layer_report['status']!='PASS': failures.append('missing or corrupt structural layer occupancy witnesses')
        stair_report=audit_stairs(v,geometry)
        if stair_report['status']!='PASS': failures.append('missing or corrupt walking stair geometry')
        stadium_report=audit_stadium(v)
        if stadium_report['status']!='PASS': failures.append('stadium itself does not satisfy full500x500x250 architectural occupancy')
        routes = geometry.get('routes',{})
        for name,points in routes.items():
            if isinstance(points,dict): points=points.get('points',[])
            bad,seen,last = [],set(),None
            samples=list(_sample_route(points))
            route_spec=geometry.get('route_specs',{}).get(name,{})
            width=route_spec.get('width',5 if name in ('primary','short_return') else 3)
            clearance=min(route_spec.get('clearance',3),24)
            width_bad=[]
            for sample_index,pos in enumerate(samples):
                if pos in seen: continue
                seen.add(pos)
                issue = _standing(v,pos)
                if last and abs(pos[1]-last[1])>1: issue='step exceeds 1 block'
                if issue and len(bad)<40: bad.append({'position':list(pos),'reason':issue})
                direction=samples[min(sample_index+1,len(samples)-1)] if sample_index+1<len(samples) else samples[max(sample_index-1,0)]
                along_x=abs(direction[0]-pos[0])>=abs(direction[2]-pos[2])
                # Full required transverse walking strip, allowing one-block stair
                # rises at a bend. Collision through ceilings stays a failure.
                for offset in range(-(width//2),width//2+1):
                    p=(pos[0]+(0 if along_x else offset),pos[1],pos[2]+(offset if along_x else 0))
                    candidate=None
                    for dy in (0,1,-1):
                        trial=(p[0],p[1]+dy,p[2])
                        if _standing(v,trial,clearance) is None:
                            candidate=trial;break
                    if candidate is None:
                        if len(width_bad)<40: width_bad.append(list(p))
                    else: walk_nodes.add(candidate)
                last = pos
            route_reports[name]={'status':'FAIL' if bad or width_bad or not seen else 'PASS','samples':len(seen),
                'width_checked':width,'clearance_checked':clearance,'failures':bad,'width_failures':width_bad}
        if not routes or any(r['status']=='FAIL' for r in route_reports.values()): failures.append('disconnected/blocked authored routes')
        connected=set()
        if 'entry' in anchors and tuple(anchors['entry']) in walk_nodes:
            connected.add(tuple(anchors['entry']));queue=deque(connected)
            while queue:
                x,y,z=queue.popleft()
                for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                    for dy in (0,1,-1):
                        p=(x+dx,y+dy,z+dz)
                        if p in walk_nodes and p not in connected:
                            connected.add(p);queue.append(p)
        unreachable={name:point for name,point in anchors.items() if tuple(point) not in connected}
        if unreachable: failures.append('authored walking network does not connect all anchors')
        room_reports=[]
        for room in geometry.get('rooms',[]):
            pos=room.get('anchor',room.get('standing'))
            if room.get('water_route') and pos is not None:
                x,y,z=pos
                issue=None if all(v.block(x,y+dy,z) in (8,9) for dy in range(2)) else 'wet route lacks actual water clearance'
            else:
                issue='missing room anchor' if pos is None else _standing(v,pos)
            room_reports.append({'id':room.get('id',room.get('name')),'status':'FAIL' if issue else 'PASS','failure':issue})
        if not room_reports or any(r['status']=='FAIL' for r in room_reports): failures.append('missing or inaccessible room standing volumes')
        if not row.get('layers'): warnings.append('No generator layer accounting provided')
        return {'id':row['id'],'status':'FAIL' if failures else 'PASS','failures':failures,'warnings':warnings,
            'sha256':actual_hash,'dimensions':dims,'cells':v.cells,'world_min':world_min,'world_max_inclusive':world_max,
            'occupied_min':bounds_min,'occupied_max':bounds_max,'non_air':v.cells-counts[0],
            'block_counts':{str(k):v for k,v in sorted(counts.items())},'metadata':{str(k):sorted(v) for k,v in sorted(metadata.items())},
            'unmapped_palette_ids':missing_palette,
            'occupied_by_y':solid_by_y,'gravity_failure_count':gravity_failure_count,'fluid_failure_count':fluid_failure_count,
            'gravity_failures':gravity_bad,'fluid_failures':fluid_bad,'tile_count':len(tiles),
            'anchors':{'status':'FAIL' if anchors_bad else 'PASS','failures':anchors_bad},'routes':route_reports,'rooms':room_reports,
            'walking_network':{'nodes':len(walk_nodes),'reachable':len(connected),'unreachable_anchors':unreachable},
            'layer_checks':layer_report,
            'walking_stairs':stair_report,
            'stadium_geometry':stadium_report,
            'scope':'independent offline output bytes; native collision/light/physics/boss/gameplay NOT RUN'}


def audit(manifest_path, strict=True):
    manifest_path=_safe_path(manifest_path)
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest.get('schema')!=1: raise AuditError('Unsupported manifest schema')
    rows=[audit_arena(row,manifest_path.parent) for row in manifest['arenas']]
    report={'schema':1,'status':'PASS' if all(r['status']=='PASS' for r in rows) else 'FAIL',
            'manifest_sha256':file_hash(manifest_path),'arenas':rows,
            'native_acceptance':'NOT_RUN','graphical_acceptance':'NOT_RUN_OWNER_KEEP_CLOSED'}
    if strict and len(rows)!=5:
        report['status']='FAIL';report['failure']='Strict release requires all five arenas'
    return report
