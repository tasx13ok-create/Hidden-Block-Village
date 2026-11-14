# Five exact v17 templates in a verified world copy

The recovered Naruto world now has a separate, verified preview containing all five original v17 templates. This is an offline integration checkpoint, not native Minecraft, visual, dungeon-gameplay or deployment acceptance. Nothing was pasted into a recovered original. No schematic, doorway, terrain, NPC, script or story-gate edit was applied.

Safe machine-readable evidence: [v17-candidate-20261113.json](../../release/evidence/v17-candidate-20261113.json). The original tooling was supplied by Fable; Codex hardened its safety/rollback/parser behavior and performed this integration. The private world itself is not stored on GitHub.

## Private frozen snapshot

- Snapshot: `D:/HiddenBlockVillage-Sprint-20261113/v17-world-candidate-01/world`.
- Full private inventory: sibling `WORLD-FROZEN-RECEIPT.private.json`, SHA-256 `cb89b22a12f18578a42ad620b454e5c95a2a7d813579013eebaf56f3efbe002c`.
- Tree manifest SHA-256: `85298e3fb60a1b6b3e521f9ef34a0b24884b9e3597955900d854e8d7f89bec2d`.
- Final snapshot: 1,771 files, 5,687,244,986 bytes. Treat this tree as read-only and copy it again for native testing.

The source was the immutable `server-intake-verified/server/Naruto.test` recovery, DataVersion 1343. The 1,761-file / 5,681,387,636-byte independent copy matched every archived file SHA and every destination ZIP CRC. After integration, every source file and every original file in the candidate still matched its original SHA, and the source file inventory and all five original schematic hashes were unchanged. Unknown tags, private inventories/player data, NPC data and scripts therefore remain preserved privately as exact copied bytes. They were not executed or migrated.

The frozen tree digest is `sha256(json.dumps(private_receipt['files'], sort_keys=True).encode('utf-8'))`, using default JSON separators, the stored list order and no trailing newline. The private receipt contains the individual hashes required for a differently framed copy-verification system. It must not be published.

## Explicit Overworld placements

All eight target region files were absent in both source and candidate before writing. Sites are separated by at least 1,712 blocks along X. The full schematic heights fit the 0–255 build range. **Nether is the template name: its preview is in the Overworld, not DIM-1.**

| Template | World minimum corner | World maximum corner | Size | New chunks / regions |
| --- | --- | --- | --- | --- |
| Sakura | `40000,10,40000` | `40287,226,40287` | 288×217×288 | 324 / 1 |
| Cloud | `42000,20,40000` | `42287,228,40287` | 288×209×288 | 324 / 1 |
| Sand | `44000,40,40000` | `44287,192,40287` | 288×153×288 | 324 / 2 |
| Mist | `46000,40,40000` | `46199,187,40199` | 200×148×200 | 169 / 2 |
| Nether | `48000,40,40000` | `48255,165,40255` | 256×126×256 | 256 / 2 |

The originals were pasted with air clearing into previously absent regions. Sakura/Cloud/Sand retain their supplied fractured geometry. Mist remains exactly 200×200, with all **427,086 real water blocks** preserved at world Y=45–56. No water cell touches its schematic side boundary. This does not establish native fluid stability.

All 1,397 newly created chunks were read back. Full non-air block-ID histograms matched each template; 1,120 deterministic exact block/metadata samples matched; all 144 translated tile payloads matched their originals in full, privately. Every final region hash and committed receipt was checked. These checks complement, rather than replace, a native load and walk. The legal-height previews can meet newly generated surrounding terrain at hard seams; adjacent terrain generation/biomes and the low placement heights still need in-game review before these sites are treated as final world integration.

The complete two-region Nether paste was rolled back. Both created files disappeared, the other six preview region hashes stayed unchanged, and the original was pasted again. The reapplication's complete compressed chunk payload digest matched the first placement exactly; only region-header timestamps may differ. Recovery receipts and snapshots remain private beside the world.

## Geometry-only landing and court candidates

These coordinates are verified against the stored candidate blocks and the conservative route graph. They have safe floor support and the stated clear air above the feet. Forward and return graph paths exist. They are **not visually/native verified, authored gate identifications or assigned boss arenas**; no positions were written into the 28 story-gate records.

| Template | Entry teleport centre | Clear court centre | Square width / air height | Shortest graph route | Containers still unreachable |
| --- | --- | --- | --- | --- | --- |
| Sakura | `40003.5,37,40143.5` | `40070.5,37,40014.5` | 29 / 4 | 196 edges | 4 |
| Cloud | `42003.5,47,40143.5` | `42070.5,47,40014.5` | 29 / 4 | 196 edges | 6 |
| Sand | `44003.5,67,40143.5` | `44070.5,67,40014.5` | 29 / 4 | 196 edges | 13 |
| Mist | `46096.5,61,40003.5` | `46105.5,61,40180.5` | 16 / 4 | 268 edges | 4 |
| Nether | `48068.5,101,40073.5` | `48072.5,101,40077.5` | 13 / 3 | 8 edges | 8 |

The four village entries are inset clear landings reachable from a safe boundary floor. Sand's boundary is actual sand; its supporting gravity-block chain and terminal solid support were checked in the candidate. Nether has no safe boundary-floor entrance in this model, so its coordinate is explicitly an interior landing. The mask found no 12×12 Nether court with four clear air blocks; the reported 13×13 court has three. No geometry was changed to manufacture those measurements.

The graph routes listed above require neither swimming nor climbing in the current model. Closed-door shape, sprint jumps, complex collision, modded movement, lighting, native mobs and physics remain outside that proof. The inaccessible container counts are findings for subsequent design/native review, not authorization to run every doorway proposal. The Nether ladder facing an open trapdoor at local `132,95,144` remains a native check candidate and was preserved.

## Native continuation

The server agent was given the frozen path, full receipt pin and graph candidates and will copy the snapshot into a separate Magma test profile. Do not launch Java against this frozen tree or `server-intake-verified`. Before calling this a playable migrated world, verify original registry ownership, CustomNPCs/scripts dependencies, native chunk loading and missing mappings, water/lighting, all five entry/exit routes, representative tiles/inventories, and a clean save/restart in that separate runtime copy. Mission runtime wiring and semantic gate measurement remain separate work.
