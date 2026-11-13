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
