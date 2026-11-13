#!/usr/bin/env python3
"""Run the private Core/Standards fixture in a fresh disposable Forge server.

Python 3.8+, standard library only. Requires a successful current component
build, Java 25 for Gradle, and VALE_JAVA8_HOME for the isolated Java 8 server.
No existing world or server directory is accepted as an input.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import uuid
import zipfile

import assemble_release as assembly
import build_modules as builds
import release_pack as pack


ROOT = Path(__file__).resolve().parents[1]
FORGE_VERSION = "14.23.5.2859"
FORGE_FILE = "forge-1.12.2-" + FORGE_VERSION + ".jar"
FORGE_SHA256 = "bd8314729d787e35164476d75afb337afb9783917e4f6fa1f77f049adba22f2d"
INSTALLER_URL = ("https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-" + FORGE_VERSION
                 + "/forge-1.12.2-" + FORGE_VERSION + "-installer.jar")
INSTALLER_SHA256 = "dbba1cd1bf34a4b82148bac50a6150f63ffcb2d5dc7d88a0508c27fdcff15b76"
INSTALLER_SHA1 = "6b3d5be8b58d5385fda9c4d285367f4cb6e697b2"
INSTALLER_BYTES = 4681890
FIXTURE = "native-recovery-0.3.9"
RESULT_FILE = "native-recovery039-result.json"
PASS_MARKER = "VALE_RECOVERY039_NATIVE_PASS:"
NEW_CHECK = "Native scroll cast reentry, resolve reservation, held-stack replacement and protected return teleport"
REED_ESCAPE_CHECK = "Native Reed Escape teleport protection, recursive callbacks and rejected movement cleanup"
REQUIRED_CHECKS = (NEW_CHECK, REED_ESCAPE_CHECK)
# Module, declared component, and actual Forge mod ID. No client-only adapters.
PRODUCTION = (
    ("vale-orders", "vale-orders", "valeorders"),
    ("vale-encounters", "vale-encounters", "valeencounters"),
    ("vale-rack", "vale-rack", "valerack"),
    ("vale-residents", "vale-residents", "valeresidents"),
    ("vale-residents-server-bridge", "vale-residents-server-bridge", "residentserverbridge"),
    ("vale-world-compat", "vale-world-compat", "valeworldcompat"),
    ("vale-compatibility-helpers", "damage-compat", "valedamagecompat"),
    ("vale-compatibility-helpers", "server-bridge", "valeserverbridge"),
    ("village-standards", "village-standards", "hvdungeonpictures"),
)
HELPERS = (
    ("vale-orders", "recoverychecks", "recoveryChecks", "reobfRecoveryChecksJar", "vale-recoverychecks", "valerecoverychecks"),
    ("village-standards", "worldchecks", "nativeChecks", "reobfStandardsNativeChecksJar", "vale-standards-checks", "valestandardschecks"),
)
# Only private compilation/reobfuscation inputs change. Production build tasks
# remain intact, and their original reported artifacts are verified afterwards.
PRIVATE_CLASSPATH = """allprojects { p ->
    p.afterEvaluate {
        def name = p.findProperty('valeNativeSourceSet')
        def task = p.findProperty('valeNativeTask')
        if (name && task && p.tasks.findByName(task)) {
            def fixture = p.sourceSets.getByName(name)
            def inputs = p.files(p.findProperty('valeNativeProductionApi'))
            if (p.findProperty('valeRackDevJar')) inputs += p.files(p.findProperty('valeRackDevJar'))
            fixture.compileClasspath = inputs + p.sourceSets.main.compileClasspath
            fixture.runtimeClasspath = fixture.output + fixture.compileClasspath
            p.tasks.named(task) { referenceClasspath.setFrom(fixture.compileClasspath) }
        }
    }
}
"""


class NativeError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise NativeError(message)


def checked_identity(path, expected):
    actual = pack.hash_file(path)
    require(actual == {"bytes": expected["bytes"], "sha256": expected["sha256"]},
            "Artifact bytes differ from the successful component build: " + str(path))
    return actual


def private_snapshot(root):
    paths = [root / "tools/run_native_checks.py"]
    for module, directory, _, _, _, _ in HELPERS:
        sources = sorted((root / "modules" / module / directory).rglob("*.java"))
        require(sources, "Missing private fixture sources: " + module)
        paths.extend(sources)
    records = [{"path": p.relative_to(root).as_posix(), **pack.hash_file(pack.no_links(p))}
               for p in sorted(paths)]
    return {"files": records, "sha256": pack.digest(records)}


def load_build_inputs(report_path, root=ROOT):
    report, report_identity = pack.read_json(report_path)
    require(type(report.get("schema")) is int and report["schema"] == 1 and report.get("passed") is True,
            "A successful source component build report is required")
    results = report.get("results", {})
    require(results and all(row.get("status") == "PASS" for row in results.values()),
            "Every reported source component must have passed")
    snapshot = builds.source_snapshot(root)
    require(report.get("source_snapshot", {}).get("sha256") == snapshot["sha256"],
            "Current source differs from the successful component build; rebuild first")
    artifacts_root = pack.no_links(root / pack.safe_relative(report["artifacts_directory"]))
    require(root / "build/source-builds" in artifacts_root.parents and artifacts_root.name == "artifacts",
            "Component copies must be inside their source-build run")
    selected = []

    def select(module, component, role):
        require(module in results, "Missing component build: " + module)
        rows = [row for row in results[module]["artifacts"]
                if row.get("role") == role and row.get("component", module) == component]
        require(len(rows) == 1, "Expected one %s output for %s" % (role, component))
        row = rows[0]
        pack.identity(row)
        original = pack.no_links(Path(row["path"]))
        require(Path(row["path"]).is_absolute() and root / "modules" / module in original.parents
                and "build" in original.relative_to(root / "modules" / module).parts,
                "Unexpected original component artifact path")
        copied = pack.no_links(root / pack.safe_relative(row["copied_path"]))
        require(copied == artifacts_root / module / role / original.name,
                "Unexpected checked component copy path")
        checked_identity(original, row)
        checked_identity(copied, row)
        inspected = builds.inspect_artifact(copied, role, component)
        require(inspected["sha256"] == row["sha256"], "Component archive changed during inspection")
        version = row.get("version", results[module].get("version"))
        require(isinstance(version, str) and version, "Missing component version")
        value = {"module": module, "component": component, "role": role, "version": version,
                 "file": copied.name, "bytes": row["bytes"], "sha256": row["sha256"],
                 "path": copied, "original": original}
        selected.append(value)
        return value

    runtime = []
    for module, component, mod_id in PRODUCTION:
        value = select(module, component, "runtime_mod")
        require(assembly.jar_mods(value["path"]) == {mod_id: value["version"]},
                "Production mod identity differs from its component declaration: " + component)
        value["mod_id"] = mod_id
        runtime.append(value)
    apis = {module: select(module, module, "development_api")
            for module in ("vale-orders", "vale-rack", "village-standards")}
    for module, api in apis.items():
        production = next(row for row in runtime if row["module"] == module)
        require(api["version"] == production["version"] and
                assembly.jar_mods(api["path"]) == {production["mod_id"]: production["version"]},
                "Development API and production mod versions differ: " + module)
    require(apis["vale-orders"]["version"] in ("0.3.9", "0.3.10"), "This native fixture requires Core 0.3.9 or 0.3.10")
    require(apis["village-standards"]["version"] == "0.3.1", "This native fixture requires Standards 0.3.1")
    prepared = report.get("prepared_minecraft_api", {})
    api = builds.prepared_api(pack.no_links(Path(prepared["path"])))
    require(pack.hash_file(api)["sha256"] == prepared["sha256"], "Prepared Minecraft API hash differs")
    pack.check_names([{"path": row["file"], "kind": "file"} for row in runtime])
    return {"report_identity": report_identity, "source_snapshot": snapshot, "runtime": runtime,
            "apis": apis, "selected": selected, "minecraft_api": api,
            "minecraft_api_sha256": prepared["sha256"]}


def recheck_inputs(inputs, report_path, root):
    checked_identity(report_path, inputs["report_identity"])
    require(builds.source_snapshot(root)["sha256"] == inputs["source_snapshot"]["sha256"],
            "Source changed during native checks")
    for row in inputs["selected"]:
        checked_identity(row["path"], row)
        checked_identity(row["original"], row)
    require(pack.hash_file(inputs["minecraft_api"])["sha256"] == inputs["minecraft_api_sha256"],
            "Prepared Minecraft API changed during native checks")


def kill_process_tree(process):
    """Every started process owns a fresh group; do not touch unrelated servers."""
    if os.name == "nt":
        if process.poll() is None:
            try:
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15, check=True)
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=15)
    else:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    if process.poll() is None:
        process.kill()
    process.wait(timeout=15)


def run_process(command, cwd, log, timeout, steps, label):
    require(timeout > 0, "Process timeout must be positive")
    step = {"name": label, "log": log.name, "timeout_seconds": timeout, "exit_code": None, "timed_out": False}
    steps.append(step)
    print("Native checks: " + label, flush=True)
    started = time.monotonic()
    options = {"start_new_session": True} if os.name != "nt" else {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
    with log.open("xb") as output:
        process = subprocess.Popen([str(part) for part in command], cwd=str(cwd), stdin=subprocess.DEVNULL,
                                   stdout=output, stderr=subprocess.STDOUT, **options)
        try:
            while True:
                remaining = timeout - (time.monotonic() - started)
                if remaining <= 0:
                    step["timed_out"] = True
                    raise NativeError(label + " exceeded its process timeout")
                try:
                    step["exit_code"] = process.wait(timeout=min(30, remaining))
                    break
                except subprocess.TimeoutExpired:
                    print("Native checks: " + label + " is still running", flush=True)
        finally:
            # POSIX also closes descendants after a successful group leader exits.
            # Windows taskkill needs a still-running parent to identify its tree.
            kill_process_tree(process)
            if step["exit_code"] is None:
                step["exit_code"] = process.returncode
            step["seconds"] = round(time.monotonic() - started, 3)
    require(step["exit_code"] == 0, label + " exited with status " + str(step["exit_code"]) + "; see " + log.name)


def java_executable(home):
    require(home is not None, "JAVA_HOME (25) and VALE_JAVA8_HOME are required")
    home = Path(home).resolve()
    java = home / "bin" / ("java.exe" if os.name == "nt" else "java")
    compiler = home / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    require(java.is_file() and compiler.is_file(), "A complete JDK is required: " + str(home))
    return java


def verify_private_jar(path, module, source_dir, mod_id, version, root):
    allowed = {p.relative_to(root / "modules" / module / source_dir).with_suffix("").as_posix()
               for p in (root / "modules" / module / source_dir).rglob("*.java")}
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        pack.check_names([{"path": n.rstrip("/"), "kind": "directory" if n.endswith("/") else "file"} for n in names])
        classes = [n for n in names if n.endswith(".class")]
        require(classes, "Private fixture has no classes")
        for name in classes:
            outer = name[:-6].split("$", 1)[0]
            require(outer in allowed, "Unexpected bundled class in private fixture: " + name)
            raw = archive.read(name)
            require(len(raw) >= 8 and raw[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(raw[6:8], "big") == 52,
                    "Private fixture must contain Java 8 bytecode")
        require(archive.testzip() is None, "Private fixture ZIP CRC failure")
    require(assembly.jar_mods(path) == {mod_id: version}, "Private fixture mod identity differs")
    if mod_id == "valerecoverychecks":
        for name in ("ScrollCastNativeChecks039", "ReedEscapeNativeChecks039"):
            require("village/vale/arsenal/" + name + ".class" in classes,
                    "The new Core 0.3.9 native callback checks are missing: " + name)
        if version == "0.3.10":
            require("village/vale/AcademyNativeChecks.class" in classes, "Core 0.3.10 academy fixture class is missing")
    return {"mod_id": mod_id, "version": version, "file": path.name, "path": path, **pack.hash_file(path)}


def build_helpers(inputs, temporary, evidence, args, steps, root):
    init = temporary / "private-classpath.gradle"
    init.write_text(PRIVATE_CLASSPATH, encoding="utf-8")
    prefix = builds.launcher(args, root)
    extra = list(args.gradle_arg)
    extra += ["-Porg.gradle.java.installations.paths=" + os.environ["JAVA_HOME"] + "," + str(args.java8_home.resolve()),
              "-Porg.gradle.java.installations.auto-download=false", "-PprebuiltMinecraft=" + str(inputs["minecraft_api"])]
    helpers = []
    for module, source_dir, source_set, task, basename, mod_id in HELPERS:
        version = inputs["apis"][module]["version"]
        output = pack.no_links(root / "modules" / module / "build/private-native-checks" / (basename + "-" + version + ".jar"))
        # A successful Gradle exit cannot authorize a stale helper from an old run.
        if output.exists():
            output.unlink()
        command = prefix + ["--no-daemon", "--console=plain", "-I", str(init), "-p", str(root / "modules" / module)] + extra
        command += ["-PvaleNativeSourceSet=" + source_set, "-PvaleNativeTask=" + task,
                    "-PvaleNativeProductionApi=" + str(inputs["apis"][module]["path"])]
        if module == "vale-orders":
            command.append("-PvaleRackDevJar=" + str(inputs["apis"]["vale-rack"]["path"]))
        command.append(task)
        run_process(command, root, evidence / (module + "-private-build.log"), 300, steps, "compile " + module + " private fixture")
        helpers.append(verify_private_jar(pack.no_links(output), module, source_dir, mod_id, version, root))
    return helpers


def obtain_installer(supplied, temporary, evidence, steps):
    target = temporary / "forge-installer.jar"
    expected = {"bytes": INSTALLER_BYTES, "sha256": INSTALLER_SHA256}
    if supplied is not None:
        checked_identity(pack.no_links(supplied), expected)
        assembly.copy_checked(supplied, target, expected)
    else:
        curl = shutil.which("curl")
        require(curl, "curl is required to download the official Forge installer")
        for suffix, output, bound in (("", target, 16 * 1024**2), (".sha1", temporary / "installer.sha1", 512)):
            command = [curl, "--fail", "--show-error", "--location", "--proto", "=https", "--proto-redir", "=https",
                       "--connect-timeout", "20", "--max-time", "120", "--max-filesize", str(bound),
                       "--output", str(output), INSTALLER_URL + suffix]
            run_process(command, temporary, evidence / ("installer-sha1-download.log" if suffix else "installer-download.log"),
                        150, steps, "download official Forge installer" + suffix)
        vendor = (temporary / "installer.sha1").read_text(encoding="ascii").strip().lower()
        require(re.fullmatch(r"[0-9a-f]{40}", vendor) is not None and vendor == INSTALLER_SHA1,
                "Official Forge installer SHA1 differs from the reviewed pin")
    checked_identity(target, expected)
    with pack.stable_reader(target) as stream:
        digest = hashlib.sha1()
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    require(digest.hexdigest() == INSTALLER_SHA1, "Forge installer SHA1 mismatch")
    return target


def stage_mods(runtime, artifacts):
    mods = runtime / "mods"
    mods.mkdir(exist_ok=True)
    require(not any(mods.iterdir()), "Disposable runtime unexpectedly contains mods")
    pack.check_names([{"path": row["file"], "kind": "file"} for row in artifacts])
    expected = {}
    for row in artifacts:
        require(row["mod_id"] not in expected, "Repeated fixture mod ID")
        assembly.copy_checked(row["path"], mods / row["file"], row)
        expected[row["mod_id"]] = {key: row[key] for key in ("version", "file", "sha256")}
    require(len(expected) == 11, "The complete fixture requires nine production and two private QA mods")
    return expected


def validate_native_result(value, expected, log):
    require(type(value.get("schema")) is int and value["schema"] == 1 and value.get("fixture") == FIXTURE,
            "Wrong or stale native fixture result")
    require(value.get("passed") is True and not value.get("failure"), "Native fixture did not pass: " + str(value.get("failure", "missing result")))
    require(type(value.get("assertions")) is int and value["assertions"] > 0, "Native fixture reported no assertions")
    require(isinstance(value.get("checks"), list) and all(check in value["checks"] for check in REQUIRED_CHECKS),
            "Core 0.3.9 scroll and Reed Escape callback checks did not complete")
    if expected["valeorders"]["version"] == "0.3.10":
        require("Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect" in value["checks"],
                "Core 0.3.10 academy acceptance did not complete")
    require(value.get("minecraft_version") == "1.12.2" and value.get("forge_version") == FORGE_VERSION,
            "Native result names a different Minecraft/Forge runtime")
    require(value.get("loaded_fixture_mods") == expected, "Loaded native mod names, versions, or hashes differ from the exact staged roster")
    require(value.get("core_sha256") == expected["valeorders"]["sha256"], "Native Core hash differs from the source build")
    output = log.read_text(encoding="utf-8", errors="replace")
    require(PASS_MARKER in output and "VALE_RECOVERY039_NATIVE_FAIL" not in output, "Native success marker is missing or contradicted")


def run(args, root=ROOT):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    evidence = pack.no_links(root / "build/native-checks" / stamp)
    evidence.mkdir(parents=True, exist_ok=False)
    report = {"schema": 1, "scope": "DISPOSABLE_FORGE_NATIVE_CHECKS", "fixture": FIXTURE, "passed": False,
              "started_utc": datetime.now(timezone.utc).isoformat(), "minecraft_process_invoked": False,
              "saved_world_tested": False, "graphical_client_tested": False, "network_protocol_tested": False,
              "hosted_acceptance": False, "bisect_deployed": False, "runtime_removed": False, "steps": []}
    temporary = None
    runtime = None
    try:
        inputs = load_build_inputs(args.build_report, root)
        private = private_snapshot(root)
        report["source_build_report"] = inputs["report_identity"]
        report["source_snapshot"] = {key: inputs["source_snapshot"][key] for key in ("sha256", "git_commit")}
        report["private_fixture_inputs"] = private
        report["compile_inputs"] = {name: {key: row[key] for key in ("file", "bytes", "sha256")}
                                    for name, row in inputs["apis"].items()}
        report["prepared_minecraft_api_sha256"] = inputs["minecraft_api_sha256"]
        java8 = java_executable(args.java8_home)
        java25 = java_executable(os.environ.get("JAVA_HOME"))
        for name, executable, pattern in (("java8", java8, r'version "1\.8\.'), ("java25", java25, r'version "25(?:[.\-+" ])')):
            log = evidence / (name + ".log")
            run_process([executable, "-version"], root, log, 30, report["steps"], "verify " + name)
            require(re.search(pattern, log.read_text(encoding="utf-8", errors="replace")) is not None, "Wrong Java version for " + name)
        temporary = tempfile.TemporaryDirectory(prefix="vale-native039-")
        scratch = Path(temporary.name)
        helpers = build_helpers(inputs, scratch, evidence, args, report["steps"], root)
        recheck_inputs(inputs, args.build_report, root)
        require(private_snapshot(root) == private, "Private fixture source changed during compilation")
        installer = obtain_installer(args.installer, scratch, evidence, report["steps"])
        report["installer"] = {"url": INSTALLER_URL, "bytes": INSTALLER_BYTES, "sha256": INSTALLER_SHA256,
                               "official_sha1": INSTALLER_SHA1, "input": "reviewed_local_file" if args.installer else "official_download"}
        runtime = scratch / "runtime"
        runtime.mkdir()
        cache = getattr(args, "library_cache", None)
        if cache is not None:
            cache = pack.no_links(cache.resolve())
            require(cache.is_dir(), "Forge library cache must be a directory")
            cached = []
            for source in sorted(cache.rglob("*.jar")):
                source = pack.no_links(source)
                destination = runtime / "libraries" / source.relative_to(cache)
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, destination)
                cached.append(source.relative_to(cache).as_posix())
            report["library_cache"] = {"jar_count": len(cached), "validation": "official Forge installer revalidates Maven dependency checksums"}
        run_process([java8, "-Djava.awt.headless=true", "-jar", installer, "--installServer", runtime],
                    scratch, evidence / "forge-install.log", 600, report["steps"], "install official Forge")
        forge = runtime / FORGE_FILE
        forge_identity = pack.hash_file(forge)
        require(forge_identity["sha256"] == FORGE_SHA256, "Installed Forge universal runtime SHA256 differs from the reviewed pin")
        report["forge_runtime"] = {"version": FORGE_VERSION, "file": FORGE_FILE, **forge_identity}
        artifacts = inputs["runtime"] + helpers
        expected = stage_mods(runtime, artifacts)
        report["expected_loaded_mods"] = expected
        report["production_mod_ids"] = [row["mod_id"] for row in inputs["runtime"]]
        report["private_qa_mod_ids"] = [row["mod_id"] for row in helpers]
        (runtime / ".vale-recovery-test").touch(exist_ok=False)
        (runtime / "eula.txt").write_text("eula=true\n", encoding="ascii")
        (runtime / "server.properties").write_text(
            "level-name=vale-recovery-test\nlevel-type=FLAT\nserver-ip=127.0.0.1\nserver-port=0\n"
            "online-mode=false\nenable-rcon=false\nenable-query=false\nspawn-monsters=true\n"
            "spawn-animals=true\ndifficulty=1\nmax-players=1\nview-distance=3\nmax-tick-time=180000\n",
            encoding="ascii")
        server_log = evidence / "native-server.log"
        report["minecraft_process_invoked"] = True
        run_process([java8, "-Xms512M", "-Xmx2G", "-Djava.awt.headless=true", "-Dvaleorders.recoverycheck=true",
                     "-Dvalestandards.nativecheck=true", "-jar", forge, "nogui"], runtime, server_log,
                    args.server_timeout, report["steps"], "run private Core and Standards fixture")
        result, _ = pack.read_json(runtime / RESULT_FILE)
        validate_native_result(result, expected, server_log)
        require({p.name for p in (runtime / "mods").iterdir()} == {row["file"] for row in artifacts},
                "Disposable mod directory changed during the native run")
        for row in artifacts:
            checked_identity(runtime / "mods" / row["file"], row)
        checked_identity(forge, forge_identity)
        recheck_inputs(inputs, args.build_report, root)
        require(private_snapshot(root) == private, "Private fixture source changed during native execution")
        report["assertions"] = result["assertions"]
        report["checks"] = result["checks"]
        report["passed"] = True
    except KeyboardInterrupt:
        report["error"] = "Interrupted; no native pass recorded"
    except (NativeError, builds.BuildError, pack.PackError, OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    finally:
        if runtime is not None and (runtime / RESULT_FILE).exists():
            try:
                result_path = pack.no_links(runtime / RESULT_FILE)
                require(result_path.stat().st_size <= 1024**2, "Native result exceeds its evidence size bound")
                assembly.copy_checked(result_path, evidence / RESULT_FILE, pack.hash_file(result_path))
            except (NativeError, pack.PackError, OSError) as error:
                report["evidence_error"] = str(error)
                report["passed"] = False
        if temporary is not None:
            try:
                temporary.cleanup()
                report["runtime_removed"] = True
            except OSError as error:
                report["cleanup_error"] = str(error)
                report["passed"] = False
        report["finished_utc"] = datetime.now(timezone.utc).isoformat()
        report["status"] = "PASS" if report["passed"] else "FAIL"
        builds.write_json(evidence / "report.json", report)
        builds.write_json(root / "build/native-check-report.json", report)
    print("Native checks: " + report["status"] + "; report: " + str(evidence.relative_to(root) / "report.json"), flush=True)
    if report.get("error"):
        print(report["error"], file=sys.stderr)
    return 0 if report["passed"] else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-report", type=Path, default=ROOT / "build/source-build-report.json")
    parser.add_argument("--java8-home", type=Path, default=os.environ.get("VALE_JAVA8_HOME"))
    parser.add_argument("--gradle-launcher", type=Path, help="Same approved Gradle launcher used for component builds")
    parser.add_argument("--gradle-arg", action="append", default=[], help="Extra Gradle argument; use --gradle-arg=-Pname=value")
    parser.add_argument("--installer", type=Path, help="Optional existing official installer; both fixed hashes are still required")
    parser.add_argument("--library-cache", type=Path, help="Optional Maven library cache; official installer still validates dependencies")
    parser.add_argument("--server-timeout", type=int, default=600, help="Server process limit in seconds (30-1800)")
    args = parser.parse_args(argv)
    if not 30 <= args.server_timeout <= 1800:
        parser.error("--server-timeout must be between 30 and 1800 seconds")
    def interrupt(signum, frame):
        raise KeyboardInterrupt
    previous = signal.signal(signal.SIGTERM, interrupt)
    try:
        return run(args)
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == "__main__":
    sys.exit(main())
