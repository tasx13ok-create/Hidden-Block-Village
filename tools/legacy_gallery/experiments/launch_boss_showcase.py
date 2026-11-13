import os
from pathlib import Path
import json,shutil,subprocess,socket,time,sys,hashlib,uuid

BASE=Path(os.environ['VALE_GALLERY_BASE'])
DEST=BASE/'Legacy-Naruto-Boss-Gallery-01'
ROOT=Path(__file__).resolve().parents[3]
SRC=BASE/'Vale-0312-All-Mods-Client-TabGuard-01'
SERVER=BASE/'magma-v17-copied-world-0312-01'
PORT=25587
with socket.socket() as s:s.bind(('127.0.0.1',PORT))
DEST.mkdir(exist_ok=False); (DEST/'requests').mkdir()
for side in ('server','client'):
 (DEST/side).mkdir()
 source=SERVER if side=='server' else SRC/'client'
 for folder in ('mods','config'):
  shutil.copytree(source/folder,DEST/side/folder)
shutil.copytree(ROOT/'local/academy-preview-0310-c/server/libraries',DEST/'server/libraries')
forge=ROOT/'local/academy-preview-0310-c/server/forge-1.12.2-14.23.5.2859.jar'
shutil.copy2(forge,DEST/'server'/forge.name)
shutil.copy2(ROOT/'local/academy-preview-0310-c/server/minecraft_server.1.12.2.jar',DEST/'server/minecraft_server.1.12.2.jar')
(DEST/'server/eula.txt').write_text('eula=true\n')
(DEST/'server/server.properties').write_text('server-ip=127.0.0.1\nserver-port=25587\nonline-mode=false\nlevel-name=boss-gallery\nlevel-type=FLAT\ngenerator-settings=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;\ngamemode=1\nforce-gamemode=true\ndifficulty=2\nallow-flight=true\nspawn-protection=0\nspawn-npcs=false\nspawn-animals=false\nspawn-monsters=false\nview-distance=8\nmax-players=2\nmax-tick-time=180000\nenable-command-block=true\nenable-rcon=false\npvp=false\nmotd=Legacy Naruto Boss Model Gallery\n')
for name in ('options.txt','optionsof.txt'):
 if (SRC/'client'/name).exists():shutil.copy2(SRC/'client'/name,DEST/'client'/name)
options=DEST/'client/options.txt'
data=options.read_text(encoding='utf-8')
for key,val in {'gamma':'1.0','renderDistance':'8','maxFps':'90','guiScale':'2','particles':'2','bobView':'false'}.items():
 import re
 data=re.sub(r'(?m)^'+key+r':.*$',key+':'+val,data)
