# Connected Windows stage: Core 0.3.8 and Standards 0.3.1

The PC connection was restored with protocol 2 on 10 November 2026. The
complete `actual-bisect-server-stage` was read and hashed before any candidate
assembly. Its original files and the Downloads archive remain unchanged.

The first local assembly attempt later stopped before output creation because
the file reader compared incompatible Windows timestamp fields. The contract
still matched its reviewed bytes. The [Windows reader correction](windows-file-reader-fix.md)
retains the original inputs and adds native Windows CI coverage; the real
candidate remains pending execution of the corrected handoff.

## Measured source

| Input | Fresh result |
| --- | --- |
| Complete server stage | 2,184 files, 251 directories, 6,020,781,875 bytes |
| Complete file inventory digest | `01106c79132783e6d271e67bfea617b49e0a60ae094ce77837babe638a2c11c2` |
| `Naruto.test` | 1,771 files, 5,702,815,313 bytes |
| World inventory digest | `430234be5aee1fc8077ae6c375bb08ea9a8e275f73338d6d075f7935d8c2a686` |
| Regions | 1,294 overworld; 6 Nether |
| Existing active runtime | 9 server mod JARs; CrazyCrates 1.10.2.2; 11 client mod JARs |
| Embedded client ZIP | 61,196,028 bytes; `bd701f570a5fcf77595ec122f4b2e40701afac44c375237fb4e1cdbec26c8a0c` |

The embedded client selects Minecraft 1.12.2 and Forge 14.23.5.2859. Essential,
OptiFine and Solas remain in their original client payload. The source still
contains Core 0.3.6; writing update inputs to a separate directory does not
change that runtime.

## Historical pictures now have a verified migration contract

The actual historical Standards JAR registers `hvdungeonpictures:picture`.
Its separate entity name, `dungeon_picture`, was not the registry ID. It saves
an integer named `Village` in this exact order:

| Integer | Village |
| ---: | --- |
| 0 | mist |
| 1 | leaf |
| 2 | sand |
| 3 | cloud |
| 4 | stone |

Standards 0.3.1 restores that canonical ID and mapping. A distinct compatibility
subclass also accepts the authored 0.3.0 identifier. Existing current-schema
fields take precedence. Missing, malformed and out-of-range historical values
retain their opaque data as unassigned records rather than being coerced into
another village. Item identifiers and reviewed art bytes stay unchanged.

The new production JAR is 37,390 bytes, SHA-256
`accce5bbbfff4e1bacc17ce9e4604d40b0c8eb5ba1d6cb7cf44a7e21d121a077`.
It passes 388 component assertions and the same 601 native assertions on
Forge 14.23.5.2859 and the exact PC Magma base. Both processes exit 0. Each
loads the nine current production server mods and one private Standards
checker. These results do not rerun the earlier Core gameplay suite or load
the actual saved world. [Verification](../release/connected038-verification.json)

The exact PC Magma base is 7,056,996 bytes, SHA-256
`2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`.
Its bytes differ from the freshly downloaded upstream file with the same
version name. Eighteen payloads differ, so the native run uses the actual PC
base rather than treating the filename as an identity check.

## Concrete assembly handoff

Twenty verified payload files are present in the selected writable folder
under `Vale-Export/vale-038/inputs/parts`. They represent thirteen runtime and
bootstrap artifacts. Core's eight bounded pieces reassemble to the exact
previously verified JAR; the other twelve artifacts transfer whole. No private
QA JAR is part of these inputs.

The private reviewed contract binds the complete source inventory, original
and final server/client rosters, all artifact pieces, the prior embedded plan,
configuration and launcher identities. `prepare_connected_release.py` verifies
it before building a new candidate. It preserves all 56 prior retirement pins
and adds the three newly superseded mod filenames. It also pins CrazyCrates'
configuration, the final matching client and the existing server properties.

The preparation and release tools pass 111 Python tests, including full
synthetic assembly, wrong/corrupt piece refusal, unknown source rejection,
historical retirement preservation, and existing-output refusal. The Windows
wrappers call Python directly; they do not install software or change script
execution policy. [Tool contract](../tools/PREPARE-CONNECTED.md)

The final output parent is `D:\Codex\Vale-Export`. The connected browser can
transfer and inspect files, but it cannot execute the Windows assembly command.
The concrete command and contract therefore accompany the transferred inputs.
The assembly status remains `CANDIDATE_PENDING_ACCEPTANCE`; no completed real
candidate archive is asserted by this source checkpoint.

## Remaining acceptance

The next measured results must come from executing the complete assembly,
starting/saving/restarting `Naruto.test` on its final runtime, connecting the
matching graphical client, checking the original armor and Solas presentation,
and testing the hosted Bisect installation. The original Downloads ZIP was
rejected by the browser archive reader as `Ambiguous archive` and was left
unchanged; the complete existing stage supplies the reviewed assembly input.

The private file inventory, user world, third-party runtime binaries and
machine-specific contract remain outside this public source repository.
