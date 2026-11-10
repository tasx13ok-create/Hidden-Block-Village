# Local inventory and ZIP transport tools

`release_pack.py` works with **existing files**. It inventories a server directory,
client directory, or ZIP; splits one accepted ZIP into transport parts; and joins
those parts back into exactly the original ZIP. It does not build a modpack,
start Minecraft, modify worlds, install anything on Bisect, authenticate, or
upload to GitHub. There are no dependencies beyond Python 3.8 or later.

These tools are source deliverables. **No final Minecraft ZIP is supplied by
these tools or their tests.** An inventory result is not a completion percentage
or runtime acceptance. Existing pending or stale recovery receipts cannot be
used as acceptance evidence by changing a top-level flag.

## Commands

Run from the repository root. On Windows, use `py -3` where the examples use
`python`. Quoted paths support spaces. Output parents must already exist.

### Audit existing bytes

```powershell
py -3 tools/release_pack.py audit "D:\Codex\Vale-Export\Review\server-stage" --output "D:\Codex\Vale-Export\Review\server-inventory.json"
py -3 tools/release_pack.py audit "D:\Codex\Vale-Export\Review\Client.zip" --output "D:\Codex\Vale-Export\Review\client-inventory.json"
```

The paths above illustrate a future review directory; this tool does not create
or populate that directory. Point `audit` at the actual located files. The report
must be outside an audited directory and must not replace a different file.
An identical existing report is verified and reused.

Every regular file gets its exact relative path, byte count, and streamed
SHA-256. Empty directories are recorded. ZIP audits also hash the compressed ZIP
itself and read **every entry to EOF** to check its CRC. Nothing is extracted.
No versions, registry keys, sides, or compatibility are inferred from filenames.

For a long inventory, add `--progress` to print aggregate `VALE_PROGRESS` lines
on stdout before the final JSON result. Tree scans, file hashes, raw ZIP hashes
and expanded ZIP-entry reads have separate counters. Updates occur at phase
boundaries and at most once every two seconds during active reads. They contain
no file paths, create no progress files and do not change the saved audit report.
The default CLI remains JSON-only. The Python `audit(..., progress=callback)`
argument is optional; callback errors abort the operation, and all existing
input-identity and CRC failures still propagate. Progress counts processed
bytes and is not a whole-job completion percentage or acceptance evidence.

For directory/ZIP content, there are two deterministic digests:

| Field | Meaning |
| --- | --- |
| `inventory_sha256` | Canonical JSON of every file and explicit directory entry. |
| `payload_inventory_sha256` | Canonical JSON of every **file** entry, including its path, size, and hash. This is the acceptance binding. |
| `archive.sha256` | All original compressed ZIP bytes, including archive metadata. ZIP input only. |

File entries are sorted by their exact relative path. Canonical JSON uses UTF-8,
sorted object keys, compact separators, no non-finite numbers, and one trailing
LF. The file-only digest allows an exact extracted tree and its ZIP to bind to
the same payload even when the ZIP omits explicit parent-directory entries.
The full inventory still records every directory; the ZIP digest binds all ZIP
metadata and directory entries as well.

### Add declared version and provenance

Supply `--declaration path/to/declaration.json` when the real component identities
have been recovered. The declaration remains visibly labelled
`DECLARED_NOT_RUNTIME_VERIFIED`; it never becomes observed runtime evidence.
This is a schema example, not an inventory of installed Vale components:

```json
{
  "schema": "vale.pack-declaration/v1",
  "release_id": "candidate-identifier",
  "pack_role": "server",
  "declared_version": "version-from-authoritative-build",
  "source_checkpoint": "commit-or-snapshot-identifier",
  "components": [
    {
      "path": "mods/example.jar",
      "id": "id-from-authoritative-registry-or-metadata",
      "declared_version": "version-from-authoritative-build",
      "side": "both",
      "provenance": "actual build receipt or source reference"
    }
  ]
}
```

`pack_role` is `server`, `client`, or `combined`. Component `side` is `server`,
`client`, `both`, or `unknown`. Every declared component path must exist in the
observed file inventory; duplicate component rows are rejected. A declaration
may describe only a reviewed subset. All files are still inventoried, including
files without component declarations. No file contents are copied into the
report, but filenames and supplied provenance text may be private: review
reports before public distribution.

### Split one accepted ZIP

This step is only applicable after the real package's acceptance gaps are
resolved. It takes an existing ZIP and never decompresses/recompresses that ZIP
to produce the parts. It does decompress entries during the preceding read-only
CRC/inventory check.

```powershell
py -3 tools/release_pack.py split "D:\Codex\Vale-Export\Accepted\final-bisect.zip" --acceptance "D:\Codex\Vale-Export\Accepted\acceptance.json" --output-dir "D:\Codex\Vale-Export\Release-Parts"
```

