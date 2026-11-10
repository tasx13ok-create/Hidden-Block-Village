#!/usr/bin/env python3
"""Prepare and assemble a reviewed, exact-byte connected-PC candidate.

Python 3.8+, standard library only. This joins bounded transfer parts, prepares
the matching client and launcher, then calls the existing strict assembler.
It never starts Minecraft, changes its inputs, or discovers an approved roster.
See PREPARE-CONNECTED.md for the required reviewed contract.
"""

import argparse
import hashlib
import os
from pathlib import Path
import re
import sys
import zipfile

import assemble_release as ar
import package_bootstrap as bp
import release_pack as rp


SCHEMA = "vale.connected-release/v1"
PART_LIMIT = 262144
MAX_TRANSFER = 512 * 1024**2
CONTRACT_FIELDS = (
    "schema", "candidate_id", "source_checkpoint", "source_inventory", "world",
    "source_server_mods", "source_plugins", "source_client", "transfer",
    "server_changes", "server_mods", "client_changes", "client_mods",
    "client_version", "client_forge", "bootstrap", "prior_bootstrap_plan", "required_configs",
)


def identity(value):
    return {"bytes": value["bytes"], "sha256": value["sha256"]}


def exact(actual, expected, label, code="PIN_MISMATCH"):
    rp.require(actual is not None and identity(actual) == identity(expected),
               code, "Missing or changed reviewed bytes: " + label)


def reference(directory, value):
    ar.fields(value, ("file", "bytes", "sha256"))
    rp.identity(value)
    path = rp.no_links(directory / rp.safe_relative(value["file"]))
    exact(rp.hash_file(path), value, str(path))
    return path


def checked_inventory(value):
    ar.fields(value, ("schema", "entries"))
    rp.require(value["schema"] == "vale.connected-source-inventory/v1",
               "INVALID_INVENTORY", "Expected a reviewed normalized source inventory")
    entries = value["entries"]
    rp.require(isinstance(entries, list) and 0 < len(entries) <= rp.MAX_ENTRIES,
               "INVALID_INVENTORY", "Invalid source inventory entry count")
    for entry in entries:
        rp.require(isinstance(entry, dict), "INVALID_INVENTORY", "Invalid inventory entry")
        kind = entry.get("kind")
        rp.require(kind in ("file", "directory"), "INVALID_INVENTORY", "Unknown inventory kind")
        ar.fields(entry, ("path", "kind", "bytes", "sha256") if kind == "file" else ("path", "kind"))
        rp.safe_relative(entry["path"])
        if kind == "file":
            rp.identity(entry)
    rp.check_names(entries)
    rp.require(entries == sorted(entries, key=lambda e: e["path"]),
               "INVALID_INVENTORY", "Reviewed inventory must be sorted by exact portable path")
    rp.require(sum(e.get("bytes", 0) for e in entries) <= rp.MAX_EXPANDED_BYTES,
               "LIMIT", "Source inventory exceeds 64 GiB")
    return entries


