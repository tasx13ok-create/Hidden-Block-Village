# Assemble a complete Vale candidate

`assemble_release.py` makes a new, isolated **candidate** from a complete frozen
server folder or ZIP and a reviewed plan. It preserves the original selected
input, including `Naruto.test`, and updates the explicitly selected runtime
files and the matching nested client. It produces a fully inventoried, CRC
checked ZIP. It does not launch Minecraft, replace the Downloads ZIP, deploy to
Bisect, or claim that gameplay acceptance passed.

The only successful completion status is `CANDIDATE_PENDING_ACCEPTANCE`.
The existing `release_pack.py split` acceptance requirements remain unchanged.
An assembly receipt cannot substitute for its required runtime evidence.

## Inputs that must actually exist

- The **complete** source server: either the full located staging directory or
  the full original archive. An incomplete extraction is not an acceptable input.
- A reviewed JSON plan for those exact source bytes, including the complete
  world inventory digest/counts, complete server/client mod rosters and old/new
  file identities.
- The actual replacement production JARs, configuration and client manifest,
  located relative to the plan. Neither filenames nor old build claims supply
  missing artifact bytes.
- Python 3.8 or later. The Python tools use only the standard library.

The source should be frozen with its server stopped. The program checks for
changes; it does not take an operating-system snapshot or stop a process.
It does not download dependencies, install a remote execution service, handle
credentials or change PowerShell execution policy.

**No populated plan for the disconnected Windows PC is supplied here.** Its
current full inventory and replacement build hashes must be recovered before
an actual package is assembled. The tests use explicitly synthetic fixtures.

## Run on Windows

From the checked-out repository, with the real reviewed plan in place:

```powershell
.\tools\Finish-Vale.ps1 -Source 'D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage' -Plan 'D:\Codex\Vale-Export\Inputs\candidate-plan.json'
```

The wrapper defaults to `D:\Codex\Vale-Export`. `-Destination` changes the
output parent, and `-Name` selects a new output directory name. It finds an
installed `py.exe -3` or `python.exe` and forwards arguments without evaluating
them as shell commands. It does not bypass a machine's script execution policy.
The equivalent command, also usable on Linux for verification, is:

```powershell
py -3 tools/assemble_release.py --source 'D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage' --plan 'D:\Codex\Vale-Export\Inputs\candidate-plan.json' --export-root 'D:\Codex\Vale-Export'
```

All output is beneath a **new** directory named by the plan's `candidate_id`,
or by `--name`. Existing directories are never resumed or replaced, even if
they contain apparently identical output. A failed run leaves its new output
with `INCOMPLETE.json`. Inspect that candidate, resolve the cause and use a new
output name. Never upload an incomplete directory.

## What the plan binds

The plan is `vale.candidate-plan/v1`. Unknown top-level fields are errors.
It is an operator-reviewed input specification, not a runtime acceptance
declaration. Its source checkpoint is recorded as supplied; the assembler does
not independently build that source or verify a remote Git commit.

| Field | Required meaning |
| --- | --- |
| `schema` | Exactly `vale.candidate-plan/v1`. |
| `candidate_id` | 1–100 letters, digits, underscores or hyphens. Must also equal the embedded bootstrap's release ID. |
| `source_checkpoint` | Nonempty `id` and lowercase SHA-256 `sha256` of the reviewed source snapshot/inventory. |
| `input_payload_inventory_sha256` | Complete file-only digest from `release_pack.py audit` of the frozen source. |
| `world` | `path`, `payload_inventory_sha256`, `files`, `bytes`, `overworld_regions`, as defined below. |
| `replacements` | Explicit server-side old/new file substitutions. |
| `server_mods` | Complete resulting active server JAR roster. |
| `client` | Exact nested-client input and intended replacements/roster/version. |
| `bootstrap` | Two root JAR aliases and embedded plan hash. |
| `required_configs` | Unique paths the embedded bootstrap must pin; includes every changed config. |

Use `release_pack.py audit` to recover complete input identities:

```powershell
py -3 tools/release_pack.py audit 'D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage' --output 'D:\Codex\Vale-Export\Inputs\source-inventory.json'
```

The audit output directory must exist and be outside the source. The source
report's `payload_inventory_sha256` includes **all** file paths, sizes and
hashes, including configuration that old selective pin lists omitted.

