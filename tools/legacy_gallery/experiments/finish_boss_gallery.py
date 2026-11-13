import os
from pathlib import Path
import json,time,uuid
root=Path(os.environ['VALE_GALLERY_BASE'])/'Legacy-Naruto-Boss-Gallery-01'
log=root/'server-console.log'
rows=json.loads((root/'gallery-roster.json').read_text(encoding='utf-8'))
seq=0
def send(commands):
 global seq
 seq+=1;mark='GALLERY_ACK_'+uuid.uuid4().hex
 q=root/'requests'/f'finish-{seq:03}.json';assert not q.exists()
 q.write_text(json.dumps({'commands':commands+['say '+mark]}),encoding='utf-8')
 deadline=time.monotonic()+45
 while mark not in log.read_text(encoding='utf-8',errors='replace'):
  if time.monotonic()>deadline:raise RuntimeError('Server did not acknowledge '+str(seq))
  time.sleep(.2)
while log.read_text(encoding='utf-8',errors='replace').count('BossGallery joined the game')<2:time.sleep(.5)
send(['gamemode 3 BossGallery'])
for r in rows:
 if r['category']!='Tailed beasts':continue
 x,_,z=r['origin']
 send([f'tp BossGallery {x+54} 35 {z+10} 0 12'])
 time.sleep(3)
 send([f'kill @e[tag={v["tag"]}]' for v in r['views']])
 time.sleep(1.5)
 commands=[]
 for v in r['views']:
  px,_,pz=v['position'];yaw=v['yaw'];label=v['label'];tag=v['tag']
  nbt='{NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,NoGravity:1b,Silent:1b,CustomNameVisible:1b,CustomName:'+json.dumps(r['name']+' | '+label)+',Rotation:['+str(yaw)+'f,0f],Tags:["legacy_gallery","'+tag+'"]}'
  commands.append(f'summon {r["id"]} {px} 4 {pz} {nbt}')
  v['position'][1]=4
 r['origin'][1]=4;r['camera']=[x+54,15,z-40]
 send(commands)
 send([f'testfor @e[tag={v["tag"]}]' for v in r['views']])
 print('ROW_COMMANDS_SUBMITTED',r['name'],flush=True)
send(['tp BossGallery 4.5 83 -8 0 8','gamemode 1 BossGallery','save-all'])
(root/'gallery-roster.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
print('GALLERY_POPULATION_COMPLETE',flush=True)
