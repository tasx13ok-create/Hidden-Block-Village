#!/usr/bin/env python3
"""Export verified source/build inputs to a fresh directory on another drive.

This preserves the checkout and its outputs. It copies no ignored local/runtime
folders and never bypasses the build/native verifier's path containment rules.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools import build_modules

PREFIXES = ("modules/", "gradle/", "content/expeditions/", "tools/")
ROOT_FILES = {"gradlew", "gradlew.bat", "gradle.properties", "settings.gradle", "settings.gradle.kts"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def export(destination):
    destination = destination.absolute()
    if destination.exists() or destination == ROOT or ROOT in destination.parents:
        raise ValueError("Build export requires a new directory outside the checkout")
    for parent in destination.parents:
        if parent.is_symlink() or parent.is_junction():
            raise ValueError("Build export may not traverse links or junctions")
    before = build_modules.source_snapshot(ROOT)
    tracked = subprocess.check_output(["git", "ls-files", "-z", "--cached"], cwd=ROOT).decode("utf-8").split("\0")
    untracked = subprocess.check_output(["git", "ls-files", "-z", "--others", "--exclude-standard"], cwd=ROOT).decode("utf-8").split("\0")
    # Checks also consume reviewed artwork/design manifests outside modules.
    # Include the complete tracked checkout, plus only new source/build inputs.
    paths = sorted({name for name in tracked if name} | {name for name in untracked if name and (name.startswith(PREFIXES) or name in ROOT_FILES)})
    destination.mkdir(parents=True, exist_ok=False)
    copied = []
    for name in paths:
        path = Path(name)
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Unsafe source path")
        source = ROOT / path
        if not source.exists():
            continue  # Git-tracked deletion is part of the current source state.
        for entry in (source, *source.parents):
            if entry == ROOT.parent:
                break
            if entry.is_symlink() or entry.is_junction():
                raise ValueError("Build export may not follow source links")
        if not source.is_file():
            raise ValueError("Expected a regular build input")
        output = destination / path
        output.parent.mkdir(parents=True, exist_ok=True)
        expected = digest(source)
        shutil.copyfile(source, output)
        if digest(output) != expected or digest(source) != expected:
            raise ValueError("Source changed during build export: " + name)
        copied.append({"path": name, "sha256": expected, "bytes": output.stat().st_size})
    after = build_modules.source_snapshot(ROOT)
    exported = build_modules.source_snapshot(destination)
    if before["sha256"] != after["sha256"] or before["sha256"] != exported["sha256"]:
        raise ValueError("Source snapshot changed or export is incomplete; preserve failed candidate")
    report = {"schema": 1, "scope": "BUILD_INPUT_EXPORT_ONLY", "created_utc": datetime.now(timezone.utc).isoformat(),
              "source_commit": before["git_commit"], "source_snapshot_sha256": before["sha256"], "source_unchanged": True,
              "export_snapshot_equal": True, "copied": copied, "minecraft_launched": False}
    (destination / "BUILD-EXPORT.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return {key: value for key, value in report.items() if key != "copied"} | {"files": len(copied), "destination": str(destination)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(export(args.destination), indent=2))
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("Build export failed: " + str(error), file=sys.stderr)
        raise SystemExit(1)