### World identity

`world.path` must be `Naruto.test` for this project. Select all **file** entries
whose paths begin `Naruto.test/`, preserving their order from the complete
audit. `world.payload_inventory_sha256` is `release_pack.digest()` of that
array, retaining their full paths and `kind` fields. `files` is its length;
`bytes` is the sum of file bytes. `overworld_regions` counts only
`Naruto.test/region/r.<signed-integer>.<signed-integer>.mca`.

For the actual recovered world, the historical 1,294 count is a useful
cross-check, not a replacement for a fresh complete inventory. The assembler
requires `level.dat`, a nonempty overworld region set and exact agreement with
every declared world count/hash. It then verifies the same world identity in
the output. It preserves Nether regions, player data and every other world
file too; the overworld count is only one completeness check.

`server.properties` must unambiguously select `level-name=Naruto.test`.
The tool does not change that file or infer an alternate world name.
These are byte-preservation checks, not NBT/region semantic validation or a
successful Minecraft load/save/restart.

### File substitutions

Every substitution has exactly `old` and `new`. Either can be `null` for a
reviewed addition/removal; both cannot be null. A non-null `old` has `path`,
integer `bytes` and lowercase `sha256`. A non-null `new` adds `file`, the
replacement input path **relative to the plan directory**. For example:

```json
{
  "old": {
    "path": "mods/vale-orders-0.3.6.jar",
    "bytes": 1907890,
    "sha256": "54879901cc9e5caed778ef3cf4ff7034d1951adad7dc7a94ee82466b78762acc"
  },
  "new": {
    "path": "mods/vale-orders-0.3.7.jar",
    "file": "artifacts/vale-orders-0.3.7.jar",
    "bytes": 123,
    "sha256": "REPLACE_WITH_ACTUAL_NEW_BUILD_SHA256"
  }
}
```

This illustrates the schema only. The example's new size/hash are deliberately
invalid placeholders and must never be treated as real build evidence.

Server substitutions are restricted to the two declared bootstrap aliases,
active mod/plugin JAR locations, `config/` and the known CrazyCrates config/crate
paths. They cannot replace world files, server properties or arbitrary player
data. New destinations cannot collide with an unselected original file.
Duplicates, traversal, symlinks, Windows reserved names, case collisions and
Unicode path collisions fail. Output directories may not be inside the source.

### Complete mod rosters

Each roster entry is:

```json
{
  "path": "mods/vale-orders-0.3.7.jar",
  "bytes": 123,
  "sha256": "REPLACE_WITH_ACTUAL_NEW_BUILD_SHA256",
  "mods": {"valeorders": "0.3.7"}
}
```

Again, the numeric/hash placeholders above are not usable artifact pins.
`mods` lists the IDs and versions actually observed in the production JAR.
The tool reads `mcmod.info` and class-level Forge `@Mod` annotations without
executing Java. Annotation-only modules, such as Encounters, are covered.
Conflicting metadata/annotation versions, duplicate IDs, missing JARs, extra
active JARs and a roster that disagrees with the inspected JAR all fail.

A reviewed coremod with neither Forge annotations nor `mcmod.info` can declare
`"mods": {}`; its exact bytes remain pinned. This is useful for loader hooks
that are not ordinary Forge mods. It is not a method for suppressing an actual
observed mod ID. The class reader is bounded and rejects malformed inputs.

### Nested client

`client` has exactly these fields:

| Field | Required value |
| --- | --- |
| `path` | `client-install/Client.zip` |
| `input_sha256` | Exact original nested ZIP hash. |
| `replacements` | Same old/new schema, restricted to `manifest.json` and `overrides/mods/*.jar`. |
| `mods` | Complete final roster, with paths under `overrides/mods/`. |
| `version` | Exact candidate version from the new client manifest. |
| `forge` | Exact Forge version, such as the recovered `14.23.5.2859`. |

The original client ZIP is fully CRC/inventory checked and copied to its own
work directory before replacements. Its manifest must select Minecraft 1.12.2,
the specified single primary Forge loader, `overrides`, and `files: []`.
Unresolved remote CurseForge downloads are rejected: this workflow handles the
complete local client recovered in this project.

