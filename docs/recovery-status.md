# Recovery and release status

Evidence snapshot: **10 November 2026**. This page distinguishes the current source continuation from the previously located Windows packages. The [candidate verification](../release/candidate038-verification.json) records current measured results. The [component lock](../release/component-lock.json) preserves historical source evidence, expected runtime pins and observation scope.

**Release state: `CORE038_NATIVE_FORGE_MAGMA_PASSED_COMPLETE_PACKAGE_PENDING`.** The current source-built server candidates passed the same 656 native assertions on both runtimes, with nine owned server mods loaded together. The complete Windows stage, actual saved world, matching graphical client and hosted installation have not been accepted on these new artifacts. No final client/server download or overall completion percentage is established.

## Current continuation: 0.3.8

| Changed runtime | Bytes | SHA-256 |
| --- | ---: | --- |
| Core 0.3.8 | 1,943,933 | `06b183bbdd0a866ec039b7318fc1542717ecf138bfea5f1c62f76fce21d6ac01` |
| Rack 0.1.2 | 33,808 | `bf259025e35cc3431f6d17ee207d251898a4a9472637b0f96f556e6f3ec1d123` |
| Field HUD 0.14.1 | 182,952 | `6b9c7153ecc1c03b25b645a59f0cd81e75b61ea839accee444cdcabe4315736e` |
| Village Standards 0.3.0 | 36,584 | `3eb99f74a0586bb08a75758632c9b4400adfd5edc80b008ed58917239d91c76e` |

The new playable work comprises Crescent Talons with protected lock-on, rear attacks, a dodgeable one-second lunge and bounded bleed; saved companion orders and allied support; unloaded recall and stale-actor rejection; six-slot quick stow/equip with exact item metadata; physical-order projectile contacts; and a complete original five-picture hanging runtime. [Controls and behavior](gameplay-038.md)

The final native suite passed **656 assertions on each runtime**, divided into 86 retained recovery checks, 48 companion checks, 82 claw/projectile checks, 78 rack checks and 362 picture checks. Both processes exited 0. The same nine production server JARs and two private QA JARs ran on Forge 14.23.5.2859 and Magma 8cc35ed/embedded Forge 14.23.5.2860. The Magma run uses the pinned upstream runtime directly; the earlier wrapper result below belongs to 0.3.7. These are 656 assertions repeated on two runtimes, not 1,312 distinct scenarios. The fixture still uses a marked disposable world and synthetic native players. [Version-bound evidence](../release/candidate038-verification.json)

The complete source build checks eleven areas and 36 declared artifacts. The release/bootstrap tooling passes all 91 Python tests without skips. New component suites include 23 claw/packet assertions, 36 companion state assertions and 341 Standards geometry/persistence assertions. Production archives exclude private QA and upstream classes. Standards resources remain byte-identical to their reviewed recovered inputs.

Village Standards 0.3.0 is a new complete implementation. Its five known item IDs are preserved; the old binary's actual entity registry and custom variant schema are still unverified. Unknown data reaching the new entity remains visible, breakable and recoverable, but an old entity with another ID cannot be migrated by that mechanism alone. The actual-world replacement decision remains open. [Runtime and migration contract](../modules/village-standards/README.md)

The Windows File Link sessions remain disconnected, with saved protocol 1 clients against a protocol 2 service. No PC mutations or real-stage assembly occurred. The original archive and rollback remain preserved. A local graphical probe also could not establish a display in this workspace, so no new in-game rendering or Solas acceptance is claimed.

## Previous continuation: 0.3.7

The following results describe the earlier published checkpoint and remain unchanged historical evidence. Its overlay and artifact counts are superseded by the current 0.3.8 section above.

Core 0.3.7 is 1,911,478 bytes, SHA-256:

```text
ccb1e9b001e4beb416d6e9e06c56ecce659bd02c34f0c9d4280c6479ec0688b4
```

Its five fixes address certification of delayed crafting results, persistent specialist cooldowns, rejoining one's existing expedition, closing out-of-range companion deployments, and diagnostics for the current 555-item registry. Residents' academy and quartermaster dialogue now explains the six separate weapon storage slots and the paid delivery queue.

