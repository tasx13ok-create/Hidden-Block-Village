# Build the next five Naruto boss coliseums

This is an execution brief for a separate arena-building chat. The assignment is **five NEW, finished 500 × 500 × 250 boss dungeons**, added alongside the existing five v17 sites. It is not an instruction to overwrite, enlarge or replace those sites. The intended result is generated Minecraft assets, working encounters, repeatable build tools and evidence from actual checks. A design document alone does not finish this assignment.

## Copy and paste this kickoff into the new chat

> Work in `tasx13ok-create/Hidden-Block-Village`. Fetch the current main branch, read `AGENTS.md`, `docs/current-handoff.md`, `docs/original-mods-first.md`, `.agents/skills/hidden-block-village/SKILL.md`, and both files in `collaboration/arena-build-orchestration/`. Then build all FIVE new Naruto boss coliseums defined there, each exactly 500 blocks wide, 500 blocks long and 250 blocks high. Complete their geometry, interiors, environmental mechanics, boss integration, rewards, reset/restart behavior, local exports and verification in this run. Do not stop after another plan, one arena, an exterior shell or attractive renders.
>
> Use the supplied original NarutoMod/addons/CustomNPCs pack with our current Core, HUD and compatibility work on top. For this assignment, integrate the real mods rather than beginning another 49-mod rewrite. Preserve Minecraft movement, building/inventory and recognizable Naruto powers. Make powerful equipment feel powerful; create challenge through readable mechanics and counterplay rather than inflated health or universal immunity. Offer a short entry, immediate practice and a useful reward/exit.
>
> Own one arena branch and only arena-specific paths. Do not edit Core, HUD, Controls, Rack, Residents, existing v17 templates, the original world or other workers' files. Coordinate any missing shared API through an integration request while continuing independent arena work. You may delegate the five arenas/verification to subagents, with disjoint file ownership and one final integrator. Preserve current local changes and shared Git history.
>
> The target is Java Minecraft 1.12.2/Forge 14.23.5.2859 and the inspected Magma runtime. Its world Y range is 0..255. Place a 250-high export at minimum Y 3: its occupied export range ends at Y 252. Do not attempt a 250-high structure starting at sea level. Keep each 500×500 plot separate; use an audited new private world copy or a fresh arena preview world. The proposed world coordinates in the brief are candidates, not proof that those cells are empty.
>
> Build original, distinctly Naruto architecture and boss arenas for Hashirama, Ay, Gaara, Kisame and Madara. The brief separates proposed game adaptations from actual native entity/ability behavior. Resolve every entity/item/block ID against the actual matching runtime; do not infer IDs from display names or claim that a static CSV proves native acceptance. Preserve imported NPC identities, dialogs, scripts, ownership and player data. Do not edit the global tailed-beast/vessel manager to make repeatable arena copies.
>
> Implement a deterministic, bounded generator and arena-specific runtime/configuration. Produce all five full-size Alpha `.schematic` exports, a new test-world package, placement/anchor metadata, route maps and honest previews. Keep private worlds, reference JARs, reference artwork, player data and large runtime archives out of the public repository. Save original generator/runtime source, configurations, meaningful tests, safe metadata and the handoff to GitHub; verify the remote commit.
>
> Use the mandatory build → independent readback → geometry/route/support/fluid audit → repair → native copied-world boot → real encounter/persistence/reset/reward tests → repair loop below. A failed gate is work to fix, not a reason to delete the test or quietly shrink the requirement. Do not claim that an offline render, item count, nominal bounding box or green component build proves the arenas playable. Finish every unblocked deliverable and state any genuinely unavailable acceptance scope precisely.
>
> The owner last chose **Keep Minecraft closed**. Do not open or control their client merely to gather screenshots. Headless generation and isolated server checks can continue. Complete graphical acceptance only after that preference changes; until then deliver the other artifacts and label actual-client/shader acceptance pending. Never stop/relaunch another live session without checking its current identity and ownership.
>
> Start executing. First establish current source/input identities and file ownership, then implement the generator/runtime and build all five. Keep commentary concise and concrete. Your final handoff must contain the five outputs, exact source/asset hashes, what was actually tested, remaining failures and the verified GitHub commit.

## 1. Authoritative baseline and private inputs

Read current files before relying on these historical pins. At the time this brief was written, main included `de686c8`, Core 0.3.16 and Field HUD 0.19.0. `release/evidence/core0316-hud019-verification.json` records an eleven-area/36-artifact build and 3,665 disposable Forge assertions. Source snapshot: `8b5ac108319049bfa86cf490ec1c4fd4f372a655b14d32f6781b8e57f349232d`; Core: `840d44ec6db5355aeec0402de6d74350cd36d18d350a4e7f5ce14e4e6bf21014`; HUD: `54b09ee318e5f97dd285a205a41dabde8cffd66d980ad95316689a2500a54d8b`.

The separate bounded full 54-Magma fixture passed 18 native API, 20 overload, 49 facade/lifecycle, 82 caller and 16 paired absorption assertions. It used a private experimental Spring shim with protected setter reflection. This is **not** approval of that shim for production, client synchronization, an original saved world, these arenas or hosting. Read `release/evidence/core0316-magma-paired-receipts.json`. Later public-API-only compatibility work may supersede it; fetch and inspect its actual evidence.

