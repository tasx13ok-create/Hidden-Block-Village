#!/usr/bin/env python3
"""Build recovered source modules and record their actual outputs.

This does not assemble or install a server/client pack. Native, saved-world,
graphics, and hosted acceptance remain separate from component builds.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[1]
MODULES = {
    "vale-orders": (),
    "vale-encounters": ("vale-orders",),
    "vale-rack": ("vale-orders",),
    "vale-residents": ("vale-orders",),
    "vale-residents-server-bridge": ("vale-residents",),
    "vale-world-compat": ("vale-orders",),
    "field-hud": (),
    "field-controls": (),
    "vale-compatibility-helpers": ("vale-orders",),
    "server-bootstrap": (),
    "village-standards": (),
}
CLASS_PREFIXES = {
    "vale-orders": ("village/vale/",),
    "vale-encounters": ("village/encounters/",),
    "vale-rack": ("village/valerack/",),
    "vale-residents": ("village/residents/",),
    "vale-residents-server-bridge": ("village/residentsbridge/",),
    "vale-world-compat": ("village/vale/worldcompat/",),
    "field-hud": ("village/fieldhud/",),
    "field-controls": ("village/controls/",),
    "vale-compatibility-helpers": (
        "village/vale/clientcompat/", "village/vale/damagecompat/",
        "village/vale/serverbridge/",
    ),
    "client-lifecycle": ("village/vale/clientcompat/",),
    "damage-compat": ("village/vale/damagecompat/",),
    "server-bridge": ("village/vale/serverbridge/",),
    "server-bootstrap": ("village/deployment/",),
    "village-standards": ("village/standards/",),
}
ROLES = {"runtime_mod", "development_api", "sources", "bootstrap_library", "resource_overlay"}


class BuildError(RuntimeError):
    pass


def build_plan(selected):
    result = []
    seen = set()

    def visit(module):
        if module not in MODULES:
            raise BuildError(f"Unknown module: {module}")
        if module not in seen:
            seen.add(module)
            for dependency in MODULES[module]:
                visit(dependency)
            result.append(module)

    for name in selected:
        visit(name)
    return result


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def source_snapshot(root=None):
    """Hash source/build inputs, excluding generated output and private fixtures."""
    root = root or ROOT
    paths = set()
    excluded = {"build", ".gradle", "__pycache__", "logs", "crash-reports", "run",
                "recoverychecks", "clientchecks", "worldchecks"}
    for area in ("modules", "gradle"):
        start = root / area
        if not start.is_dir():
            continue
        for directory, subdirs, files in os.walk(start):
            subdirs[:] = sorted(name for name in subdirs if name not in excluded)
            for name in files:
                path = Path(directory) / name
                if path.suffix not in {".jar", ".zip", ".class", ".pyc", ".log"}:
                    paths.add(path)
    for name in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "tools/build_modules.py"):
        path = root / name
        if path.is_file():
            paths.add(path)
    records = []
    for path in sorted(paths, key=lambda item: item.relative_to(root).as_posix()):
        if path.is_symlink():
            raise BuildError(f"Source snapshot does not follow symlinks: {path}")
        records.append({"path": path.relative_to(root).as_posix(), "bytes": path.stat().st_size, "sha256": sha256(path)})
    payload = json.dumps(records, sort_keys=True, separators=(",", ":")).encode()
    try:
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, stderr=subprocess.DEVNULL, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        commit = None
    return {"schema": 1, "git_commit": commit, "sha256": hashlib.sha256(payload).hexdigest(),
            "files": records, "private_native_fixture_included": False}


def inspect_artifact(path, role, module):
    """Reject upstream classes, private probes, unsupported bytecode, bad ZIPs."""
    if role not in ROLES or not path.is_file() or path.is_symlink():
        raise BuildError(f"Invalid declared artifact: {path} ({role})")
    before = path.stat()
    classes = 0
    majors = set()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise BuildError(f"Duplicate archive entries: {path.name}")
        for name in names:
            parts = name.rstrip("/").split("/")
            if name.startswith("/") or "\\" in name or any(p in ("", ".", "..") for p in parts):
                raise BuildError(f"Unsafe archive path in {path.name}: {name}")
            if name.endswith(".class"):
                if not name.startswith(CLASS_PREFIXES[module]):
                    raise BuildError(f"Unexpected bundled class in {path.name}: {name}")
                simple = name.rsplit("/", 1)[-1].split("$", 1)[0]
                if simple.endswith(".class"):
                    simple = simple[:-len(".class")]
                if any(word in simple for word in ("NativeChecks", "WorldChecks", "RecoveryChecks", "RecoveryNativeChecks", "GameplayRecoveryChecks", "ClientChecks")):
                    raise BuildError(f"Private test class in production output: {name}")
                raw = archive.read(name)
                if len(raw) < 8 or raw[:4] != b"\xca\xfe\xba\xbe":
                    raise BuildError(f"Invalid class header: {name}")
                major = struct.unpack(">H", raw[6:8])[0]
                if major != 52:
                    raise BuildError(f"Java 8 class required; {name} has major {major}")
                classes += 1
                majors.add(major)
        corrupt = archive.testzip()
        if corrupt:
            raise BuildError(f"CRC failure in {path.name}: {corrupt}")
    if role in {"runtime_mod", "development_api", "bootstrap_library"} and not classes:
        raise BuildError(f"No compiled classes in {role}: {path.name}")
    if role in {"resource_overlay", "sources"} and classes:
        raise BuildError(f"Compiled classes unexpectedly bundled in {role}: {path.name}")
    digest = sha256(path)
    after = path.stat()
    if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
        raise BuildError(f"Artifact changed during verification: {path}")
    return {
        "bytes": after.st_size, "sha256": digest,
        "archive_entries": len(names), "class_count": classes,
        "class_major_versions": sorted(majors), "zip_crc_verified": True,
    }


def read_metadata(module, root=None):
    root = root or ROOT
    directory = root / "modules" / module
    path = directory / "build" / "vale-build.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema") != 1 or data.get("minecraft_launched") is not False:
        raise BuildError(f"Invalid component metadata: {module}")
    entries = data.get("artifacts")
    if not isinstance(entries, list) or not entries:
        raise BuildError(f"Missing artifacts for {module}")
    seen = set()
    for entry in entries:
        artifact = Path(entry["path"])
        if not artifact.is_absolute() or artifact.is_symlink():
            raise BuildError(f"Artifact path must be an absolute regular file: {artifact}")
        relative = artifact.resolve().relative_to(directory.resolve())
        if "build" not in relative.parts or artifact in seen:
            raise BuildError(f"Unexpected or duplicate artifact declaration: {artifact}")
        seen.add(artifact)
        component = entry.get("component", module)
        if module == "vale-compatibility-helpers" and component not in {"client-lifecycle", "damage-compat", "server-bridge"}:
            raise BuildError(f"Missing adapter identity in {module}")
        if module != "vale-compatibility-helpers" and component != module:
            raise BuildError(f"Unexpected component identity: {component}")
        entry.update(inspect_artifact(artifact, entry["role"], component))
        entry["path"] = str(artifact.resolve())
    return data


def development_api(results, name):
    matches = [a["path"] for a in results[name]["artifacts"] if a["role"] == "development_api"]
    if len(matches) != 1:
        raise BuildError(f"Expected exactly one development API from {name}")
    return matches[0]


def prepared_api(path):
    path = path.resolve()
    if not path.is_file():
        raise BuildError(f"Prepared Minecraft API does not exist: {path}")
    with zipfile.ZipFile(path) as archive:
        required = {"net/minecraft/world/World.class", "net/minecraft/server/MinecraftServer.class"}
        if not required.issubset(archive.namelist()):
            raise BuildError("Prepared Minecraft input must be the stable-39 development API, not a runtime or mod JAR")
    return path


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def launcher(args, root=None):
    root = root or ROOT
    if args.gradle_launcher:
        command = Path(args.gradle_launcher).resolve()
        if not command.is_file():
            raise BuildError(f"Gradle launcher not found: {command}")
        return ([sys.executable] if command.suffix == ".py" else []) + [str(command)]
    return [str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modules", nargs="+", choices=list(MODULES), help="Selected modules; dependencies are built first. Default: all source areas.")
    parser.add_argument("--list", action="store_true", help="Print the build plan without invoking Gradle")
    parser.add_argument("--gradle-launcher", type=Path, help="Optional executable or Python wrapper for a managed build environment")
    parser.add_argument("--gradle-arg", action="append", default=[], help="Extra Gradle argument; use --gradle-arg=-Pname=value")
    parser.add_argument("--java8-home", type=Path, default=os.environ.get("VALE_JAVA8_HOME"), help="Use an installed Azul JDK8; defaults to VALE_JAVA8_HOME")
    parser.add_argument("--prebuilt-minecraft", type=Path, help="Explicit existing Forge stable-39 development API; normally prepared by the Core build")
    args = parser.parse_args(argv)
    plan = build_plan(args.modules or MODULES)
    if args.list:
        print(json.dumps({"modules": plan, "minecraft_launched": False}, indent=2))
        return 0
    prefix = launcher(args)
    extra = list(args.gradle_arg)
    if args.java8_home:
        java8 = args.java8_home.resolve()
        if not (java8 / "bin" / ("javac.exe" if os.name == "nt" else "javac")).is_file():
            raise BuildError(f"Java8 compiler not found: {java8}")
        paths = [str(java8)]
        if os.environ.get("JAVA_HOME"):
            paths.insert(0, os.environ["JAVA_HOME"])
        extra += ["-Porg.gradle.java.installations.paths=" + ",".join(paths),
                  "-Porg.gradle.java.installations.auto-download=false"]
    api = prepared_api(args.prebuilt_minecraft) if args.prebuilt_minecraft else None
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    work = ROOT / "build" / "source-builds" / stamp
    work.mkdir(parents=True, exist_ok=False)
    artifacts_root = work / "artifacts"
    before_source = source_snapshot()
    write_json(work / "source-inputs.json", before_source)
    report = {
        "schema": 1, "started_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "SOURCE_COMPONENT_BUILDS", "minecraft_launched": False,
        "saved_world_tested": False, "hosted_acceptance": False,
        "plan": plan, "results": {}, "artifacts_directory": str(artifacts_root.relative_to(ROOT)),
        "source_snapshot": {"sha256": before_source["sha256"], "git_commit": before_source["git_commit"],
                            "path": str((work / "source-inputs.json").relative_to(ROOT))},
    }
    results = report["results"]
    for module in plan:
        failed = [d for d in MODULES[module] if results[d]["status"] != "PASS"]
        if failed:
            results[module] = {"status": "SKIPPED", "reason": "Failed dependencies: " + ", ".join(failed)}
            print(f"SKIPPED {module}: failed dependencies", flush=True)
            continue
        directory = ROOT / "modules" / module
        metadata = directory / "build" / "vale-build.json"
        metadata.unlink(missing_ok=True)
        log = work / (module + ".log")
        command = prefix + ["--no-daemon", "--console=plain", "-p", str(directory)] + extra
        if "vale-orders" in MODULES[module]:
            command.append("-PvaleCoreDevJar=" + development_api(results, "vale-orders"))
        if module == "vale-residents-server-bridge":
            command.append("-PresidentApiJar=" + development_api(results, "vale-residents"))
        if api and module != "server-bootstrap":
            command.append("-PprebuiltMinecraft=" + str(api))
        command.append("writeValeBuildMetadata")
        print(f"Building {module}; log: {log.relative_to(ROOT)}", flush=True)
        try:
            with log.open("w", encoding="utf-8") as stream:
                completed = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
            if completed.returncode:
                raise BuildError(f"Gradle exited with status {completed.returncode}; see {log.relative_to(ROOT)}")
            data = read_metadata(module)
            for entry in data["artifacts"]:
                artifact = Path(entry["path"])
                target = artifacts_root / module / entry["role"] / artifact.name
                target.parent.mkdir(parents=True, exist_ok=True)
                if target.exists():
                    raise BuildError(f"Duplicate artifact destination: {target}")
                shutil.copyfile(artifact, target)
                if sha256(target) != entry["sha256"]:
                    raise BuildError(f"Artifact changed while copying: {artifact}")
                entry["copied_path"] = str(target.relative_to(ROOT))
            results[module] = {"status": "PASS", **data, "log": str(log.relative_to(ROOT))}
            if module == "vale-orders" and not api:
                prepared = directory / "build" / "rfg" / "recompiled_minecraft-1.12.2.jar"
                if prepared.is_file():
                    api = prepared_api(prepared)
            print(f"PASS {module}: {len(data['artifacts'])} declared outputs checked", flush=True)
        except (BuildError, OSError, ValueError, zipfile.BadZipFile) as error:
            results[module] = {"status": "FAIL", "error": str(error), "log": str(log.relative_to(ROOT))}
            print(f"FAIL {module}: {error}", flush=True)
        write_json(work / "report.json", report)
    report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    report["passed"] = all(r["status"] == "PASS" for r in results.values())
    after_source = source_snapshot()
    if after_source["sha256"] != before_source["sha256"]:
        before_files = {f["path"]: f["sha256"] for f in before_source["files"]}
        after_files = {f["path"]: f["sha256"] for f in after_source["files"]}
        report["source_changed_during_build"] = sorted(p for p in before_files.keys() | after_files.keys()
                                                       if before_files.get(p) != after_files.get(p))
        report["passed"] = False
        write_json(work / "source-inputs-after.json", after_source)
    else:
        report["source_changed_during_build"] = []
    if api:
        report["prepared_minecraft_api"] = {"path": str(api), "sha256": sha256(api)}
    write_json(work / "report.json", report)
    write_json(ROOT / "build" / "source-build-report.json", report)
    print(f"Report: {(work / 'report.json').relative_to(ROOT)}", flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (BuildError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"Build setup failed: {error}", file=sys.stderr)
        raise SystemExit(2)
