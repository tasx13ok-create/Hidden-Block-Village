# Fable: detailed project and reference context


Generated UTC: 2026-11-13T18:08:16.381589+00:00. Base Git commit: `eb94b462136f91b55cc7bf8c76a76b9651bb40a8`.

This is a source-backed offline snapshot. Source sections retain their actual text, apart from LF line-ending normalization and Markdown wrappers. New source files can be ahead of the recorded base commit; the accompanying manifest pins every input byte hash. Read current Git state before acting on an old snapshot. Plans are not completed work.

Read this book to understand the product, the five dungeon campaigns, all 49 reference-mod dispositions, evidence quality, the academy target and actual native verification procedure. Use the separate memory book for current facts and hashes. No proprietary JAR code, world payload, player record or private NPC asset is embedded here.

Important version distinction: some native documentation retains the historical 0.3.9 fixture name and result filename. The current Core 0.3.10 receipt proves 1,261 assertions, including 103 academy assertions. Do not infer that every older example or proposed feature is current.

Contents: master plan; dungeon atlas; complete rebuild matrix; primary/community research and its limits; full academy specification; native test reproduction and evidence boundaries.


---

## Repository source: design/MASTER-PLAN.md

# Hidden Block Village: complete game plan

## Product promise

Build a living shinobi world inside Minecraft: train, earn a village's trust, join a team, develop a distinctive combat style, travel through places worth exploring, protect people, and grow into difficult responsibilities. The Naruto fantasy includes hand signs, elemental chakra, lineage techniques, eye powers, summons, seals, nine beast-inspired transformation paths, exams, village rivalries and consequential missions. Minecraft contributes tactile movement, readable blocks, inventories, crafting, gathering, player building, persistent worlds and emergent cooperation. Neither half should feel like an unrelated minigame pasted onto the other.

Target 1:1 **functional coverage** of the provided references through a measurable behavior ledger, followed by the enhancements below. A list of matching names, reskinned swords, generic mobs or registry entries is insufficient. Each ability needs its acquisition, inputs, effects, counterplay, presentation, persistence and multiplayer behavior. A full feature may intentionally have better balance or accessibility than its reference; record that deviation instead of calling it identical.

The objective is no mandatory paid mod-developer license for our original implementation. Author original code, geometry, textures, effects, dialogue and music, with provenance for every included asset and dependency. Do not transplant proprietary JAR payloads or server maps into the repository. Naruto names/characters and third-party branding have separate rights considerations; original code does not itself establish permission to publish every franchise asset. Keep technical IDs stable and make user-facing terminology a localization/content layer, so compatibility does not depend on copying another mod's implementation or art.

## What exists, and what does not

The supplied archive is 202,413,488 bytes, SHA-256 `c4d54acb14e719ec4ece244aea6843fa20a345b29ff3ebfe6f5c4e719cbd6fdd`, containing 49 JARs. Inventory metadata is not a behavior specification. Embedded versions disagree with several filenames; preserve both and use whole-file hashes as identity. Some packages have unresolved placeholders or no `mcmod.info`; those identities require loader/runtime observation.

Current original source spans eleven components: Core/Orders 0.3.9, Encounters 0.3.6, Rack 0.1.2, Residents 0.2.0, Residents Server Bridge 0.1.0, World Compatibility 1.0.0, HUD 0.14.1, Controls 1.4 simple, three compatibility helpers, Server Bootstrap 0.3.7 and Village Standards 0.3.1. On 13 November 2026, all 36 component outputs reproduced, the private Forge fixture passed 1,158 assertions, and a real OptiFine client joined a separate loopback inspection server. These observations do not establish full reference parity or gameplay completion.

Core currently registers 556 items; Encounters has 136 profiles across 70 mobs, 29 bosses and 37 companions, implemented through shared engines. Profile count does not prove unique enemy behavior or finished visuals. A historical bounded item audit reported 461/485 counterpart rows and 24 unmapped rows. A separate bounded mechanics audit still recorded 217 missing and 52 untested obligations out of 324; this is not an exhaustive audit of these 49 JARs. Do not convert any of those numbers into a percentage of the complete game.

The available local dungeon source/save is v9. Later chat evidence mentions v17, but those schematic bytes have not been recovered here. The private production `Naruto.test` world, approved inventory and complete server stage are still missing from the inspected locations. Friend-authored CustomNPCs creations have not arrived. The plan preserves those recovery tasks while allowing original gameplay/content work to proceed independently.

## The experience, minute to minute

The owner subsequently prioritized quicker access to exciting Naruto powers. [The Naruto fun brief](NARUTO-FUN-BRIEF.md) supersedes the following older pacing examples where they imply a long compulsory tutorial; deep academy/story content can remain optional. Its timings are playtest hypotheses, not verified current performance.

**First five minutes:** spawn at a village arrival court, receive a concise welcome, see a visible training ground, meet a mentor, practice a single movement and defensive technique, and acquire a journal through an actual interaction. No mandatory long exposition or unexplained wall of menus. A player who skips dialogue can still find the next action through a diegetic board and journal breadcrumb.

**First thirty minutes:** complete a civilian errand with a small choice, perform a nonlethal training duel, learn a basic elemental technique, gather/craft one useful provision, and earn a team-ready mission. Show the consequences of spending chakra, failing a dodge, protecting an ally and returning to a village. Graduation is earned through a practical demonstration and can be retried without a character reset.

**A normal hour:** prepare supplies, pick a mission, travel through a discovery route, encounter an optional diversion, use skills with a teammate, make one meaningful tactical or social decision, return for a reward and spend it toward an understandable next unlock. The village changes its dialogue and services as trust grows. Long travel has landmarks, gathering, patrols and encounters rather than uninterrupted empty terrain.

**Long-term play:** deepen a nature, specialize a clan path, master a weapon or companion, pass exams, lead mixed-role teams, clear the five dungeon campaigns, pursue rare transformations through authored trials, contribute to village projects and participate in opt-in faction conflicts. A rare mode changes choices and responsibilities rather than deleting ordinary players' usefulness.

## Scope and implementation boundaries

Keep Minecraft Java 1.12.2, Forge client 14.23.5.2859 and Java 8 runtime as the current compatibility target. JDK 25 runs Gradle 9.5/RFG; an Azul JDK 8 compiles game code. Do not port to 1.16.5 because an inspiration server uses it. A future port is a separate project decision after the save and content contracts are stable.

Organize by system ownership, not one replacement JAR per historical filename. Existing modules own their established responsibilities. Extend Core for player state, abilities, progression, equipment and reward authority; Encounters for actors and combat behavior; Residents for original dialogue and services; Rack for physical weapon storage; HUD/Controls for presentation and requests; World Compatibility for conservative migrations. Add new modules only when independent lifecycle, dependency direction or server/client separation justifies them.

New content should be data-driven where authoring benefits: ability definitions, lineage prerequisites, mission graphs, dialogue graphs, loot tables, resident schedules, encounter compositions, dungeon anchors and localization. Validate data before starting a world. Keep Java engines for authoritative state transitions, geometry/collision, networking and rendering. Do not invent a scripting language when a bounded JSON schema and existing engine suffice.

Server owns inventory, resources, unlocks, damage, target eligibility, cooldowns, rewards, NPC outcomes, travel and dungeon state. Client owns input requests, prediction that can be corrected, animation, audio, hints and cosmetic settings. Reuse current receipt/outbox protections. Build no second payment authority in a quest, boss or NPC script.

## Workstreams and deliverable shape

| Workstream | Delivery | Required proof |
| --- | --- | --- |
| Reference recovery | Hash-bound mod inventory; per-feature observations; explicit unresolved behavior | Exact archive coverage, playable reference observations or labeled documentation evidence |
| Combat and mobility | Ability engines, player resources, melee and movement, counterplay | Native protection/collision checks, two-client scenarios, real visual/input review |
| Character and progression | Academy, ranks, clans, natures, modes, mastery and respec | New-player route without OP; save/restart/reconnect; no duplicate grants |
| Living world | NPCs, services, faction relationships, schedules, travel and exploration | Authored interactions, navigation, dependency validation and persistent consequences |
| Content and economy | Missions, recipes, food, supplies, equipment, rewards and village projects | Complete acquisition/spend loop; no duplicated receipts; meaningful sinks |
| Dungeons | Five campaign spaces and encounter graphs with production-ready traversal | Build geometry, actual client walkthrough, encounter/reward/reset evidence |
| Art and experience | Original models, effects, sound, UI, animation and accessibility | Footage under vanilla and OptiFine, several aspect ratios, bounded effects |
| Operations | Matching distributions, migration, logging, backups and hosting | Fresh install, copied-world restart, mod handshake, rollback and Bisect acceptance |

## Content progression and milestones

**M0 â€” reliable production workspace:** keep the real running inspection session untouched unless the owner requests a change. Complete the 49-row matrix, inventory tool, skill and machine-readable backlog. Fix the observed empty-list JEI bridge fault as a separately versioned candidate, with a regression test and graphical smoke. Recover v17 and friend NPC exports when available; their absence must not stall unrelated engine work.

**M1 â€” one complete shinobi beginning:** implement a finished academy-to-first-mission slice in one village, using original residents, one elemental path with three distinct techniques, one weapon style, substitution, a teammate/summon introduction, a crafting/provision step and one short boss encounter. Every step is accessible through normal play. Deliver a new client/server test pair, localized help, a playable acceptance route and actual footage. This is the quality reference for subsequent expansion.

**M2 â€” five foundational natures and travel:** add Fire, Water, Earth, Wind and Lightning with a deliberate role spread, cross-nature interactions, surface traversal, stealth and support. Author the first five-village service layer, dependable party formation, discoverable travel, cooking and field recovery. Deliver distinct first-hour routes without duplicating the same quest with a new village name.

**M3 â€” lineage and specialist expression:** introduce the first eight fully authored clan/specialist paths, then expand through the complete requested roster and reference obligations. Each starts with a signature mechanic, tactical weakness, social/training progression and distinct presentation. Add combined natures only after parent-nature interactions are testable. Deliver eye-power, medic, weapon specialist, sensory and non-eye alternatives with fair routes to relevance.

**M4 â€” five dungeon campaigns:** Earth/Sakura, Cloud, Sand, Mist and Fire/Nether each get a full readable layout, local story, puzzle, tactical roster, multi-phase boss, optional mastery route and reusable expedition lifecycle. Start with Mist as the architectural and encounter-quality benchmark, then verify the other four individually. The [atlas](DUNGEON-ATLAS.md) is the content contract.

**M5 â€” advanced transformations and village play:** complete nine independently authored beast-inspired ward paths, sage/curse/gates systems, advanced eye trials, seal networks, faction responsibilities, exam tournaments and opt-in events. Test large silhouettes and effects with multiple players before scaling content counts. Add beast encounters as authored server events with bounded ownership and recovery rather than unattended world-destroying spawns.

**M6 â€” feature closure and release:** close every observed reference behavior or record a deliberate owner-reviewed departure; remove migration-only libraries after consumers are gone; validate friend NPC integration; finish original art and sound; execute the full production acceptance sequence. Public release is based on recorded scope and artifact identity, not an optimistic feature count.

