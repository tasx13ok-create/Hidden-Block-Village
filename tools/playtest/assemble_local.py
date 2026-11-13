#!/usr/bin/env python3
"""Prepare a new private local playtest copy from checked artifacts and local inputs.

Never imports a launcher, runs Java, installs Forge, downloads dependencies, edits
an input, or copies launcher authentication. `check-source` is entirely read-only.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CANDIDATE_DRIVE = "D:" if os.name == "nt" else None
sys.path.insert(0, str(ROOT / "tools"))
import assemble_release as assembly
import build_modules as builds
import release_pack as pack
import run_native_checks as native
from world_merge import nbt

CLIENT_COMPONENTS = {"vale-orders", "vale-encounters", "vale-rack", "vale-residents",
    "vale-world-compat", "village-standards", "field-hud", "field-controls", "client-lifecycle"}
SERVER_IDS = {component: mod_id for _, component, mod_id in native.PRODUCTION}
SUPPORT_PINS = {
    "Essential_1-5-1_forge_1-12-2.jar": "0eefd238fb7781eb1bb4b5c9b294cfccffddb9694d44222ba8477edd3fd1636e",
    "OptiFine_1.12.2_HD_U_G5.jar": "3b0006797771feb97f2d0d2908ae7c9a78cefb2e730af1c2e85d08a839ba271e",
    "jei_1.12.2-4.22.0.1034.jar": "e069347966155efd050fe18f060162a0406c194528c7c16974263c8879c3a8a2",
}
SECRET_PATH = re.compile(r"(^|[/_. -])(auth|accounts?|credentials?|secrets?|tokens?|rcon)([/_. -]|$)", re.I)
SECRET_VALUE = re.compile(r'''(?im)["']?(?:rcon[.]password|password|access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret)["']?[ \t]*[:=][ \t]*["']?([^\s"',;}]+)''')
EMPTY_VALUES = {"", "0", "false", "none", "null", "changeme", "disabled"}


class PlaytestError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise PlaytestError(message)


def identity(path):
    return pack.hash_file(pack.no_links(path))


def sha1(path):
    value = hashlib.sha1()
    with pack.stable_reader(path) as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):value.update(block)
    return value.hexdigest()


def checked(path, expected):
    actual = identity(path)
    require(actual["sha256"] == expected["sha256"] and
            ("bytes" not in expected or actual["bytes"] == expected["bytes"]), "Input changed: " + str(path))
    return actual


def relative(root, name):
    return pack.no_links(root / pack.safe_relative(name))


def inspect_mods(path):
    """Use actual @Mod identities; old mcmod.info examples are not registry owners.

    Pinned bootstrap/tweaker JARs may have no @Mod. Metadata is retained only as
    an observation when annotations exist; it cannot invent an Example Mod ID.
    """
    found = {}
    with zipfile.ZipFile(path) as archive:
        pack.check_names([{"path": i.filename.rstrip("/"), "kind": "directory" if i.is_dir() else "file"} for i in archive.infolist()])
        total = 0
        for entry in archive.infolist():
            if not entry.filename.endswith(".class"):
                continue
            require(entry.file_size <= 8 * 1024**2, "Oversize class metadata: " + path.name)
            total += entry.file_size
            require(total <= 256 * 1024**2, "JAR class inspection exceeds bound: " + path.name)
            raw = archive.read(entry)
            if b"Lnet/minecraftforge/fml/common/Mod;" in raw:
                for mid, version in assembly.ClassReader(raw).mods().items():
                    require(mid not in found, "Repeated @Mod ID inside " + path.name + ": " + mid)
                    found[mid] = version
        require(archive.testzip() is None, "Corrupt JAR: " + path.name)
    return found


def evidence(root, build_report=None, native_report=None):
    """Validate the existing receipts, not documentation or working-tree churn.

    The build's source inventory already excludes build output, private native
    fixtures, docs and unrelated tooling. Private fixture inputs use their own
    native receipt. Adding this assembler cannot invalidate a compiled JAR.
    """
    root = Path(root).resolve()
    build_path = Path(build_report or root / "build/source-build-report.json")
    native_path = Path(native_report or root / "build/native-check-report.json")
    report, build_id = pack.read_json(build_path)
    qa, native_id = pack.read_json(native_path)
    require(report.get("schema") == 1 and report.get("passed") is True, "A passing source build is required")
    require(report.get("results") and all(r.get("status") == "PASS" for r in report["results"].values()), "All source components must pass")
    source = report.get("source_snapshot", {})
    snapshot, _ = pack.read_json(relative(root, source["path"]))
    source_digest = hashlib.sha256(json.dumps(snapshot["files"], sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    require(source_digest == snapshot["sha256"] == source["sha256"], "Source inventory digest is inconsistent")
    require(builds.source_snapshot(root)["sha256"] == source["sha256"], "Runtime source differs from passing build; rebuild first")
    require(qa.get("schema") == 1 and qa.get("passed") is True and qa.get("status") == "PASS"
            and qa.get("minecraft_process_invoked") is True, "Passing disposable native evidence is required")
    require(qa.get("source_build_report") == build_id, "Native checks do not bind this exact source build report")
    require(qa.get("private_fixture_inputs") == native.private_snapshot(root), "Private native fixture sources differ from the passing run")
    runtime = {}
    for module, result in report["results"].items():
        for row in result.get("artifacts", []):
            copied = relative(root, row["copied_path"])
            require(root / "build/source-builds" in copied.parents, "Artifact must be a checked source-build copy")
            checked(copied, row)
            original = pack.no_links(Path(row["path"]))
            require(root / "modules" / module in original.parents and "build" in original.relative_to(root / "modules" / module).parts, "Unexpected original artifact path")
            checked(original, row)
            if row["role"] != "runtime_mod":
                continue
            component = row.get("component", module)
            require(component not in runtime, "Duplicate source component: " + component)
            builds.inspect_artifact(copied, "runtime_mod", component)
            value = {"source": str(copied), "file": copied.name, "component": component,
                     "origin": "vale", "version": row.get("version", result.get("version")),
                     **identity(copied), "mod_ids": inspect_mods(copied)}
            runtime[component] = value
    require(set(runtime) == CLIENT_COMPONENTS | set(SERVER_IDS), "Unexpected or missing runtime components")
    require(set(qa.get("production_mod_ids", [])) == set(SERVER_IDS.values()), "Unexpected native production roster")
    loaded = qa.get("expected_loaded_mods", {})
    for component, mid in SERVER_IDS.items():
        row = runtime[component]
        require(mid in row["mod_ids"] and loaded.get(mid, {}).get("sha256") == row["sha256"]
                and loaded[mid].get("version") == row["mod_ids"][mid], "Native roster differs from selected artifact: " + component)
    return {"build": build_id, "native": native_id, "source_sha256": source["sha256"],
            "private_source_sha256": qa["private_fixture_inputs"]["sha256"],
            "native_assertions": qa.get("assertions"), "runtime": runtime}


def files_under(root):
    root = pack.no_links(Path(root).absolute())
    require(root.is_dir(), "Missing input directory: " + str(root))
    rows, names = [], set()
    for directory, subdirs, files in os.walk(root, followlinks=False):
        for name in subdirs + files:
            pack.no_links(Path(directory) / name)
        for name in sorted(files):
            path = Path(directory) / name
            rel = path.relative_to(root).as_posix()
            require(rel.casefold() not in names, "Case-colliding input path: " + rel)
            pack.safe_relative(rel)
            names.add(rel.casefold())
            require(path.is_file(), "Nonregular input: " + str(path))
            rows.append((path, rel))
    return sorted(rows, key=lambda row: row[1])


def support_mods(root):
    rows = []
    for name, pin in SUPPORT_PINS.items():
        path = pack.no_links(Path(root) / name)
        value = checked(path, {"sha256": pin})
        rows.append({"source": str(path), "file": name, "origin": "support", **value, "mod_ids": inspect_mods(path)})
    return rows


def baseline_mods(directory, inventory):
    expected = {row["file"]: row for row in inventory["jars"]}
    require(inventory.get("jar_count") == len(expected) == 49, "Baseline must be the pinned complete 49-JAR reference inventory")
    paths = files_under(directory)
    jars = {path.name: path for path, rel in paths if rel.lower().endswith(".jar")}
    require(len(jars) == sum(rel.lower().endswith(".jar") for _, rel in paths) and set(jars) == set(expected), "Baseline active JAR names differ from the pinned 49")
    result = []
    for name, row in sorted(expected.items()):
        path = jars[name];checked(path, row)
        result.append({"source": str(path), "file": name, "origin": "baseline", **identity(path), "mod_ids": inspect_mods(path)})
    omissions = [{"source": str(path), "reason": "not an active baseline JAR", **identity(path)} for path, rel in paths if not rel.lower().endswith(".jar")]
    return result, omissions


def baseline_inventory(root, source):
    if source == "reference":
        path = root / "design/reference-mod-inventory.json"
        inventory, receipt_id = pack.read_json(path)
        require(inventory.get("schema") == "vale.reference-mod-inventory/v1", "Unexpected reference inventory schema")
    else:
        path = root / "release/evidence/server-intake-20261113.json"
        receipt, receipt_id = pack.read_json(path)
        require(receipt.get("schema") == 1 and receipt.get("scope") == "STATIC_SERVER_INTAKE_ONLY"
                and receipt.get("crc_verified") is True and receipt.get("output_readback_verified") is True
                and receipt.get("source_unmodified") is True and receipt.get("jar_counts", {}).get("mods") == 49,
                "Server baseline requires the verified public archive-intake receipt")
        rows = []
        for row in receipt.get("jars", []):
            if row.get("category") != "mods":continue
            rel = pack.safe_relative(row["path"])
            require(len(Path(rel).parts) == 2 and Path(rel).parts[0] == "mods", "Server mod pin must identify one flat mods JAR")
            rows.append({"file": Path(rel).name, "bytes": row["bytes"], "sha256": row["sha256"]})
        inventory = {"jar_count": 49, "jars": rows}
    return inventory, {"selection": source, "receipt_path": path.relative_to(root).as_posix(), **receipt_id}


def resolve_roster(base, additions, preferences=None, replace_pictures=False):
    """Never quietly discard original registry owners; every loss is explicit."""
    preferences = dict(preferences or {})
    require(all(value in {"baseline", "vale"} for value in preferences.values()), "Preference must be baseline or vale")
    if replace_pictures:
        require(preferences.get("hvdungeonpictures", "vale") == "vale", "Conflicting picture replacement preference")
        preferences["hvdungeonpictures"] = "vale"
    else:
        require(preferences.get("hvdungeonpictures", "baseline") == "baseline", "Use --replace-standard-pictures to replace original pictures")
        preferences["hvdungeonpictures"] = "baseline"
    selected, omitted, replacements = [], [], []
    def duplicate(items):
        owners = {}
        for row in items:
            for mid in row["mod_ids"]:
                require(mid not in owners, "Duplicate original/runtime mod ID: " + mid)
                owners[mid] = row
        return owners
    duplicate(base)
    selected.extend(base)
    for addition in additions:
        same = [r for r in selected if r["sha256"] == addition["sha256"]]
        if same:
            omitted.append({**addition, "reason": "identical bytes already preserved", "retained": same[0]["file"]});continue
        owners = duplicate(selected)
        conflicts = set(addition["mod_ids"]) & set(owners)
        if conflicts:
            require(all(owners[mid]["origin"] == "baseline" for mid in conflicts), "Conflicting enhancement mod ID: " + repr(sorted(conflicts)))
            require(all(mid in preferences for mid in conflicts), "Unresolved original/enhancement mod IDs: " + repr(sorted(conflicts)) + "; use explicit --prefer-mod ID=baseline|vale")
            choices = {preferences[mid] for mid in conflicts}
            require(len(choices) == 1, "One JAR cannot satisfy mixed duplicate preferences")
            if choices == {"baseline"}:
                omitted.append({**addition, "reason": "explicit original registry owner retained", "conflicting_mod_ids": sorted(conflicts)});continue
            old = [r for r in selected if any(r is owners[mid] for mid in conflicts)]
            for row in old:
                require(set(row["mod_ids"]) <= set(addition["mod_ids"]), "Replacement would silently remove other original registry IDs: " + row["file"])
                selected.remove(row);omitted.append({**row, "reason": "explicit tested enhancement replacement", "replacement": addition["file"]})
                replacements.append({"removed": row, "selected": addition, "mod_ids": sorted(conflicts), "explicit": True})
        require(addition["file"].casefold() not in {r["file"].casefold() for r in selected}, "Conflicting output JAR filename: " + addition["file"])
        selected.append(addition)
    duplicate(selected)
    return selected, omitted, replacements


def java8(home):
    home = pack.no_links(Path(home).absolute())
    release = home / "release"
    require(release.is_file() and re.search(r'JAVA_VERSION="1[.]8[.]', release.read_text(encoding="utf-8")), "Use a local Java 8 home")
    java = home / "bin/java.exe";javaw = home / "bin/javaw.exe"
    require(java.is_file() and javaw.is_file(), "Windows Java 8 java.exe/javaw.exe missing")
    return {"home": str(home), "release": identity(release), "java": {"path": str(java), **identity(java)}, "javaw": {"path": str(javaw), **identity(javaw)}}


def world_version(world):
    path = pack.no_links(Path(world) / "level.dat")
    _, root = nbt.load(path, max_bytes=16 * 1024**2)
    data = root.get("Data")
    require(data is not None and data.type == nbt.COMPOUND, "World level.dat lacks Data")
    version = data.scalar("DataVersion")
    require(version is None or isinstance(version, int) and version <= 1343, "World is newer than Minecraft 1.12.2")
    mods = []
    fml = root.get("FML") or data.get("FML")
    if fml is not None and fml.type == nbt.COMPOUND:
        listing = fml.get("ModList")
        if listing is not None and listing.type == nbt.LIST:
            for row in listing.value:
                if row.type == nbt.COMPOUND:
                    mid = row.scalar("ModId")
                    if isinstance(mid, str):mods.append(mid)
    return {"minecraft_target": "1.12.2", "data_version": version, "level_dat": identity(path),
            "recorded_fml_mod_ids": sorted(set(mods)), "scope": "level.dat identity only; not chunk/registry migration acceptance"}


def safe_destination(destination, inputs):
    destination = pack.no_links(Path(destination).absolute())
    require(not destination.exists(), "Destination must be new and nonexistent")
    require(destination.parent.is_dir(), "Destination parent must already exist")
    if CANDIDATE_DRIVE:require(destination.drive.upper() == CANDIDATE_DRIVE, "Large candidates must be created on " + CANDIDATE_DRIVE)
    for source in inputs:
        source = pack.no_links(Path(source).absolute())
        require(destination != source and source not in destination.parents and destination not in source.parents, "Candidate must not overlap an input: " + str(source))
    return destination


def config_omission(path, rel):
    if SECRET_PATH.search(rel):return "credential/account/RCON path excluded"
    require(path.stat().st_size <= 8 * 1024**2, "Oversize configuration needs explicit review: " + rel)
    raw = path.read_bytes()
    if b"\x00" in raw:return "binary configuration requires explicit review"
    text = raw.decode("utf-8-sig", errors="replace")
    if any(match.group(1).lower() not in EMPTY_VALUES for match in SECRET_VALUE.finditer(text)):
        return "configuration with nonempty credential/RCON value excluded"
    return None


def copy_row(path, destination, category):
    return {"source": str(path), "destination": destination, "category": category, **identity(path)}


def client_recipe(install, java, destination, connect_port=None):
    install = pack.no_links(Path(install).absolute())
    parent, _ = pack.read_json(install / "versions/1.12.2/1.12.2.json")
    forge, _ = pack.read_json(install / "versions/forge-14.23.5.2859/forge-14.23.5.2859.json")
    require(parent.get("id") == "1.12.2" and forge.get("id") == "forge-14.23.5.2859" and forge.get("inheritsFrom") == "1.12.2", "Unexpected installed Minecraft/Forge profile")
    libraries = {}
    for lib in parent.get("libraries", []) + forge.get("libraries", []):
        rules = lib.get("rules") or []
        allowed = not rules
        for rule in rules:
            os_rule = rule.get("os", {})
            if os_rule.get("name", "windows") == "windows":allowed = rule["action"] == "allow"
        artifact = (lib.get("downloads") or {}).get("artifact")
        if allowed and artifact:
            path = relative(install / "libraries", artifact["path"])
            if artifact.get("sha1"):require(sha1(path) == artifact["sha1"], "Installed library differs from its version pin: " + str(path))
            libraries[":".join(lib["name"].split(":")[:2])] = path
    jar = install / "versions/1.12.2/1.12.2.jar"
    if parent.get("downloads", {}).get("client", {}).get("sha1"):
        require(sha1(jar) == parent["downloads"]["client"]["sha1"], "Installed Minecraft client differs from its version pin")
    references = [{"path": str(p), **identity(p)} for p in list(libraries.values()) + [jar]]
    natives = install / "natives/forge-14.23.5.2859"
    require(natives.is_dir() and (install / "assets/indexes/1.12.json").is_file(), "Installed native libraries/assets index missing")
    references += [{"path": str(p), **identity(p)} for p, _ in files_under(natives)]
    references += [{"path": str(p), **identity(p)} for p in [install / "assets/indexes/1.12.json", install / "versions/1.12.2/1.12.2.json", install / "versions/forge-14.23.5.2859/forge-14.23.5.2859.json"]]
    # Deliberately synthetic offline identity; no launcher/account profile is read.
    import uuid
    name = "ValePlaytest"
    uid = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3)).replace("-", "")
    argv = [java["javaw"]["path"], "-Xms1G", "-Xmx5G", "-Djava.library.path=" + str(natives), "-cp", ";".join(str(p) for p in list(libraries.values()) + [jar]),
            "net.minecraft.launchwrapper.Launch", "--username", name, "--uuid", uid, "--accessToken", "0", "--userType", "legacy", "--version", "forge-14.23.5.2859",
            "--versionType", "Forge", "--gameDir", str(destination / "client"), "--assetsDir", str(install / "assets"), "--assetIndex", "1.12", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker", "--width", "1440", "--height", "900"]
    if connect_port is not None:argv += ["--server", "127.0.0.1", "--port", str(connect_port)]
    return {"argv": argv, "cwd": str(destination / "client"), "external_inputs": references,
            "authentication": "synthetic offline loopback/singleplayer identity; no account credentials"}


def make_plan(args):
    root = Path(args.root).resolve();proof = evidence(root, args.build_report, args.native_report)
    require(1024 <= args.server_port <= 65535, "Use an unprivileged local server port")
    inputs = [root, args.support_mods, args.config, args.world, args.java_home, args.install]
    if args.forge_server:inputs.append(args.forge_server)
    if args.baseline_mods:inputs.append(args.baseline_mods)
    destination = safe_destination(args.destination, inputs)
    java = java8(args.java_home);world = world_version(args.world)
    additions = [row for key, row in sorted(proof["runtime"].items()) if key in CLIENT_COMPONENTS] + support_mods(args.support_mods)
    omissions, replacements = [], []
    baseline_proof = None
    if args.variant == "baseline":
        require(args.baseline_mods is not None, "Baseline variant requires --baseline-mods")
        inventory, baseline_proof = baseline_inventory(root, args.baseline_source)
        base, omissions = baseline_mods(args.baseline_mods, inventory)
        require(len(dict(args.prefer_mod)) == len(args.prefer_mod), "Each duplicate mod preference must be specified once")
        client, omitted, replacements = resolve_roster(base, additions, dict(args.prefer_mod), args.replace_standard_pictures);omissions += omitted
        server = []
    else:
        require(not args.replace_standard_pictures and not args.prefer_mod, "Duplicate preferences are for baseline variant only")
        client, omitted, _ = resolve_roster([], additions);omissions += omitted
        server = [proof["runtime"][key] for key in sorted(SERVER_IDS)]
        require(args.forge_server is not None, "Vale variant needs an existing --forge-server (never installed automatically)")
    for component, row in sorted(proof["runtime"].items()):
        if component not in CLIENT_COMPONENTS:
            omissions.append({**row, "side": "client", "reason": "dedicated-server runtime component excluded from client"})
        if server and component not in SERVER_IDS:
            omissions.append({**row, "side": "server", "reason": "client runtime component excluded from dedicated server"})
    if server:
        for row in additions:
            if row["origin"] == "support":omissions.append({**row, "side": "server", "reason": "client-only graphics/JEI/bootstrap support excluded from dedicated server"})
    for path, rel in files_under(args.support_mods):
        if rel not in SUPPORT_PINS:
            omissions.append({"source": str(path), "context": "support-directory selection", "reason": "outside the three pinned support inputs", **identity(path)})
    actual_ids = {mid for row in (server or client) for mid in row["mod_ids"]}
    missing = set(world["recorded_fml_mod_ids"]) - actual_ids - {"minecraft", "mcp", "FML", "forge"}
    require(not missing or args.allow_unverified_world, "World records missing mod owners: " + repr(sorted(missing)) + "; choose a compatible source world or explicitly --allow-unverified-world for a migration experiment")
    trees = [{"root": str(Path(directory).absolute()), "paths": [rel for _, rel in files_under(directory)]}
             for directory in [args.config, args.world] + ([args.baseline_mods] if args.variant == "baseline" else [])]
    rows = []
    for side, roster in [("client", client), ("server", server)]:
        for row in roster:rows.append(copy_row(Path(row["source"]), side + "/mods/" + row["file"], "mod"))
    for path, rel in files_under(args.config):
        reason = config_omission(path, rel)
        if reason:omissions.append({"source": str(path), "reason": reason, **identity(path)});continue
        for side in (["client", "server"] if server else ["client"]):rows.append(copy_row(path, side + "/config/" + rel, "config"))
    world_prefix = "server/playtest-world/" if server else "client/saves/playtest-world/"
    for path, rel in files_under(args.world):
        if path.name == "session.lock":omissions.append({"source": str(path), "reason": "transient source-world session lock", **identity(path)});continue
        require(not SECRET_PATH.search(rel), "Unexpected credential/account/RCON path in world; review separately: " + rel)
        rows.append(copy_row(path, world_prefix + rel, "world"))
    generated = {"client/options.txt": "fullscreen:false\nrenderDistance:8\nguiScale:2\nmaxFps:120\n", "client/optionsshaders.txt": "shaderPack=OFF\n"}
    if server:
        template = Path(args.forge_server);forge_name = native.FORGE_FILE
        checked(template / forge_name, {"sha256": native.FORGE_SHA256})
        rows.append(copy_row(template / forge_name, "server/" + forge_name, "forge-runtime"))
        require((template / "minecraft_server.1.12.2.jar").is_file(), "Existing Forge server lacks vanilla 1.12.2 server JAR")
        version, _ = pack.read_json(Path(args.install) / "versions/1.12.2/1.12.2.json")
        server_pin = version.get("downloads", {}).get("server", {}).get("sha1")
        require(isinstance(server_pin, str) and len(server_pin) == 40 and sha1(template / "minecraft_server.1.12.2.jar") == server_pin, "Vanilla server differs from installed Minecraft version pin")
        rows.append(copy_row(template / "minecraft_server.1.12.2.jar", "server/minecraft_server.1.12.2.jar", "minecraft-runtime"))
        for path, rel in files_under(template / "libraries"):rows.append(copy_row(path, "server/libraries/" + rel, "forge-library"))
        allowed_template = {forge_name, "minecraft_server.1.12.2.jar", "libraries", "eula.txt"}
        for path in sorted(template.iterdir()):
            if path.name not in allowed_template:
                omissions.append({"source": str(path), "kind": "directory-prefix" if path.is_dir() else "file", "reason": "existing-server template allowlist excludes configs, mods, saves, players, accounts and logs"})
        if (template / "eula.txt").is_file():rows.append(copy_row(template / "eula.txt", "server/eula.txt", "existing-eula-choice"))
        else:generated["server/eula.txt"] = "eula=false\n"
        generated["server/server.properties"] = "level-name=playtest-world\nserver-ip=127.0.0.1\nserver-port=%d\nonline-mode=false\ngamemode=0\nforce-gamemode=true\ndifficulty=2\nspawn-protection=0\nview-distance=8\nmax-players=4\nenable-query=false\nenable-rcon=false\nenable-command-block=false\nmax-tick-time=180000\nmotd=Private Vale playtest\n" % args.server_port
        for name in ["ops.json", "whitelist.json", "banned-players.json", "banned-ips.json"]:generated["server/" + name] = "[]\n"
    recipe = client_recipe(args.install, java, destination, args.server_port if server else None)
    names = [row["destination"].casefold() for row in rows] + [name.casefold() for name in generated]
    require(len(names) == len(set(names)), "Case-colliding candidate output")
    scope = "PRIVATE_LOCAL_VALE_NATIVE_ROSTER" if server else "PRIVATE_LOCAL_49_MOD_COMPATIBILITY_EXPERIMENT"
    return {"schema": 1, "scope": scope, "variant": args.variant, "destination": str(destination), "minecraft": "1.12.2", "forge": native.FORGE_VERSION,
            "evidence": {k: v for k, v in proof.items() if k != "runtime"}, "java8": java, "world": world,
            "baseline_inventory": baseline_proof,
            "missing_world_mod_ids": sorted(missing), "world_override_explicit": bool(args.allow_unverified_world),
            "client_mods": client, "server_mods": server, "copies": rows, "generated": generated,
            "input_trees": trees,
            "omissions": omissions, "replacements": replacements, "client_launch_recipe": recipe,
            "server_launch_recipe": {"argv": [java["java"]["path"], "-Xms512M", "-Xmx4G", "-jar", str(destination / "server" / native.FORGE_FILE), "nogui"], "cwd": str(destination / "server")} if server else None,
            "loopback_server_port": args.server_port if server else None,
            "accepted_scopes": {"source_current": True, "native_runtime_roster": True, "graphical_candidate_tested": False,
                "full_49_mod_stack_tested": False, "copied_world_tested": False, "magma_plugins_tested": False, "hosted_acceptance": False}}


def assemble(plan, recheck=None):
    destination = Path(plan["destination"])
    require(not destination.exists(), "Destination became occupied; refusing to overwrite")
    needed = sum(row["bytes"] for row in plan["copies"]) + 32 * 1024**2
    require(shutil.disk_usage(destination.parent).free >= needed, "Insufficient free space for independent copy")
    if recheck:recheck()
    destination.mkdir()
    try:
        for row in plan["copies"]:
            source = Path(row["source"]);checked(source, row)
            target = relative(destination, row["destination"]);target.parent.mkdir(parents=True, exist_ok=True)
            with pack.stable_reader(source) as reader, target.open("xb") as writer:shutil.copyfileobj(reader, writer, 1024 * 1024)
            checked(source, row);checked(target, row)
        for name, text in plan["generated"].items():
            target = relative(destination, name);target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("x", encoding="utf-8", newline="\n") as handle:handle.write(text)
        for row in plan["copies"]:checked(Path(row["source"]), row)
        for row in plan["omissions"]:
            if "sha256" in row:checked(Path(row["source"]), row)
        for tree in plan.get("input_trees", []):
            require([rel for _, rel in files_under(tree["root"])] == tree["paths"], "Input directory membership changed during copying: " + tree["root"])
        for row in plan["client_launch_recipe"]["external_inputs"]:checked(Path(row["path"]), row)
        if recheck:recheck()
        receipt = {**plan, "assembled_utc": datetime.now(timezone.utc).isoformat(), "passed": True,
                   "generated_identities": [{"destination": name, **identity(destination / name)} for name in sorted(plan["generated"])],
                   "minecraft_invoked": False, "source_inputs_unchanged": True,
                   "privacy": "Local private receipt: contains machine paths and copied private-world identities; do not publish."}
        (destination / "PRIVATE-PLAYTEST-RECEIPT.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        (destination / "START-HERE.txt").write_text("PRIVATE LOCAL CANDIDATE — NOT LAUNCHED\n\nReview PRIVATE-PLAYTEST-RECEIPT.json before executing its argv recipes.\nUse the pinned Java 8. No launcher credentials were read or copied.\n" +
            ("Server binds only 127.0.0.1. Check the preserved EULA choice, then start server before client.\n" if plan["server_mods"] else "Full 49-mod profile is a separate singleplayer compatibility experiment. Do not connect it to the smaller Vale-only server.\n") +
            "World/config are independent copies. No graphical, copied-world, full-stack or hosted acceptance is claimed.\n", encoding="utf-8")
        return receipt
    except BaseException as error:
        (destination / "ASSEMBLY-FAILED.json").write_text(json.dumps({"passed": False, "error": str(error), "partial_copy_preserved": True}, indent=2) + "\n", encoding="utf-8")
        raise


def preference(value):
    pieces = value.split("=")
    if len(pieces) != 2 or not re.fullmatch(r"[A-Za-z0-9_.-]+", pieces[0]) or pieces[1] not in {"baseline", "vale"}:
        raise argparse.ArgumentTypeError("Use MOD_ID=baseline or MOD_ID=vale")
    return tuple(pieces)


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("command", choices=["check-source", "plan", "assemble"])
    p.add_argument("--root", type=Path, default=ROOT)
    p.add_argument("--build-report", type=Path);p.add_argument("--native-report", type=Path)
    p.add_argument("--variant", choices=["vale", "baseline"], default="vale")
    p.add_argument("--destination", type=Path);p.add_argument("--world", type=Path);p.add_argument("--config", type=Path)
    p.add_argument("--support-mods", type=Path);p.add_argument("--baseline-mods", type=Path);p.add_argument("--forge-server", type=Path)
    p.add_argument("--baseline-source", choices=["reference", "server-archive"], default="reference")
    p.add_argument("--java-home", type=Path, default=ROOT / "local/toolchains/zulu8.98.0.111-ca-jdk8.0.512-win_x64")
    p.add_argument("--install", type=Path, default=Path("C:/Users/Steal/curseforge/minecraft/Install"))
    p.add_argument("--server-port", type=int, default=25578)
    p.add_argument("--prefer-mod", type=preference, action="append", default=[])
    p.add_argument("--replace-standard-pictures", action="store_true")
    p.add_argument("--allow-unverified-world", action="store_true")
    return p


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        if args.command == "check-source":
            result = evidence(args.root, args.build_report, args.native_report);result.pop("runtime")
            print(json.dumps({"passed": True, "minecraft_invoked": False, **result}, indent=2));return 0
        require(all(getattr(args, key) is not None for key in ["destination", "world", "config", "support_mods"]), "--destination, --world, --config and --support-mods are required")
        plan = make_plan(args)
        if args.command == "assemble":assemble(plan, lambda: evidence(args.root, args.build_report, args.native_report))
        print(json.dumps({"scope": plan["scope"], "destination": plan["destination"], "client_jars": len(plan["client_mods"]),
              "server_jars": len(plan["server_mods"]), "copy_files": len(plan["copies"]), "copy_bytes": sum(row["bytes"] for row in plan["copies"]),
              "omissions": len(plan["omissions"]), "replacements": len(plan["replacements"]), "assembled": args.command == "assemble", "minecraft_invoked": False}, indent=2))
        return 0
    except (PlaytestError, pack.PackError, builds.BuildError, native.NativeError, OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print("REFUSED: " + str(error), file=sys.stderr);return 1


if __name__ == "__main__":
    sys.exit(main())
