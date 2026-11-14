"""Read-only exact-byte intake. Never executes archive scripts or NPC payloads."""
import hashlib
import json
from pathlib import Path
import zipfile

from .volume import sha256


def inspect_inputs(reference_archive, inventory, server_root=None, component_report=None):
    inventory=Path(inventory); archive=Path(reference_archive)
    ledger=json.loads(inventory.read_text(encoding='utf-8'))
    result={'schema':1,'scope':'READ_ONLY_ORIGINAL_PACK_IDENTITY','passed':True,
            'archive':{'name':archive.name,'bytes':archive.stat().st_size,'sha256':sha256(archive)},
            'design_inventory_sha256':sha256(inventory),'jars':[],'worlds_opened':False,
            'native_registry_acceptance':False,'minecraft_client_opened':False}
    result['passed'] &= result['archive']['sha256']==ledger['archive_sha256']
    with zipfile.ZipFile(archive) as z:
        infos=z.infolist()
        names=[i.filename for i in infos]
        if len(names)!=len(set(names)):
            raise ValueError('Duplicate ZIP entry names')
        for row in ledger['jars']:
            h=hashlib.sha256(); size=0
            with z.open(row['archive_entry']) as stream:
                for part in iter(lambda:stream.read(1<<20),b''):
                    h.update(part);size+=len(part)
            ok=h.hexdigest()==row['sha256'] and size==row['bytes']
            result['jars'].append({'file':row['file'],'bytes':size,'sha256':h.hexdigest(),
                                   'matches_original_inventory':ok,'crc_read_complete':True})
            result['passed'] &= ok
    if server_root:
        root=Path(server_root)
        paths=list((root/'mods').glob('*.jar'))+list((root/'plugins').glob('*.jar'))
        result['recovered_dependencies']=[{'path':p.relative_to(root).as_posix(),'bytes':p.stat().st_size,'sha256':sha256(p)} for p in sorted(paths)]
        result['recovered_intake_receipt_sha256']=sha256(root.parent/'PRIVATE-SERVER-INTAKE.json')
    if component_report:
        report_path=Path(component_report)
        report=json.loads(report_path.read_text(encoding='utf-8'))
        rows=[]
        for module,data in report['results'].items():
            for item in data.get('artifacts',[]):
                if item['role']!='runtime_mod': continue
                copied=Path(item['copied_path'])
                # Reports use paths relative to the source snapshot root.
                root=report_path
                while root.name!='build' and root.parent!=root: root=root.parent
                p=root.parent/copied
                digest=sha256(p)
                ok=digest==item['sha256']
                rows.append({'module':module,'file':p.name,'sha256':digest,'bytes':p.stat().st_size,'matches_build_report':ok})
                result['passed'] &= ok
        result['component_report_sha256']=sha256(report_path)
        result['source_snapshot_sha256']=report.get('source_snapshot_sha256',report.get('source_snapshot',{}))
        result['component_runtime_artifacts']=rows
    return result