The example does not assert that `final-bisect.zip` or an accepted receipt exists.
`Release-Parts` must be a **new** directory. The default part length is
**1,899,000,000 bytes**, strictly below 1.9 decimal GB. `--part-bytes` can choose a
smaller positive integer. At most 999 parts are allowed, leaving room for the
manifest alongside parts when a distributor permits 1,000 assets. The tool
does not verify a hosting account's current limits.

For a two-part archive, the output names are:

```text
final-bisect.zip.part-00001-of-00002
final-bisect.zip.part-00002-of-00002
final-bisect.zip.parts.json
```

Names, part lengths, part hashes, and the manifest are deterministic for the same
archive, part size, and acceptance receipt. The manifest identifies the exact
original filename/byte count/SHA-256 and ordered parts. Parts are streamed to
temporary files, flushed, read back and hashed, then published without replacing
existing files. The manifest is published last. The command prints its SHA-256;
retain that value through a trusted channel for download verification.

The tool does not copy private source snapshots, test logs, or acceptance
receipts into the transport directory. The manifest contains their binding
summary and receipt digest. Public distribution permission and hosted-player
data review are separate from byte integrity; neither is certified by this tool.

### Join downloaded parts

Put the manifest and all its parts in one directory. Use an existing destination
directory with enough free space for the complete ZIP:

```powershell
py -3 tools/release_pack.py join "D:\Downloads\Release-Parts\final-bisect.zip.parts.json" --output-dir "D:\Codex\Vale-Export\Verified-Download" --manifest-sha256 "TRUSTED_64_CHARACTER_LOWERCASE_SHA256"
```

Replace the placeholder digest with the real recorded manifest digest. The
`--manifest-sha256` option is optional for local round trips, but should be used
when checking a downloaded manifest against a trusted published value. Hashes
are integrity checks, not signatures or proof of publisher identity.

`join` verifies every part, its exact expected length/name/order, and the complete
reconstructed hash/size. It writes a unique `.partial` file, flushes and reads it
back, then publishes the original filename. It never overwrites a different
existing ZIP. An identical existing ZIP is verified and reused; the parts are
still checked. The result says `NOT_RETESTED` for runtime acceptance because no
gameplay test occurs during joining.

## Acceptance evidence contract

`split` is deliberately stricter than `audit`. The tool does not manufacture
acceptance receipts or offer a flag to ignore pending checks.

The evidence producer must record the actual tested **pretest input payload**:
the exact runtime, configuration, matching client, and intended world bytes.
Tests should operate on an isolated copy. Post-test world saves or generated
logs must not silently replace that input snapshot. If the packaged inputs
change, existing receipts for the old payload are stale. The complete file
inventory binds every configuration file, including files omitted from older
selective pin lists.

The existing local-release checklist supplies mandatory gate IDs R01–R18:

| Gate | Existing requirement |
| --- | --- |
| R01 | Authoritative source checkpoint |
| R02 | Required behavior/content list and unresolved scope |
| R03 | Runtime/dependency versions |
| R04 | Server/client contents |
| R05 | Duplicate mod IDs and wrong artifacts |
| R06 | Intended world identity and preservation |
| R07 | Configuration and service data |
| R08 | Host-upload layout |
| R09 | Measured unpacked payload size |
| R10 | Selected-source build |
| R11 | Shipped/tested artifact identity |
| R12 | Packaged dedicated-server boot |
| R13 | World load, save, and restart |
| R14 | Installed services and failure paths |
| R15 | Matching-client join |
| R16 | Required visuals/content in game |
| R17 | Resource and persistence behavior |
| R18 | Final archive identity and release notes |

Include every known supplemental gate in `required_gates` as well. For the
interrupted 0.3.6 recovery, the pending Quartermaster/Spirit provisions acceptance
belongs in the R14 evidence and must remain an explicit required supplemental
gate until actually resolved, for example `R14-quartermaster-supplemental`.
An outer class-comparison pass does not satisfy this check. The script validates
the supplied required set; it cannot discover omitted gameplay obligations.

### Receipt fields

`acceptance.json` uses these fields:

