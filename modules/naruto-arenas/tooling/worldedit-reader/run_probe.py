"""Original private installed-reader runner; no server/client/world writes."""
from pathlib import Path
import ctypes
from ctypes import wintypes
import hashlib
import json
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT))
from tools.arenas.native import BASELINE,JAVA8
from tools.arenas.audit import readback

HERE=Path(__file__).resolve().parent
WORK=ROOT/'local'/'arena-worldedit-reader'
WE=BASELINE/'mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar'
LIBS=[WE,BASELINE/'libraries/com/google/guava/guava/21.0/guava-21.0.jar',BASELINE/'libraries/com/google/code/gson/gson/2.8.0/gson-2.8.0.jar']
NAMES=['EndTag','ByteTag','ShortTag','IntTag','LongTag','FloatTag','DoubleTag','ByteArrayTag','StringTag','ListTag','CompoundTag','IntArrayTag','LongArrayTag']

class Counters(ctypes.Structure):
    _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD),*[(name,ctypes.c_size_t) for name in ('PeakWorkingSetSize','WorkingSetSize','QuotaPeakPagedPoolUsage','QuotaPagedPoolUsage','QuotaPeakNonPagedPoolUsage','QuotaNonPagedPoolUsage','PagefileUsage','PeakPagefileUsage','PrivateUsage')]]

def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def typed(tag):
    result={'type':NAMES[tag.type]}
    if tag.type==10:result['value']={k:typed(v) for k,v in tag.value.items()}
    elif tag.type==9:result.update(element_type=NAMES[tag.elem],value=[typed(t) for t in tag.value])
    elif tag.type==7:result['value']=[b if b<128 else b-256 for b in tag.value]
    else:result['value']=tag.value
    return result

def compile_probe():
    WORK.mkdir(parents=True,exist_ok=True)
    result=subprocess.run([str(JAVA8/'bin/javac.exe'),'-encoding','UTF-8','-source','8','-target','8','-cp',';'.join(map(str,LIBS)),'-d',str(WORK),str(HERE/'WorldEditReadProbe.java')],capture_output=True,text=True)
    (WORK/'javac.log').write_text(result.stdout+result.stderr,encoding='utf-8');result.check_returncode()

def run_one(manifest,row,heap='3072m'):
    source=manifest.parent/row['schematic'];out=WORK/(manifest.parent.name+'-'+row['id']+'-'+heap)
    if out.exists():
        serial=2
        while out.with_name(out.name+'-'+str(serial)).exists():serial+=1
        out=out.with_name(out.name+'-'+str(serial))
    out.mkdir(exist_ok=False);expected={'origin':row['we_origin'],'offset':row['we_offset'],'points':[],'tiles':[]}
    source_before=digest(source)
    if source_before!=row['sha256']:raise ValueError('Schematic identity differs from manifest')
    with readback(source) as v:
        for name,anchor in row['geometry']['anchors'].items():
            for dy,part in ((-1,'floor'),(0,'feet'),(1,'head')):
                at=[anchor[0],anchor[1]+dy,anchor[2]];expected['points'].append({'name':name+'/'+part,'at':at,'block':v.block(*at),'meta':v.meta(*at)})
        for i,at in enumerate(row['geometry']['mechanic_nodes']):expected['points'].append({'name':'physical_seal/'+str(i),'at':at,'block':v.block(*at),'meta':v.meta(*at)})
        for tile in v.tile_tags:
            at=[tile.value[k].value for k in ('x','y','z')];expected['tiles'].append({'at':at,'typed_nbt':typed(tile)})
    (out/'expectations.json').write_text(json.dumps(expected,indent=2),encoding='utf-8')
    command=[str(JAVA8/'bin/java.exe'),'-Xms128m','-Xmx'+heap,'-XX:+UseSerialGC','-cp',';'.join(map(str,[WORK,*LIBS])),'WorldEditReadProbe',str(source),str(out/'expectations.json'),str(out/'reader-result.json')]
    start=time.monotonic();peak_ws=peak_private=0
    psapi=ctypes.WinDLL('psapi');psapi.GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.POINTER(Counters),wintypes.DWORD]
    with (out/'stdout.log').open('w',encoding='utf-8') as stdout,(out/'stderr.log').open('w',encoding='utf-8') as stderr:
        process=subprocess.Popen(command,cwd=WORK,stdout=stdout,stderr=stderr)
        while process.poll() is None:
            counters=Counters();counters.cb=ctypes.sizeof(counters)
            if psapi.GetProcessMemoryInfo(wintypes.HANDLE(int(process._handle)),ctypes.byref(counters),ctypes.sizeof(counters)):
                peak_ws=max(peak_ws,counters.PeakWorkingSetSize);peak_private=max(peak_private,counters.PrivateUsage)
            if time.monotonic()-start>240:process.terminate();raise TimeoutError('Reader process exceeded240seconds')
            time.sleep(.1)
    receipt={'arena':row['id'],'build':manifest.parent.name,'schematic_sha256':source_before,'manifest_sha256':digest(manifest),'source_unchanged':digest(source)==source_before,'probe_source_sha256':digest(HERE/'WorldEditReadProbe.java'),'runner_source_sha256':digest(Path(__file__)),'reader_jar_sha256':digest(WE),'java8_executable_sha256':digest(JAVA8/'bin/java.exe'),'libraries':[{'file':p.name,'sha256':digest(p)} for p in LIBS],'command':command,'process_exit':process.returncode,'process_peak_working_set_bytes':peak_ws,'process_sampled_peak_private_bytes':peak_private,'elapsed_seconds':round(time.monotonic()-start,3),'heap_setting':heap,'scope':'FULL_ORIGINAL_WORLDEDIT_CLIPBOARD_LOAD_ONLY; memory is measured standalone, not server live-paste performance'}
    if (out/'reader-result.json').exists():receipt['reader']=json.loads((out/'reader-result.json').read_text())
    receipt['passed']=receipt['source_unchanged'] and process.returncode==0 and receipt.get('reader',{}).get('passed',False)
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps({'out':str(out),'passed':receipt['passed'],'exit':process.returncode,'peak_ws':peak_ws,'seconds':receipt['elapsed_seconds'],'failure':receipt.get('reader',{}).get('failure')}),flush=True)
    return receipt

if __name__=='__main__':
    compile_probe();manifest=Path(sys.argv[1]);data=json.loads(manifest.read_text());rows=data['arenas'];needle=sys.argv[2] if len(sys.argv)>2 else 'all';heap=sys.argv[3] if len(sys.argv)>3 else '3072m'
    if needle!='all':rows=[r for r in rows if needle in r['id']]
    results=[run_one(manifest,row,heap) for row in rows];(WORK/(manifest.parent.name+'-summary-'+needle+'-'+heap+'.json')).write_text(json.dumps(results,indent=2),encoding='utf-8')