The exact Core passed **the same 86 assertions on a real Forge 1.12.2 / 14.23.5.2859 dedicated server and on Magma 1.12.2-8cc35ed**, whose embedded Forge reports 14.23.5.2860. Both processes exited successfully. The isolated native fixture exercises result-slot crafting, actual specialist weapon use, player NBT save/logout/reload, expedition charter use, companion lifecycle, 36 eligible Spirit provisions, paid purchases and full-inventory receipt replay without duplicate rewards. It uses a synthetic player with a native server handler and a disposable `vale-recovery-test` world. It does not connect a graphical client or load `Naruto.test`. The separate persistent-state component suite passes 23 assertions. [Exact evidence and scope](../release/candidate037-verification.json)

The Magma check used the real upstream 8cc35ed JAR plus the final owned bootstrap wrapper and an isolated fixture plan. All unowned upstream file payloads are preserved. A subsequent `--vale-upgrade-check-only` also passed and revalidated the runtime fingerprints. This is bootstrap/native fixture evidence; it is not a test of the complete PC plugin/mod roster or a second saved-world gameplay session.

The source workspace now has portable dependency-ordered builds. All eleven source areas and 34 declared artifacts passed; the source manifest covers 1,483 files and remained unchanged during the run. The Python release/bootstrap suite passed 91 tests. The outputs include production mods, separate compatibility adapters, an owned bootstrap wrapper library and the Village Standards art/metadata overlay. The overlay lacks the six unavailable renderer Java files and cannot replace the existing Village Standards mod. Rebuilt candidates whose hashes differ from the historical lock retain their new identities; they are not described as byte-identical recoveries. [Build instructions and artifact roles](build-modules.md)

The revised bootstrap binds installation markers to the embedded plan, revalidates runtime JARs on every start, rejects extra or reintroduced retired runtime JARs, preserves checked rollback files and requires launch from the server's own folder. The full assembler copies the selected complete source into a new destination, verifies all saved-world bytes, updates the matching nested client, pins changed configuration including CrazyCrates, and removes stale acceptance receipts from the candidate runtime. Its successful status is always `CANDIDATE_PENDING_ACCEPTANCE`. [Assembly procedure](../tools/ASSEMBLY.md)

Both remembered Windows File Link sessions are disconnected at this snapshot. The browser link also needs protocol 2 for complete export operations. No current Windows files have been changed or exported during this continuation. Reconnect the complete source folders and the intended writable destination before generating the real assembly plan; the previously incomplete extraction cannot substitute for the complete stage or original archive.

## Existing package and later candidate

### Core 0.3.5 package

The existing Downloads ZIP is 3,047,208,198 bytes. Its observed central directory contains 2,176 entries with 6,019,099,153 declared expanded bytes. Its active server roster has eight mod JARs, including `vale-orders-0.3.5.jar`, and it includes its own `client-install/Client.zip`.

The world folder is `Naruto.test`: its directory records 1,294 overworld terrain region files and six Nether region files. The recovered `level.dat` entry was read with CRC validation. All world regions have not been revalidated by this recovery. The preserved server properties entry names that world. These observations establish the located world and selected entry integrity; they do not establish a complete new world load test. [Lock: `downloads035`](../release/component-lock.json)

The current archive's recorded whole-file SHA-256 is:

```text
52d8a14a8511a9c9f5b9f638c65612e33ade8f5c7cf13fd79b21b654f993e1ca
```

That value came from a recovered record. The entire current archive was not rehashed for this summary. The separate observed central-directory hash has a narrower scope and must never be substituted for the whole-file hash.

The CRC-verified historical `release/VERIFICATION.json` records 1,134 scroll checks, 98 arsenal checks, and actual-world first-start/restart results. The scroll checks used dedicated fixtures with fake players. The same receipt explicitly leaves hosted Bisect connection and Solas GPU verification false. These earlier results belong to their recorded artifacts.

### Core 0.3.6 candidate

The later local stage contains nine server mod JARs. It replaces Core 0.3.5 with Core 0.3.6 and adds Encounters 0.3.6. Its candidate client ZIP is 61,196,028 bytes, with 19 directory entries and 11 mod JAR entries. The client manifest specifies Minecraft 1.12.2 and Forge 14.23.5.2859. [Lock: `stage036`](../release/component-lock.json)

