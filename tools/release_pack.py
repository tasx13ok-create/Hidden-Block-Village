#!/usr/bin/env python3
"""Inventory existing packs and transport an accepted ZIP without changing it.

Python 3.8+, standard library only. No server execution or network operations.
See tools/RELEASE-TOOLS.md for schemas, acceptance limits, and usage.
"""

import argparse
import contextlib
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import tempfile
import unicodedata
import zipfile


TOOL_VERSION = "1.0.0"
CHUNK = 1024 * 1024
DEFAULT_PART_BYTES = 1_899_000_000
PART_LIMIT_EXCLUSIVE = 1_900_000_000
MAX_PARTS = 999
MAX_ENTRIES = 100_000
MAX_EXPANDED_BYTES = 64 * 1024**3
MAX_JSON_BYTES = 64 * 1024**2
REQUIRED_GATES = tuple("R%02d" % i for i in range(1, 19))
RUNTIME_GATES = frozenset("R%02d" % i for i in range(10, 18))
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
RESERVED = re.compile(r"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)", re.I)


class PackError(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def require(condition, code, message):
    if not condition:
        raise PackError(code, message)


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True,
                       separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def strict_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "INVALID_JSON", "Duplicate JSON key: " + key)
        result[key] = value
    return result


def reject_constant(value):
    raise PackError("INVALID_JSON", "Non-finite JSON number: " + value)


def finite_float(value):
    parsed = float(value)
    require(math.isfinite(parsed), "INVALID_JSON", "Non-finite JSON number")
    return parsed


def parse_json(raw):
    require(len(raw) <= MAX_JSON_BYTES, "LIMIT", "JSON exceeds 64 MiB limit")
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=strict_pairs,
                           parse_constant=reject_constant, parse_float=finite_float)
    except (UnicodeError, ValueError, RecursionError) as exc:
        raise PackError("INVALID_JSON", str(exc)) from exc
    require(isinstance(value, dict), "INVALID_JSON", "JSON root must be an object")
    return value


def integer(value, label, minimum=0):
    require(type(value) is int and value >= minimum, "INVALID_MANIFEST",
            label + " must be an integer >= " + str(minimum))
    return value


def sha(value):
    require(isinstance(value, str) and SHA_RE.fullmatch(value), "INVALID_MANIFEST",
            "Expected a lowercase SHA-256 digest")
    return value


def text_field(value, label):
    require(isinstance(value, str) and bool(value.strip()), "INVALID_MANIFEST",
            label + " must be a nonempty string")
    return value


def safe_relative(value, basename=False):
    require(isinstance(value, str) and value and "\\" not in value,
            "UNSAFE_PATH", "Expected a nonempty portable relative path")
    parts = value.split("/")
    require(not basename or len(parts) == 1, "UNSAFE_PATH", "Expected a filename only")
    for part in parts:
        require(part not in ("", ".", "..") and part[-1:] not in (" ", "."),
                "UNSAFE_PATH", "Unsafe path component in " + repr(value))
        require(not any(ord(c) < 32 or ord(c) == 127 or c in '<>:"|?*' for c in part),
                "UNSAFE_PATH", "Nonportable path: " + repr(value))
        require(not RESERVED.match(part), "UNSAFE_PATH", "Reserved filename: " + repr(value))
    return value


def collision_key(path):
    return unicodedata.normalize("NFC", path).casefold()


def check_names(entries):
    seen = {}
    kinds = {}
    prefixes = {}
    for entry in entries:
        name = safe_relative(entry["path"])
        key = collision_key(name)
        require(key not in seen, "PATH_COLLISION", "Duplicate/case-colliding path: " + name)
        seen[key] = name
        kinds[key] = entry["kind"]
        components = name.split("/")
        for i in range(1, len(components) + 1):
            prefix = "/".join(components[:i])
            normalized = collision_key(prefix)
            require(prefixes.get(normalized, prefix) == prefix, "PATH_COLLISION",
                    "Case/Unicode-colliding parent directory: " + name)
            prefixes[normalized] = prefix
    for key in seen:
        parts = key.split("/")
        for i in range(1, len(parts)):
            require(kinds.get("/".join(parts[:i])) != "file", "PATH_COLLISION",
                    "File is also a parent directory: " + seen[key])


