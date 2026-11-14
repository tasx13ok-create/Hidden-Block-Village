#!/usr/bin/env python3
"""Build original Java8 server plugin; never packages private/reference classes."""
import argparse,hashlib,json,pathlib,subprocess,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parent
MAGMA='2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 a=argparse.ArgumentParser();a.add_argument('--java8-home',type=pathlib.Path,required=True);a.add_argument('--magma',type=pathlib.Path,required=True);a.add_argument('--output',type=pathlib.Path,required=True);v=a.parse_args()
 if v.output.exists():raise FileExistsError(v.output)
 if digest(v.magma)!=MAGMA:raise ValueError('Wrong private Magma API')
 javac=v.java8_home/'bin/javac.exe';ver=subprocess.check_output([str(javac),'-version'],stderr=subprocess.STDOUT).decode();assert '1.8.' in ver
 sources=sorted((ROOT/'src').rglob('*.java'));before={str(p.relative_to(ROOT)):digest(p) for p in sources}
 v.output.parent.mkdir(parents=True,exist_ok=True);scratch=pathlib.Path(tempfile.mkdtemp(prefix='ninja-health-build-',dir=v.output.parent));classes=scratch/'classes';classes.mkdir()
 subprocess.run([str(javac),'-J-Xmx128m','-encoding','UTF-8','-source','8','-target','8','-cp',str(v.magma),'-d',str(classes)]+[str(p) for p in sources],check=True)
 assert before=={str(p.relative_to(ROOT)):digest(p) for p in sources}
 files={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')}
 assert files and all(n.startswith('village/naruto/health/') for n in files)
 for p in files.values():assert p.read_bytes()[:4]==bytes.fromhex('cafebabe') and int.from_bytes(p.read_bytes()[6:8],'big')==52
 files.update({n:ROOT/n for n in ['plugin.yml','config.yml']})
 with zipfile.ZipFile(v.output,'x',compression=zipfile.ZIP_DEFLATED) as z:
  for n,p in sorted(files.items()):
   info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
 with zipfile.ZipFile(v.output) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
  assert all(hashlib.sha256(z.read(n)).hexdigest()==digest(p) for n,p in files.items())
 report={'file':str(v.output),'bytes':v.output.stat().st_size,'sha256':digest(v.output),'java_major':52,'crc_and_all_entry_hashes_passed':True,'source':before,'magma_api_sha256':MAGMA,'native_acceptance':'PENDING'}
 v.output.with_suffix('.build.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
