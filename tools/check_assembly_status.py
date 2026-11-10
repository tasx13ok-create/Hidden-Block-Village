#!/usr/bin/env python3
"""Observe the current Vale Windows assembly without interrupting it.

Python 3.8+, standard library only. Run beside connected-contract.json in
Vale-Export/vale-038, in a second window. Only a new status report is written.
The report is an observation, not package verification or runtime acceptance.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
import uuid


CANDIDATE_ID = "vale-038-standards031-connected1"
CONTRACT_SHA256 = "6407151f0a80e45be999eace44afdb9b392e84c1b0edf01e3aeb9f48035c537e"
SOURCE = Path(r"D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage")
EXPORT_ROOT = Path(r"D:\Codex\Vale-Export")
SAMPLE_SECONDS = 5
QUERY_TIMEOUT_SECONDS = 12
MAX_JSON_BYTES = 128 * 1024
COUNTERS = ("kernel_100ns", "user_100ns", "read_bytes", "write_bytes")

# Command lines stay inside the local query. Only exact tool paths belonging to
# this handoff are selected, and no raw command line is returned or saved.
POWERSHELL_QUERY = r"""
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$handoff = __HANDOFF_LITERAL__
try {
    $patterns = @{}
    foreach ($tool in @('prepare_connected_release', 'assemble_release')) {
        $toolPath = Join-Path $handoff ('tools\' + $tool + '.py')
        $patterns[$tool] = '(?i)(?:^|[\s"])' + [regex]::Escape($toolPath) + '(?=$|[\s"])'
    }
    $rows = @()
    $unreadable = 0
    $processes = @(Get-CimInstance -ClassName Win32_Process -Filter "Name LIKE 'python%.exe' OR Name = 'py.exe'" -ErrorAction Stop)
    foreach ($process in $processes) {
        $line = [string]$process.CommandLine
        if ([string]::IsNullOrWhiteSpace($line)) {
            $unreadable++
            continue
        }
        $line = $line.Replace('/', '\')
        $role = $null
        foreach ($tool in @('prepare_connected_release', 'assemble_release')) {
            if ([regex]::IsMatch($line, $patterns[$tool])) { $role = $tool; break }
        }
        if ($null -eq $role) { continue }
        $created = $null
        if ($null -ne $process.CreationDate) {
            $created = $process.CreationDate.ToUniversalTime().ToString('o')
        }
        $rows += [pscustomobject]@{
            pid = [int]$process.ProcessId
            role = $role
            started_utc = $created
            kernel_100ns = [string]$process.KernelModeTime
            user_100ns = [string]$process.UserModeTime
            read_bytes = [string]$process.ReadTransferCount
            write_bytes = [string]$process.WriteTransferCount
        }
    }
    [pscustomobject]@{status = 'ok'; unreadable_python_command_lines = $unreadable; processes = @($rows)} | ConvertTo-Json -Depth 4 -Compress
} catch {
    [pscustomobject]@{status = 'unavailable'; reason = 'cim_query_failed'; processes = @()} | ConvertTo-Json -Depth 3 -Compress
    exit 2
}
"""


def utc_now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def ps_literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def unsigned(value):
    if type(value) is int and 0 <= value <= 2**64 - 1:
        return value
    if isinstance(value, str) and re.fullmatch(r"[0-9]{1,20}", value):
        number = int(value)
        if number <= 2**64 - 1:
            return number
    return None


def parse_sample(raw):
    """Keep a small explicit field set; discard unexpected query output."""
    value = json.loads(raw)
    if not isinstance(value, dict) or value.get("status") != "ok":
        return {"status": "unavailable", "reason": "cim_query_failed", "processes": []}
    rows = value.get("processes")
    unreadable = unsigned(value.get("unreadable_python_command_lines"))
    if not isinstance(rows, list) or len(rows) > 1000 or unreadable is None:
        raise ValueError("Invalid process response")
    result = []
    seen = set()
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("Invalid process row")
        pid = unsigned(row.get("pid"))
        created = row.get("started_utc")
        role = row.get("role")
        if pid is None or not 0 < pid < 2**32 or pid in seen or role not in ("prepare_connected_release", "assemble_release"):
            raise ValueError("Invalid process identity")
        if created is not None and not (isinstance(created, str) and re.fullmatch(r"\d{4}-\d\d-\d\dT[0-9:.]+(?:Z|\+00:00)", created)):
            raise ValueError("Invalid process creation time")
        seen.add(pid)
        result.append({"pid": pid, "role": role, "started_utc": created,
                       **{key: unsigned(row.get(key)) for key in COUNTERS}})
    return {"status": "ok", "unreadable_python_command_lines": unreadable,
            "processes": sorted(result, key=lambda row: row["pid"])}


def query_processes(handoff):
    started = utc_now()
    if os.name != "nt":
        result = {"status": "unavailable", "reason": "windows_required", "processes": []}
    else:
        command = POWERSHELL_QUERY.replace("__HANDOFF_LITERAL__", ps_literal(handoff))
        try:
            completed = subprocess.run(
                ["powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command],
                stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                timeout=QUERY_TIMEOUT_SECONDS, check=False,
            )
            if len(completed.stdout) > MAX_JSON_BYTES:
                raise ValueError("Oversized process response")
            result = parse_sample(completed.stdout.decode("utf-8-sig"))
            if completed.returncode != 0:
                result = {"status": "unavailable", "reason": "cim_query_failed", "processes": []}
        except subprocess.TimeoutExpired:
            result = {"status": "unavailable", "reason": "query_timed_out", "processes": []}
        except OSError:
            result = {"status": "unavailable", "reason": "powershell_unavailable", "processes": []}
        except (UnicodeError, ValueError, TypeError):
            result = {"status": "unavailable", "reason": "invalid_query_response", "processes": []}
    return {"started_utc": started, "finished_utc": utc_now(), **result}


def compare_samples(first, second):
    """Never compare a reused PID or turn absent/invalid counters into zero."""
    if first["status"] != "ok" or second["status"] != "ok":
        return {"state": "INSPECTION_UNAVAILABLE", "deltas": []}
    partial = bool(first["unreadable_python_command_lines"] or second["unreadable_python_command_lines"])
    before = {(row["pid"], row["started_utc"]): row for row in first["processes"] if row["started_utc"]}
    after = {(row["pid"], row["started_utc"]): row for row in second["processes"] if row["started_utc"]}
    identity_missing = len(before) != len(first["processes"]) or len(after) != len(second["processes"])
    deltas, activity, uncertain = [], False, identity_missing
    for key in sorted(set(before) & set(after)):
        old, new = before[key], after[key]
        valid = all(old[name] is not None and new[name] is not None and new[name] >= old[name] for name in COUNTERS)
        if old["role"] != new["role"]:
            valid = False
        row = {"pid": key[0], "started_utc": key[1], "role": new["role"]}
        if valid:
            differences = {name: new[name] - old[name] for name in COUNTERS}
            activity = activity or any(differences.values())
            row.update({"state": "COUNTERS_COMPARED", "cpu_seconds": (differences["kernel_100ns"] + differences["user_100ns"]) / 10_000_000,
                        "read_bytes": differences["read_bytes"], "write_bytes": differences["write_bytes"]})
        else:
            uncertain = True
            row["state"] = "COUNTERS_UNAVAILABLE_OR_DECREASED"
        deltas.append(row)
    changed = set(before) != set(after)
    if activity:
        state = "ACTIVITY_OBSERVED"
    elif changed:
        state = "MATCHING_PROCESS_SET_CHANGED"
    elif uncertain:
        state = "INCONCLUSIVE_COUNTERS"
    elif not before:
        state = "NO_VISIBLE_MATCH_INSPECTION_PARTIAL" if partial else "NO_MATCHING_PROCESS"
    else:
        state = "NO_ACTIVITY_IN_SAMPLE"
    return {"state": state, "deltas": deltas, "visibility_partial": partial,
            "new_process_ids": sorted(key[0] for key in set(after) - set(before)),
            "ended_process_ids": sorted(key[0] for key in set(before) - set(after)),
            "identity_or_counters_incomplete": uncertain}


def path_metadata(path):
    result = {"path": str(path)}
    try:
        info = path.lstat()
    except FileNotFoundError:
        return {**result, "state": "MISSING"}
    except OSError as error:
        return {**result, "state": "UNAVAILABLE", "error_number": error.errno}
    if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
        kind = "LINK_OR_REPARSE_POINT"
    elif stat.S_ISDIR(info.st_mode):
        kind = "DIRECTORY"
    elif stat.S_ISREG(info.st_mode):
        kind = "FILE"
    else:
        kind = "OTHER"
    return {**result, "state": kind, "bytes": info.st_size, "modified_ns": info.st_mtime_ns}


def small_json(path):
    result = path_metadata(path)
    if result["state"] != "FILE":
        return result
    try:
        with path.open("rb") as source:
            before = os.fstat(source.fileno())
            raw = source.read(MAX_JSON_BYTES + 1)
            after = os.fstat(source.fileno())
        # Compare one stat API to itself; Windows path/handle ctime semantics differ.
        signature = lambda info: (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)
        if signature(before) != signature(after):
            return {**result, "read_state": "CHANGED_WHILE_READING"}
        if len(raw) > MAX_JSON_BYTES:
            return {**result, "read_state": "TOO_LARGE"}
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError("Expected JSON object")
        # No full receipt, private path inventory, or arbitrary JSON value is copied.
        fields = {}
        for key in ("schema", "candidate_id", "status", "runtime_acceptance", "contract_sha256"):
            item = value.get(key)
            if isinstance(item, str) and re.fullmatch(r"[A-Za-z0-9_./:-]{1,128}", item):
                fields[key] = item
        return {**result, "read_state": "READ", "read_bytes": len(raw),
                "sha256": hashlib.sha256(raw).hexdigest(), "fields": fields}
    except (UnicodeError, ValueError):
        return {**result, "read_state": "INVALID_JSON"}
    except OSError as error:
        return {**result, "read_state": "UNAVAILABLE", "error_number": error.errno}


def collect_paths(handoff, export_root=EXPORT_ROOT, source=SOURCE):
    candidate = export_root / CANDIDATE_ID
    preparation = export_root / (CANDIDATE_ID + "-preparation")
    result = {
        "source": path_metadata(source), "export_root": path_metadata(export_root),
        "candidate": path_metadata(candidate), "preparation": path_metadata(preparation),
        "contract": small_json(handoff / "connected-contract.json"),
        "assembly_receipt": small_json(candidate / "ASSEMBLY.json"),
        "preparation_receipt": small_json(preparation / "PREPARATION.json"),
        "candidate_incomplete_marker": path_metadata(candidate / "INCOMPLETE.json"),
        "preparation_incomplete_marker": path_metadata(preparation / "INCOMPLETE.json"),
        "client_zip": path_metadata(candidate / "Client-Candidate.zip"),
        "server_zip": path_metadata(candidate / (CANDIDATE_ID + "-server-candidate.zip")),
    }
    result["contract_matches_reviewed"] = result["contract"].get("sha256") == CONTRACT_SHA256
    receipts = [result["assembly_receipt"], result["preparation_receipt"]]
    good_receipts = all(row.get("read_state") == "READ" and
                        row.get("fields", {}).get("candidate_id") == CANDIDATE_ID and
                        row.get("fields", {}).get("status") == "CANDIDATE_PENDING_ACCEPTANCE" and
                        row.get("fields", {}).get("runtime_acceptance") == "NOT_RUN" for row in receipts)
    good_receipts = good_receipts and result["preparation_receipt"].get("fields", {}).get("contract_sha256") == CONTRACT_SHA256
    markers_absent = all(result[name]["state"] == "MISSING" for name in
                         ("candidate_incomplete_marker", "preparation_incomplete_marker"))
    archives_present = all(result[name]["state"] == "FILE" for name in ("client_zip", "server_zip"))
    if good_receipts and markers_absent and archives_present and result["contract_matches_reviewed"]:
        result["completion_observation"] = "SUCCESS_RECEIPTS_PRESENT_NOT_REVERIFIED"
    elif any(result[name]["state"] != "MISSING" for name in ("candidate_incomplete_marker", "preparation_incomplete_marker")):
        result["completion_observation"] = "INCOMPLETE_OR_UNAVAILABLE_MARKERS"
    else:
        result["completion_observation"] = "COMPLETION_NOT_ESTABLISHED"
    return result


def save_report(handoff, report):
    """One new report beside this diagnostic; no output path option or mkdir."""
    if handoff.name.lower() != "vale-038" or handoff.parent.name.lower() != "vale-export":
        raise ValueError("Place both diagnostic files in Vale-Export\\vale-038 before saving a report")
    if path_metadata(handoff)["state"] != "DIRECTORY":
        raise ValueError("Report folder must be an existing regular directory")
    filename = "ASSEMBLY-STATUS-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12] + ".json"
    destination = handoff / filename
    raw = (json.dumps(report, indent=2, sort_keys=True, ensure_ascii=True) + "\n").encode("utf-8")
    with destination.open("xb") as output:
        output.write(raw)
    return destination


MESSAGES = {
    "ACTIVITY_OBSERVED": "The matching assembly process used CPU or transferred I/O during the sample.",
    "MATCHING_PROCESS_SET_CHANGED": "A matching process started or ended during the sample; inspect the assembly window for its result.",
    "NO_ACTIVITY_IN_SAMPLE": "The matching process showed no counter increase in this short sample. This does not establish a hang.",
    "NO_MATCHING_PROCESS": "No process using this handoff's exact assembly tool paths was found. Inspect the original window for an error or completion message.",
    "NO_VISIBLE_MATCH_INSPECTION_PARTIAL": "No matching process was visible, but some Python command lines were unavailable. Process absence is not established.",
    "INCONCLUSIVE_COUNTERS": "A matching process was found, but its identity or counters could not be compared reliably.",
    "INSPECTION_UNAVAILABLE": "Process inspection was unavailable. This does not tell us whether assembly is running.",
}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--print-only", action="store_true", help="Print the report without writing a file")
    args = parser.parse_args(argv)
    if sys.version_info < (3, 8):
        print("Python 3.8 or later is required.")
        return 2
    handoff = Path(__file__).absolute().parent
    print("Vale assembly observation. Keep the existing assembly window open.", flush=True)
    print("Checking matching processes twice; the sampling interval is five seconds.", flush=True)
    first = query_processes(handoff)
    started_wait = time.monotonic()
    if os.name == "nt":
        time.sleep(SAMPLE_SECONDS)
    second = query_processes(handoff)
    elapsed = time.monotonic() - started_wait
    comparison = compare_samples(first, second)
    paths = collect_paths(handoff) if os.name == "nt" else {"completion_observation": "WINDOWS_PATHS_NOT_INSPECTED"}
    report = {"schema": "vale.assembly-status/v1", "observed_utc": utc_now(),
              "candidate_id": CANDIDATE_ID, "handoff_directory": str(handoff),
              "scope": "Exact assembly tool paths under this handoff; fixed D:\\Codex source and export paths",
              "interval_requested_seconds": SAMPLE_SECONDS, "seconds_between_first_query_end_and_second_query_end": round(elapsed, 3),
              "samples": [first, second], "process_observation": comparison, "paths": paths,
              "limits": ["Short observation; zero counters do not establish a hang.",
                         "I/O counters are process activity, not file progress or verified bytes.",
                         "Only small JSON receipts/contract and path metadata were read; archives and worlds were not hashed.",
                         "No assembly process was stopped or restarted, and no candidate or source file was changed.",
                         "No raw process command lines, environment variables, or unrelated process details were saved.",
                         "This diagnostic does not establish Minecraft or hosted acceptance."]}
    print(MESSAGES[comparison["state"]])
    if comparison.get("visibility_partial"):
        print("Some Python command lines were unavailable; another matching process could be unobserved.")
    for row in comparison["deltas"]:
        if row["state"] == "COUNTERS_COMPARED":
            print("PID {pid}: CPU {cpu_seconds:.3f}s; read {read_bytes:,} bytes; wrote {write_bytes:,} bytes.".format(**row))
    if os.name == "nt":
        print("Source directory: " + paths["source"]["state"])
        print("Expected output: " + str(EXPORT_ROOT / CANDIDATE_ID))
        print("Candidate directory: " + paths["candidate"]["state"] + "; preparation directory: " + paths["preparation"]["state"])
        print("Completion observation: " + paths["completion_observation"])
        print("Reviewed contract matches: " + str(paths["contract_matches_reviewed"]))
    if args.print_only:
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    if os.name != "nt":
        print("Windows is required for this diagnostic. No report was written; --print-only shows the unavailable result.")
        return 2
    try:
        destination = save_report(handoff, report)
    except (OSError, ValueError) as error:
        print("Could not save a new status report: " + str(error))
        return 2
    print("Saved new report: " + str(destination))
    print("The report can be inspected through File Link. Keep incomplete output unchanged.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
