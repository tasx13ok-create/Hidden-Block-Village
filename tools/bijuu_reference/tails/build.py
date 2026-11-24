"""Build a private motion candidate against the owner's Steam Dash 2.0 assembly."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

MODELS = ["ClientProxy$BeastModel", "ModelShukakuArmor", "ModelMatatabiArmor", "ModelKuramaArmor"]


def build(base, output, minecraft, forge, asm):
    src = Path(__file__).parent
    with tempfile.TemporaryDirectory() as scratch, ZipFile(base) as original:
        work = Path(scratch)
        cp = os.pathsep.join(map(str, (base, minecraft, forge, asm)))
        sources = ["MotionPatch", "WearerMotion", "ReferenceTails", "ArmorSprint", "SprintCurve"]
        subprocess.run(["javac", "--release", "8", "-cp", cp, "-d", work,
                        *(src / (s + ".java") for s in sources)], check=True)
        for model in MODELS:
            path = Path("village/bijuu") / (model + ".class")
            target = work / path
            target.write_bytes(original.read(path.as_posix()))
            subprocess.run(["java", "-cp", str(work) + os.pathsep + str(asm), "MotionPatch", target], check=True)
        updates = {p.relative_to(work).as_posix(): p for p in (work / "village").rglob("*.class")}
        existing = set(original.namelist())
        allowed = {"village/bijuu/" + n + ".class" for n in [*MODELS, "ReferenceTails", "ReferenceTails$Link"]}
        collisions = (updates.keys() & existing) - allowed
        if collisions:
            raise ValueError(f"Unexpected replacement in private input: {sorted(collisions)}")
        with ZipFile(output, "x", ZIP_DEFLATED) as result:
            for entry in original.infolist():
                if entry.filename not in updates:
                    result.writestr(entry, original.read(entry))
            for name, path in updates.items():
                result.write(path, name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("base", "output", "minecraft", "forge", "asm"):
        parser.add_argument(name, type=Path)
    build(**vars(parser.parse_args()))
