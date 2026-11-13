# Master implementation prompt

Use this prompt with the `hidden-block-village` project skill and the current repository. It is a standing product brief and execution method, not permission to spend money, acquire accounts, message others, or publish unreviewed third-party content. The owner's existing authorization for routine project commits/pushes remains in `AGENTS.md`.

---

You are continuing Hidden Block Village / Vale, an existing Minecraft Java shinobi game project. Build an immersive Naruto-inspired experience that retains Minecraft's movement, building, crafting, tangible world and persistent multiplayer. The owner wants feature-level replacements for the supplied 49-mod reference pack and substantial enhancements: complete chakra combat, progression, clans/eyes/natures, transformations, weapons, companions, original NPCs and quests, village life, co-op, and five exceptionally detailed dungeon campaigns. Do actual implementation work as well as design. Preserve the accumulated work across PCs.

## Establish the real starting point

Locate the checkout whose remote is `tasx13ok-create/Hidden-Block-Village`. Read its `AGENTS.md`, `docs/current-handoff.md` and `design/README.md`; inspect worktree state and fetch/integrate remote changes without discarding local work. If the workspace opens in the older parent dungeon folder, follow its routing `AGENTS.md` into the current checkout. Do not treat an older v9 folder or a prior chat summary as the current source.

Read `design/MASTER-PLAN.md`, then only the relevant sections of `SYSTEMS-SPEC.md`, `DUNGEON-ATLAS.md`, `MOD-REBUILD-MATRIX.md` and `backlog.json` for the work you are doing. The full pack inventory is `design/reference-mod-inventory.json`. Match exact bytes and declared mod identity; several reference filenames contain misleading versions. The archive is reference input, not owned source or a public distribution asset.

Current verified base is Core 0.3.9 with eleven source areas and 36 artifacts, a six-slot weapon rack, original ward geometry, original Residents, three shared encounter engines with 136 profiles, and documented Forge native checks. A real local OptiFine client has joined a separate inspection world. Respect the exact scope of those results. Current item counts and historical item-counterpart percentages do not establish ability parity, unique AI, finished visuals, a tested production world or hosted readiness.

## Translate the fantasy into observable gameplay

For every feature, define who uses it, how a normal player acquires it, exact inputs, server eligibility, resource reservation, timing, active effect, counterplay, recovery, persistence and first/third-person presentation. Implement that connected behavior instead of stopping at an item, enum, registry mapping, recipe or placeholder dialogue. Use shared engines where useful, but make each technique, weapon, lineage and boss mechanically distinct where the reference or design requires it.

Keep the full 1:1 feature ledger visible. If reference behavior is not yet observed, mark it `needs_observation` and continue an independently specifiable task. If an enhancement intentionally changes reference behavior, record the intended deviation and its gameplay reason. Do not invent measured parity or silently reduce the requested roster to the currently implemented subset. Do not imply a mode works because its item appears in creative inventory.

The desired experience is a complete shinobi life: academy, mentor, practice, first mission, nature mastery, clan/specialist expression, team identity, rank exams, village services, travel, discovery, tactical dungeons, rare transformative trials and long-term village responsibility. Make the first thirty minutes playable before expanding hundreds of unfinished entries. Teach using interactions and readable spaces; use menus to support decisions, not replace the entire world.

Retain standard Minecraft building and movement. Preserve E inventory/hotbar behavior and existing configurable focus/rack controls. Test that chat, JEI/search, settings and focus loss cannot cast techniques accidentally. Let users tune effects, shake, flashes, sound and text scale. A fight must remain readable without shaders.

## Choose and finish a concrete work package

Use the backlog and current handoff to choose the highest-value unblocked slice. Start with a demonstrated defect, a missing acquisition path, or an end-to-end milestone dependency rather than a cosmetic feature-count increase. If blocked by the private production world, continue work that can be verified in a disposable world. Do not end a session after merely identifying a file that another PC needs.

Before editing, identify the owning module and its actual interfaces. Avoid changing stable registry IDs, wire formats or saved item semantics casually. For a changed behavior, define a small meaningful regression scenario; for visuals, define camera angles and states; for content, define a playable route and failure/retry. Make the implementation, run the appropriate checks, inspect the result and repair demonstrated failures. Keep the task's design and status coherent with the final behavior.

Do not impose an artificial number of files, functions, tests or hours. Do not write tests that merely match generated headings or wording. Do not rerun expensive unrelated suites after an already-sufficient pass without a concrete reason. Conversely, never skip a required gameplay, compatibility or runtime check because a registry or unit test was easier.

## Build and authority rules

Target Minecraft 1.12.2, Forge client 14.23.5.2859, Java 8 runtime. JDK 25 runs Gradle 9.5/RFG, Azul JDK 8 compiles game code. Follow `docs/build-modules.md` and the checked wrapper. The portable toolchains may already exist under ignored `local/toolchains/`; use them or a verified compatible installation without changing unrelated system Java settings.

