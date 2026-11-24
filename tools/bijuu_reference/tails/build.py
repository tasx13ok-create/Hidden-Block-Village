"""Build a private motion candidate against the owner's Steam Dash 2.0 assembly."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

def build(base, output, minecraft, forge, asm=None):
    src = Path(__file__).parent
    with tempfile.TemporaryDirectory() as scratch, ZipFile(base) as original:
        work = Path(scratch)
        cp = os.pathsep.join(map(str, (base, minecraft, forge)))
        sources = ["ReferenceTails", "ArmorSprint", "SprintCurve"]
        subprocess.run(["javac", "--release", "8", "-cp", cp, "-d", work,
                        *(src / (s + ".java") for s in sources)], check=True)
        updates = {p.relative_to(work).as_posix(): p for p in (work / "village").rglob("*.class")}
        existing = set(original.namelist())
        allowed = {"village/bijuu/ReferenceTails.class"}
        collisions = (updates.keys() & existing) - allowed
        if collisions:
            raise ValueError(f"Unexpected replacement in private input: {sorted(collisions)}")
        with ZipFile(output, "x", ZIP_DEFLATED) as result:
            for entry in original.infolist():
                if entry.filename not in updates and entry.filename != "village/bijuu/ReferenceTails$Link.class":
                    result.writestr(entry, original.read(entry))
            for name, path in updates.items():
                result.write(path, name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("base", "output", "minecraft", "forge"):
        parser.add_argument(name, type=Path)
    parser.add_argument("asm", nargs="?", help=argparse.SUPPRESS)  # Legacy positional argument
    build(**vars(parser.parse_args()))