The old twenty-hour sprint ended at 2026-11-14 13:32:41 UTC with the full game incomplete. Do not backdate arena work or say it was finished within that sprint.

Inputs available on the owner's PC, subject to checking their existence and manifests:

| Input | Location / evidence | Use |
| --- | --- | --- |
| Complete Bisect archive | `D:/archive-2026-11-09T202203Z.zip` | Immutable private recovery input; use the reviewed intake, never archived startup scripts |
| Inspected server recovery | `D:/HiddenBlockVillage-Sprint-20261113/server-intake-verified/server` | Exact dependency/config/registry source after checking private receipts |
| Immutable placed v17 world | `D:/HiddenBlockVillage-Sprint-20261113/v17-world-candidate-01/world` | Read-only reference or source for a NEW hash-verified copy |
| Five original schematics | `C:/Users/Steal/Downloads/archive-2026-11-13T173625Z/{sakura,cloud,sand,mist,nether}_v17.schematic` | Composition/reference measurements; preserve original bytes |
| Original pack | `C:/Users/Steal/curseforge/minecraft/Instances/Hidden Block Village/mods.zip` and inspected instance mods | Private pinned Naruto/addon/CustomNPC distributions; compare to design inventory |
| Existing v9 authoring | `C:/Users/Steal/OneDrive/Documents/ChatGPT/Naruto mod/source`, `output_v9`, `tools` | Read-only historical generator examples; port only reviewed original source needed by the new repo tools |
| Latest compiled cohort | Private D: source-build/native reports referenced in current handoff | Select exact copied artifacts by report identity; never glob a stale `build/libs` |

The immutable v17 candidate receipt records 1,771 files/5,687,244,986 bytes. The newer sites are Sakura/Cloud/Sand 288×288, Mist 200×200 and Nether 256×256. The existing v17 preview origins include Sakura around 40000, Cloud 42000, Sand 44000, Mist 46000 and Nether 48000 on Z40000. Check full AABBs and region inventories instead of treating these numbers as a complete occupancy map.

Preserve old Mist's200×200 real-water settlement. Keep the old Earth/Leaf, Cloud and Sand fractured-base treatment and Fire/Nether's separate base treatment. **The new Kisame coliseum is a different 500×500 site**; it does not change the old Mist footprint.

Existing supporting material:

- `design/DUNGEON-ATLAS.md`, `design/NARUTO-FUN-BRIEF.md` and `design/REFERENCE-RESEARCH.md`.
- `docs/dungeons/v17-integration.md`, `docs/dungeons/v17-candidate-current.md` and the latest v17 evidence. Their historical sections can disagree; use the newest measured receipts and distinguish offline from native results.
- `tools/world_merge/`: defensive NBT/Alpha schematic I/O, placement, audits, reviewed edits, Anvil copy/paste/rollback and NPC inventory.
- `tools/magma_smoke/`: separate runtime staging, copied-world guards, loopback controllers and safe console requests.
- `collaboration/claude-naruto-bridge/BOSS-NPC-FACTS.md`, `.json`, `boss-npc-registry-table.csv` and `CHAKRA-AND-COMBAT-API.md`.
- `content/expeditions/`, `tools/validate_expeditions.py`, existing Core mission/reward/native-check source. Some content README statements predate runtime adapters; inspect current implementation.

The 1,772 inventory icons and bbmodels are not proof of full humanoid worn-armor UV textures. Do not count inventory art as completed armor rendering. Existing native Naruto models can be used locally through their installed mod; original new art needs provenance and actual UV/render verification.

The latest art/ownership policy is `docs/original-mods-first.md`: retain the original supplied mod experience, including original Varied Commodities icons and worn armor, with project features on top. Do not re-enable the historical global reference-art reskin. Namespace new arena/project assets without shadowing original providers' assets or registry ownership. Preserve stable technical/save IDs; use the latest accepted stock-art client selection for any new export rather than assuming a historical friend archive represents current policy.

## 2. Ownership and integration boundaries

Create/reuse one suitable checkout according to the host worktree rules. Branch name suggestion: `codex/five-new-naruto-coliseums`. Fetch main first; preserve unrelated working changes. Keep the runtime/core chat informed using the available coordination tools.

Arena worker ownership:

- `tools/arenas/` — new generator, large-volume streaming exporter, audit, staging and native harness tooling.
- `content/arenas/` — versioned arena geometry/palettes/encounter/reward/anchor definitions.
- `modules/naruto-arenas/` — an optional NEW arena-specific controller, if runtime code is necessary.
- `tests/test_arenas_*.py`, arena-specific module tests, `docs/dungeons/new-arenas/` and safe `release/evidence/arenas-*.json`.
- This orchestration directory and arena-specific integration requests/handoff.

Protected shared areas include existing Core/bridge/armor/academy, HUD/menus/hotbars, Controls, Rack, Residents, Encounters implementation/profile registry, shared build/release registries and all original v17 inputs. Reading them is necessary; editing them is another owner's job. Do not mutate `Profiles.ALL` at runtime to bypass its 136-profile validation. Request a reviewed extension surface if required.