Core (`valeorders`) and Encounters (`valeencounters`) must exist on both sides.
Every mod ID shared by server and client must have identical entire JAR bytes
and identical version. Client-only and server-only modules are retained in
their own complete rosters. Existing shader settings and other unselected
client files remain byte-identical.

Old `CLIENT-PINS.json` and `CANDIDATE-ASSEMBLY.json`, if present, move outside
the candidate into `prior-evidence/client/`. A new candidate record states
`NOT_RUN` for runtime acceptance. This record binds to the candidate ID,
source checkpoint and complete client mod roster; it does not contain the
whole plan digest, so a bootstrap can pin the resulting client ZIP without
creating a circular hash dependency.

### Bootstrap and configuration

`bootstrap` contains:

```json
{
  "paths": ["server.jar", "Magma-1.12.2-8cc35ed-server.jar"],
  "resource_sha256": "REPLACE_WITH_ACTUAL_VALE_RELEASE_PROPERTIES_SHA256"
}
```

Both aliases must contain identical exact bytes. The assembler requires the
manifest main class `village.deployment.ServerBootstrap`, exactly one embedded
`vale-release.properties`, and the actual hash declared above. Properties must
use unambiguous UTF-8 `key=value` lines without escapes or duplicate keys.
The complete expected/retired counts and entries are validated. Every expected
path must match the candidate's actual hash. Every retired path must be an
allowed retired runtime/crate file and already absent from the candidate.

Bootstrap expected pins must cover every active server mod and plugin JAR,
every path in `required_configs`, and `plugins/CrazyCrates/config.yml` whenever
it exists. Every changed configuration must be in `required_configs`.
An existing `.done` or `.pending` update marker for the candidate's release ID
causes failure; a reused marker cannot silently skip a new candidate preflight.

Prepare the bootstrap from the exact final inputs first. Its expected hashes
cannot truthfully refer to different versions or old configuration. The
assembler validates a supplied production bootstrap; it does not inject
compiled classes, modify a third-party runtime, accept an old pass flag or
invent a production bootstrap hash.

## Output and evidence invalidation

A completed output directory contains:

- `server/`: the complete copied server with the reviewed substitutions.
- `<candidate_id>-server-candidate.zip`: deterministic ZIP metadata/order,
  verified against every staged file and directory.
- `Client-Candidate.zip`: the matching client, also embedded in the server.
- `client-work/`: the complete candidate client tree used for that ZIP.
- `prior-evidence/`: untouched copies of the old server `release/` directory
  and old client pin/candidate records, outside the runtime ZIP.
- `input-inventory.json`, `candidate-inventory.json`, `client-inventory.json`:
  complete paths, byte counts and SHA-256 identities.
- `ASSEMBLY.json`: successful byte-assembly result, pending runtime checks,
  source/world identities and final archive hash.

The entire old server `release/` directory is removed from the candidate
runtime and preserved outside it. The new `release/` contains only the checked
embedded plan copy and `CANDIDATE-ASSEMBLY.json`. Old verification and payload
records therefore cannot appear to certify the new artifact. Historical logs
elsewhere in the original server remain unchanged; they are not adopted as
acceptance evidence.

All input file hashes remain unchanged except explicit replacements, the
rebuilt nested client and this evidence relocation. The world is compared
again in full. The source tree's metadata snapshot, or the source ZIP hash,
is checked at completion to detect concurrent changes. The tool itself never
writes to the selected source.

ZIP timestamps are normalized to 1980-01-01, file permissions to 0644 and
directories to 0755. Byte-identical output is verified for the same inputs,
plan and Python/zlib implementation; different compressor versions can produce
different ZIP bytes while retaining the same file inventory. The Java server
is launched through its JAR. Shell scripts, if present, can be run through
their shell rather than relying on preserved executable mode.

Space is conservatively checked for the full tree, ZIP and client work. A
surprise disk-full/permission error still leaves an incomplete candidate and
does not replace an original. Power loss can leave partial files. The program
never resumes them or treats a directory with `INCOMPLETE.json` as complete.
A file named `*-server-candidate.zip` inside an incomplete directory is still
unaccepted; the whole operation succeeds only when `ASSEMBLY.json` exists and
the incomplete marker has been removed.