| Field | Required value/content |
| --- | --- |
| `schema` | `vale.release-acceptance/v1` |
| `status` | Exactly `ACCEPTED` only after real evidence supports it. |
| `scope` | `local_client_and_server`; no hosted-Bisect assertion. |
| `archive` | Exact `filename`, integer `bytes`, lowercase `sha256` from the ZIP audit. |
| `payload_inventory_sha256` | File-only digest from the exact input payload audit. |
| `source_checkpoint` | Object with nonempty `id` and lowercase `sha256` of the frozen source snapshot or complete source-file inventory artifact. |
| `source_snapshot` | Relative `path`, integer `bytes`, and `sha256` for that actual artifact. Its hash must match `source_checkpoint.sha256`. |
| `required_gates` | Unique R01–R18 IDs plus all applicable supplemental IDs. |
| `unresolved_requirements` | Empty array only when no required unresolved work remains. |
| `evidence` | Nonempty array of relative file references: `path`, `bytes`, `sha256`. |

References resolve below the receipt directory. Each evidence JSON uses:

| Field | Required value/content |
| --- | --- |
| `schema` | `vale.validation-evidence/v1` |
| `status` | `PASSED` |
| `payload_inventory_sha256` | Same exact input-payload digest as the receipt. |
| `source_checkpoint` | Same exact checkpoint object as the receipt. |
| `unresolved_requirements` | Empty array. |
| `gates` | Nonempty array of `{ "id": "R12", "status": "PASSED" }` records for the gates genuinely evidenced. |
| `logs` | Relative references with `path`, `bytes`, `sha256`; paths resolve below this evidence JSON's directory. |

Each gate may appear only once across the referenced evidence set. At least one
hashed log is required for evidence covering build/runtime gates R10–R17 or any
supplemental gate. An evidence JSON may cover multiple gates when its real log
and recorded observations support all of them. The actual evidence producer
must preserve the observed scope and failures, not mechanically rewrite an old
receipt into this schema.

The validator checks hashes, completeness of the declared required gates, and
binding consistency. It hashes the source snapshot/inventory artifact but does
not independently rebuild the source or inspect every file referenced inside a
source-inventory document. **It cannot prove that tests actually ran or that a
producer's claimed pass is truthful.** No test harness is supplied here. Old
startup logs for different runtime hashes, pending supplemental results, missing
logs, or changed inputs fail this contract. Identical tested bytes do not become
stale simply because their filesystem timestamp changes.

R19–R21 concern the actual Bisect instance/deployment. They are separate from
local acceptance and are not claimed by split/join. This contract is evidence
validation, not a request for a new human approval ceremony or legal signoff.

## Failure and preservation behavior

- Original files are read-only. The tools do not delete, rename, or overwrite
  originals, operate a server, or automatically resume an interrupted assembler.
- Names must be portable relative paths. Absolute paths, traversal, backslashes
  in manifest names, reserved Windows names, duplicate/case/Unicode-colliding
  paths, symlinks/reparse points, encrypted ZIP entries, and special files fail
  explicitly. No unsupported entry is silently skipped.
- JSON duplicate keys, non-finite numbers, malformed roots, and invalid sizes
  fail. Current safety limits are 100,000 inventory entries, 64 GiB total file
  bytes, and 64 MiB per JSON document; exceeding a limit is an error, not a
  truncated successful report.
- Files and directory inventories are checked for concurrent change. Freeze
  the input first: this is not a filesystem snapshot or a synchronization tool.
- Space is checked before writing. An unexpected disk-full or permission error
  still fails safely. For split, allow at least the original ZIP size plus 1 MiB
  free; for join, the ZIP size plus a small reserve. Retaining both source and
  reconstructed ZIPs requires space for both copies.
- A failed split can leave its new directory, completed parts, `.partial` files,
  and `INCOMPLETE.json`. It is never reused automatically. Inspect and retain it
  as appropriate, then choose a new output directory after resolving the cause.
  `join` refuses a directory whose incomplete marker remains even if a manifest
  is present.
- Handled join failures remove only the unique temporary file created by that
  invocation. An abrupt process kill or power loss can leave a `.partial` file.
  It is not treated as an archive or resumed automatically.
- Publication uses a same-directory atomic operation that cannot replace a
  different destination: Windows non-replacing rename or POSIX hard link.
  Filesystems lacking this operation fail safely; there is no unsafe overwrite
  fallback. Power-loss durability and hostile concurrent filesystem changes are
  outside this tool's guarantees.
- Operation success is emitted as one JSON object on stdout. Operation failures
  return exit code 2 and a JSON error on stderr; Ctrl+C returns 130. CLI usage
  errors use Python's normal argparse help/error output.

## Tool verification

```text
python -m unittest discover -s tests -v
```

Tests use small synthetic ZIPs and explicit synthetic receipt/log fixtures in
temporary directories. They cover exact round trips, deterministic output,
stale/pending proof, changed source/evidence/log hashes, path/case collisions,
CRC corruption, source mutation, disk-space failure, interrupted output, missing
or corrupt parts, and conflicting existing files. They do not run Minecraft,
test the multi-GB PC archive, or establish Windows/Bisect compatibility.