def is_link(st):
    return stat.S_ISLNK(st.st_mode) or bool(getattr(st, "st_file_attributes", 0) & 0x400)


def no_links(path):
    path = Path(os.path.abspath(str(path)))
    for current in reversed([path] + list(path.parents)):
        try:
            st = current.lstat()
        except FileNotFoundError:
            continue
        require(not is_link(st), "SYMLINK", "Symlink/reparse point refused: " + str(current))
    return path


def signature(st):
    return (st.st_dev, st.st_ino, st.st_mode, st.st_size, st.st_mtime_ns, st.st_ctime_ns)


@contextlib.contextmanager
def stable_reader(path):
    path = no_links(path)
    before = path.stat()
    require(stat.S_ISREG(before.st_mode), "UNSUPPORTED_FILE", "Not a regular file: " + str(path))
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(str(path), flags)
    with os.fdopen(fd, "rb") as stream:
        require(signature(os.fstat(stream.fileno())) == signature(before),
                "SOURCE_CHANGED", "File changed while opening: " + str(path))
        yield stream
        require(signature(os.fstat(stream.fileno())) == signature(before),
                "SOURCE_CHANGED", "File changed while reading: " + str(path))
        require(signature(no_links(path).stat()) == signature(before),
                "SOURCE_CHANGED", "File replaced while reading: " + str(path))


def hash_stream(stream, writer=None, aggregate=None, max_bytes=None):
    h = hashlib.sha256()
    count = 0
    while True:
        block = stream.read(CHUNK)
        if not block:
            break
        require(max_bytes is None or count + len(block) <= max_bytes,
                "SIZE_MISMATCH", "Input exceeded its declared byte length")
        h.update(block)
        if aggregate is not None:
            aggregate.update(block)
        if writer is not None:
            writer.write(block)
        count += len(block)
    return {"bytes": count, "sha256": h.hexdigest()}


def hash_file(path):
    with stable_reader(path) as stream:
        return hash_stream(stream)


