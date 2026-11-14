"""Verify authored assets and retained baseline; optionally rebuild from the original SVG recipes."""
from pathlib import Path
import argparse,json,hashlib,subprocess,sys,uuid
import xml.etree.ElementTree as ET
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--rebuild',action='store_true');a=p.parse_args()
R=Path(__file__).resolve().parents[2];A=R/'artwork/worn-armor-v1';checks=0

def check(ok,why):
 global checks
 checks+=1
 if not ok:raise AssertionError(why)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
ledger=json.loads((A/'runtime-assets.json').read_text(encoding='utf-8'));roster=json.loads((A/'roster-manifest.json').read_text(encoding='utf-8'));base=json.loads((A/'coverage.json').read_text(encoding='utf-8'));uv=json.loads((A/'native-uv-contract.json').read_text(encoding='utf-8'))
check(len(ledger['assets'])==173,'Runtime count');check(len(roster['entries'])==163,'Entry count');check(len(base['reference_backlog'])==256,'Reference rows retained');check(len({e['jar']for e in base['reference_backlog']})==13,'Reference JARs retained');check(sha(R/'collaboration/sonnet-art/08-WORN-ARMOR-AND-WEAPON-ART.txt')==base['source08_sha256'],'Exact source08 pin')
changed={e['path']for e in ledger['assets']}
for e in base['current_owned_paths']:
 if e['path'] not in changed:check(sha(R/e['path'])==e['sha256'],'Untouched baseline '+e['path'])
check(uv['canvas']==[64,32] and uv['asset_pixels']==[256,128],'Normalized native canvas')
for name,e in uv['parts'].items():
 for face,(x,y,w,h)in e['faces'].items():check(x>=0 and y>=0 and x+w<=64 and y+h<=32,'Native island bounds')
for path in (A/'masters').rglob('*.svg'):
 tree=ET.parse(path).getroot()
 if path.name.endswith('guide.svg'):continue
 check(tree.attrib['viewBox']=='0 0 64 32','SVG native viewbox');check(tree.attrib['width']=='256' and tree.attrib['height']=='128','Fresh raster size')
for e in ledger['assets']:check(sha(R/e['path'])==e['sha256'],'Runtime sha '+e['path'])
for name,entrycount in [('roster-render-qa.json',163),('hero-render-qa.json',1)]:
 data=json.loads((A/name).read_text(encoding='utf-8'));check(data.get('native_visual_acceptance',data.get('native_client_acceptance',False)) is False,'Offline scope')
 if entrycount==163:
  check(data['entry_count']==163 and data['views']==652,'Actual full roster render coverage')
  for e in data['entries']:
   check(len(e['views'])==4,'Four poses');check(all(x['invalid_uv_samples']==0 and all(n>0 for n in x['visible_pixels'].values())for x in e['views']),'Actual visibility and UV geometry')
 else:check(len(data['views'])==4 and all(e['out_of_canvas_uv_samples']==0 for e in data['views']),'Actual hero render')
if a.rebuild:
 dest=R/'local/worn-verify'/('rebuild-'+uuid.uuid4().hex);dest.mkdir(parents=True,exist_ok=False)
 for script,name in [('build_roster.py','roster'),('build_hero.py','hero')]:subprocess.run([sys.executable,str(Path(__file__).parent/script),str(dest/name)],check=True)
 for e in roster['entries']:
  for f in e['files']:check(sha(dest/'roster'/f['path'])==f['sha256'],'Rebuilt roster '+f['path'])
 for layer in(1,2):check(sha(dest/f'hero/resourcepack/assets/valeorders/textures/models/armor/storm_layer_{layer}.png')==sha(R/f'modules/vale-orders/src/main/resources/assets/valeorders/textures/models/armor/lightning_chakra_layer_{layer}.png'),'Rebuilt hero')
 for master in (A/'masters/roster').rglob('*.svg'):check(sha(master)==sha(dest/'roster/masters'/master.relative_to(A/'masters/roster')),'Editable roster SVG recipe')
 for master in (A/'masters/hero').glob('*.svg'):check(sha(master)==sha(dest/'hero/masters'/master.name),'Editable hero SVG recipe')
print('Worn authoring checks PASS',checks,'; original pixels rebuilt, reference artwork untouched; native acceptance separate.')
