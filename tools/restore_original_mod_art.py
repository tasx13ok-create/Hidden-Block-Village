"""Restore stock mod art in a stopped private client; retain every source JAR/pack."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = "Vale-Reference-Flat-Icons-v3.zip"
PACK_SHA = "ed45b2f8aab0840b236aef97cfcec42c452a43a84a7c6119fbb622ce617ee39e"


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return {"bytes": path.stat().st_size, "sha256": h.hexdigest()}


def stopped(client):
    if os.name != "nt":
        raise RuntimeError("This local profile guard requires Windows")
    environment = dict(os.environ, HBV_ART_PROFILE=str(client))
    script = """$target=$env:HBV_ART_PROFILE.Replace('/','\\');
Get-CimInstance Win32_Process | Where-Object {
($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -and
$_.CommandLine.Replace('/','\\').IndexOf($target,[StringComparison]::OrdinalIgnoreCase) -ge 0
} | Select-Object -ExpandProperty ProcessId"""
    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", script],
                            env=environment, capture_output=True, text=True, check=True)
    if result.stdout.strip():
        raise RuntimeError("The selected Minecraft profile is running; preserve its live settings")


def restore(client, output):
    client = client.resolve(strict=True)
    output = output.absolute()
    if output.exists() or ROOT == output or ROOT in output.parents or client == output or client in output.parents:
        raise ValueError("Use a new private receipt folder outside the checkout/client")
    for path in (client, output, *client.parents, *output.parents):
        if path.is_symlink() or path.is_junction():
            raise ValueError("Profile and receipt paths must not traverse links")
    stopped(client)
    inventory = json.loads((ROOT / "design/reference-mod-inventory.json").read_text())
    original = {r["file"]: r for r in inventory["jars"]}
    mods = {p.name: digest(p) for p in sorted((client / "mods").glob("*.jar"))}
    varied = next(r for r in inventory["jars"] if r["file"].startswith("VariedCommodities_"))
    if mods.get(varied["file"], {}).get("sha256") != varied["sha256"]:
        raise ValueError("Varied Commodities is not the exact original input")
    preserved = [name for name, identity in mods.items()
                 if name in original and identity["sha256"] == original[name]["sha256"]]
    path = client / "options.txt"
    before = path.read_bytes()
    lines = before.decode("utf-8").splitlines(keepends=True)
    indices = [i for i, line in enumerate(lines) if line.startswith("resourcePacks:")]
    if len(indices) != 1:
        raise ValueError("Ambiguous Minecraft resource-pack selection")
    index = indices[0]
    selected = json.loads(lines[index].split(":", 1)[1])
    if not isinstance(selected, list) or not all(isinstance(x, str) for x in selected):
        raise ValueError("Invalid resource-pack selection")
    removed = [x for x in selected if x in (PACK, "file/" + PACK)]
    retained = [x for x in selected if x not in removed]
    if retained:
        raise ValueError("Additional active packs require their own stock-art audit: " + repr(retained))
    pack = client / "resourcepacks" / PACK
    pack_identity = digest(pack)
    if pack_identity["sha256"] != PACK_SHA:
        raise ValueError("Reference overlay differs from the inspected private pack")
    with zipfile.ZipFile(pack) as archive:
        if archive.testzip() is not None:
            raise ValueError("Reference overlay failed CRC")
        model_overrides = sum(n.startswith("assets/") and "/models/" in n and n.endswith(".json") for n in archive.namelist())
        varied_overrides = sum(n.startswith("assets/variedcommodities/models/") and n.endswith(".json") for n in archive.namelist())
        if any(n.startswith("assets/variedcommodities/textures/models/") for n in archive.namelist()):
            raise ValueError("Unexpected worn-armor override; inspect before changing settings")
    newline = "\r\n" if lines[index].endswith("\r\n") else "\n" if lines[index].endswith("\n") else ""
    lines[index] = "resourcePacks:" + json.dumps(retained, separators=(",", ":")) + newline
    after = "".join(lines).encode("utf-8")
    output.mkdir(parents=True, exist_ok=False)
    (output / "options.before.txt").write_bytes(before)
    stopped(client)
    if path.read_bytes() != before:
        raise ValueError("Settings changed during inspection; original retained, no overwrite")
    if removed:
        path.write_bytes(after)
    if {p.name: digest(p) for p in sorted((client / "mods").glob("*.jar"))} != mods or digest(pack) != pack_identity:
        raise ValueError("A source JAR/pack changed; retain receipt folder for diagnosis")
    if path.read_bytes() != after:
        raise ValueError("Settings write verification failed")
    record = {"schema": 1, "scope": "STOPPED_CLIENT_STOCK_MOD_ART_SELECTION",
              "passed": True, "minecraft_launched": False, "removed_active_packs": removed,
              "remaining_active_packs": retained, "reference_model_overrides_disabled": model_overrides,
              "varied_commodities_model_overrides_disabled": varied_overrides,
              "varied_commodities_original_jar_sha256": varied["sha256"],
              "varied_worn_textures_replaced_by_this_pack": False,
              "all_runtime_jars_unchanged": len(mods), "exact_original_reference_jars_present": len(preserved),
              "reference_pack_retained_unchanged": pack_identity,
              "options_before_sha256": hashlib.sha256(before).hexdigest(),
              "options_after_sha256": hashlib.sha256(after).hexdigest(),
              "project_core_icons_and_worn_armor_retained": True,
              "graphical_acceptance": False}
    (output / "PRIVATE-RESTORE-RECEIPT.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    return record


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(restore(args.client, args.output), indent=2))
