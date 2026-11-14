"""Local review gallery for exact generated blocks and acceptance reports."""
import html
import json
from pathlib import Path


def make_gallery(manifest_path,audit_path):
    manifest_path=Path(manifest_path).resolve()
    m=json.loads(manifest_path.read_text(encoding='utf-8'))
    report=json.loads(Path(audit_path).read_text(encoding='utf-8'))
    audits={a['id']:a for a in report['arenas']}
    cards=[]
    for a in m['arenas']:
        esc=html.escape
        folder=a['id'];r=audits[folder]
        pictures=[]
        for name,label in [('silhouette-southwest','Southwest structure'),('silhouette-southeast','Southeast structure'),
                           ('silhouette-northeast','Northeast structure'),('route-map','Routes and anchors'),
                           ('top-down','Top-down blocks'),('interior-section','Preparation section'),
                           ('boss-floor-section','Court section'),('foundation-section','Foundation section')]:
            pictures.append('<figure><a href="%s/%s.png"><img loading="lazy" src="%s/%s.png" alt="%s"></a><figcaption>%s</figcaption></figure>'%(folder,name,folder,name,esc(label),esc(label)))
        stadium=r.get('stadium',r.get('stadium_architecture',{}))
        cards.append('<article id="%s"><h2>%s / %s</h2><p>500 wide × 500 long × 250 high. Independent offline result: <b>%s</b>.</p><p>Export SHA-256: <code>%s</code></p><p><a href="%s">Alpha schematic</a> · <a href="%s/arena.json">Anchors, geometry and encounters</a></p><div class="pictures">%s</div></article>'%
            (folder,esc(a['title']),esc(a['encounter']['intended_boss']),r['status'],a['sha256'],a['schematic'],folder,''.join(pictures)))
    navigation=''.join('<a href="#%s">%s</a>'%(a['id'],html.escape(a['encounter']['intended_boss'])) for a in m['arenas'])
    document='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Five Naruto coliseums — local block review</title>
<style>body{margin:0;background:#101923;color:#e4ebee;font:16px/1.6 system-ui,sans-serif}header,main{max-width:1450px;margin:auto;padding:24px}header{position:sticky;top:0;background:#101923ed;z-index:2;border-bottom:1px solid #38505b}h1{margin:0;font-size:26px}h2{font-size:24px}a{color:#8ed9e7}nav{display:flex;gap:24px;flex-wrap:wrap}article{padding:30px 0;border-bottom:1px solid #38505b;scroll-margin-top:180px}.pictures{display:grid;grid-template-columns:repeat(auto-fit,minmax(420px,1fr));gap:16px}figure{margin:0;background:#1d2937;border-radius:5px;overflow:hidden}img{width:100%;display:block}figcaption{padding:10px 16px}code{overflow-wrap:anywhere;color:#e5cb9c}.scope{color:#f2c885;font-size:14px}@media(max-width:500px){.pictures{grid-template-columns:1fr}header{position:static}}</style>
<header><h1>Five additional Naruto coliseums</h1><p class="scope">Offline projections of generated Minecraft blocks. Client stays closed. Native lighting, physics, gameplay, shaders and hosting require their own results.</p><nav>'''+navigation+'''</nav></header><main><p>The stadium architecture itself must occupy the full 500 × 500 × 250 envelope. The independent audit excludes terrain and plot padding when measuring the stadium. Useful 136-block combat courts sit within the larger stadiums.</p><p><a href="manifest.json">Exact build manifest</a></p>'''+''.join(cards)+'''</main></html>'''
    target=manifest_path.parent/'index.html'
    target.write_text(document,encoding='utf-8')
    return target
