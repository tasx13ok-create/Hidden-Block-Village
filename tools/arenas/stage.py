"""New-world Anvil staging of independently re-read arena exports.

Never opens or changes an existing owner world. A new candidate and whole-world
cold snapshot are hash joined. Native lighting/physics remain separate gates.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import time
import uuid

from tools.world_merge import nbt
from tools.world_merge.anvil import (_safe_path, _atomic_json, _atomic_write,
    Region, new_chunk, copy_world, _world_tiles)
from .audit import AuditError, file_hash, readback, audit


def _files(root):
    rows=[]
    for path in sorted(Path(root).rglob('*')):
        _safe_path(path)
        if path.is_file():
            rows.append({'path':path.relative_to(root).as_posix(),'bytes':path.stat().st_size,'sha256':file_hash(path)})
    return rows


def _new_world_destination(dest):
    dest=_safe_path(dest)
    if dest.exists(): raise FileExistsError('World destination already exists')
    if any((parent/'level.dat').exists() or (parent/'server.properties').exists() for parent in dest.parents):
        raise AuditError('Destination must not be nested inside an existing world/runtime')
    return dest


def _new_level(world, first, seed):
    position=[first['world_min'][i]+first['anchors']['entry'][i] for i in range(3)]
    data=nbt.compound({
        'version':nbt.Tag(nbt.INT,19133),'DataVersion':nbt.Tag(nbt.INT,1343),
        'LevelName':nbt.Tag(nbt.STRING,'Five Naruto Coliseums - isolated preview'),
        'RandomSeed':nbt.Tag(nbt.LONG,int(seed)), 'generatorName':nbt.Tag(nbt.STRING,'flat'),
        'generatorVersion':nbt.Tag(nbt.INT,1),'generatorOptions':nbt.Tag(nbt.STRING,'3;minecraft:air;1;'),
        'MapFeatures':nbt.Tag(nbt.BYTE,0),'GameType':nbt.Tag(nbt.INT,1),
        'hardcore':nbt.Tag(nbt.BYTE,0),'allowCommands':nbt.Tag(nbt.BYTE,1),
        'Difficulty':nbt.Tag(nbt.BYTE,2),'DifficultyLocked':nbt.Tag(nbt.BYTE,0),
        'SpawnX':nbt.Tag(nbt.INT,position[0]),'SpawnY':nbt.Tag(nbt.INT,position[1]),'SpawnZ':nbt.Tag(nbt.INT,position[2]),
        'Time':nbt.Tag(nbt.LONG,6000),'DayTime':nbt.Tag(nbt.LONG,6000),'LastPlayed':nbt.Tag(nbt.LONG,0),
        'SizeOnDisk':nbt.Tag(nbt.LONG,0),'raining':nbt.Tag(nbt.BYTE,0),'thundering':nbt.Tag(nbt.BYTE,0),
        'GameRules':nbt.compound({key:nbt.Tag(nbt.STRING,value) for key,value in {
            'doFireTick':'false','doMobSpawning':'false','keepInventory':'true','keepNinjaXp':'true',
            'mobGriefing':'false','doDaylightCycle':'false','doWeatherCycle':'false',
            'commandBlockOutput':'false','logAdminCommands':'false'}.items()})})
    _atomic_write(world/'level.dat',nbt.dumps(nbt.compound({'Data':data}),compression='gzip'))


def _chunk_arrays(v, mx, my, mz, cx, cz):
    import numpy as np
    blocks=np.zeros((256,16,16),dtype=np.uint16)
    meta=np.zeros((256,16,16),dtype=np.uint8)
    wx0,wx1=max(cx*16,mx),min(cx*16+16,mx+v.width)
    wz0,wz1=max(cz*16,mz),min(cz*16+16,mz+v.length)
    src=np.frombuffer(v.blocks,dtype=np.uint8).reshape(v.height,v.length,v.width)
    src_meta=np.frombuffer(v.data,dtype=np.uint8).reshape(v.height,v.length,v.width)
    target=(slice(my,my+v.height),slice(wz0-cz*16,wz1-cz*16),slice(wx0-cx*16,wx1-cx*16))
    source=(slice(None),slice(wz0-mz,wz1-mz),slice(wx0-mx,wx1-mx))
    blocks[target]=src[source]
    meta[target]=src_meta[source]
    if v.add is not None:
        # The selected palette is vanilla <=255. Refuse hidden high bits rather
        # than silently dropping a registry mapping.
        if v.add_has_high: raise AuditError('Fresh preview currently requires vanilla <=255 palette')
    del src,src_meta
    return blocks,meta


def _store_chunk(region, cx, cz, blocks, meta, tiles=None):
    import numpy as np
    root=new_chunk(cx,cz)
    level=root['Level']
    level['TileEntities'].value=list(tiles or [])
    for sy in range(16):
        values=blocks[sy*16:sy*16+16].reshape(-1)
        if not values.any(): continue
        info=meta[sy*16:sy*16+16].reshape(-1)
        packed=((info[0::2]&15)|(info[1::2]<<4)).tobytes()
        section=nbt.compound({'Y':nbt.Tag(nbt.BYTE,sy),
            'Blocks':nbt.Tag(nbt.BYTE_ARRAY,values.astype(np.uint8).tobytes()),
            'Data':nbt.Tag(nbt.BYTE_ARRAY,packed),
            'BlockLight':nbt.Tag(nbt.BYTE_ARRAY,bytes(2048)),
            'SkyLight':nbt.Tag(nbt.BYTE_ARRAY,bytes(2048))})
        if values.max()>255:
            high=(values>>8).astype(np.uint8)
            section.value['Add']=nbt.Tag(nbt.BYTE_ARRAY,((high[0::2]&15)|(high[1::2]<<4)).tobytes())
        level['Sections'].value.append(section)
    exists=blocks!=0
    highest=np.where(exists.any(axis=0),256-np.argmax(exists[::-1],axis=0),0)
    level.value['HeightMap']=nbt.Tag(nbt.INT_ARRAY,list(map(int,highest.reshape(-1))))
    region.store_chunk((cz&31)*32+(cx&31),'',root)


def _reserve_empty(world, world_min, dims):
    """Read full candidate regions before a paste; never infer vacancy by distance."""
    mx,my,mz=world_min
    x1,y1,z1=[world_min[i]+dims[i]-1 for i in range(3)]
    inspected=0
    for rz in range(mz>>9,(z1>>9)+1):
        for rx in range(mx>>9,(x1>>9)+1):
            region=Region(world/'region'/('r.%d.%d.mca'%(rx,rz)))
            for slot in region.chunks:
                _,root=region.load_chunk(slot)
                level=root['Level']
                cx,cz=level.scalar('xPos'),level.scalar('zPos')
                if not ((mx>>4)<=cx<=(x1>>4) and (mz>>4)<=cz<=(z1>>4)): continue
                inspected+=1
                if level.get('Entities') and level['Entities'].value:
                    raise AuditError('Reservation contains saved entities')
                if level.get('TileEntities') and level['TileEntities'].value:
                    raise AuditError('Reservation contains tile entities')
                for sec in level['Sections'].value:
                    sy=sec.scalar('Y')
                    add=sec.get('Add')
                    for i,block in enumerate(sec['Blocks'].value):
                        if add is not None:
                            block |= ((add.value[i//2]>>(4*(i%2)))&15)<<8
                        if not block: continue
                        wy=sy*16+(i>>8);wz=cz*16+((i>>4)&15);wx=cx*16+(i&15)
                        if mx<=wx<=x1 and my<=wy<=y1 and mz<=wz<=z1:
                            raise AuditError('Reservation contains blocks')
    return {'status':'PASS','existing_chunks_inspected':inspected,'volume':[world_min,[x1,y1,z1]],
            'scope':'new isolated world has no imported claims/player/NPC data; no production vacancy claim'}


def _buffer_coordinates(manifest,distance=2):
    plots=set();buffer=set();reservations=[]
    for row in manifest['arenas']:
        mx,_,mz=row['world_min'];width,_,length=row.get('dimensions',[500,250,500])
        cx0,cz0=mx>>4,mz>>4;cx1,cz1=(mx+width-1)>>4,(mz+length-1)>>4
        own={(cx,cz) for cx in range(cx0,cx1+1) for cz in range(cz0,cz1+1)};plots.update(own)
        expanded={(cx,cz) for cx in range(cx0-distance,cx1+distance+1) for cz in range(cz0-distance,cz1+distance+1)}
        buffer.update(expanded-own)
        reservations.append({'id':row['id'],'chunk_min':[cx0-distance,cz0-distance],
            'chunk_max_inclusive':[cx1+distance,cz1+distance],'plot_chunks':len(own),'buffer_chunks':len(expanded-own),
            'world_min':[16*(cx0-distance),0,16*(cz0-distance)],
            'world_max_inclusive':[16*(cx1+distance)+15,255,16*(cz1+distance)+15]})
    if buffer&plots: raise AuditError('Native buffer overlaps another authored plot')
    return sorted(buffer),reservations


def verify_empty_buffers(world,manifest,distance=2):
    coords,reservations=_buffer_coordinates(manifest,distance);cache={};failures=[];rows=[]
    for cx,cz in coords:
        key=(cx>>5,cz>>5)
        if key not in cache: cache[key]=Region(Path(world)/'region'/('r.%d.%d.mca'%key))
        loaded=cache[key].load_chunk((cz&31)*32+(cx&31))
        if loaded is None:
            failures.append({'chunk':[cx,cz],'reason':'missing generated void buffer'});continue
        name,root=loaded;level=root['Level'];blank=True
        if level.scalar('xPos')!=cx or level.scalar('zPos')!=cz: blank=False
        if level['Entities'].value or level['TileEntities'].value: blank=False
        for section in level['Sections'].value:
            for tag in ('Blocks','Data','Add'):
                if tag in section and any(section[tag].value): blank=False
        if level.get('TileTicks') and level['TileTicks'].value: blank=False
        if not blank: failures.append({'chunk':[cx,cz],'reason':'buffer contains blocks/meta/entities/tiles/ticks or wrong coordinates'})
        rows.append({'chunk':[cx,cz],'nbt_sha256':hashlib.sha256(nbt.dumps(root,name,compression=None)).hexdigest()})
    digest=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    return {'schema':1,'status':'FAIL' if failures else 'PASS','world':str(_safe_path(world)),
        'verification_source_sha256':file_hash(__file__),'buffer_distance_chunks':distance,
        'buffer_chunks':len(coords),'verified_zero_cells':len(rows)*65536,'reservations':reservations,
        'chunk_inventory_sha256':digest,'chunks':rows,'failures':failures,
        'scope':'fresh private void chunks only; all256Y layers logically zero blocks/meta/tiles/entities; no copied player/claim/world data'}


def _stage_empty_buffers(world,manifest,distance=2):
    coords,_=_buffer_coordinates(manifest,distance);cache={}
    for cx,cz in coords:
        key=(cx>>5,cz>>5)
        if key not in cache: cache[key]=Region(world/'region'/('r.%d.%d.mca'%key))
        region=cache[key];slot=(cz&31)*32+(cx&31)
        if slot in region.chunks: raise AuditError('Expected an unoccupied fresh-world buffer chunk')
        root=new_chunk(cx,cz)
        root['Level'].value['LightPopulated']=nbt.Tag(nbt.BYTE,1)
        region.store_chunk(slot,'',root)
    for region in cache.values(): region.save_atomic()
    result=verify_empty_buffers(world,manifest,distance)
    if result['status']!='PASS': raise AuditError('Independent void buffer readback failed')
    return result


def stage_world(manifest_path, source=None, dest=None):
    if dest is None: raise AuditError('Explicit new destination required')
    manifest_path=_safe_path(manifest_path)
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest.get('schema')!=1 or len(manifest.get('arenas',[]))!=5:
        raise AuditError('Staging requires schema1 complete five-arena manifest')
    world=_new_world_destination(dest)
    snapshot=_safe_path(world.parent/(world.name+'.cold-snapshot'))
    if snapshot.exists(): raise FileExistsError('Snapshot destination already exists')
    empty_snapshot=_safe_path(world.parent/(world.name+'.empty-snapshot'))
    if empty_snapshot.exists(): raise FileExistsError('Initial snapshot destination already exists')
    plot_regions=set()
    for row in manifest['arenas']:
        dims=row.get('dimensions',[500,250,500]);minimum=row.get('world_min',[])
        if dims!=[500,250,500] or len(minimum)!=3 or any(type(value) is not int for value in minimum):
            raise AuditError('Five integer500x250x500 reservations required')
        if minimum[1]!=3 or minimum[0]%512 or minimum[2]%512:
            raise AuditError('Fresh500block plots require region-aligned X/Z and Y3')
        if any(not -29999999<=minimum[i]<=29999999-dims[i]+1 for i in (0,2)):
            raise AuditError('Arena exceeds Minecraft coordinate bounds')
        region=(minimum[0]>>9,minimum[2]>>9)
        if region in plot_regions: raise AuditError('Arena reservations share a region')
        plot_regions.add(region)
    if manifest.get('source_drift') is not False:
        raise AuditError('Missing or drifted build source identity')
    if manifest.get('source',{}).get('sha256')!=manifest.get('source_after',{}).get('sha256'):
        raise AuditError('Build source-before/source-after identity differs')
    from .build import source_identity
    if source_identity()['sha256']!=manifest['source']['sha256']:
        raise AuditError('Current arena source differs from exported source identity; rebuild first')
    manifest_hash=file_hash(manifest_path)
    prerequisite=audit(manifest_path,strict=True)
    prerequisite_path=world.parent/(world.name+'.prerequisite-audit-'+uuid.uuid4().hex+'.json')
    _atomic_json(prerequisite_path,prerequisite)
    if prerequisite['status']!='PASS' or prerequisite['manifest_sha256']!=manifest_hash:
        raise AuditError('Strict independent five-arena output audit must pass before staging')
    # Original saved-world imports need Forge saved-registry/player/NPC review;
    # this command intentionally implements the authorized fresh-world option.
    if source is not None:
        raise AuditError('Use source=None for fresh preview; original-world integration requires reviewed world_merge copy/paste')
    world.mkdir(parents=True)
    (world/'region').mkdir()
    _new_level(world,manifest['arenas'][0],manifest.get('seed',0))
    shutil.copytree(world,empty_snapshot)
    empty_files=_files(world)
    if empty_files!=_files(empty_snapshot): raise AuditError('Initial whole-world snapshot mismatch')
    _atomic_json(world.parent/(world.name+'.empty-snapshot.json'),{
        'schema':1,'world':str(world),'snapshot':str(empty_snapshot),'files':empty_files,
        'manifest_path':str(manifest_path),'manifest_sha256':manifest_hash,'whole_world_hash_verified':True})
    reservations=[]
    staged=[]
    for row in manifest['arenas']:
        dims=row.get('dimensions',[500,250,500]);minimum=row['world_min']
        if dims!=[500,250,500] or minimum[1]!=3: raise AuditError('Illegal arena envelope')
        if len(minimum)!=3 or any(type(value) is not int for value in minimum):
            raise AuditError('World minimum must contain three integer coordinates')
        if any(not -29999999<=minimum[i]<=29999999-dims[i]+1 for i in (0,2)):
            raise AuditError('Arena exceeds Minecraft coordinate bounds')
        reservations.append({'id':row['id'],**_reserve_empty(world,minimum,dims)})
        path=_safe_path(manifest_path.parent/row['schematic'])
        if not path.is_relative_to(manifest_path.parent) or file_hash(path)!=row['sha256']:
            raise AuditError('Schematic identity/path mismatch')
        mx,my,mz=minimum
        region_cache={}
        chunk_count=0
        with readback(path) as v:
            if [v.width,v.height,v.length]!=dims: raise AuditError('Readback dimensions differ')
            world_tiles=_world_tiles(v,minimum)
            tiles_by_chunk={}
            for pos,tag in world_tiles:
                tiles_by_chunk.setdefault((pos[0]>>4,pos[2]>>4),[]).append(tag)
            for cz in range(mz>>4,((mz+v.length-1)>>4)+1):
                for cx in range(mx>>4,((mx+v.width-1)>>4)+1):
                    key=(cx>>5,cz>>5)
                    if key not in region_cache:
                        region_cache[key]=Region(world/'region'/('r.%d.%d.mca'%key))
                    blocks,meta=_chunk_arrays(v,mx,my,mz,cx,cz)
                    _store_chunk(region_cache[key],cx,cz,blocks,meta,tiles_by_chunk.get((cx,cz),[]))
                    chunk_count+=1
            for region in region_cache.values(): region.save_atomic()
        staged.append({'id':row['id'],'chunks':chunk_count,'regions':len(region_cache),'tiles':len(world_tiles),'schematic_sha256':row['sha256']})
    buffers=_stage_empty_buffers(world,manifest,distance=2)
    report={'schema':1,'stage_uuid':uuid.uuid4().hex,'status':'PASS_OFFLINE_STAGING',
        'world':str(world),'manifest_path':str(manifest_path),'manifest_sha256':manifest_hash,'created_unix':int(time.time()),
        'stage_source_sha256':file_hash(__file__),'prerequisite_audit':str(prerequisite_path),
        'source':'fresh void preview, no production world imported','reservations':reservations,'arenas':staged,
        'before_paste_snapshot':str(empty_snapshot),
        'native_prewarm_empty_buffers':buffers,
        'fresh_preview_death_policy':{'keepInventory':True,'keepNinjaXp':True,
            'scope':'Only this newly generated arena preview world; no original world/global config edits; no repeated awakening XP grant'},
        'native_lighting':'NOT_RUN; LightPopulated=0; initial arrays zero','native_physics':'NOT_RUN',
        'native_gameplay':'NOT_RUN','graphical':'NOT_RUN_OWNER_KEEP_CLOSED'}
    _atomic_json(world/'ARENA-STAGE.json',report)
    shutil.copytree(world,snapshot)
    original,copied=_files(world),_files(snapshot)
    if original!=copied: raise AuditError('Whole-world cold snapshot mismatch')
    receipt={'schema':1,'world':str(world),'snapshot':str(snapshot),'manifest_path':str(manifest_path),
             'manifest_sha256':manifest_hash,'files':original,
             'whole_world_hash_verified':True,'restore_policy':'stop owned runtime and restore entire snapshot into NEW destination'}
    _atomic_json(world.parent/(world.name+'.cold-snapshot.json'),receipt)
    report['cold_snapshot']=str(snapshot)
    report['file_rows']=original
    _atomic_json(world.parent/(world.name+'.stage-report.json'),report)
    return report


def verify_world_samples(manifest_path, world):
    """Independent Anvil readback checks all anchor blocks against export bytes."""
    from tools.world_merge.anvil import read_block
    manifest_path=_safe_path(manifest_path)
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    failures=[];checked=0
    for row in manifest['arenas']:
        with readback(manifest_path.parent/row['schematic']) as v:
            for name,point in row['anchors'].items():
                for dy in (-1,0,1,2):
                    x,y,z=point[0],point[1]+dy,point[2]
                    expected=(v.block(x,y,z),v.meta(x,y,z))
                    actual=read_block(world,row['world_min'][0]+x,row['world_min'][1]+y,row['world_min'][2]+z)
                    checked+=1
                    if actual is None or tuple(actual)!=expected: failures.append({'id':row['id'],'anchor':name,'dy':dy,'expected':expected,'actual':actual})
    return {'status':'PASS' if not failures else 'FAIL','checked_cells':checked,'failures':failures,
            'scope':'independent Anvil sample roundtrip; not native physics'}


def restore_snapshot(receipt_path, dest):
    """Whole-copy recovery into a NEW directory; does not delete failed candidates."""
    receipt_path=_safe_path(receipt_path)
    receipt=json.loads(receipt_path.read_text(encoding='utf-8'))
    if receipt.get('schema')!=1 or not receipt.get('whole_world_hash_verified'):
        raise AuditError('Invalid complete-snapshot receipt')
    snapshot=_safe_path(receipt['snapshot']);dest=_new_world_destination(dest)
    if dest.is_relative_to(snapshot) or snapshot.is_relative_to(dest):
        raise AuditError('Restore destination overlaps cold snapshot')
    if _files(snapshot)!=receipt['files']:
        raise AuditError('Cold snapshot identity changed')
    shutil.copytree(snapshot,dest)
    if _files(dest)!=receipt['files']: raise AuditError('Whole-copy restoration mismatch')
    report={'schema':1,'status':'PASS','snapshot':str(snapshot),'restored_world':str(dest),
            'receipt_sha256':file_hash(receipt_path),'manifest_sha256':receipt.get('manifest_sha256'),
            'files':len(receipt['files']),'all_bytes_hash_verified':True,'original_failed_candidate_preserved':True}
    _atomic_json(dest.parent/(dest.name+'.restore-report.json'),report)
    return report


def verify_world_full(manifest_path,world):
    """Read actual region/section arrays and compare every export block/meta cell.

    This reader deliberately does not call the staging chunk conversion helper.
    Native lighting and legitimate world metadata changes are outside the compare.
    """
    import numpy as np
    manifest_path=_safe_path(manifest_path);world=_safe_path(world)
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    rows=[]
    for row in manifest['arenas']:
        mx,my,mz=row['world_min'];failures=[];cells=0;chunks=0
        tiles_checked=0
        region_cache={}
        with readback(manifest_path.parent/row['schematic']) as v:
            source_ids=np.frombuffer(v.blocks,dtype=np.uint8).reshape(v.height,v.length,v.width)
            source_meta=np.frombuffer(v.data,dtype=np.uint8).reshape(v.height,v.length,v.width)
            if v.add_has_high: raise AuditError('Full world comparison requires vanilla low-byte palette')
            expected_tiles={pos:tag for pos,tag in _world_tiles(v,row['world_min'])}
            actual_tiles={}
            for cz in range(mz>>4,((mz+v.length-1)>>4)+1):
                for cx in range(mx>>4,((mx+v.width-1)>>4)+1):
                    key=(cx>>5,cz>>5)
                    if key not in region_cache: region_cache[key]=Region(world/'region'/('r.%d.%d.mca'%key))
                    loaded=region_cache[key].load_chunk((cz&31)*32+(cx&31))
                    if loaded is None:
                        failures.append({'chunk':[cx,cz],'reason':'missing'});continue
                    level=loaded[1]['Level'];chunks+=1
                    for tile in level['TileEntities'].value:
                        pos=tuple(tile.scalar(axis) for axis in 'xyz')
                        if pos in actual_tiles:
                            failures.append({'tile':list(pos),'reason':'duplicate native tile'})
                        actual_tiles[pos]=tile
                    wx0=max(cx*16,mx);wx1=min(cx*16+16,mx+v.width)
                    wz0=max(cz*16,mz);wz1=min(cz*16+16,mz+v.length)
                    for section_y in range(my>>4,((my+v.height-1)>>4)+1):
                        ys=max(my,section_y*16);ye=min(my+v.height,section_y*16+16)
                        sections=[s for s in level['Sections'].value if s.scalar('Y')==section_y]
                        if len(sections)>1: raise AuditError('Duplicate native section Y')
                        if sections:
                            section=sections[0]
                            native_ids=np.frombuffer(section['Blocks'].value,dtype=np.uint8).astype(np.uint16)
                            native_data=np.frombuffer(section['Data'].value,dtype=np.uint8)
                            native_meta=np.empty(4096,dtype=np.uint8)
                            native_meta[0::2]=native_data&15;native_meta[1::2]=native_data>>4
                            if 'Add' in section:
                                add=np.frombuffer(section['Add'].value,dtype=np.uint8)
                                native_ids[0::2]|=(add.astype(np.uint16)&15)<<8
                                native_ids[1::2]|=(add.astype(np.uint16)>>4)<<8
                            native_ids=native_ids.reshape(16,16,16)
                            native_meta=native_meta.reshape(16,16,16)
                        else:
                            native_ids=np.zeros((16,16,16),dtype=np.uint16)
                            native_meta=np.zeros((16,16,16),dtype=np.uint8)
                        actual=(slice(ys-section_y*16,ye-section_y*16),slice(wz0-cz*16,wz1-cz*16),slice(wx0-cx*16,wx1-cx*16))
                        expected=(slice(ys-my,ye-my),slice(wz0-mz,wz1-mz),slice(wx0-mx,wx1-mx))
                        difference=(native_ids[actual]!=source_ids[expected]) | (native_meta[actual]!=source_meta[expected])
                        count=int(np.count_nonzero(difference));cells+=difference.size
                        if count and len(failures)<100:
                            failures.append({'chunk':[cx,cz],'section_y':section_y,'different_cells':count})
            del source_ids,source_meta
            for pos in sorted(set(expected_tiles)|set(actual_tiles)):
                tiles_checked+=1
                if expected_tiles.get(pos)!=actual_tiles.get(pos):
                    failures.append({'tile':list(pos),'reason':'typed tile payload mismatch (including unknown tags)'})
        rows.append({'id':row['id'],'status':'FAIL' if failures else 'PASS','compared_cells':cells,
            'expected_cells':62500000,'chunks':chunks,'tiles_checked':tiles_checked,'failures':failures})
    return {'schema':1,'status':'PASS' if all(row['status']=='PASS' and row['compared_cells']==62500000 for row in rows) else 'FAIL',
        'manifest_path':str(manifest_path),'manifest_sha256':file_hash(manifest_path),'world':str(world),
        'world_stage_marker_sha256':file_hash(world/'ARENA-STAGE.json') if (world/'ARENA-STAGE.json').exists() else None,
        'verification_source_sha256':file_hash(__file__),
        'arenas':rows,'scope':'all exported block IDs and metadata in actual Anvil; no native lighting/physics acceptance'}
