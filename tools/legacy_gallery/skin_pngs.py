"""Show original legacy entity skin PNGs locally; publish metadata, never artwork.

python tools/legacy_gallery/skin_pngs.py --mods-dir <private-mods> --output <new-private-output>
Requires Pillow for labeled contact sheets. Input JARs are read, never executed.
"""
from pathlib import Path, PurePosixPath
import argparse,base64,collections,hashlib,html,io,json,math,re,sys,textwrap,zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from tools.build_equipment_handoff import class_metadata

RELATED={'jashin_symbol.png':'ritual symbol','sasori_core.png':'Sasori core form',
         'bijudama.png':'projectile texture','beam_gold.png':'beam texture'}
def digest(raw):return hashlib.sha256(raw).hexdigest()

def build(mods,output,font_path=None):
    from PIL import Image,ImageDraw,ImageFont
    rows=json.loads((ROOT/'collaboration/legacy-boss-gallery/roster.json').read_text(encoding='utf-8'))['roster']
    assert len(rows)==67 and len({r['id'] for r in rows})==67
    output=output.resolve()
    if output.exists():raise ValueError('Use a new output directory; earlier artifacts are preserved')
    if ROOT not in output.parents:raise ValueError('Write private artifacts under the repository exports directory')
    if output.relative_to(ROOT).parts[0]!='exports':raise ValueError('Third-party artwork output must remain under ignored exports/')
    output.mkdir(parents=True)
    archives={}; classes={}; source_hashes={}; resources=collections.defaultdict(list)
    try:
        for name in sorted({r['jar'] for r in rows}):
            path=mods/name;raw=path.read_bytes();source_hashes[name]=digest(raw)
            expected={r['sha256'] for r in rows if r['jar']==name}
            if expected!={source_hashes[name]}:raise ValueError('Reference JAR changed: '+name)
            z=zipfile.ZipFile(io.BytesIO(raw));archives[name]=z;classes[name]={}
            for info in z.infolist():
                p=PurePosixPath(info.filename)
                if p.is_absolute() or '..' in p.parts:raise ValueError('Unsafe archive path')
                if info.filename.endswith('.class'):
                    if info.file_size>8*1024**2:raise ValueError('Oversize class metadata')
                    classes[name][info.filename[:-6]]=class_metadata(z.read(info))
                elif info.filename.startswith('assets/') and info.filename.endswith('.png'):
                    if info.file_size>16*1024**2:raise ValueError('Oversize image')
                    resources[info.filename].append(name)
        images={};bindings=[]
        for row in rows:
            refs=set()
            for name,c in classes[row['jar']].items():
                if name==row['class'] or name.startswith(row['class']+'$'):
                    refs.update(s for s in c['strings'] if re.fullmatch(r'[a-z0-9_.-]+:textures/[A-Za-z0-9_./ -]+\.png',s))
            if not refs:raise ValueError('No direct PNG references for '+row['id'])
            for ref in sorted(refs):
                ns,path=ref.split(':',1);asset='assets/'+ns+'/'+path
                candidates=resources.get(asset,[])
                jar=row['jar'] if row['jar'] in candidates else candidates[0] if len(candidates)==1 else None
                if jar is None:raise ValueError('Missing or ambiguous texture: '+ref)
                raw=archives[jar].read(asset)
                with Image.open(io.BytesIO(raw)) as im:
                    im.verify()
                with Image.open(io.BytesIO(raw)) as im:width,height=im.size
                role=RELATED.get(PurePosixPath(path).name,'skin / model texture')
                if path.endswith('twotailsflames.png'):role='flame overlay'
                if path.endswith('ninetailskcm.png'):role='chakra-mode skin'
                if path.endswith('tentailsl1.png'):role='alternate form'
                if path.endswith('tentails_minion.png'):role='Ten Tails minion skin'
                if path.endswith('jiraiya_sagemode.png'):role='Sage Mode skin'
                binding={'entity_id':row['id'],'name':row['name'],'category':row['category'],'texture':ref,'role':role}
                bindings.append(binding)
                if ref not in images:
                    relative='originals/'+ns+'/'+path
                    target=output/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
                    images[ref]={'texture':ref,'source_jar':jar,'asset_path':asset,'path':relative,'bytes':len(raw),'sha256':digest(raw),'width':width,'height':height,'owners':[],'role':role}
                images[ref]['owners'].append({k:binding[k] for k in ('entity_id','name','category')})
        index={'schema':1,'scope':'Direct PNG resource literals in each curated legacy entity and nested classes; includes explicitly labeled related forms/overlays/effects. Not a private CustomNPC skin inventory or rendered 3D views.',
               'counts':{'entity_types':len(rows),'named_characters':37,'other_combat_npcs':20,'tailed_beasts':10,'unique_pngs':len(images),'entity_png_bindings':len(bindings)},
               'jars':source_hashes,'entities':[{'id':r['id'],'name':r['name'],'category':r['category']} for r in rows],
               'images':list(images.values()),'bindings':bindings}
        (output/'skin-texture-index.json').write_text(json.dumps(index,indent=2)+'\n',encoding='utf-8')
        groups=[('Named characters','Named characters'),('Other legacy combat NPCs','Other combat NPCs'),('Tailed beasts','Tailed beasts')]
        font_path=font_path or (Path('C:/Windows/Fonts/segoeui.ttf') if Path('C:/Windows/Fonts/segoeui.ttf').exists() else None)
        def font(size):return ImageFont.truetype(str(font_path),size) if font_path else ImageFont.load_default(size=size)
        big,medium,small=font(30),font(18),font(14)
        sheets=[]
        for category,title in groups:
            subset=[im for im in images.values() if any(o['category']==category for o in im['owners'])]
            for at in range(0,len(subset),20):
                page=subset[at:at+20];cols=5;cellw,cellh=240,304;page_rows=math.ceil(len(page)/cols)
                sheet=Image.new('RGB',(cols*cellw,page_rows*cellh+100),'#101725');d=ImageDraw.Draw(sheet)
                d.text((18,12),title+' — original PNG textures',font=big,fill='#f2f5fa')
                d.text((18,54),'Unmodified PNGs in the ZIP. These are flat texture maps, not 3D character renders.',font=small,fill='#bcc9da')
                for i,im in enumerate(page):
                    x=(i%cols)*cellw;y=(i//cols)*cellh+92
                    for yy in range(0,180,12):
                        for xx in range(0,216,12):d.rectangle((x+12+xx,y+yy,x+12+xx+11,y+yy+11),fill='#dce0e6' if (xx//12+yy//12)%2==0 else '#bac3cf')
                    with Image.open(output/im['path']) as original:
                        rgba=original.convert('RGBA');scale=min(212/rgba.width,176/rgba.height)
                        rgba=rgba.resize((max(1,round(rgba.width*scale)),max(1,round(rgba.height*scale))),Image.Resampling.NEAREST)
                        sheet.paste(rgba,(x+120-rgba.width//2,y+90-rgba.height//2),rgba)
                    labels=' / '.join(dict.fromkeys(o['name'] for o in im['owners']))
                    if im['role']!='skin / model texture':labels+=' ('+im['role']+')'
                    lines=textwrap.wrap(labels,width=24)[:3]
                    for j,line in enumerate(lines):d.text((x+12,y+188+j*22),line,font=medium,fill='#f3f6fc')
                    d.text((x+12,y+258),str(im['width'])+' × '+str(im['height'])+' | '+im['role'],font=small,fill='#b8c8dc')
                    short=PurePosixPath(im['asset_path']).name
                    if len(short)>30:short=short[:27]+'…'
                    d.text((x+12,y+280),short,font=small,fill='#8ea6c3')
                name=category.lower().replace(' ','-')+'-'+str(at//20+1)+'.png'
                target=output/'previews'/name;target.parent.mkdir(exist_ok=True);sheet.save(target)
                sheets.append({'category':category,'path':'previews/'+name,'images':len(page)})
        def esc(s):return html.escape(str(s),quote=True)
        cards=[]
        for im in images.values():
            names=' / '.join(dict.fromkeys(o['name'] for o in im['owners']));ids=' / '.join(o['entity_id'] for o in im['owners']);category=im['owners'][0]['category']
            payload=base64.b64encode((output/im['path']).read_bytes()).decode('ascii')
            cards.append('<article class="card" data-search="'+esc((names+' '+ids+' '+im['texture']+' '+category+' '+im['role']).lower())+'"><a href="'+esc(im['path'])+'" download><img loading="lazy" src="data:image/png;base64,'+payload+'" alt="'+esc(names)+'"></a><h2>'+esc(names)+'</h2><p>'+esc(im['role'])+' · '+str(im['width'])+' × '+str(im['height'])+'</p><code>'+esc(im['texture'])+'</code><p>'+esc(category)+'</p><a href="'+esc(im['path'])+'" download>Download original PNG</a></article>')
        html_text='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Legacy Naruto boss and character skins</title><style>
body{background:#101725;color:#edf2f9;font:16px system-ui;margin:0;padding:24px}header{max-width:1100px;margin:auto}h1{font-size:30px}p{color:#b8c8dc}input{width:100%;box-sizing:border-box;padding:14px;font-size:18px;background:#1c293e;color:white;border:1px solid #516784;border-radius:8px;position:sticky;top:12px}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(245px,1fr));gap:16px;margin:24px auto;max-width:1600px}.card{background:#192338;border:1px solid #32435d;border-radius:12px;padding:14px;min-width:0}.card[hidden]{display:none}img{width:100%;height:240px;object-fit:contain;image-rendering:pixelated;background:repeating-conic-gradient(#dce0e6 0 25%,#bac3cf 0 50%) 50% / 24px 24px}h2{font-size:19px}code{font-size:12px;overflow-wrap:anywhere}a{color:#9cc8ff}.categories{display:flex;gap:8px;flex-wrap:wrap}button{background:#283b58;color:white;border:1px solid #547297;border-radius:6px;padding:9px;cursor:pointer}</style>
<header><h1>Legacy Naruto boss and character skins</h1><p>67 roster entries: 37 named characters, 20 additional combat NPCs and ten tailed beasts. Merchants/supporting characters are included for completeness and are not all bosses.</p><p>Original flat PNG texture maps from the supplied mods. Variants, shared skins, overlays and related effect/core textures are labeled. This is not a set of 3D character renders.</p><input id="filter" placeholder="Search a name, mod ID, form or texture…" aria-label="Search skins"><p id="count"></p><div class="categories"><button data-filter="">All PNGs</button><button data-filter="named characters">Named characters</button><button data-filter="other legacy combat npcs">Other NPCs</button><button data-filter="tailed beasts">Tailed beasts</button></div></header><main>'''+''.join(cards)+'''</main><script>const q=document.getElementById('filter'),cards=[...document.querySelectorAll('.card')];function apply(){const terms=q.value.toLowerCase().trim().split(/\\s+/);let n=0;for(const c of cards){c.hidden=!terms.every(t=>c.dataset.search.includes(t));if(!c.hidden)n++}document.getElementById('count').textContent=n+' PNGs shown'}q.oninput=apply;for(const b of document.querySelectorAll('button'))b.onclick=()=>{q.value=b.dataset.filter;apply()};apply()</script></html>'''
        (output/'index.html').write_text(html_text,encoding='utf-8')
        names=[]
        for category,title in groups:
            names.extend([title.upper(),'']+[r['name']+' | '+r['id'] for r in rows if r['category']==category]+[''])
        (output/'ALL-BOSS-AND-CHARACTER-NAMES.txt').write_text('\n'.join(names),encoding='utf-8')
        (output/'README.txt').write_text('Open index.html for the searchable local gallery. Originals remain byte-identical to the supplied JAR entries. Previews are labeled contact sheets. Shared textures are stored once and list every roster owner. Related symbols, beams/projectiles, overlays and alternate forms are explicitly labeled. Skin PNGs and this local bundle are not uploaded to the public source repository.\n',encoding='utf-8')
        bundle=output.parent/(output.name+'.zip')
        if bundle.exists():raise ValueError('Earlier ZIP exists')
        paths=sorted(p for p in output.rglob('*') if p.is_file())
        with zipfile.ZipFile(bundle,'x',compression=zipfile.ZIP_DEFLATED) as z:
            for p in paths:z.write(p,p.relative_to(output).as_posix())
        with zipfile.ZipFile(bundle) as z:
            assert z.testzip() is None
            assert set(z.namelist())=={p.relative_to(output).as_posix() for p in paths}
            assert all(z.read(p.relative_to(output).as_posix())==p.read_bytes() for p in paths)
        for im in images.values():assert digest((output/im['path']).read_bytes())==im['sha256']
        public={'counts':index['counts'],'scope':index['scope'],'jars':source_hashes,'images':index['images'],'bindings':bindings,'preview_sheets':sheets,'private_zip_sha256':digest(bundle.read_bytes()),'original_png_hashes_and_zip_verified':True}
        (output/'public-metadata.json').write_text(json.dumps(public,indent=2)+'\n',encoding='utf-8')
        return {'counts':index['counts'],'preview_sheets':sheets,'gallery':str(output/'index.html'),'zip':str(bundle),'zip_sha256':public['private_zip_sha256']}
    finally:
        for z in archives.values():z.close()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mods-dir',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--font',type=Path)
    args=p.parse_args();print(json.dumps(build(args.mods_dir,args.output,args.font),indent=2))
