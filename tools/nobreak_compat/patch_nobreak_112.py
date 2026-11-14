"""Private exact-pin adapter; atomically creates a new candidate, preserves original bytes."""
import argparse,hashlib,io,json,os,subprocess,tempfile,zipfile
from pathlib import Path
PIN='981fad52e429de342d585f30289784510b3d3dd466fc2ced0110930249cfdf1f'
HERE=Path(__file__).resolve().parent
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('output',type=Path);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--magma',type=Path,required=True);p.add_argument('--asm',type=Path,required=True);a=p.parse_args()
 original=a.source.read_bytes();assert len(original)==6787 and sha(original)==PIN,'Unreviewed input pin'
 assert not os.path.lexists(a.output) and not os.path.lexists(a.output.with_suffix('.receipt.json')),'Candidate or receipt already exists'
 with zipfile.ZipFile(io.BytesIO(original)) as z:
  assert z.testzip() is None
  entries={name:z.read(name) for name in z.namelist()}
 assert set(name for name in entries if name.endswith('.class'))=={'com/jojodmo/nobreak/PlayerBlockEditEvent.class','com/jojodmo/nobreak/BreakListener.class','com/jojodmo/nobreak/Main.class'}
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='nobreak-adapter-',dir=a.output.parent) as temporary:
  work=Path(temporary);classes=work/'classes';classes.mkdir();raw=work/'BreakListener.class';raw.write_bytes(entries['com/jojodmo/nobreak/BreakListener.class'])
  source=[HERE/'compat-src/PatchNoBreak.java',HERE/'compat-src/village/vale/nobreakcompat/InteractionCompat.java']
  compile_argv=[str(a.java_home/'bin/javac.exe'),'-J-Xmx128m','-J-Djava.io.tmpdir='+str(work),'-source','8','-target','8','-cp',str(a.magma)+os.pathsep+str(a.asm),'-d',str(classes)]+list(map(str,source))
  subprocess.run(compile_argv,check=True)
  patched=work/'BreakListener.patched.class'
  subprocess.run([str(a.java_home/'bin/java.exe'),'-Xmx128m','-cp',str(classes)+os.pathsep+str(a.asm),'PatchNoBreak',str(raw),str(patched)],check=True)
  changed=dict(entries);changed['com/jojodmo/nobreak/BreakListener.class']=patched.read_bytes()
  helper='village/vale/nobreakcompat/InteractionCompat.class';changed[helper]=(classes/helper).read_bytes()
  buffer=io.BytesIO()
  with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,data in changed.items():
    info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
  candidate=buffer.getvalue()
  with zipfile.ZipFile(io.BytesIO(candidate)) as z:
   assert z.testzip() is None
   assert all(z.read(name)==data for name,data in entries.items() if name!='com/jojodmo/nobreak/BreakListener.class')
   assert all(int.from_bytes(z.read(name)[6:8],'big')==52 for name in z.namelist() if name.endswith('.class'))
  temp=work/'candidate.jar';temp.write_bytes(candidate)
  # Atomic no-replace publication on NTFS (same-volume hard link); never overwrite a candidate.
  assert a.source.read_bytes()==original,'Original input changed during review'
  os.link(temp,a.output)
 receipt=dict(schema=1,scope='PRIVATE_PINNED_NOBREAK_112_CALL_AND_FIELD_ADAPTER',original_sha256=PIN,original_preserved=a.source.read_bytes()==original,candidate_sha256=sha(candidate),candidate_bytes=len(candidate),patched_call_count=1,farmland_to_soil_alias_count=1,changed_original_entries=['com/jojodmo/nobreak/BreakListener.class'],added_original_helper=helper,unchanged_entry_count=len(entries)-1,plugin_metadata_unchanged=True,helper_source_sha256=sha(source[1].read_bytes()),patcher_source_sha256=sha(source[0].read_bytes()),runtime_accepted=False)
 with a.output.with_suffix('.receipt.json').open('x',encoding='utf-8') as stream:json.dump(receipt,stream,indent=2);stream.write('\n')
 print(json.dumps(receipt))
if __name__=='__main__':main()