Prefer a server-side arena controller with no new entity/item registry entries when existing native bosses, items, particles and vanilla boss bars can provide the experience. That can retain 58 client JARs and add one server-only arena module to the 53-JAR DI-omitted preview, yielding 54 server JARs. These are **expected composition counts, not acceptance checks**. A new client renderer/item/entity needs an explicit matching client package and new manifest; never hide that dependency to preserve a count.

Each subagent owns one definition/generator layer or one verification role. One integrator owns shared geometry utilities, runtime state schema and final packaging. Publish intermediate tested checkpoints, then the final handoff. Do not merge an unrelated Claude parent graph: the recovered/decompiled tail source remains outside the reviewed original-source boundary.

## 3. Fixed size, vertical envelope and reservation

Each export is 500×250×500 in `(width,height,length)` order:62,500,000 cells. All five total 312,500,000 cells. Minimum world Y 3 gives maximum inclusive Y 252. Reserve Y 0..2 and 253..255 as margins. A tower peak or statue must use the upper volume deliberately; empty padding alone does not satisfy the requested monumental scale.

`tools/world_merge/schematic.py` currently allows 64×1024×1024 cells, and defensive NBT defaults to 512 MiB decompressed input. One 62.5 million-cell arena fits the cell limit. Do not weaken hostile-input checks just because a naive five-arena allocation runs out of memory. `new_schematic`, `to_bytes` and NBT `dumps` currently build dense arrays/buffers; they are not a streaming production generator.

Proposed preview reservations are in `arena-brief.json`: minimum corners `(64000,3,64000)`, thenX+1024 per arena. Each 500-wide plot fits within a 512-wide Anvil region when the minimum is region aligned; these candidates leave 524 blocks between plots. **Audit them first.** If any reserved plot/buffer contains unrelated blocks, entities, structures, claims, player builds or saved data, choose a different verified reservation in a NEW world. Never call these cells vacant based only on distance from the known v17 sites.

For every export, record size, local/world minimum and maximum, local arrival/boss/reward/exit anchors, dimension, WEOrigin, WEOffset, copied-position convention, palette identity and SHA-256. Use explicit `placement --anchor local:world` arithmetic. A 250-high export has legal minimum Y 0..6; this release chooses 3. Verify `maxY = minY + height - 1` on the written output. Never blindly paste at old stored origins or use `//paste -o` without checking that export's own metadata.

The default test-world placement is Overworld for side-by-side access. Fire's actual Nether presentation is a separate acceptance pass: contained lava, fog, light, bed/travel restrictions and safe exit must be tested in dimension-1 before describing its Nether atmosphere as accepted.

## 4. Common architectural and play contract

The 500-block plot contains a monumental stadium, an inhabited preparation district, tactical side spaces, an accessible main route, an alternative route, a reward scene and a short return. It is not 500 blocks of flat floor or a hollow decorative shell.

Design a 220..340-block-wide outer bowl/silhouette around a roughly 112..144-block-wide active boss court. Use scenery, stands and vertical framing for scale while keeping combat distances useful to actual jutsu and Minecraft movement. The visual plot can be enormous without forcing a minute of running between each hit. Entry-to-first-action target is 30..60 seconds; a normal full run targets 6..12 minutes and a learned repeat 4..8 minutes. These are playtest targets, not measured claims.

Primary walking routes are at least 5 blocks clear, ordinary secondary routes 3, with deliberate landings and headroom. Measure actual player/native-form AABBs. Provide a human-scale route throughout and an expanded arena/gateway route for supported large forms. Do not advertise a giant mode as supported when it clips through the gate or ceiling. No mandatory precision parkour, flight, rare eye or one specific nature is required to finish normal mode.

Every large mass needs supports and usable space. Build bearing columns, stairs, galleries, roof transitions, maintenance access, shaded/rest areas, storage, treatment/aid rooms and believable services. Furnish functional interiors with sparse purposeful details. Make decoration, colors and landmarks identify routes. Avoid five recolors of one stadium mesh, random block noise, unsupported lamps, identical cones, inaccessible upper floors and chest rooms sealed behind glass.

The main path must work without shaders, particles, audio, color discrimination or camera shake. Give audio tells a visual/text equivalent. Preview BSL/shader presentation later without using bloom/fog to hide missing geometry or unreadable attacks. Each site needs daylight, dusk/night, interior, roofline, underside/foundation, boss-floor and exit compositions.

## 5. Five concrete arena briefs

The following moves are **intended game adaptations**. Native bosses retain their real installed behavior unless a verified adapter explicitly controls it. A named attack in this brief is not a claim that the original JAR exposes that exact move or a safe callable API. Own environmental mechanics must be original source and visibly distinct from native actions.

### A. Hashirama — Valley of Roots Coliseum

Identity: an immense Leaf-style timber-and-stone coliseum embracing a sculpted living tree and two unequal fractured earth terraces. Broad roots bridge deliberate cracks; dark timber frames, plaster, green roof accents and inhabited archive balconies contrast with the exposed geology. Use original block-built statuary, not copied server sculptures.

The main court is around world Y 80. Roots and maintenance passages occupy Y 12..58; galleries Y 96..170; tree crown/upper monuments reach Y 240..252. The safe entrance is on the south axis, with a preparation veranda and a split garden loop. The alternative route is a supported root-side gallery, not a lethal jumping shortcut. Include two enterable archive/service wings and a high overlook reached by real stairs.