A milestone ships when its end-to-end route works and its defects are resolved. Additional branches remain visible in the backlog. Do not label M1 a full Naruto game or fill unfinished content with nonfunctional placeholder rewards to inflate scope.

The concrete first campaign is [The Missing Dispatch](ACADEMY-CAMPAIGN.md). `SYS-PARITY` establishes the behavioral ledger and the first auditable slice; it does not require observing every feature of all 49 mods before original implementation can start. Continue per-feature observations as their corresponding work packages are taken up.

## Balancing and content-production strategy

Begin with small complete sets: five base natures with three high-quality introductory techniques each; eight complete initial lineage paths; one complete introductory mission chain; one authored boss per dungeon before adding variants. These are production sequence targets, not the ceiling of the requested game. Expand from demonstrated mechanics, not copied numerical catalogs.

Every new mission needs a changed player decision: escort with route choice, intelligence gathering with suspicion, recovery with an extraction window, defense with repair/triage, capture with nonlethal control, investigation with evidence, or an encounter with an exploitable enemy pattern. Palette-swapped kill counters are supporting duties, never the entire campaign. Every elite enemy needs a reliable counter that can be demonstrated by an appropriately equipped ordinary player.

Use creative mode to inspect assets, survival mode to validate acquisition, and dedicated multiplayer to validate authority. Those are different checks. Debug commands are not the production onboarding flow. Keep unfinished/test-only entries out of ordinary rewards and clearly mark dev inspection categories.

## Art, performance and deployment targets

The art direction is stylized Minecraft-scale Japanese fantasy: clear block forms, restrained original motifs, handmade material variation, expressive but readable effects, and no mandatory shader to understand a fight. Plan artwork by silhouette and material budget before adding surface noise. Large modes must retain hand, head, hip and tail relationships through crouch, jump, riding, water, death, perspective changes and network interpolation.

Initial engineering budgets are design targets to measure on named hardware: 20 TPS under the agreed player/encounter load; p95 server tick below 45 ms during the representative stress route; at least 60 FPS in a representative 1080p no-shader scene on the recorded midrange test GPU; reduced-effects mode with clear telegraphs; bounded per-player particles and sounds; no unbounded chunk loading, entity spawning or world scans. Record real hardware and results before treating these as achieved. Profile both CPU and GPU; never fix a server load problem by hiding it with client fog.

Keep OptiFine, Essential and other optional third-party components separate from the owned core and record their permission/distribution handling. A default build must have an inspectable gameplay path without optional shaders. Test the existing BSL setup and the intended Solas setup separately when exact files are available. A client menu screenshot cannot certify either.

## Definition of ready

Release readiness requires a complete matching server/client candidate; a hash-verified copy of the intended world; first start, clean save and restart; tested registry migration and NPC state; two real clients connecting; normal-player onboarding and reward progression; representative PvE and opt-in PvP; all five dungeon traversals and at least one restart during an expedition; original models/sound/UI reviewed in-game; tested bounded stress; a rollback trial; and Bisect start/restart/client connection on the actual deployed tree. Preserve source, prior packages, rollback material and original worlds throughout.

The current missing private inputs block only the acceptance steps that require them. Continue building and testing original systems in disposable instances, and report those results at their actual scope. Keep every delivered checkpoint and exact next action in the repository handoff.


---

## Repository source: design/DUNGEON-ATLAS.md

# Five dungeon campaigns: world and encounter atlas

## Production baseline and shared contract

This is the next design specification, not a new exported dungeon release. Available v9 source has Earth/Leaf, Cloud and Sand at 192Ã—192; Mist at 200Ã—200; Fire/Nether at 160Ã—160. All five newer v17 schematics have now been recovered and metadata-audited: Sakura/Cloud/Sand 288×288, Mist 200×200 and Nether 256×256. See `release/evidence/v17-intake-20261113.json` and the current handoff. Their geometry/routes/native runtime still require comparison and validation before replacement. Preserve the original arrays, world, anchors and exports, and author each revision in a separate output/save.

Keep Mist exactly 200Ã—200 at the designed settlement footprint. Earth, Cloud and Sand retain fractured torn-ground undersides. Mist sits in real surrounding water and Fire/Nether retains its own grounded/base treatment. Proposed Fire expansion is a 320Ã—320 compound with a central citadel roughly 192Ã—192; stage it by district so scale is earned through usable content and measured rendering cost.

Every dungeon needs: readable arrival; context from a resident or environmental clue; a safe preparation area; a primary route; at least one alternative route; a mid-route landmark; an optional challenge; an understandable puzzle; an encounter sequence that uses the space; a distinct boss; a reward scene; a short readable exit; and a clean reset/recovery plan. Proposed normal runs are 25â€“40 minutes, with 10â€“15 minute shortcuts after mastery. Times are playtest targets. No fifteen-minute walk back to a failed boss.

Campaign difficulty is progression-led. Expedition tiers are optional variants, not the replacement for authored normal difficulty. Support 1â€“4 players first, with scale data separated from geometry. Consider 5â€“6 only after navigation, target visibility, healing and effects have been stress-tested. Scaling adjusts patterns/add counts and selected defenses within bounds; it must not multiply health indefinitely. Solo players need puzzle alternatives and support options.

Each site has an immutable authored baseline plus a runtime state graph. An expedition records run ID, template version, seed, allowed participants, checkpoints, objective state, placed temporary blocks, actor ownership, boss state, reward receipts, disconnect deadline and reset status. Private runs occupy validated reserved cells with buffers or a proven isolated-world design; do not assume the shared v9 inspection world already provides instancing. Never paste/reset over player homes or unrelated expeditions.

## Earth / Leaf: The Rootbound Archive

**Identity:** a sacred sakura complex built around an ancient archive and exposed roots, torn from layered earth. The player feels the tension between cultivated serenity above and geological violence below. Use the existing broader sakura as an authored tree, with clear trunk roots and a branching hierarchy rather than a pink sphere.

**Arrival composition:** the first approach frames the tree behind a modest gate, then reveals the deeper fractured base from a side overlook. Keep the main trunk out of the exact central sightline until the second court. A river-stone path, moss edge and supported veranda connect inhabited rooms; the route is readable without luminous arrows.

**Room and route graph:** outer sanctuary â†’ keeper's veranda/preparation â†’ split garden routes â†’ archive threshold â†’ root chamber/midboss â†’ three reading courts â†’ fractured descent â†’ heartwood arena â†’ bloom balcony/reward â†’ return lift or stair. The alternate garden route trades a combat encounter for an observation puzzle and rejoins before the root chamber. A lower side gallery rewards controlled traversal without becoming mandatory for movement-impaired players.

**Spatial brief:** arrival court about 24Ã—28; readable secondary streets/verandas at least three clear blocks wide; ordinary doorways with actual player collision clearance; archive hall about 30Ã—22 with two usable floors; root chamber about 34Ã—30 with four broad lanes; boss arena about 44Ã—44, leaving the largest allowed player form and camera a tested envelope. Dimensions are design starting points, not unreviewed array edits.

**Architecture/material:** dark timber framing, warm plaster, gray-green tile and light stone foundation. Distinct main/secondary roof pitch, curved eaves made from native 1.12.2 stairs/slabs and deliberate corners. Brackets carry visible loads. Roofs connect to walls, and walls meet foundations. Archive rooms contain categorized shelves, scroll workstations, paper storage, ventilation and inhabited reading nooks. Do not cover every surface in trapdoors.

**Terrain:** three unequal major rock plates with different depths, torn joints, exposed soil/root bands and smaller subordinate fragments. Design load-bearing relationships around the tree. Root tips follow cracks and terminate deliberately. No uniform cones, identical stalactites or unconnected floating rubble. Maintain explicit walking routes around fractures.

**Puzzle:** restore the archive's seasonal records by interpreting garden/room clues and directing three seed marks. The order is communicated visually and in accessible text; every clue can be revisited. Wrong choices activate a short resettable defense, not permanent lockout. Co-op can divide observations; solo can perform the same sequence.

**Encounter language:** root-bound guards use braced frontal defense; archivist echoes interrupt sustained casts; seed carriers punish standing in the same lane. Fire is useful against a marked temporary root barrier but must not burn the authored tree. Earth cover protects against a sweep but reduces escape space; wind can move a seed objective only when explicitly eligible.

**Boss â€” Heartwood Warden:** phase one teaches a slow root fan with three safe lanes; phase two changes selected cover and creates a visible channeling target; phase three combines a bark break window with a moving safe sector. Every danger has a clear tell, short anticipation, actual active volume and recovery. Damaging the correct temporary root interrupts an attack; no forced reliance on one rare element. Rewards improve a chosen control/support specialty and unlock an archive crafting/knowledge service.

**Mood:** warm ambient daylight above, cool green pockets below, layered leaf/wood/water sound, sparse paper and bell cues. Night has supported lantern rhythm and room-local pools of light; the sakura silhouette stays legible. Falling petals are locally capped particles, not a permanent world-wide emitter.

## Cloud: The Thunderstep Monastery

**Identity:** high-altitude monastery and signal station across unequal fractured terraces. Emphasize light stone, deep blue roofs, open sky, distant thunder and vertical rhythm. Its distinctive experience is controlled exposure and line-of-sight management, not endless parkour over unavoidable death.

**Route graph:** sheltered arrival terrace â†’ bell court/training â†’ two bridge choices â†’ wind archive â†’ signal platform/midboss â†’ three tower interiors â†’ storm observatory â†’ thunder arena â†’ leeward rest court/exit. One bridge is direct and exposed; the other uses a covered interior and a puzzle. Each tower has a reason to enter and an exit that is not the same dead-end ladder.

**Geometry:** use a clear dominant tower with two subordinate masses. Stagger floor elevations with supported stairs and visible landings. Primary bridges provide at least three clear blocks and intermittent shelter; challenge ledges are optional. Place catch terraces/rescue volumes under intended jumps so a single latency spike does not erase a run. Sky bridges connect at believable structural bearings.

**Materials and detailing:** pale stone piers, blue-gray tiles, indigo accents, restrained brass/wood signal apparatus and cloud-weathered joints. Put water runoff, repair patches and hanging fabric where wind/support would make sense. Reuse shapes at different scales, not the same repeated tower module. Interiors include map tables, bell ropes, signal storage and occupied rest quarters.

**Puzzle:** align three grounded signal stations according to intervals heard/seen in the bell court. Sound clues have visual/subtitle equivalents. Players can inspect alignment from safe positions; combat does not reset a solved puzzle arbitrarily. A short alternate maintenance route can be unlocked by a prior mission.

**Encounters:** ranged sentinels lock a visible firing lane before release; dueling monks punish predictable rushing; a weather keeper alternates safe/crosswind zones. Knockback is bounded near exposed edges. Party healers have protected short windows and clear sight to allies.

**Boss â€” Storm Bell Abbot:** first teaches two sweeping arcs; second introduces grounded safe pads and a breakable channel; final rotates the arena's danger lane while preserving a stable refuge. Wind does not secretly remove all player control. Successful dodges and interruption create real punish windows. Reward a movement/precision technique, signal-network discovery and a meaningful traversal mastery route.