options.write_text(data,encoding='utf-8')
(DEST/'client/optionsshaders.txt').write_text('shaderPack=OFF\n')
recipe=json.loads((SRC/'PRIVATE-CLIENT-LAUNCH-RECIPE.json').read_text(encoding='utf-8'))
args=recipe['argv']; assert args[args.index('--accessToken')+1]=='0'
raw=bytearray(hashlib.md5(b'OfflinePlayer:BossGallery').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
for key,value in [('--username','BossGallery'),('--uuid',uuid.UUID(bytes=bytes(raw)).hex),('--gameDir',str(DEST/'client')),('--server','127.0.0.1'),('--port',str(PORT))]:args[args.index(key)+1]=value
recipe['cwd']=str(DEST/'client')
(DEST/'PRIVATE-CLIENT-LAUNCH-RECIPE.json').write_text(json.dumps(recipe,indent=2),encoding='utf-8')
roster=json.loads((ROOT/'collaboration/legacy-boss-gallery/experimental-roster-list.json').read_text(encoding='utf-8'))
roster=[r for r in roster if r['id']!='kabutoaddon:anbu'] # Historical failed assumption: the registered Rogue Anbu ID is actually kabutoaddon:anbu.
priority=['narutomod:itachi','ahznbcursemarkaddon:edotensei_madara','kabutoaddon:uchiha_obito','kabutoaddon:uzumaki_naruto','narutomod:kisame_hoshigaki','narutomod:deidara','narutomod:hidan','narutomod:kakuzu']
roster.sort(key=lambda r:(0,priority.index(r['id'])) if r['id'] in priority else (1 if r['category']=='Named characters' else 2 if r['category']=='Other legacy combat NPCs' else 3,r['name']))
commands=['gamerule doDaylightCycle false','time set 6000','gamerule doWeatherCycle false','weather clear','gamerule doMobSpawning false','gamerule mobGriefing false','gamerule doFireTick false','gamerule commandBlockOutput false','gamerule sendCommandFeedback false','gamerule keepInventory true','setworldspawn 5 82 -10','fill -5 80 -14 64 80 175 minecraft:quartz_block','fill -5 79 -14 64 79 175 minecraft:glowstone']
human=0;beast=0
for i,r in enumerate(roster):
 giant=r['category']=='Tailed beasts'
 if giant:x,z,step=180,beast*80,36;beast+=1
 else:x,z,step=(human%4)*16,(human//4)*12,3;human+=1
 r.update(index=i,origin=[x,81,z],camera=[x+step*1.5,88 if giant else 83,z-32 if giant else z-9],views=[])
 if giant:commands.append(f'fill {x-18} 80 {z-20} {x+126} 80 {z+40} minecraft:quartz_block')
 for j,(label,yaw) in enumerate([('FRONT',180),('LEFT',-90),('BACK',0),('RIGHT',90)]):
  px=x+j*step;tag=f'legacy_{i}_{j}'
  nbt='{NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,NoGravity:1b,Silent:1b,CustomNameVisible:1b,CustomName:'+json.dumps(r['name']+' | '+label)+',Rotation:['+str(yaw)+'f,0f],Tags:["legacy_gallery","'+tag+'"]}'
  command=f'summon {r["id"]} {px} 81 {z} {nbt}'
  commands.append(command);r['views'].append({'label':label,'yaw':yaw,'position':[px,81,z],'tag':tag})
  text=json.dumps(json.dumps({'text':label,'color':'black'},separators=(',',':')))
  title=json.dumps(json.dumps({'text':r['name'][:32],'color':'dark_blue'},separators=(',',':')))
  commands.append(f'setblock {px} 81 {z-2} minecraft:standing_sign 8 replace {{Text1:{title},Text2:{text}}}')
pages=[]
for start in range(0,len(roster),4):
 parts=[{'text':'LEGACY NARUTO GALLERY\nClick a name to visit\n\n','bold':True}]
 for r in roster[start:start+4]:
  x,y,z=r['camera'];parts.append({'text':r['name']+'\n\n','color':'dark_blue','bold':False,'clickEvent':{'action':'run_command','value':f'/tp @s {x} {y} {z} 0 12'}})
 pages.append(json.dumps(json.dumps(parts,separators=(',',':'))))
book='give BossGallery minecraft:written_book 1 0 {title:"Legacy Boss Gallery",author:"Gallery",pages:['+','.join(pages)+']}'
(DEST/'gallery-roster.json').write_text(json.dumps(roster,indent=2),encoding='utf-8')
(DEST/'gallery-commands.json').write_text(json.dumps(commands,indent=2),encoding='utf-8')
(DEST/'gallery-book-command.txt').write_text(book,encoding='utf-8')
java=ROOT/'local/toolchains/zulu8.98.0.111-ca-jdk8.0.512-win_x64/bin/java.exe'
log=(DEST/'server-console.log').open('x',encoding='utf-8')
server=subprocess.Popen([str(java),'-Xms512M','-Xmx3G','-jar',forge.name,'nogui'],cwd=DEST/'server',stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
state={'server_pid':server.pid,'port':PORT,'boss_types':len(roster),'planned_displays':len(roster)*4,'network_joined':False,'original_world_changed':False}
def save():(DEST/'LIVE-SESSION.json').write_text(json.dumps(state,indent=2),encoding='utf-8')
def send(cmd):server.stdin.write((cmd+'\n').encode('utf-8'));server.stdin.flush()
save();print('GALLERY_SERVER_STARTED',server.pid,flush=True)
deadline=time.monotonic()+180
while time.monotonic()<deadline:
 if server.poll() is not None:raise RuntimeError('Server exited; inspect '+str(DEST/'server-console.log'))
 if 'Done (' in (DEST/'server-console.log').read_text(encoding='utf-8',errors='replace'):break
 time.sleep(.5)
else:raise RuntimeError('Gallery boot timeout')
print('GALLERY_SERVER_READY',flush=True)
send('op BossGallery')
for command in commands:send(command);time.sleep(.02)
send('save-all');state['commands_submitted']=len(commands);save()
out=(DEST/'client-console.log').open('x',encoding='utf-8')
client=subprocess.Popen(args,cwd=recipe['cwd'],stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
state['client_pid']=client.pid;save();print('GALLERY_CLIENT_STARTED',client.pid,flush=True)
seen=set();joined=False
while server.poll() is None:
 for req in sorted((DEST/'requests').glob('*.json')):
  if req.name in seen:continue
  try:d=json.loads(req.read_text(encoding='utf-8'))
  except json.JSONDecodeError:continue
  for command in d['commands']:send(command)
  seen.add(req.name);req.with_suffix('.sent').write_text('sent')
 if not joined and 'BossGallery joined the game' in (DEST/'server-console.log').read_text(encoding='utf-8',errors='replace'):
  joined=True;send('gamemode 1 BossGallery');send('tp BossGallery 4.5 83 -9 0 12');send(book);send('time set 6000');state['network_joined']=True;save();print('GALLERY_JOINED',len(roster),'types',flush=True)
 time.sleep(.5)
