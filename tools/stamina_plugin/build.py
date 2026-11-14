#!/usr/bin/env python3
"""Build only the original stamina plugin against the exact private Magma API."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
MAGMA_SHA256 = "2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce"
OWN_CLASS = "village/naruto/stamina/NarutoStamina.class"
ALLOWED = {OWN_CLASS, "plugin.yml", "config.yml"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_jar(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != ALLOWED:
            raise ValueError("Unexpected or duplicate runtime archive entry")
        if archive.testzip() is not None:
            raise ValueError("Plugin CRC verification failed")
        raw = archive.read(OWN_CLASS)
        if raw[:4] != bytes.fromhex("cafebabe") or int.from_bytes(raw[6:8], "big") != 52:
            raise ValueError("Original Java8 plugin class required")
    return {"file": path.name, "bytes": path.stat().st_size, "sha256": digest(path),
            "java_class_major": 52, "crc_passed": True, "entries": sorted(ALLOWED)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8-home", type=Path, required=True)
    parser.add_argument("--magma", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError("Preserve existing runtime candidates; choose a new output")
    if not args.magma.is_file() or digest(args.magma) != MAGMA_SHA256:
        raise ValueError("Exact inspected Magma1.12.2 compile input required")
    javac = args.java8_home / "bin" / "javac.exe"
    if not javac.is_file():
        javac = args.java8_home / "bin" / "javac"
    version = subprocess.run([str(javac), "-version"], capture_output=True, text=True, check=True)
    if "javac 1.8." not in version.stdout + version.stderr:
        raise ValueError("An actual Java8 compiler is required")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="naruto-stamina-", dir=args.output.parent) as scratch:
        classes = Path(scratch) / "classes"
        classes.mkdir()
        source = ROOT / "src" / "village" / "naruto" / "stamina" / "NarutoStamina.java"
        subprocess.run([str(javac), "-J-Xmx128m", "-source", "8", "-target", "8", "-encoding", "UTF-8",
                        "-cp", str(args.magma.resolve()), "-d", str(classes), str(source)], check=True)
        compiled = [(p.relative_to(classes).as_posix(), p) for p in classes.rglob("*.class")]
        files = compiled + [(name, ROOT / name) for name in ("plugin.yml", "config.yml")]
        if {name for name, _ in files} != ALLOWED:
            raise ValueError("Compiler output exceeded the original class allowlist")
        with zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, path in sorted(files):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, path.read_bytes())
    print(json.dumps(verify_jar(args.output), indent=2))


if __name__ == "__main__":
    main()
