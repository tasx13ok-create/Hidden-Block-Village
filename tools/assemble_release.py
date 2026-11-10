#!/usr/bin/env python3
"""Assemble a hash-bound Vale candidate in a new directory; never accept it.

Python 3.8+, standard library only. See ASSEMBLY.md for the reviewed-plan schema.
This program does not start Minecraft, alter its source, or upload anything.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import sys
import zipfile

import release_pack as rp
import package_bootstrap as bp


VERSION = "1.0.0"
STATUS = "CANDIDATE_PENDING_ACCEPTANCE"
SERVER_EVIDENCE = "release"
CLIENT_EVIDENCE = ("CLIENT-PINS.json", "CANDIDATE-ASSEMBLY.json")
SERVER_INSTALLATION_NOTES = frozenset(("START-HERE.txt",))
CLIENT_INSTALLATION_NOTES = frozenset(("START-HERE.txt", "CLIENT-INSTALL.txt"))
MAX_INSTALLATION_NOTE_BYTES = 64 * 1024
PENDING = ["exact-artifact build verification", "dedicated-server startup",
           "Naruto.test load/save/restart", "services and Quartermaster",
           "matching-client join", "visuals and shaders", "hosted Bisect test"]


def fields(value, required, optional=()):
    rp.require(isinstance(value, dict), "INVALID_PLAN", "Expected an object")
    rp.require(set(required).issubset(value) and set(value).issubset(set(required) | set(optional)),
               "INVALID_PLAN", "Missing or unexpected fields: " + repr(sorted(value)))


def file_identity(value, source=False):
    fields(value, ("path", "bytes", "sha256") + (("file",) if source else ()))
    rp.safe_relative(value["path"])
    rp.identity(value)
    if source:
        rp.safe_relative(value["file"])
    return value


def files(report):
    return {e["path"]: e for e in report["entries"] if e["kind"] == "file"}


def short_identity(entry):
    return {"bytes": entry["bytes"], "sha256": entry["sha256"]}


def matches(actual, expected, label):
    rp.require(actual is not None and short_identity(actual) == short_identity(expected),
               "PIN_MISMATCH", "Missing or changed file: " + label)


def under(path, parent):
    return path == parent or path.startswith(parent + "/")


def world_identity(entries, world):
    subset = [e for e in entries if e["kind"] == "file" and under(e["path"], world)]
    regions = [e for e in subset if re.fullmatch(re.escape(world) + r"/region/r\.-?\d+\.-?\d+\.mca", e["path"])]
    rp.require(any(e["path"] == world + "/level.dat" for e in subset), "WORLD_MISSING", "World level.dat is missing")
    rp.require(bool(regions), "WORLD_MISSING", "No overworld region files found")
    return {"payload_inventory_sha256": rp.digest(subset), "files": len(subset),
            "bytes": sum(e["bytes"] for e in subset), "overworld_regions": len(regions)}


def validate_world(entries, declaration):
    fields(declaration, ("path", "payload_inventory_sha256", "files", "bytes", "overworld_regions"))
    rp.safe_relative(declaration["path"], basename=True)
    rp.require(declaration["path"] == "Naruto.test", "WORLD_IDENTITY", "This workflow preserves the intended Naruto.test world")
    for key in ("files", "bytes", "overworld_regions"):
        rp.integer(declaration[key], "world " + key, 1)
    rp.sha(declaration["payload_inventory_sha256"])
    actual = world_identity(entries, declaration["path"])
    rp.require(actual == {key: declaration[key] for key in actual}, "WORLD_MISMATCH", "World inventory/count/bytes differ from the reviewed plan")
    return actual


def read_small(path, limit=4 * 1024 * 1024):
    with rp.stable_reader(path) as stream:
        raw = stream.read(limit + 1)
    rp.require(len(raw) <= limit, "LIMIT", "Metadata file is too large: " + str(path))
    return raw


def property_lines(raw):
    # Minecraft 1.12.2 loads server.properties with Properties.load(InputStream):
    # ISO-8859-1 bytes, CR/LF physical lines, and odd-backslash continuations.
    pending, logical = False, ""
    for physical in re.split(r"\r\n|\r|\n", raw.decode("latin-1")):
        body = physical.lstrip(" \t\f")
        if not pending and (not body or body.startswith(("#", "!"))):
            continue
        logical = logical + body if pending else body
        slash_count = len(logical) - len(logical.rstrip("\\"))
        pending = slash_count % 2 == 1
        if pending:
            logical = logical[:-1]
        else:
            yield logical
            logical = ""
    if pending:
        yield logical


def unescape_property(value):
    result, index = [], 0
    escapes = {"t": "\t", "r": "\r", "n": "\n", "f": "\f"}
    while index < len(value):
        char = value[index]
        index += 1
        if char != "\\":
            result.append(char)
            continue
        rp.require(index < len(value), "WORLD_SELECTION", "Malformed properties escape")
        char = value[index]
        index += 1
        if char == "u":
            digits = value[index:index + 4]
            rp.require(re.fullmatch(r"[0-9a-fA-F]{4}", digits), "WORLD_SELECTION", "Malformed properties Unicode escape")
            result.append(chr(int(digits, 16)))
            index += 4
        else:
            result.append(escapes.get(char, char))
    return "".join(result)


def check_world_selection(path, world):
    # Interpret escaped/continued keys before counting them, so an apparently
    # harmless later key cannot override level-name when Java reads the file.
    values = []
    for line in property_lines(read_small(path)):
        index = 0
        while index < len(line):
            if line[index] == "\\":
                index += 2
            elif line[index] in "=: \t\f":
                break
            else:
                index += 1
        key = unescape_property(line[:index])
        while index < len(line) and line[index] in " \t\f":
            index += 1
        if index < len(line) and line[index] in "=:":
            index += 1
        while index < len(line) and line[index] in " \t\f":
            index += 1
        value = unescape_property(line[index:])
        if key == "level-name":
            values.append(value)
    rp.require(values == [world], "WORLD_SELECTION", "server.properties must select exactly one " + world + " world")


def copy_checked(source, destination, expected, progress=None):
    destination.parent.mkdir(parents=True, exist_ok=True)
    with rp.stable_reader(source) as src, destination.open("xb") as dst:
        actual = rp.hash_stream(src, writer=dst, max_bytes=expected["bytes"], progress=progress)
        dst.flush()
        os.fsync(dst.fileno())
    matches(actual, expected, str(source))
    matches(rp.hash_file(destination, progress=progress), expected, str(destination))


def copy_input(source, report, destination, progress=None):
    """Copy every original entry; no filtering or writes to the source."""
    destination.mkdir()
    counters = rp.ProgressCounters(progress, "Copying files and reading them back", report["file_count"],
                                   2 * report["total_file_bytes"])
    if report["source_kind"] == "directory":
        for entry in report["entries"]:
            target = destination / entry["path"]
            if entry["kind"] == "directory":
                target.mkdir(parents=True, exist_ok=True)
            else:
                copy_checked(source / entry["path"], target, entry,
                             progress=counters.read_bytes if progress is not None else None)
                counters.file_done()
        counters.finish()
        return
    with rp.stable_reader(source) as stream, zipfile.ZipFile(stream) as archive:
        for entry in report["entries"]:
            target = destination / entry["path"]
            if entry["kind"] == "directory":
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(entry["path"]) as src, target.open("xb") as dst:
                actual = rp.hash_stream(src, writer=dst, max_bytes=entry["bytes"],
                                        progress=counters.read_bytes if progress is not None else None)
                dst.flush()
                os.fsync(dst.fileno())
            matches(actual, entry, entry["path"])
            matches(rp.hash_file(target, progress=counters.read_bytes if progress is not None else None), entry, str(target))
            counters.file_done()
    counters.finish()


def validate_changes(changes, original, plan_dir, allowed):
    rp.require(isinstance(changes, list), "INVALID_PLAN", "replacements must be an array")
    old_paths, new_paths = set(), set()
    result = dict(original)
    for change in changes:
        fields(change, ("old", "new"))
        old, new = change["old"], change["new"]
        rp.require(old is not None or new is not None, "INVALID_PLAN", "Empty replacement")
        if old is not None:
            file_identity(old)
            rp.require(allowed(old["path"]), "CHANGE_SCOPE", "Replacement is outside allowed file locations: " + old["path"])
            rp.require(old["path"] not in old_paths, "DUPLICATE_CHANGE", "Repeated original replacement")
            old_paths.add(old["path"])
            matches(original.get(old["path"]), old, old["path"])
        if new is not None:
            file_identity(new, source=True)
            rp.require(allowed(new["path"]), "CHANGE_SCOPE", "Replacement is outside allowed file locations: " + new["path"])
            rp.require(new["path"] not in new_paths, "DUPLICATE_CHANGE", "Repeated replacement destination")
            new_paths.add(new["path"])
            matches(rp.hash_file(rp.no_links(plan_dir / new["file"])), new, new["file"])
    for path in old_paths:
        del result[path]
    for change in changes:
        new = change["new"]
        if new is not None:
            rp.require(new["path"] not in result, "OUTPUT_CONFLICT", "Replacement would overwrite an unselected source file: " + new["path"])
            result[new["path"]] = {"path": new["path"], "kind": "file", **short_identity(new)}
    rp.check_names(list(result.values()))
    return result


def installation_note_changes(changes, client=False):
    """Select note rows after the complete replacement list has been validated."""
    allowed = CLIENT_INSTALLATION_NOTES if client else SERVER_INSTALLATION_NOTES
    return [change for change in changes
            if any(row is not None and row["path"] in allowed for row in (change["old"], change["new"]))]


def validate_installation_notes(changes, original, plan_dir, client=False):
    allowed = CLIENT_INSTALLATION_NOTES if client else SERVER_INSTALLATION_NOTES
    rp.require(isinstance(changes, list), "INVALID_PLAN", "Installation notes must be an array")
    for change in changes:
        fields(change, ("old", "new"))
        old, new = change["old"], change["new"]
        rp.require(old is not None and new is not None,
                   "CHANGE_SCOPE", "Installation notes require an existing same-path replacement")
        file_identity(old)
        file_identity(new, source=True)
        rp.require(old["path"] == new["path"] and old["path"] in allowed,
                   "CHANGE_SCOPE", "Installation notes require an existing same-path replacement at a known note location")
        rp.require(0 < new["bytes"] <= MAX_INSTALLATION_NOTE_BYTES,
                   "LIMIT", "An installation note must contain 1 to 65536 bytes")
    validate_changes(changes, original, plan_dir, lambda path: path in allowed)


def preserve_installation_notes(root, changes, destination):
    """Keep exact old note bytes outside the candidate before replacing its copy."""
    preserved = []
    for change in changes:
        old = change["old"]
        copy_checked(root / old["path"], destination / old["path"], old)
        preserved.append({"path": old["path"], **short_identity(old)})
    return preserved


def apply_changes(root, changes, plan_dir):
    # All destinations are inside a newly owned incomplete candidate directory.
    # Originals remain in the selected source. Failed candidates are never resumed.
    for change in changes:
        old = change["old"]
        if old is not None:
            target = root / old["path"]
            matches(rp.hash_file(target), old, old["path"])
            target.unlink()
    for change in changes:
        new = change["new"]
        if new is not None:
            copy_checked(plan_dir / new["file"], root / new["path"], new)


class ClassReader:
    """Read only class-level Forge @Mod string values; never execute bytecode."""
    def __init__(self, raw):
        self.stream = io.BytesIO(raw)
        self.pool = [None]

    def take(self, count):
        raw = self.stream.read(count)
        rp.require(len(raw) == count, "INVALID_CLASS", "Truncated Java class")
        return raw

    def u(self, width):
        return int.from_bytes(self.take(width), "big")

    def value(self):
        tag = chr(self.u(1))
        if tag in "BCDFIJSZs":
            return self.pool[self.u(2)]
        if tag == "e":
            return (self.u(2), self.u(2))
        if tag == "c":
            return self.u(2)
        if tag == "@":
            return self.annotation()
        if tag == "[":
            return [self.value() for _ in range(self.u(2))]
        raise rp.PackError("INVALID_CLASS", "Unknown annotation value")

    def annotation(self):
        name = self.pool[self.u(2)]
        values = {}
        for _ in range(self.u(2)):
            key = self.pool[self.u(2)]
            values[key] = self.value()
        return name, values

    def mods(self):
        rp.require(self.take(4) == b"\xca\xfe\xba\xbe", "INVALID_CLASS", "Invalid Java class")
        self.take(4)
        count = self.u(2)
        while len(self.pool) < count:
            tag = self.u(1)
            if tag == 1:
                value = self.take(self.u(2)).decode("utf-8", errors="replace")
            elif tag in (3, 4):
                value = self.take(4)
            elif tag in (5, 6):
                self.take(8)
                self.pool.extend([None, None])
                continue
            elif tag in (7, 8, 16, 19, 20):
                value = self.u(2)
            elif tag in (9, 10, 11, 12, 17, 18):
                value = self.take(4)
            elif tag == 15:
                value = self.take(3)
            else:
                raise rp.PackError("INVALID_CLASS", "Unknown Java constant-pool tag")
            self.pool.append(value)
        self.take(6)
        self.take(2 * self.u(2))
        for _ in range(2):  # fields, then methods
            for _ in range(self.u(2)):
                self.take(6)
                for _ in range(self.u(2)):
                    self.take(2)
                    self.take(self.u(4))
        found = {}
        for _ in range(self.u(2)):
            name = self.pool[self.u(2)]
            size = self.u(4)
            raw = self.take(size)
            if name not in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                continue
            saved, self.stream = self.stream, io.BytesIO(raw)
            for _ in range(self.u(2)):
                descriptor, values = self.annotation()
                if descriptor == "Lnet/minecraftforge/fml/common/Mod;":
                    modid = values.get("modid")
                    rp.require(isinstance(modid, str) and modid, "INVALID_MOD", "Forge annotation has no mod ID")
                    rp.require(modid not in found, "DUPLICATE_MOD", "Repeated Forge mod ID")
                    found[modid] = values.get("version", "")
            self.stream = saved
        return found


def jar_mods(path):
    result, metadata = {}, {}
    with rp.stable_reader(path) as stream, zipfile.ZipFile(stream) as archive:
        infos = archive.infolist()
        rp.check_names([{"path": e.filename.rstrip("/"), "kind": "directory" if e.is_dir() else "file"} for e in infos])
        total = 0
        for info in infos:
            if not info.filename.endswith(".class") and info.filename != "mcmod.info":
                continue
            total += info.file_size
            rp.require(info.file_size <= 8 * 1024**2 and total <= 256 * 1024**2, "LIMIT", "JAR metadata/class inspection exceeds bound")
            raw = archive.read(info)
            if info.filename == "mcmod.info":
                value = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=rp.strict_pairs)
                rows = value if isinstance(value, list) else value.get("modList", []) if isinstance(value, dict) else []
                rp.require(isinstance(rows, list), "INVALID_MOD", "Invalid mcmod.info")
                for row in rows:
                    rp.require(isinstance(row, dict) and isinstance(row.get("modid"), str), "INVALID_MOD", "Invalid mcmod.info row")
                    mid = row["modid"]
                    rp.require(mid and mid not in metadata, "DUPLICATE_MOD", "Repeated mcmod.info ID")
                    metadata[mid] = str(row.get("version", ""))
            elif b"Lnet/minecraftforge/fml/common/Mod;" in raw:
                for mid, version in ClassReader(raw).mods().items():
                    rp.require(mid not in result, "DUPLICATE_MOD", "Multiple @Mod classes have ID " + mid)
                    result[mid] = version
    for mid, version in metadata.items():
        if mid in result and version and result[mid]:
            rp.require(version == result[mid], "MOD_VERSION", "Annotation/metadata version mismatch: " + mid)
        if mid not in result or not result[mid]:
            result[mid] = version
    return result


def check_roster(root, inventory, roster, prefix):
    rp.require(isinstance(roster, list) and roster, "INVALID_PLAN", "Complete mod roster is required")
    active = {p for p in inventory if under(p, prefix) and p.lower().endswith(".jar")}
    named, ids = set(), {}
    for mod in roster:
        fields(mod, ("path", "bytes", "sha256", "mods"))
        rp.safe_relative(mod["path"])
        rp.identity(mod)
        rp.require(mod["path"] not in named, "DUPLICATE_MOD", "Repeated mod JAR path")
        named.add(mod["path"])
        matches(inventory.get(mod["path"]), mod, mod["path"])
        rp.require(isinstance(mod["mods"], dict), "INVALID_PLAN", "mods must map observed mod IDs to versions; {} is for reviewed coremods")
        actual = jar_mods(root / mod["path"])
        rp.require(actual == mod["mods"], "MOD_IDENTITY", "Mod IDs/versions differ from reviewed roster: " + mod["path"])
        for mid in actual:
            rp.require(mid not in ids, "DUPLICATE_MOD", "Mod ID appears in multiple JARs: " + mid)
            ids[mid] = {"path": mod["path"], **short_identity(mod), "version": actual[mid]}
    rp.require(active == named, "MOD_ROSTER", "Missing or extra active JARs: " + repr(sorted(active ^ named)))
    return ids


def write_zip(root, report, destination, progress=None):
    """Deterministic metadata/order; verify every ZIP entry against the tree."""
    partial = destination.with_name(destination.name + ".partial")
    counters = rp.ProgressCounters(progress, "Compressing ZIP payload", report["file_count"], report["total_file_bytes"])
    with partial.open("xb") as stream:
        with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as archive:
            for entry in report["entries"]:
                directory = entry["kind"] == "directory"
                info = zipfile.ZipInfo(entry["path"] + ("/" if directory else ""), (1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = ((stat.S_IFDIR | 0o755) if directory else (stat.S_IFREG | 0o644)) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                if directory:
                    archive.writestr(info, b"")
                else:
                    with rp.stable_reader(root / entry["path"]) as src, archive.open(info, "w", force_zip64=True) as dst:
                        matches(rp.hash_stream(src, writer=dst, max_bytes=entry["bytes"],
                                               progress=counters.read_bytes if progress is not None else None), entry, entry["path"])
                    counters.file_done()
        stream.flush()
        os.fsync(stream.fileno())
    counters.finish()
    verified = rp.audit(partial, progress=rp.progress_scope(progress, "Verifying completed ZIP"))
    rp.require(verified["inventory_sha256"] == report["inventory_sha256"], "ZIP_MISMATCH", "ZIP inventory differs from candidate tree")
    identity = short_identity(verified["archive"])
    rp.promote_no_clobber(partial, destination, identity)
    if partial.exists():
        partial.unlink()
    verified["archive"]["filename"] = destination.name
    return verified


def retire(root, names, destination):
    retired = []
    for name in names:
        source = root / name
        if source.exists():
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            rp.require(not target.exists(), "OUTPUT_CONFLICT", "Prior-evidence destination exists")
            source.rename(target)
            retired.append(name)
    return retired


def check_bootstrap(root, declaration, inventory, roster, required_configs, required_files=()):
    fields(declaration, ("paths", "resource_sha256"))
    paths = declaration["paths"]
    rp.require(isinstance(paths, list) and len(paths) == 2 and "server.jar" in paths, "INVALID_PLAN", "Bootstrap requires server.jar and one named alias")
    rp.check_names([{"path": rp.safe_relative(p, basename=True), "kind": "file"} for p in paths])
    rp.require(all(p.endswith(".jar") for p in paths), "INVALID_PLAN", "Bootstrap aliases must be root JARs")
    rp.sha(declaration["resource_sha256"])
    rp.require(all(path in inventory for path in paths), "BOOTSTRAP_PLAN", "Bootstrap alias is missing")
    matches(inventory.get(paths[0]), inventory[paths[1]], "bootstrap aliases")
    with zipfile.ZipFile(root / paths[0]) as jar:
        rp.require(jar.namelist().count("vale-release.properties") == 1, "BOOTSTRAP_PLAN", "Expected exactly one embedded bootstrap plan")
        raw = jar.read("vale-release.properties")
        rp.require(hashlib.sha256(raw).hexdigest() == declaration["resource_sha256"], "BOOTSTRAP_PLAN", "Embedded bootstrap plan hash differs")
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
        rp.require(re.search(r"(?m)^Main-Class: village\.deployment\.ServerBootstrap\r?$", manifest), "BOOTSTRAP_MAIN", "Bootstrap main class is not selected")
    embedded = bp.validate_plan(raw, paths)
    expected = embedded["expected"]
    for name, fingerprint in expected.items():
        rp.require(name in inventory and inventory[name]["sha256"] == fingerprint, "BOOTSTRAP_PLAN", "Bootstrap expects missing/different file: " + name)
    for name in embedded["retired"]:
        rp.require(name not in inventory, "BOOTSTRAP_PLAN", "Candidate still contains a file the bootstrap would retire: " + name)
    required = {m["path"] for m in roster} | set(required_configs) | set(required_files)
    required |= {p for p in inventory if re.fullmatch(r"(?:plugins|plugins/update)/[^/]+\.jar", p)}
    if "plugins/CrazyCrates/config.yml" in inventory:
        required.add("plugins/CrazyCrates/config.yml")
    rp.require(required.issubset(expected), "BOOTSTRAP_PLAN", "Bootstrap omits required runtime/configuration/installation pins: " + repr(sorted(required - set(expected))))
    return raw, embedded["release"]


def assemble(source, plan_path, export_root, output_name=None, progress=None):
    source, plan_path, export_root = map(rp.no_links, (source, plan_path, export_root))
    plan, plan_hash = rp.read_json(plan_path)
    fields(plan, ("schema", "candidate_id", "source_checkpoint", "input_payload_inventory_sha256", "world", "replacements", "server_mods", "client", "bootstrap", "required_configs"))
    rp.require(plan["schema"] == "vale.candidate-plan/v1", "INVALID_PLAN", "Wrong plan schema")
    candidate_id = rp.safe_relative(plan["candidate_id"], basename=True)
    rp.require(re.fullmatch(r"[A-Za-z0-9_-]{1,100}", candidate_id), "INVALID_PLAN", "Use a portable candidate ID")
    rp.checkpoint(plan["source_checkpoint"])
    rp.sha(plan["input_payload_inventory_sha256"])
    name = rp.safe_relative(output_name or candidate_id, basename=True)
    destination = export_root / name
    rp.require(not destination.exists(), "OUTPUT_CONFLICT", "Candidate destination must be new; incomplete outputs are not resumed")
    rp.require(source != export_root and not (source.is_dir() and source in export_root.parents), "OUTPUT_SEPARATION", "Export root must be outside the source")
    rp.require(source != destination and plan_path != destination and destination not in plan_path.parents, "OUTPUT_SEPARATION", "Input plan/source must be outside candidate output")
    snapshot = rp.tree_snapshot(source, progress=rp.progress_scope(progress, "Source identity snapshot")) if source.is_dir() else None
    before = rp.audit(source, progress=rp.progress_scope(progress, "Source inventory"))
    rp.require(before["payload_inventory_sha256"] == plan["input_payload_inventory_sha256"], "INPUT_MISMATCH", "Complete source payload differs from reviewed input")
    world = validate_world(before["entries"], plan["world"])
    original = files(before)
    client = plan["client"]
    fields(client, ("path", "input_sha256", "replacements", "mods", "version", "forge"))
    rp.safe_relative(client["path"])
    rp.require(client["path"] == "client-install/Client.zip", "INVALID_PLAN", "Expected the matching nested Client.zip")
    rp.require(client["path"] in original and original[client["path"]]["sha256"] == rp.sha(client["input_sha256"]), "CLIENT_MISMATCH", "Nested input client hash differs")
    rp.text_field(client["version"], "client version")
    rp.text_field(client["forge"], "Forge version")
    bootstrap = plan["bootstrap"]
    fields(bootstrap, ("paths", "resource_sha256"))
    rp.require(isinstance(bootstrap["paths"], list), "INVALID_PLAN", "Bootstrap aliases must be a list")
    for path in bootstrap["paths"]:
        rp.safe_relative(path, basename=True)
    def allowed_server(path):
        return (path in bootstrap["paths"] or path in SERVER_INSTALLATION_NOTES or
                bool(re.fullmatch(r"(?:mods|mods/1\.12\.2|plugins|plugins/update)/[^/]+\.jar", path)) or
                path.startswith("config/") or bool(re.fullmatch(r"plugins/CrazyCrates/(?:config\.yml|Crates/[^/]+\.ya?ml)", path)))
    validate_changes(plan["replacements"], original, plan_path.parent, allowed_server)
    server_notes = installation_note_changes(plan["replacements"])
    validate_installation_notes(server_notes, original, plan_path.parent)
    configs = plan["required_configs"]
    rp.require(isinstance(configs, list) and len(configs) == len(set(configs)), "INVALID_PLAN", "required_configs must be a unique array")
    for path in configs:
        rp.safe_relative(path)
    changed_configs = {change["new"]["path"] for change in plan["replacements"]
                       if change["new"] is not None and (change["new"]["path"].startswith("config/") or
                                                        change["new"]["path"].startswith("plugins/CrazyCrates/"))}
    rp.require(changed_configs.issubset(configs), "INVALID_PLAN", "Every changed configuration must be required by the bootstrap")
    # This is a conservative space estimate: a full tree plus worst-case ZIP and
    # a second nested-client tree/ZIP. It does not reserve space against other apps.
    export_root.mkdir(parents=True, exist_ok=True)
    rp.no_links(export_root)
    replacement_bytes = sum(c["new"]["bytes"] for c in plan["replacements"] if c["new"] is not None)
    rp.ensure_space(export_root, 3 * before["total_file_bytes"] + 2 * replacement_bytes + 64 * 1024**2)
    destination.mkdir()
    rp.atomic_json(destination / "INCOMPLETE.json", {"status": "INCOMPLETE", "candidate_id": candidate_id})
    stage = destination / "server"
    copy_input(source, before, stage, progress=rp.progress_scope(progress, "Complete server copy"))
    check_world_selection(stage / "server.properties", plan["world"]["path"])
    prior = destination / "prior-evidence"
    retired_server = retire(stage, (SERVER_EVIDENCE,), prior / "server")
    preserved_server_notes = preserve_installation_notes(stage, server_notes, prior / "server")
    apply_changes(stage, plan["replacements"], plan_path.parent)
    nested = stage / client["path"]
    nested_before = rp.audit(nested, progress=rp.progress_scope(progress, "Original client inventory"))
    client_stage = destination / "client-work"
    copy_input(nested, nested_before, client_stage, progress=rp.progress_scope(progress, "Client extraction"))
    allowed_client = lambda path: path == "manifest.json" or path in CLIENT_INSTALLATION_NOTES or bool(re.fullmatch(r"overrides/mods/[^/]+\.jar", path))
    validate_changes(client["replacements"], files(nested_before), plan_path.parent, allowed_client)
    client_notes = installation_note_changes(client["replacements"], client=True)
    validate_installation_notes(client_notes, files(nested_before), plan_path.parent, client=True)
    retired_client = retire(client_stage, CLIENT_EVIDENCE, prior / "client")
    preserved_client_notes = preserve_installation_notes(client_stage, client_notes, prior / "client")
    apply_changes(client_stage, client["replacements"], plan_path.parent)
    manifest = rp.parse_json(read_small(client_stage / "manifest.json"))
    rp.require(manifest.get("version") == client["version"] and manifest.get("minecraft", {}).get("version") == "1.12.2", "CLIENT_VERSION", "Client manifest version differs")
    rp.require(manifest.get("minecraft", {}).get("modLoaders") == [{"id": "forge-" + client["forge"], "primary": True}], "CLIENT_VERSION", "Client Forge loader differs")
    rp.require(manifest.get("overrides") == "overrides" and manifest.get("files") == [], "CLIENT_INCOMPLETE", "Client must contain its complete local overrides; unresolved remote files are unsupported")
    client_report = rp.audit(client_stage, progress=rp.progress_scope(progress, "Client mod roster inventory"))
    client_ids = check_roster(client_stage, files(client_report), client["mods"], "overrides/mods")
    server_report = rp.audit(stage, progress=rp.progress_scope(progress, "Server mod roster inventory"))
    server_ids = check_roster(stage, files(server_report), plan["server_mods"], "mods")
    rp.require({"valeorders", "valeencounters"}.issubset(server_ids) and {"valeorders", "valeencounters"}.issubset(client_ids), "CLIENT_MISMATCH", "Core and Encounters must exist on both sides")
    shared = sorted(set(server_ids) & set(client_ids))
    for modid in shared:
        rp.require(short_identity(server_ids[modid]) == short_identity(client_ids[modid]) and server_ids[modid]["version"] == client_ids[modid]["version"], "CLIENT_MISMATCH", "Shared module bytes/version differ: " + modid)
    client_note = {"schema": "vale.client-candidate/v1", "status": STATUS,
                   "candidate_id": candidate_id, "mods": client["mods"],
                   "source_checkpoint": plan["source_checkpoint"], "runtime_acceptance": "NOT_RUN"}
    rp.atomic_json(client_stage / "CANDIDATE-ASSEMBLY.json", client_note)
    client_report = rp.audit(client_stage, progress=rp.progress_scope(progress, "Final client inventory"))
    new_client = destination / "Client-Candidate.zip"
    write_zip(client_stage, client_report, new_client, progress=rp.progress_scope(progress, "Matching client ZIP"))
    nested.unlink()  # Owned incomplete output only; original nested ZIP is untouched.
    copy_checked(new_client, nested, rp.hash_file(new_client))
    server_report = rp.audit(stage, progress=rp.progress_scope(progress, "Bootstrap payload inventory"))
    note_pins = [change["new"]["path"] for change in server_notes] + ([client["path"]] if client_notes else [])
    raw_bootstrap, release_id = check_bootstrap(stage, bootstrap, files(server_report), plan["server_mods"], configs, note_pins)
    rp.require(release_id == candidate_id, "BOOTSTRAP_PLAN", "Embedded release ID must equal candidate_id")
    rp.require(not any((stage / ("vale-update-state/" + release_id + suffix)).exists() for suffix in (".done", ".pending")),
               "BOOTSTRAP_STATE", "Input contains an existing state marker for this candidate; use a new reviewed release ID")
    (stage / "release").mkdir(exist_ok=True)
    with (stage / "release/release-plan.properties").open("xb") as output:
        output.write(raw_bootstrap)
    candidate = {"schema": "vale.server-candidate/v1", "status": STATUS,
                 "tool_version": VERSION, "candidate_id": candidate_id,
                 "plan_sha256": plan_hash["sha256"], "source_checkpoint": plan["source_checkpoint"],
                 "source_payload_inventory_sha256": before["payload_inventory_sha256"],
                 "world": {"path": plan["world"]["path"], **world},
                 "shared_mod_ids": shared, "runtime_acceptance": "NOT_RUN",
                 "pending_checks": PENDING, "prior_evidence": "INVALIDATED_FOR_THIS_CANDIDATE"}
    rp.atomic_json(stage / "release/CANDIDATE-ASSEMBLY.json", candidate)
    after = rp.audit(stage, progress=rp.progress_scope(progress, "Final candidate inventory"))
    validate_world(after["entries"], plan["world"])
    check_world_selection(stage / "server.properties", plan["world"]["path"])
    # Verify every unchanged file, not only the map. Only explicitly replaced
    # files, the rebuilt nested client and invalidated release evidence may differ.
    exempt = {c["old"]["path"] for c in plan["replacements"] if c["old"] is not None} | {client["path"]}
    final_files = files(after)
    for path, expected in original.items():
        if path not in exempt and not under(path, SERVER_EVIDENCE):
            matches(final_files.get(path), expected, path)
    final_zip = destination / (candidate_id + "-server-candidate.zip")
    zip_report = write_zip(stage, after, final_zip, progress=rp.progress_scope(progress, "Complete server ZIP"))
    if snapshot is not None:
        rp.require(snapshot == rp.tree_snapshot(source, progress=rp.progress_scope(progress, "Final source identity snapshot")),
                   "SOURCE_CHANGED", "Source tree changed during assembly")
    else:
        counters = rp.ProgressCounters(progress, "Final source archive identity", 1, before["archive"]["bytes"])
        matches(rp.hash_file(source, progress=counters.read_bytes if progress is not None else None), before["archive"], str(source))
        counters.file_done()
        counters.finish()
    rp.atomic_json(destination / "input-inventory.json", before)
    rp.atomic_json(destination / "candidate-inventory.json", after)
    rp.atomic_json(destination / "client-inventory.json", client_report)
    result = {**candidate, "archive": zip_report["archive"],
              "payload_inventory_sha256": after["payload_inventory_sha256"],
              "zip_crc_check": zip_report["zip_crc_check"],
              "original_source": "TOOL_PERFORMED_NO_WRITES", "source_change_check": "NO_CHANGE_OBSERVED_AT_COMPLETION",
              "world_preservation": "ALL_FILE_HASHES_MATCH",
              "prior_server_evidence_moved_outside_candidate": retired_server,
              "prior_client_evidence_moved_outside_candidate": retired_client}
    if preserved_server_notes or preserved_client_notes:
        result["prior_installation_notes_preserved_outside_candidate"] = {
            "server": preserved_server_notes, "client": preserved_client_notes}
    rp.atomic_json(destination / "ASSEMBLY.json", result)
    (destination / "INCOMPLETE.json").unlink()
    return {"status": STATUS, "output": str(destination), "archive": str(final_zip),
            "sha256": zip_report["archive"]["sha256"], "runtime_acceptance": "NOT_RUN"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Complete frozen server folder or ZIP")
    parser.add_argument("--plan", type=Path, required=True, help="Reviewed exact-byte assembly plan")
    parser.add_argument("--export-root", type=Path, required=True, help="Output parent, e.g. D:\\Codex\\Vale-Export")
    parser.add_argument("--name", help="New output directory name; defaults to candidate_id")
    parser.add_argument("--progress", action="store_true", help="Print aggregate progress on stdout before the final JSON result")
    args = parser.parse_args(argv)
    try:
        result = assemble(args.source, args.plan, args.export_root, args.name,
                          progress=rp.ConsoleProgress() if args.progress else None)
    except KeyboardInterrupt:
        print(json.dumps({"status": "INTERRUPTED", "runtime_acceptance": "NOT_RUN", "note": "Retain incomplete output; use a fresh directory after fixing the cause."}), file=sys.stderr)
        return 130
    except (rp.PackError, OSError, ValueError, KeyError, IndexError, TypeError, RecursionError, zipfile.BadZipFile, RuntimeError) as exc:
        print(json.dumps({"status": "FAILED", "code": getattr(exc, "code", "IO_OR_PLAN_ERROR"), "message": str(exc),
                          "runtime_acceptance": "NOT_RUN", "note": "Original source was not modified. Incomplete output is never resumed automatically."}), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