Intended boss identity: Hashirama. Static intake candidate `ahznbcursemarkaddon:edotensei_hashirama`; prove current registry class, actual body, revival and cleanup behavior. An Edo appearance, revived replacement or unrelated summon does not automatically settle the run.

Three encounter beats:

1. **Root lanes:** three broad, marked ground lanes rise in a bounded owned-cell set. The player can dodge between lanes, guard the final sweep or break an exposed seal with ordinary attacks. Wood blocks never grow outside the journaled volume.
2. **Dragon commitment:** a readable wood-dragon-shaped arena hazard tracks during its tell, then commits to a bounded path. Side openings and an interactable root anchor create a punish window. A fire-specialist option is useful, but the arena tree does not burn and every kit has a weapon/seal counter.
3. **Sage bloom:** alternate two safe sectors and an exposed central anchor. Successful control/dodge creates a generous burst window. Strong armor can overpower guards; the meaningful decision is when to commit damage versus open a safer lane.

Reward scene: the roots lower into a bright bloom balcony, the keeper visibly changes dialogue and a short return stair/gate opens. Resolve a distinct control/earth/wood-themed reward bundle from actual supported items/techniques; do not announce an unimplemented Wood Release skill. Retain an optional exploration cache outside the mandatory fight route.

### B. Ay — Storm Crown Coliseum

Identity: an asymmetrical Cloud fortress-stadium on three fractured pale-stone plates, with indigo roofs, grounded lightning pylons and one dominant signal tower. Connect the stands with supported broad bridges and sheltered galleries. The broken underside must vary structurally from Hashirama's roots.

Entry/rest terraces are around world Y 112, the main court Y 128, optional tower routes Y 164..220 and the crown Y 248..252. Catch terraces and safe recovery volumes sit under exposed routes. A grounded inner path completes the entire run without jumping gaps.

Intended boss identity: Ay, with static candidate `kabutoaddon:ay`. Verify that identity and native Lightning Armor/attack state rather than labeling a different Raikage as the same entity.

1. **Lariat lanes:** a wide shoulder-charge lane has a visible start, committed heading and recovery. Side-step, guard/counter or exploit a flanking opening. Never combine unavoidable edge knockback with a hidden wind force.
2. **Grounded crown:** two pylons alternately conduct an arena pulse. Interact/interrupt a pylon or occupy a marked grounded pad; a wind/movement specialist gets another option, not exclusive access. Cover is useful but cannot wall off every route.
3. **Guillotine commitment:** a large overhead strike marks its landing and exposes a long punish interval. Rotate cover and grounded pads while retaining one stable refuge. Lightning Step and high-speed native moves remain helpful; they do not randomly fail because the boss is designated "immune".

Reward scene: the signal network lights outward in an actual block-light sequence, opens a leeward exit and offers a verified movement/Lightning kit upgrade choice. Do not secretly replace the player's chosen starter nature.

### C. Gaara — Kazekage Sun-Sand Coliseum

Identity: a monumental sandstone-and-terracotta bowl with turquoise scholarly bays, shaded bazaar/service halls, a bounded cistern and an offset observatory. The fractured desert base contains supported sand over non-falling structure, exposed strata and wind-cut joints.

The main court is around world Y 90, a dry alternative/cistern route Y 42..62, inhabited galleries Y 110..174 and the observatory/statues Y 225..252. Domes transition into actual walls/piers. Make the market, storage, aid rooms and water maintenance usable. No unsupported sand/gravel is permitted, including after a phase update/reset.

Intended boss identity: Gaara, static candidate `narutomod:gaara`; validate its actual native body and drops/XP behavior.

1. **Sand guard:** rotating cover and a visible commitment attack expose flanks. High damage can break the opening rapidly. The shield is a readable directional/state mechanic, not permanent percentage damage denial.
2. **Burial lanes:** bounded pre-authored dune/cover states alter two lanes and preserve a dry safe route. A seal node or accepted heavy weapon contact interrupts burial. Never simulate an uncontrolled falling-sand avalanche across the 500-block site.
3. **Desert convergence:** marked sand bands converge, then release a large punish window at the observatory seal. A wind specialist can clear an explicitly eligible hazard; every other kit can use the physical node/side route.

Reward scene: the cistern visibly restores a small protected garden, merchants react and a fast shaded exit opens. Award a distinct sealing/projectile/sand-oriented bundle. Magnetic interactions must use a verified eligible-item/material table; they cannot confiscate arbitrary player inventory.

### D. Kisame — Drowned Shark Coliseum

Identity: a 500×500 coastal stadium on real piers and seabed supports, with a real water arena, warm lantern piers, a boathouse, dry regrouping islands and a lighthouse reaching the upper silhouette. This new ocean site is separate from the old 200×200 Mist settlement.

Use a documented water level around world Y 63, dry platforms/entry Y 66, a contained underwater route around Y 48..58 and lighthouse/rigging monuments Y 210..252. Verify source water, shore continuation, hull clearance, boats, seabed/piles, dry interiors and breathing recovery. Shader water is optional; native water blocks and collision are mandatory.