| Component | Recorded expected SHA-256 |
| --- | --- |
| Core 0.3.5 in existing package | `4c95104a7df81a0f5e2fd16e4a9b19f54a8e6e921a8f9a8a0cdd39f5ca010a20` |
| Core 0.3.6 in later stage | `54879901cc9e5caed778ef3cf4ff7034d1951adad7dc7a94ee82466b78762acc` |
| Encounters 0.3.6 in later stage | `83a2e89c51a7fba159371d4fbf91fce01803ad4233067388e8fcb900bdf2ba68` |
| Later Magma bootstrap | `32fed477ef1c2b773d1c18764ddb5d1a79b530aba6ec982e9113b5384be40f9d` |

The lock contains the authoritative full pin set. The table above is a reading aid; none of these recorded values is a newly computed runtime payload hash from this documentation work.

The candidate's six shared modules are Core, Encounters, Rack, Residents, world compatibility, and Village Standards. Their recorded client/server pins match. The current complete JAR bytes still need to be hashed and compared. Directory entries and matching recorded pins do not establish runtime side support, dependencies, or network compatibility.

The additional candidate client entries are HUD 0.14.0, Controls 1.4.0 simple, client lifecycle compatibility 0.1.0, Essential 1.5.1, and OptiFine G5. Solas 3.9 is a separate shader archive. The damage compatibility module and the two server bridges appear on the server roster and are absent from the candidate client directory. The lock preserves the actual filenames and expected digests for all entries.

## Source and build status

The complete Core 0.3.6 source is recovered: 1,218 unchanged files, including 118 production Java files, nine test Java files and 1,087 resources. The source ZIP SHA-256 is `8ab84d16f8c810f339ed40f5a1c05a00f7a6d3da53754b1fc410f16403176f75`. Its `check reobfJar sourcesJar` build passed all ten custom verification tasks and reproduced the recorded production JAR SHA-256 exactly. This is a fresh source-to-binary reproduction; it does not mean the current PC runtime file was freshly rehashed or that the saved world ran. [Build instructions](build-core.md)

The public workspace contains eleven reviewed source areas with recovery provenance. Core 0.3.6 remains available at baseline commit `ffa7d0a9f048f8fb2279ba4729a64f2fb6280701`; the current working source is 0.3.8. The current candidate result is described above and does not retroactively change the historical 0.3.6 receipts.

Recovered server records target Java 8 and a named Magma 1.12.2 bootstrap. The bootstrap changed after the earlier staged startup receipts. Both bootstrap filenames, the final Core, Encounters, and the matching client must be included in the next exact-artifact check. A version filename is insufficient to choose between historical candidates.

## Historical assembly gaps and current fixes

The historical Core 0.3.6 class-comparison receipt reports 219 unchanged production classes and one changed class, `village/vale/Quartermaster.class`. It corrected the Spirit provision page to enumerate original armor. That old receipt explicitly left supplemental native acceptance pending. The version-bound 0.3.7 native runs verified the Spirit menu and paid-delivery behavior; the 0.3.8 suite retains those checks. The historical receipt itself remains unchanged.

The class comparison's native gameplay base hash is `75aa6d67928c7208c0791afa69144d60f72b3ddae2de9fdef07d1f6f13d3305a`. Its final Core hash is the staged `54879901…` pin. A generic `passed` field in this comparison is evidence of the comparison result, not of the pending gameplay check.

The staged first-start and restart receipts precede both the final Core JAR and the changed bootstrap. The staged verification and payload hash documents also predate final assembly. New final-artifact results and refreshed package evidence are required.

`plugins/CrazyCrates/config.yml` was modified by the recovered assembler but omitted from its expected pins. The new assembler requires that pin when the file exists and requires every changed configuration to be declared. The actual eight staged crate definitions, configured dialogue/actions, world destinations and launcher expectations still need reconciliation with the final current files.

The old recovered assembler moved files to deterministic backup names and replaced nested archives without an atomic transaction. The new tooling builds a separate candidate, refuses existing output destinations, checks source and output inventories and binds the bootstrap's pending/done markers to its exact plan. Real stage assembly and acceptance remain unperformed while PC access is disconnected. [Historical pending gates](../release/component-lock.json)

