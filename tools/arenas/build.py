"""Sequential full-size build, identity manifest and private artifacts."""
from datetime import datetime, timezone
import ctypes
import json
from pathlib import Path
import subprocess
import time

from .volume import Volume, safe_new_directory, sha256

ROOT = Path(__file__).resolve().parents[2]

# Vanilla 1.12.2 numeric save IDs. A native runtime join is a separate gate.
VANILLA = {
    0:'air',1:'stone',2:'grass',3:'dirt',4:'cobblestone',5:'planks',7:'bedrock',
    8:'flowing_water',9:'water',10:'flowing_lava',11:'lava',12:'sand',13:'gravel',
    17:'log',18:'leaves',20:'glass',24:'sandstone',35:'wool',43:'double_stone_slab',
    44:'stone_slab',45:'brick_block',47:'bookshelf',48:'mossy_cobblestone',49:'obsidian',
    50:'torch',53:'oak_stairs',54:'chest',57:'diamond_block',58:'crafting_table',
    60:'farmland',61:'furnace',62:'lit_furnace',64:'wooden_door',65:'ladder',
    67:'stone_stairs',76:'redstone_torch',85:'fence',87:'netherrack',89:'glowstone',
    95:'stained_glass',96:'trapdoor',98:'stonebrick',101:'iron_bars',102:'glass_pane',
    103:'melon_block',107:'fence_gate',108:'brick_stairs',109:'stone_brick_stairs',
    112:'nether_brick',113:'nether_brick_fence',114:'nether_brick_stairs',
    118:'cauldron',123:'redstone_lamp',124:'lit_redstone_lamp',125:'double_wooden_slab',126:'wooden_slab',
    128:'sandstone_stairs',133:'emerald_block',134:'spruce_stairs',135:'birch_stairs',
    139:'cobblestone_wall',145:'anvil',152:'redstone_block',155:'quartz_block',
    156:'quartz_stairs',159:'stained_hardened_clay',160:'stained_glass_pane',
    161:'leaves2',162:'log2',163:'acacia_stairs',164:'dark_oak_stairs',
    168:'prismarine',169:'sea_lantern',170:'hay_block',171:'carpet',172:'hardened_clay',
    173:'coal_block',179:'red_sandstone',180:'red_sandstone_stairs',182:'stone_slab2',
    201:'purpur_block',202:'purpur_pillar',203:'purpur_stairs',205:'purpur_slab',
    206:'end_bricks',213:'magma',214:'nether_wart_block',215:'red_nether_brick',
    216:'bone_block',251:'concrete',252:'concrete_powder',
}


def source_identity():
    paths = [p for base in ('tools/arenas','content/arenas')
             for p in (ROOT/base).rglob('*') if p.is_file()
             and p.suffix in ('.py','.json','.java','.md','.gradle','.xml','.properties')
             and not any(x in p.parts for x in ('build','__pycache__','.gradle'))
             and p.name not in ('native.py','audit.py','stage.py')]
    paths += [ROOT/'collaboration/arena-build-orchestration/arena-brief.json']
    files = [{'path':p.relative_to(ROOT).as_posix(),'bytes':p.stat().st_size,'sha256':sha256(p)}
             for p in sorted(paths)]
    import hashlib
    return {'sha256':hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'files':files}


def peak_memory_bytes():
    if hasattr(ctypes,'windll'):
        from ctypes import wintypes
        class Counters(ctypes.Structure):
            _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD)]+[
                (n,ctypes.c_size_t) for n in ('PeakWorkingSetSize','WorkingSetSize','QuotaPeakPagedPoolUsage',
                'QuotaPagedPoolUsage','QuotaPeakNonPagedPoolUsage','QuotaNonPagedPoolUsage','PagefileUsage','PeakPagefileUsage')]
        c=Counters(); c.cb=ctypes.sizeof(c)
        kernel=ctypes.windll.kernel32
        kernel.GetCurrentProcess.restype=wintypes.HANDLE
        get_info=ctypes.windll.psapi.GetProcessMemoryInfo
        get_info.argtypes=[wintypes.HANDLE,ctypes.POINTER(Counters),wintypes.DWORD]
        get_info.restype=wintypes.BOOL
        if get_info(kernel.GetCurrentProcess(),ctypes.byref(c),c.cb):
            return int(c.PeakWorkingSetSize)
    else:
        import resource
        return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss*1024
    return None


