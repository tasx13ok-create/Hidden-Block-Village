"""Read-only post-native block/tile differences, correlated with saved journals.

A clean stop receipt and matching arena manifest are mandatory. Reports never
infer a native cause merely from a changed block ID. Source/world bytes stay put.
"""
from collections import Counter
import json
from pathlib import Path

from tools.world_merge import nbt
from tools.world_merge.anvil import Region, _safe_path, _world_tiles
from .audit import file_hash, readback


def _files(world):
    paths=sorted((world/'region').glob('*.mca'))+[world/'data/naruto_arenas_v1.dat',world/'level.dat']
    return {p.relative_to(world).as_posix():file_hash(p) for p in paths}


def _saved_journals(world):
    _,root=nbt.load(world/'data/naruto_arenas_v1.dat');data=root.get('data',root)
    if 'PayloadUtf8' in data: payload=bytes(data['PayloadUtf8'].value).decode('utf-8')
    else: payload=data.scalar('Payload')
    if len(payload)>4*1024*1024: raise ValueError('Arena journal exceeds4MiB')
    saved=json.loads(payload)
    if saved.get('schema')!=1: raise ValueError('Unknown arena journal schema; refuse attribution')
    runs=[];journal={}
    for aid,run in saved['runs'].items():
        runs.append({'id':aid,'state':run.get('state'),'phase':run.get('phase'),
                     'journal_cells':len(run.get('cells',[])),'run':run.get('run'),'template':run.get('template')})
        for cell in run.get('cells',[]):
            journal[(cell['x'],cell['y'],cell['z'])]={'arena':aid,'state':run.get('state'),'cell':cell}
    return runs,journal


