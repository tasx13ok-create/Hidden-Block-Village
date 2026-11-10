# Replace installation notes in a fresh candidate

The connected preparer can replace three known installation-note files through
an optional, hash-bound `installation_notes` contract field. It changes only
copies inside a **new** candidate. The complete original server, original
client ZIP, source inventory, existing candidates and existing preparation
directories remain unchanged.

This is useful when a new runtime candidate would otherwise inherit instructions
or acceptance claims from an older release. Reviewing and replacing the text
does not perform a Minecraft, saved-world, client or Bisect acceptance test.
Successful output remains `CANDIDATE_PENDING_ACCEPTANCE`, with
`runtime_acceptance: NOT_RUN`.

## Exact allowed paths

| Contract group | Path inside that group |
| --- | --- |
| `server` | Server root `START-HERE.txt` |
| `client` | Client ZIP root `START-HERE.txt` |
| `client` | Client ZIP root `CLIENT-INSTALL.txt` |

These names are exact and case-sensitive. Other text files, files under
`overrides/`, configuration, scripts, worlds, manifests and JARs cannot be
selected through this field. Each selected file must already exist and must be
replaced at the same path. Additions, deletions, renames, duplicate rows and
case-alias paths are refused. A replacement note must contain 1–65,536 bytes.

The existing runtime artifact transfer format stays JAR-only, with the existing
`runtime_mod` and `bootstrap_library` roles. Installation notes do not widen
`server_changes` or `client_changes`; those fields still select reviewed mods.

## Reviewed contract field

`vale.connected-release/v1` contracts without `installation_notes` retain their
existing behavior, including their original note bytes. When the optional field
is present, it contains exactly `server` and `client` arrays. Either array may be
empty. To replace all three stale notes, provide one server row and two client
rows.

Each row uses the assembler's exact `old`/`new` identity structure. The following
is a schema example, not a usable contract: replace the example sizes and hash
placeholders with independently reviewed identities.

```json
{
  "installation_notes": {
    "server": [
      {
        "old": {
          "path": "START-HERE.txt",
          "bytes": 123,
          "sha256": "REVIEWED_OLD_SHA256"
        },
        "new": {
          "path": "START-HERE.txt",
          "file": "notes/server-START-HERE.txt",
          "bytes": 456,
          "sha256": "REVIEWED_NEW_SHA256"
        }
      }
    ],
    "client": []
  }
}
```

`old` contains exactly `path`, integer `bytes` and lowercase SHA-256 `sha256`.
`new` contains those same fields plus `file`, a portable relative path from the
contract directory to the reviewed replacement input. No traversal or symlink
is permitted. Client rows use the same shape and client-root destination names;
for example, `notes/client-START-HERE.txt` can supply the client's
`START-HERE.txt` without colliding with the server note.

The preparer verifies both the old file and the new input before creating a
preparation directory. The fixed source inventory and original client ZIP hash
continue to describe the original bytes. They must not be rewritten to make
changed input appear approved. The contract itself still needs its separately
supplied reviewed SHA-256.

The new candidate ID must be fresh. Keep the new contract, replacement notes and
reviewed tool copies in a separate handoff directory; do not edit inputs or
tools used by an active assembly. Existing candidate and preparation directories
are never resumed or overwritten.

## Preparation, preservation and binding

The preparer copies the new notes into these paths relative to its new output:

```text
installation-notes/server/START-HERE.txt
installation-notes/client/START-HERE.txt
installation-notes/client/CLIENT-INSTALL.txt
```

The concrete assembly plan references those verified copies. Original client
notes are retained in `prior-client-evidence/` during preparation. The assembler
also preserves every replaced original note in its own output, outside the
server directory and both distribution ZIPs:

```text
prior-evidence/server/START-HERE.txt
prior-evidence/client/START-HERE.txt
prior-evidence/client/CLIENT-INSTALL.txt
```

Only selected rows produce these files. Preservation checks the exact original
bytes and uses no-overwrite writes. It copies the old note before the existing
replacement operation removes that note from the new candidate tree. The
original selected source is never written.

The existing `release/`, `CLIENT-PINS.json` and `CANDIDATE-ASSEMBLY.json` evidence
retirement rules remain separate. Text notes are not added to the launcher's
runtime retirement list; its JAR/crate restrictions remain unchanged.

Replacing client notes changes the complete client ZIP identity. The preparer
builds the client preview with the new notes, computes its exact ZIP hash, embeds
that hash in the new first-boot plan, and repackages both launcher aliases. The
assembler independently reconstructs the matching client and checks the
embedded expected hash. Prepared and assembled client ZIP bytes must agree.
The server note's new hash is also included in the first-boot plan, whether or
not the older plan covered that note. Other reviewed prior pins and retirement
identities remain covered.

For a directly prepared `vale.candidate-plan/v1` plan, the three exact note paths
can likewise appear in the existing server/client replacement arrays, with the
same strict identity and same-path rules. A changed server note must be pinned
by the wrapper. Any client-note replacement requires the exact resulting
`client-install/Client.zip` pin. Missing or stale pins fail assembly.

First-boot expected pins cover all listed files before installation. On later
launches, the existing bootstrap revalidates runtime JARs; non-JAR files such as
notes and the client ZIP do not become new permanent restart checks. This
extension does not change that Java behavior or provide runtime acceptance.

The final client and server inventories, final ZIP identities and assembly
receipt are generated from the actual output. The assembly receipt additionally
records `prior_installation_notes_preserved_outside_candidate`, containing the
old path, size and SHA-256 rows for each side. Complete original and final world
inventories must still agree, including player data and non-overworld files.

## Focused verification

```text
python -m unittest discover -s tests -p test_prepare_connected_release.py -v
python -m unittest discover -s tests -p test_assemble_release.py -v
```

Synthetic fixtures cover all three replacements, original-byte preservation,
client preview/assembly equality, wrapper pins and aliases, complete inventories
and world preservation, updates to prior note pins, stale size/hash rejection,
exact path restrictions, prohibited addition/removal/rename, duplicate rows,
unchanged JAR transfer scope, missing bootstrap coverage and existing-output
refusal. They do not execute the connected PC assembly or a game runtime.