Intended boss identity: Kisame, static candidate `narutomod:kisame_hoshigaki`; resolve actual native source ownership and Samehada/chakra interactions before adding another cost system.

1. **Shark lanes:** curved marked wakes cross water channels while dry islands stay connected. Melee users have a real approach; ranged users cannot remain permanently unreachable behind one railing.
2. **Water prison:** a bounded precomputed water/dry-state volume changes the fight space. Provide an owned breathing safeguard, two exits and a dry alternative. Preserve stronger existing player potion effects; remove only the controller's own effect on exit.
3. **Chakra pressure:** a visible weapon/channel tell can create a small bounded resource pressure only after actual accepted contact. Existing native absorption remains authoritative; never charge both native and glue for one contact. Dodge, guard, interrupt or disengage all offer a counter. A depleted player always retains an ordinary weapon/seal route.

Reward scene: water recedes only inside the owned state volume, lamps expose a safe reward pier and the boathouse return opens. Offer a verified water/control or sustained-combat reward. Drowning, logout underwater and restart during a water phase must all have tested recovery landings.

### E. Madara — Ashen Moon Coliseum

Identity: the most imposing grounded citadel-stadium, with charcoal stone, Nether brick, contained furnace light, monumental guardian shapes, a high moon gate and a vertical reward sanctum. It must not inherit the torn-floating-ground treatment. Use a massive supported podium, nested districts and several useful interiors.

The main court is around world Y 96, lower cooling/service routes Y 28..62, galleries Y 130..190 and the citadel crown Y 245..252. Lava is contained behind tested margins and cannot destroy objective items. Separate the dramatic outer bowl from a useful roughly 144-block combat court. Preview in the Overworld first; accept actual Nether lighting/travel only in a separate dimension-1 test.

Intended boss identity: Madara, static candidate `ahznbcursemarkaddon:edotensei_madara`. Prove real-body/revival/phase cleanup and terminal outcome; a canceled death or Edo revival cannot issue another reward.

1. **Fire and fan:** broad fire lanes with a marked safe side and a recoverable committed fan/sweep. Fire-resistant or strong armor should meaningfully help. Do not burn the authored citadel or fill the entire view with opaque particles.
2. **Susanoo pressure:** show the native/verified avatar or an original supported presentation, with actual attack volumes matching the tell. Two seal stations open a punish interval. If an avatar is unavailable, implement a complete original supported substitute and clearly identify the adaptation; a renamed generic mob is not visual parity.
3. **Meteor commitment:** two enormous visible overhead warnings lead to bounded blasts, each with more than one valid shelter/counter. Meteor presentation must not allocate thousands of falling-block entities or make an uncontrolled explosion through the world. After commitment, expose a satisfying burst window and a short final reversal.

Reward scene: furnace light cools, the upper seal opens and the player chooses a real advanced armor/weapon/mode reward. The exit returns quickly to the aid/preparation court. This is a boss coliseum ending, not a claim that the broader Naruto game's whole story is complete.

## 6. Combat, kits and encounter authority

Use actual current native registry/public APIs and existing Core authority. Static candidate IDs above are initial evidence only. In a disposable runtime, join each ID to the real living class, renderer/asset availability, spawn requirements, dimensions, hostility, ownership, native XP/drop hooks, revival behavior and removal side effects. Record the mapping and hashes. Do not spawn natural unique biju or rewrite vessel/global boss ownership to supply copies.

Prefer native AI/abilities plus our original arena/environment/reward glue. Do not call a native cast again when merely observing it. If a phase director controls native behavior, prove the exact supported control surface and that normal AI/procedures do not double-cast behind it. `setNoAI(true)` alone does not prove every modded onUpdate procedure stopped. Scope any suppression to the exact run-owned body and version; never disable all NPC AI globally.

Each attack record needs tell duration, aim/heading commitment time, active native volume, damage/control source, cancellation behavior, recovery, player counters and owned effects. Normal tells generally begin around 20..35 ticks and leave an actual punish interval; choose timings from playtests with the intended movement speed and latency. Avoid overlapping environmental and native attacks that eliminate every safe answer.

Normal supports 1..4 players. Start health scaling at `1 + 0.55*(participants-1)` and tune with measured damage, native boss behavior and real OP kits; this is a design starting point. Scale selected patterns/add counts, not just health. Keep one solo-completable node sequence and cap simultaneous combat adds, initially 4. Powerful builds may kill during a valid window and skip later phases. Phase coverage tests can deliberately exercise thresholds; do not force endless invulnerability so every normal run sees a script.

Support melee/taijutsu, projectile/jutsu, control/cover and sustain/support approaches. Every mandatory mechanic has at least two ordinary answers. Nature-specific interactions add advantage. Do not make one eye, one armor set, an unimplemented mode or a rare drop mandatory.

Use the current Dodge/guard/accepted-hit path. Naruto `ninjutsu`/`senjutsu` sources are not guaranteed to set vanilla magic/projectile flags. Resolve actual true/immediate source and verified summon ownership. Observe final event references and actual HP+absorption results. The actual Magma Damage 0 event can precede absorption deduction; do not regress to intent-only contact credit or let later unrelated damage inherit an earlier zero-loss receipt. Native casts retain their own cost/cooldown. No second spend, cast, XP award or invented refund is permitted.

