"""Reviewable local-only exports package, excluding runtimes and scratch grids."""
import json
from pathlib import Path
import zipfile

from .volume import sha256


def package(manifest_path,audit_path,world,destination,extra_files=()):
    manifest_path=Path(manifest_path).resolve();audit_path=Path(audit_path).resolve()
    manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    audit=json.loads(audit_path.read_text(encoding='utf-8'))
    if audit.get('status')!='PASS' or audit.get('manifest_sha256')!=sha256(manifest_path):
        raise ValueError('Exact independent passing offline audit required')
    dest=Path(destination).absolute()
    if dest.exists():raise FileExistsError(dest)
    files=[(manifest_path,'exports/manifest.json'),(audit_path,'reports/offline-audit.json')]
    if (manifest_path.parent/'index.html').is_file():
        files.append((manifest_path.parent/'index.html','exports/index.html'))
    for arena in manifest['arenas']:
        schem=(manifest_path.parent/arena['schematic']).resolve()
        if not schem.is_relative_to(manifest_path.parent) or sha256(schem)!=arena['sha256']:
            raise ValueError('Export path/hash changed')
        base=schem.parent
        files += [(schem,'exports/'+arena['schematic']),(base/'arena.json','exports/'+arena['id']+'/arena.json')]
        for row in arena['previews']:
            p=base/row['path']
            if sha256(p)!=row['sha256']:raise ValueError('Preview identity changed')
            files.append((p,'exports/'+arena['id']+'/'+row['path']))
    world=Path(world).resolve()
    if not (world/'ARENA-STAGE.json').is_file():raise ValueError('Fresh preview-world receipt required')
    if any((world/key).exists() for key in ('playerdata','players','stats','advancements','customnpcs')):
        raise ValueError('Private/generated player or imported NPC data may not enter this package')
    for path in sorted(world.rglob('*')):
        if path.is_symlink():raise ValueError('World symlink rejected')
        if path.is_file():files.append((path,'preview-world/'+path.relative_to(world).as_posix()))
    for path in extra_files:
        path=Path(path).resolve()
        files.append((path,'reports/'+path.name))
    rows=[{'path':name,'bytes':p.stat().st_size,'sha256':sha256(p)} for p,name in files]
    receipt={'schema':1,'scope':'LOCAL_ONLY_FIVE_EXPORTS_AND_FRESH_PREVIEW_WORLD',
             'offline_audit':'PASS','native_encounter_acceptance':'See separate runtime report; no implication from this package',
             'graphical_acceptance':'NOT_RUN_OWNER_KEEP_CLOSED','files':rows}
    with zipfile.ZipFile(dest,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as out:
        for p,name in files:out.write(p,name)
        out.writestr('DELIVERY.json',json.dumps(receipt,indent=2)+'\n')
    with zipfile.ZipFile(dest) as check:
        if check.testzip() is not None:raise ValueError('Package CRC failed')
        if len(check.infolist())!=len(files)+1:raise ValueError('Package inventory mismatch')
        import hashlib
        for row in rows:
            if hashlib.sha256(check.read(row['path'])).hexdigest()!=row['sha256']:
                raise ValueError('Package entry identity mismatch')
    receipt['archive']={'name':dest.name,'bytes':dest.stat().st_size,'sha256':sha256(dest)}
    dest.with_suffix('.receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    return receipt
