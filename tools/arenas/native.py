"""Private, explicitly guarded native arena checks. No owner client is launched.

The full-acceptance flag deliberately remains false until real combat/revival,
earned admission/outbox and restart-per-phase cases are actually implemented and
observed. A passing registry diagnostic is never promoted to those gates.
"""
from datetime import datetime, timezone
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import socket
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PRIVATE_BASE = Path('D:/HiddenBlockVillage-Sprint-20261113')
BASELINE = PRIVATE_BASE/'bridge-native016-public-api-canonical-01/bridge-native016-runtime-01'
TOOLCHAIN = Path('C:/Users/Steal/OneDrive/Documents/ChatGPT/Naruto mod/Hidden-Block-Village/local/toolchains')
JAVA8 = TOOLCHAIN/'zulu8.98.0.111-ca-jdk8.0.512-win_x64'
JAVA25 = TOOLCHAIN/'zulu25.38.109-ca-jdk25.0.5.0.3-win_x64'
GRADLE = Path('C:/Users/Steal/.gradle/wrapper/dists/gradle-9.5.0-bin/bvnork1r7n8i6kp5cnkibsc9q/gradle-9.5.0/bin/gradle.bat')
API = PRIVATE_BASE/'core016-build-snapshot-08/modules/vale-orders/build/rfg/recompiled_minecraft-1.12.2.jar'
CORE_API = PRIVATE_BASE/'core016-build-snapshot-08/build/source-builds/20261114T134645Z-7db6ba67/artifacts/vale-orders/development_api/vale-orders-0.3.16-dev.jar'
MODULE = ROOT/'modules/naruto-arenas'
MAGMA = 'Magma-1.12.2-8cc35ed-server.jar'
MAGMA_SHA = '2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce'


def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for data in iter(lambda:f.read(1024*1024),b''):h.update(data)
    return h.hexdigest()


def no_links(path):
    p=Path(path).absolute()
    for ancestor in [p,*p.parents]:
        if ancestor.is_symlink() or (hasattr(ancestor,'is_junction') and ancestor.is_junction()):
            raise ValueError('Link/junction path rejected: '+str(ancestor))
    return p


def tree(path):
    result=[]
    for p in sorted(no_links(path).rglob('*')):
        no_links(p)
        if p.is_file():result.append({'path':p.relative_to(path).as_posix(),'bytes':p.stat().st_size,'sha256':digest(p)})
    return result


def save(path,data):
    Path(path).write_text(json.dumps(data,indent=2,sort_keys=True)+'\n',encoding='utf-8')


def own_sources():
    return [{'path':p.relative_to(MODULE).as_posix(),'sha256':digest(p)} for p in sorted(MODULE.rglob('*'))
            if p.is_file() and not any(x in p.relative_to(MODULE).parts for x in ('build','.gradle'))]


def build_module():
    before=own_sources();env=dict(os.environ,VALE_JAVA8_HOME=str(JAVA8),JAVA_HOME=str(JAVA25))
    if digest(CORE_API)!='1cc217db62a94fe04bb7c6ea9dc20cedbfb26d824aca851f4c86847addbb1a72':raise ValueError('Accepted Core API identity differs')
    subprocess.run([str(GRADLE),'--no-daemon','-PprebuiltMinecraft='+str(API),'-PvaleCoreDevJar='+str(CORE_API),'writeValeBuildMetadata','reobfNativeChecksJar'],cwd=MODULE,env=env,check=True)
    if own_sources()!=before:raise ValueError('Arena module source drift during build')
    metadata=json.loads((MODULE/'build/vale-build.json').read_text())
    artifact=Path(next(a['path'] for a in metadata['artifacts'] if a['role']=='runtime_mod'))
    with zipfile.ZipFile(artifact) as z:
        if z.testzip() is not None:raise ValueError('Arena module archive CRC failed')
    qa=MODULE/'build/private-native-checks/PRIVATE-naruto-arenas-checks-0.1.0.jar'
    report={'schema':1,'source_files':before,'runtime_mod':str(artifact),'sha256':digest(artifact),'bytes':artifact.stat().st_size,'private_qa':{'path':str(qa),'sha256':digest(qa)},'prepared_minecraft_api_sha256':digest(API),'core_development_api_sha256':digest(CORE_API),'java8_executable_sha256':digest(JAVA8/'bin/java.exe'),'acceptance':'COMPONENT_BUILD_ONLY'}
    save(MODULE/'build/arena-build-identity.json',report)
    return report


