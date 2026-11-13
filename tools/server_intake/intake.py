"""Recover a server ZIP into a new private tree and preserve conflicting entries.

Standard library only. This program never starts Java/server/plugins/scripts and
never edits an input or existing destination. The receipt is PRIVATE: it contains
all archived names, including world/player/config filenames.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import stat
import sys
import time
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_pack as pack


class IntakeError(ValueError):
    pass


def require(ok, message):
    if not ok:
        raise IntakeError(message)


def preflight(infos, max_entries=100000, max_bytes=64 * 1024**3):
    require(len(infos) <= max_entries, "Archive entry limit exceeded")
    require(sum(row.file_size for row in infos) <= max_bytes, "Archive expanded-size limit exceeded")
    rows, prefixes, materialized = [], {}, {}
    for index, info in enumerate(infos):
        original = info.filename[:-1] if info.is_dir() else info.filename
        try:
            pack.safe_relative(original)
            raw_original = info.orig_filename[:-1] if info.is_dir() else info.orig_filename
            pack.safe_relative(raw_original)
        except pack.PackError as error:
            raise IntakeError(str(error)) from error
        require(not info.is_dir() or info.file_size == 0, "Directory entries containing payload bytes are unsupported")
        require(not info.flag_bits & 1, "Encrypted archive entry is unsupported")
        mode = info.external_attr >> 16
        require(not stat.S_ISLNK(mode), "Archive symlinks are refused")
        require(stat.S_IFMT(mode) in (0, stat.S_IFREG, stat.S_IFDIR), "Special archive file is refused")
        canonical_parts, aliases = [], []
        for component in original.split("/"):
            tentative = "/".join(canonical_parts + [component])
            key = pack.collision_key(tentative)
            canonical = prefixes.setdefault(key, tentative)
            if canonical != tentative:
                aliases.append({"original_prefix": tentative, "canonical_prefix": canonical})
            canonical_parts = canonical.split("/")
        canonical = "/".join(canonical_parts)
        key = pack.collision_key(canonical)
        parts = canonical.split("/")
        ancestor_file = any(materialized.get(pack.collision_key("/".join(parts[:n]))) == "file"
                            for n in range(1, len(parts)))
        prior_kind = materialized.get(key)
        conflict = ancestor_file or prior_kind == "file" or (prior_kind == "directory" and not info.is_dir())
        # Parent directories already needed by an earlier materialized file count
        # as directories even when the ZIP has no explicit directory entries.
        if conflict:
            destination = "quarantine/%06d/" % index + ("directory-entry" if info.is_dir() else parts[-1])
            classification = "directory_conflict" if info.is_dir() else "payload_conflict"
        else:
            destination = "server/" + canonical
            classification = "directory" if info.is_dir() else "primary"
            for n in range(1, len(parts)):
                materialized.setdefault(pack.collision_key("/".join(parts[:n])), "directory")
            materialized[key] = "directory" if info.is_dir() else "file"
        rows.append({"archive_index": index, "path": original, "canonical_path": canonical,
                     "destination": destination, "kind": "directory" if info.is_dir() else "file",
                     "classification": classification, "prefix_aliases": aliases,
                     "bytes": info.file_size, "compressed_bytes": info.compress_size,
                     "zip_crc32": "%08x" % info.CRC})
    return rows


def collision_report(rows):
    groups = {}
    for row in rows:
        groups.setdefault(pack.collision_key(row["canonical_path"]), []).append(row)
    reports = []
    for key, members in groups.items():
        files = [row for row in members if row["kind"] == "file"]
        kinds = {row["kind"] for row in members}
        if len(files) > 1 or len(kinds) > 1:
            digests = {row.get("sha256") for row in files}
            reports.append({"canonical_key": key,
                            "entries": [dict({k: row[k] for k in ("archive_index", "path", "destination", "kind", "bytes")},
                                             **({"sha256": row["sha256"]} if "sha256" in row else {})) for row in members],
                            "content_equivalent": len(files) > 1 and len(digests) == 1 and None not in digests,
                            "review_required": len(kinds) > 1 or len(digests) != 1 or None in digests})
    # An ancestor-file conflict can have a different full key. Preserve it as an
    # explicit unresolved mapping rather than pretending the tree is runnable.
    mapped = {entry["archive_index"] for group in reports for entry in group["entries"]}
    for row in rows:
        if row["classification"].endswith("conflict") and row["archive_index"] not in mapped:
            reports.append({"canonical_key": pack.collision_key(row["canonical_path"]),
                            "entries": [{k: row[k] for k in ("archive_index", "path", "destination", "kind", "bytes")}],
                            "content_equivalent": False, "review_required": True})
    return reports


def save(path, value):
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")


def recover(source, destination, progress=None, max_entries=100000, max_bytes=64 * 1024**3):
    source, destination = pack.no_links(source), pack.no_links(destination)
    require(source.is_file(), "Source must be a regular ZIP file")
    require(not destination.exists(), "Destination must be new; preserved recovery is never overwritten")
    started = time.monotonic()
    report = {"schema": 1, "scope": "PRIVATE_SERVER_ARCHIVE_RECOVERY", "status": "IN_PROGRESS",
              "source_archive": str(source), "destination": str(destination),
              "started_utc": datetime.now(timezone.utc).isoformat(), "server_executed": False,
              "world_started": False, "production_merged": False, "rows": []}
    made = False
    try:
        with pack.stable_reader(source) as raw:
            with zipfile.ZipFile(raw) as archive:
                infos = archive.infolist()
                rows = preflight(infos, max_entries, max_bytes)
                total = sum(row["bytes"] for row in rows)
                require(shutil.disk_usage(destination.parent if destination.parent.exists() else source.parent).free
                        > total + 1024**2, "Insufficient free space for a preserved recovery")
                # Hash the actual open archive handle; seek/extract the same handle.
                raw.seek(0)
                def hash_progress(count):
                    if progress:
                        progress({"phase": "hash_archive", "delta_bytes": count})
                identity = pack.hash_stream(raw, progress=hash_progress)
                report["archive_bytes"], report["archive_sha256"] = identity["bytes"], identity["sha256"]
                destination.mkdir(parents=True, exist_ok=False)
                made = True
                report["rows"] = rows
                for info, row in zip(infos, rows):
                    output = pack.no_links(destination.joinpath(*row["destination"].split("/")))
                    if row["kind"] == "directory":
                        output.mkdir(parents=True, exist_ok=True)
                        row["crc_verified"] = True
                        continue
                    output.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(info, "r") as reader, output.open("xb") as writer:
                        identity = pack.hash_stream(reader, writer=writer, max_bytes=info.file_size)
                    require(identity["bytes"] == info.file_size, "Declared ZIP member size differs")
                    # ZipExtFile verifies CRC at EOF. Verify disk bytes separately.
                    require(pack.hash_file(output) == identity, "Recovered disk bytes differ from the archive stream")
                    row.update(identity, crc_verified=True, output_readback_verified=True)
                    if progress:
                        progress({"phase": "extract_and_verify", "entry": row["archive_index"], "entries": len(rows),
                                  "bytes": row["bytes"]})
            # The stable_reader context verifies source inode/size/timestamps after
            # archive hashing, extraction and all output readbacks have finished.
        report["collisions"] = collision_report(report["rows"])
        report["prefix_aliases"] = [alias for row in report["rows"] for alias in row["prefix_aliases"]]
        report["manifest_sha256"] = pack.digest(report["rows"])
        report["entries"] = len(report["rows"])
        report["file_entries"] = sum(row["kind"] == "file" for row in report["rows"])
        report["uncompressed_bytes"] = sum(row["bytes"] for row in report["rows"])
        report["crc_verified"] = all(row.get("crc_verified") for row in report["rows"])
        report["output_readback_verified"] = all(row.get("output_readback_verified") for row in report["rows"] if row["kind"] == "file")
        report["distinct_payloads_preserved"] = True
        report["source_unmodified"] = True
        report["unresolved_collision_groups"] = sum(group["review_required"] for group in report["collisions"])
        report["status"] = "RECOVERED_REVIEW_REQUIRED" if report["unresolved_collision_groups"] or report["prefix_aliases"] else "RECOVERED_NOT_STARTED"
    except BaseException as error:
        report["status"] = "FAILED_PRESERVED"
        report["error_type"] = type(error).__name__
        report["error"] = str(error)
        if made:
            report["finished_utc"] = datetime.now(timezone.utc).isoformat()
            save(destination / "PRIVATE-SERVER-INTAKE.json", report)
        raise
    report["elapsed_seconds"] = round(time.monotonic() - started, 3)
    report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    save(destination / "PRIVATE-SERVER-INTAKE.json", report)
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args(argv)
    last = [0.0]
    total_hash = [0]
    def progress(event):
        if event["phase"] == "hash_archive":
            total_hash[0] += event["delta_bytes"]
        now = time.monotonic()
        if now - last[0] >= 5:
            last[0] = now
            print(json.dumps({"phase": event["phase"], "archive_hash_bytes": total_hash[0]} if event["phase"] == "hash_archive"
                             else {key: event[key] for key in ("phase", "entry", "entries")}), flush=True)
    report = recover(args.archive, args.destination, progress=progress)
    print(json.dumps({key: report[key] for key in ("status", "archive_bytes", "archive_sha256", "entries",
                                                  "unresolved_collision_groups", "crc_verified", "output_readback_verified", "elapsed_seconds")}, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
