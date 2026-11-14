"""Separate cold-copy Fire Nether presentation candidate, never an original world.

The Overworld export remains unchanged. Only its Fire plot and two generated
void-buffer chunks are admitted into a new DIM-1. Native checks are separate.
"""
from __future__ import annotations

import json
from pathlib import Path
import shutil

from tools.world_merge import nbt
from tools.world_merge.anvil import Region, _safe_path, _atomic_json, read_block
from .audit import AuditError, file_hash, readback
from .stage import _files, restore_snapshot


def _empty_chunk(level):
    if level['Entities'].value or level['TileEntities'].value:
        return False
    if level.get('TileTicks') and level['TileTicks'].value:
        return False
    return not any(any(section[key].value) for section in level['Sections'].value
                   for key in ('Blocks', 'Data', 'Add') if key in section)


def stage_fire_nether(receipt_path, dest):
    """Build a new hash-verified world plus DIM-1, from an exact cold receipt.

    The source world must be unbooted and contain no DIM-1 or playerdata. A
    separate whole-copy snapshot is retained after staging. No runtime is booted.
    """
    receipt_path=_safe_path(receipt_path)
    receipt=json.loads(receipt_path.read_text(encoding='utf-8'))
    if receipt.get('schema')!=1 or not receipt.get('whole_world_hash_verified'):
        raise AuditError('Exact whole-world cold receipt required')
    source=_safe_path(receipt['snapshot'])
    if (source/'DIM-1').exists() or (source/'playerdata').exists():
        raise AuditError('Only fresh unbooted, player-free cold worlds are admitted')
    source_files=_files(source)
    if source_files!=receipt['files']:
        raise AuditError('Cold snapshot identity drift')
    manifest_path=_safe_path(receipt['manifest_path'])
    if file_hash(manifest_path)!=receipt['manifest_sha256']:
        raise AuditError('Cold snapshot export manifest differs')
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    fire=[a for a in manifest['arenas'] if '_fire_' in a['id']]
    if len(fire)!=1:
        raise AuditError('Exactly one original Fire export required')
    fire=fire[0]
    if fire['dimensions']!=[500,250,500] or fire['world_min'][1]!=3:
        raise AuditError('Fire dimensions/Y envelope differ')
    schematic=_safe_path(manifest_path.parent/fire['schematic'])
    if file_hash(schematic)!=fire['sha256']:
        raise AuditError('Fire Alpha source hash differs')
    world=_safe_path(dest)
    final_snapshot=_safe_path(world.parent/(world.name+'.nether-cold-snapshot'))
    if world.exists() or final_snapshot.exists():
        raise FileExistsError('Nether world/snapshot destination already exists')
    restore_snapshot(receipt_path,world)
    region_dir=world/'DIM-1'/'region'
    region_dir.mkdir(parents=True)
    mx,my,mz=fire['world_min']
    cx0,cz0=mx>>4,mz>>4
    cx1,cz1=(mx+499)>>4,(mz+499)>>4
    sources={};targets={};source_hashes={};chunks=[]
    for cz in range(cz0-2,cz1+3):
        for cx in range(cx0-2,cx1+3):
            key=(cx>>5,cz>>5)
            if key not in sources:
                sources[key]=Region(source/'region'/('r.%d.%d.mca'%key))
                targets[key]=Region(region_dir/('r.%d.%d.mca'%key))
                source_hashes[key]=file_hash(sources[key].path)
            item=sources[key].load_chunk((cz&31)*32+(cx&31))
            if item is None:
                raise AuditError('Missing already generated Fire/buffer chunk')
            name,root=item;level=root['Level']
            if level.scalar('xPos')!=cx or level.scalar('zPos')!=cz:
                raise AuditError('Source chunk coordinates disagree')
            plot=cx0<=cx<=cx1 and cz0<=cz<=cz1
            if not plot and not _empty_chunk(level):
                raise AuditError('Fire Nether buffer is not completely empty')
            if level['Entities'].value:
                raise AuditError('Cold Fire source contains an actor payload')
            # The authored blocks, metadata, containers and unknown safe chunk tags
            # are retained. Native Nether biome8 replaces the Overworld biome only.
            level.value['Biomes']=nbt.Tag(nbt.BYTE_ARRAY,bytes([8])*256)
            level.value['LightPopulated']=nbt.Tag(nbt.BYTE,0)
            targets[key].store_chunk((cz&31)*32+(cx&31),name,root)
            chunks.append({'chunk':[cx,cz],'plot':plot,
                           'source_region_sha256':source_hashes[key]})
    for region in targets.values():region.save_atomic()
    # Independent Anvil reads join all standing anchors to exact Alpha block/meta
    # bytes. This does not establish native collision, physics, light or gameplay.
    checked=0
    with readback(schematic) as v:
        for point in fire['anchors'].values():
            for dy in (-1,0,1,2):
                x,y,z=point[0],point[1]+dy,point[2]
                actual=read_block(world,mx+x,my+y,mz+z,dimension='nether')
                if actual is None or tuple(actual)!=(v.block(x,y,z),v.meta(x,y,z)):
                    raise AuditError('Nether independent anchor readback differs')
                checked+=1
    if _files(source)!=source_files:
        raise AuditError('Immutable source changed during Nether staging')
    report={'schema':1,'scope':'NEW_PRIVATE_FIRE_NETHER_GEOMETRY_COLD_COPY_ONLY',
        'world':str(world),'source_cold_snapshot':str(source),
        'cold_receipt_sha256':file_hash(receipt_path),'manifest_sha256':file_hash(manifest_path),
        'staging_source_sha256':file_hash(__file__),'arena_id':fire['id'],
        'template_sha256':fire['sha256'],'world_min':fire['world_min'],
        'world_max_inclusive':[mx+499,252,mz+499],'dimension':-1,
        'biome':8,'plot_chunks':sum(r['plot'] for r in chunks),
        'buffer_chunks':sum(not r['plot'] for r in chunks),'regions':len(targets),
        'independent_anchor_cells_checked':checked,'source_unchanged':True,
        'allowed_sacrificial_bed_center':[mx-20,32,mz-20],
        'bed_journal_bounds':[[mx-26,26,mz-26],[mx-14,38,mz-14]],
        'native_acceptance':'NOT_RUN','graphical_acceptance':'NOT_RUN_OWNER_KEEP_CLOSED',
        'chunks':chunks,'dim_region_files':_files(region_dir)}
    _atomic_json(world/'ARENA-NETHER-STAGE.json',report)
    shutil.copytree(world,final_snapshot)
    if _files(world)!=_files(final_snapshot):
        raise AuditError('Complete Nether cold snapshot copy differs')
    _atomic_json(world.parent/(world.name+'.nether-cold-snapshot.json'),{
        'schema':1,'whole_world_hash_verified':True,'world':str(world),
        'snapshot':str(final_snapshot),'files':_files(world),
        'manifest_path':str(manifest_path),'manifest_sha256':file_hash(manifest_path),
        'restore_policy':'restore complete cold snapshot into a new directory; retain failed copy'})
    return report
