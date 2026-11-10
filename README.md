# Hidden Block Village — Vale

Vale is a Minecraft 1.12.2 ninja simulation project with original gameplay systems, a matching Forge client, and a Magma server package intended for Bisect Hosting. This workspace brings the recovered component identities, source recovery work, and release checks into one place.

**Current state: Core 0.3.8 and the integrated server modules pass 656 native checks on both Forge and Magma.** This continuation adds a playable Crescent Talons weapon, persistent companion orders and allied support, quick stow/equip for all six rack slots, projectile fixes, and a complete original Village Standards runtime. The actual Windows stage, saved world, graphical client and complete Bisect export still require acceptance. [Gameplay and controls](docs/gameplay-038.md) · [Recovery status](docs/recovery-status.md)

## Start here

| Resource | Purpose |
| --- | --- |
| [Recovery status](docs/recovery-status.md) | Distinguishes the existing package, later candidate, historical evidence, and remaining acceptance work. |
| [Core build instructions](docs/build-core.md) | Reproduce the recovered Core source with the pinned wrapper and correct Java toolchains. |
| [All component builds](docs/build-modules.md) | Build the recovered source areas in dependency order, inspect artifact roles and retain exact source/output hashes. |
| [Candidate assembly](tools/ASSEMBLY.md) | Build a new full server/client candidate from the complete frozen input while preserving the original world and rollback. |
| [Core 0.3.8 verification](release/candidate038-verification.json) | Exact source/artifact identities and both 656-assertion native results. |
| [Gameplay and controls](docs/gameplay-038.md) | New claw, companion orders, quick rack transfers, projectile changes and wall pictures. |
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

These two runtime snapshots describe the recovered PC files. The new source-built Core is `vale-orders-0.3.8.jar`, SHA-256 `06b183bbdd0a866ec039b7318fc1542717ecf138bfea5f1c62f76fce21d6ac01`; it has not been installed into that Windows stage.

The candidate client manifest targets **Minecraft 1.12.2, Forge 14.23.5.2859**. Recovered server records target **Java 8** and `Magma-1.12.2-8cc35ed-server.jar`. Keep each server snapshot paired with its matching client. The isolated Core fixture is narrower evidence than running the complete saved-world package. [Component lock](release/component-lock.json)

The historical staged candidate includes the HUD 0.14.0 and Rack 0.1.1 lineage for six separate weapon storage slots, Controls 1.4.0 simple, Residents 0.2.0 with its server bridge, and Solas 3.9 configuration. Their presence and recorded identities are established at the scope described in the lock. Final input, persistence, networking, and graphics checks remain open. [Component and acceptance detail](docs/recovery-status.md)

## What the coverage number means

The recovered item receipt reports **461 of 485 reference item rows with registered counterparts (95.1%)**, resolving to **353 distinct targets**, with **24 unmapped rows**. This measures item counterpart coverage. An overall gameplay completion percentage has not been established.

A separate, bounded audit of 95 reference items records 324 ability obligations, including 217 missing and 52 untested rows. The visual receipt also marks new visuals as deferred. These gaps remain visible during recovery; registry mappings alone do not close them. [Coverage scope](docs/recovery-status.md#coverage-and-scope)

## Development and release work

The Core 0.3.6 source and its recorded byte-identical reproduction remain in the baseline history. This continuation builds Core 0.3.8 using Gradle 9.5.0, Java 25 for Gradle and an Azul Java 8 Minecraft toolchain. The current changes preserve a reviewable diff from that baseline. See [the portable build instructions](docs/build-core.md).

Core 0.3.8 has 556 registered items, including 27 specialists. The new claw supports a server-chosen lock, a dodgeable lunge, rear attacks and bounded bleed. Companions retain Assist, Follow, Guard and Hold orders; the medic treats allies and refuses attack commands. Rack 0.1.2 and HUD 0.14.1 add direct stow/equip behavior with full-inventory and charging safeguards. Village Standards 0.3.0 provides original placement, persistence, recovery and rendering code for the five known picture items.

All eleven reviewed source areas and their 36 declared artifacts passed the integrated build. The Python release/bootstrap suite passed 91 tests. The same 656 native assertions passed on Forge 14.23.5.2859 and Magma 1.12.2-8cc35ed with all nine owned server mods and two separate private QA mods loaded. The latter runtime embeds Forge 14.23.5.2860. These runs use a disposable world and synthetic players; the actual save, full external plugin roster, graphical client and hosted installation remain separate. [Exact evidence](release/candidate038-verification.json)

Village Standards is now a complete newly authored runtime rather than the earlier resource overlay. Its known item IDs and recovered art bytes are retained, while the old binary's entity ID/custom NBT migration remains to verify on a real historical save. The bootstrap output is still an owned wrapper library that contains no Magma distribution. [Build scope](docs/build-modules.md)

The next release sequence is to reconcile the current complete Windows stage, bind the new source-built artifacts and configuration in a reviewed plan, validate the matching client and saved world, refresh package evidence, and produce one verified server ZIP with its matching client. The assembler preserves original inputs, checks the whole copied world, and labels its output `CANDIDATE_PENDING_ACCEPTANCE`. Hosted start, restart, and client connection remain separate acceptance steps. [Open gates](docs/recovery-status.md#remaining-release-gates)

For the Python release tooling, the local help command is:

```sh
python tools/release_pack.py --help
```

Tool success is evidence about the files or operation checked. Minecraft startup, graphics, progression, and multiplayer behavior require their own version-bound results.

## Repository contents and distribution

This public workspace is for reviewed original source, documentation, small manifests, and release tooling. Runtime archives, world saves, credentials, private player records, local backups, reference decompilations, and artwork awaiting approval stay outside Git. The ignore rules support this boundary; every publication still requires a contents review.

The existing world remains part of the local server recovery objective. Preserving a server's saved state and publishing a public download are separate operations. Any eventual large release must identify the exact source commit, package and component hashes, installation instructions, and the evidence supporting its release state.

Legacy item and entity identifiers serve as compatibility and mechanical reference records. Original art, geometry, symbols, names, and dialogue must be authored independently. A component pin or recovered license file is not a blanket redistribution permission or legal clearance statement.