**Lighting and atmosphere:** use broad sky brightness plus warm sheltered rooms, not global fullbright. Fog is an optional presentation layer; paths, edges and attack tells remain visible without shaders and under reduced view distance. Thunder/screen effects are opt-out individually. Fracture strata under terraces follow the same torn-earth language as Earth/Sand without copying their shapes.

## Sand: The Windcarved Observatory

**Identity:** a sun-weathered scholarly/trading compound with turquoise/lapis wall bays, supported lamps, protected courts, carved stone reveals, wind channels and a hidden water system. The visual language is deliberate shinobi-fantasy architecture; the plan does not claim to reconstruct a particular historical culture.

**Route graph:** shaded caravan gate â†’ market/preparation court â†’ wind hall â†’ two courtyard wings â†’ buried cistern/midboss â†’ observatory ascent â†’ open-sky instrument court â†’ dune arena â†’ sheltered reward garden/exit. The underground section contrasts heat, texture and sound with the bright upper court.

**Material/structure:** a primary sandstone family with deliberate strata, recessed dark joints and occasional repaired masonry. Tile bands frame thresholds, not every block edge. Turquoise/lapis focus surfaces identify scholarly/service spaces. Domes/pointed bays need supported transitions to square rooms; roofs have drainage and access. Make stalls, sleeping quarters, storage, kitchens and cistern maintenance genuinely enterable.

**Terrain:** unequal fractured desert rock plates with exposed compacted sand, stone bands and scarps. Sand at edges has an explicit supporting layer in 1.12.2; it must not collapse on first update. Add small sheltered oases and wind-shaped vegetation where water/access justify them. Avoid a smooth sandstone bowl beneath the whole island.

**Puzzle:** route water through a bounded, resettable cistern network while using wind indicators to identify the observatory's intended calibration. The puzzle does not require destructive block mining or uncontrolled fluid updates. Co-op players can communicate indicator positions, but solo has a designed loop. Mark completed stages with physical changes and journal text.

**Encounters:** shielded caravaneers, bounded puppet lanes and sand marks that punish stationary casting. A support actor can be interrupted or isolated. Heat is a readable pressure mechanic with recovery shelters, not a hidden timer that punishes exploration. Magnetic/projectile interactions use the explicit eligible-material table.

**Boss â€” Glasswind Custodian:** phase one throws visible patterned volleys; phase two uses pillars as temporary lanes/shelter; phase three exposes a weak core after a commitment attack. Provide a weapon-based counter as well as nature interactions. Rewards improve sealing/projectile specialization, unlock a regional craft and reveal a caravan route.

**Atmosphere:** long shadows, sunlit amber stone, small blue/cool shaded interiors, restrained dust and cloth/rattle sound. Dust cannot hide ground warnings. Illuminate nighttime stairs and landings with lamps that actually cast vanilla block light. Show a second visual identity at dusk rather than applying the same orange bloom everywhere.

## Mist: The Tidal Covenant

**Identity:** a complete 200Ã—200 waterfront settlement in an ocean, supported by submerged piles, seabed foundations and a connected tidal sanctuary. It is not a floating rock island. Use the v9 1536-block ocean context as a local inspection baseline; the larger production coastline must be separately recovered/designed.

**Route graph:** moored arrival/fishing pier â†’ covered market walk â†’ boat workshop/preparation â†’ branching veranda network â†’ tide archive â†’ lower water gate/midboss â†’ three-tier observatory â†’ rear garden/quiet clue â†’ ritual hall â†’ tide-basin arena â†’ lantern quay/reward/exit. The settlement has ordinary homes and maintenance spaces as well as combat zones.

**Vertical layers:** submerged seabed/piles; working boat and dock level; dry raised circulation; furnished primary rooms; usable upper balconies/galleries; roof/observatory silhouettes. Every upper floor has a real internal route and purpose. A broad gallery cannot be supported by arbitrary posts placed in its boat channel. Main joists connect to perimeter bearings, and water approaches stay navigable.

**Architecture:** connected curved tile roofs, deep eaves, timber posts with visible brackets, shutters, asymmetric balconies, boat storage and drainage. Closed walls meet floor/roof; roof intersections have a designed valley/ridge; corner gardens connect to accessible veranda edges. Include a complete moored cedar hull with coherent waterline, gangway, rope/fence attachment and clearance from pilings. Furnish the upper rooms as rooms, not decorative empty shells.

**Water contract:** no fake water plane in place of real source blocks. Verify fluid containment, shore continuation, dry interiors, seabed contact, boat passage and collision. The v9 schematic has local top water Y=16 and origin anchor Y=21; placing into top source blocks at world Y=63 uses origin Y=68. Preserve those values only for the existing v9 template; every new export gets measured metadata and its own paste instructions. Include air deliberately when clearing occupied room volumes, but do not erase surrounding production terrain blindly.

**Puzzle:** read tidal markers, open a controlled sequence of floodgates and recover a covenant seal through a split dry/water route. It can be solved without a water-nature character; water specialists gain a useful alternative, not exclusive access. Gate changes are modeled by bounded authored states rather than simulating an unbounded ocean flood. A failed attempt returns the puzzle to an understandable checkpoint.

**Encounters:** mist silhouettes telegraph from sound plus visible lantern/weapon cues; boat-house defenders use short, readable flanks; a seal keeper creates temporary safe/unsafe water sectors. No essential fight takes place in a one-block doorway. Include dry regrouping platforms and clear drowning recovery routes. Fog never removes all target information.

**Boss â€” Covenant Tidekeeper:** first teaches a circular wake attack and safe islands; second adds paired seal nodes that can be handled sequentially solo or simultaneously in co-op; final alternates water and raised dry lanes with a large recoverable commitment. Clarify status and dispel rules; do not turn the screen into opaque blue particles. Reward a water/control mastery, harbor service, original mantle cosmetic and an optional rescue-story continuation.

**Atmosphere:** cool exterior air, warm inhabited rooms, pale dawn, restrained rain, reflected lantern lines and local water/wood/bell sound. Shader reflections are optional. Establish readability with actual block light and material contrast before BSL/Solas tuning. Capture day, wet dusk, night, underwater pile detail, room interiors and roofline views.

## Fire / Nether: The Ember Regent's Citadel

**Identity:** the largest campaign site: a 320Ã—320 proposed fortress district with an imposing central citadel, mask-like guardian gate, monumental approach, occupied lower service courts and a vertical sanctum. Its scale comes from nested spaces and useful districts, not a huge empty cube. Preserve the requested exclusion from torn-floating-ground redesign.

**District and route graph:** ash approach/preview overlook â†’ guardian gate â†’ forge court/preparation â†’ split barracks and archive districts â†’ furnace machinery/midboss â†’ great stair â†’ high galleries â†’ seal sanctum â†’ regent arena â†’ cooled vault/reward â†’ sheltered lift/return. The central gate has an open, intentional approach and a walkable entrance at a believable scale. Side towers connect through usable interiors.

**Materials:** charcoal/basalt-like stone families available in 1.12.2, dark Nether brick, restrained red accents, controlled warm metal suggestions and ash-colored repairs. Use native blocks or owned registered materials; do not specify modern Minecraft blocks unavailable to the target version. Lava is contained behind traversable safety margins and actually checked for spread/updates. Provide ventilation and believable support for furnaces and chimneys.

**Vertical plan:** lower maintenance and cistern-like cooling routes; military/service floor; formal halls; upper galleries; a high sanctum with a controlled reveal. Large halls have secondary doorways, supports, galleries and inhabited edge spaces. Ceilings use structure and ordered light groups, not random glowstone dots. Scaled guardian statuary has original geometry and a strong silhouette; avoid copying another server's sculpture.

**Puzzle:** balance three forge/seal stations to expose a safe route through the sanctum. Each station has a tactical challenge and a physical, readable state. A late puzzle failure should reopen a short retry route. Required items cannot be permanently lost in lava; their objective state can be recovered through the run record.

**Encounters:** armored guards with frontal tells; a furnace specialist who commits to an area blast; seal breakers who can be interrupted; upper-gallery archers with clear counter-paths. Use actual routes and shields instead of projectile saturation. A dramatic gate defense can occur in a bounded approach cell without editing the wider Nether.

**Boss â€” Ember Regent:** phase one teaches weapon sweeps and furnace lanes; phase two raises the importance of seal stations and safe galleries; final changes commitment patterns while leaving two distinct counter approaches. Arena heat and effects have a reduced setting. An advanced mode can alter boss movement/decision timing while respecting a solo route. Rewards include a transformation trial prerequisite, an original equipment variant and an authored epilogue, not just a crate roll.

**Dimension acceptance:** prior inspection arenas were in the Overworld. Actual Nether fog, lighting, sky, sound, travel rules, bed restrictions, route entry/exit and death recovery require explicit live Nether tests. Do not mark this campaign's atmosphere verified from an offline Blender render or Overworld screenshot.

## Cross-dungeon encounters, rewards and replay

Every arena has a reusable encounter sheet: participants and power band; safe entry; spawn anchors with validated collision; tells and attack volumes; cover; alternate player strategies; ally support opportunities; phase triggers; timeout/soft-enrage; boss leashing; disconnect policy; failure reset; rewards and exit. Bind all actors to the run ID and clean up only those actors. Friendly NPCs and unrelated players must not become boss targets.

Normal, veteran and mastery tiers use explicit configuration. A mastery route should test understanding, coordination or optional constraints rather than simply demand better loot. Rewards offer choice: technique lesson/mastery, craft material, village progress and original cosmetics. Deliver unique rewards once per eligible receipt; repeats provide useful bounded materials/currency. A full inventory uses the current paid-delivery queue. A player relogging during a reward scene cannot reroll or duplicate it.

Record contributions for healing, protection, objectives and control as well as damage. Someone who disconnected before the run must not claim a later first-clear; someone who contributed and briefly reconnects follows the documented grace rule. Restore a safe checkpoint and objective state after a restart, or explicitly refund/close an unrecoverable run. Keep the original authored template immutable.

## Block placement, lighting and visual acceptance checklist

1. **Baseline identity:** hash inputs; identify whether v9, recovered v17 or a new branch is being edited; preserve anchor/dimensions/palette metadata.
2. **Massing:** approve top-down route and three silhouette views before detailed furnishing. Every major mass has a purpose, entry and support.
3. **Connectivity:** check stairs/slabs orientation, roof valleys, bracket joins, rail corners, shutters, doors, bed orientation, chest access, support and actual collision. Use native 1.12.2 metadata/tile entities; no renderer-only repairs.
4. **Terrain:** inspect underside, strata, roots/piles/foundations and water continuity. Remove disconnected accidental fragments and structurally meaningless supports.
5. **Traversal:** run human-size and maximum supported-form collision envelopes from entry to exit and alternate routes. Test jumping separately from ordinary walking; provide a usable accessibility route. Validate all teleport/checkpoint landings.
6. **Lighting:** measure actual vanilla block light on safe paths (design target at least 8), identify deliberately dark encounter zones, suppress incidental spawns through explicit instance rules where needed, and anchor every visible fixture. Check stair edges and water reflections without shaders.
7. **Native persistence:** schematic/save round-trip; all doors/containers/signs/books/other tile entities; fluid updates; gravity blocks; save/stop/restart; edited-node reset and a second encounter run.
8. **Gameplay:** solo and cooperative run, resource shortage, wrong puzzle answer, boss reset, ally death, disconnect/rejoin, restart, full inventory reward and already-completed member.
9. **Visual review:** real client entry, room sequence, interior, roofline, underside, arena, night and intended dimension. Record shaders/hardware/settings. Offline renders support composition and are labeled as such.
10. **Polish:** no unintentional visual clutter, exposed room backs, empty inaccessible shells, repeated roof mistakes, floating lamps, unusable furniture, unreadable telegraphs, unexplained dead ends or missing reward exit. Record remaining issues explicitly.

