# Measured v17 gate sites

Updated 2026-11-13. This is an offline geometry draft for a separate private copied-world preview. All **28 feet anchors** are measured against the exact original v17 schematics and frozen candidate blocks. Original schematics, recovered worlds, frozen world, packaged content defaults and production Java were not changed. No actual Resident UUIDs have been invented or bound. Native walking, AI navigation, lighting, combat and visible acceptance remain pending.

The new data is in ignored `local/v17-gate-sites-draft/`: `gates.measured.json` (also `gates.json` for validator input), strict-schema `sites.draft.json`, detailed `measurements.evidence.json`, `hardware-overlay.proposed.json`, `givers.creation-draft.json` and `validation.json`. It is not a release config. Root owns review, creating a new candidate, native hardware/NPC placement and private runtime bindings.

## First playable route: Sakura

Use the actual village platform, not the separate fractured base at local Y27. The north approach entry is local (144,99,55), world (40144,109,40055). Its dry path to Hanae’s talk approach is 25 edges. Proposed Hanae stands at (40147,109,40078), leaving the main center corridor available. Create a new original Vale Resident here and record its real native UUID; do not adopt a saved CustomNPC or fabricate one. This is a static preview giver. Her authored day veranda routine has not been implemented by this binding.

From the overlook, the garden branch visits the mossy stone east of the approach, a real-water pond edge and the wood stair beside the root spread: **178 dry edges**. The alternate scout branch reaches a clear 9×9 grass yard in **93 edges**, with three exact full-floor/air/head-air mob slots. Both branch endpoints are within eight blocks of the courier start (40147,107,40159). Its bidirectional dry route to the northeastern pagoda’s outside veranda is **86 edges**; the authored limit is 120 seconds, still requiring native AI acceptance. The veranda-to-giver claim return is 49 edges. Branch choices are offered in place by the adapter.

All three click targets already exist and are separate from player feet: mossy cobble A/B and a jungle-wood stair C. No doorway or terrain edit is required for this selected route. Sealed chest rooms and upper storeys are excluded from this claim.

## Placement and feet anchors

Every preview is dimension **0**, including the template named Nether. World coordinates below are block feet cells; actor creation uses block centers. These distant sites require an explicit preview teleport/travel arrangement; no connection from the recovered village is claimed.

| Template | Minimum corner in world | Main playable feet height |
| --- | --- | --- |
| sakura_v17 | (40000, 10, 40000) | 109 |
| cloud_v17 | (42000, 20, 40000) | 143 |
| sand_v17 | (44000, 40, 40000) | 147 |
| mist_v17 | (46000, 40, 40000) | 61 |
| nether_v17 | (48000, 40, 40000) | 91 |

| Anchor | Local feet | World feet |
| --- | --- | --- |
| `sakura_gate_overlook` | (144, 99, 73) | (40144, 109, 40073) |
| `sakura_seed_stone_a` | (158, 97, 104) | (40158, 107, 40104) |
| `sakura_seed_stone_b` | (157, 97, 133) | (40157, 107, 40133) |
| `sakura_seed_stone_c` | (154, 99, 161) | (40154, 109, 40161) |
| `sakura_scout_yard` | (141, 97, 155) | (40141, 107, 40155) |
| `sakura_keepers_veranda` | (167, 99, 107) | (40167, 109, 40107) |
| `cloud_arrival_terrace` | (144, 123, 73) | (42144, 143, 40073) |
| `cloud_bell_court` | (146, 123, 79) | (42146, 143, 40079) |
| `cloud_exposed_bridge` | (144, 123, 85) | (42144, 143, 40085) |
| `cloud_signal_lever_a` | (106, 123, 105) | (42106, 143, 40105) |
| `cloud_signal_lever_b` | (106, 123, 116) | (42106, 143, 40116) |
| `cloud_signal_platform` | (199, 123, 99) | (42199, 143, 40099) |
| `sand_caravan_gate` | (144, 107, 73) | (44144, 147, 40073) |
| `sand_wind_vane_a` | (100, 107, 132) | (44100, 147, 40132) |
| `sand_wind_vane_b` | (100, 107, 151) | (44100, 147, 40151) |
| `sand_stall_ambush` | (105, 107, 143) | (44105, 147, 40143) |
| `sand_shelter_court` | (165, 107, 151) | (44165, 147, 40151) |
| `mist_moored_pier` | (96, 21, 10) | (46096, 61, 40010) |
| `mist_tide_lantern_a` | (54, 21, 65) | (46054, 61, 40065) |
| `mist_tide_lantern_b` | (162, 21, 103) | (46162, 61, 40103) |
| `mist_tide_lantern_c` | (109, 21, 128) | (46109, 61, 40128) |
| `mist_boathouse_lane` | (162, 21, 111) | (46162, 61, 40111) |
| `mist_lantern_quay` | (161, 21, 100) | (46161, 61, 40100) |
| `nether_ash_overlook` | (127, 51, 49) | (48127, 91, 40049) |
| `nether_cooling_valve_a` | (85, 51, 100) | (48085, 91, 40100) |
| `nether_cooling_valve_b` | (173, 51, 96) | (48173, 91, 40096) |
| `nether_gate_court` | (129, 51, 96) | (48129, 91, 40096) |
| `nether_forge_court` | (128, 57, 145) | (48128, 97, 40145) |

