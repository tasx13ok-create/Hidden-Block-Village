# Hidden Block Village — Vale

Vale is a Minecraft 1.12.2 ninja simulation project with original gameplay systems, a matching Forge client, and a Magma server package intended for Bisect Hosting. This workspace brings the recovered component identities, source recovery work, and release checks into one place.

**Current source: Core 0.3.9 fixes scroll callback accounting, protected return and escape movement, and stale ward-armor animation state.** Its dedicated Forge fixture checks the exact production JARs in a disposable world. Complete Windows assembly, the actual saved world, graphical client and Bisect acceptance remain pending. The earlier Core 0.3.8 handoff remains a separate PC input set; a fresh 0.3.9 contract and output are required. [Current verification](release/candidate039-verification.json) · [Gameplay changes](docs/gameplay-039.md) · [Recovery status](docs/recovery-status.md)

## Start here

| Resource | Purpose |
| --- | --- |
| [Current cross-PC handoff](docs/current-handoff.md) | Latest observed results, blockers and next steps. Read [standing instructions](AGENTS.md) before continuing work on any PC. |
| [Recovery status](docs/recovery-status.md) | Distinguishes the existing package, later candidate, historical evidence, and remaining acceptance work. |
| [Core build instructions](docs/build-core.md) | Reproduce the recovered Core source with the pinned wrapper and correct Java toolchains. |
| [All component builds](docs/build-modules.md) | Build the recovered source areas in dependency order, inspect artifact roles and retain exact source/output hashes. |
| [Candidate assembly](tools/ASSEMBLY.md) | Build a new full server/client candidate from the complete frozen input while preserving the original world and rollback. |
| [Client and Bisect installation](docs/client-and-bisect-039.md) | Contents and installation steps for a completed matching Core 0.3.9 candidate, including the preserved world and separate hosting test. |
| [Windows assembly status](docs/windows-assembly-status.md) | Observe the current assembly in a second window without interrupting it or rehashing the saved world. |
| [Connected-stage preparation](docs/connected-stage-038.md) | Recorded PC inventory, prior 0.3.8 transfer, historical picture compatibility and the existing handoff. |
| [Connected preparation tools](tools/PREPARE-CONNECTED.md) | Verify bounded transfer pieces and an independently reviewed source contract before calling the complete assembler. |
| [Core 0.3.9 verification](release/candidate039-verification.json) | Exact current source/artifact identities, component checks, Forge native result and outstanding acceptance. |
| [Native Forge checks](docs/native-checks.md) | Reproduce the private fixture from a successful source build; CI retains separate native logs and results. |
| [Gameplay and controls](docs/gameplay-039.md) | Current scroll and ward changes, with links to the retained claw, companion, rack and picture controls. |
| [Component lock](release/component-lock.json) | Exact recovered filenames, byte counts, expected SHA-256 pins, evidence scope, and unresolved checks. |
| [Release tools](tools/release_pack.py) | Local archive inventory and transport tooling. Run `python tools/release_pack.py --help` for its current commands. |
| [Tool tests](tests/test_release_pack.py) | Checks for the release tooling; these do not exercise Minecraft gameplay or establish hosting compatibility. |

## Runtime snapshots

| Area | Existing Downloads package | Later staged candidate |
| --- | --- | --- |
| Core | `vale-orders-0.3.5.jar` | `vale-orders-0.3.6.jar` |
| Encounters | Absent from the active server mod roster | `vale-encounters-0.3.6.jar` |
| Server mod entries | 8 | 9 |
| Matching client | Its own enclosed `client-install/Client.zip`; complete nested runtime inventory still to verify | 11 mod JAR entries observed; six shared modules have matching recorded server/client pins |
| Whole package | 3,047,208,198 compressed bytes; 6,019,099,153 declared expanded bytes | Final complete archive pending |
| Release state | Historical receipt: `READY_FOR_HOSTED_TEST` | Final acceptance pending; not promoted |

These two runtime snapshots describe the recovered PC files. The new source-built Core is `vale-orders-0.3.9.jar`; its exact hash is in the [current verification record](release/candidate039-verification.json). A completed Windows candidate using those new bytes has not been observed. The original stage, original archive and incomplete outputs remain separate.

The candidate client manifest targets **Minecraft 1.12.2, Forge 14.23.5.2859**. Recovered server records target **Java 8** and `Magma-1.12.2-8cc35ed-server.jar`. Keep each server snapshot paired with its matching client. The isolated Core fixture is narrower evidence than running the complete saved-world package. [Component lock](release/component-lock.json)

The historical staged candidate includes the HUD 0.14.0 and Rack 0.1.1 lineage for six separate weapon storage slots, Controls 1.4.0 simple, Residents 0.2.0 with its server bridge, and Solas 3.9 configuration. Their presence and recorded identities are established at the scope described in the lock. Final input, persistence, networking, and graphics checks remain open. [Component and acceptance detail](docs/recovery-status.md)