def read_json(path):
    with stable_reader(path) as stream:
        raw = stream.read(MAX_JSON_BYTES + 1)
    return parse_json(raw), {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def ensure_space(parent, needed):
    require(shutil.disk_usage(parent).free >= needed, "INSUFFICIENT_SPACE",
            "Need at least %d free bytes at %s" % (needed, parent))


def same_content(path, expected):
    if not path.exists() and not path.is_symlink():
        return False
    path = no_links(path)
    if path.stat().st_size != expected["bytes"]:
        return False
    return hash_file(path) == expected


def promote_no_clobber(temp, destination, expected):
    """Publish complete bytes only; never replace a different destination."""
    no_links(destination)
    try:
        if os.name == "nt":
            # Windows rename refuses an existing destination, unlike POSIX rename.
            os.rename(str(temp), str(destination))
        else:
            os.link(str(temp), str(destination))
    except FileExistsError:
        require(same_content(destination, expected), "OUTPUT_CONFLICT",
                "Different output already exists: " + str(destination))
        return "EXISTING_IDENTICAL_VERIFIED"
    except OSError as exc:
        raise PackError("ATOMIC_PUBLISH_FAILED",
                        "Filesystem could not publish without replacement: " + str(exc)) from exc
    return "CREATED_VERIFIED"


def atomic_json(path, value):
    path = no_links(path)
    require(path.parent.is_dir(), "OUTPUT_PARENT", "Output parent directory must exist")
    raw = canonical(value)
    expected = {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    if path.exists():
        require(same_content(path, expected), "OUTPUT_CONFLICT", "Different output exists: " + str(path))
        return expected
    ensure_space(path.parent, len(raw) + 4096)
    fd, name = tempfile.mkstemp(prefix="." + path.name + ".", suffix=".partial", dir=str(path.parent))
    temp = Path(name)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        promote_no_clobber(temp, path, expected)
    finally:
        if temp.exists():
            temp.unlink()
    return expected


def tree_snapshot(root):
    result = {}
    pending = [root]
    while pending:
        current = pending.pop()
        with os.scandir(str(current)) as children:
            for child in children:
                p = Path(child.path)
                st = child.stat(follow_symlinks=False)
                require(not is_link(st), "SYMLINK", "Symlink/reparse point refused: " + str(p))
                require(stat.S_ISREG(st.st_mode) or stat.S_ISDIR(st.st_mode),
                        "UNSUPPORTED_FILE", "Special file refused: " + str(p))
                name = p.relative_to(root).as_posix()
                safe_relative(name)
                result[name] = ("directory" if stat.S_ISDIR(st.st_mode) else "file", signature(st))
                require(len(result) <= MAX_ENTRIES, "LIMIT", "Inventory exceeds 100,000 entries")
                if stat.S_ISDIR(st.st_mode):
                    pending.append(p)
    check_names([{"path": name, "kind": value[0]} for name, value in result.items()])
    return result


def inventory_tree(root):
    before = tree_snapshot(root)
    entries = []
    expanded = 0
    for name, (kind, _) in sorted(before.items()):
        entry = {"path": name, "kind": kind}
        if kind == "file":
            require(expanded + before[name][1][3] <= MAX_EXPANDED_BYTES,
                    "LIMIT", "Inventory exceeds 64 GiB file bytes")
            entry.update(hash_file(root / name))
            expanded += entry["bytes"]
            require(expanded <= MAX_EXPANDED_BYTES, "LIMIT", "Inventory exceeds 64 GiB file bytes")
        entries.append(entry)
    require(before == tree_snapshot(root), "SOURCE_CHANGED", "Directory tree changed during inventory")
    return entries, None


def inventory_zip(path):
    with stable_reader(path) as source:
        archive = {"filename": safe_relative(path.name, basename=True), **hash_stream(source)}
        source.seek(0)
        with zipfile.ZipFile(source) as z:
            infos = z.infolist()
            require(len(infos) <= MAX_ENTRIES, "LIMIT", "ZIP exceeds 100,000 entries")
            entries = []
            total = 0
            for info in infos:
                require(info.orig_filename == info.filename, "UNSAFE_PATH", "NUL-containing ZIP path")
                name = info.filename[:-1] if info.is_dir() else info.filename
                mode = info.external_attr >> 16
                file_type = stat.S_IFMT(mode)
                require(file_type in (0, stat.S_IFREG, stat.S_IFDIR),
                        "UNSUPPORTED_FILE", "ZIP symlink/special entry refused: " + repr(name))
                require(not info.flag_bits & 1, "ENCRYPTED_ZIP", "Encrypted ZIP entries are not supported")
                require(not info.is_dir() or info.file_size == 0, "INVALID_ZIP", "ZIP directory contains data")
                require(not file_type == stat.S_IFDIR or info.is_dir(),
                        "INVALID_ZIP", "ZIP directory flag/name mismatch")
                require(not file_type == stat.S_IFREG or not info.is_dir(),
                        "INVALID_ZIP", "ZIP regular-file flag/directory-name mismatch")
                entries.append({"path": name, "kind": "directory" if info.is_dir() else "file"})
                total += info.file_size
                require(total <= MAX_EXPANDED_BYTES, "LIMIT", "ZIP exceeds 64 GiB expanded bytes")
            check_names(entries)
            for entry, info in zip(entries, infos):
                # Read every entry to EOF, including empty directories: ZipFile validates CRC.
                with z.open(info) as member:
                    hashed = hash_stream(member)
                require(hashed["bytes"] == info.file_size, "INVALID_ZIP", "Expanded size mismatch")
                if entry["kind"] == "file":
                    entry.update(hashed)
            return sorted(entries, key=lambda x: x["path"]), archive


def declaration(path, entries):
    if path is None:
        return None
    value, identity = read_json(path)
    require(value.get("schema") == "vale.pack-declaration/v1", "INVALID_DECLARATION", "Wrong declaration schema")
    require(value.get("pack_role") in ("server", "client", "combined"), "INVALID_DECLARATION", "Invalid pack_role")
    for key in ("release_id", "declared_version", "source_checkpoint"):
        text_field(value.get(key), key)
    components = value.get("components", [])
    require(isinstance(components, list), "INVALID_DECLARATION", "components must be a list")
    files = {entry["path"] for entry in entries if entry["kind"] == "file"}
    names = []
    for component in components:
        require(isinstance(component, dict), "INVALID_DECLARATION", "Component must be an object")
        name = safe_relative(component.get("path"))
        require(name in files, "INVALID_DECLARATION", "Declared component missing: " + name)
        for key in ("id", "declared_version", "provenance"):
            text_field(component.get(key), key)
        require(component.get("side") in ("server", "client", "both", "unknown"),
                "INVALID_DECLARATION", "Invalid declared component side")
        names.append({"path": name, "kind": "file"})
    check_names(names)
    return {"verification": "DECLARED_NOT_RUNTIME_VERIFIED", "document": value, "document_identity": identity}


def audit(source, declared=None):
    source = no_links(source)
    if source.is_dir():
        entries, archive = inventory_tree(source)
        kind = "directory"
    else:
        entries, archive = inventory_zip(source)
        kind = "zip"
    report = {
        "schema": "vale.pack-audit/v1", "tool_version": TOOL_VERSION,
        "status": "INVENTORY_VERIFIED", "runtime_acceptance": "NOT_EVALUATED",
        "source_kind": kind, "source_name": source.name,
        "file_count": sum(x["kind"] == "file" for x in entries),
        "directory_count": sum(x["kind"] == "directory" for x in entries),
        "total_file_bytes": sum(x.get("bytes", 0) for x in entries),
        "inventory_sha256": digest(entries),
        "payload_inventory_sha256": digest([x for x in entries if x["kind"] == "file"]),
        "entries": entries,
        "declared": declaration(declared, entries),
    }
    if archive is not None:
        report["archive"] = archive
        report["zip_crc_check"] = "ALL_ENTRIES_PASSED"
    return report


def identity(value, filename=False):
    require(isinstance(value, dict), "INVALID_MANIFEST", "Expected file identity object")
    integer(value.get("bytes"), "bytes")
    sha(value.get("sha256"))
    if filename:
        safe_relative(value.get("filename"), basename=True)
    return value


def checkpoint(value):
    require(isinstance(value, dict), "INVALID_ACCEPTANCE", "Missing source checkpoint identity")
    text_field(value.get("id"), "source checkpoint id")
    sha(value.get("sha256"))
    return value


def verify_reference(root, reference):
    require(isinstance(reference, dict), "INVALID_ACCEPTANCE", "Invalid evidence reference")
    name = safe_relative(reference.get("path"))
    expected = identity(reference)
    path = no_links(root / name)
    require(hash_file(path) == {"bytes": expected["bytes"], "sha256": expected["sha256"]},
            "EVIDENCE_MISMATCH", "Evidence file identity mismatch: " + name)
    return path


def validate_acceptance(receipt_path, report):
    receipt_path = no_links(receipt_path)
    receipt, receipt_hash = read_json(receipt_path)
    require(receipt.get("schema") == "vale.release-acceptance/v1", "INVALID_ACCEPTANCE", "Wrong acceptance schema")
    require(receipt.get("status") == "ACCEPTED", "NOT_ACCEPTED", "Release acceptance is not ACCEPTED")
    require(receipt.get("scope") == "local_client_and_server", "INVALID_ACCEPTANCE", "Wrong acceptance scope")
    require(receipt.get("archive") == report["archive"], "STALE_ACCEPTANCE", "Acceptance does not identify this exact ZIP")
    require(receipt.get("payload_inventory_sha256") == report["payload_inventory_sha256"],
            "STALE_ACCEPTANCE", "Acceptance does not bind the complete current payload")
    source = checkpoint(receipt.get("source_checkpoint"))
    source_ref = receipt.get("source_snapshot")
    verify_reference(receipt_path.parent, source_ref)
    require(source_ref["sha256"] == source["sha256"], "STALE_EVIDENCE",
            "Source snapshot hash does not match the evidence checkpoint")
    require(receipt.get("unresolved_requirements") == [], "NOT_ACCEPTED", "Unresolved requirements remain or are unspecified")
    gates = receipt.get("required_gates")
    require(isinstance(gates, list) and all(isinstance(x, str) and x.strip() for x in gates),
            "INVALID_ACCEPTANCE", "required_gates must be named gate IDs")
    require(len(set(gates)) == len(gates) and set(REQUIRED_GATES).issubset(gates),
            "INVALID_ACCEPTANCE", "Local release requires existing R01–R18 gates without duplicates")
    refs = receipt.get("evidence")
    require(isinstance(refs, list) and refs, "INVALID_ACCEPTANCE", "Missing evidence files")
    check_names([{"path": ref.get("path") if isinstance(ref, dict) else None, "kind": "file"} for ref in refs])
    passed = set()
    for ref in refs:
        path = verify_reference(receipt_path.parent, ref)
        proof, actual = read_json(path)
        require(actual == {"bytes": ref["bytes"], "sha256": ref["sha256"]},
                "EVIDENCE_MISMATCH", "Evidence changed during validation")
        require(proof.get("schema") == "vale.validation-evidence/v1", "INVALID_ACCEPTANCE", "Wrong evidence schema")
        require(proof.get("status") == "PASSED", "NOT_ACCEPTED", "Evidence is pending/failed")
        require(proof.get("payload_inventory_sha256") == report["payload_inventory_sha256"] and
                proof.get("source_checkpoint") == source,
                "STALE_EVIDENCE", "Evidence binds different payload or source bytes")
        require(proof.get("unresolved_requirements") == [], "NOT_ACCEPTED", "Evidence has unresolved requirements")
        checks = proof.get("gates")
        require(isinstance(checks, list) and checks, "INVALID_ACCEPTANCE", "Evidence has no named gates")
        log_refs = proof.get("logs", [])
        require(isinstance(log_refs, list), "INVALID_ACCEPTANCE", "Evidence logs must be a list")
        check_names([{"path": ref.get("path") if isinstance(ref, dict) else None, "kind": "file"} for ref in log_refs])
        for log_ref in log_refs:
            verify_reference(path.parent, log_ref)
        for check in checks:
            require(isinstance(check, dict), "INVALID_ACCEPTANCE", "Gate must be an object")
            gate_id = text_field(check.get("id"), "gate id")
            require(check.get("status") == "PASSED", "NOT_ACCEPTED", "Pending/failed gate: " + gate_id)
            require(gate_id not in passed, "INVALID_ACCEPTANCE", "Duplicate gate evidence: " + gate_id)
            if gate_id in RUNTIME_GATES or gate_id not in REQUIRED_GATES:
                require(log_refs, "UNBOUND_RUNTIME", "Build/runtime/supplemental gate needs hashed logs: " + gate_id)
            passed.add(gate_id)
    require(set(gates).issubset(passed), "MISSING_EVIDENCE", "Missing passed required gates: " + ", ".join(sorted(set(gates) - passed)))
    return {
        "receipt_sha256": receipt_hash["sha256"], "scope": receipt["scope"],
        "source_checkpoint": source, "required_gates": sorted(gates),
        "verification": "RECEIPT_BINDING_AND_CONSISTENCY_ONLY",
        "runtime_execution_by_tool": False,
    }


def part_name(archive_name, index, total):
    return "%s.part-%05d-of-%05d" % (archive_name, index, total)


def split(archive, receipt, destination, part_bytes=DEFAULT_PART_BYTES):
    integer(part_bytes, "part bytes", 1)
    require(part_bytes < PART_LIMIT_EXCLUSIVE, "INVALID_PART_SIZE", "Each part must be below 1,900,000,000 bytes")
    archive = no_links(archive)
    destination = no_links(destination)
    require(not destination.exists(), "OUTPUT_CONFLICT", "Split destination must be new; partial/existing directories are not resumed")
    require(destination.parent.is_dir(), "OUTPUT_PARENT", "Split destination parent must exist")
    report = audit(archive)
    require(report["source_kind"] == "zip", "INVALID_ZIP", "Split accepts an existing ZIP only")
    accepted = validate_acceptance(receipt, report)
    expected = report["archive"]
    count = (expected["bytes"] + part_bytes - 1) // part_bytes
    require(1 <= count <= MAX_PARTS, "LIMIT", "Split must produce 1–999 parts")
    ensure_space(destination.parent, expected["bytes"] + CHUNK)
    # Exclusive mkdir reserves the complete namespace; final manifest is written last.
    destination.mkdir()
    marker = destination / "INCOMPLETE.json"
    atomic_json(marker, {"status": "INCOMPLETE", "archive": expected,
                         "note": "No valid transport set until the parts manifest exists and this marker is gone."})
    parts = []
    combined = hashlib.sha256()
    with stable_reader(archive) as stream:
        for index in range(1, count + 1):
            name = part_name(archive.name, index, count)
            path = destination / name
            temp = destination / (name + ".partial")
            h = hashlib.sha256()
            size = 0
            with temp.open("xb") as output:
                remaining = min(part_bytes, expected["bytes"] - (index - 1) * part_bytes)
                while remaining:
                    block = stream.read(min(CHUNK, remaining))
                    require(block, "SOURCE_CHANGED", "Archive shortened during split")
                    output.write(block)
                    h.update(block)
                    combined.update(block)
                    size += len(block)
                    remaining -= len(block)
                output.flush()
                os.fsync(output.fileno())
            entry = {"index": index, "filename": name, "bytes": size, "sha256": h.hexdigest()}
            require(hash_file(temp) == {"bytes": size, "sha256": entry["sha256"]},
                    "WRITE_MISMATCH", "Part readback failed: " + name)
            promote_no_clobber(temp, path, {"bytes": size, "sha256": entry["sha256"]})
            if temp.exists():
                temp.unlink()
            parts.append(entry)
        require(not stream.read(1), "SOURCE_CHANGED", "Archive grew during split")
    require(combined.hexdigest() == expected["sha256"], "SOURCE_CHANGED", "Archive bytes changed after acceptance validation")
    manifest = {
        "schema": "vale.zip-parts/v1", "tool_version": TOOL_VERSION,
        "status": "TRANSPORT_VERIFIED", "archive": expected,
        "payload_inventory_sha256": report["payload_inventory_sha256"],
        "part_bytes": part_bytes, "parts": parts, "acceptance": accepted,
    }
    manifest_path = destination / (archive.name + ".parts.json")
    output_identity = atomic_json(manifest_path, manifest)
    marker.unlink()
    return {"status": "SPLIT_VERIFIED", "manifest": str(manifest_path),
            "manifest_sha256": output_identity["sha256"], "parts": count,
            "runtime_acceptance": "SUPPLIED_EVIDENCE_BINDING_ONLY"}


def validate_parts(manifest):
    require(manifest.get("schema") == "vale.zip-parts/v1" and manifest.get("status") == "TRANSPORT_VERIFIED",
            "INVALID_MANIFEST", "Not a complete parts manifest")
    archive = identity(manifest.get("archive"), filename=True)
    part_bytes = integer(manifest.get("part_bytes"), "part_bytes", 1)
    require(part_bytes < PART_LIMIT_EXCLUSIVE, "INVALID_PART_SIZE", "Part size exceeds limit")
    parts = manifest.get("parts")
    require(isinstance(parts, list) and 1 <= len(parts) <= MAX_PARTS, "INVALID_MANIFEST", "Invalid part count")
    expected_count = (archive["bytes"] + part_bytes - 1) // part_bytes
    require(len(parts) == expected_count, "INVALID_MANIFEST", "Part count does not cover archive")
    for i, part in enumerate(parts, 1):
        identity(part, filename=True)
        require(type(part.get("index")) is int and part["index"] == i, "INVALID_MANIFEST", "Part ordering mismatch")
        require(part["filename"] == part_name(archive["filename"], i, len(parts)),
                "INVALID_MANIFEST", "Part filename does not match exact archive and order")
        size = min(part_bytes, archive["bytes"] - (i - 1) * part_bytes)
        require(part["bytes"] == size, "INVALID_MANIFEST", "Part length mismatch")
    sha(manifest.get("payload_inventory_sha256"))
    return archive, parts


def join(manifest_path, output_dir, expected_manifest_sha256=None):
    manifest_path = no_links(manifest_path)
    manifest, manifest_hash = read_json(manifest_path)
    if expected_manifest_sha256 is not None:
        require(manifest_hash["sha256"] == sha(expected_manifest_sha256),
                "MANIFEST_MISMATCH", "Manifest digest does not match trusted value")
    require(not (manifest_path.parent / "INCOMPLETE.json").exists(), "INCOMPLETE_PARTS", "Incomplete split marker remains")
    archive, parts = validate_parts(manifest)
    output_dir = no_links(output_dir)
    require(output_dir.is_dir(), "OUTPUT_PARENT", "Join output directory must exist")
    destination = output_dir / archive["filename"]
    no_links(destination)
    expected = {"bytes": archive["bytes"], "sha256": archive["sha256"]}
    existing = destination.exists()
    if existing:
        require(same_content(destination, expected), "OUTPUT_CONFLICT", "Different archive already exists: " + str(destination))
    else:
        ensure_space(output_dir, archive["bytes"] + 4096)
    fd, name = (None, None) if existing else tempfile.mkstemp(
        prefix="." + archive["filename"] + ".", suffix=".partial", dir=str(output_dir))
    temp = Path(name) if name else None
    combined = hashlib.sha256()
    total = 0
    try:
        with contextlib.ExitStack() as stack:
            output = stack.enter_context(os.fdopen(fd, "wb")) if fd is not None else None
            for part in parts:
                path = manifest_path.parent / part["filename"]
                with stable_reader(path) as stream:
                    require(os.fstat(stream.fileno()).st_size == part["bytes"],
                            "PART_MISMATCH", "Part byte length mismatch: " + part["filename"])
                    actual = hash_stream(stream, writer=output, aggregate=combined, max_bytes=part["bytes"])
                require(actual == {"bytes": part["bytes"], "sha256": part["sha256"]},
                        "PART_MISMATCH", "Missing/corrupt part: " + part["filename"])
                total += actual["bytes"]
            require(total == expected["bytes"] and combined.hexdigest() == expected["sha256"],
                    "ARCHIVE_MISMATCH", "Joined bytes do not match original archive")
            if output is not None:
                output.flush()
                os.fsync(output.fileno())
        if temp is not None:
            require(hash_file(temp) == expected, "WRITE_MISMATCH", "Joined file readback failed")
        if existing:
            require(same_content(destination, expected), "OUTPUT_CONFLICT",
                    "Existing archive changed during part verification")
        state = "EXISTING_IDENTICAL_VERIFIED" if existing else promote_no_clobber(temp, destination, expected)
    finally:
        if temp is not None and temp.exists():
            temp.unlink()
    return {"status": state, "archive": str(destination), **expected,
            "parts_verified": len(parts), "runtime_acceptance": "NOT_RETESTED"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    a = sub.add_parser("audit", help="Hash every file/ZIP entry; do not infer runtime readiness")
    a.add_argument("source", type=Path)
    a.add_argument("--output", type=Path, required=True)
    a.add_argument("--declaration", type=Path)
    s = sub.add_parser("split", help="Split an accepted existing ZIP without recompression")
    s.add_argument("archive", type=Path)
    s.add_argument("--acceptance", type=Path, required=True)
    s.add_argument("--output-dir", type=Path, required=True)
    s.add_argument("--part-bytes", type=int, default=DEFAULT_PART_BYTES)
    j = sub.add_parser("join", help="Verify parts and reconstruct the exact archive")
    j.add_argument("manifest", type=Path)
    j.add_argument("--output-dir", type=Path, required=True)
    j.add_argument("--manifest-sha256")
    args = parser.parse_args(argv)
    try:
        if args.command == "audit":
            source = no_links(args.source)
            output = no_links(args.output)
            require(source != output and not (source.is_dir() and source in output.parents),
                    "OUTPUT_SEPARATION", "Audit output must be outside the audited input")
            report = audit(source, args.declaration)
            written = atomic_json(output, report)
            result = {"status": report["status"], "runtime_acceptance": "NOT_EVALUATED",
                      "file_count": report["file_count"], "inventory_sha256": report["inventory_sha256"],
                      "payload_inventory_sha256": report["payload_inventory_sha256"],
                      "output": str(output), "output_sha256": written["sha256"]}
        elif args.command == "split":
            result = split(args.archive, args.acceptance, args.output_dir, args.part_bytes)
        else:
            result = join(args.manifest, args.output_dir, args.manifest_sha256)
    except KeyboardInterrupt:
        print(json.dumps({"status": "INTERRUPTED", "note": "No incomplete file is published as a final archive; inspect INCOMPLETE.json/.partial outputs before retry."}), file=sys.stderr)
        return 130
    except (PackError, OSError, ValueError, zipfile.BadZipFile, zipfile.LargeZipFile, NotImplementedError, RuntimeError) as exc:
        print(json.dumps({"status": "FAILED", "code": getattr(exc, "code", "IO_OR_ZIP_ERROR"),
                          "message": str(exc), "note": "No release-readiness claim. Partial split directories are never resumed automatically."}), file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
