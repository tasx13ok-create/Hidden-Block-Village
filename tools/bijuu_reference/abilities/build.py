"""Layer the server-side native-arts listener onto a private fitted armor build."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile


def build(source, output, javac, classpath):
    with tempfile.TemporaryDirectory() as scratch:
        root=Path(scratch)
        subprocess.run([javac,"--release","8","-cp",os.pathsep.join(map(str,[source,*classpath])),
                        "-d",root,Path(__file__).with_name("NativeArmorArts.java"),Path(__file__).with_name("ChakraBridge.java")],check=True)
        with ZipFile(source) as original, ZipFile(output,"x",ZIP_DEFLATED) as candidate:
            replacement="village/bijuu/ChakraBridge.class"
            assert replacement in original.namelist()
            for info in original.infolist():
                if info.filename==replacement:
                    continue
                candidate.writestr(info,original.read(info))
            for compiled in root.rglob("*.class"):
                name=compiled.relative_to(root).as_posix()
                if name!=replacement and name in original.namelist():
                    raise ValueError("Input already contains "+name)
                candidate.write(compiled,name)


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    for arg in ("source","output","javac"):
        parser.add_argument(arg,type=Path)
    parser.add_argument("classpath",nargs="+",type=Path)
    args=parser.parse_args()
    build(args.source,args.output,args.javac,args.classpath)
