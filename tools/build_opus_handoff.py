#!/usr/bin/env python3
"""Build exactly 1,000,000 useful Unicode characters from a pinned source commit.

Includes complete instruction/context/memory books and whole relevant source/test
files. A subset-sum selects whole files; no padding or mid-file truncation.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = 1000000
BOOKS = ["collaboration/claude-inputs/FABLE-INSTRUCTIONS.md",
         "collaboration/claude-inputs/FABLE-CONTEXT.md",
         "collaboration/claude-inputs/FABLE-MEMORY.md"]
CORE_NAMES = {"AcademyCampaign", "AcademyCourse", "AcademyEvents", "AcademyProgress", "Missions",
              "PlayerState", "RewardLedger", "Arts", "Combat", "Adventures", "Targets", "OwnedSkills",
              "AbilityActions", "AbilitySnapshot", "AbilityLoadoutSnapshot", "Gear", "OriginalArmorRegistry",
              "OriginalArmorLoadoutProvider", "Forms", "Summons", "FieldRogue", "ValeOrders"}
CONTENT_AREAS = {"vale-residents", "vale-residents-server-bridge", "vale-encounters", "vale-world-compat", "village-standards"}


def git(*args):
    return subprocess.check_output(["git"] + list(args), cwd=ROOT)


def generate(args):
    destination = args.destination.resolve()
    if destination.exists():
        raise ValueError("Use a new output directory; existing handoffs are preserved")
    commit = git("rev-parse", args.commit + "^{commit}").decode().strip()
    moment = args.generated_utc or datetime.now(timezone.utc).isoformat()
    names = git("ls-tree", "-r", "--name-only", commit).decode("utf-8").splitlines()
    candidates = [name for name in names if name.startswith("modules/") and name.endswith(".java")]
    needed = BOOKS + candidates
    blobs = {}
    process = subprocess.Popen(["git", "cat-file", "--batch"], cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for name in needed:
            process.stdin.write((commit + ":" + name + "\n").encode("utf-8"))
            process.stdin.flush()
            header = process.stdout.readline().decode().split()
            if len(header) != 3 or header[1] != "blob":
                raise ValueError("Missing committed source: " + name)
            raw = process.stdout.read(int(header[2]))
            if process.stdout.read(1) != b"\n":
                raise ValueError("Git blob framing error")
            blobs[name] = raw
    finally:
        process.stdin.close()
        process.stdout.close()
        process.wait(timeout=30)

    def unit(name, book=False):
        raw = blobs[name]
        text = raw.decode("utf-8").replace("\r\n", "\n")
        digest = hashlib.sha256(raw).hexdigest()
        label = "PROJECT BOOK" if book else "READ-ONLY IMPLEMENTATION/TEST REFERENCE"
        index = "- " + name + " | " + str(len(text)) + " source characters | SHA256 " + digest + "\n"
        body = "\n\n---\n\n# " + label + ": " + name + "\n\n"
        body += "Source commit: `" + commit + "`; original-byte SHA256: `" + digest + "`.\n\n"
        if not book:
            body += "```java\n" + text.rstrip("\n") + "\n```\n"
        else:
            body += text
        return {"path": name, "index": index, "body": body, "weight": len(index) + len(body),
                "sha256": digest, "source_characters": len(text), "source_bytes": len(raw)}

    units = {name: unit(name, name in BOOKS) for name in needed}
    required = BOOKS + [name for name in candidates if name.split("/")[1] in CONTENT_AREAS
                       or Path(name).stem in CORE_NAMES and "/src/main/java/" in name]
    required = list(dict.fromkeys(required))
    remaining = [name for name in candidates if name not in required]
    remaining.sort(key=lambda name: (0 if "recoverychecks" in name else 1 if "tests" in name else 2,
                                     0 if "vale-orders" in name else 1, name))
    prefix = ("# Opus 5.5 / Fable: complete Hidden Block Village implementation handoff\n\n"
              "Owner-requested artifact: exactly 1,000,000 Unicode characters including LF line breaks. "
              "This is a substantial working prompt and source-reference corpus, not one million characters "
              "of newly invented instructions. Whole project books, real implementation and actual test "
              "sources are included. No quota padding, repeated file copies, private payloads or arbitrary "
              "mid-file truncation were added.\n\n"
              "Generated UTC: " + moment + ". Pinned source commit: `" + commit + "`.\n\n"
              "## Your active role\n\n"
              "You are Fable, the owner's Claude assistant, collaborating with Codex in the same Naruto "
              "Minecraft project. Continue your current isolated branch; do not restart completed work "
              "or replace your newer verified findings with this snapshot. Read current AGENTS.md, "
              "CLAUDE.md, docs/current-handoff.md and collaboration/BOARD.md before edits. Use the "
              "relevant sections of this long file as needed instead of wasting the sprint rereading "
              "unrelated source. Source excerpts are read-only references, not permission to edit every "
              "listed file.\n\n"
              "The owner wants a legitimately Naruto game inside Minecraft: powerful armor/modes with "
              "real moves, satisfying weapon combos, distinct interacting jutsu, responsive enemies, "
              "living villages, rewarding short missions and detailed dungeons. Preserve Minecraft's "
              "building, crafting, inventory, exploration and multiplayer. Avoid stretched-out mandatory "
              "onboarding, stat-only reskins, repetitive grind and claims of completion based on counts.\n\n"
              "Codex owns Core combat, Lightning Chakra Armor, controls and integration. You own the "
              "v17 dungeon/content package assigned in the books. The owner has now explicitly authorized "
              "subagents: Codex has separate workers on server archive intake, starter nature mechanics "
              "and weapon combos. Preserve these boundaries. Coordinate shared interfaces before editing "
              "the same module. The API reviewer is explicitly deferred and must remain off.\n\n"
              "Sprint deadline: 2026-11-14 13:32:41 UTC. This is not a fresh twenty-hour allocation. "
              "The full game remains unfinished. Prefer implementation, integration, meaningful native "
              "and player checks, and reviewable branch checkpoints over another master plan.\n\n"
              "## Changes after the pinned books\n\n"
              "The full server archive has been located at D:/archive-2026-11-09T202203Z.zip "
              "(8,944,663,680 bytes; 6,873 entries). It contains Magma configuration, mods, plugins and "
              "Naruto.test. Strict extraction found a duplicate/case collision at plugins/locations.yml; "
              "a dedicated worker is preserving conflicts and validating recovery. Do not treat its "
              "failed first recovery directory as a complete server or silently overwrite collisions. "
              "The world-only recovery and v17 originals remain preserved. Codex's Lightning kit draft "
              "is under construction and is not part of this pinned source or a verified release.\n\n"
              "The three books below describe their own earlier generation point; these current notes "
              "and newer verified Git state take precedence. Preserve original worlds, private NPC/player "
              "data and live game sessions. Do not upload binaries or execute unreviewed server scripts "
              "merely because they appear in an archive.\n\n"
              "## How to deliver useful work\n\n"
              "Finish connected behavior: normal-player acquisition or clear practice access, understandable "
              "inputs, actual effect, visible response, failure/retry, reward settlement and reconnect. "
              "Dungeons need explicit paste alignment/heights, intact tile data, readable routes, encounter "
              "lifecycle and an exit. Record exact branch/commit, changed paths, tests actually run and "
              "remaining gaps in collaboration/fable/STATUS.md. Push your branch for Codex to review; "
              "do not overwrite main or claim an unobserved review.\n\n"
              "## Source index\n\n")
    footer = ("\n\n---\n\n# End of the pinned handoff\n\n"
              "The companion manifest records exact selected source paths, original byte hashes, "
              "character counts and artifact identity. All source units are complete. New live facts "
              "must be checked in the repository; this file creates no automatic cross-agent messaging, "
              "production-deployment or API-spending authority. Continue implementing the assigned "
              "playable package, then publish a reviewable branch checkpoint with honest evidence.\n")
    fixed_weight = len(prefix) + len(footer) + sum(units[name]["weight"] for name in required)
    if fixed_weight >= TARGET:
        raise ValueError("Required reference scope exceeds target; choose an explicit smaller scope")
    selected = None
    for reserve in (60000, 120000, 240000, TARGET):
        picked, pool = [], []
        current = fixed_weight
        for name in remaining:
            if current + units[name]["weight"] <= TARGET - reserve:
                picked.append(name)
                current += units[name]["weight"]
            else:
                pool.append(name)
        gap = TARGET - current
        bits, history = 1, []
        mask = (1 << (gap + 1)) - 1
        for name in pool:
            history.append(bits)
            bits = (bits | bits << units[name]["weight"]) & mask
        if not (bits >> gap) & 1:
            continue
        extra = []
        for name, previous in reversed(list(zip(pool, history))):
            if not (previous >> gap) & 1:
                extra.append(name)
                gap -= units[name]["weight"]
        if gap != 0:
            raise ValueError("Whole-file reconstruction failed")
        selected = required + picked + list(reversed(extra))
        break
    if selected is None:
        raise ValueError("No exact whole-file combination; no padding will be generated")
    if len(selected) != len(set(selected)):
        raise ValueError("Repeated source path")
    text = prefix + "".join(units[name]["index"] for name in selected)
    text += "".join(units[name]["body"] for name in selected) + footer
    if len(text) != TARGET:
        raise ValueError("Character count differs from requested target")
    destination.mkdir(parents=True)
    raw = text.encode("utf-8")
    output = destination / "OPUS55-FULL-HANDOFF-1000000-CHARS.md"
    output.write_bytes(raw)
    manifest = {"schema": 1, "generated_utc": moment, "source_commit": commit,
                "file": output.name, "characters": len(text), "utf8_bytes": len(raw),
                "sha256": hashlib.sha256(raw).hexdigest(), "whole_source_units": len(selected),
                "padding_added": False, "arbitrary_truncation": False, "private_payloads_included": False,
                "sources": [{key: units[name][key] for key in ("path", "sha256", "source_characters", "source_bytes")}
                            for name in selected]}
    (destination / "MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    archive = destination / "Opus55-Full-Handoff.zip"
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.write(output, output.name)
        zipped.write(destination / "MANIFEST.json", "MANIFEST.json")
    with zipfile.ZipFile(archive) as zipped:
        if zipped.testzip() is not None or zipped.read(output.name) != raw:
            raise ValueError("Archive verification failed")
    print(json.dumps({key: manifest[key] for key in ("file", "characters", "utf8_bytes", "sha256", "whole_source_units")}, indent=2))
    print("Archive CRC and byte comparison passed: " + str(archive))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", default="HEAD")
    parser.add_argument("--generated-utc")
    parser.add_argument("--destination", type=Path, default=ROOT / "exports/Opus55-Full-Handoff-20261113")
    generate(parser.parse_args())