def read_reviewed_inventory(path):
    """Normalize a complete, hash-bound File Link v2 scan or normalized JSON."""
    with rp.stable_reader(path) as source:
        raw = source.read(rp.MAX_JSON_BYTES + 1)
    rp.require(len(raw) <= rp.MAX_JSON_BYTES, "LIMIT", "Reviewed inventory exceeds 64 MiB")
    raw_identity = {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    lines = raw.splitlines()
    rp.require(bool(lines), "INVALID_INVENTORY", "Empty reviewed inventory")
    if path.suffix.lower() != ".jsonl":
        return checked_inventory(rp.parse_json(raw)), raw_identity
    rows = [rp.parse_json(line) for line in lines]
    rp.require(len(rows) >= 3, "INVALID_INVENTORY", "Incomplete File Link scan")
    header, footer = rows[0], rows[-1]
    ar.fields(header, ("type", "version", "operation", "sourceRootId", "sourcePath",
                       "destinationRootId", "destinationPath", "hashedFiles"))
    ar.fields(footer, ("type", "files", "directories", "totalBytes", "complete"))
    rp.require(header["type"] == "header" and type(header["version"]) is int and header["version"] == 2 and
               header["operation"] == "scan_tree" and header["hashedFiles"] is True and
               footer["type"] == "footer" and footer["complete"] is True,
               "INVALID_INVENTORY", "File Link scan must be complete, version 2, and hash every file")
    for field in ("sourceRootId", "destinationRootId"):
        rp.text_field(header[field], field)
    if header["sourcePath"] != "":
        rp.safe_relative(header["sourcePath"])
    rp.safe_relative(header["destinationPath"])
    entries = []
    for row in rows[1:-1]:
        if row.get("type") == "file":
            ar.fields(row, ("type", "path", "size", "lastModified", "sha256"))
            rp.integer(row["lastModified"], "File Link lastModified")
            entries.append({"path": row["path"], "kind": "file", "bytes": row["size"], "sha256": row["sha256"]})
        else:
            ar.fields(row, ("type", "path"))
            rp.require(row["type"] == "directory", "INVALID_INVENTORY", "Unknown File Link row or omitted-file record")
            entries.append({"path": row["path"], "kind": "directory"})
    for field in ("files", "directories", "totalBytes"):
        rp.integer(footer[field], "File Link " + field)
    rp.require(footer["files"] == sum(e["kind"] == "file" for e in entries) and
               footer["directories"] == sum(e["kind"] == "directory" for e in entries) and
               footer["totalBytes"] == sum(e.get("bytes", 0) for e in entries),
               "INVALID_INVENTORY", "File Link footer counts do not match its complete rows")
    normalized = {"schema": "vale.connected-source-inventory/v1", "entries": sorted(entries, key=lambda e: e["path"])}
    return checked_inventory(normalized), raw_identity


def transfer_spec(path, checkpoint):
    value, raw_identity = rp.read_json(path)
    ar.fields(value, ("schema", "source_commit", "source_input_sha256", "part_max_bytes", "artifacts"))
    rp.require(value["schema"] == "vale.artifact-transfer/v1" and value["part_max_bytes"] == PART_LIMIT,
               "INVALID_TRANSFER", "Wrong bounded transfer schema or part limit")
    rp.require(isinstance(value["source_commit"], str) and re.fullmatch(r"[0-9a-f]{40}", value["source_commit"]),
               "INVALID_TRANSFER", "Transfer must identify its source Git commit")
    rp.sha(value["source_input_sha256"])
    rp.require(value["source_input_sha256"] == checkpoint["sha256"],
               "SOURCE_CHECKPOINT", "Transfer and reviewed source checkpoint differ")
    rows = value["artifacts"]
    rp.require(isinstance(rows, list) and 0 < len(rows) <= 100,
               "INVALID_TRANSFER", "Invalid artifact count")
    artifacts, parts, total = {}, [], 0
    for row in rows:
        ar.fields(row, ("module", "role", "filename", "bytes", "sha256", "parts"))
        rp.text_field(row["module"], "module")
        rp.require(row["role"] in ("runtime_mod", "bootstrap_library"),
                   "INVALID_TRANSFER", "Only reviewed runtime mods and the owned wrapper may be transferred")
        name = rp.safe_relative(row["filename"], basename=True)
        rp.require(name.endswith(".jar"), "INVALID_TRANSFER", "Artifact must be a JAR")
        rp.identity(row)
        rp.require(0 < row["bytes"] <= MAX_TRANSFER, "LIMIT", "Invalid artifact length")
        rp.require(isinstance(row["parts"], list) and 0 < len(row["parts"]) <= 2048,
                   "INVALID_TRANSFER", "Invalid part count")
        for part in row["parts"]:
            ar.fields(part, ("file", "bytes", "sha256"))
            rp.safe_relative(part["file"], basename=True)
            rp.identity(part)
            rp.require(0 < part["bytes"] <= PART_LIMIT, "LIMIT", "Part exceeds 256 KiB")
            parts.append({"path": part["file"], "kind": "file"})
        rp.require(sum(p["bytes"] for p in row["parts"]) == row["bytes"],
                   "INVALID_TRANSFER", "Part lengths do not equal the artifact length")
        rp.require(name not in artifacts, "PATH_COLLISION", "Duplicate artifact filename")
        artifacts[name] = row
        total += row["bytes"]
    rp.check_names([{"path": name, "kind": "file"} for name in artifacts])
    rp.check_names(parts)
    rp.require(total <= MAX_TRANSFER, "LIMIT", "Transfer exceeds 512 MiB")
    # Check all parts before creating any preparation output. Whole-file hashes
    # are separately checked while joining; part hashes alone do not certify it.
    for row in rows:
        for part in row["parts"]:
            exact(rp.hash_file(path.parent / part["file"]), part, part["file"], "TRANSFER_MISMATCH")
    return artifacts, raw_identity


def restore_artifacts(directory, manifest_path, artifacts):
    directory.mkdir()
    for name, row in sorted(artifacts.items()):
        output = directory / name
        partial = directory / (name + ".partial")
        aggregate = hashlib.sha256()
        size = 0
        with partial.open("xb") as destination:
            for part in row["parts"]:
                with rp.stable_reader(manifest_path.parent / part["file"]) as source:
                    actual = rp.hash_stream(source, writer=destination, aggregate=aggregate, max_bytes=part["bytes"])
                exact(actual, part, part["file"], "TRANSFER_MISMATCH")
                size += actual["bytes"]
            destination.flush()
            os.fsync(destination.fileno())
        expected = identity(row)
        exact({"bytes": size, "sha256": aggregate.hexdigest()}, row, name, "TRANSFER_MISMATCH")
        exact(rp.hash_file(partial), row, name, "TRANSFER_MISMATCH")
        rp.require(not output.exists(), "OUTPUT_CONFLICT", "Restored artifact already exists")
        rp.promote_no_clobber(partial, output, expected)
        if partial.exists():
            partial.unlink()


def mod_path(path, client):
    expression = r"overrides/mods/[^/]+\.jar" if client else r"mods/(?:1\.12\.2/)?[^/]+\.jar"
    rp.require(isinstance(path, str) and re.fullmatch(expression, path),
               "CHANGE_SCOPE", "Replacement is outside the reviewed mod locations: " + str(path))
    rp.safe_relative(path)


def changes_from_contract(rows, original, artifacts, client=False):
    rp.require(isinstance(rows, list), "INVALID_CONTRACT", "Changes must be an array")
    result, used = [], set()
    for row in rows:
        ar.fields(row, ("old", "new"))
        old, new = row["old"], row["new"]
        rp.require(old is not None or new is not None, "INVALID_CONTRACT", "Empty replacement")
        if old is not None:
            mod_path(old, client)
            rp.require(old in original, "PIN_MISMATCH", "Selected old mod is absent: " + old)
            old = {"path": old, **identity(original[old])}
        if new is not None:
            ar.fields(new, ("path", "artifact"))
            mod_path(new["path"], client)
            name = rp.safe_relative(new["artifact"], basename=True)
            rp.require(name in artifacts and artifacts[name]["role"] == "runtime_mod",
                       "INVALID_TRANSFER", "Replacement must select a locked production artifact")
            used.add(name)
            new = {"path": new["path"], "file": "artifacts/" + name, **identity(artifacts[name])}
        result.append({"old": old, "new": new})
    return result, used


def check_plugins(original, roster):
    rp.require(isinstance(roster, list), "INVALID_CONTRACT", "Plugin roster must be an array")
    expected = set()
    for row in roster:
        ar.file_identity(row)
        path = row["path"]
        rp.require(re.fullmatch(r"plugins/(?:update/)?[^/]+\.jar", path),
                   "CHANGE_SCOPE", "Invalid plugin roster path")
        rp.require(path not in expected, "PATH_COLLISION", "Duplicate plugin roster entry")
        expected.add(path)
        exact(original.get(path), row, path)
    actual = {p for p in original if re.fullmatch(r"plugins/(?:update/)?[^/]+\.jar", p, re.I)}
    rp.require(actual == expected, "PLUGIN_ROSTER", "Missing or additional active plugin JARs")


def check_result_roster(original, replacements, roster, source, preparation, prefix):
    predicted = dict(original)
    locations = {path: source / path for path in original}
    for change in replacements:
        if change["old"] is not None:
            predicted.pop(change["old"]["path"], None)
    for change in replacements:
        if change["new"] is not None:
            row = change["new"]
            predicted[row["path"]] = {"path": row["path"], "kind": "file", **identity(row)}
            locations[row["path"]] = preparation / row["file"]
    rp.require(isinstance(roster, list) and roster, "INVALID_CONTRACT", "A complete final roster is required")
    named, ids = set(), {}
    for row in roster:
        ar.fields(row, ("path", "bytes", "sha256", "mods"))
        mod_path(row["path"], prefix == "overrides/mods")
        rp.identity(row)
        rp.require(row["path"] not in named, "DUPLICATE_MOD", "Duplicate final roster path")
        named.add(row["path"])
        exact(predicted.get(row["path"]), row, row["path"])
        actual = ar.jar_mods(locations[row["path"]])
        rp.require(isinstance(row["mods"], dict) and actual == row["mods"],
                   "MOD_IDENTITY", "Final mod IDs/versions differ: " + row["path"])
        for modid, version in actual.items():
            rp.require(modid not in ids, "DUPLICATE_MOD", "Duplicate final mod ID: " + modid)
            ids[modid] = {"version": version, **identity(row)}
    active = {p for p in predicted if ar.under(p, prefix) and p.lower().endswith(".jar")}
    rp.require(active == named, "MOD_ROSTER", "Final roster omits or adds active mod JARs")
    return predicted, ids


def prepare(source, contract_path, contract_sha256, export_root, launcher=None):
    source, contract_path, export_root = map(rp.no_links, (source, contract_path, export_root))
    contract, contract_identity = rp.read_json(contract_path)
    rp.require(contract_identity["sha256"] == rp.sha(contract_sha256),
               "CONTRACT_MISMATCH", "Contract differs from its independently supplied reviewed SHA-256")
    ar.fields(contract, CONTRACT_FIELDS)
    rp.require(contract["schema"] == SCHEMA, "INVALID_CONTRACT", "Wrong connected-release contract schema")
    candidate_id = rp.safe_relative(contract["candidate_id"], basename=True)
    rp.require(re.fullmatch(r"[A-Za-z0-9_-]{1,100}", candidate_id), "INVALID_CONTRACT", "Invalid candidate ID")
    rp.checkpoint(contract["source_checkpoint"])
    rp.require(source.is_dir(), "INVALID_INPUT", "A complete frozen server directory is required")
    rp.require(export_root.name == "Vale-Export", "OUTPUT_SEPARATION", "Output parent must be named Vale-Export")
    rp.require(source != export_root and source not in export_root.parents,
               "OUTPUT_SEPARATION", "Output parent must be outside the complete source")
    preparation = export_root / (candidate_id + "-preparation")
    candidate = export_root / candidate_id
    for destination in (preparation, candidate):
        rp.require(not destination.exists(), "OUTPUT_CONFLICT", "Existing outputs are never resumed: " + str(destination))
        rp.require(destination != contract_path and destination not in contract_path.parents,
                   "OUTPUT_SEPARATION", "Contract cannot live inside a new output")
    inventory_ref = contract["source_inventory"]
    ar.fields(inventory_ref, ("file", "bytes", "sha256", "inventory_sha256", "payload_inventory_sha256"))
    inventory_path = reference(contract_path.parent, {k: inventory_ref[k] for k in ("file", "bytes", "sha256")})
    expected_entries, inventory_identity = read_reviewed_inventory(inventory_path)
    exact(inventory_identity, inventory_ref, "reviewed inventory read")
    rp.require(rp.digest(expected_entries) == rp.sha(inventory_ref["inventory_sha256"]) and
               rp.digest([e for e in expected_entries if e["kind"] == "file"]) == rp.sha(inventory_ref["payload_inventory_sha256"]),
               "INVALID_INVENTORY", "Reviewed inventory digests disagree with its exact entries")
    print("Checking the complete source against the reviewed inventory.", flush=True)
    before = rp.audit(source)
    rp.require(before["entries"] == expected_entries, "SOURCE_INVENTORY_MISMATCH",
               "Source differs from the fixed reviewed inventory; no changed source is automatically approved")
    ar.validate_world(before["entries"], contract["world"])
    ar.check_world_selection(source / "server.properties", "Naruto.test")
    original = ar.files(before)
    ar.check_roster(source, original, contract["source_server_mods"], "mods")
    check_plugins(original, contract["source_plugins"])
    client = contract["source_client"]
    ar.fields(client, ("path", "bytes", "sha256", "mods", "manifest"))
    rp.require(client["path"] == "client-install/Client.zip", "INVALID_CONTRACT", "Expected the complete nested client")
    rp.identity(client)
    exact(original.get(client["path"]), client, client["path"])
    ar.fields(client["manifest"], ("bytes", "sha256"))
    rp.identity(client["manifest"])
    client_report = rp.audit(source / client["path"])
    client_original = ar.files(client_report)
    exact(client_original.get("manifest.json"), client["manifest"], "original client manifest")
    transfer_path = reference(contract_path.parent, contract["transfer"])
    artifacts, transfer_identity = transfer_spec(transfer_path, contract["source_checkpoint"])
    exact(transfer_identity, contract["transfer"], "reviewed transfer manifest read")
    bootstrap = contract["bootstrap"]
    ar.fields(bootstrap, ("aliases", "wrapper_artifact", "input"))
    aliases = bootstrap["aliases"]
    rp.require(isinstance(aliases, list) and len(aliases) == 2 and "server.jar" in aliases,
               "INVALID_CONTRACT", "Exactly two root launcher aliases including server.jar are required")
    rp.check_names([{"path": rp.safe_relative(p, basename=True), "kind": "file"} for p in aliases])
    for path in aliases:
        rp.require(path.endswith(".jar") and path in original, "PIN_MISMATCH", "Reviewed source launcher is missing")
    exact(original[aliases[0]], original[aliases[1]], "original launcher aliases")
    prior_ref = contract["prior_bootstrap_plan"]
    ar.file_identity(prior_ref)
    rp.require(prior_ref["path"] == "release/release-plan.properties",
               "CHANGE_SCOPE", "The prior plan must be the reviewed source release/release-plan.properties")
    exact(original.get(prior_ref["path"]), prior_ref, "prior source bootstrap plan")
    prior_raw = ar.read_small(source / prior_ref["path"], limit=1024 * 1024)
    exact(bp.raw_identity(prior_raw), prior_ref, "prior bootstrap plan read")
    prior = bp.validate_plan(prior_raw, aliases)
    rp.require(prior["release"] != candidate_id, "REUSED_RELEASE_ID", "Use a new candidate ID, not the prior deployment ID")
    with rp.stable_reader(source / aliases[0]) as stream, zipfile.ZipFile(stream) as jar:
        rp.require(jar.namelist().count(bp.RESOURCE) == 1, "BOOTSTRAP_PLAN", "Source wrapper must contain exactly one reviewed plan")
        rp.require(bp.small_member(jar, bp.RESOURCE) == prior_raw, "BOOTSTRAP_PLAN", "Source wrapper resource differs from the reviewed prior plan")
    for path, fingerprint in prior["expected"].items():
        rp.require(path in original and original[path]["sha256"] == fingerprint,
                   "PRIOR_PIN_MISMATCH", "Prior bootstrap expects missing or changed source file: " + path)
    for path in prior["retired"]:
        rp.require(path not in original, "RETIRED_PRESENT", "A historical retired runtime/crate file is still in the selected source: " + path)
    wrapper = rp.safe_relative(bootstrap["wrapper_artifact"], basename=True)
    rp.require(wrapper in artifacts and artifacts[wrapper]["role"] == "bootstrap_library",
               "INVALID_TRANSFER", "Bootstrap must select its exact owned wrapper library")
    launcher_ref = bootstrap["input"]
    ar.fields(launcher_ref, ("kind", "bytes", "sha256"), ("path",))
    rp.identity(launcher_ref)
    if launcher_ref["kind"] == "source":
        rp.require(launcher is None and launcher_ref.get("path") in aliases,
                   "INVALID_CONTRACT", "Source launcher must be a reviewed alias; do not supply an external launcher")
        launcher_path = source / launcher_ref["path"]
    else:
        rp.require(launcher_ref["kind"] == "external" and "path" not in launcher_ref and launcher is not None,
                   "INVALID_CONTRACT", "External launcher requires explicit --launcher and exact reviewed identity")
        launcher_path = rp.no_links(launcher)
    exact(rp.hash_file(launcher_path), launcher_ref, "bootstrap input")
    server_changes, used_server = changes_from_contract(contract["server_changes"], original, artifacts)
    client_changes, used_client = changes_from_contract(contract["client_changes"], client_original, artifacts, client=True)
    rp.require(set(artifacts) == used_server | used_client | {wrapper},
               "INVALID_TRANSFER", "Every transferred artifact must be explicitly selected by the reviewed contract")
    configs = contract["required_configs"]
    rp.require(isinstance(configs, list) and len(configs) == len(set(configs)),
               "INVALID_CONTRACT", "Required configs must be unique paths")
    for path in configs:
        rp.safe_relative(path)
        rp.require(path in original and (path.startswith("config/") or re.fullmatch(r"plugins/CrazyCrates/(?:config\.yml|Crates/[^/]+\.ya?ml)", path)),
                   "CHANGE_SCOPE", "Required configuration is missing or outside the reviewed scope: " + path)
    if "plugins/CrazyCrates/config.yml" in original:
        rp.require("plugins/CrazyCrates/config.yml" in configs, "CONFIG_PIN", "CrazyCrates config must be explicitly required")
    for suffix in (".done", ".pending"):
        rp.require("vale-update-state/" + candidate_id + suffix not in original,
                   "BOOTSTRAP_STATE", "Use a fresh candidate ID without an existing state marker")
    rp.text_field(contract["client_version"], "client version")
    rp.text_field(contract["client_forge"], "client Forge version")
    export_root.mkdir(parents=True, exist_ok=True)
    rp.no_links(export_root)
    rp.ensure_space(export_root, 3 * before["total_file_bytes"] + 4 * client["bytes"] + 2 * sum(a["bytes"] for a in artifacts.values()) + 128 * 1024**2)
    preparation.mkdir()
    rp.atomic_json(preparation / "INCOMPLETE.json", {"status": "INCOMPLETE", "candidate_id": candidate_id})
    print("Restoring exact transfer artifacts and preparing the matching client.", flush=True)
    restore_artifacts(preparation / "artifacts", transfer_path, artifacts)
    ar.copy_checked(contract_path, preparation / "reviewed-contract.json", contract_identity)
    ar.copy_checked(inventory_path, preparation / ("reviewed-source-inventory" + inventory_path.suffix.lower()), identity(inventory_ref))
    ar.copy_checked(transfer_path, preparation / "artifact-transfer.json", transfer_identity)
    ar.validate_changes(server_changes, original, preparation, lambda p: bool(re.fullmatch(r"mods/(?:1\.12\.2/)?[^/]+\.jar", p)))
    predicted, server_ids = check_result_roster(original, server_changes, contract["server_mods"], source, preparation, "mods")
    preview = preparation / "client-preview"
    ar.copy_input(source / client["path"], client_report, preview)
    ar.check_roster(preview, client_original, client["mods"], "overrides/mods")
    manifest = rp.parse_json(ar.read_small(preview / "manifest.json"))
    rp.require(manifest.get("minecraft", {}).get("version") == "1.12.2" and
               manifest.get("minecraft", {}).get("modLoaders") == [{"id": "forge-" + contract["client_forge"], "primary": True}] and
               manifest.get("overrides") == "overrides" and manifest.get("files") == [],
               "CLIENT_INCOMPLETE", "Original client must already be the reviewed complete Minecraft/Forge client")
    manifest["version"] = contract["client_version"]
    manifest_identity = rp.atomic_json(preparation / "client-manifest.json", manifest)
    client_changes.append({"old": {"path": "manifest.json", **identity(client["manifest"])},
                           "new": {"path": "manifest.json", "file": "client-manifest.json", **manifest_identity}})
    ar.validate_changes(client_changes, client_original, preparation,
                        lambda p: p == "manifest.json" or bool(re.fullmatch(r"overrides/mods/[^/]+\.jar", p)))
    ar.retire(preview, ar.CLIENT_EVIDENCE, preparation / "prior-client-evidence")
    ar.apply_changes(preview, client_changes, preparation)
    client_final = rp.audit(preview)
    client_ids = ar.check_roster(preview, ar.files(client_final), contract["client_mods"], "overrides/mods")
    rp.require({"valeorders", "valeencounters"}.issubset(server_ids) and {"valeorders", "valeencounters"}.issubset(client_ids),
               "CLIENT_MISMATCH", "Core and Encounters must exist on both sides")
    for modid in set(server_ids) & set(client_ids):
        exact(server_ids[modid], client_ids[modid], "shared mod " + modid, "CLIENT_MISMATCH")
        rp.require(server_ids[modid]["version"] == client_ids[modid]["version"], "CLIENT_MISMATCH", "Shared versions differ")
    client_note = {"schema": "vale.client-candidate/v1", "status": ar.STATUS, "candidate_id": candidate_id,
                   "mods": contract["client_mods"], "source_checkpoint": contract["source_checkpoint"], "runtime_acceptance": "NOT_RUN"}
    rp.atomic_json(preview / "CANDIDATE-ASSEMBLY.json", client_note)
    prepared_client = preparation / "Client-Prepared.zip"
    ar.write_zip(preview, rp.audit(preview), prepared_client)
    # Keep the previously reviewed pin coverage. Explicit replacements can
    # change or retire those files, but unrelated prior pins must not disappear.
    expected = {p: predicted[p]["sha256"] for p in prior["expected"] if p in predicted}
    expected.update({p: predicted[p]["sha256"] for p in configs + [row["path"] for row in contract["server_mods"]] +
                     [row["path"] for row in contract["source_plugins"]] + ["server.properties"]})
    expected[client["path"]] = rp.hash_file(prepared_client)["sha256"]
    retired = dict(prior["retired"])
    retired.update({c["old"]["path"]: c["old"]["sha256"] for c in server_changes
                    if c["old"] is not None and (c["new"] is None or c["old"]["path"] != c["new"]["path"])})
    properties = {"release": candidate_id, "expected.count": str(len(expected)), "retired.count": str(len(retired))}
    for group, rows in (("expected", expected), ("retired", retired)):
        for index, path in enumerate(sorted(rows)):
            properties[group + ".%d.path" % index] = path
            properties[group + ".%d.sha256" % index] = rows[path]
    raw_properties = ("\n".join(key + "=" + properties[key] for key in sorted(properties)) + "\n").encode("utf-8")
    bp.validate_plan(raw_properties, aliases)
    properties_path = preparation / "vale-release.properties"
    with properties_path.open("xb") as output:
        output.write(raw_properties)
    properties_identity = rp.hash_file(properties_path)
    packaged = preparation / "candidate-bootstrap.jar"
    bp.package(launcher_path, launcher_ref["sha256"], preparation / "artifacts" / wrapper,
               artifacts[wrapper]["sha256"], properties_path, properties_identity["sha256"], packaged, aliases)
    packaged_identity = rp.hash_file(packaged)
    for path in aliases:
        server_changes.append({"old": {"path": path, **identity(original[path])},
                               "new": {"path": path, "file": packaged.name, **packaged_identity}})
    plan = {"schema": "vale.candidate-plan/v1", "candidate_id": candidate_id,
            "source_checkpoint": contract["source_checkpoint"], "input_payload_inventory_sha256": before["payload_inventory_sha256"],
            "world": contract["world"], "replacements": server_changes, "server_mods": contract["server_mods"],
            "client": {"path": client["path"], "input_sha256": client["sha256"], "replacements": client_changes,
                       "mods": contract["client_mods"], "version": contract["client_version"], "forge": contract["client_forge"]},
            "bootstrap": {"paths": aliases, "resource_sha256": properties_identity["sha256"]}, "required_configs": configs}
    plan_path = preparation / "candidate-plan.json"
    plan_identity = rp.atomic_json(plan_path, plan)
    print("Assembling the complete new server and matching client; runtime acceptance remains pending.", flush=True)
    result = ar.assemble(source, plan_path, export_root)
    exact(rp.hash_file(candidate / "Client-Candidate.zip"), rp.hash_file(prepared_client), "prepared and assembled clients")
    exact(rp.hash_file(contract_path), contract_identity, "reviewed contract after assembly")
    exact(rp.hash_file(inventory_path), inventory_ref, "reviewed inventory after assembly")
    exact(rp.hash_file(transfer_path), transfer_identity, "transfer manifest after assembly")
    receipt = {"schema": "vale.connected-preparation/v1", "status": ar.STATUS, "candidate_id": candidate_id,
               "contract_sha256": contract_identity["sha256"], "plan_sha256": plan_identity["sha256"],
               "prior_bootstrap_plan_sha256": prior_ref["sha256"], "prior_retirements_retained": len(prior["retired"]),
               "source_checkpoint": contract["source_checkpoint"], "source_inventory_sha256": before["inventory_sha256"],
               "source_payload_inventory_sha256": before["payload_inventory_sha256"],
               "artifact_count": len(artifacts), "part_count": sum(len(a["parts"]) for a in artifacts.values()),
               "original_source": "TOOL_PERFORMED_NO_WRITES", "minecraft_launched": False,
               "runtime_acceptance": "NOT_RUN", "assembly": result}
    rp.atomic_json(preparation / "PREPARATION.json", receipt)
    (preparation / "INCOMPLETE.json").unlink()
    return {**result, "preparation": str(preparation), "plan": str(plan_path)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Complete frozen server directory; never changed")
    parser.add_argument("--contract", type=Path, required=True, help="Independently reviewed exact-input contract")
    parser.add_argument("--contract-sha256", required=True, help="Exact reviewed contract SHA-256; never inferred")
    parser.add_argument("--export-root", type=Path, required=True, help="Output parent named Vale-Export")
    parser.add_argument("--launcher", type=Path, help="Only for a contract selecting a separately pinned external base launcher")
    args = parser.parse_args(argv)
    try:
        result = prepare(args.source, args.contract, args.contract_sha256, args.export_root, args.launcher)
    except KeyboardInterrupt:
        print(rp.canonical({"status": "INTERRUPTED", "runtime_acceptance": "NOT_RUN", "note": "Keep incomplete output for inspection; use a new reviewed candidate ID."}).decode(), file=sys.stderr)
        return 130
    except (rp.PackError, OSError, ValueError, KeyError, IndexError, TypeError, RecursionError, zipfile.BadZipFile, RuntimeError) as exc:
        print(rp.canonical({"status": "FAILED", "code": getattr(exc, "code", "IO_OR_CONTRACT_ERROR"), "message": str(exc),
                            "runtime_acceptance": "NOT_RUN", "note": "Original inputs were not changed. Existing or incomplete outputs are never resumed."}).decode(), file=sys.stderr)
        return 2
    print(rp.canonical(result).decode(), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