def write_json(path,data):
    Path(path).write_text(json.dumps(data,indent=2,sort_keys=True)+'\n',encoding='utf-8')


def build_all(brief_path,output,seed=20261114,dry_run=False):
    from .geometry import build_geometry
    brief_path=Path(brief_path).resolve()
    brief=json.loads(brief_path.read_text(encoding='utf-8'))
    if len(brief['arenas']) != 5 or [brief['geometry'][k] for k in ('width','height','length')] != [500,250,500]:
        raise ValueError('The five-arena full-size contract is mandatory')
    source=source_identity()
    plan={'schema':1,'source':source,'brief_sha256':sha256(brief_path),'seed':seed,
          'arenas':[a['id'] for a in brief['arenas']], 'owner_client_preference':'KEEP_CLOSED',
          'reservations':'NEW EMPTY PREVIEW WORLD ONLY; not approved in any existing world',
          'geometry_order':'x,y,z; Alpha index=(y*length+z)*width+x',
          'base_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()}
    if dry_run:
        return plan
    out=safe_new_directory(output)
    write_json(out/'run-plan.json',plan)
    manifest={'schema':1,'status':'BUILDING','started_utc':datetime.now(timezone.utc).isoformat(),
              'source':source,'seed':seed,'brief_sha256':sha256(brief_path),'base_commit':plan['base_commit'],
              'arenas':[],'runtime_acceptance':'NOT_RUN','graphical_acceptance':'NOT_RUN_OWNER_KEEP_CLOSED'}
    write_json(out/'manifest.json',manifest)
    for spec in brief['arenas']:
        started=time.monotonic()
        arena_id=spec['id']
        directory=out/arena_id
        directory.mkdir()
        print('Building '+arena_id,flush=True)
        with Volume(directory/'scratch') as volume:
            geometry=build_geometry(spec,volume,seed)
            filename=arena_id+'.schematic'
            digest=volume.export(directory/filename,spec['proposed_world_min'])
            palette={}
            for block,meta in sorted(volume.used):
                if block not in VANILLA:
                    raise ValueError('Unmapped vanilla block ID: %s'%block)
                palette.setdefault(str(block),{'name':'minecraft:'+VANILLA[block],'metadata':[]})['metadata'].append(meta)
            rec={'id':arena_id,'title':spec['title'],'schematic':arena_id+'/'+filename,'sha256':digest,
                 'bytes':(directory/filename).stat().st_size,'dimensions':[500,250,500],'cells':62500000,
                 'world_min':spec['proposed_world_min'],'world_max':spec['proposed_world_max_inclusive'],
                 'local_min':[0,0,0],'local_max':[499,249,499],'dimension':0,
                 'we_origin':spec['proposed_world_min'],'we_offset':[0,0,0],
                 'copy_position':spec['proposed_world_min'],'placement_convention':'local 0,0,0 anchored to world_min',
                 'anchors':{k.removesuffix('_local'):v for k,v in spec.items() if k.endswith('_local')},
                 'geometry':geometry,'palette':palette,'layers':dict(volume.layers),
                 'encounter':spec,'seconds':round(time.monotonic()-started,3),
                 'peak_process_working_set_bytes':peak_memory_bytes(),
                 'native_palette_join':'NOT_RUN','native_encounter':'NOT_RUN'}
            if 'anchors' in geometry:
                rec['anchors'].update(geometry['anchors'])
            write_json(directory/'arena.json',rec)
            from .preview import make_previews
            rec['previews']=make_previews(volume,rec,directory)
        manifest['arenas'].append(rec)
        write_json(out/'manifest.json',manifest)
        print('Exported '+arena_id+' '+digest,flush=True)
    manifest['status']='OFFLINE_EXPORTS_COMPLETE'
    manifest['source_after']=source_identity()
    manifest['source_drift']=manifest['source_after']['sha256']!=source['sha256']
    manifest['peak_process_working_set_bytes']=peak_memory_bytes()
    manifest['finished_utc']=datetime.now(timezone.utc).isoformat()
    write_json(out/'manifest.json',manifest)
    if manifest['source_drift']:
        raise ValueError('Source changed during generation; preserve this candidate and rebuild')
    return manifest
