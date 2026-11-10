#!/usr/bin/env python3
"""Package an owned Vale wrapper into an exact local launcher JAR, without downloads.

Only owned ServerBootstrap classes, its plan and Main-Class may change. The
result remains a candidate: packaging does not launch or validate Minecraft.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import zipfile

import release_pack as rp


OWN_MAIN = "village.deployment.ServerBootstrap"
FORGE_MAIN = "net.minecraftforge.fml.relauncher.ServerLaunchWrapper"
MANIFEST = "META-INF/MANIFEST.MF"
RESOURCE = "vale-release.properties"
OWN_CLASS = re.compile(r"^village/deployment/ServerBootstrap(?:\$[A-Za-z0-9_$]+)?\.class$")
MAIN_CLASS_PATH = "village/deployment/ServerBootstrap.class"
STATUS = "BOOTSTRAP_CANDIDATE_PENDING_ACCEPTANCE"


def raw_identity(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def small_member(archive, name, limit=1024 * 1024):
    info = archive.getinfo(name)
    rp.require(info.file_size <= limit, "LIMIT", "Oversized metadata member: " + name)
    return archive.read(info)


def manifest_attributes(raw):
    """Parse manifest sections, preserving source lines for a one-field update."""
    rp.require(len(raw) <= 1024 * 1024, "LIMIT", "Manifest exceeds one MiB")
    lines = raw.splitlines(keepends=True)
    sections, current, previous = [{}], None, None
    for index, raw_line in enumerate(lines):
        line = raw_line.rstrip(b"\r\n")
        if not line:
            sections.append({})
            current = previous = None
            continue
        if line.startswith(b" "):
            rp.require(previous is not None, "INVALID_MANIFEST", "Manifest continuation without a header")
            previous["value"] += line[1:].decode("utf-8")
            previous["end"] = index + 1
            continue
        rp.require(b": " in line, "INVALID_MANIFEST", "Invalid manifest header")
        key, value = line.split(b": ", 1)
        key = key.decode("ascii").lower()
        rp.require(re.fullmatch(r"[a-z0-9_-]+", key) and key not in sections[-1], "INVALID_MANIFEST", "Duplicate/invalid manifest header")
        previous = {"value": value.decode("utf-8"), "start": index, "end": index + 1}
        sections[-1][key] = previous
    rp.require(sections and sections[0].get("manifest-version", {}).get("value") == "1.0", "INVALID_MANIFEST", "Manifest-Version must be 1.0")
    for section in sections:
        for key in section:
            rp.require(key != "signature-version" and "-digest" not in key, "SIGNED_JAR", "Signed/digested manifest cannot be modified")
    return lines, sections


def update_manifest(raw):
    lines, sections = manifest_attributes(raw)
    entry = sections[0].get("main-class")
    rp.require(entry is not None and entry["value"] in (FORGE_MAIN, OWN_MAIN), "UNSUPPORTED_MAIN", "Input Main-Class must be the reviewed Forge launcher or owned Vale bootstrap")
    lines[entry["start"]:entry["end"]] = [("Main-Class: " + OWN_MAIN + "\r\n").encode()]
    updated = b"".join(lines)
    manifest_attributes(updated)
    return updated, entry["value"]


def reject_signatures(report):
    for entry in report["entries"]:
        name = entry["path"].upper()
        if entry["kind"] == "file" and name.startswith("META-INF/"):
            basename = name.rsplit("/", 1)[-1]
            rp.require(not basename.endswith((".SF", ".RSA", ".DSA", ".EC")) and not basename.startswith("SIG-"),
                       "SIGNED_JAR", "Signed JAR is not repackaged; signatures are never silently stripped")


def parse_properties(raw):
    rp.require(len(raw) <= 1024 * 1024, "LIMIT", "Embedded plan exceeds one MiB")
    result = {}
    for line in raw.decode("utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        rp.require("=" in line and "\\" not in line, "INVALID_PLAN", "Use unescaped key=value properties")
        key, value = (part.strip() for part in line.split("=", 1))
        rp.require(key and key not in result, "INVALID_PLAN", "Empty or duplicate properties key")
        result[key] = value
    return result


def validate_plan(raw, aliases):
    properties = parse_properties(raw)
    release = properties.get("release", "")
    rp.require(re.fullmatch(r"[A-Za-z0-9_-]{1,100}", release), "INVALID_PLAN", "Invalid release ID")
    forbidden = {rp.collision_key(name) for name in aliases} | {rp.collision_key(RESOURCE), "release/release-plan.properties"}
    known = {"release", "expected.count", "retired.count"}
    entries, expected, retired = [], {}, {}
    for group, limit in (("expected", 2000), ("retired", 5000)):
        number = properties.get(group + ".count", "")
        rp.require(number.isdigit() and (1 if group == "expected" else 0) <= int(number) <= limit, "INVALID_PLAN", "Invalid " + group + " count")
        result = expected if group == "expected" else retired
        for index in range(int(number)):
            stem = group + "." + str(index)
            known.update((stem + ".path", stem + ".sha256"))
            path = rp.safe_relative(properties.get(stem + ".path"))
            fingerprint = rp.sha(properties.get(stem + ".sha256"))
            rp.require(rp.collision_key(path) not in forbidden, "SELF_REFERENCE", "The embedded plan cannot hash/retire its own bootstrap aliases or plan: " + path)
            if group == "retired":
                rp.require(re.fullmatch(r"(?:mods|mods/1\.12\.2|plugins|plugins/update)/[^/]+\.jar", path) or
                           re.fullmatch(r"plugins/CrazyCrates/Crates/[^/]+\.ya?ml", path),
                           "INVALID_PLAN", "Unsafe retirement path: " + path)
            entries.append({"path": path, "kind": "file"})
            result[path] = fingerprint
    rp.check_names(entries)  # Includes expected/retired overlap and case aliases.
    rp.require(set(properties) == known, "INVALID_PLAN", "Missing/unexpected plan properties")
    return {"release": release, "expected": expected, "retired": retired}


def checked_report(path, expected):
    rp.sha(expected)
    report = rp.audit(path)
    rp.require(report["source_kind"] == "zip", "INVALID_INPUT", "Expected a JAR, not a directory")
    rp.require(report["archive"]["sha256"] == expected, "INPUT_MISMATCH", "Input JAR differs from its supplied expected hash: " + str(path))
    reject_signatures(report)
    return report


def package(input_jar, input_sha256, wrapper_jar, wrapper_sha256, plan_path, plan_sha256, output, aliases=()):
    input_jar, wrapper_jar, plan_path, output = map(rp.no_links, (input_jar, wrapper_jar, plan_path, output))
    receipt_path = output.with_name(output.name + ".receipt.json")
    rp.require(output not in (input_jar, wrapper_jar, plan_path) and receipt_path not in (input_jar, wrapper_jar, plan_path), "OUTPUT_SEPARATION", "Output must differ from every input")
    rp.require(not output.exists() and not receipt_path.exists(), "OUTPUT_CONFLICT", "Output JAR and receipt must both be new")
    rp.require(output.parent.is_dir() and output.suffix.lower() == ".jar", "OUTPUT_PARENT", "A new .jar in an existing output directory is required")
    aliases = list(aliases) + ["server.jar", input_jar.name, output.name]
    for name in aliases:
        rp.safe_relative(name, basename=True)
    before = checked_report(input_jar, input_sha256)
    owned = checked_report(wrapper_jar, wrapper_sha256)
    with rp.stable_reader(plan_path) as stream:
        raw_plan = stream.read(1024 * 1024 + 1)
    rp.require(raw_identity(raw_plan)["sha256"] == rp.sha(plan_sha256), "INPUT_MISMATCH", "Plan differs from its supplied expected hash")
    plan = validate_plan(raw_plan, aliases)
    source_files = {e["path"]: e for e in before["entries"] if e["kind"] == "file"}
    owned_files = {e["path"]: e for e in owned["entries"] if e["kind"] == "file"}
    rp.require(MANIFEST in source_files, "INVALID_MANIFEST", "Input JAR has no canonical manifest")
    rp.require(MAIN_CLASS_PATH in owned_files, "INVALID_WRAPPER", "Owned wrapper library is missing its main class")
    rp.require(all(OWN_CLASS.fullmatch(name) or name == MANIFEST for name in owned_files), "INVALID_WRAPPER", "Wrapper library includes non-owned payload; use the source-built bootstrap library only")
    rp.require(RESOURCE not in owned_files, "INVALID_WRAPPER", "Wrapper library must not supply an independent release plan")
    with rp.stable_reader(input_jar) as original_stream, zipfile.ZipFile(original_stream) as original, \
            rp.stable_reader(wrapper_jar) as wrapper_stream, zipfile.ZipFile(wrapper_stream) as wrapper:
        new_manifest, old_main = update_manifest(small_member(original, MANIFEST))
        if old_main == OWN_MAIN:
            rp.require(RESOURCE in source_files and MAIN_CLASS_PATH in source_files, "INVALID_INPUT", "Existing owned bootstrap lacks its class or plan")
        if RESOURCE in source_files:
            old_plan = parse_properties(small_member(original, RESOURCE))
            old_id = old_plan.get("release", "")
            rp.require(re.fullmatch(r"[A-Za-z0-9_-]{1,100}", old_id) and plan["release"] != old_id,
                       "REUSED_RELEASE_ID", "Repackaging an existing bootstrap requires a new release ID")
        if MANIFEST in owned_files:
            manifest_attributes(small_member(wrapper, MANIFEST))
        new_classes = {}
        for name in owned_files:
            if not OWN_CLASS.fullmatch(name):
                continue
            raw = small_member(wrapper, name, limit=8 * 1024**2)
            rp.require(len(raw) >= 8 and raw[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(raw[6:8], "big") == 52,
                       "INVALID_WRAPPER", "Owned class is not Java 8 bytecode: " + name)
            new_classes[name] = raw
        changed = {MANIFEST: new_manifest, RESOURCE: raw_plan, **new_classes}
        retained = [e for e in before["entries"] if not OWN_CLASS.fullmatch(e["path"]) and e["path"] not in (MANIFEST, RESOURCE)]
        target_entries = retained + [{"path": name, "kind": "file", **raw_identity(raw)} for name, raw in changed.items()]
        target_entries.sort(key=lambda e: e["path"])
        rp.check_names(target_entries)
        rp.require(len(target_entries) <= rp.MAX_ENTRIES, "LIMIT", "Output JAR has too many entries")
        target_bytes = sum(e.get("bytes", 0) for e in target_entries)
        rp.require(target_bytes <= rp.MAX_EXPANDED_BYTES, "LIMIT", "Output JAR exceeds expanded size limit")
        rp.ensure_space(output.parent, target_bytes + 8 * 1024**2)
        fd, temporary_name = tempfile.mkstemp(prefix="." + output.name + ".", suffix=".partial", dir=str(output.parent))
        temporary = Path(temporary_name)
        try:
            with os.fdopen(fd, "wb") as destination:
                with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as result:
                    for entry in target_entries:
                        name = entry["path"]
                        directory = entry["kind"] == "directory"
                        info = zipfile.ZipInfo(name + ("/" if directory else ""), (1980, 1, 1, 0, 0, 0))
                        info.create_system = 3
                        info.external_attr = ((stat.S_IFDIR | 0o755) if directory else (stat.S_IFREG | 0o644)) << 16
                        info.compress_type = zipfile.ZIP_DEFLATED
                        if directory:
                            result.writestr(info, b"")
                        elif name in changed:
                            result.writestr(info, changed[name])
                        else:
                            with original.open(name) as src, result.open(info, "w", force_zip64=True) as dst:
                                actual = rp.hash_stream(src, writer=dst, max_bytes=entry["bytes"])
                            rp.require(actual == {"bytes": entry["bytes"], "sha256": entry["sha256"]}, "SOURCE_CHANGED", "Upstream payload changed while copying: " + name)
                destination.flush()
                os.fsync(destination.fileno())
            verified = rp.audit(temporary)
            rp.require(verified["entries"] == target_entries, "WRITE_MISMATCH", "Output JAR differs from its complete planned payload")
            # Ensure neither source was replaced/edited after its initial audit.
            rp.require(rp.hash_file(input_jar)["sha256"] == input_sha256 and rp.hash_file(wrapper_jar)["sha256"] == wrapper_sha256 and
                       rp.hash_file(plan_path) == raw_identity(raw_plan), "SOURCE_CHANGED", "An input changed during packaging")
            verified["archive"]["filename"] = output.name
            receipt = {
                "schema": "vale.bootstrap-package/v1", "status": STATUS,
                "release": plan["release"], "input": before["archive"], "wrapper_library": owned["archive"],
                "plan": raw_identity(raw_plan), "archive": verified["archive"],
                "payload_inventory_sha256": verified["payload_inventory_sha256"],
                "zip_crc_check": verified["zip_crc_check"],
                "old_main_class": old_main, "new_main_class": OWN_MAIN,
                "replaced_or_added_paths": sorted(changed),
                "removed_previous_owned_classes": sorted(name for name in source_files if OWN_CLASS.fullmatch(name) and name not in new_classes),
                "preserved_upstream_files": sum(e["kind"] == "file" for e in retained),
                "upstream_preservation": "ALL_UNOWNED_MEMBER_PAYLOAD_HASHES_MATCH",
                "bootstrap_aliases_for_self_reference_check": sorted(set(aliases)),
                "server_file_pins": "STRUCTURE_CHECKED_NOT_RESOLVED_TO_SERVER_FILES",
                "runtime_acceptance": "NOT_RUN", "original_inputs": "TOOL_PERFORMED_NO_WRITES",
            }
            rp.promote_no_clobber(temporary, output, {"bytes": verified["archive"]["bytes"], "sha256": verified["archive"]["sha256"]})
            rp.atomic_json(receipt_path, receipt)
        finally:
            if temporary.exists():
                temporary.unlink()
    return {"status": STATUS, "output": str(output), "receipt": str(receipt_path),
            "sha256": verified["archive"]["sha256"], "runtime_acceptance": "NOT_RUN"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--input-sha256", required=True)
    parser.add_argument("--wrapper", type=Path, required=True)
    parser.add_argument("--wrapper-sha256", required=True)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--plan-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--bootstrap-alias", action="append", default=[], help="Additional root launcher filename; cannot be self-pinned")
    args = parser.parse_args(argv)
    try:
        result = package(args.input, args.input_sha256, args.wrapper, args.wrapper_sha256,
                         args.plan, args.plan_sha256, args.output, args.bootstrap_alias)
    except KeyboardInterrupt:
        print(rp.canonical({"status": "INTERRUPTED", "runtime_acceptance": "NOT_RUN"}).decode(), file=sys.stderr, end="")
        return 130
    except (rp.PackError, OSError, ValueError, KeyError, IndexError, TypeError, RuntimeError, zipfile.BadZipFile) as exc:
        print(rp.canonical({"status": "FAILED", "code": getattr(exc, "code", "IO_OR_JAR_ERROR"), "message": str(exc),
                            "note": "Originals were not modified; both output JAR and receipt are required for completed packaging."}).decode(), file=sys.stderr, end="")
        return 2
    print(rp.canonical(result).decode(), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