Progress in passes: graybox â†’ support/roof/water correctness â†’ playable encounter graph â†’ material/art pass â†’ inhabited detail â†’ lighting/audio â†’ performance/accessibility â†’ final actual-client review. Detail belongs where it strengthens material, story, navigation or atmosphere. A thousand extra blocks cannot repair a broken route or an unfair boss.


---

## Repository source: design/MOD-REBUILD-MATRIX.md

# Complete reference-mod rebuild matrix

All 49 supplied JARs are accounted for. This is a feature replacement/compatibility plan; none of these rows establishes complete reference parity. Whole-file identities and embedded declarations are in [reference-mod-inventory.json](reference-mod-inventory.json).

Core gameplay and utility behavior are to be authored as owned systems. Dependency-only libraries can disappear after their consumers are replaced. Optional external integrations remain explicit; their presence locally does not establish redistribution permission. We are not proposing a gratuitous rewrite of Java, Forge, or every general-purpose library.

For each row, first collect actual behavior obligations. A filename or item label is not a complete mechanic specification. Rows whose identity is unresolved remain investigations rather than invented feature lists.

## 01. !mixinbooter-11.18.jar
- **Observed metadata:** mixinbooter 11.18
- **Disposition / owner:** `dependency_transition` — compatibility helpers.
- **Functional scope:** Mixin bootstrap requirements of retained consumers; remove dependency only after those consumers are gone.
- **Current boundary:** Not an owned replacement. Current optional integrations may bootstrap their own mixins.
- **Acceptance:** Start the approved roster without duplicate injection or unsupported bytecode; identify every remaining consumer.
- **Work package:** `MOD-001`.

## 02. [1.12.2]DamageIndicatorsMod-3.5.1.jar
- **Observed metadata:** DamageIndicatorsMod 3.5.1
- **Disposition / owner:** `original_utility_rebuild` — field-hud.
- **Functional scope:** Target health, status, damage feedback, bounded range and visibility rules.
- **Current boundary:** HUD exists; full reference indicator behavior has not been audited.
- **Acceptance:** Real two-player combat shows correct health/status without hidden-entity disclosure or stale target bars.
- **Work package:** `MOD-002`.

## 03. ahznb weapons plus 1.2.1.jar
- **Observed metadata:** ahznbswordsplus 1.2.1
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders arsenal.
- **Functional scope:** Every reference weapon: acquisition, reach, charged/secondary action, effects, model and persistence.
- **Current boundary:** Original arsenal and rack exist; weapon-by-weapon parity remains open.
- **Acceptance:** Each observed weapon has a distinct tested action, readable presentation and normal-player acquisition.
- **Work package:** `MOD-003`.

## 04. ahznbstweaks-2.0.jar
- **Observed metadata:** ahznbstweaks 1.7
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders and world-compat.
- **Functional scope:** Observe actual balance/progression/behavior patches and specify intended replacements individually.
- **Current boundary:** Filename 2.0 and embedded 1.7 disagree; behavior scope unresolved.
- **Acceptance:** Record each observed tweak and test its interaction with the selected owned systems; do not infer behavior from filename.
- **Work package:** `MOD-004`.

## 05. almirioncore-1.1.jar
- **Observed metadata:** examplemod ${version}
- **Disposition / owner:** `identity_investigation` — compatibility helpers.
- **Functional scope:** Resolve loader identity and actual dependency/behavior obligations before choosing an implementation.
- **Current boundary:** Embedded Example Mod and version placeholder are not a trustworthy identity.
- **Acceptance:** Observe runtime identity and dependent consumers; document a concrete replacement or justified dependency retirement.
- **Work package:** `MOD-005`.

## 06. AnbuBingoBook - 1.6.1.jar
- **Observed metadata:** anbubingobook 1.5.3
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders missions.
- **Functional scope:** Bounty dossiers, mission eligibility, target identity, contribution, payouts and public/private intel.
- **Current boundary:** Mission/journal foundations exist; complete bounty behavior is unverified.
- **Acceptance:** Correct eligible target and contributor receive exactly one payout; invalid targets and duplicate claims are refused.
- **Work package:** `MOD-006`.

## 07. AppleSkin-mc1.12-1.0.14.jar
- **Observed metadata:** appleskin 1.0.14
- **Disposition / owner:** `original_utility_rebuild` — field-hud and culinary.
- **Functional scope:** Readable hunger/saturation and food-preview information integrated into provisions.
- **Current boundary:** Culinary runtime exists; owned saturation/preview presentation is planned.
- **Acceptance:** Compare actual eating result to preview, respect GUI scale and avoid obscuring combat information.
- **Work package:** `MOD-007`.

## 08. Armourers-Workshop-1.12.2-0.51.0.jar
- **Observed metadata:** armourers_workshop 1.12.2-0.51.0
- **Disposition / owner:** `original_gameplay_rebuild` — owned appearance tools.
- **Functional scope:** Cosmetic equipment/wardrobe, original authoring and a documented import path for permitted user-created skins.
- **Current boundary:** Original ward renderer exists; AW editor/import parity is not implemented.
- **Acceptance:** Equip/save/reload cosmetics without altering combat equipment; import only explicitly supported formats and retain source provenance.
- **Work package:** `MOD-008`.

## 09. Baryon Expansion_beta2.jar
- **Observed metadata:** baryonexpansion 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders modes.
- **Functional scope:** Observed high-risk transformation mechanics, strain, activation, duration, counters and visuals.
- **Current boundary:** Original modes are a foundation, not verified Baryon parity.
- **Acceptance:** Costs and consequences persist through relog/death; opponents see readable tells; no free toggle exploit.
- **Work package:** `MOD-009`.

## 10. biju_unleashing-1.7.3.jar
- **Observed metadata:** biju_unleashing 1.7.3
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders wards and encounters.
- **Functional scope:** Nine beast-related progression paths, encounters, contracts, forms and distinct combat expression.
- **Current boundary:** Nine original ward profiles and shared encounters exist; complete beast loop/unique mechanics remain open.
- **Acceptance:** All nine have acquisition, distinct kit, counterplay, ownership recovery and real client animation evidence.
- **Work package:** `MOD-010`.

## 11. BorutoMod Addon byAnimeSupremeTeam v1.0.jar
- **Observed metadata:** borutomodaddononero 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders advanced content.
- **Functional scope:** Observe its specific advanced techniques/entities/items and map each obligation into owned engines.
- **Current boundary:** No complete artifact-specific parity claim.
- **Acceptance:** Each observed feature has an evidence row; no assumed modern-franchise roster is marked implemented.
- **Work package:** `MOD-011`.

## 12. Clumps-3.1.2.jar
- **Observed metadata:** clumps 3.1.2
- **Disposition / owner:** `original_utility_rebuild` — world runtime optimization.
- **Functional scope:** Bounded XP aggregation preserving the actual XP sum, pickup rules and progression effects.
- **Current boundary:** Not yet an owned verified equivalent.
- **Acceptance:** Stress test many XP awards with unchanged totals, party semantics and no duplicate pickup.
- **Work package:** `MOD-012`.

## 13. colorfulhealthbar-0.0.4a.jar
- **Observed metadata:** colorfulhealthbar 0.0.4a
- **Disposition / owner:** `original_utility_rebuild` — field-hud.
- **Functional scope:** Consistent player/target health bars with configurable layout, status shapes and accessibility.
- **Current boundary:** Original HUD exists; reference-specific visual behavior remains unverified.
- **Acceptance:** Readability at multiple aspect ratios and health scales; no duplicate conflicting bars.
- **Work package:** `MOD-013`.

## 14. Controlling-3.0.12.4.jar
- **Observed metadata:** controlling 3.0.12.4
- **Disposition / owner:** `original_utility_rebuild` — field-controls.
- **Functional scope:** Binding search, conflict detection, remap persistence, focus and input safety.
- **Current boundary:** Controls 1.4 simple exists; full binding-search parity remains planned.
- **Acceptance:** Resolve a conflict, save/restart and verify chat/search do not fire abilities.
- **Work package:** `MOD-014`.

## 15. CosmeticArmorReworked-1.12.2-v5a.jar
- **Observed metadata:** cosmeticarmorreworked 1.12.2-v5a
- **Disposition / owner:** `original_gameplay_rebuild` — owned appearance and rack.
- **Functional scope:** Separate cosmetic presentation from real armor stats with exact stack storage and synchronized visibility.
- **Current boundary:** Ward renderer exists; independent cosmetic-slot parity is not established.
- **Acceptance:** Equip/remove/death/reconnect preserves real combat gear, cosmetics and observer rendering without duplication.
- **Work package:** `MOD-015`.

## 16. CustomNPCs_1.12.2-(05Jul20).jar
- **Observed metadata:** customnpcs 1.12
- **Disposition / owner:** `compatibility_import_and_rebuild` — vale-residents.
- **Functional scope:** Original NPC behavior/dialogue/services/schedules plus careful import or optional retention of friend-authored definitions.
- **Current boundary:** Residents has three service roles; it is not a complete CNPC runtime.
- **Acceptance:** ID/reference remap, scripts, quests, trade, faction, appearance, pathing and copied-world restart all pass for imported content.
- **Work package:** `MOD-016`.

## 17. Essential_1-5-1_forge_1-12-2.jar
- **Observed metadata:** No usable mcmod.info declaration; confirm through loader/runtime evidence.
- **Disposition / owner:** `optional_external` — client integration.
- **Functional scope:** Keep optional local compatibility; implement owned server/party join UX without requiring a proprietary social/account service.
- **Current boundary:** Pinned local JAR works in inspection; no redistribution permission inferred.
- **Acceptance:** Owned gameplay works without Essential; optional enabled profile still starts and connects.
- **Work package:** `MOD-017`.

## 18. Fluidlogged-API-v3.3.3-mc1.12.2.jar
- **Observed metadata:** fluidlogged_api 3.3.3
- **Disposition / owner:** `compatibility_import_and_rebuild` — world-compat and water geometry.
- **Functional scope:** Legacy fluid/block data compatibility and required water-bearing architecture behavior.
- **Current boundary:** Mist uses real water; arbitrary legacy fluidlogged block migration is unverified.
- **Acceptance:** Preserve source water/block identity in a test copy; no flooding, silent deletion or invented universal conversion.
- **Work package:** `MOD-018`.

