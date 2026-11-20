"""Run the private-art probe in an isolated Forge 1.12.2 client on Windows."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

FORGE = "forge-14.23.5.2859"


def libraries(install, native):
    entries = {}
    for version in (FORGE, "1.12.2"):
        profile = install / "versions" / version / f"{version}.json"
        document = json.loads(profile.read_bytes())
        for library in document["libraries"]:
            coordinate = library["name"].rsplit(":", 1)[0]
            if coordinate in entries:
                continue
            rules = library.get("rules") or []
            allowed = not rules
            for rule in rules:
                if rule.get("os", {}).get("name", "windows") == "windows" and "features" not in rule:
                    allowed = rule["action"] == "allow"
            if allowed:
                entries[coordinate] = library

    classpath = []
    for library in entries.values():
        downloads = library["downloads"]
        if artifact := downloads.get("artifact"):
            path = install / "libraries" / artifact["path"]
            if not path.is_file():
                raise FileNotFoundError(path)
            classpath.append(path)
        if platform := (library.get("natives") or {}).get("windows"):
            platform = platform.replace("$" + "{arch}", "64")
            path = install / "libraries" / downloads["classifiers"][platform]["path"]
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.endswith(".dll") and "/" not in name and "\\" not in name:
                        (native / name).write_bytes(archive.read(name))
    classpath.append(install / "versions/1.12.2/1.12.2.jar")
    return classpath


def run(install, java, javac, artifact, trial, optifine=None, bsl=None):
    if os.name != "nt":
        raise RuntimeError("This launch recipe uses Windows client natives")
    trial.mkdir(parents=True, exist_ok=False)
    mods, classes, native = (trial / name for name in ("mods", "classes", "natives"))
    for folder in (mods, classes, native):
        folder.mkdir()
    classpath = libraries(install, native)
    probe = Path(__file__).with_name("ArmorProbe.java")
    forge_jar = classpath[0]
    subprocess.run([str(javac), "--release", "8", "-cp", str(forge_jar),
                    "-d", str(classes), str(probe)], check=True)
    shutil.copy2(artifact, mods / artifact.name)
    with zipfile.ZipFile(mods / "visual-probe.jar", "x") as archive:
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())

    if bool(optifine)!=bool(bsl):
        raise ValueError("Provide both OptiFine and BSL for the optional shader gallery")
    flags=[]
    if bsl:
        shutil.copy2(optifine, mods / optifine.name)
        shader_folder=trial / "shaderpacks"
        shader_folder.mkdir()
        shutil.copy2(bsl, shader_folder / bsl.name)
        (shader_folder / (bsl.name+".txt")).write_text(
            "ADVANCED_MATERIALS=true\nMATERIAL_FORMAT=1\nPARALLAX=false\n"
            "SHADOW=true\nshadowDistance=48.0\nshadowMapResolution=1024\n"
            "DOF=false\nMOTION_BLUR=false\nTAA=false\nFXAA=false\nBLOOM_STRENGTH=0.6\n",encoding="utf8")
        (trial/"optionsshaders.txt").write_text(
            f"shaderPack={bsl.name}\nnormalMapEnabled=true\nspecularMapEnabled=true\n",encoding="utf8")
        (trial/"options.txt").write_text(
            "pauseOnLostFocus:false\nrenderDistance:6\nfov:0.0\n",encoding="utf8")
        flags=["-Dbijuu.gallery=true"]

    if "8K" in artifact.name:
        flags.append("-Dbijuu.atlasSize=8192")

    command = [
        str(java), "-Xmx7G" if "8K" in artifact.name else "-Xmx3G", *flags, f"-Djava.library.path={native}", "-cp", os.pathsep.join(map(str, classpath)),
        "net.minecraft.launchwrapper.Launch", "--username", "BijuuQA", "--version", FORGE,
        "--gameDir", str(trial), "--assetsDir", str(install / "assets"), "--assetIndex", "1.12",
        "--uuid", "f4ea74d9a25f4f30b3ed82605aee9188", "--accessToken", "0", "--userType", "legacy",
        "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker", "--versionType", "Forge",
        "--width", "1920", "--height", "1080",
    ]
    console = trial / "client-console.log"
    with console.open("w", encoding="utf8") as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, cwd=trial,
                                creationflags=subprocess.CREATE_NO_WINDOW, timeout=420 if "8K" in artifact.name else 200, check=False)
    lines = console.read_text(encoding="utf8", errors="replace").splitlines()
    for line in lines:
        if "BIJUU_VISUAL_QA_PASS" in line or "BIJUU_BSL_GALLERY_PASS" in line:
            print(line)
    print(f"Exit {result.returncode}. Full log: {console}")
    visual=any("BIJUU_VISUAL_QA_PASS" in line for line in lines)
    gallery=not bsl or any("BIJUU_BSL_GALLERY_PASS" in line for line in lines)
    return result.returncode == 0 and visual and gallery


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("install", "java", "javac", "artifact", "output"):
        parser.add_argument(f"--{option}", type=lambda s: Path(s).resolve(), required=True)
    for option in ("optifine","bsl"):
        parser.add_argument(f"--{option}",type=lambda s: Path(s).resolve())
    args = parser.parse_args()
    return run(args.install, args.java, args.javac, args.artifact, args.output, args.optifine, args.bsl)


if __name__ == "__main__":
    sys.exit(0 if main() else 1)
