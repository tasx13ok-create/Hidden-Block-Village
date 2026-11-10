# Hidden Block Village — Vale

Vale is a Minecraft 1.12.2 ninja simulation project with original gameplay systems, a matching Forge client, and a Magma server package intended for Bisect Hosting. This workspace brings the recovered component identities, source recovery work, and release checks into one place.

**Current state: the exact Core 0.3.6 build is recovered; final package acceptance is in progress.** The latest located server candidate is Core 0.3.6 with Encounters 0.3.6. The existing Downloads package still contains Core 0.3.5. Promotion of the later candidate, a verified final download, and a hosted client connection remain pending. [Recovery status](docs/recovery-status.md)

## Start here

| Resource | Purpose |
| --- | --- |
| [Recovery status](docs/recovery-status.md) | Distinguishes the existing package, later candidate, historical evidence, and remaining acceptance work. |
| [Core build instructions](docs/build-core.md) | Reproduce the recovered Core source with the pinned wrapper and correct Java toolchains. |
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

The candidate client manifest targets **Minecraft 1.12.2, Forge 14.23.5.2859**. Recovered server records target **Java 8** and `Magma-1.12.2-8cc35ed-server.jar`. These are the recovered targets; execution against the exact final artifacts still needs validation. Keep each server snapshot paired with its matching client. [Component lock](release/component-lock.json)

The candidate includes the HUD 0.14.0 and Rack 0.1.1 lineage for six separate weapon storage slots, Controls 1.4.0 simple, Residents 0.2.0 with its server bridge, and Solas 3.9 configuration. Their presence and recorded identities are established at the scope described in the lock. Final input, persistence, networking, and graphics checks remain open. [Component and acceptance detail](docs/recovery-status.md)

## What the coverage number means

The recovered item receipt reports **461 of 485 reference item rows with registered counterparts (95.1%)**, resolving to **353 distinct targets**, with **24 unmapped rows**. This measures item counterpart coverage. An overall gameplay completion percentage has not been established.

A separate, bounded audit of 95 reference items records 324 ability obligations, including 217 missing and 52 untested rows. The visual receipt also marks new visuals as deferred. These gaps remain visible during recovery; registry mappings alone do not close them. [Coverage scope](docs/recovery-status.md#coverage-and-scope)

## Development and release work

The complete Core 0.3.6 source has been recovered and built successfully with Gradle 9.5.0, Java 25 for Gradle and an Azul Java 8 Minecraft toolchain. All ten custom verification tasks passed, and the 1,907,890-byte production JAR exactly matches the recorded staged SHA-256 `54879901cc9e5caed778ef3cf4ff7034d1951adad7dc7a94ee82466b78762acc`. See [the portable build instructions](docs/build-core.md).

Eleven reviewed source modules are included under `modules/`, with per-file recovery provenance. Some companion bundles still require external compile inputs; their source presence is not a claim that every companion was rebuilt. A separate 0.3.7 candidate fixes five concrete crafting, cooldown, expedition, companion and diagnostic defects and has passed its component build; native acceptance is in progress. This first recovery commit retains the exact 0.3.6 baseline so later fixes have a reviewable history.

The release sequence is to freeze and hash the exact candidate, close its final native acceptance gaps, validate the matching client and saved world, refresh the package evidence, and produce one verified server ZIP with its matching client. Hosted start, restart, and client connection are separate acceptance steps. [Open gates](docs/recovery-status.md#remaining-release-gates)

For the Python release tooling, the local help command is:

```sh
python tools/release_pack.py --help
```

Tool success is evidence about the files or operation checked. Minecraft startup, graphics, progression, and multiplayer behavior require their own version-bound results.

## Repository contents and distribution

This public workspace is for reviewed original source, documentation, small manifests, and release tooling. Runtime archives, world saves, credentials, private player records, local backups, reference decompilations, and artwork awaiting approval stay outside Git. The ignore rules support this boundary; every publication still requires a contents review.

The existing world remains part of the local server recovery objective. Preserving a server's saved state and publishing a public download are separate operations. Any eventual large release must identify the exact source commit, package and component hashes, installation instructions, and the evidence supporting its release state.

Legacy item and entity identifiers serve as compatibility and mechanical reference records. Original art, geometry, symbols, names, and dialogue must be authored independently. A component pin or recovered license file is not a blanket redistribution permission or legal clearance statement.