## Click targets and proposed controls

Six actual original click targets are bound in the draft:

| Anchor | Actual world block | ID:data |
| --- | --- | --- |
| `sakura_seed_stone_a` | (40158, 106, 40104) | 48:0 |
| `sakura_seed_stone_b` | (40157, 106, 40133) | 48:0 |
| `sakura_seed_stone_c` | (40155, 109, 40161) | 134:0 |
| `mist_tide_lantern_a` | (46053, 61, 40065) | 171:12 |
| `mist_tide_lantern_b` | (46161, 61, 40103) | 171:12 |
| `mist_tide_lantern_c` | (46106, 61, 40129) | 171:12 |

Mist’s targets are real carpet caps over existing glowstone floor lamps. The lamps already emit light in the original; interaction does not by itself prove an extinguish/relight effect. That visible world reaction remains a separate enhancement.

The following **seven controls are proposed only**. Every destination is currently air in both original and frozen copy; supports are exact original blocks. None replaces a tile or deletes a decorative button. They are omitted from `sites.draft.json` until review and actual native placement in a new candidate.

| Anchor | World air cell → new ID:data | Exact support world / ID:data / face |
| --- | --- | --- |
| `cloud_bell_court` | (42147, 143, 40080) → 25:0 | (42147, 142, 40080) / 155:0 / UP |
| `cloud_signal_lever_a` | (42105, 145, 40105) → 69:1 | (42104, 145, 40105) / 159:0 / EAST |
| `cloud_signal_lever_b` | (42105, 145, 40116) → 69:1 | (42104, 145, 40116) / 159:0 / EAST |
| `sand_wind_vane_a` | (44101, 147, 40132) → 69:5 | (44101, 146, 40132) / 24:2 / UP |
| `sand_wind_vane_b` | (44101, 147, 40151) → 69:5 | (44101, 146, 40151) / 24:2 / UP |
| `nether_cooling_valve_a` | (48086, 91, 40100) → 69:5 | (48086, 90, 40100) / 159:7 / UP |
| `nether_cooling_valve_b` | (48172, 91, 40096) → 69:5 | (48172, 90, 40096) / 159:7 / UP |

Cloud’s note block is an explicit bell stand-in, not an existing bell model. Its two real levers would sit below the existing decorative buttons on covered eaves; the actual roof is local Y127, four blocks above feet. Sand vane and Nether valve lever controls still need recognizable dressing/wording. All anchor feet and forward/return route pairs stay connected after an **in-memory** application of these seven changes. Levers have no entity collision; the solid note block remains outside the causeway corridor. Native block support, placement callbacks, note tile creation, click feedback and protection checks are still required.

## Other connected routes and semantic limits

| Template | Suggested route | Dry edges | Limits |
| --- | --- | --- | --- |
| Cloud | arrival → bell → bridge → signal | 113 | Two bridge spawn slots and three signal slots are clear. Catch terrace is four blocks lower and has an existing recovery route; no teleport binding was invented. |
| Cloud | arrival → bell → covered A/B → signal | 203 | Reachable covered eaves; sealed tower interiors excluded. |
| Sand | arrival → vane A/B → courier → actual shaded awning | 233 | Shade has a real slab roof at local Y112. Nearest real cistern water is 15.03 blocks away; immediate water-service dressing remains pending. |
| Sand | arrival → ambush → courier → awning | 209 | Three clear dry mob slots. Courier start is near the west market; native navigation required. |
| Mist | arrival → lamps A,C,B → lane → quay | 341 | Unordered A,C,B saves 200 edges versus A,B,C for the same lamps. |
| Mist | lamp B → dry skiff watchpoint → quay | 18 | Dry protection location; riding and return require access work described below. |
| Nether | arrival → valve A/B → front court → preparation hall | 349 | Dry service flanks and real connected hall, not solid Y51 castle foundation. Forge dressing remains pending. |
| Nether | arrival → front court → preparation hall | 99 | Three assault and one interrupt offsets sampled; upper sanctums excluded. |

