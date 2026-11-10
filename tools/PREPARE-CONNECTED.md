# Prepare a reviewed connected-PC candidate

`prepare_connected_release.py` connects bounded artifact transfers to the
existing strict [candidate assembler](ASSEMBLY.md). It reconstructs exact JARs,
prepares the matching client and the owned launcher wrapper, writes a concrete
assembly plan, then invokes `assemble_release.py`. Its only successful release
status is `CANDIDATE_PENDING_ACCEPTANCE`.

This is file preparation and assembly. It never starts Java or Minecraft,
downloads dependencies, deploys to Bisect, replaces the original server or ZIP,
or treats a historical acceptance receipt as a new test result.

## Run once from the reviewed inputs

Keep these files together in one tools directory:

- `Resume-Vale.ps1`
- `Resume-Vale.cmd` (optional Command Prompt entry point)
- `prepare_connected_release.py`
- `assemble_release.py`
- `release_pack.py`
- `package_bootstrap.py`

The reviewed contract, its referenced source inventory, transfer manifest and
transfer parts must already exist. The source server must be stopped and remain
unchanged while the command runs. Python 3.8 or later must already be installed.

```powershell
.\tools\Resume-Vale.ps1 -Contract 'D:\Codex\Vale-Export\Inputs\connected-contract.json' -ContractSha256 REVIEWED_CONTRACT_SHA256
```

The equivalent Command Prompt entry point calls the same Python utility
directly, without invoking PowerShell or changing its execution policy:

```text
tools\Resume-Vale.cmd "D:\Codex\Vale-Export\Inputs\connected-contract.json" REVIEWED_CONTRACT_SHA256
```

Optional third and fourth arguments specify the complete source directory and
`Vale-Export` parent. An operator-created concrete shortcut or command file can
supply these reviewed arguments; the generic launcher never reads an expected
contract digest from the current contract itself.

The digest above is a placeholder. Supply the exact SHA-256 recorded when the
concrete contract was reviewed; the tool never computes an expected contract
digest from an unreviewed current file. Defaults are the actual project stage
at `D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage` and export parent
`D:\Codex\Vale-Export`. `-Source` and `-Destination` explicitly override them.
The destination parent must be named `Vale-Export` and must be outside the source.

The same operation can be run without PowerShell:

```text
python tools/prepare_connected_release.py --source COMPLETE_SERVER_DIRECTORY --contract REVIEWED_CONTRACT_JSON --contract-sha256 REVIEWED_CONTRACT_SHA256 --export-root VALE_EXPORT_DIRECTORY
```

An explicitly reviewed external Magma base is optional. When the contract sets
the bootstrap input kind to `external`, supply `-Launcher` or `--launcher` with
that exact local JAR. Otherwise the tool uses the reviewed launcher alias in
the complete source. `package_bootstrap.py` accepts an already owned wrapper
and replaces only owned wrapper classes, its plan and `Main-Class`; it does not
require removing upstream data or extracting a guessed base runtime.

## Fixed review contract

The contract has schema `vale.connected-release/v1`. All fields below are
required and unknown fields are rejected. No source inventories or component
rosters are discovered and silently accepted during execution.

| Field | Meaning |
| --- | --- |
| `candidate_id` | A fresh 1–100 character identifier using letters, digits, underscores and hyphens. |
| `source_checkpoint` | Reviewed source `id` and canonical source-input `sha256`; same meaning as the assembler field. |
| `source_inventory` | `file`, `bytes`, `sha256`, `inventory_sha256`, `payload_inventory_sha256`. The file path is relative to the contract. The two inventory digests use the existing release tool canonical encoding. |
| `world` | Exact `Naruto.test` path, file digest, count, bytes and overworld region count, as in the assembler plan. |
| `source_server_mods` | Complete original server mod roster, including exact `path`, `bytes`, `sha256`, and `mods` mapping of IDs to versions. |
| `source_plugins` | Complete original active plugin JAR roster. Each row has `path`, `bytes`, `sha256`; `plugins/update` is included when present. |
| `source_client` | Exactly `path`, `bytes`, `sha256`, `mods`, `manifest`. Path is `client-install/Client.zip`; `mods` is the complete original client roster; `manifest` has the original manifest's `bytes` and `sha256`. |
| `transfer` | Exact `file`, `bytes`, `sha256` of the bounded transfer manifest, relative to the contract. |
| `server_changes` | Explicit original path to new artifact substitutions, described below. |
| `server_mods` | Complete final server mod roster with exact file identities and observed mod IDs/versions. |
| `client_changes` | Explicit original client path to new artifact substitutions. |
| `client_mods` | Complete final client roster with exact identities and mod IDs/versions. |
| `client_version` | Exact new manifest version. The utility changes only this manifest field. |
| `client_forge` | Existing exact Forge loader version. The original client must already select it and Minecraft 1.12.2. |
| `bootstrap` | Exactly `aliases`, `wrapper_artifact`, `input`, described below. |
| `prior_bootstrap_plan` | Exact `path`, `bytes`, `sha256` of the original `release/release-plan.properties`. Its bytes must also equal the embedded plan in both original launcher aliases. |
| `required_configs` | Unique original paths under `config/` or the known CrazyCrates configuration/crate paths. CrazyCrates `config.yml` must be listed whenever present. All are pinned in the new bootstrap. |

Every mod roster row has exactly these fields:

```text
path: portable relative JAR path
bytes: exact integer size
sha256: exact lowercase SHA-256
mods: object mapping actually observed Forge mod IDs to versions
```

A change has exactly `old` and `new`. `old` is the existing relative mod path,
or `null` for an explicitly reviewed addition. `new` is `null` for an explicit
removal, or an object containing `path` and `artifact`. `artifact` is the exact
filename in the transfer manifest, not an arbitrary source path. For example,
the shape of a server upgrade is:

