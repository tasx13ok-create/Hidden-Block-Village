"""Prepare a private Java-8-compatible copy of the exact inspected JEI input.

The source archive is retained. This removes one misplaced Java21 build output;
it does not replace JEI's code, modify any retained entry, or publish the JAR.
Activate the result only in a stopped/new client after reviewing its receipt.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

EXPECTED_SOURCE = "e069347966155efd050fe18f060162a0406c194528c7c16974263c8879c3a8a2"
MISPLACED = "java/mezz/jei/config/Tags.class"


def identity(path: Path) -> dict:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"bytes": path.stat().st_size, "sha256": digest.hexdigest()}


def repair(source: Path, destination: Path, receipt: Path) -> dict:
    source = source.resolve(strict=True)
    destination = destination.resolve()
    receipt = receipt.resolve()
    if len({source, destination, receipt}) != 3:
        raise ValueError("Source, destination and receipt must be different paths")
    if destination.exists() or receipt.exists():
        raise FileExistsError("Preserve existing candidates and receipts; select new paths")
    original = identity(source)
    if original["sha256"] != EXPECTED_SOURCE:
        raise ValueError("Unrecognized JEI input; inspect it separately")
    retained = {}
    with zipfile.ZipFile(source) as archive:
        if archive.testzip() is not None:
            raise ValueError("Source ZIP failed its CRC check")
        names = archive.namelist()
        if len(names) != len(set(names)) or names.count(MISPLACED) != 1:
            raise ValueError("Ambiguous archive entries")
        if any(n.upper().startswith("META-INF/") and n.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) for n in names):
            raise ValueError("Signed archive requires a separate reviewed repair")
        misplaced = archive.read(MISPLACED)
        if len(misplaced) < 8 or misplaced[:4] != b"\xca\xfe\xba\xbe" or struct.unpack(">H", misplaced[6:8])[0] != 65:
            raise ValueError("Misplaced entry does not match the observed Java21 class")
        for info in archive.infolist():
            if info.filename == MISPLACED:
                continue
            data = archive.read(info)
            if info.filename.endswith(".class"):
                if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe" or struct.unpack(">H", data[6:8])[0] > 52:
                    raise ValueError("Another class is incompatible with Java8: " + info.filename)
            retained[info.filename] = hashlib.sha256(data).hexdigest()
        destination.parent.mkdir(parents=True, exist_ok=True)
        with destination.open("xb") as output, zipfile.ZipFile(output, "w") as result:
            result.comment = archive.comment
            for info in archive.infolist():
                if info.filename != MISPLACED:
                    result.writestr(info, archive.read(info))
    with zipfile.ZipFile(destination) as result:
        if result.testzip() is not None or result.namelist() != [n for n in names if n != MISPLACED]:
            raise ValueError("Candidate failed CRC or exact inventory verification; retain for diagnosis")
        for name, expected in retained.items():
            if hashlib.sha256(result.read(name)).hexdigest() != expected:
                raise ValueError("Retained entry changed: " + name)
    if identity(source) != original:
        raise ValueError("Source changed during repair; do not activate candidate")
    record = {
        "schema": 1, "scope": "PRIVATE_LOCAL_JEI_JAVA8_REPAIR",
        "passed": True, "activated": False, "redistribution_permission_claimed": False,
        "source": original, "candidate": identity(destination),
        "removed_entry": {"name": MISPLACED, "bytes": len(misplaced), "class_major": 65,
                          "sha256": hashlib.sha256(misplaced).hexdigest()},
        "retained_entries_verified_byte_identical": len(retained),
        "source_retained_unchanged": True, "candidate_crc_passed": True,
        "all_retained_classes_java8_compatible": True,
    }
    receipt.parent.mkdir(parents=True, exist_ok=True)
    with receipt.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(record, stream, indent=2)
        stream.write("\n")
    return record


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(repair(args.source, args.destination, args.receipt), indent=2))


if __name__ == "__main__":
    main()