A graph edge count is a route length, not a playthrough time or native NPC pathfinding result. Paths disallow swimming, climbing and drops over one block. The block-grid model still approximates stairs, carpet, doors and collision shapes. Actor slots additionally require actual air at body/head and a full dry floor; native collision checks must make the final decision.

## Actors and original Resident proposals

| Actor stage | World spawn | Geometry evidence |
| --- | --- | --- |
| `gate_sakura_seedfall/escort` | (40147, 107, 40159) | full dry floor; body/head air; reachable dry route |
| `gate_sand_dry_wind/escort` | (44104, 147, 40141) | full dry floor; body/head air; reachable dry route |
| `gate_mist_low_tide/skiff` | (46172, 57, 40103) | 3×3 real-water surface, air through rider headroom local Y22 |

The final Mist boat proposal is **(46172,57,40103)**, beside the eastern dock, superseding the earlier central-water draft. Its dry watchpoint is **(46169,61,40103)**, five blocks away. All nine cells below the boat footprint are real stationary water (ID9) at local Y16, with air through Y22. The original remains 200×200 with real water.

**Literal skiff riding is not yet a complete route:** no graph swim/climb return exists from this water surface to the local Y21 boardwalk. All 32 original ladders start at local Y32 or higher. Do not call the branch a verified boat ride or strand a player after requiring boarding. A reviewed small dock ladder/boarding platform or other explicit return interaction is needed in a new candidate. Protecting the skiff from the dry dock fits the current measured geometry. No access edit has been applied.

| New original Resident | Proposed world feet | Entry world feet | Entry → talk approach edges |
| --- | --- | --- | --- |
| `keeper_hanae` | (40147, 109, 40078) | (40144, 109, 40055) | 25 |
| `bellwarden_tsumugi` | (42147, 143, 40078) | (42144, 143, 40055) | 25 |
| `cisternkeeper_rihane` | (44147, 147, 40078) | (44144, 147, 40055) | 25 |
| `pilot_shiori` | (46096, 61, 40015) | (46096, 61, 40005) | 11 |
| `forgesentinel_kagero` | (48129, 91, 40053) | (48127, 91, 40049) | 7 |

Each proposed Resident has exact original/frozen floor and air samples, a clear 0.6×1.8 body cell matching `EntityResident.setSize`, multiple clear neighboring talk cells, and a bidirectional physical entry path. The creation draft records all local/world paths and footprint bounds. UUIDs remain null there and `sites.draft.json` has `givers:{}`. Actual native creation, ownership, collision, world persistence and UUID binding are next. Daily resident routines and authored atmospheric reactions are not implied by static spawning.

## Verification boundary and next action

The actual content validator passed **5 missions / 5 residents / 28 measured anchors / zero unmeasured anchors**, with no errors or warnings. The strict site field sets and coordinate bounds were checked against `GateBook` schema. Each anchor’s original/frozen floor, feet, head and extra headroom block values match. Detailed original IDs/meta, hashes, complete route coordinates and actor offsets remain in the ignored evidence. The seven proposed overlays were tested only on temporary in-memory schematic objects; no schematic/world file was written.

No selected dry gate route requires Fable’s sealed-chest-room C1 doorway proposals. Those separate unopened rooms and disconnected upper floors remain outside the measured campaign route, not declared fixed. Mist water-return access is the explicit additional proposed access work. The previously flagged Nether ladder above/below an open trapdoor remains a native verification candidate; it was not deleted or filled.

Review the seven-control overlay and semantic limitations, then copy the frozen candidate to a **new** Core 0.3.13 private world, apply reviewed native changes, create the five original Vale Residents and bind their actual UUIDs. Load the measured config privately, validate first Sakura garden and scout branches through escort/claim/save/restart, then the other four. Keep packaged null defaults until this acceptance. No Git checkpoint or full-game completion is claimed by this measurement task.