## 19. FTBLib-5.4.7.2.jar
- **Observed metadata:** ftblib 5.4.7.2
- **Disposition / owner:** `dependency_transition` — vale-orders party services.
- **Functional scope:** Map actual team/library consumers to owned party/state APIs; remove only after their obligations are covered.
- **Current boundary:** Current party/reward foundations exist, not complete FTBLib parity.
- **Acceptance:** Team membership and consumers survive restart/rejoin; no abandoned library dependency remains.
- **Work package:** `MOD-019`.

## 20. FTBQuests-1202.9.0.15.jar
- **Observed metadata:** ftbquests 1202.9.0.15
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders missions and residents.
- **Functional scope:** Versioned objective graphs, chapters, party credit, rewards, repeat rules, content authoring and safe import.
- **Current boundary:** Current missions are partial foundations; full quest editor/import parity is planned.
- **Acceptance:** Normal-player chain and co-op replay work through restart; each payout is idempotent; unsupported imported objectives are explicit.
- **Work package:** `MOD-020`.

## 21. geckolib-forge-1.12.2-3.0.31.jar
- **Observed metadata:** geckolib3 ${version}
- **Disposition / owner:** `dependency_transition` — owned animation renderer.
- **Functional scope:** Document retained animation consumers and replace necessary behavior with original rigs/controllers or reviewed dependencies.
- **Current boundary:** Original cuboid ward engine exists; it is not a general GeckoLib replacement.
- **Acceptance:** Every owned actor has a tested animation contract; no transitive runtime dependency is removed prematurely.
- **Work package:** `MOD-021`.

## 22. HiddenVillage-BijuuArmor-0.16.2-rc1.jar
- **Observed metadata:** hvbijuu 0.16.2-rc1
- **Disposition / owner:** `original_gameplay_rebuild` — owned ward renderer and legacy repair.
- **Functional scope:** Audit all nine armor silhouettes, tail/body transforms, native attachments, motion, clipping and mode hooks.
- **Current boundary:** Original Vale wards and separate recovered hvbijuu candidate are distinct; later private legacy candidate is not installed.
- **Acceptance:** Test all forms/movement states at several frame rates and perspectives; never substitute one renderer while claiming to fix the other.
- **Work package:** `MOD-022`.

## 23. ItemFilters-1.0.4.2.jar
- **Observed metadata:** itemfilters 1.0.4.2
- **Disposition / owner:** `original_utility_rebuild` — crafting and mission matching.
- **Functional scope:** Bounded item/tag/metadata matching for recipes, objectives and service filters.
- **Current boundary:** Existing owned crafting/state checks cover some use cases; complete filter language is not established.
- **Acceptance:** Positive/negative metadata/NBT cases, unknown items and changed tags behave deterministically without arbitrary expressions.
- **Work package:** `MOD-023`.

## 24. jei_1.12.2-4.22.0.1034.jar
- **Observed metadata:** jei 4.22.0.1034
- **Disposition / owner:** `original_utility_rebuild` — owned codex and optional integration.
- **Functional scope:** Search, recipe/use discovery and item visibility, with retained optional JEI support during development.
- **Current boundary:** JEI is in inspection; empty-list hide bridge warning is an observed P0 fix.
- **Acceptance:** Empty/nonempty hide bridge works; owned recipe/codex search gives accurate acquisition and input handling.
- **Work package:** `MOD-024`.

## 25. JustEnoughResources-1.12.2-0.9.2.60.jar
- **Observed metadata:** jeresources 0.9.2
- **Disposition / owner:** `original_utility_rebuild` — owned codex.
- **Functional scope:** Explain observed resource locations, mob drops and authored loot ranges without exposing hidden content.
- **Current boundary:** Planned; do not invent observed drop tables.
- **Acceptance:** Displayed sources match actual configured loot/world generation and discovery eligibility.
- **Work package:** `MOD-025`.

## 26. JutsuAddon v1.0.jar
- **Observed metadata:** jutsuaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders techniques.
- **Functional scope:** Observe each additional technique, its acquisition, timing, geometry, costs and visual identity.
- **Current boundary:** Current scroll engines cover several families; exact addon parity remains open.
- **Acceptance:** Each mapped technique completes the universal behavior record and native/two-client scenarios.
- **Work package:** `MOD-026`.

## 27. kabutoaddon 5.5.8-beta.jar
- **Observed metadata:** kabutoaddon 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders and encounters.
- **Functional scope:** Audit its actual techniques, actors, upgrades and progression rather than inferring them from the title.
- **Current boundary:** Filename 5.5.8 and generic embedded 1.0.0 cannot establish mechanics coverage.
- **Acceptance:** Every observed actor/technique gets acquisition, unique mechanics, persistence and presentation proof.
- **Work package:** `MOD-027`.

## 28. Naruto-Dungeon-Pictures-1.12.2-1.0.0.jar
- **Observed metadata:** hvdungeonpictures 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — village-standards.
- **Functional scope:** Five village picture items, placement/support, dimensions, variants, saved entity identity and original art.
- **Current boundary:** Standards 0.3.1 has verified historical binary contract; actual production-world migration remains open.
- **Acceptance:** Existing pictures load in a copied world with correct village, support and recovery; replacement art is provenance-reviewed.
- **Work package:** `MOD-028`.

## 29. NarutoMod-1.12.2-0.3.1.beta.jar
- **Observed metadata:** narutomod 0.3.1-beta
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders, encounters and systems.
- **Functional scope:** Full shinobi base: natures, clans, eyes, combat, movement, modes, actors, items, blocks and progression.
- **Current boundary:** 556 current items and partial mechanics do not establish base-mod parity.
- **Acceptance:** Complete per-feature ledger and normal-player campaign with unique mechanics, multiplayer and visual evidence.
- **Work package:** `MOD-029`.

## 30. NarutoMod-Addon_byAnimeSupremeTeam-v1.0.jar
- **Observed metadata:** narutomodaddononero 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders content.
- **Functional scope:** Audit actual addon-specific equipment/techniques/entities and integrate distinct obligations.
- **Current boundary:** Some counterpart/encounter foundations exist; source-artifact mapping needs observation.
- **Acceptance:** No merged generic placeholder can close multiple unique obligations; verify actual configured behaviors.
- **Work package:** `MOD-030`.

## 31. NarutoModAddon-0.3.1- V1.3.jar
- **Observed metadata:** narutomodaddon 1.1
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders progression.
- **Functional scope:** Observe chakra/stat/progression additions and their interactions with the base mod.
- **Current boundary:** Embedded 1.1 differs from filename V1.3.
- **Acceptance:** Resource scaling, purchase/unlock costs, reset behavior and save/relog are specified and tested.
- **Work package:** `MOD-031`.

## 32. nmee-0.2.2-beta.jar
- **Observed metadata:** nmee 0.2.1-beta
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders and compatibility.
- **Functional scope:** Audit enhanced-extension fixes/features and map each concrete behavior.
- **Current boundary:** Embedded 0.2.1-beta differs from filename 0.2.2-beta; no exhaustive obligation list yet.
- **Acceptance:** Reference observations distinguish a bug fix from a new ability; each accepted replacement has a regression case.
- **Work package:** `MOD-032`.

## 33. OptiFine_1.12.2_HD_U_G5.jar
- **Observed metadata:** No usable mcmod.info declaration; confirm through loader/runtime evidence.
- **Disposition / owner:** `optional_external` — client rendering integration.
- **Functional scope:** Retain optional local shader/rendering compatibility; owned game readability, effects and settings work without it.
- **Current boundary:** Exact local pinned JAR initialized and connected in inspection.
- **Acceptance:** Actual client tests vanilla and OptiFine separately; no private JAR is silently bundled into public distribution.
- **Work package:** `MOD-033`.

## 34. Patchouli-1.0-23.6.jar
- **Observed metadata:** patchouli 1.0-23.6
- **Disposition / owner:** `original_utility_rebuild` — owned codex.
- **Functional scope:** In-world readable journal/codex, discovery gates, localization, navigation and content authoring.
- **Current boundary:** Current technique codex/mission journal are foundations, not a full book framework.
- **Acceptance:** A novice can find acquisition, recipe and mission help without OP/debug commands; localization and navigation persist.
- **Work package:** `MOD-034`.

## 35. RinneganAddon v1.3.jar
- **Observed metadata:** rinneganaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders advanced eyes.
- **Functional scope:** Observe path-specific abilities, sustained summons/forces, unlocks, drains, geometry and counters.
- **Current boundary:** Advanced eye-path parity is not established by current generic effects.
- **Acceptance:** Each path has distinct tested mechanics, sustained cost, loaded-chunk/protection limits and observer visuals.
- **Work package:** `MOD-035`.

## 36. SealsAddon v1.0.jar
- **Observed metadata:** sealsaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders sealing.
- **Functional scope:** Marks, placed seals, bindings, travel/storage/ritual semantics actually observed in reference.
- **Current boundary:** Waymark and some scroll effects are partial foundations.
- **Acceptance:** Ownership, placement, multiple targets, removal, expiry, protection and restart behave deterministically.
- **Work package:** `MOD-036`.

## 37. SharinganAddon v1.0.jar
- **Observed metadata:** sharinganaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders eye progression.
- **Functional scope:** Observed eye stages and abilities, copied techniques, counters, strain and evolution paths.
- **Current boundary:** No full eye-stage/mechanic parity claim.
- **Acceptance:** Unlocks, eligible copy whitelist, information limits, form conflicts and two-client readability pass.
- **Work package:** `MOD-037`.

## 38. shinobiculinary-1.2.0.jar
- **Observed metadata:** shinobiculinary 1.1.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders culinary.
- **Functional scope:** Regional ingredients, farming, processing, cooking, stations, recipes, food benefits and trade.
- **Current boundary:** Owned culinary runtime/items exist; full recipe and machine-behavior audit remains open.
- **Acceptance:** Each recipe/station has real inputs/outputs, occupied/full cases, unload/restart and valid normal-player acquisition.
- **Work package:** `MOD-038`.

## 39. SpringAddon7.0.jar
- **Observed metadata:** ahznbcursemarkaddon 5.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders curse and advanced content.
- **Functional scope:** Audit observed curse-mark and other registered content with activation/risk/progression/visual contracts.
- **Current boundary:** Embedded ahznbcursemarkaddon 5.0 differs from SpringAddon7.0 filename.
- **Acceptance:** Distinct stages/costs/counters persist and render correctly; no inferred feature is marked complete.
- **Work package:** `MOD-039`.

## 40. stg-1.12.2-1.2.3.jar
- **Observed metadata:** stg 1.12.2-1.2.3
- **Disposition / owner:** `original_utility_rebuild` — vale-orders combat trace.
- **Functional scope:** Allow intended melee through replaceable grass while preserving real obstacle/target order.
- **Current boundary:** Existing owned attack geometry needs a dedicated foliage parity scenario.
- **Acceptance:** Grass does not block a permitted target; walls, allies and nearer collision still prevent an invalid hit.
- **Work package:** `MOD-040`.