Use `tools/build_modules.py` for actual production artifacts and `tools/run_native_checks.py` for the private disposable Forge fixture when the change requires it. Keep native QA JARs out of production packages. Relative JSON report paths use forward slashes; physical absolute paths remain platform-native. Bind evidence to actual source and JAR hashes. Preserve failed-run evidence and successful output identities.

Server owns costs, inventory, valid targets, damage, learned state, cooldowns, NPC/mission outcomes, travel and rewards. Retain the current reserve/settle/refund semantics, receipt IDs and full-inventory outbox. Validate finite coordinates, loaded geometry, collision, world border, ownership and Forge protection callbacks. State changes during callbacks, movement followed by exceptions, packet duplication, reconnect, death and dimension change need explicit outcomes. Client animation and prediction must not become independent authority.

## Original implementation and reference discipline

Inspect supplied JARs as reference artifacts and observe permitted gameplay, publicly documented APIs and behavior. Author new implementation and assets. Never copy proprietary implementation/artwork, scrape private server resources, bypass access restrictions, or claim that rebuilding code guarantees rights to Naruto branding or someone else's character art. Record dependency licenses and original asset provenance. Optional local OptiFine/Essential support and public redistribution are different decisions.

Use Iceeman's public IceeRamen videos and creator-published material to study experience: social roles, earned progression, team play and readable encounter structure. Record the exact URL, access date, evidence type and actual observations. Separate watched, timestamped footage from video descriptions, release notes and your own proposed improvements. Direct video playback was unavailable in the initial research; do not invent timestamps, scenes, combat timings or watched hours. Fill the footage-review queue when access is available.

## Dungeon production direction

Use the detailed atlas. Mist remains a real-water 200×200 waterfront settlement with seabed/piles, clear boat channels, layered usable interiors and coherent roofs. Earth/Leaf, Cloud and Sand have irregular fractured torn-ground bases, not rounded cones. Fire/Nether remains excluded from that treatment and gets a proposed monumental 320×320 district/citadel expansion. Preserve the existing baseline and compare recovered v17 before replacing it.

Each campaign needs a room/route graph, narrative purpose, functioning puzzle, distinct encounter language, multi-phase boss, fair solo/co-op routes, meaningful reward and short recovery/exit. Detail supports material, navigation, story and atmosphere. Validate native stairs/slabs/doors/tile entities, support, roofs, gravity blocks, water, illumination, collision and landing points before offline glamour renders. Actual dimension atmosphere and BSL/Solas gameplay require real client review. Offline rendering cannot stand in for an in-game walkthrough.

Use immutable templates with explicit anchors and a bounded expedition lifecycle. Instancing/reset must not overwrite player areas or unrelated runs. Test disconnected members, restart, already-claimed rewards, full inventory and an interrupted reset. Difficulty variants add tactical demands rather than unlimited health and particle spam.

## Friend NPCs and preserved worlds

Original CustomNPCs data and the owned Residents runtime are separate. Intake the friend's world/profile CustomNPCs folders, relevant config/assets/dependencies, changed NPC list and baseline backup. Graph IDs and references, remap collisions consistently, preserve original exports and test in a copy. Decide whether to retain an optional compatible profile or translate behavior into Residents; do not pretend a clone export automatically ports scripted behavior. Keep worlds, personal progress, credentials and unreconciled third-party content out of public Git.

The private `Naruto.test` stage and reviewed inventory are not present in the inspected folders. Do not synthesize a replacement inventory and claim it matches the missing original. Existing archives, incomplete candidates, rollback files and world saves are preserved. Build complete production packages only with their actual pinned inputs and separate output IDs.

## Verify, deliver and continue

Evidence ladder: component behavior → native runtime → actual local client/server handshake → visible gameplay/graphics → copied production-world save/restart/migration → actual Magma/plugin roster → hosted Bisect start/restart/client connection. Report only steps actually run, with their exact identities and limits. Keep a live user inspection session available unless the task requires restarting that specific session or the owner asks.

Update the backlog task and related design if behavior changed. Record changed files, meaningful checks/results, artifact hashes, known defects and the exact next executable action in `docs/current-handoff.md`. Commit and push completed authorized checkpoints; verify the remote commit. Keep private outputs local. Final communication should lead with the actual result and usable artifact or game state, then material limits. Do not claim the entire game is finished when only this slice is complete, and do not replace execution with another vague promise to continue.

---

Suggested first resumed task after this planning checkpoint: implement the observed empty-list JEI catalog bridge correction in a new candidate, test empty/nonempty/error cases, build the affected module/dependencies, launch a separate graphical smoke profile and verify the bridge no longer warns. Then advance the normal-player academy/mentor/acquisition slice and Mist encounter graybox according to the backlog. Do not replace the owner's currently running inspection JARs during play.
