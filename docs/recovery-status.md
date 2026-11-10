# Recovery and release status

Evidence snapshot: **10 November 2026**. This page is a public summary of the recovered client/server reconciliation and repository intake. The companion [component lock](../release/component-lock.json) records the source evidence document hashes, exact component pins, and the scope of each observation without exposing private recovery locations.

**Release state: `CORE036_STAGED_NOT_PROMOTED_FINAL_ACCEPTANCE_PENDING`.** The later source and runtime candidate still needs final acceptance before it can replace the existing complete package. No overall completion percentage or final client/server download is established by this snapshot.

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

The public workspace contains eleven reviewed source modules with recovery provenance. A separate 0.3.7 source candidate addresses delayed crafting issuance, specialist cooldown persistence, expedition rejoin, companion distance closure and stale registry self-tests. Its eleven custom component tasks pass; native checks remain in progress. The recovered 0.3.6 module is kept intact in this baseline commit for a clear subsequent fix history.

Recovered server records target Java 8 and a named Magma 1.12.2 bootstrap. The bootstrap changed after the earlier staged startup receipts. Both bootstrap filenames, the final Core, Encounters, and the matching client must be included in the next exact-artifact check. A version filename is insufficient to choose between historical candidates.

## Final acceptance gap

The final Core class-comparison receipt reports 219 unchanged production classes and one changed class, `village/vale/Quartermaster.class`. The changed class corrects the Spirit provision page to enumerate original armor instead of looking up absent retired armor content. Its **supplemental native acceptance is explicitly `PENDING`**.

The class comparison's native gameplay base hash is `75aa6d67928c7208c0791afa69144d60f72b3ddae2de9fdef07d1f6f13d3305a`. Its final Core hash is the staged `54879901…` pin. A generic `passed` field in this comparison is evidence of the comparison result, not of the pending gameplay check.

The staged first-start and restart receipts precede both the final Core JAR and the changed bootstrap. The staged verification and payload hash documents also predate final assembly. New final-artifact results and refreshed package evidence are required.

`plugins/CrazyCrates/config.yml` was modified by the recovered assembler but omitted from its expected pins. That configuration must join the final verification set. Eight staged crate definitions, dialogue schema and actions, world destinations, and launcher expectations must resolve against the accepted registries.

The recovered assembler also moves files to deterministic backup names and replaces nested archives without an atomic transaction. Resume from the actual stage using a reviewed recoverable procedure; a blind repeat can damage the intermediate state. [Lock: pending acceptance gates](../release/component-lock.json)

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

The visual receipt states that new visuals were deferred with shared valid placeholders. Complete ability behavior, original art, animation, quest equivalents, and multiplayer balancing need their own evidence. The 320 legacy entity IDs are classification records: projectiles, effects, temporary controllers, icons, NPCs, summons, and actual living opponents must remain distinct. This repository snapshot does not assert that the complete entity classification manifest has been recovered.

The HUD and Rack lineage reports six server-backed weapon storage slots; preserve that later lineage instead of reverting to the earlier shortcut rail. Final validation must cover transfers, full-inventory behavior, persistence, reconnect, and Controls/HUD input contexts using the accepted client and server.

Residents' recovered scope covers the academy, contracts, and quartermaster roles with editable dialogue and operator placement. Broader legacy NPC APIs, furniture, carpentry, and hosted-data migration remain separate requirements. Solas configuration presence likewise leaves final GPU appearance pending.

## Remaining release gates

These are reconciliation gates G01–G08. They summarize the open work; the release tool's own acceptance schema may use additional identifiers.

| Gate | Required result |
| --- | --- |
| G01 — Exact identities | Full hashes for all final server/client runtimes, both bootstrap filenames, and nested client files; all discrepancies resolved. |
| G02 — Quartermaster | A successful supplemental native result for Spirit provisions on the exact final Core and relevant configuration. |
| G03 — Configuration | Resolved registry/action/resource references; nine server mod entries, eight crates, dialogue, destinations, launcher rules, and the previously omitted crate config covered. |
| G04 — World lifecycle | Isolated first-start, save, and restart of the intended saved world on the exact final server; preserved saved records and explicit migration results. |
| G05 — Matching client | Real client connection and bounded checks for progression, specialist weapons, scroll roles, rack behavior, dialogue, armor, and input contexts. |
| G06 — Visuals | Actual icon/model/armor and Solas inspection on the final client; approval holds honored. |
| G07 — Final package | Refreshed verification and payload hashes, complete archive validation, correct saved world and matching client, preserved rollback, and atomic promotion. |
| G08 — Hosted acceptance | Bisect start/restart and actual matching-client connection evidence. |

Local acceptance can support `READY_FOR_HOSTED_TEST`. A successful hosted connection requires separate evidence; it cannot be inferred from compilation, unit checks, or local fake-player fixtures.

## Publication and provenance

Repository intake on 10 November 2026 found a public repository at commit `f0625f3cdc559bf771ab72a0db95a9ccdee6a31a`, with a single 22-byte README and no releases. That is the historical intake baseline, before this workspace is published.

The public workspace is intended for selected technical metadata and reviewed source/tooling. Keep full server backups, private saved-player records, credentials, reference decompilations, private preview worlds, and approval-held artwork outside public publication. Preserve the existing local server state while reviewing a separate distributable contents list. Component names and hashes establish identity, not redistribution permission or legal clearance.

The [component lock](../release/component-lock.json) is an identity ledger with explicit unknowns. It is not an approval to install the candidate, a substitute for native results, or an assertion that a final download exists.