def _compare(volume,row,world,journal):
    import numpy as np
    mx,my,mz=row['world_min'];cache={};groups={};changed=0;tiles={}
    source=np.frombuffer(volume.blocks,dtype=np.uint8).reshape(volume.height,volume.length,volume.width)
    metadata=np.frombuffer(volume.data,dtype=np.uint8).reshape(volume.height,volume.length,volume.width)
    for cz in range(mz>>4,((mz+499)>>4)+1):
        for cx in range(mx>>4,((mx+499)>>4)+1):
            key=(cx>>5,cz>>5)
            if key not in cache: cache[key]=Region(world/'region'/('r.%d.%d.mca'%key))
            loaded=cache[key].load_chunk((cz&31)*32+(cx&31))
            if loaded is None: raise ValueError('Authored native chunk missing')
            level=loaded[1]['Level'];sections={s.scalar('Y'):s for s in level['Sections'].value}
            for tile in level['TileEntities'].value: tiles[tuple(tile.scalar(axis) for axis in 'xyz')]=tile
            x0=max(mx,cx*16);x1=min(mx+500,cx*16+16);z0=max(mz,cz*16);z1=min(mz+500,cz*16+16)
            for sy in range(16):
                y0=max(my,sy*16);y1=min(my+250,sy*16+16)
                if y0>=y1: continue
                section=sections.get(sy);ids=np.zeros(4096,dtype=np.uint16);data=np.zeros(4096,dtype=np.uint8)
                if section is not None:
                    ids=np.frombuffer(section['Blocks'].value,dtype=np.uint8).astype(np.uint16)
                    packed=np.frombuffer(section['Data'].value,dtype=np.uint8);data[0::2]=packed&15;data[1::2]=packed>>4
                    if 'Add' in section:
                        high=np.frombuffer(section['Add'].value,dtype=np.uint8).astype(np.uint16)
                        ids[0::2]|=(high&15)<<8;ids[1::2]|=(high>>4)<<8
                ids=ids.reshape(16,16,16);data=data.reshape(16,16,16)
                actual=(slice(y0-sy*16,y1-sy*16),slice(z0-cz*16,z1-cz*16),slice(x0-cx*16,x1-cx*16))
                expected=(slice(y0-my,y1-my),slice(z0-mz,z1-mz),slice(x0-mx,x1-mx))
                old_ids=source[expected];old_meta=metadata[expected];new_ids=ids[actual];new_meta=data[actual]
                yy,zz,xx=np.nonzero((old_ids!=new_ids)|(old_meta!=new_meta));changed+=len(xx)
                for yi,zi,xi in zip(yy,zz,xx):
                    pos=(x0+int(xi),y0+int(yi),z0+int(zi));old=(int(old_ids[yi,zi,xi]),int(old_meta[yi,zi,xi]));new=(int(new_ids[yi,zi,xi]),int(new_meta[yi,zi,xi]));owned=journal.get(pos)
                    kind='ACTIVE_OWNED_JOURNAL' if owned else 'UNJOURNALED'
                    if not owned and old[0]==2 and new[0]==3: kind='UNJOURNALED_GRASS_TO_DIRT'
                    identity=(old,new,kind)
                    if identity not in groups:
                        if len(groups)>=512: raise ValueError('Difference type budget exceeded; retain candidate for manual inspection')
                        groups[identity]={'before':list(old),'after':list(new),'category':kind,'count':0,'world_min':list(pos),'world_max':list(pos),'samples':[]}
                    g=groups[identity];g['count']+=1;g['world_min']=[min(g['world_min'][i],pos[i]) for i in range(3)];g['world_max']=[max(g['world_max'][i],pos[i]) for i in range(3)]
                    if len(g['samples'])<12: g['samples'].append({'position':list(pos),'journal':owned})
    expected_tiles={p:t for p,t in _world_tiles(volume,row['world_min'])};tile_differences=[]
    for pos in sorted(set(expected_tiles)|set(tiles)):
        before=expected_tiles.get(pos);after=tiles.get(pos)
        if before==after: continue
        fields={}
        for key in set(before.value if before else {})|set(after.value if after else {}):
            old=before.get(key) if before else None;new=after.get(key) if after else None
            if old!=new: fields[key]={'expected':nbt.to_plain(old) if old else None,'actual':nbt.to_plain(new) if new else None}
        tile_differences.append({'position':list(pos),'fields':fields})
    return {'id':row['id'],'compared_cells':62500000,'changed_block_meta_cells':changed,
            'groups':list(groups.values()),'tile_differences':tile_differences}


def inspect_native_world(manifest_path,world,stop_receipt):
    manifest_path=_safe_path(manifest_path);world=_safe_path(world);stop_receipt=_safe_path(stop_receipt)
    stop=json.loads(stop_receipt.read_text(encoding='utf-8'))
    if not stop.get('clean_shutdown') or stop.get('exit_code')!=0 or stop.get('forced_termination'):
        raise ValueError('Exact copied runtime must have a clean stopped receipt')
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'));marker=json.loads((world/'ARENA-STAGE.json').read_text(encoding='utf-8'))
    if marker['manifest_sha256']!=file_hash(manifest_path): raise ValueError('Copied world/manifest identity differs')
    before=_files(world);runs,journal=_saved_journals(world);rows=[]
    for row in manifest['arenas']:
        path=_safe_path(manifest_path.parent/row['schematic'])
        if file_hash(path)!=row['sha256']: raise ValueError('Export identity differs')
        # Function-frame scope releases every NumPy view before mmap cleanup.
        with readback(path) as volume: rows.append(_compare(volume,row,world,journal))
    after=_files(world)
    if before!=after: raise ValueError('Native candidate changed during read-only inspection')
    return {'schema':1,'world':str(world),'manifest_sha256':file_hash(manifest_path),
        'clean_shutdown_receipt_sha256':file_hash(stop_receipt),'verification_source_sha256':file_hash(__file__),
        'candidate_unchanged_during_readback':True,'runs':runs,'arenas':rows,'readback_file_hashes':before,
        'scope':'All312.5M authored block/meta cells and typed tiles; correlated with actual saved journals; no cause inferred merely from block identity'}