def config_from_manifest(manifest_path):
    m=json.loads(Path(manifest_path).read_text(encoding='utf-8'));out=[]
    if len(m['arenas'])!=5:raise ValueError('All five exported arenas required')
    for a in m['arenas']:
        spec=a['encounter'];minimum=a['world_min']
        def anchor(k):return [v+minimum[i] for i,v in enumerate(a['anchors'][k])]
        raw_nodes=a.get('geometry',{}).get('mechanic_nodes',[])
        nodes=[]
        for node in raw_nodes:
            point=node.get('local') or node.get('position') or node.get('pos') if isinstance(node,dict) else node
            if point is not None:nodes.append([int(v)+minimum[i] for i,v in enumerate(point)])
        rooms=a.get('geometry',{}).get('rooms',[])
        def room_point(kind,fallback):
            candidates=[r['standing'] for r in rooms if r.get('service')==kind]
            local=min(candidates,key=lambda p:sum((p[i]-a['anchors']['prep'][i])**2 for i in range(3))) if candidates else a['anchors'][fallback]
            return [int(v)+minimum[i] for i,v in enumerate(local)]
        services=[anchor('prep'),room_point('aid','prep'),room_point('workshop','prep')]
        samples=[]
        for name,points in a.get('geometry',{}).get('routes',{}).items():
            for p in points[::8]+points[-1:]:samples.append({'name':name,'at':[int(v)+minimum[i] for i,v in enumerate(p)]})
        for room in rooms:samples.append({'name':room['name'],'at':[int(v)+minimum[i] for i,v in enumerate(room['standing'])]})
        containers=[{'name':c['room'],'at':[int(v)+minimum[i] for i,v in enumerate(c['position'])], 'front':[int(v)+minimum[i] for i,v in enumerate(c['front'])]} for c in a.get('geometry',{}).get('containers',[])]
        lights=[[int(v)+minimum[i] for i,v in enumerate(p)] for p in a.get('geometry',{}).get('light_sources',[])]
        prison=a.get('geometry',{}).get('water_prison')
        if prison:
            prison={k:([int(v)+minimum[i] for i,v in enumerate(prison[k])] if k in ('min','max') else
                       [[int(v)+minimum[i] for i,v in enumerate(point)] for point in prison[k]]) for k in ('min','max','exits')}
        out.append(dict(id=a['id'],title=a['title'],boss=spec['static_native_registry_candidate'],template_sha256=a['sha256'],min=minimum,
                        entry=anchor('entry'),boss_anchor=anchor('boss'),reward=anchor('reward'),exit=anchor('exit'),
                        floor=spec['main_combat_floor_world_y'],water='mist' in a['id'],terminal_verified=False,palette=a['palette'],nodes=nodes,services=services,samples=samples,containers=containers,lights=lights,water_prison=prison))
    return {'schema':1,'arenas':out}


def verified_copy(source,dest,expected=None):
    source=no_links(source);dest=no_links(dest);value=digest(source)
    if expected and value!=expected:raise ValueError('Source identity mismatch: '+str(source))
    dest.parent.mkdir(parents=True,exist_ok=True)
    with source.open('rb') as src,dest.open('xb') as dst:shutil.copyfileobj(src,dst,1024*1024)
    if digest(source)!=value or digest(dest)!=value:raise ValueError('Copy drift')
    return value


