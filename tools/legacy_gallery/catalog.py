from pathlib import Path
import zipfile,json,sys,re,hashlib
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
from tools.build_equipment_handoff import class_metadata,normalize
if len(sys.argv)!=3:raise SystemExit('Usage: python tools/legacy_gallery/catalog.py <private-mods-directory> <output-json>')
ROOT=Path(sys.argv[1])
classes={}; jars=[]
for p in ROOT.glob('*.jar'):
 if not p.name.startswith(('NarutoMod','kabutoaddon','BorutoMod','SpringAddon','ahznb')):continue
 with zipfile.ZipFile(p) as z:
  local={}; labels={}
  for n in z.namelist():
   if n.endswith('.class'):
    c=class_metadata(z.read(n));local[c['name']]=c;classes[c['name']]=c
   elif n.endswith('/en_us.lang'):
    for l in z.read(n).decode('utf-8',errors='replace').splitlines():
     if l.startswith('entity.') and '=' in l:
      k,v=l.split('=',1);labels[k]=v
  jars.append((p,local,labels))
def living(name,seen=None):
 seen=set() if seen is None else seen
 if name in seen:return False
 seen.add(name)
 if name.startswith('net/minecraft/entity/') and name.rsplit('/',1)[-1] in ('EntityCreature','EntityMob','EntityTameable','EntityBat','EntityLiving','EntityLivingBase'):return True
 return living(classes[name]['parent'],seen) if name in classes else False
named={'ginkaku','tobirama','momoshiki_boss','isshiki_boss','boro','ay','c','darui','edotensei_itachi','edotensei_nagato','hatake_kakashi','jiraiya','kimimaro','uchiha_obito','uzumaki_naruto','yakushi_kabuto','gaara','zabuza_momochi','kakashi','temari','kisame_hoshigaki','sasori','hidan','tenten','itachi','kankuro','sakura_haruno','deidara','kurotsuchi','kakuzu','haku','mightguy','iruka_sensei','edotensei_madara','edotensei_hashirama','tsunade','orochimaru'}
beasts={'one_tail','two_tails','three_tails','four_tails','five_tails','six_tails','seven_tails','eight_tails','nine_tails','ten_tails'}
support={'anbu','whitezetsu','ninja_iwa','ninja_kiri','ninja_konoha','ninja_kumo','ninja_suna','explosive_ninja','iwa_rogue','lava_ninja','medical_ninja','ninjabandit','otsutsuki','rogue_anbu','rogue_ninja','rogue_puppet_master','shinobi','sound_ninja','s_rank_missing_ninja','storm_rogue'}
rows=[]
for p,local,labels in jars:
 for name,c in local.items():
  if '/entity/' not in name or '$' in name:continue
  ns=name.split('/entity/')[0].rsplit('/',1)[-1]
  keys=[s for s in c['strings'] if re.fullmatch('[a-z][a-z0-9_]*',s) and s in named|beasts|support]
  for k in keys:
   if ns not in c['strings']:continue
   types=[x for x in local if x.startswith(name+'$') and living(x)]
   if not types:continue
   label=labels.get('entity.'+k+'.name',k.replace('_',' ').title())
   if k=='edotensei_hashirama':label='Edotensei Hashirama'
   rows.append({'id':ns+':'+k,'name':label,'category':'Tailed beasts' if k in beasts else 'Named characters' if k in named else 'Other legacy combat NPCs','jar':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'class':name})
rows=sorted({r['id']:r for r in rows}.values(),key=lambda r:(r['category'],r['name']))
Path(sys.argv[2]).write_text(json.dumps(rows,indent=2),encoding='utf-8')
print(json.dumps(rows,indent=2))