Existing Core `RewardLedger.issue` accepts `Gear.ITEMS`, with a separate reviewed owned-reward path; it is not a generic `narutomod:*` issuance API. Use genuine Core ownership/serial receipts for Core gear. Native items need their own reviewed bounded item/outbox policy and real ownership/setup API where applicable. Never stamp a bare creative item with a forged Core serial. Never copy a boss's eye stack and assume its owner is now the player.

## 7. Entry, progression and a complete run

Give each arena a short self-contained entry board/NPC, a clear boss preview, one optional explanation and a safe practice space. A player can inspect immediately. Keep **unrated preview** distinct from **earned normal clears**: preview can supply authentic admin/test gear, but cannot award rank, first-clear or economy rewards.

For a new normal route, use a legitimate saved introduction interaction and supported starter/mission APIs. If the global academy is unavailable, implement the arena's own short normal introduction without pretending the old academy was completed. Coordinate shared awakening/entitlement identity with the Core owner. Actual public `PlayerTracker.addBattleXp(EntityPlayer,double)` can admit a native ninja, but creates a maximum with current chakra 0. Native-installed, unsupported or unready accounts must not silently receive a second Resolve pool. Any earned initial admission/seed needs a bounded, durable once-only receipt and a tested interrupted/reconnect policy. Do not repeat native combat XP awards.

Do not wipe or replace the user's inventory to make a demo. Loan/test gear must be explicitly owned and recorded; cleanup touches only the exact loan serials, not same-looking existing equipment. A permanent one-time starter grant with no preview economy rewards may be simpler than an unsafe loan system. Describe the implemented policy honestly.

The normal state graph is:

`AVAILABLE → PREPARING → READY → ENTERED → PHASE1 → PHASE2 → PHASE3 → VICTORY_PENDING → REWARD_PENDING → REWARDED → EXITED → RESET_READY`.

Optional phase skips from legitimate lethal damage are explicit transitions. Failure is `ACTIVE → RECOVERY_CHECKPOINT → RETRY` or a documented safe close/refund. Persist schema, arena/template version/hash, run UUID, seed, participants, eligibility/contributions, checkpoint, phase/objective state, boss/actor UUIDs, owned-cell journal, reward receipts, grace deadline and reset status. Unknown future records stay intact/read-only.

Test death, spectator/fake entry, late join, disconnect/rejoin, changed UUID/body/dimension, server restart during each phase, full inventory, offline reward delivery, repeat clear and reward replay. Contributions include accepted damage, protection/healing and actual objectives; proximity or a particle does not prove contribution. Choose and record a reasonable reconnect grace rule. Short recovery should not become another long walk to the arena.

After victory, make a physical world reaction, deliver the chosen supported reward through a durable outbox, open the short exit and leave a replay option. A second run must reset the same arena correctly with no stale boss/add/water/cover/loot state.

## 8. Generator, assets and bounded work

Implement deterministic generation from versioned JSON and a seed. Use named structural layers: reservation/terrain, supports, circulation, bowl/stands, landmark masses, functional interiors, arena mechanics, details, lighting and anchors. Shared primitives are acceptable; whole silhouettes, circulation and encounter mechanics must remain distinct.

Generate by chunk/section or bounded bands. Use byte/uint 16/nibble arrays, run-length spans, memory-mapped scratch or a streamed NBT writer. Do not allocate five 62.5 million-cell Python integer/object grids or several full compressed/uncompressed copies at once. Raw Blocks+Data are 125,000,000 bytes per arena before AddBlocks/NBT/compression. Build/verify one full-size arena at a time; write all five sequentially or with a measured memory budget. Preserve partial candidates and resume only with matching source/config/seed hashes.

Prefer a verified vanilla 1.12 palette for major geometry. Suitable families include stone/stonebrick, logs/planks/stairs/slabs, stained clay, stained glass, concrete, quartz and Nether brick. Blackstone, deepslate, copper and other later-version materials require an actual installed registered substitute, not an invented 1.12 ID. Metadata orientation matters for doors, stairs, slabs, beds, signs and containers. Preserve correct Alpha AddBlocks nibble order for 12-bit IDs.

Optional mod blocks resolve from real registry names into the selected runtime's numeric mapping, then get pinned in a palette manifest. Inspect saved Forge registry data and the matching runtime registry before paste. A numeric block ID from another pack is not a stable material name. Refuse a mapping mismatch; do not type FML confirm/discard or silently substitute air.

Add original block-built landmarks, bounded particles and actual supported lights. A visible redstone lamp must be powered; a glow decoration in a render does not establish block light. Main safe paths, stairs and landings target actual native block light≥8, with deliberate dark encounter zones separately documented and protected from unintended spawns. Water/lava/gravity tests run after native updates, not only before export.

Do not fill spectator stands with hundreds of AI entities or use armor stands as every decorative block. Start with at most 6 functional friendly actors, 4 adds, 1 boss and a small bounded owned-effect pool per active arena; tune only with measurements. Render spectators as authored architecture/banners/statues unless a tested low-cost system exists.