```json
{
  "old": "mods/vale-orders-0.3.6.jar",
  "new": {
    "path": "mods/vale-orders-0.3.8.jar",
    "artifact": "vale-orders-0.3.8.jar"
  }
}
```

Server changes are restricted to `mods/*.jar` and `mods/1.12.2/*.jar`; client
changes to `overrides/mods/*.jar`. This preparation utility does not alter
configuration, world records, plugins or unrelated files. Configuration changes
would need a separately reviewed extension, or a fully prepared plan for the
existing assembler. Retained plugin bytes remain exactly those in the fixed
source inventory.

`bootstrap.aliases` contains exactly `server.jar` and one other root JAR name;
both originals must exist and be identical. `wrapper_artifact` selects the
exact transferred owned wrapper library. `input` is either:

- `kind: "source"`, `path` equal to one reviewed alias, `bytes`, `sha256`; or
- `kind: "external"`, `bytes`, `sha256`, with an explicit local launcher argument.

The input launcher may already contain the older owned wrapper. The new
candidate ID must differ from its embedded release ID. All unowned launcher
member payloads are preserved and signature-bearing inputs are refused by
the existing bootstrap packager.

The prior source plan is required and parsed by the existing strict bootstrap
validator. Every prior expected pin must match the original source. Every
historically retired path must already be absent. The new plan retains all
unchanged prior expected pins and all historical retirement identities, then
adds the explicitly replaced old mod paths. Expected/retired overlap, including
case and Unicode collisions, is rejected. A legacy mod or crate cannot be
silently reintroduced by a new filename mapping or by dropping an old retirement.

## Reviewed inventory format

The normalized source inventory is:

```json
{
  "schema": "vale.connected-source-inventory/v1",
  "entries": []
}
```

The empty example is intentionally unusable. Populate `entries` with the
complete reviewed inventory sorted by exact portable path. Directory rows
contain exactly `path` and `kind: "directory"`; file rows also contain `bytes`
and `sha256`, with `kind: "file"`. Preserve empty directory records. No paths,
hashes or counts can be omitted. The tool checks the raw file identity, both
canonical digests, and exact equality against a fresh complete local audit.

A complete File Link scan can be normalized into this format during review.
Alternatively, `source_inventory.file` can directly name the already-created
`.jsonl` scan. The utility accepts only the exact version 2 `scan_tree` header,
`hashedFiles: true`, complete file/directory rows, and a final `complete: true`
footer whose counts and byte total match. Unknown or omitted-file records fail.
It normalizes these rows into the same sorted release-tool entries. The contract
must still pin both the original JSONL file's bytes/hash and the two independently
reviewed canonical inventory digests. A scan's raw hash is not the release
tool's canonical digest; never substitute one for the other.

## Bounded artifact transfer format

The transfer has schema `vale.artifact-transfer/v1` and exactly these fields:

- `source_commit`: the 40-character Git commit identifying the built source.
- `source_input_sha256`: the reviewed source-input digest, equal to the contract.
- `part_max_bytes`: exactly `262144`.
- `artifacts`: nonempty array of exact component records.

Each artifact contains exactly `module`, `role`, `filename`, `bytes`, `sha256`
and `parts`. `role` is `runtime_mod` or `bootstrap_library`. A part contains
exactly `file`, `bytes`, `sha256`; paths are filenames relative to the transfer
manifest and sizes are at most 256 KiB. The parts array order defines byte order.
Small artifacts may use one unchanged JAR as their sole part. Larger artifacts
use numbered part files. Both individual parts and the whole reconstructed JAR
must match. Duplicate/colliding part or artifact paths, unused transferred
artifacts and unselected replacement files are rejected.

Every artifact must be explicitly used by a server/client change or selected
as the wrapper. The complete final roster remains a separate fixed review;
providing a transferred JAR alone does not authorize adding it to the runtime.

## New output directories and evidence

For candidate `NAME`, output appears only in new directories:

- `Vale-Export/NAME-preparation/`: exact restored artifacts, retained reviewed
  inputs, prepared client, wrapper/receipt, concrete `candidate-plan.json` and
  `PREPARATION.json`.
- `Vale-Export/NAME/`: the full assembler output documented in `ASSEMBLY.md`.

The new bootstrap pins all active server mod and plugin JARs, required configs,
server properties and the exact newly prepared client ZIP. It also retains the
reviewed historical pin coverage and retirement list. Shared client/server
mods must have identical whole bytes and versions. The utility prepares the
same client receipt and deterministic ZIP as the assembler and checks their
equality, avoiding a circular plan hash. Existing client shader files remain
unchanged.

Existing output directories are never resumed or overwritten. A failure keeps
an `INCOMPLETE.json` marker and any partial preparation for inspection. Resolve
the failure, review a new candidate ID/contract and use new output directories.
The utility does not delete incomplete or original data. A partial preparation,
old runtime log or an assembly receipt is never a runtime pass.

## Verification

```text
python -m unittest discover -s tests -p test_prepare_connected_release.py -v
```

The fixtures exercise complete source preservation, multipart reconstruction,
exact client parity, wrapper upstream preservation and pin coverage, altered
source/contract rejection, bad parts and full hashes, duplicates, out-of-scope
changes, unknown plugins, mismatched client metadata, shared-byte mismatches,
missing CrazyCrates pins, existing outputs, source/output separation and an
explicitly pinned external launcher, plus retained historical retirements,
retirement collisions, prior resource mismatch and reused deployment IDs.
They do not execute Windows PowerShell or Command Prompt,
Minecraft, the real saved world or Bisect.