## What the coverage number means

The recovered item receipt reports **461 of 485 reference item rows with registered counterparts (95.1%)**, resolving to **353 distinct targets**, with **24 unmapped rows**. This measures item counterpart coverage. An overall gameplay completion percentage has not been established.

A separate, bounded audit of 95 reference items records 324 ability obligations, including 217 missing and 52 untested rows. The visual receipt also marks new visuals as deferred. These gaps remain visible during recovery; registry mappings alone do not close them. [Coverage scope](docs/recovery-status.md#coverage-and-scope)

## Development and release work

The Core 0.3.6 source and its recorded byte-identical reproduction remain in the baseline history. This continuation builds Core 0.3.9 using Gradle 9.5.0, Java 25 for Gradle and an Azul Java 8 Minecraft toolchain. The current changes preserve a reviewable diff from that baseline. See [the portable build instructions](docs/build-core.md).

Core 0.3.9 retains 556 registered items, including 27 specialists. Crescent Talons supports a server-chosen lock, a dodgeable lunge, rear attacks and bounded bleed. Companions retain Assist, Follow, Guard and Hold orders; the medic treats allies and refuses attack commands. Rack 0.1.2 and HUD 0.14.1 provide direct stow/equip behavior with full-inventory and charging safeguards. Village Standards 0.3.1 provides original placement, persistence, recovery and rendering code for the five known picture items, with the verified historical entity ID and integer village mapping.

The 0.3.9 scroll changes reserve the full resolve cost before native callbacks, prevent recursive casts, retain independent callback state and held-item changes, and refund failed effects to the account actually debited. Waymark Step and Reed Escape honor Forge teleport protection and verify actual movement. Ward armor now treats its first or stale network snapshot as a baseline and resets stale wing interpolation. The ward renderer is separate from the recovered `hvbijuu` Matatabi/tail renderer; this repository update does not replace that legacy mod. [Behavior and limits](docs/gameplay-039.md)

The complete current source build covers eleven areas and 36 declared artifacts. The Forge native fixture binds nine production mods and two private QA mods to the checked build, validates exact loaded hashes and completion labels, and removes its disposable runtime. The new CI step reproduces that procedure after source builds. The upstream Magma comparison stopped during dependency setup with HTTP 403, before mod loading; it establishes no current Magma gameplay result. [Current evidence](release/candidate039-verification.json) · [Native runner](docs/native-checks.md)

Historical Core 0.3.8 evidence remains separate: all eleven source areas and 36 artifacts passed, its release/bootstrap suite passed 91 tests, and the same 656 native assertions passed on Forge 14.23.5.2859 and upstream Magma 1.12.2-8cc35ed. Magma embeds Forge 14.23.5.2860. Those runs used a disposable world and synthetic players; they do not establish a Magma result for Core 0.3.9. [Historical evidence](release/candidate038-verification.json)

Village Standards is a complete newly authored runtime. Read-only inspection of the actual old PC binary established its `hvdungeonpictures:picture` entity ID and exact `Village` integer mapping. Version 0.3.1 accepts that format and the exact identifier published by 0.3.0, retaining malformed records for recovery. It passes 388 component assertions and the same 601 native assertions on Forge and the actual PC Magma base. The preparation and existing release tools pass 111 Python tests. These later results are separate from the earlier 656-check gameplay suite. The bootstrap output remains an owned wrapper library containing no Magma distribution. [Current evidence](release/connected038-verification.json)

The next release sequence is to obtain the current Windows assembly status, use the fixed reviewed inventory with a fresh 0.3.9 handoff, and assemble the complete server and matching client in a new output directory. The assembler preserves original inputs, checks the whole copied world, reports bounded progress, and labels successful output `CANDIDATE_PENDING_ACCEPTANCE`. Saved-world start/save/restart, graphical play and hosted connection remain separate acceptance steps. [Open gates](docs/recovery-status.md#remaining-release-gates)

For the Python release tooling, the local help command is:

```sh
python tools/release_pack.py --help
```

Tool success is evidence about the files or operation checked. Minecraft startup, graphics, progression, and multiplayer behavior require their own version-bound results.

## Repository contents and distribution

This public workspace is for reviewed original source, documentation, small manifests, and release tooling. Runtime archives, world saves, credentials, private player records, local backups, reference decompilations, and artwork awaiting approval stay outside Git. The ignore rules support this boundary; every publication still requires a contents review.

The existing world remains part of the local server recovery objective. Preserving a server's saved state and publishing a public download are separate operations. Any eventual large release must identify the exact source commit, package and component hashes, installation instructions, and the evidence supporting its release state.

Legacy item and entity identifiers serve as compatibility and mechanical reference records. Original art, geometry, symbols, names, and dialogue must be authored independently. A component pin or recovered license file is not a blanket redistribution permission or legal clearance statement.