Performance starting budgets: one arena generation process below 1.5 GiB measured peak; no unbounded geometry/route queues; bounded native writes/prewarm per tick; no full 500-cube scan every server tick; incremental actor/objective work and spatial indexing. Aim for 20 TPS, p95 tick≤50 ms and p99≤100 ms during the documented 1..4 player test. Record hardware, memory, loaded chunks, entities, effects and exact test duration. These are targets that require measurement, not promises from a config value.

Do not permanently force-load all 5,120 possible plot chunks. Prewarm existing generated chunks in bounded batches, save completed lighting and release inactive regions. Preserve a loaded safe entry/checkpoint before admitting a party. Choose conservative per-instance loaded-chunk and effect caps from real measurements; record how the controller handles budget pressure without deleting a boss or silently skipping damage.

## 9. Original/imported NPC and world preservation

All five new sites use new stable IDs and explicit run ownership. Imported CustomNPCs are separate actors. Preserve their UUIDs, clone/dialog/faction/quest IDs, inventories, scripts, skins, commands, dependencies and event bus. Do not repurpose Hanae or other v17 gate givers as arena actors. CustomNPC notifications run on `NpcAPI.Instance().events()` as well as native Forge events; deduplicate cross-bus observations and do not treat a script Died notification as final unrecoverable death.

Recover archives as data. Do not execute downloaded startup scripts or arbitrary NPC commands while scanning. Existing static script reviews and private intake manifests are evidence to reuse, not permission to skip version-specific checks. Record safe metadata publicly; keep raw player/NPC/dialog/script/world payloads private.

Before world staging, make a new complete hash-verified cold copy or fresh preview world and a whole-copy rollback snapshot. Reuse the existing guarded copy/paste helpers where suitable. New arena files must not overwrite the immutable v17 candidate, original archive, owner's previous inspection worlds or a live world directory. For every paste, snapshot affected region bytes, preserve unknown tags/untouched chunks and write atomically.

Native boot/save can legitimately rewrite time, lighting, heightmaps, entity/chunk metadata and mod saved data. Report those changes and audit logical blocks/entities/imports. Do not claim that every file outside a few edited cells stayed byte-identical merely because an offline paste did. Preserve original player profiles and all untouched source hashes. A failed native callback or ambiguous setup mutation quarantines the new copy; restore the **entire cold snapshot** when claiming full rollback. Removing one boss/block is not a whole-world rollback.

Temporary runtime hazards use an owned before/after journal and conditional restoration. Never blind-paste the whole arena over online players. Reset only an empty owned instance/cell after verifying identity, participants and expected states. Prove actual native jutsu cannot damage protected structures/imported areas beyond the chosen instance policy; Forge block events alone are not proof against mods that directly set blocks.

## 10. Required execution passes and repair loop

Implement the new arena commands below; they are **required interfaces to build**, not commands claimed to exist today. Keep the existing `tools.world_merge` commands usable.

1. **Intake and reservation.** Capture Git/source/input hashes, mod roster, registry/palette, protected world/actor inventory and 5 plot reservations. Write ownership and `run-plan.json`. Missing private inputs block only their dependent runtime check; continue original generation and synthetic geometry checks.
2. **Implement common tools/runtime.** Add deterministic generator, streamed full-size export, manifests, independent readback/audits, route and encounter definitions, bounded arena controller, reward/reset persistence and guarded staging/native harness.
3. **Build ALL FIVE massing/circulation candidates.** Run full-size dimensions/support/headroom/route checks before detail. Fix disconnected floors and inaccessible mandatory spaces. Save original source/config checkpoint.
4. **Finish distinct geometry and real assets.** Complete each arena's architecture/interiors, functional services, hazards, boss anchors, lighting, reward and exit. Export all five schematics and maps. No placeholder hollow shells count as finished.
5. **Independent readback.** Reopen outputs through an independent/read-only path, verify dimensions, exact palette/meta, block/tile/entity counts, bounds, anchor standing volumes, structural and fluid invariants. Compare expected module occupancy with output occupancy so a missed layer cannot pass from nominal size alone.
6. **Offline route/geometry audit and repair.** Verify entry→prep→boss→reward→exit and every mandatory/alternative objective route. Check stair/slab metadata, doors, liquids, support, gravity, tile identity/access and largest supported form. Capture top/down and three silhouette views plus interiors. Label offline renders accurately. Repair and rerun affected full-output gates.
7. **Stage a NEW private world/runtime.** Verify exact copied source/build artifacts and all registry mappings. Apply all five reservations, prewarm only existing generated chunks in bounded batches, then boot isolated loopback Magma with the actual mod/plugin composition. Native assertions must use official current synthetic player login/lifecycle, not a fake unregistered body. Never launch the owner's client.
8. **Native geometry/physics.** Measure actual collision, support, stairs/doors/containers, gravity/fluid updates and vanilla block light. Check normal and large-form routes, recovery/teleport landing, boats and dimension behavior. Save/stop/restart and repeat measurements.
9. **Native gameplay.** Exercise solo and cooperative paths in every arena: real admission/kit use, each phase/puzzle/counter, accepted/canceled attacks, intended victory, reward choice, full-inventory outbox, exit, reset and a second run. Exercise negative/cancellation cases, reconnect and restart at active and reward-pending states. Validate native XP/drop coexistence and boss revival authenticity. Do not replace these with mocked event names or source regex checks.
10. **Load and presentation.** Measure server budgets during actual attack/add/hazard cases. Honor the closed-client preference; record graphical acceptance as pending until the owner permits a client session. When allowed, verify shaders ON/OFF, reduced effects, real boss/armor rendering, view distance and readable telegraphs on the actual client.
11. **Fix loop.** For every failure, retain its report/candidate, identify the root cause, repair source/config, regenerate affected assets, rerun the failing gate plus downstream affected checks and update exact artifact identities. Do not weaken the gate, drop one arena, rename a stub as a feature or reuse a passing report from different bytes.
12. **Package and publish.** Finish all five local outputs and safe source/tests/docs/evidence. Validate all manifests, hash every delivered file, verify a clean shutdown and publish reviewed original changes/checkpoint to the arena branch. Verify GitHub, record unresolved integration requests and write the final handoff for the Core owner.

