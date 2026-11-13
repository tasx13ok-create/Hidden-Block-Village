#!/usr/bin/env python3
"""Start a NEW, loopback-only academy sandbox from checked build outputs.

Requires an existing CurseForge Minecraft installation, original local support
JARs, official Forge installer, Java 8 and a complete current source build.
Never opens or changes an existing world/profile. Runtime files stay ignored.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import uuid

import build_modules as builds
import run_native_checks as native

ROOT = Path(__file__).resolve().parents[1]
CLIENT_COMPONENTS = {"vale-orders", "vale-encounters", "vale-rack", "vale-residents",
                     "vale-world-compat", "village-standards", "field-hud", "field-controls", "client-lifecycle"}
SERVER_COMPONENTS = {row[1] for row in native.PRODUCTION}
SUPPORT = {"Essential_1-5-1_forge_1-12-2.jar": "0eefd238fb7781eb1bb4b5c9b294cfccffddb9694d44222ba8477edd3fd1636e",
           "OptiFine_1.12.2_HD_U_G5.jar": "3b0006797771feb97f2d0d2908ae7c9a78cefb2e730af1c2e85d08a839ba271e"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def wait_for(process, path, marker, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Process exited; inspect " + str(path))
        if path.is_file():
            text = path.read_text(errors="replace")
            if marker in text:
                return
            if "Course placement refused:" in text:
                raise RuntimeError("Academy placement refused; preserved sandbox at " + str(path.parent))
        time.sleep(1)
    raise RuntimeError("Timed out waiting for " + marker + "; inspect " + str(path))


def allowed(lib):
    if not lib.get("rules"):
        return True
    result = False
    for rule in lib["rules"]:
        system = rule.get("os", {})
        if system.get("name", "windows") == "windows" and not system.get("version") and not system.get("arch"):
            result = rule["action"] == "allow"
    return result


def run(args):
    if os.name != "nt":
        raise RuntimeError("This preview launcher currently supports Windows only")
    session = args.destination.resolve()
    if session.exists():
        raise RuntimeError("A new destination is required; existing sessions are preserved")
    report = json.loads((ROOT / "build/source-build-report.json").read_text())
    if not report["passed"] or builds.source_snapshot()["sha256"] != report["source_snapshot"]["sha256"]:
        raise RuntimeError("Rebuild the complete current source first")
    java = args.java8_home.resolve() / "bin/java.exe"
    javav = java.with_name("javaw.exe")
    if not java.is_file() or not javav.is_file() or digest(args.installer) != native.INSTALLER_SHA256:
        raise RuntimeError("Java 8 or pinned official Forge installer unavailable")
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    selected = []
    for module, result in report["results"].items():
        for row in result["artifacts"]:
            if row["role"] == "runtime_mod":
                path = ROOT / row["copied_path"]
                if digest(path) != row["sha256"]:
                    raise RuntimeError("Build output hash mismatch: " + str(path))
                selected.append((row.get("component", module), path, row["sha256"]))
    for components in (CLIENT_COMPONENTS, SERVER_COMPONENTS):
        if {name for name, _, _ in selected if name in components} != components:
            raise RuntimeError("The complete client/server component build is required")
    support = [(args.support_mods / name, pin) for name, pin in SUPPORT.items()]
    jei = args.support_mods / "jei_1.12.2-4.22.0.1034.jar"
    if not jei.is_file():
        raise RuntimeError("The inspected JEI 4.22.0.1034 JAR is required for the regression smoke test")
    support.append((jei, digest(jei)))
    for path, pin in support:
        if digest(path) != pin:
            raise RuntimeError("Support JAR hash mismatch: " + path.name)
    session.mkdir(parents=True)
    client, server = session / "client", session / "server"
    for directory in (client / "mods", server / "mods"):
        directory.mkdir(parents=True)
    for component, path, pin in selected:
        for directory, components in ((client, CLIENT_COMPONENTS), (server, SERVER_COMPONENTS)):
            if component in components:
                shutil.copyfile(path, directory / "mods" / path.name)
    for path, pin in support:
        shutil.copyfile(path, client / "mods" / path.name)
    (client / "options.txt").write_text("fullscreen:false\nrenderDistance:8\nguiScale:2\nmaxFps:90\n")
    (client / "optionsshaders.txt").write_text("shaderPack=OFF\n")
    print("Installing Forge into a fresh academy sandbox", flush=True)
    with (session / "forge-install.log").open("w") as log:
        subprocess.run([str(java), "-Djava.awt.headless=true", "-jar", str(args.installer.resolve()),
                        "--installServer", str(server)], cwd=session, stdout=log, stderr=subprocess.STDOUT,
                       check=True, timeout=180, creationflags=subprocess.CREATE_NO_WINDOW)
    if digest(server / native.FORGE_FILE) != native.FORGE_SHA256:
        raise RuntimeError("Installed Forge runtime hash mismatch")
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        "level-name=academy-sandbox\nlevel-type=FLAT\ngenerate-structures=false\nserver-ip=127.0.0.1\nserver-port=" + str(args.port) + "\n"
        "online-mode=false\ngamemode=0\nforce-gamemode=true\ndifficulty=1\nspawn-protection=0\n"
        "spawn-animals=false\nspawn-monsters=false\nview-distance=6\nmax-players=2\n"
        "enable-rcon=false\nenable-query=false\nmax-tick-time=180000\nmotd=Vale Academy 0.3.10 Sandbox\n")
    server_log = session / "server-console.log"
    with server_log.open("w") as output:
        host = subprocess.Popen([str(java), "-Xms512M", "-Xmx2G", "-jar", str(server / native.FORGE_FILE), "nogui"],
                                cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    record = {"server_pid": host.pid, "server_port": args.port, "scope": "NEW_LOCAL_ACADEMY_SANDBOX",
              "source_snapshot": report["source_snapshot"], "production_world_tested": False,
              "client_mods": [{"file": path.name, "sha256": digest(path)} for path in sorted((client / "mods").glob("*.jar"))]}
    (session / "session.json").write_text(json.dumps(record, indent=2))
    wait_for(host, server_log, "Done (")
    host.stdin.write(b"gamerule doMobSpawning false\ngamerule doDaylightCycle false\ntime set day\nweather clear\nsetworldspawn -24 5 -4\nvaleacademy build 0 4 0\nsave-all\n")
    host.stdin.flush()
    wait_for(host, server_log, "Academy placed.")
    install = args.minecraft_install.resolve()
    parent = json.loads((install / "versions/1.12.2/1.12.2.json").read_text())
    forge = json.loads((install / "versions/forge-14.23.5.2859/forge-14.23.5.2859.json").read_text())
    libraries = {}
    for lib in parent["libraries"] + forge["libraries"]:
        artifact = (lib.get("downloads") or {}).get("artifact")
        if allowed(lib) and artifact:
            libraries[":".join(lib["name"].split(":")[:2])] = install / "libraries" / artifact["path"]
    paths = list(libraries.values()) + [install / "versions/1.12.2/1.12.2.jar"]
    if not all(path.is_file() for path in paths):
        raise RuntimeError("Minecraft installation is missing classpath files")
    name = "ValeAcademy"
    identity = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3)).replace("-", "")
    command = [str(javav), "-Xms512M", "-Xmx3G", "-Djava.library.path=" + str(install / "natives/forge-14.23.5.2859"),
               "-cp", ";".join(map(str, paths)), "net.minecraft.launchwrapper.Launch", "--username", name,
               "--uuid", identity, "--accessToken", "0", "--userType", "legacy", "--version", "forge-14.23.5.2859",
               "--versionType", "Forge", "--gameDir", str(client), "--assetsDir", str(install / "assets"),
               "--assetIndex", "1.12", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker",
               "--width", "1440", "--height", "900", "--server", "127.0.0.1", "--port", str(args.port)]
    client_log = session / "client-console.log"
    with client_log.open("w") as output:
        game = subprocess.Popen(command, cwd=client, stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    record["client_pid"] = game.pid
    (session / "session.json").write_text(json.dumps(record, indent=2))
    print("Academy built; launching OptiFine + JEI client", flush=True)
    wait_for(game, server_log, name + " joined the game", 240)
    host.stdin.write(b"tp ValeAcademy -23.5 5 -3.5 0 0\nsave-all\n")
    host.stdin.flush()
    record["network_join_observed"] = True
    record["jei_empty_list_pass"] = "VALE_JEI_CATALOG_PASS:" in client_log.read_text(errors="replace")
    (session / "session.json").write_text(json.dumps(record, indent=2))
    print(json.dumps({key: record[key] for key in ("server_pid", "client_pid", "network_join_observed", "jei_empty_list_pass")}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8-home", type=Path, required=True)
    parser.add_argument("--installer", type=Path, required=True)
    parser.add_argument("--minecraft-install", type=Path, required=True)
    parser.add_argument("--support-mods", type=Path, required=True)
    parser.add_argument("--destination", type=Path, default=ROOT / "local/academy-preview-0310")
    parser.add_argument("--port", type=int, default=25577, choices=range(1024, 65536), metavar="PORT")
    run(parser.parse_args())
