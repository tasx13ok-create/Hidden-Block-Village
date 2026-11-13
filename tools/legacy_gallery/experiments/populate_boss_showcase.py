import os
from pathlib import Path
import json,time
root=Path(os.environ['VALE_GALLERY_BASE'])/'Legacy-Naruto-Boss-Gallery-01'
rows=json.loads((root/'gallery-roster.json').read_text(encoding='utf-8'))
allcmd=json.loads((root/'gallery-commands.json').read_text(encoding='utf-8'))
sequence=0
def send(cmds):
 global sequence
 sequence+=1
 p=root/'requests'/f'populate-{sequence:03}.json'
 assert not p.exists()
 p.write_text(json.dumps({'commands':cmds}),encoding='utf-8')
 while not p.with_suffix('.sent').exists():time.sleep(.1)
while not json.loads((root/'LIVE-SESSION.json').read_text())['network_joined']:time.sleep(.5)
send(['tp BossGallery 30 90 80 0 15','effect BossGallery minecraft:night_vision 999999 0 true'])
time.sleep(4)
send(['kill @e[tag=legacy_gallery,x=30,y=81,z=80,r=130]'])
time.sleep(2)
human=[r for r in rows if r['category']!='Tailed beasts']
cmds=[c for c in allcmd if c.startswith('fill -5')]
for r in human:
 for v in r['views']:
  cmds += [c for c in allcmd if c.startswith('summon '+r['id']+' ') and '"'+v['tag']+'"' in c]
  px,py,pz=v['position'];cmds += [c for c in allcmd if c.startswith(f'setblock {px} 81 {pz-2} ')]
send(cmds);print('HUMAN_GALLERY_POPULATED',len(human)*4,flush=True)
time.sleep(3)
for r in [r for r in rows if r['category']=='Tailed beasts']:
 x,y,z=r['origin']
 send([f'tp BossGallery {x+54} 100 {z+10} 0 12'])
 time.sleep(2.5)
 send([f'kill @e[tag={v["tag"]}]' for v in r['views']])
 time.sleep(1.2)
 cmds=[c for c in allcmd if c.startswith('fill ') and f'{z-20} ' in c and c.startswith(f'fill {x-18} ')]
 for v in r['views']:
  cmds += [c for c in allcmd if c.startswith('summon '+r['id']+' ') and '"'+v['tag']+'"' in c]
  px,py,pz=v['position'];cmds += [c for c in allcmd if c.startswith(f'setblock {px} 81 {pz-2} ')]
 send(cmds)
 print('BEAST_GALLERY_POPULATED',r['name'],flush=True)
time.sleep(2)
send(['tp BossGallery 4.5 81 -8 0 4','time set 6000','save-all'])
(root/'POPULATED.json').write_text(json.dumps({'types':len(rows),'planned_displays':len(rows)*4,'native_verification_pending':True}),encoding='utf-8')
print('SHOWCASE_READY_FOR_VISUAL_CHECK',flush=True)