Suggested command surface after implementation:

```text
python -m tools.arenas build --brief collaboration/arena-build-orchestration/arena-brief.json --all --output <NEW_PRIVATE_OUTPUT>
python -m tools.arenas audit --manifest <OUTPUT>/manifest.json --strict
python -m tools.arenas stage-world --manifest <OUTPUT>/manifest.json --source <IMMUTABLE_SOURCE> --dest <NEW_COPY>
python -m tools.arenas native-checks --candidate <NEW_COPY> --cases all
```

Build APIs must refuse existing output/world destinations by default, unsafe paths, symlink escapes, source drift and unmapped blocks. Provide explicit dry-run/intake modes. Existing `python -m tools.world_merge placement`, `audit`, `copy-world`, `paste-world` and `rollback` can form checked substeps; inspect their actual current CLI help before using arguments. Do not disable their limits to make a test green.

## 11. Minimum acceptance matrix

Every arena gets its own rows, source/template/build hashes and result links. Distinguish PASS, FAIL, NOT RUN and BLOCKED INPUT; never collapse missing tests into PASS.

| Gate | Concrete evidence |
| --- | --- |
| Full size / placement | Readback 500×250×500,62.5 M cells, min Y 3/max Y 252 and explicit local→world anchors |
| Complete construction | Layer occupancy, floors/interiors/supports, three silhouette views and room/route inventory |
| Palette / NBT | Actual runtime registry join, valid 12-bit IDs/meta, tile identities/coordinates/round trip and preserved unknown tags |
| Traversal | Connected mandatory/alternative routes plus native human/selected-form collision and recovery landings |
| Physics | Native fluid containment/gravity/support/doors/containers and post-update/save-restart results |
| Lighting | Native block-light measurements on required paths; actual dimension and shader scope labeled |
| Boss identity / behavior | Exact live registry/class/body, accepted phase outcomes, actual assets, native AI/cast/drop/XP/revival coexistence |
| Power/counterplay | Real kit acquisition/ownership, costs/cooldowns/dodge/guard/counters, two answers per mandatory mechanic |
| Persistence / reset | Restart/rejoin during active and pending reward; no duplicate actor/reward; clean second run; whole-copy rollback trial |
| Load | Measured TPS/tick percentiles, loaded chunks/entities/effects/memory and stable bounded queues |
| Owner inspection | Client stays closed until authorized; graphical/video results are actual only when observed |
| Delivery | Five private schematics/world artifacts, safe source/tests/manifests/docs, GitHub head verified |

Test meaningful boundaries: unknown schema, missing optional addon, changed palette, unloaded target chunk, occupied landing, canceled spawn/hurt/damage/death, absorption-only accepted damage, later unrelated loss/heal, boss revival/UUID replacement, wrong run/party/source, creative/spectator/fake rewards, full inventory, logout/restart, interrupted reset and a changed before-state. Keep all preexisting acceptance checks relevant to the changed integration.

## 12. Deliverables and final response

Private output directory, newly created on D:, must contain:

- Five clearly named full-size Alpha schematics, each with placement/palette/anchor/encounter/reward/reset metadata and SHA-256.
- A manifest joining source/config/seed, each output hash, exact runtime composition and every acceptance report.
- A new preview/candidate world containing all five sites, or a fully reproducible guarded staging package if a private runtime input is genuinely unavailable.
- Readable top-down/route maps, silhouette/interior/arena/reward previews and measured native results. Screenshots/videos only when actually captured with owner authorization.
- An install/run/rollback guide with exact tested commands, per-arena entry/checkpoint/exit coordinates and dependency identities.
- Failure/repair history and a precise pending-check list. Do not package reference decompilations, credentials or real player profiles into a shareable client/archive.

Repository deliverables are original arena generator/runtime source, data/configuration, meaningful tests, asset-generation/provenance records, safe result metadata and a current handoff. Keep large binaries/private references under ignored local or D: output; record safe hashes and recovery pointers. The Core owner performs the final shared-module/production integration after reviewing the arena branch; the arena worker must leave concrete tested artifacts, not a request for the Core owner to build them from scratch.

Final response: list the five sites and their actual completed scope; provide local artifact links and the verified GitHub branch/commit; summarize checks with their proper scope; state material failures or unavailable graphical/hosting acceptance plainly. Aim to finish this whole assignment in one run with self-repair. Do not promise perfection, 100% canon parity or deployment readiness from a render or a nominal 500-block box.