## Verification scope

Run the synthetic fixture suite with:

```text
python -m unittest discover -s tests -p test_assemble_release.py -v
```

It covers complete directory/ZIP inputs, world/player-data preservation,
deterministic output, annotation-only modules, stale/corrupt pins, missing world
data, duplicate module IDs, client/server mismatch, unsafe paths, existing
output, missing configuration coverage, interrupted/corrupt copies and source
mutation. It does not run Minecraft, inspect the actual disconnected PC archive,
test Windows PowerShell execution, or establish hosted readiness.

## Package the owned bootstrap into an exact local Magma JAR

`package_bootstrap.py` fills the preparation step above without downloading or
committing a third-party runtime. It takes the actual local launcher JAR, the
source-built `Vale-Server-Bootstrap` **library** JAR, the reviewed raw
`vale-release.properties`, and expected SHA-256 values for all three inputs.
The output must be a new filename in an existing directory.

```powershell
py -3 tools/package_bootstrap.py --input 'D:\Codex\Vale-Export\Inputs\Magma-1.12.2-8cc35ed-server.jar' --input-sha256 ACTUAL_INPUT_SHA256 --wrapper 'modules\server-bootstrap\build\libs\Vale-Server-Bootstrap-0.3.7.jar' --wrapper-sha256 ACTUAL_WRAPPER_SHA256 --plan 'D:\Codex\Vale-Export\Inputs\vale-release.properties' --plan-sha256 ACTUAL_PLAN_SHA256 --output 'D:\Codex\Vale-Export\Inputs\candidate-bootstrap.jar' --bootstrap-alias 'Magma-1.12.2-8cc35ed-server.jar'
```

Replace the digest placeholders with the exact independently recorded input
identities. The command refuses stale input hashes. It accepts an existing
`Main-Class` of `net.minecraftforge.fml.relauncher.ServerLaunchWrapper` or
`village.deployment.ServerBootstrap` only. Every upstream member's expanded
bytes are preserved except the explicitly owned `ServerBootstrap.class` and
its `$...` inner classes, `vale-release.properties`, and the main manifest's
`Main-Class` field. All other manifest attributes and their original bytes are
preserved. ZIP container ordering, timestamps and permissions are normalized.

The wrapper library may contain only its manifest and the owned Java 8 class
files. A whole server JAR, source JAR or library containing unowned classes
cannot be used in its place. Superseded owned inner classes are removed and
listed explicitly in the receipt. Signature files or manifest digest/signature
attributes cause refusal; the utility never silently strips or invalidates
signatures. Unsafe ZIP paths, duplicate/colliding members, symlinks, corruption
and duplicate plan keys fail before publication.

The embedded plan cannot pin or retire its own launcher aliases or its own
plan bytes. `server.jar`, both input/output basenames and every additional
`--bootstrap-alias` are excluded from those self-references. Declare all names
the launcher will be installed under. Expected and retired paths are checked
together for case/Unicode collisions and overlap. Runtime file existence/hashes
are checked later by the full assembler; this utility checks plan structure
without claiming to have the server directory.

Repackaging an already wrapped JAR requires a **new release ID**, even if only
the wrapper implementation changed. An old `.done` marker cannot be reused as
evidence for that upgrade. The assembler also refuses any same-ID `.done` or
`.pending` marker in its source candidate.

The tool publishes a new JAR with no-overwrite semantics and an adjacent
`<filename>.receipt.json`. Both must exist for a completed packaging result.
An interruption between the two can leave a JAR without a receipt; retain it
for inspection and use a new output filename after resolving the cause. The
receipt records full input/output hashes, CRC verification, exact changed and
removed owned paths, upstream preservation and
`BOOTSTRAP_CANDIDATE_PENDING_ACCEPTANCE`. It does not claim a Magma startup,
saved-world load or successful player connection. All input files remain
untouched. No packaged Magma binary belongs in this source repository.

```text
python -m unittest discover -s tests -p test_package_bootstrap.py -v
```

These synthetic tests verify upstream payload/manifest preservation, fresh
output, stale hashes, signature refusal, strict wrapper ownership, Java 8 class
headers, plan self-reference/collision rules, new release IDs, ZIP corruption
and unsafe entries. They do not execute the upstream runtime.
