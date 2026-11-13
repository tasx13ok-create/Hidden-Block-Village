#!/usr/bin/env python3
"""Build the owner's three substantial Claude input books from reviewed sources.

No padding, arbitrary truncation, private inputs or generated history. Character
counts are Unicode characters in the exact LF output, not UTF-8 byte counts.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INSTRUCTIONS = [
    "collaboration/FABLE-PROJECT-INSTRUCTIONS.md", "AGENTS.md",
    ".agents/skills/hidden-block-village/SKILL.md", "collaboration/FABLE-TASKS.md",
    "design/NARUTO-FUN-BRIEF.md", "design/IMPLEMENTATION-PROMPT.md",
    "design/SYSTEMS-SPEC.md", "docs/build-modules.md",
]
CONTEXT = [
    "design/MASTER-PLAN.md", "design/DUNGEON-ATLAS.md",
    "design/MOD-REBUILD-MATRIX.md", "design/REFERENCE-RESEARCH.md",
    "design/ACADEMY-CAMPAIGN.md", "docs/native-checks.md",
]
MEMORY = [
    "collaboration/fable/MEMORY.md", "design/backlog.json",
    "release/evidence/academy0310-verification.json",
    "release/evidence/v17-intake-20261113.json",
]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def generate(destination):
    destination = destination.resolve()
    if destination.exists():
        raise ValueError("Use a new generated-output directory; existing bundles are preserved")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    moment = datetime.now(timezone.utc).isoformat()
    sources = {}

    def read(name):
        # Explicit allowlists above are the only project source inputs; no worlds,
        # credentials, local logs or private NPC content is discovered or read.
        path = ROOT / name
        if path.is_symlink():
            raise ValueError("Source symlink refused")
        raw = path.read_bytes()
        sources[name] = {"path": name, "bytes": len(raw), "sha256": sha(raw)}
        return raw.decode("utf-8").replace("\r\n", "\n")

    def include(name):
        content = read(name)
        if name.endswith(".json"):
            content = "```json\n" + content.rstrip() + "\n```\n"
        return "\n\n---\n\n## Repository source: " + name + "\n\n" + content.rstrip() + "\n"

    common = ("\n\nGenerated UTC: " + moment + ". Base Git commit: `" + commit + "`.\n\n"
              "This is a source-backed offline snapshot. Source sections retain their actual text, "
              "apart from LF line-ending normalization and Markdown wrappers. New source files can be "
              "ahead of the recorded base commit; the accompanying manifest pins every input byte hash. "
              "Read current Git state before acting on an old snapshot. Plans are not completed work.\n")
    instructions = ("# Fable: detailed project instructions\n" + common + "\n"
                    "Use these instructions to implement the owner's Naruto Minecraft game in coordination "
                    "with Codex. The latest owner fun/pacing direction takes precedence over older compulsory "
                    "academy examples. Work within the assigned branch and file boundaries. This book contains "
                    "the actual standing rules, execution task, power/world behavior contracts and build workflow; "
                    "it does not create spending, production-deployment or external-messaging permission.\n"
                    "\nContents: collaboration instructions; repository rules; project skill; Fable task packet; "
                    "Naruto fun contract; implementation prompt; systems specification; source-build procedure.\n")
    instructions += "".join(include(name) for name in INSTRUCTIONS)

    context = ("# Fable: detailed project and reference context\n" + common + "\n"
               "Read this book to understand the product, the five dungeon campaigns, all 49 reference-mod "
               "dispositions, evidence quality, the academy target and actual native verification procedure. "
               "Use the separate memory book for current facts and hashes. No proprietary JAR code, world "
               "payload, player record or private NPC asset is embedded here.\n\n"
               "Important version distinction: some native documentation retains the historical 0.3.9 fixture "
               "name and result filename. The current Core 0.3.10 receipt proves 1,261 assertions, including "
               "103 academy assertions. Do not infer that every older example or proposed feature is current.\n\n"
               "Contents: master plan; dungeon atlas; complete rebuild matrix; primary/community research "
               "and its limits; full academy specification; native test reproduction and evidence boundaries.\n")
    context += "".join(include(name) for name in CONTEXT)
    intake = json.loads(read("release/evidence/v17-intake-20261113.json"))
    context += ("\n\n## Derived v17 paste-height check\n\n"
                "This is a calculation from observed schematic metadata, not a completed native paste. "
                "WorldEdit origin plus Y offset can put the bottom below Minecraft's world bounds. "
                "Choose and validate an explicit paste anchor; do not silently clip the authored base.\n\n"
                "| Schematic | Stored origin Y | Offset Y | Bottom at stored origin | Top at stored origin |\n"
                "| --- | ---: | ---: | ---: | ---: |\n")
    by_file = {row["file"]: row for row in intake["schematics"]}
    for row in intake["structural_scan"]:
        meta = row["nbt_origin"]
        bottom = meta["WEOriginY"] + meta["WEOffsetY"]
        top = bottom + by_file[row["file"]]["height"] - 1
        context += ("| " + row["file"] + " | " + str(meta["WEOriginY"]) + " | " + str(meta["WEOffsetY"])
                    + " | " + str(bottom) + " | " + str(top) + " |\n")
    context += ("\nSakura, Cloud, Sand and Nether therefore require a deliberate height/alignment choice "
                "if using those stored origins. Mist's stored metadata starts at Y=0. Actual world "
                "placement still requires terrain, routes, water, tile data, entity and restart checks.\n")

    memory = ("# Fable: factual project memory and evidence\n" + common + "\n"
              "The initial memory seed is maintained separately from the attached evidence appendices. "
              "Update facts after actual work; never claim that Fable ran Codex's tests or has started "
              "a branch merely because this packet exists. The backlog below records what is planned, "
              "in progress or verified at the snapshot; planned rows are not achievements. Exact hashes "
              "are included for reproducible identification rather than a fabricated completion score.\n\n"
              "Contents: current memory; all 92 work packages; final academy/native evidence; v17/world "
              "recovery evidence; all 49 reference-JAR identities and declared mod metadata.\n")
    memory += "".join(include(name) for name in MEMORY)
    inventory = json.loads(read("design/reference-mod-inventory.json"))
    concise = {
        "source": "design/reference-mod-inventory.json",
        "archive_bytes": inventory["archive_bytes"], "archive_sha256": inventory["archive_sha256"],
        "jar_count": inventory["jar_count"],
        "scope": "Identity/CRC/class metadata only; no behavioral parity or redistribution permission inferred",
        "jars": [{"file": row["file"], "bytes": row["bytes"], "sha256": row["sha256"],
                  "crc_verified": row["crc_verified"], "classes": row["classes"],
                  "declared_mods": [{"modid": mod.get("modid"), "name": mod.get("name"),
                                     "version": mod.get("version"), "minecraft": mod.get("mcversion")}
                                    for mod in row["declared_mods"]]}
                 for row in inventory["jars"]],
    }
    # One object per JAR keeps the full metadata without spending context on
    # repeated indentation; no record or field is cut to meet a quota.
    inventory_text = "{\n" + "\n".join("  " + json.dumps(key) + ": " + json.dumps(value, ensure_ascii=False) + ","
                                           for key, value in concise.items() if key != "jars")
    inventory_text += "\n  \"jars\": [\n" + ",\n".join("    " + json.dumps(row, ensure_ascii=False, separators=(",", ":"))
                                                         for row in concise["jars"]) + "\n  ]\n}"
    memory += ("\n\n---\n\n## Condensed reference inventory: all 49 exact JAR identities\n\n"
               "This preserves every JAR's identity and declared mods while omitting repetitive resource-extension "
               "counts and external URLs. The full source inventory remains in the repository. A missing or "
               "placeholder declaration stays unresolved; it is not replaced with a guessed identity.\n\n```json\n"
               + inventory_text + "\n```\n")

    books = {"FABLE-INSTRUCTIONS.md": instructions, "FABLE-CONTEXT.md": context, "FABLE-MEMORY.md": memory}
    for name, text in books.items():
        if not 95000 <= len(text) <= 105000:
            raise ValueError(name + " is outside the approximate 100,000-character target: " + str(len(text)))
    destination.mkdir(parents=True)
    manifest = {"schema": 1, "generated_utc": moment, "base_git_commit": commit,
                "count_definition": "Unicode characters, including LF newlines; UTF-8 bytes reported separately",
                "padding_added": False, "arbitrary_truncation": False,
                "private_payloads_included": False, "sources": sorted(sources.values(), key=lambda row: row["path"]),
                "files": []}
    for name, text in books.items():
        raw = text.encode("utf-8")
        (destination / name).write_bytes(raw)
        manifest["files"].append({"file": name, "characters": len(text), "utf8_bytes": len(raw),
                                  "lines": text.count("\n"), "sha256": sha(raw)})
    start = ("FABLE PROJECT SETUP\n\n"
             "FABLE-INSTRUCTIONS.md: detailed execution rules and implementation contracts.\n"
             "FABLE-CONTEXT.md: product, dungeon, reference and verification explanation.\n"
             "FABLE-MEMORY.md: dated factual state plus evidence/backlog/reference appendices.\n\n"
             "Attach these three files to Claude. In the project instructions or first message write:\n"
             "Read FABLE-INSTRUCTIONS.md as the project instructions, FABLE-CONTEXT.md as background, "
             "and FABLE-MEMORY.md as dated memory. Verify current repo state, then implement the "
             "assigned Fable task in an isolated branch. Plans and old evidence are not new achievements.\n\n"
             "If the app rejects a large pasted instructions field, attach the instructions file and "
             "use the short instruction above; do not silently truncate the books. Keep source links "
             "and relative paths anchored to the actual repository. A manifest records exact counts/hashes.\n")
    (destination / "START-HERE.txt").write_text(start, encoding="utf-8", newline="\n")
    (destination / "MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    archive = destination / "Fable-Project-Setup.zip"
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as zipped:
        for name in list(books) + ["START-HERE.txt", "MANIFEST.json"]:
            zipped.write(destination / name, name)
    with zipfile.ZipFile(archive) as zipped:
        if zipped.testzip() is not None:
            raise ValueError("Generated archive CRC failure")
        for row in manifest["files"]:
            raw = zipped.read(row["file"])
            if sha(raw) != row["sha256"] or len(raw.decode("utf-8")) != row["characters"]:
                raise ValueError("Generated archive content mismatch")
    print(json.dumps({"directory": str(destination), "files": manifest["files"],
                      "archive_bytes": archive.stat().st_size, "archive_crc_verified": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=ROOT / "exports/Fable-Project-Setup-20261113")
    generate(parser.parse_args().destination)
