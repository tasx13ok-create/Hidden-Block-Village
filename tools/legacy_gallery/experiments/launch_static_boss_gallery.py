import os
from pathlib import Path
import json,shutil,subprocess,time,hashlib,uuid
ROOT=Path(__file__).resolve().parents[3]
OLD=Path(os.environ['VALE_GALLERY_BASE'])/'Legacy-Naruto-Boss-Gallery-01'
NEW=Path(os.environ['VALE_GALLERY_BASE'])/'Legacy-Naruto-Model-Gallery-02'
NEW.mkdir(exist_ok=False);(NEW/'client').mkdir()
for name in ('mods','config'):shutil.copytree(OLD/'client'/name,NEW/'client'/name)
for name in ('options.txt','optionsof.txt','optionsshaders.txt'):
 if (OLD/'client'/name).exists():shutil.copy2(OLD/'client'/name,NEW/'client'/name)
meta=json.loads((ROOT/'tools/legacy_gallery_preview/build/vale-build.json').read_text(encoding='utf-8'))
jar=next(Path(a['path']) for a in meta['artifacts'] if a['role']=='runtime_mod')
shutil.copy2(jar,NEW/'client/mods'/jar.name)
recipe=json.loads((OLD/'PRIVATE-CLIENT-LAUNCH-RECIPE.json').read_text(encoding='utf-8'));args=recipe['argv']
raw=bytearray(hashlib.md5(b'OfflinePlayer:BossModels').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
for key,val in [('--username','BossModels'),('--uuid',uuid.UUID(bytes=bytes(raw)).hex),('--gameDir',str(NEW/'client'))]:args[args.index(key)+1]=val
recipe['cwd']=str(NEW/'client');(NEW/'PRIVATE-CLIENT-LAUNCH-RECIPE.json').write_text(json.dumps(recipe,indent=2),encoding='utf-8')
out=(NEW/'client-console.log').open('x',encoding='utf-8')
client=subprocess.Popen(args,cwd=recipe['cwd'],stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(NEW/'LIVE-SESSION.json').write_text(json.dumps({'client_pid':client.pid,'port':25587,'helper_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'mode':'Client-only stationary instances of actual legacy entity renderers; no world actors/AI','planned_views':268}),encoding='utf-8')
print('STATIC_MODEL_GALLERY_CLIENT',client.pid,flush=True)
deadline=time.monotonic()+180
while 'BossModels joined the game' not in (OLD/'server-console.log').read_text(encoding='utf-8',errors='replace'):
 if client.poll() is not None:raise RuntimeError('Model client exited')
 if time.monotonic()>deadline:raise RuntimeError('Join timeout')
 time.sleep(.5)
rows=json.loads((OLD/'gallery-roster.json').read_text(encoding='utf-8'))
for r in rows:
 if r['id']=='kabutoaddon:rogue_anbu':r['id']='kabutoaddon:anbu'
 r['origin'][0]+=1000;r['origin'][1]=4
 r['camera']=[r['origin'][0]+(54 if r['category']=='Tailed beasts' else 4.5),15 if r['category']=='Tailed beasts' else 5,r['origin'][2]-(40 if r['category']=='Tailed beasts' else 9)]
 for v in r['views']:v['position'][0]+=1000;v['position'][1]=4
pages=[]
for start in range(0,len(rows),4):
 parts=[{'text':'LEGACY MODEL GALLERY\nClick a name to visit\nFront / Left / Back / Right\n\n','bold':True}]
 for r in rows[start:start+4]:
  x,y,z=r['camera'];parts.append({'text':r['name']+'\n\n','color':'dark_blue','bold':False,'clickEvent':{'action':'run_command','value':f'/tp @s {x} {y} {z} 0 6'}})
 pages.append(json.dumps(json.dumps(parts,separators=(',',':'))))
book='give BossModels minecraft:written_book 1 0 {title:"Every Legacy Boss",author:"Gallery",pages:['+','.join(pages)+']}'
commands=['op BossModels','gamemode 1 BossModels','tp BossModels 1004.5 4 -9 0 4','effect BossModels minecraft:night_vision 999999 0 true',book,'time set 6000','save-all']
(OLD/'requests/static-models-ready.json').write_text(json.dumps({'commands':commands}),encoding='utf-8')
(NEW/'gallery-roster.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
print('STATIC_MODEL_GALLERY_JOINED',flush=True)