## 41. supermartijn642configlib-1.1.8-forge-mc1.12.jar
- **Observed metadata:** supermartijn642configlib 1.1.8
- **Disposition / owner:** `dependency_transition` — field-controls and configuration.
- **Functional scope:** Identify actual consumers and replace their required settings workflows with owned validated schemas.
- **Current boundary:** Not an owned library replacement; removal depends on consumer audit.
- **Acceptance:** Invalid values are bounded; saved settings reload; no active consumer references a removed library.
- **Work package:** `MOD-041`.

## 42. TravelersBackpack-1.12.2-1.0.35.jar
- **Observed metadata:** travelersbackpack 1.0.35
- **Disposition / owner:** `original_gameplay_rebuild` — owned inventory equipment.
- **Functional scope:** Field pack storage, upgrades, carried/displayed state and any observed fluid/tool behavior.
- **Current boundary:** Six-slot rack is not backpack parity.
- **Acceptance:** No nested-container duplication; full inventory/death/drop/reconnect preserves every exact stack and upgrade.
- **Work package:** `MOD-042`.

## 43. VariedCommodities_1.12.2-(31Mar23).jar
- **Observed metadata:** variedcommodities ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — owned world decoration and compatibility.
- **Functional scope:** Original furnishings/props and required gameplay blocks, plus conservative legacy saved-data support.
- **Current boundary:** Canonical combat-prop quarantine exists; complete decoration/block migration does not.
- **Acceptance:** Usable original furnishings and accurate collision; unknown legacy blocks never silently become air in a preserved world.
- **Work package:** `MOD-043`.

## 44. warpmod-1.12.2-1.0.1.jar
- **Observed metadata:** warp_mod 1.0.0
- **Disposition / owner:** `original_utility_rebuild` — owned travel service.
- **Functional scope:** Permissioned destinations, named travel, cost, cooldown, combat lock and failure recovery.
- **Current boundary:** Waymark is a separate personal technique, not complete named-warp parity.
- **Acceptance:** Private/unknown/unsafe destinations fail without charging; successful travel charges once and respects protection.
- **Work package:** `MOD-044`.

## 45. Waystones_1.12.2-4.1.0.jar
- **Observed metadata:** waystones 4.1.0
- **Disposition / owner:** `original_gameplay_rebuild` — owned travel and discoveries.
- **Functional scope:** Discoverable physical travel network, bound destinations, ownership and regional progression.
- **Current boundary:** Planned; current inspection teleport book is not a finished travel network.
- **Acceptance:** Discovery, destination destruction, rename, transfer, costs, cooldown and restart all preserve correct state.
- **Work package:** `MOD-045`.

## 46. worldedit-forge-mc1.12.2-6.1.10-dist.jar
- **Observed metadata:** worldedit 6.1.10-SNAPSHOT;e6b38e8
- **Disposition / owner:** `authoring_tool_and_runtime_rebuild` — owned template/expedition tools.
- **Functional scope:** Template import/export, anchors, safe placement, validation, bounded restoration and required authoring workflow.
- **Current boundary:** Offline schematic generators and checks exist outside this source checkout; reusable runtime template service is planned.
- **Acceptance:** Round-trip native metadata/tile entities, refuse unsafe targets, preserve rollback and reset only owned encounter cells.
- **Work package:** `MOD-046`.

## 47. xaerolib-forge-1.12.2-1.7.3.jar
- **Observed metadata:** xaerolib 1.7.3
- **Disposition / owner:** `dependency_transition` — owned map and configuration.
- **Functional scope:** Map dependency obligations follow the owned map/discovery UI; retain locally only for explicit compatibility tests.
- **Current boundary:** No owned replacement or redistribution inference.
- **Acceptance:** Owned discovery/map features function without the library; any retained map consumer has matching required dependencies.
- **Work package:** `MOD-047`.

## 48. xaerominimap-forge-1.12.2-26.6.0.jar
- **Observed metadata:** xaerominimap 26.6.0
- **Disposition / owner:** `original_utility_rebuild` — owned exploration HUD.
- **Functional scope:** Readable local navigation, discovered landmarks and consensual party waypoints.
- **Current boundary:** Planned; avoid inheriting radar/hidden-entity disclosures by default.
- **Acceptance:** Map orientation/scale/visibility and saved waypoints work; inaccessible rooms/enemies remain undisclosed.
- **Work package:** `MOD-048`.

## 49. xaeroworldmap-forge-1.12.2-1.47.0.jar
- **Observed metadata:** xaeroworldmap 1.47.0
- **Disposition / owner:** `original_utility_rebuild` — owned atlas.
- **Functional scope:** Persistent discovered-world atlas, dimension-aware routes, labels and party sharing.
- **Current boundary:** Planned; no existing full atlas parity.
- **Acceptance:** Discover/save/reload/dimension switch and bounded map storage work without scanning unloaded world terrain.
- **Work package:** `MOD-049`.


---

## Repository source: design/REFERENCE-RESEARCH.md

# Reference research and evidence ledger

Research date: 13 November 2026. The supplied 49-JAR archive is the concrete feature-recovery input; IceeRamen is experience inspiration. The proposed mechanics, tuning, stories, original bosses and layouts are our designs, not claims about another creator's implementation.

## Naruto identity, power and pacing research

The owner subsequently prioritized unmistakable Naruto identity, exciting powerful armor/movesets, world reactions and shorter mandatory onboarding. The design response is [the Naruto fun brief](NARUTO-FUN-BRIEF.md). These are product decisions informed by the sources below, not proof of player satisfaction or implemented parity. The pages were consulted on 13 November 2026. No paid access, private server resources or private Discord material was used.