def stage_runtime(world,manifest_path,dest,registry_only=False):
    dest=no_links(dest)
    if dest.exists():raise FileExistsError(dest)
    dest.mkdir(parents=True)
    if digest(BASELINE/MAGMA)!=MAGMA_SHA:raise ValueError('Magma pin mismatch')
    guard=json.loads((BASELINE/'PRIVATE-DISPOSABLE-BRIDGE-NATIVE.json').read_text())
    if guard['source_snapshot_sha256']!='8b5ac108319049bfa86cf490ec1c4fd4f372a655b14d32f6781b8e57f349232d':raise ValueError('Unaccepted Core cohort')
    historical_rows=guard['production54'];assert len(historical_rows)==54
    omitted=[r for r in historical_rows if r['file']=='[1.12.2]DamageIndicatorsMod-3.5.1.jar']
    if len(omitted)!=1:raise ValueError('Explicit reviewed optional server-DI omission missing')
    if omitted[0]['sha256']!='4150b8b73313c9134ab412e935e6c3d9b3def3e18e84c7f5dd105f95e428b808':raise ValueError('Reviewed DI bytes differ')
    rows=[r for r in historical_rows if r not in omitted]
    if len(rows)!=53:raise ValueError('Unexpected original composition after reviewed DI omission')
    for r in rows:
        verified_copy(BASELINE/'mods'/r['file'],dest/'mods'/r['file'],r['sha256'])
    # These are data/dependency directories in the previously inspected, stopped fixture.
    for name in ('libraries','mappings','config','plugins','armourers_workshop','customnpcs','patchouli_books'):
        if (BASELINE/name).exists():
            before=tree(BASELINE/name)
            for r in before:verified_copy(BASELINE/name/r['path'],dest/name/r['path'],r['sha256'])
            if tree(BASELINE/name)!=before:raise ValueError('Dependency drift')
    if (dest/'customnpcs/scripts').exists():raise ValueError('Global scripts are not admitted by this fresh-world probe')
    for name in (MAGMA,'forge-1.12.2-14.23.5.2859.jar','magma_libs.json','magma.yml','bukkit.yml','spigot.yml','paper.yml','commands.yml','help.yml','permissions.yml','binpatches.pack.lzma','deobfuscation_data-1.12.2.lzma'):
        if (BASELINE/name).is_file():verified_copy(BASELINE/name,dest/name)
    module=build_module();verified_copy(module['runtime_mod'],dest/'mods'/Path(module['runtime_mod']).name,module['sha256'])
    verified_copy(module['private_qa']['path'],dest/'mods'/Path(module['private_qa']['path']).name,module['private_qa']['sha256'])
    save(dest/'config/naruto-arenas.json',config_from_manifest(manifest_path))
    if not registry_only:
        world=no_links(world)
        if not (world/'level.dat').is_file() or (world/'playerdata').exists():raise ValueError('Only fresh staged arena world without playerdata is accepted')
        before=tree(world)
        for r in before:verified_copy(world/r['path'],dest/'arena-preview'/r['path'],r['sha256'])
        if tree(world)!=before:raise ValueError('Staged world changed during copy')
    else:before=[]
    props={'level-name':'arena-preview','level-type':'FLAT','generator-settings':'3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;',
           'server-ip':'127.0.0.1','server-port':'25612','online-mode':'false','enable-rcon':'false','enable-query':'false','max-players':'0',
           'white-list':'true','view-distance':'3','max-tick-time':'180000','spawn-protection':'0','difficulty':'1','spawn-monsters':'true','spawn-animals':'false',
           'motd':'PRIVATE NEW ARENA WORLD NATIVE DIAGNOSTIC','allow-flight':'true'}
    (dest/'server.properties').write_text(''.join(k+'='+v+'\n' for k,v in props.items()),encoding='ascii')
    (dest/'eula.txt').write_text('eula=true\n',encoding='ascii')
    (dest/'.hbv-arenas-native-probe').write_text('NEW_FRESH_ARENA_WORLD_ONLY\n',encoding='ascii')
    result={'schema':1,'scope':'PRIVATE_FRESH_ARENA_NATIVE_CANDIDATE','base_production53':rows,'omitted_original_server_mods':[dict(r,reason='Latest approved preview omits optional server Damage Indicators after recorded permission-packet failure; original bytes preserved') for r in omitted],'total_production_jars':len(rows)+1,'private_qa_jars':1,'arena_module':module,'manifest_sha256':digest(manifest_path),
            'source_world':str(world) if not registry_only else None,'world_source_files':before,'registry_only':registry_only,'original_world_used':False,'client_launched':False,
            'magma_sha256':MAGMA_SHA,'java8_sha256':digest(JAVA8/'bin/java.exe')}
    save(dest/'PRIVATE-ARENA-NATIVE-INPUTS.json',result);return result