## Coverage and scope

| Recovered metric | Value | Meaning |
| --- | ---: | --- |
| Item reference rows | 485 | Reference inventory denominator |
| Rows with registered counterparts | 461 | Explicit item counterpart coverage |
| Distinct counterpart targets | 353 | Several reference rows may share one canonical replacement |
| Unmapped rows | 24 | Still open |
| Counterpart coverage | 95.1% | A registry coverage metric |
| Overall gameplay completion | Unknown | No supported aggregate percentage |
| Bounded ability audit | 95 reference items / 324 obligations | A partial mechanics audit |
| Missing / untested ability rows in that audit | 217 / 52 | Open baseline findings |

The historical visual receipt states that new visuals were deferred with shared valid placeholders. The new talon geometry and Standards renderer are additional source work; actual graphical acceptance remains open. Complete ability behavior, original art, animation, quest equivalents, and multiplayer balancing need their own evidence. The 320 legacy entity IDs are classification records: projectiles, effects, temporary controllers, icons, NPCs, summons, and actual living opponents must remain distinct. This repository snapshot does not assert that the complete entity classification manifest has been recovered.

The HUD and Rack lineage reports six server-backed weapon storage slots; preserve that later lineage instead of reverting to the earlier shortcut rail. Final validation must cover transfers, full-inventory behavior, persistence, reconnect, and Controls/HUD input contexts using the accepted client and server.

Residents' recovered scope covers the academy, contracts, and quartermaster roles with editable dialogue and operator placement. Broader legacy NPC APIs, furniture, carpentry, and hosted-data migration remain separate requirements. Solas configuration presence likewise leaves final GPU appearance pending.

## Remaining release gates

These are reconciliation gates G01–G08. They summarize the open work; the release tool's own acceptance schema may use additional identifiers.

| Gate | Current result and remaining requirement |
| --- | --- |
| G01 — Exact identities | Current source-build outputs are hashed. The full Windows server/client roster, both final bootstrap aliases and nested client still need fresh hashes and reconciliation. |
| G02 — Quartermaster | Current Core passes isolated native Forge and Magma Spirit eligibility, purchase, owner binding, queued delivery and duplicate-prevention checks. Retain this exact hash and verify the final configured server package. |
| G03 — Configuration | Bootstrap/assembler checks and Residents guidance are implemented. Actual staged crates, configured actions, destinations and all final configuration pins remain to verify. |
| G04 — World lifecycle | Still requires first-start, save and restart of the actual `Naruto.test` world on the exact final server, with saved records checked. |
| G05 — Matching client | Still requires a real client connection and bounded progression, weapons, scrolls, rack, dialogue, armor and input checks. |
| G06 — Visuals | Still requires icon/model/armor and Solas inspection on the final graphical client. Static resource checks do not establish appearance. |
| G07 — Final package | Assembly machinery is checked; the real complete package has not been assembled or promoted. It still requires refreshed evidence, complete archive validation and matching client/world. |
| G08 — Hosted acceptance | Still requires Bisect start/restart and actual matching-client connection evidence. |

Local acceptance can support `READY_FOR_HOSTED_TEST`. A successful hosted connection requires separate evidence; it cannot be inferred from compilation, unit checks, or local fake-player fixtures.

## Publication and provenance

Initial repository intake found commit `f0625f3cdc559bf771ab72a0db95a9ccdee6a31a`, containing a single 22-byte README and no releases. The recovered Core 0.3.6 source baseline was subsequently published as `ffa7d0a9f048f8fb2279ba4729a64f2fb6280701`. This continuation builds on that baseline; neither source publication is a full runtime release.

The public workspace is intended for selected technical metadata and reviewed source/tooling. Keep full server backups, private saved-player records, credentials, reference decompilations, private preview worlds, and approval-held artwork outside public publication. Preserve the existing local server state while reviewing a separate distributable contents list. Component names and hashes establish identity, not redistribution permission or legal clearance.

The [component lock](../release/component-lock.json) is an identity ledger with explicit unknowns. It is not an approval to install the candidate, a substitute for native results, or an assertion that a final download exists.