| Source | Directly available evidence | Decision informed; limits |
| --- | --- | --- |
| [IceeRamen project description](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod) | Creator describes progression trees and a combat bar. | Make equipped actions discoverable; retain Minecraft's ordinary inventory controls rather than automatically copying its E-key choice. Creator description is not a measured usability result. |
| [IceeRamen 3.0.0](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod/files/9184507) | Creator describes story-earned outfits, set effects and signature techniques. | Connect equipment to acquisition and real moves; avoid a collection of stat-only reskins. Its mission counts do not establish our scope or completion. |
| [IceeRamen 3.0.2](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod/files/9250830) | Creator describes private party dungeons, difficulty selection, outfit abilities and specialized NPC techniques. | Treat dungeon lifecycle, rewards and enemy identity as part of the feature. Do not copy its rooms, actors or progression sequence. |
| [IceeRamen 3.0.6](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod/files/9516780) | Creator describes staged gates, distinct effects, an escalating boss and reduced-flash/motion settings. | Escalation needs audiovisual identity and accessibility; our exact costs, duration and encounters require independent tuning. |
| [Official Shinobi Striker developer letter 13](https://naruto-official.com/en/news/01_2603) | Developer explains Jiraiya's specialized active moveset, additional inputs and an ultimate with positioning/down-state limitations. | A transformation should change actions and decisions, with readable opportunities to respond. This is a game adaptation, not a universal canon mechanic. |
| [Official Naruto Sage Mode release](https://en.bandainamcoent.eu/naruto/news/naruto-uzumaki-sage-mode-now-available-naruto-boruto-shinobi-striker) | Publisher names specific techniques, a beast-state finisher and themed equipment. | A named mode needs recognizable actions and a coherent visual identity. No claim that our existing potion/armor engines reproduce it. |
| [Official STORM Connections overview](https://en.bandainamcoent.eu/naruto/news/naruto-x-boruto-ultimate-ninja-storm-connections-discover-the-latest-information-about) | Publisher describes practice and accessible controls alongside the normal controls. | Offer a simple route to expressive actions; do not make excessive keybinds the price of power. This does not prove an automatic combo system fits Minecraft. |
| [Official Baryon Mode database](https://naruto-official.com/en/news/01_1895) | Official account describes life-consuming power and contact effects. | Keep canon consequence and repeatable multiplayer adaptation explicit. Do not silently turn every dramatic form into permanent free stats. |
| [Sucker Punch environment art lead: crafting Tsushima](https://blog.playstation.com/2020/07/09/crafting-the-world-of-tsushima/) | Primary account describes regional identity, reacting foliage/cloth, navigation, environmental motion and authoring tools. | Coordinate small environmental cues; create reusable original authoring tools instead of promising hand placement of every detail. We did not inspect its proprietary tools. |
| [Valve: AI Systems of Left 4 Dead, slides 77–81 and 91](https://cdn.cloudflare.steamstatic.com/apps/valve/2009/ai_systems_of_l4d_mike_booth.pdf) | Developer presentation distinguishes fluctuating encounter pacing from changing difficulty and keeps authored bosses outside ambient pacing. | Alternate exciting action with meaningful breathers; vary ambient activity without secretly canceling a player's earned strength. This is our transfer hypothesis, not Naruto-specific player research. |
| [Welcome to IceeRamen Tsukuyomi](https://www.youtube.com/watch?v=AyZyJEJ8XCA) | Search-indexed creator title/description; direct fetch failed again. | A footage-review lead only. No watched sequences, timestamps or measured combat timing are claimed. |

## What public community material actually supports

This was a bounded qualitative scan, not a representative survey. Search results were dominated by repeated server advertisements; duplicates are not independent players or votes. Creator marketing is labeled as such. Distinguish the owner's preferences, individual complaints, advertised market positioning and our design inference.

| Signal and source | Evidence category | Actionable hypothesis |
| --- | --- | --- |
| A player explicitly seeks to bypass training/grinding for weapons and jutsu in [this JinGames support thread](https://main.jingames.net/forums/topic/naruto-c-mod-nbtedits-problem/). | One direct player preference, dated 2020; not proof that every player dislikes progression. | Add clearly separated advanced-power practice and satisfying early unlocks; retain optional mastery progression. |
| [Shinobi Reborn's author](https://www.reddit.com/r/Naruto/comments/1qexupw/shinobi_reborn_a_modded_naruto_minecraft_server/) positions the server against forced extensive RP and unfair gameplay. | Server advertisement and its author's preference. | Roleplay can enrich play without making social attendance/staff approval the only route to core abilities. Validate with actual players. |
| [Naruto Shinobi Anarchy's author](https://www.curseforge.com/minecraft/modpacks/naruto-shinobi-anarchy-1-12-2) complains about disabled explosions, blocked mining and constrained survival freedom. | Creator positioning; not permission to grief this project's village/world. | Keep building/mining and opt-in destructive combat zones. Clearly distinguish protected villages from wilderness/arena rules. |
| [Daikage's author](https://www.reddit.com/r/MinecraftServers/comments/1v1z2zl/12111_naruto_daikage_custom_jutsu_clans_ninja/) advertises taijutsu combos, movement, inventory tools and reactive rogue/guard systems. | Creator feature claims, not independently tested behavior or user demand counts. | Prioritize how weapons, jutsu, traversal and village responses connect. Do not equate a listed feature with quality. |
| [AHZNB's creator description](https://modrinth.com/mod/ahznbs-shinobicraft) calls out future mission work to support progression. | Creator's stated design direction. | Missions should offer an enjoyable progression route; avoid mandatory repetitive training as the only acquisition path. |
| [A creator's Naruto C pack notes](https://www.curseforge.com/minecraft/modpacks/naruto-c-the-desire-to-be-stronger/files/8170151/additional-files) explicitly warn about conflicting bindings. | Search-indexed author setup text; not a tested current installation. | Make bindings coherent and configurable, prevent chat/search casting, and explain the current action set in-game. |

Translate these into observed playtest questions: how long until the first exciting action, can the player explain their weapon combo, does a transformation change choices, can a solo player progress, can a group recover after disconnect, can players still build, and does a world reaction have an understandable cause? Do not claim that this scan proves what all Naruto Minecraft players want. Continue collecting direct player reports during the sprint while implementation proceeds.

## Creator sources consulted

| Source | Evidence accessible | What it supports |
| --- | --- | --- |
| [Iceeman: Welcome to IceeRamen](https://www.youtube.com/watch?v=yzY1IspaQ0M), 8 May 2021 | Search-indexed creator title/description; direct playback fetch failed | Describes village leadership, ANBU and jinchuriki roles. Motivates meaningful village identity and social responsibility. No scene/timing claims. |
| [Iceeman: A New Uchiha Surfaces](https://www.youtube.com/watch?v=wEI3bJ0QKs4), 27 April 2022 | Search-indexed creator title/description; direct playback fetch failed | Describes a character story, eye/bloodline progression and beasts. Motivates authored character arcs rather than isolated menu unlocks. |
| [Creator's Evolution project page](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod) | Opened description and metadata | Describes progression trees, chakra combat, clans, natures, weapons and custom interface. The reference targets 1.16.5; our established platform remains 1.12.2. Its All Rights Reserved listing supplies no source/assets permission. |
| [Creator's 3.0.0 release notes](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod/files/9184507) | Search-indexed creator-authored release text | Describes village stories, rank exams and cooperative progression. Product lesson: authored story/training can carry progression. Its exact content quantities are not our promised scope. |
| [Creator's 3.0.2 release notes](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod/files/9250830) | Search-indexed creator-authored release text | Describes a private party dungeon with rooms, a boss, difficulty settings and reward reveal. Product lesson: lifecycle/team recovery matter as much as geometry. Our layouts, stories and bosses are original. |
| [Creator's 3.0.6 release notes](https://www.curseforge.com/minecraft/mc-mods/iceeramen-naruto-mod/files/9516780) | Opened release page, uploaded 8 November 2026 | Describes an escalating gates-themed dungeon and transformation progression. Product lesson: phases can have environmental expression and escalating commitment. Do not copy its map, actors, sequence or rewards. |
| [Creator's Evolution SMP pack](https://www.curseforge.com/minecraft/modpacks/iceeramen-evolution-smp) | Opened pack page | Confirms a creator-published pack and 1.16.5 context. It is not the supplied 1.12.2 archive or permission to redistribute its components. |

No complete video was played or visually timestamp-reviewed in this pass. Do not describe this as hours of watched footage, hundreds of visual references or measured combat timing. Playback access failed; do not invent scene observations or bypass restrictions.

## Footage review queue

When playback is available, start with the linked creator videos and follow the creator's own public playlist/channel. Record URL, title, date, actually viewed timestamp interval, scene, player action, feedback, counterplay/failure, Minecraft integration, proposed improvement and related backlog ID. Keep direct observation separate from inference. Do not supply a timestamp before watching that interval.

Review concrete questions:

1. How does a novice find a useful next action without administrator guidance?
2. What does chakra expenditure communicate before, during and after activation?
3. Can a spectator identify the tell, counter window and why a player won/lost?
4. How do movement, hand signs, weapons, building and ability selection coexist?
5. What distinguishes a lineage in actual decisions rather than its label?
6. How do teammates contribute outside damage and understand shared rewards?
7. Where do village life, travel and training make downtime worthwhile?
8. What actually happens on mission failure, boss reset, death and reconnect?
9. Which landmarks, terrain/roof silhouettes and lights guide a real player?
10. Which effects obscure combat, menus require excess inputs or loops become repetitive?

Translate observations into original solutions and testable scenarios. Do not acquire private server files or reuse footage as the game's art/audio.

## Existing project evidence

Use `docs/recovery-status.md`, `docs/current-handoff.md`, the gameplay guides, Encounters/Rack documentation and `release/evidence/windows-desktop-20261113-verification.json` for existing-work claims. Available dungeon baseline: the older workspace's `README-v9.txt`, authored v9 arrays/scripts and prepared save. The new archive audit supplies metadata/CRC/hash results, not mechanics or license clearance.

The historical 324-obligation audit was bounded, and its complete private records are not present here. Recover/repeat observations rather than fabricate missing rows. Registered items and shared-engine profiles need behavioral acceptance before they close a reference obligation.


---

## Repository source: design/ACADEMY-CAMPAIGN.md

# First playable campaign: The Missing Dispatch

Core 0.3.10 now implements a smaller introductory runtime route: see [the playable candidate guide](../docs/academy-0310.md). The full specification below remains the M1 target; the six basics and two contract prototypes do not fulfill its final nature-specific lessons, escort branches or graduation story.

This is an original authored campaign specification for milestone M1. IDs and events below are proposed content contracts, not already registered runtime features. Build the first village graybox and wire real server events before marking any mission playable. Display names can use the selected village localization; technical IDs remain stable. Introduce the wider shinobi conflict through a local problem that first appears to be a misdelivered dispatch.

## Cast, places and tone

Original role/name drafts: Aki, the practical academy mentor; Ren, a courier who made a consequential mistake; Sora, a field medic who values protection over spectacle; Jun, the quartermaster responsible for supplies; and the dispatch keeper, whose missing records connect the village to the later Mist campaign. These are new characters and dialogue premises, not copied anime scenes. Friend-created NPCs can replace matching roles only through a reviewed content mapping.

Required playable spaces: arrival gate, mentor's court, forgiving movement route, practice ring, provision kitchen, civilian street/clinic, two short courier routes, a roadside encounter yard, a signal shelter and the dispatch office. Give every place an arrival view, clear exit and useful function. Connect destinations through a walkable loop with a short return route; do not scatter tutorial steps across a large empty map.

Tone: inhabitants have jobs and understandable stakes. Ren's mistake has a repair path; choosing to help an injured civilian is acknowledged later. The tutorial never insults a player for failed inputs. A player who skips dialogue still gets a short objective, destination and current reason in the journal.

## Mission graph and runtime contract

Default chain is A01 Ã¢â€ â€™ A02 Ã¢â€ â€™ A03 Ã¢â€ â€™ A04 Ã¢â€ â€™ A05 Ã¢â€ â€™ A06 Ã¢â€ â€™ A07 Ã¢â€ â€™ A08 Ã¢â€ â€™ A09 Ã¢â€ â€™ A10. A07 has two authored branches that rejoin. Returning players can resume completed stages without repeating unique rewards. Replays explicitly distinguish practice credit from first completion.

Each state transition checks authoritative player identity, mission version, prerequisite state, target/anchor ID and eligibility. Objective events include a sequence/run ID and must be accepted at most once. If a required actor or item disappears, a mission service reconstructs the pending objective from state instead of resetting the whole character. No client button or screenshot constitutes completion.

| ID | Action and authored experience | Authoritative completion | Failure/retry and reward contract |
| --- | --- | --- | --- |
| A01 `academy.arrival` | Meet the arrival contact and receive an introductory journal. The gate frames the academy destination. | Valid interaction with the configured arrival role after initial onboarding state is created. | Journal uses a stable grant receipt; full inventory queues delivery. Repeat login/interaction cannot produce more journals or currency. |
| A02 `academy.orientation` | Aki explains one immediate goal. Choose comfortable text/effect settings and a provisional nature; do not force a permanent lineage choice yet. | Server records the allowed provisional choice; accessibility choices do not change power eligibility. | The player can leave/reopen the interaction. No reward for repeatedly changing settings. Existing configured controls remain intact. |
| A03 `academy.movement` | Follow three landmarks through ordinary movement, then a safe short leap. An equally valid accessible route teaches positioning without a precision jump. | Ordered, bounded visits to configured loaded anchors; final mentor interaction confirms the route. | Falling returns to a catch terrace; quitting preserves visited checkpoints. Do not allow teleporting into a final anchor to skip required steps. Award a small one-time training receipt. |
| A04 `academy.defense` | A training partner telegraphs a slow strike; demonstrate spacing, guard and substitution in separate exercises. | Three distinct verified combat events in a training run: evade valid attack, block accepted contact, settle a permitted substitution. | Nonlethal practice, no equipment loss and no ordinary-player PvP toggle. Each exercise retries independently. Repeated dummy hits do not farm combat XP. |
| A05 `academy.first-nature` | Learn one introductory technique for the provisional nature. Show the tell, cost and recovery; practice hitting a permitted target while avoiding a friendly marker. | Mentor grants the specific lesson; three successful settled uses plus a final safe-target check. | Practice uses bounded tutorial resources or a clearly labeled practice mode; it cannot mint transferable items or unlimited persistent mastery. Give the legitimate starter lesson once. |
| A06 `academy.provisions` | Jun asks for a field ration. Gather/buy a small ingredient set, use the kitchen, and prepare an actual ration. | Server observes the matching workstation recipe result and consumes/records the required hand-in stack once. | Full station/output, unload and relog preserve actual inputs. A player who spends ingredients gets a recoverable earn/buy route. Crafting existing unrelated items does not satisfy the objective. |
| A07 `academy.courier-choice` | Escort Ren's message along a direct training route or a civilian street. The civilian route reveals an injured resident and a medical clue; the direct route reveals evidence of tampering. | Both branches end at the same dispatch role, with an explicit branch flag and appropriate evidence/support event. | Neither branch loses essential later progression. Record helpful conduct as a small reputation difference, not a combat-power lock. Escort stuck/death has a bounded reset at the last safe anchor. |
| A08 `academy.roadside-ambush` | Face a small authored ambush in a contained yard. One enemy teaches a committed charge, another teaches interrupting a channel. | Encounter service emits an accepted completion receipt with required participation and protected civilian outcome. | Solo health/resource budget is fair; a wipe resets only that run and reopens a short return. Do not spawn endless reinforcements or allow rewards from repeat console summons. |
| A09 `academy.squad-drill` | Coordinate a signal-and-rescue exercise. A solo player can use a temporarily issued support companion; a party divides roles. | Valid signal objective, rescued actor and completed return are bound to this drill's run and participants. | Leader departure/rejoin and temporary companion cleanup are explicit. A member who already completed first clear can assist without duplicating their unique reward. |
| A10 `academy.missing-dispatch` | Trace the evidence, recover a sealed dispatch from a short encounter/puzzle space, return it, and graduate. A final conversation points toward Mist's tide archive. | Evidence prerequisites, seal/puzzle result, eligible encounter clear and dispatch hand-in all settle in order. | Missing objective item is reconstructed through its pending receipt. Graduation/rank, supplies and next mission unlock commit once and survive restart. Optional branch acknowledgement has no irreversible power penalty. |

## The five starter lessons

These are concrete delivery targets, not new claims of reference parity. Use an existing engine only if its actual behavior satisfies the contract.

- **Fire:** one visible aimed burst. Cost is displayed before use, impact respects protection, and no permanent building fire starts. The learner identifies the short recovery after a miss.
- **Water:** one controlled bolt/stream with a clear maximum reach. Water context is explained, and dry-land behavior/cost is explicit. The learner can succeed without standing in an ocean.
- **Earth:** one temporary cover action with a restricted valid-placement volume and a weapon-based follow-up. It cannot overwrite containers/protected blocks and restores only its owned temporary changes.
- **Wind:** one directed gust/arc with a legible width and endpoint. It affects permitted targets and eligible projectiles without knocking allies off safe paths.
- **Lightning:** one precise lance with a short visible charge. Movement/cancel rules are explained, and a wall or friendly target correctly prevents invalid contact.

If one of these engines is unfinished, the campaign stage remains blocked for that nature and the backlog shows the missing implementation. Do not quietly hand every nature the same projectile with a different icon.

## Rewards, pacing and discoverability

Set numerical XP/Ryo rewards only after measuring the route; proposed progression is graduation plus starter equipment/provisions and an understandable next training choice. The chain must not instantly unlock advanced eyes, beast forms or all nine wards. Currency granted during the tutorial cannot support an exploitably profitable replay loop. A normal player can buy the missing ration ingredient without paying real money or using an OP command.

Target 30Ã¢â‚¬â€œ45 minutes for an exploratory first completion and a shorter returning-player route. Measure actual play rather than forcing timers. Keep optional dialogue, practice and discoveries available for players who enjoy them. A journal breadcrumb says current action, destination and reason; the environment supplies the corresponding landmark. No quest stage requires remembering an unexplained coordinate string from chat.

Give players a first meaningful specialization choice after graduation, with a preview/practice route and a bounded respec policy. Expose the first dungeon's prerequisite through a resident conversation and map discovery, not a debug inventory item. The Missing Dispatch thread seeds future village/harbor stories without requiring that all later campaign content already be shipped.

## M1 acceptance route

Create a fresh, non-OP player on a dedicated test server. Complete all ten stages using only delivered gameplay. Observe actual journal, resident, technique, workstation and reward UI. Test a solo route and a two-client party route, including an assisting already-graduated player. Then test:

1. Full inventory at each unique grant and a later outbox delivery.
2. Leave/rejoin during the movement route, escort and squad drill.
3. Save/stop/restart while a mission is active and between graduation debit/grant settlement phases.
4. Failed guard, missed cast, declined dialogue, wrong puzzle choice, escort stuck and combat wipe.
5. Repeated event/packet, repeated NPC interaction, repeated mission hand-in and the same completion receipt replay.
6. Missing/disabled optional client mods, UI scaling, reduced effects and conflicting keybindings.
7. A friendly or protected target placed in the ability path and a deliberately invalid destination.
8. Both courier branches, with their distinct acknowledgement and the same essential future access.

Evidence must show the real normal-player route and preserved state, not an administrator issuing all rewards. Record exact source/JAR/world-copy identities, actual duration, failures and fixes. M1 is complete when the journey is coherent and replay/recovery are correct; a list of ten quest definitions is only authored content until it is connected and played.


---

## Repository source: docs/native-checks.md

# Disposable Forge native checks

`tools/run_native_checks.py` runs the current Core 0.3.9 recovery fixture and
the Standards 0.3.1 helper against the exact production mods from a successful
source component build. It creates its own temporary Forge installation and
synthetic world, validates the fixture's result, then removes that installation
on both success and failure. It accepts no existing server or world directory.

The regular source workflow runs this command immediately after
`tools/build_modules.py` succeeds. A component build and a native fixture result
remain separate records. Adding this job is not evidence that a particular
revision has already passed it; inspect that revision's completed CI run.

## Run after a current source build

Use JDK 25 for the pinned Gradle wrapper and an Azul JDK 8 for compilation and
the Forge runtime, as in [the source build instructions](build-modules.md):

```sh
export JAVA_HOME='/path/to/zulu-jdk25'
export VALE_JAVA8_HOME='/path/to/zulu-jdk8'
export PATH="$JAVA_HOME/bin:$PATH"
python tools/build_modules.py
python tools/run_native_checks.py
```

The native runner itself supports Python 3.8 or later and uses the standard
library. It also requires `curl` unless the exact reviewed installer is already
available. `--build-report` selects a different successful component report;
its source snapshot must still match the current checkout. The selected copied
artifacts, their original build outputs and prepared Minecraft development API
must retain their recorded hashes.

An existing official installer can be supplied without downloading another:

```sh
python tools/run_native_checks.py --installer /absolute/path/to/forge-1.12.2-14.23.5.2859-installer.jar
```

Both pinned installer hashes are still checked before execution. An environment
using an approved Gradle launcher may pass the same `--gradle-launcher` and
`--gradle-arg=-Pname=value` arguments used for its source build. `curl` uses the
inherited HTTP(S) proxy and TLS settings. The Java installer needs its own JVM
proxy/trust-store configuration where the environment requires it, for example
through approved inherited `JAVA_TOOL_OPTIONS`; `HTTP_PROXY` alone does not
configure Java. The runner changes no network policy and does not disable TLS
verification. Failed official downloads fail the check.

## What the runner binds

The production roster contains exactly these nine mods:

| Component | Forge mod ID |
| --- | --- |
| Core | `valeorders` |
| Encounters | `valeencounters` |
| Rack | `valerack` |
| Residents | `valeresidents` |
| Residents server bridge | `residentserverbridge` |
| World compatibility | `valeworldcompat` |
| Forge damage compatibility | `valedamagecompat` |
| Server bridge | `valeserverbridge` |
| Village Standards | `hvdungeonpictures` |

Runtime filenames, versions and SHA-256 identities come from the actual checked
component artifacts. The runner independently inspects their mod declarations
and archive contents. HUD, Controls and the client lifecycle adapter are not
added to the server roster.

The two private helpers are built by `reobfRecoveryChecksJar` and
`reobfStandardsNativeChecksJar`. A temporary Gradle init script supplies the
checked Core and Standards development JARs to their private compile and
reobfuscation classpaths. The Core fixture also receives the checked Rack
development JAR. This changes no production build definitions. The helpers must
contain Java 8 classes from their own private source directories; Core's new
`ScrollCastNativeChecks039` and `ReedEscapeNativeChecks039` classes are both
required. Private fixture source hashes and
the runner's own hash are recorded separately from the production source
snapshot, which intentionally excludes these private tests.

The official Forge installer is downloaded from
[Forge's Maven repository](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-installer.jar),
along with its `.sha1`. The installer command uses the official
[`--installServer` entry point](https://github.com/MinecraftForge/Installer/blob/2.0/src/main/java/net/minecraftforge/installer/SimpleInstaller.java).

| Pinned input | Identity |
| --- | --- |
| Installer byte count | `4681890` |
| Installer SHA-256 | `dbba1cd1bf34a4b82148bac50a6150f63ffcb2d5dc7d88a0508c27fdcff15b76` |
| Official installer SHA1 | `6b3d5be8b58d5385fda9c4d285367f4cb6e697b2` |
| Installed Forge universal SHA-256 | `bd8314729d787e35164476d75afb337afb9783917e4f6fa1f77f049adba22f2d` |

The runtime must be Minecraft 1.12.2 with Forge 14.23.5.2859. It contains the
nine production mods and exactly two private QA mods: `valerecoverychecks`
0.3.9 and `valestandardschecks` 0.3.1. The server binds to loopback with an
ephemeral port, RCON and query disabled, and no real players. Its flat world is
named `vale-recovery-test`, with the existing `.vale-recovery-test` marker and
both required JVM flags. The runner writes `eula=true` only inside this
authorized disposable fixture. Hostile and livestock spawning remain enabled
at difficulty 1 for the native tests.

## Passing conditions and limits

A pass requires all of the following to agree:

- The server process exits with code zero within its timeout.
- `native-recovery039-result.json` identifies `native-recovery-0.3.9`, reports
  `passed: true`, has positive assertion counts, and includes completion of the
  new scroll-cast and Reed Escape callback checks. Earlier 0.3.9 helpers that
  lack the Reed Escape class or completion label are rejected.
- Its Minecraft and Forge versions match the selected runtime.
- Every loaded mod ID, version, filename and SHA-256 equals the complete staged
  roster, including both private helpers; Core's separate recorded hash agrees.
- The console contains the current native success marker without a failure
  marker, and the original source, fixture inputs and staged mod bytes remain
  unchanged.
- The disposable runtime is removed successfully.

Each helper build is limited to five minutes; Forge installation to ten
minutes; and server execution to ten minutes by default. `--server-timeout`
accepts 30–1800 seconds for an explicit local run. Downloads and Java version
checks have shorter bounds. On POSIX systems each subprocess starts in its own
process group; the runner terminates that group on timeout/interruption and
also closes remaining descendants after the parent exits. On Windows it uses
`taskkill /T /F` while the parent is still running; it cannot guarantee cleanup
of descendants that outlive an already-exited parent. The native CI job runs on
Linux and uses the stronger process-group cleanup. The runner prints a bounded
status update during long subprocesses.

A native pass checks the implemented behaviors in a new synthetic Forge world.
It does not establish migration of `Naruto.test`, a Magma runtime result,
graphical rendering or animation quality, real network protocol behavior,
matching-client play, Bisect deployment, or release acceptance.

## Retained evidence and CI artifacts

The latest result is `build/native-check-report.json`. Each invocation also
retains `build/native-checks/<unique-run-id>/report.json`, subprocess logs and,
when produced, the fixture's original result JSON. Missing results, failed
fixtures, nonzero exits, altered inputs and cleanup failures leave a failed
report. Existing native reports are never used as current execution evidence.

The workflow uploads only those explicit JSON/log paths as its separate native
artifact. The original component artifact still contains only the checked
project-owned outputs from `build/source-builds/`. Upstream runtime JARs,
downloaded libraries, private QA JARs, the temporary Gradle script, and generated
world files are excluded from both artifacts. The QA build outputs remain in
their existing ignored `build/private-native-checks/` directories locally;
they are never production installation files.


## Derived v17 paste-height check

This is a calculation from observed schematic metadata, not a completed native paste. WorldEdit origin plus Y offset can put the bottom below Minecraft's world bounds. Choose and validate an explicit paste anchor; do not silently clip the authored base.

| Schematic | Stored origin Y | Offset Y | Bottom at stored origin | Top at stored origin |
| --- | ---: | ---: | ---: | ---: |
| cloud_v17.schematic | 93 | -123 | -30 | 178 |
| mist_v17.schematic | 21 | -21 | 0 | 147 |
| nether_v17.schematic | 43 | -51 | -8 | 117 |
| sakura_v17.schematic | 73 | -99 | -26 | 190 |
| sand_v17.schematic | 69 | -107 | -38 | 114 |

Sakura, Cloud, Sand and Nether therefore require a deliberate height/alignment choice if using those stored origins. Mist's stored metadata starts at Y=0. Actual world placement still requires terrain, routes, water, tile data, entity and restart checks.