def boot(runtime,iteration,registry_only=False,controller_only=False):
    runtime=no_links(runtime)
    inputs=json.loads((runtime/'PRIVATE-ARENA-NATIVE-INPUTS.json').read_text())
    if digest(runtime/MAGMA)!=MAGMA_SHA:raise ValueError('Runtime drift')
    for r in inputs['base_production53']:
        if digest(runtime/'mods'/r['file'])!=r['sha256']:raise ValueError('Production module drift')
    arena=inputs['arena_module']
    if digest(runtime/'mods'/Path(arena['runtime_mod']).name)!=arena['sha256']:raise ValueError('Arena module drift')
    with socket.socket() as sock:sock.bind(('127.0.0.1',25612))
    result_path=runtime/'PRIVATE-ARENA-NATIVE-RESULT.json'
    if result_path.exists():result_path.rename(runtime/f'PRIVATE-ARENA-NATIVE-RESULT-{iteration-1}.json')
    argv=[str(JAVA8/'bin/java.exe'),'-Xms512M','-Xmx3G','-Djava.awt.headless=true','-Dhbv.arenas.nativeProbe=true','-Dhbv.arenas.probeIteration='+str(iteration)]
    if registry_only:argv.append('-Dhbv.arenas.registryOnly=true')
    if controller_only:argv.append('-Dhbv.arenas.controllerOnly=true')
    argv+=['-jar',str(runtime/MAGMA),'nogui']
    log=runtime/f'PRIVATE-ARENA-BOOT-{iteration}.log';started=time.monotonic();receipt={'started_utc':datetime.now(timezone.utc).isoformat(),'iteration':iteration,'forced_termination':False}
    with log.open('xb') as out:
        proc=subprocess.Popen(argv,cwd=runtime,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        receipt['pid']=proc.pid
        try:proc.wait(timeout=900)
        except subprocess.TimeoutExpired:
            proc.stdin.write(b'stop\n');proc.stdin.flush()
            try:proc.wait(timeout=60)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=20);receipt['forced_termination']=True
    log_text=log.read_text(encoding='utf-8',errors='replace')
    critical=re.findall(r'(?im)^.*(?:UTFDataFormatException|OutOfMemoryError|Exception in server tick loop|Encountered an unexpected exception|Could not save|Couldn.t save|Failed to save|MissingMappings).*$',log_text)
    receipt.update(exit_code=proc.returncode,duration_seconds=round(time.monotonic()-started,3),log_sha256=digest(log),critical_log_failures=critical,
                   clean_shutdown=proc.returncode==0 and not receipt['forced_termination'] and not critical and 'Stopping server' in log_text and 'Saving worlds' in log_text,
                   native_result=json.loads(result_path.read_text()) if result_path.exists() else None)
    save(runtime/f'PRIVATE-ARENA-BOOT-{iteration}.json',receipt)
    return receipt


def run_checks(candidate,cases='all'):
    candidate=no_links(candidate)
    stage_path=candidate/'ARENA-STAGE.json'
    if not stage_path.is_file():raise ValueError('Exact arena world stage marker required; no latest-build selection is allowed')
    stage=json.loads(stage_path.read_text());manifest=stage['manifest_path']
    if digest(no_links(manifest))!=stage['manifest_sha256']:raise ValueError('Stage manifest identity differs')
    registry_only=cases=='registry'
    controller_only=cases=='controller'
    if cases not in ('all','registry','controller'):raise ValueError('Unknown native case selection')
    for index in range(1,100):
        runtime=candidate.parent/(candidate.name+f'-native-{index:02d}')
        if not runtime.exists():break
    else:raise ValueError('Native candidate cap exceeded')
    staged=stage_runtime(candidate,manifest,runtime,registry_only)
    first=boot(runtime,1,registry_only,controller_only)
    second=boot(runtime,2,registry_only,controller_only) if first['exit_code']==0 and not first['forced_termination'] and not first['critical_log_failures'] else None
    report={'schema':1,'scope':'REGISTRY_ONLY' if registry_only else 'FOCUSED_CONTROLLER_ONLY_NO_GEOMETRY_ACCEPTANCE' if controller_only else 'NATIVE_GEOMETRY_CONTROLLER_DIAGNOSTIC','runtime':str(runtime),'inputs_sha256':digest(runtime/'PRIVATE-ARENA-NATIVE-INPUTS.json'),
            'boots':[first]+([second] if second else []),'passed':bool(first['clean_shutdown'] and first['native_result'] and first['native_result']['passed'] and second and second['clean_shutdown'] and second['native_result'] and second['native_result']['passed']),
            'full_encounter_acceptance':False,'graphical_acceptance':'NOT_RUN_OWNER_KEEP_CLOSED','remaining':['earned entry API integration','distinct encounter playtests','native Edo terminal/revival verification','durable reward crash tests','restart during every phase','load measurements','client rendering']}
    save(runtime/'PRIVATE-ARENA-NATIVE-SUMMARY.json',report)
    return report
