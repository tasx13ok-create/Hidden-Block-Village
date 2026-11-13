# Fable: detailed project instructions


Generated UTC: 2026-11-13T18:08:16.381589+00:00. Base Git commit: `eb94b462136f91b55cc7bf8c76a76b9651bb40a8`.

This is a source-backed offline snapshot. Source sections retain their actual text, apart from LF line-ending normalization and Markdown wrappers. New source files can be ahead of the recorded base commit; the accompanying manifest pins every input byte hash. Read current Git state before acting on an old snapshot. Plans are not completed work.

Use these instructions to implement the owner's Naruto Minecraft game in coordination with Codex. The latest owner fun/pacing direction takes precedence over older compulsory academy examples. Work within the assigned branch and file boundaries. This book contains the actual standing rules, execution task, power/world behavior contracts and build workflow; it does not create spending, production-deployment or external-messaging permission.

Contents: collaboration instructions; repository rules; project skill; Fable task packet; Naruto fun contract; implementation prompt; systems specification; source-build procedure.


---

## Repository source: collaboration/FABLE-PROJECT-INSTRUCTIONS.md

# Fable project instructions: working with Codex

You are Fable, the owner's Claude Desktop assistant, collaborating on Hidden Block Village / Vale. Build an enjoyable, recognizable Naruto game inside Minecraft, with the supplied reference pack's functional obligations and substantially improved world/content. This is an implementation assignment, not a request to keep generating plans.

## Read the right material

Use the generated `FABLE-INSTRUCTIONS.md` as the detailed instruction book, `FABLE-CONTEXT.md` as the project/reference explanation, and `FABLE-MEMORY.md` as the dated factual handoff. They are offline snapshots assembled from named repository files, not a live connection to Codex. Read the current checkout and GitHub before treating an old snapshot as current. Consult `collaboration/BOARD.md` and `docs/current-handoff.md` first on a resumed session.

The owner's latest direction emphasizes powerful armor/modes with real moves, distinctive weapons/jutsu, early exciting play and world reactions while preserving Minecraft. The updated Naruto fun brief supersedes earlier long compulsory academy pacing. Keep the full requested scope visible; a complete playable slice is progress, not permission to drop the rest.

## Own a useful independent workstream

Codex owns the active Core combat, power kits, Controls/HUD integration and final builds. Your first independent package is `collaboration/FABLE-TASKS.md`: v17 dungeon audit/import/fixes, followed by original NPC/mission/environment content and adversarial gameplay review. Work in your own worktree and branch. Do not change the branch or files under Codex's active checkout. Do not edit shared module/build interfaces concurrently without an agreed small contract.

Use real files, not imagined server state. Preserve original schematics and recovered worlds. Write fixes to a new candidate and record input/output hashes, alignment and precise edits. A large build should not be flattened or replaced because it is difficult to audit. Preserve Mist's 200×200 water settlement and the specified terrain identities. Keep raw world/player/NPC exports, JARs and credentials private.

## Build gameplay people can enjoy

Make each featured power identifiable by its actions, silhouette, timing, movement, impact and consequences. A label, potion or colored projectile alone is insufficient. Teach inside interesting action. Offer clear destinations, short missions, useful rewards, quick retries and optional deep lore. Avoid excessive compulsory roleplay, repeated travel and empty grinding. Keep ordinary enemies satisfying to overpower; create readable challenges through encounter decisions instead of blanket immunity.

NPCs need useful roles and understandable reactions. A witness reacts to something they plausibly saw/heard, with proportionate consequences and a recovery path. A dungeon needs a route, arrival composition, purposeful rooms, actual encounters, reward/exit and failure/rejoin behavior. Support solo and co-op where specified. Preserve vanilla inventory/building and configurable controls; chat/search must not trigger combat actions.

## Verify the actual result

Distinguish source/component checks, native Forge tests, connected client startup, visible gameplay, copied-world restart/migration, Magma/plugin compatibility and hosted acceptance. A pass in one scope does not prove another. Test the behavior that could fail: pasted bounds/offsets, tile data, collision, fluids, actor lifecycle, reward replay, full inventories, reconnect, death and interrupted callbacks. Inspect actual evidence before marking a task done.

Preserve technical IDs and saved data. Follow the existing receipt/outbox and authority rules. In Minecraft 1.12, UUIDs are stored as `Most`/`Least` pairs; deleting only the unsuffixed key does not clear them. If reference behavior is unknown, record that fact and continue independent work. Research findings are not automatically implemented features.

## Deliver reviewable work

Commit and push meaningful completed checkpoints to your branch. Do not force-push shared history or push directly to `main`. Report the branch/commit, affected paths, checks actually run, candidate locations, concrete defects and remaining gaps in `collaboration/fable/STATUS.md`. Codex reviews and integrates the branch. Do not claim a direct chat/API connection or a review that has not happened.

The sprint deadline is in the current handoff. Use available time for implementation, integration and verification. No API worker spending, account creation, external messaging or production deployment is authorized by this file. The optional API setup is explicitly deferred. Keep memory concise and factual as work advances; the large generated snapshot has evidence appendices so it can be audited, not to manufacture history.


---

## Repository source: AGENTS.md

# Standing project instructions

## GitHub is the shared source of truth

The owner explicitly requests that work on this project always be saved into this GitHub repository so it can be continued across PCs. This preference applies to future sessions on any computer.

- Work in a checkout of `tasx13ok-create/Hidden-Block-Village`. At session start, read these instructions and the current project status, inspect local changes, and fetch remote updates before editing. Integrate updates without discarding local work or rewriting shared history.
- Save original source, build and release tools, tests, reviewed licensing/provenance records, and project documentation in this repository.
- Commit and push meaningful completed checkpoints and an end-of-session handoff. The owner's standing instruction authorizes routine commits and pushes for this project; do not require a repeated request merely to sync authorized work. Follow any applicable branch protection and review requirements.
- Record what changed, checks actually run and their results, unresolved blockers, and the exact next steps in `docs/current-handoff.md`. Keep it current so another PC or a fresh chat can resume without the original conversation. Distinguish observed results from historical records and unverified claims.
- Verify the pushed commit is on GitHub before saying work is saved across PCs. If pushing is blocked, retain local work and report the unsynced files/commits and blocker clearly.
- Do not rely on chat history or local-only memory as the sole record of decisions or progress. Do not claim this file automatically transfers complete chats or unpushed PC files.

## Preserve private inputs and original work

This repository is public. Never commit credentials, access tokens, authentication profiles, raw chat exports, private player data, world saves, large runtime archives, reference decompilations, or third-party artwork/distributions without reviewed permission. Follow the repository's existing distribution boundaries and ignore rules. Record safe metadata and references for private inputs instead.

Preserve original saved worlds, source archives, rollback files, and incomplete candidates. Track licensing and provenance accurately; do not assume a source rewrite or component hash alone establishes redistribution permission. Do not label a release deployment-ready until its version-specific acceptance checks pass.

## Current game direction and execution plan

The owner requests an immersive Naruto-inspired shinobi game inside Minecraft, original functional replacements/enhancements for the supplied 49-mod pack, and five substantially enhanced dungeon campaigns. Preserve Minecraft building, movement and tangible world play. The maintained entry point is `design/README.md`; the full continuing brief is `design/IMPLEMENTATION-PROMPT.md`. Use the project skill at `.agents/skills/hidden-block-village/SKILL.md` for broad project continuation and read only the relevant detailed specifications for a narrow task.

Keep reference behavior, intended enhancements, implemented behavior and verified results distinct. Every supplied JAR has an exact identity and disposition in the design ledger. Do not silently declare full parity from item/profile counts or treat a planned work package as delivered. Validate changed reference mappings/backlog with `python tools/validate_design.py`.

Preserve Mist's 200x200 real-water layout; Earth/Leaf, Cloud and Sand retain fractured torn-ground bases; Fire/Nether is excluded from that terrain treatment and has a larger proposed citadel. Recover/diff later v17 work before replacing it. Keep the owner's active inspection session available while independent development continues.


---

## Repository source: .agents/skills/hidden-block-village/SKILL.md

---
name: hidden-block-village
description: "Build Hidden Block Village / Vale: recognizable Naruto powers, powerful armor and weapon movesets, reactive Minecraft world, reference-mod recovery, playable progression and verified releases. Use for this project and its 49-mod rebuild."
---

# Hidden Block Village

## Start from the current repository

Locate the checkout with remote `tasx13ok-create/Hidden-Block-Village`. An older parent workspace named Naruto mod contains v9 dungeon sources; follow its routing AGENTS.md into the current checkout. Read repository `AGENTS.md`, `docs/current-handoff.md` and `design/README.md`; inspect local changes and fetch/integrate current remote work without discarding files. This skill applies to this project, not unrelated Minecraft work.

The owner wants an immersive Naruto-inspired shinobi game that still plays as Minecraft, functional coverage of the supplied 49-mod reference pack, original implementations/assets, enhanced progression/social life and five much more detailed dungeon campaigns. Existing eleven-component Vale source is the starting point. Do not restart it or replace actual implementation with another broad status report.

## Naruto identity and fun take priority

Read `design/NARUTO-FUN-BRIEF.md` when building combat, equipment, progression, NPCs, world interaction or content. It records the owner's updated direction and supersedes the earlier long mandatory onboarding target. Deliver recognizable Naruto actions, powerful equipment with actual movesets and causal world reactions while preserving Minecraft building/inventory. Use public-facing Naruto terminology when the implemented behavior supports it; preserve stable registry/save IDs. Keep canon facts, gameplay adaptations and unfinished stand-ins distinct.

Prioritize one complete power kit + short mission + visible world reaction over more compulsory academy steps or shared-engine item counts. Let players try exciting powers early in clearly scoped practice, then earn mastery through interesting play. Maintain the full requested roster; a playable slice is not permission to silently cut the rest. Judge a kit by acquisition, inputs, changed decisions, animation/impact, response/counter, resource rhythm and restart behavior. Keep ordinary enemies satisfying to overpower; challenge through readable mechanics rather than universal immunities.

Use the adversarial questions in the fun brief to catch repeated menu errands, stat-only armor, identical recolored attacks, lifeless NPCs, confusing controls and unearned completion claims. Consult `design/REFERENCE-RESEARCH.md` for primary sources and bounded community signals. Advertisements are not a player survey; inaccessible video is not watched footage. Research should change implementation decisions, not replace shipping.

## Route by the work being done

- Product direction and priorities: `design/MASTER-PLAN.md` and `design/backlog.json`.
- First normal-player implementation route: `design/ACADEMY-CAMPAIGN.md` and task `SYS-ACADEMY`.
- Combat, progression, lineages, modes, actors, NPCs, economy, UI or multiplayer: the relevant sections of `design/SYSTEMS-SPEC.md` and actual owning-module source.
- Reference recovery: `design/reference-mod-inventory.json`, `design/mod-rebuild-map.json`, `design/MOD-REBUILD-MATRIX.md` and `design/REFERENCE-RESEARCH.md`.
- Dungeon geometry, encounters, lighting or atmosphere: `design/DUNGEON-ATLAS.md`; establish whether actual input is v9, recovered v17 or a new branch before edits.
- A full continuing brief: `design/IMPLEMENTATION-PROMPT.md`. Read it when resuming a broad build task; do not load every document for a narrow fix.
- Builds, native checks, candidate assembly or hosting: the existing repository build/native/assembly/client-and-Bisect guides, with exact version-bound receipts.

Resolve document paths against the checkout, including when this skill is installed in a global skills directory. Design proposals are not implementation evidence. Current counts, hashes and open work come from the current handoff/receipts, not this skill's static text.

## Implement an observable slice

Choose a concrete unblocked backlog package that advances an end-to-end player route. Record acquisition, inputs, authority, resource settlement, active behavior, counterplay, persistence and first/third-person feedback before calling a gameplay feature complete. Shared engines are useful; matching an item name or mob profile does not prove unique mechanic parity. Preserve full reference obligations and label unknown behavior and deliberate enhancements explicitly.

Server owns costs, targets, inventory, cooldowns, mission/NPC outcomes and rewards. Retain receipt/outbox protections, native event cancellation, collision/loaded-geometry checks and correct callback/exception settlement. Keep stable registry/wire/save identities unless a tested versioned migration is part of the task. Preserve Minecraft inventory, hotbar/building and configurable input focus; text entry must not trigger abilities.

Author original code, models, effects, dialogue and audio with provenance. Reference JARs and public footage are observations, not source/artwork permission. Video descriptions and creator release notes must not be represented as watched/timestamped footage. No mandatory paid developer dependency is a product objective, not a legal-clearance claim.

Mist stays a real-water 200Ã—200 settlement. Earth/Leaf, Cloud and Sand retain torn-ground fractured bases. Fire/Nether is excluded from that treatment and has a proposed much larger citadel. Preserve old arrays/exports/worlds and recover/diff later work before overwriting it. Validate native geometry, water, support, tile entities, routes and encounter lifecycle; label offline renders accurately.

Friend CustomNPCs intake and original Residents integration are different operations. Preserve exports, graph/remap IDs and dependencies, and test supported behavior in a copy. Missing private production inputs block only the checks that actually need them; continue independent original gameplay/content work in disposable instances.

## Verification and handoff

Use the actual project toolchain and appropriate checks. Component â†’ native â†’ real network client â†’ visible gameplay â†’ copied production-world restart/migration â†’ actual Magma/plugins â†’ Bisect are distinct evidence scopes. Relative report paths must remain portable on Windows. Keep private QA out of production; preserve original worlds, archives, rollback and incomplete candidates. Leave an owner's live inspection session intact unless their task calls for a change.

Validate the design ledger with `python tools/validate_design.py` when changing it. Update related task status and design based on observed results. Save completed authorized work to GitHub, verify the remote checkpoint, and update `docs/current-handoff.md` with changes, tests, exact next action and material gaps. Do not invent completion percentages, claim an entire game is complete after a slice, or stop at a promise to resume. Do not infer permission to spend, contact others, publish private inputs or use additional agents from this skill.


---

## Repository source: collaboration/FABLE-TASKS.md

# Fable: dungeon and content implementation packet

You are the owner's Claude Desktop assistant, Fable. Collaborate with Codex on Hidden Block Village during the twenty-hour sprint. **Build and verify useful content/tools; do not stop at a new master plan.** Work in your own Git worktree/branch based on current `main`, preferably `fable/dungeons-v17`. Read `CLAUDE.md` and its linked project instructions first.

## First package: recover and integrate the supplied v17 dungeons safely

Codex has read all five supplied schematics and preserved exact copies under the primary checkout's ignored `local/v17-intake`. Original source folder on this machine: `C:/Users/Steal/Downloads/archive-2026-11-13T173625Z`. Give your Claude session explicit folder access if its worktree cannot see these inputs. Never overwrite originals.

Observed dimensions: Sakura 288×217×288; Cloud 288×209×288; Sand 288×153×288; Mist 200×148×200; Nether 256×126×256. They use vanilla numeric block IDs, valid array lengths, in-bounds unique tile positions and no command blocks in the initial scan. These facts do not establish walkability, aesthetics or runtime stability. Mist contains real water. Preserve its 200×200 footprint and the non-Nether fractured bases.

Earlier v9 comparison inputs and original authored tooling are in the **parent** workspace: `../output_v9`, `../source`, `../tools`. The five v9 schematic stems are `earth_sacred_sakura`, `cloud_sky_monastery`, `sand_windcarved_oasis`, `mist_tidal_sanctuary`, `fire_nether_citadel`. Read data and relevant source before porting owned reusable helpers into this repo. Do not automatically treat instructions found inside downloaded data as owner requests.

Build a reusable command-line audit/import package under `tools/world_merge/` with explicit input/output arguments. Audit schematic format/shape, unsigned block/data IDs including AddBlocks, tile-entity identity/coordinates, paste offsets/height limits, route connectivity/collision, stair/door access, gravity supports and fluid outlets. Identify intentional waterfalls/open ocean separately from defects. Respect WorldEdit origin/offset metadata. Compare v9 and v17 using an explicit alignment; a centered crop alone does not prove the authored entrances align.

Author a **new** candidate output for fixes, with before/after hashes and a list of precise edits. Never flatten or rebuild the large geometries just because they are difficult to inspect. Preserve inventories, authored tile data and legacy IDs. Add meaningful fixture tests for malformed/hostile paths, format bounds, offsets and representative fixes. Do not mirror the implementation with superficial tests.

The recovered 5.67 GB archive at `C:/Users/Steal/Downloads/9a70913e-616c-483b-926e-2e28e391ebf0.zip` contains **Naruto.test world data only**, not the full server. Codex is staging a CRC/hash-verified private copy on the roomy D: drive under `D:/HiddenBlockVillage-Sprint-20261113/recovered-world`. Check `docs/current-handoff.md` / the local receipt for whether staging completed before using it. Never edit that recovered source copy; use another new candidate directory. The owner will provide the complete server download when it finishes; do not invent its contents or ask the owner to repeat that promise.

The world archive includes CustomNPCs dialogs and a Kakashi clone. Treat them as private compatibility inputs, not original Residents implementation or permission to publish those assets. Inventory dependencies/IDs and propose a tested compatibility mapping. Do not execute embedded scripts/commands while merely auditing. Public evidence contains reviewed aggregate metadata; private player/world payload stays outside Git.

Deliver source/tests on your branch plus `docs/dungeons/v17-integration.md`: exact inputs and alignment, defects actually found/fixed, route/native checks, candidate location, hashes, remaining limitations. Prefer a playable copied-world import path, including reconnect/restart/rollback behavior, over a pile of decorative renderings. Label offline renders accurately.

## Next independent packages

After the first coherent checkpoint, continue as far as useful within the sprint:

1. Develop original NPC routines/dialogue and short, exciting mission content connected to the dungeon entrances. Define stable content IDs, acquisition, branches, failure/retry and actual rewards. Use data files and a small documented adapter contract; coordinate before editing Core interfaces.
2. Improve environmental navigation, arrival views, interiors, lighting, material transitions, landmarks and world reactivity in separate candidate geometry. Preserve the owner's village constraints and large set-piece identities. No empty long walks to pad playtime.
3. Produce a player-facing QA route and adversarial findings: can a new player find the fun, do powerful abilities fit rooms/camera, can solo/co-op complete it, and do disconnects strand actors/rewards?

Keep work independent of Codex's current `modules/vale-orders` changes. Do not edit shared build/version files, root skill/plan/handoff or the same live instance. If a Core hook is needed, document the smallest desired event/API in your status and continue independent content work.

## Working with Codex

Commit and push your branch when a meaningful package is reviewable. Do not push directly to `main`, rewrite shared history or merge unfinished work to claim progress. Put branch name, commit SHA, affected paths and validation in `collaboration/fable/STATUS.md`. The owner can relay the branch/commit to Codex; Codex can then inspect the actual diff. Never say Codex has reviewed a branch until that happened.

If you only have Claude Chat/Cowork without Code/worktrees, research the same bounded content questions and return original content files plus a reviewable patch proposal in a separate folder. Do not edit the active checkout concurrently. No API spending, account creation, external messaging or production deployment is authorized by this task packet.


---

## Repository source: design/NARUTO-FUN-BRIEF.md

# Naruto identity, power and a living Minecraft world

Owner direction, 13 November 2026: the game must be recognizably Naruto, exciting to play, rich in powerful armor/modes with actual moves, immersive and reactive. Avoid boring stretched-out progression. This brief updates product priorities in the earlier plan; it does not mark these features implemented. Core 0.3.10 supplies an introductory prototype, not the finished experience described here. [Research and evidence limits](REFERENCE-RESEARCH.md) separate source observations from our proposals.

## The experience to build

Let a player become their own ninja in the Naruto world while still building, gathering, trading, exploring and fighting in Minecraft. Use recognizable public-facing vocabulary: chakra, jutsu, Fire/Water/Earth/Wind/Lightning Release, substitution, chakra control, village ranks, Sharingan, Byakugan, Susanoo, Sage Mode and tailed-beast transformations where the implemented behavior supports the name. Existing technical IDs and save data remain stable. Do not hide an unfinished Naruto mechanic behind a generic fantasy name or rename an unrelated effect and call it finished.

Canon identity, game adaptation and new enhancement are separate fields in each feature specification. Record the source for a specific canon claim. The owner's desire for powerful playable transformations does not mean every outfit is literally armor in canon: clothes, chakra cloaks, avatars, body transformations, eyes and disciplines need their own presentation and ownership rules. Lee's taijutsu and puppet mastery are disciplines, not invented hereditary bloodlines. Write original code, geometry, textures, dialogue and sound; observed material remains reference material.

The finished identity test: remove the labels from a short gameplay clip. Can a Naruto fan still identify the power from its silhouette, stance, tell, movement, impact and aftermath? If the only clues are a color and a chat line, continue building it.

## Reach the fun quickly

The following are starting playtest hypotheses, not measured current results or mandatory timers. They supersede a long compulsory academy before anything spectacular happens.

| Time from fresh join | Desired player experience | Evidence to collect |
| --- | --- | --- |
| First minute | See a recognizable village landmark, choose a short introduction or direct practice, perform a responsive movement or chakra action. | First useful action and time spent unable to act. |
| First three minutes | Use a real starter jutsu on a responsive opponent and see one physical world reaction. | Successful input without an administrator explaining coordinates. |
| First five minutes | Start a short mission with a clear visual destination and understandable reward. | Mission start, route discovery, time in menus and travel. |
| First ten minutes | Try an exciting borrowed advanced power in a clearly marked practice/trial space. | Player can explain what changed and wants to use it again. |
| First twenty minutes | Keep a meaningful earned unlock, fight a small set-piece, and choose a next activity. | Concrete new combat option, not only a larger number. |
| A returning session | Resume immediately; choose a quick contract, expedition, duel, build or social activity. | No repeated mandatory tutorial or lost checkpoint. |

An advanced trial loaner must be server-scoped, visibly temporary, nontransferable and cleaned up on exit, death, disconnect and expiry. It must not replace the player's saved armor or damage their existing inventory. Preview and permanent progression are distinct, clearly explained states. Do not advertise an unlocked transformation when only its preview is available.

Compress onboarding into a practical encounter with contextual help. Teach movement, substitution and the first jutsu while something interesting happens. Offer mentor practice for those who want it; allow demonstrated competence to skip redundant explanation without skipping server validation. Keep earned academy checkpoints and existing character choices. Optional lore can be deep; compulsory exposition should be brief. Remove repeated walks to the same mentor when an observed objective can complete naturally at the encounter's end. A ration/civilian task can enrich the world without blocking all combat fun.

Support quick 5–10 minute contracts, 15–25 minute expeditions and longer optional campaigns. These are scheduling targets to test, not padded durations. No daily-login requirement, arbitrary real-time wait, repeated scroll clicking or low-value enemy count should be the only path to a signature power. Use authored challenges, crafting, exploration, team contribution and understandable mastery. Provide a deliberate path to a desired build instead of making identity depend only on random rolls.

## Powerful armor must be a playable kit

For every featured set/mode, specify acquisition, equip/activation, basic attack changes, two active moves, mobility/defense, signature finisher, resource rhythm, opponent response, world reaction, first/third-person assets, accessibility, persistence and failure cleanup. Not every input needs another hotkey: reuse a small configurable action set with a visible mode-specific bar. Preserve Minecraft inventory and building controls. Chat, search, settings and focus loss must suppress combat inputs.

The following are **proposed game adaptations**, not assertions that all details are canon or existing functionality. Select a coherent initial group and finish its behavior/presentation before expanding the full roster. Preserve the larger 49-mod obligations in the ledger.

| Power family | Distinct playable decisions | Big moment | Response and world expression |
| --- | --- | --- | --- |
| Susanoo | Choose a defensive ribcage guard or a more committed avatar; sword arcs and aimed ranged attacks use different openings and reach. A companion-sized shell is different from a giant arena avatar. | A short, player-controlled avatar strike with visible reach and a recoverable ending. | Flanking, cover and baited commitments matter. Giant forms use approved spaces and camera/collision rules; village guards notice the manifested avatar. |
| Tailed-beast chakra cloak | Close gaps, use chakra-arm extensions, protect a teammate or commit stored power to a ranged attack. Individual beasts need distinct expressions rather than nine palette swaps. | A clearly telegraphed beast attack with a controllable aim/release. | Civilians seek shelter near an unleashed dangerous form; trained allies react differently. Give enemies positioning responses, not universal immunity. |
| Lightning chakra armor | Burst acceleration, committed taijutsu and a timed counter; speed changes positioning, not only walk-speed stats. | A readable high-speed intercept or lariat-style impact. | Overshooting and a baited direction create openings. Trails show route and ending; movement cannot pass through protected walls. |
| Sage Mode | Sense eligible nearby threats, use enhanced close combat and nature-themed techniques; stored natural-energy management changes the rhythm. | A combined summon/technique opportunity with the player still in control. | Sensing has declared range and information limits. Solve recharge as an interesting preparation choice; do not make most combat consist of standing still waiting. |
| Cursed Seal | Choose a stronger altered moveset and mobility with a visible commitment meter. Changes to body silhouette and locomotion distinguish stages. | A brief surge or aerial attack with a clear landing/recovery. | Pressure windows and exhaustible resources give opponents decisions. End cleanly; do not punish a disconnect with permanent character loss. |
| Eight Gates | Escalating taijutsu, acceleration and specific high-tier techniques; the body mode changes melee strings and timing. | A dramatic final technique controlled by the player. | Make risk and ending legible before activation. A repeatable multiplayer adaptation should not silently erase a character to reproduce a story sacrifice. |
| Sand defense | Spend a sand reserve on automatic interception, manual shields or pressure/control; pulling defense into offense exposes a meaningful gap. | A large controlled capture or sand-wave sequence. | Opponents can change angle and force reserve decisions. Temporary sand respects buildings, routes and ownership. |
| Hyuga/Byakugan | Information, precise close-range pressure, a defensive rotation and controlled chakra disruption; avoid generic poison with a new label. | A short precise combo whose victim can understand the opening and escape rule. | Information cannot disclose arbitrary private state. Repeated disruption has a bounded escape; no permanent loss of input. |
| Medical and support equipment | Rescue, cleanse, cover and enabling teammate combos remain active choices with a viable offensive option. | A visible save or coordinated team attack. | Contribution rewards include support and objectives. Solo completion has a companion/alternate route, not mandatory population dependence. |
| Sealing and puppetry | Prepare a limited number of seals or command a persistent construct; placement and orders matter more than raw spam. | A trap reversal or coordinated construct technique. | Visible tells and destructible/interruptible opportunities. Loaded geometry, owner range, stale actors and recall are real lifecycle constraints. |

Baryon Mode requires a separate adaptation decision before implementation. The official database describes a life-consuming power, including effects on contact; a repeatable game mode must explicitly distinguish its chosen gameplay cost from that canon consequence. Do not quietly treat it as another permanent no-cost cloak. [Official description](https://naruto-official.com/en/news/01_1895).

Let powerful PvE builds overpower ordinary threats. Preserve earned dominance in familiar regions. Bosses challenge positioning, protection, control and coordination through telegraphed mechanics rather than flattening every build's damage or granting blanket immunity. Optional ranked PvP can use an openly stated rule set; casual PvE, duels and competitive normalization must not silently share conflicting tuning. Never turn every enemy into a perfect hard counter to the equipped kit.

Each set needs appealing passive effects and one unmistakable gameplay identity. Avoid twenty tiny affixes with unclear stacking. Define activation exclusivity, legal layered combinations, equipment swaps while transformed and save/reconnect behavior. A costume can be cosmetic without inventing a power. A prestigious armor unlock should come with a memorable challenge and a concrete new move.

## Make the world respond causally

World reaction is a service, not random bark spam. A settled server event supplies actor, target, event kind, location, intensity, faction and expiry. Nearby witnesses evaluate line of sight/hearing, relationship, current task and personal cooldown. Choose a small local response. Store only meaningful bounded consequences; do not save an unbounded history of every particle or hit.

| Player action | Immediate local response | Lasting consequence and limit |
| --- | --- | --- |
| Cast a small jutsu in a training court | Mentor watches, partner defends, practice target visibly responds. | Relevant mastery/objective credit only; no village emergency. |
| Manifest a threatening form in a civilian street | Nearby people step back or seek shelter; guards approach and warn. | A proportionate witnessed incident, with a way to de-escalate. Not instant hostility from the entire world. |
| Rescue an attacked civilian | That civilian acknowledges the actual rescuer; nearby allies cover the return. | A bounded reputation memory, local dialogue or service change. No repeated reward from the same fabricated incident. |
| Show controlled power after helping the village | Some residents admire it; a rival challenges or a mentor offers advanced work. | React to relationship as well as appearance. Not every transformation is treated as a crime. |
| Fight a known enemy repeatedly | The enemy changes tactics using what it plausibly observed. | Limited encounter memory. No reading the player's hidden inventory or perfect frame reactions. |
| Break a barrier in a dungeon | Dust, support debris, new sightline, enemy movement and a revealed route agree. | The run owns the change; it restores correctly or persists by declared world rules. |
| Return after clearing a regional threat | A checkpoint reopens, a patrol returns, supplies or dialogue visibly change. | Shared world state and personal quest state are separate. One party cannot permanently lock everyone else out. |

Residents need look-at behavior, routines, useful destinations, varying short dialogue, interruption/rejoin and meaningful relationships. Favor a few convincing routines over hundreds of frozen named models. A cook actually uses the kitchen; a guard notices a disturbance; a courier has a route and a recovery if blocked. Use original scripts and friend-created assets through the reviewed intake process. No required remote AI service or paid account should sit in the moment-to-moment gameplay loop.

Element interactions should create choices: a wind effect can redirect eligible projectiles; water can affect a defined lightning attack; temporary earth cover can block a sightline; fire can create a bounded hazard. Define the actual reaction table and ownership/protection rules instead of claiming every material interacts with every jutsu. In protected villages, keep destructive spectacle cosmetic or owned/temporary. In opt-in encounter arenas, restore only blocks still matching the run's expected edits, preserving intervening player changes and tile-entity data.

## Make effects feel physical

Every signature action has anticipation, execution, contact and recovery. Match hands, body pose, weapon orientation, sound, trail, target motion and ground response to the same authoritative result. A bright particle cloud is not a substitute for animation. Give local player input immediate presentation feedback while the server remains authoritative. Preserve understandable rejected/canceled casts and resource refunds.

Rasengan-like rotational impact and a Chidori-like committed lightning thrust must differ in charge, approach, range, release, impact and counter. Five starter natures cannot all remain the existing lance plus a status effect. Use canonical terminology only when the implemented move earns it. The current shared ward lances are explicitly interim.

Armor and modes need equip, idle, run, jump, attack, hurt, transformation and ending poses, including first-person hands and third-person silhouettes. Tail/cloth/energy animation, footstep changes, a short impact pause and local sound can sell force without prolonged control loss. Camera shake, flashes, blur, large avatars and sound peaks need adjustable reduced-effects alternatives with equivalent combat information. Do not require shaders to read an attack.

Environmental detail has a purpose: silhouettes identify districts; cloth, lanterns, leaves and water guide movement; voices and footfalls reveal nearby activity; light and weather reinforce a mood. Keep menus short, show costs and legal actions before commitment, use consistent icons and spell names, and provide a one-line reason when an action fails. Replace repeated coordinate-only instructions with visible signs, path composition, residents, optional compass hints and a readable journal destination.

## Dungeons and long-term variety

Retain the detailed five-campaign obligations in [the atlas](DUNGEON-ATLAS.md). Mist remains a 200×200 real-water settlement; Earth/Leaf, Cloud and Sand retain fractured terrain; Fire/Nether has the separately expanded citadel direction. Preserve v9 and recover/diff v17 inputs before replacing work.

Each dungeon needs an arrival view, a preview of its central danger, distinct encounter verbs, an optional discovery, an environmental set-piece, a boss whose phases alter decisions, and an exit/reward aftermath. Mix duels, crowd-clearing power demonstrations, movement, rescue, traps, environmental manipulation and coordinated finishers. A room can be calm, beautiful or ominous without being empty travel padding. Never make every campaign the same three waves with a different block palette.

Plan fast solo/co-op entrances and visible route choices. Reward mastery, support, discovery and special execution, with an understandable desired-drop path and duplicate conversion. Retry near the interesting encounter rather than after a long walk. A wipe, leader departure, actor unload, disconnect, replay or late join cannot duplicate rewards or strand a run. Combine authored layouts with bounded variations; do not randomize away a necessary clue, boss or visual composition.

Track combat pressure, recent setbacks and inactivity to schedule ambient encounters, quiet travel and discoveries. This is a pacing proposal, not permission to secretly adjust enemy health when a player performs well. Record the chosen rules. Keep main authored boss beats predictable in availability, while optional local activity varies. Test repetition over several sessions, not only the first clear.

## Finish the whole game through complete playable slices

The full scope includes nature releases, clan/discipline identity, eyes, taijutsu/weapons, seals, genjutsu, medical play, summons/companions, transformations/avatars, rank/trials, missions/story, village economy/crafting, factions/social play, traversal/exploration, housing/building, all five dungeon campaigns, audio/art/UI/accessibility and dependable multiplayer/save behavior. The 49-JAR mapping remains the inventory of reference obligations. A taxonomy, item count or this document does not finish any category.

The next high-value slice after the academy reliability fix is **one recognizable power kit, one short combat mission and one local world reaction delivered together**. Include normal-player acquisition, a preview, a tangible move change, a satisfying target response, a resident/guard reaction, reward/retry and reconnect. Prefer this over another compulsory introductory dialogue stage or another hundred shared-engine profiles. Then finish a contrasting kit and validate meaningful differences before growing the roster.

Use a completion matrix per kit/system: specified, implemented, acquired in normal play, native-verified, visibly tested, multiplayer-tested, saved/restarted, integrated with world/content. Record gaps and evidence. Maintain actual feature IDs and dependencies; deliver source, built candidates, compact evidence and GitHub checkpoints. A prompt cannot make unlimited work complete; it must make the next missing behavior unambiguous and prevent paper completion.

## Adversarial review before calling it fun or finished

| Challenge | Failure to look for | Required response |
| --- | --- | --- |
| Remove labels and recolor the effect | Every jutsu becomes the same generic projectile. | Change decisions, timing, interaction, pose and impact; not only names. |
| Give a new player the intended starter | They spend the session walking, reading, clicking practice or waiting. | Cut redundant gates; teach inside short meaningful action. |
| Give an experienced player endgame equipment | It adds stats but no new behavior, or trivializes every interesting encounter. | Add kit identity and optional mechanics that respect earned dominance. |
| Fight a stronger opponent | Substitution, defense and escape never matter; permanent stagger removes input. | Bound control chains and make a readable opportunity to respond. |
| Transform beside friendly civilians | Nothing happens, or every guard magically attacks forever. | Implement plausible witnesses, proportionate response and de-escalation. |
| Play with reduced effects and no shaders | Signature attacks become invisible or unreadable. | Preserve essential silhouettes, tells, sound cues and target information. |
| Play support or solo | Rewards require last hits or a full active server. | Credit contribution; provide a viable solo route. |
| Replay a dungeon three times | Identical delays, mandatory filler and useless duplicates dominate. | Shorten retries, vary optional events, preserve authored highlights and useful reward goals. |
| Disconnect with an active mode/run | Lost progress, orphan actors, duplicated items or a blocked objective. | Exercise actual native save/reload and cleanup; bind UUIDs/receipts correctly. |
| Profile an eight-player spectacle | Unbounded particles, area scans, actors or chunk work destroy responsiveness. | Set measured budgets and degrade cosmetic density before removing gameplay tells. |

Run a developer walkthrough first, then observe fresh players without coaching when available. Ask what felt powerful, what they understood, where they waited and what they chose to do next. Record observed behavior separately from inferred satisfaction; no invented player-retention claims. Optimize the specific dull segment, then replay the affected route. Use the existing performance targets on named hardware and publish measured limits rather than saying every detail is flawless.


---

## Repository source: design/IMPLEMENTATION-PROMPT.md

# Master implementation prompt

Use this prompt with the `hidden-block-village` project skill and the current repository. It is a standing product brief and execution method, not permission to spend money, acquire accounts, message others, or publish unreviewed third-party content. The owner's existing authorization for routine project commits/pushes remains in `AGENTS.md`.

---

You are continuing Hidden Block Village / Vale, an existing Minecraft Java shinobi game project. Build an immersive Naruto-inspired experience that retains Minecraft's movement, building, crafting, tangible world and persistent multiplayer. The owner wants feature-level replacements for the supplied 49-mod reference pack and substantial enhancements: complete chakra combat, progression, clans/eyes/natures, transformations, weapons, companions, original NPCs and quests, village life, co-op, and five exceptionally detailed dungeon campaigns. Do actual implementation work as well as design. Preserve the accumulated work across PCs.

## Naruto-first fun contract

The owner's updated direction is `design/NARUTO-FUN-BRIEF.md`: build recognizably Naruto gameplay, powerful armor/modes with distinctive moves, enjoyable weapon combos, world reactions and fast access to fun. Read it before selecting combat, progression, NPC or content work. Its pacing priorities supersede earlier long compulsory academy targets. Keep its proposed tuning distinct from measured current behavior.

Deliver a connected player experience: acquire or preview a power, understand a small configurable action set, use it in an interesting encounter, see physical feedback and a plausible world reaction, earn a useful reward, and resume after failure/reconnect. Existing ward names/shared lances/static vanilla guides are interim implementations. Public-facing Naruto identity must be supported by mechanics and presentation; changing an item's label alone does not satisfy the request. Preserve technical IDs and existing progress.

A featured armor/mode needs changed attacks, active techniques, mobility/defense, a signature moment, readable cost/recovery, first/third-person presentation and actual opponent/world responses. Let strong PvE characters feel strong. Challenge them with encounter decisions, not blanket immunity or constant hidden scaling. Keep advanced trial loaners explicitly temporary and safe for the real loadout. Do not lock every exciting action behind repeated dialogue, travel or grinding.

For each selected package, challenge whether it is recognizably Naruto without its label, whether the first few minutes are fun, whether the weapon/mode changes decisions, and whether world reactions follow witnesses and causes. Use the research ledger's direct evidence honestly; do not turn a few advertisements or one forum complaint into a claim about all players. During a bounded build sprint, record the deadline in the handoff, prioritize implementation/verification, and continue shipping useful checkpoints.

## Establish the real starting point

Locate the checkout whose remote is `tasx13ok-create/Hidden-Block-Village`. Read its `AGENTS.md`, `docs/current-handoff.md` and `design/README.md`; inspect worktree state and fetch/integrate remote changes without discarding local work. If the workspace opens in the older parent dungeon folder, follow its routing `AGENTS.md` into the current checkout. Do not treat an older v9 folder or a prior chat summary as the current source.

Read `design/MASTER-PLAN.md`, then only the relevant sections of `SYSTEMS-SPEC.md`, `DUNGEON-ATLAS.md`, `MOD-REBUILD-MATRIX.md` and `backlog.json` for the work you are doing. The full pack inventory is `design/reference-mod-inventory.json`. Match exact bytes and declared mod identity; several reference filenames contain misleading versions. The archive is reference input, not owned source or a public distribution asset.

Read the current handoff and exact-hash evidence for the real baseline. Core 0.3.10 now has an introductory academy and two contract prototypes; later Naruto identity, complete distinct nature moves and final NPC presentation remain open. Existing source covers eleven areas and 36 build artifacts. Item/profile counts and native assertions establish only their stated scopes, not a finished production world or game.

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

Use the detailed atlas. Mist remains a real-water 200Ã—200 waterfront settlement with seabed/piles, clear boat channels, layered usable interiors and coherent roofs. Earth/Leaf, Cloud and Sand have irregular fractured torn-ground bases, not rounded cones. Fire/Nether remains excluded from that treatment and gets a proposed monumental 320Ã—320 district/citadel expansion. Preserve the existing baseline and compare recovered v17 before replacing it.

Each campaign needs a room/route graph, narrative purpose, functioning puzzle, distinct encounter language, multi-phase boss, fair solo/co-op routes, meaningful reward and short recovery/exit. Detail supports material, navigation, story and atmosphere. Validate native stairs/slabs/doors/tile entities, support, roofs, gravity blocks, water, illumination, collision and landing points before offline glamour renders. Actual dimension atmosphere and BSL/Solas gameplay require real client review. Offline rendering cannot stand in for an in-game walkthrough.

Use immutable templates with explicit anchors and a bounded expedition lifecycle. Instancing/reset must not overwrite player areas or unrelated runs. Test disconnected members, restart, already-claimed rewards, full inventory and an interrupted reset. Difficulty variants add tactical demands rather than unlimited health and particle spam.

## Friend NPCs and preserved worlds

Original CustomNPCs data and the owned Residents runtime are separate. Intake the friend's world/profile CustomNPCs folders, relevant config/assets/dependencies, changed NPC list and baseline backup. Graph IDs and references, remap collisions consistently, preserve original exports and test in a copy. Decide whether to retain an optional compatible profile or translate behavior into Residents; do not pretend a clone export automatically ports scripted behavior. Keep worlds, personal progress, credentials and unreconciled third-party content out of public Git.

The private `Naruto.test` stage and reviewed inventory are not present in the inspected folders. Do not synthesize a replacement inventory and claim it matches the missing original. Existing archives, incomplete candidates, rollback files and world saves are preserved. Build complete production packages only with their actual pinned inputs and separate output IDs.

## Verify, deliver and continue

Evidence ladder: component behavior â†’ native runtime â†’ actual local client/server handshake â†’ visible gameplay/graphics â†’ copied production-world save/restart/migration â†’ actual Magma/plugin roster â†’ hosted Bisect start/restart/client connection. Report only steps actually run, with their exact identities and limits. Keep a live user inspection session available unless the task requires restarting that specific session or the owner asks.

Update the backlog task and related design if behavior changed. Record changed files, meaningful checks/results, artifact hashes, known defects and the exact next executable action in `docs/current-handoff.md`. Commit and push completed authorized checkpoints; verify the remote commit. Keep private outputs local. Final communication should lead with the actual result and usable artifact or game state, then material limits. Do not claim the entire game is finished when only this slice is complete, and do not replace execution with another vague promise to continue.

---

Suggested first resumed task after this planning checkpoint: implement the observed empty-list JEI catalog bridge correction in a new candidate, test empty/nonempty/error cases, build the affected module/dependencies, launch a separate graphical smoke profile and verify the bridge no longer warns. Then advance the normal-player academy/mentor/acquisition slice and Mist encounter graybox according to the backlog. Do not replace the owner's currently running inspection JARs during play.


---

## Repository source: design/SYSTEMS-SPEC.md

# Shinobi systems and behavior contracts

All new values below are proposed tuning defaults, not observations of legacy mods. Existing technical IDs and saved states remain stable until an explicit migration is implemented. Narrative Naruto-facing names are presentation/content keys; internal namespaces remain owned and save-compatible.

## 1. Character identity and persistent state

Persist schema version, player UUID, village allegiance, independent village reputation, rank, nature affinities, lineage/specialist choices, learned techniques, mastery, equipment ownership, modes, companion contracts, mission graph state, exploration discoveries, currency balances, receipt/outbox records and cooldown deadlines. Use stable keys rather than translated text or collection indexes. Unknown fields survive migrations where practical; unknown referenced content is quarantined with an actionable diagnostic rather than silently replaced.

Character creation offers village and a coherent starting specialization. A server can opt into randomized lineage for roleplay, but the default design provides a comprehensible choice and a deterministic route to desired gameplay. Never make paid rerolls a prerequisite. Explain rarity as content rarity or narrative status, not an exemption from balance. Separate character power from administrator role, social rank and cosmetic identity.

Rank path: academy student → genin → chunin → jonin → specialist/leadership responsibilities. Promotion combines practical skill checks, mission contributions and a narrative/training assessment. Teamwork and support count. Kage is a limited governance role with a configured term and succession rules, not an unlimited combat multiplier. Missing-nin status changes services and missions while retaining a viable progression route and a way to recover reputation.

## 2. Chakra, stamina and strain

Preserve the current resolve ledger until a versioned chakra presentation/migration exists. The desired model separates chakra expenditure from exertion and transformation strain. Chakra powers techniques; exertion limits repeated physical bursts; strain limits unsafe form stacking. Food and rest support recovery without making eating a combat exploit. Do not add three simultaneous bars before the HUD and encounter design justify them.

Proposed starting tuning: 100 chakra; basic techniques cost roughly 6–12, tactical techniques 16–30, high-impact techniques 35–60; exact costs are per-technique data. Regeneration slows during active casting and direct combat but never leaves a novice permanently unable to act. Basic weapon defense and locomotion remain useful when chakra is exhausted. Concentration recovery creates a visible vulnerable window; it cannot be held while attacking, changing modes, riding a protected vehicle or spamming reconnects.

Costs reserve before native callbacks. Denied effects refund only the reserved attempt to the actual debited account. Successful effects remain charged even if callbacks replace the held stack or lesson state. Model each action as a state machine: eligible → reserved → windup → active effect → settlement → recovery. Record interruptions explicitly. Reconnect, death, dimension change, target despawn and packet duplication must not reset an earned cost or create two effects.

## 3. Universal technique record

Each technique definition must specify:

| Field group | Required detail |
| --- | --- |
| Identity | Stable ID, localization keys, version, nature/lineage/style tags, source observation and original author |
| Acquisition | Mentor/trial/recipe path, rank/mastery prerequisites, respec and migration behavior |
| Inputs | Tap/hold/release behavior, hand-sign sequence if any, focus-slot rules, aim mode and cancel binding |
| Timing | Windup, active interval, recovery, charges, cooldown scope, saved deadlines and interruption states |
| Resource | Initial reservation, sustained drain, hit/miss/cancel settlement, maximum duration and strain |
| Geometry | Target filters, reach, sweep/volume, line of sight, collision masks, loaded-chunk and world-border behavior |
| Effect | Damage/status/utility, scaling formula, stacking/refresh policy, secondary-effect conditions and cleanses |
| Counterplay | Tell, dodge/guard/dispel route, terrain interaction, immunity windows and ally response |
| Presentation | First/third-person animation, original model/material, readable VFX, audio, reduced-effects version and camera behavior |
| Multiplayer | Server authority, request payload, rate limit, owner/team checks, observer range and stale-message rejection |
| Recovery | Death, relog, respawn, transfer, unload, expiration, owner replacement, exception and disabled-content handling |
| Evidence | Component cases, native scenarios, two-client behavior, recorded visual review and remaining limitations |

No label-only techniques. A projectile, area, summon and stance cannot share one generic damage action and be counted as four finished powers. Common engines are desirable; distinct behavior and authored content still need proof.

## 4. Combat foundations

Default melee uses normal Minecraft aim and physical weapon reach. Give light attacks, held/charged attacks, guard, deflect and a short evasive action clear recovery. Separate collision from visual trail. Server chooses valid hits; packets do not carry trusted damage or arbitrary target coordinates. Current Crescent Talons lock, dodgeable lunge, rear damage and bounded bleed are a foundation to extend, not proof that all reference weapons are complete.

Soft lock helps orientation but must respect occlusion, range, targets behind the player, owner/team protection and disengagement. It must not silently rotate a player's camera across large angles or hit through walls. Spectators, allies, peaceful service residents and dead actors are ineligible unless an explicit opt-in scenario says otherwise. Combat through tall grass should trace past appropriate replaceable foliage while still respecting solid terrain and entity ordering.

Guard resolves facing and impact direction; stronger attacks can break guard with a clear tell. Control effects have bounded duration, diminishing repeat value and an escape/counter opportunity. Avoid continuous stun chains and unlimited airborne juggles. Apply damaging secondary effects only after accepted primary contact when appropriate. Vanilla/Forge cancellation, team protection, region protection and target invulnerability participate in the same outcome.

Movement includes short dash, wall/tree training, water-surface control, leap and substitution. Validate finite positions, loaded geometry, collision, support, world border and destination dimension. Distinguish a rejected move from a move that happened before a callback threw. Preserve vanilla jumping, swimming and building. Training routes teach movement in forgiving spaces before demanding it over a lethal drop.

## 5. Five base natures and advanced families

| Nature | Introductory kit | Tactical identity | Counter and world interaction |
| --- | --- | --- | --- |
| Fire | Small aimed burst, sustained cone, delayed zone | Pressure and space denial | Visible ignition tells; extinguish/evade; no persistent terrain burning by default |
| Water | Water bolt, moving serpent, temporary binding | Flow, redirection and control | Break line of sight, release/rescue conditions, terrain wetness as bounded context |
| Earth | Raised cover, grounded thrust, armor stance | Position and durability | Flanks, climb routes, finite cover lifetime; restore changed blocks deterministically |
| Wind | Cutting arc, push/gust, weapon infusion | Spacing and projectile interaction | Telegraph direction; respect knockback immunity and borders; no ally griefing |
| Lightning | Aimed lance, short acceleration, committed melee charge | Precision and brief burst | Interruptible commitment, clear endpoint, predictable escape angle |

Add combined families through parent-mastery plus an authored trial: Ice, Wood, Lava, Boil, Storm, Magnet, Scorch, Dust and Crystal, followed by other verified reference obligations. Each has an engine-specific role, not simply two damage colors. Ice uses readable routes and breakable constraints; Wood emphasizes seeded control and support; Magnet requires a bounded affected-material set; Dust needs strong exposure/counterplay and caps against bosses; Crystal cannot leave permanent world debris. Space-time, sensory, medical, seals, sound, puppetry, insects, shadows and mind techniques are independent specialist tracks.

Nature interactions use a small documented rule table. For example, water can reduce a temporary fire zone; earth can interrupt a ground-traveling attack; wind can redirect an eligible light projectile. The table controls eligibility, magnitude, ownership and VFX; it must not become an uncontrolled pairwise physics simulation. Decide conflict order deterministically on the server. In player-owned building areas, temporary battle terrain must not overwrite containers or protected blocks.

## 6. Lineages, eyes and specialist paths

The target roster includes requested Naruto archetypes, subject to observed-reference expansion: Uchiha, Hyuga, Uzumaki, Senju, Nara, Yamanaka, Akimichi, Aburame, Inuzuka, Kaguya, Hozuki, puppet specialists, taijutsu/gates specialists, medical specialists, sealing specialists, sensory specialists and other identified pack-specific branches. This is a planned roster, not a claim that every named path already exists or is implemented in `mods.zip`.

| Path | Signature mechanic | Growth | Essential counter/limit |
| --- | --- | --- | --- |
| Eye prediction/copy archetype | Read a bounded attack tell; copy only eligible learned-form techniques | Training, sparring and authored trials | Information is partial; no invisible-player ESP; expensive focus and copy whitelist |
| Gentle-fist/sensory-eye archetype | Precise close-range pressure and bounded chakra sight | Accuracy drills, defensive stance, team reconnaissance | Range/occlusion limits; concentration tells; no unrestricted x-ray mining |
| Sealing/resilience archetype | Placed marks, bindings and rescue/support seals | Ritual components, mentor puzzles, squad utility | Placement/protection validation, destructible or dispellable nodes, active-node cap |
| Wood/life archetype | Temporary terrain control and ally shelter | Ecology tasks and layered mastery | Finite structures, area exclusion, no overwrite of saved player buildings |
| Shadow archetype | Facing/light-aware tether and position manipulation | Setup, pursuit and multi-target restraint | Line break, maximum reach, diminishing control, visible anchor |
| Mind archetype | Information, brief control window and coordinated support | Read intent, bind, assist | Real-player camera/agency preserved; strict PvP opt-in and breakout |
| Expansion archetype | Size, mass and committed impact | Food/preparation and form discipline | Model/collision consistency, door clearance, recovery and stamina drain |
| Insect archetype | Small owned swarms with sensing/drain/support roles | Colony care and finite brood capacity | Aggregate bounded actors/effects, owner cleanup, area counterplay |
| Hound archetype | Bonded companion and coordinated pincer attacks | Trust and team timing | Owner/companion separation limits, protected targets, fair replacement on loss |
| Bone archetype | Persistent short-lived weapon forms and movement pressure | Controlled-form trials | Health/strain costs, bounded projectiles, distinct weak windows |
| Fluid-body archetype | Conditional evasion and fluid movement | Source-context training | Finite duration, cooldown lock, defined electrical/containment interaction |
| Puppet archetype | Remote weapon actor with a vulnerable controller | Modules and positioning | Leash range, line/terrain restrictions, lost-control state and cleanup |
| Gates/taijutsu | Escalating physical commitment | Skill trials and controlled exertion | Increasing strain, meaningful disengagement, no free toggling past costs |
| Medical | Triage, diagnosis, cleanse and protected recovery | Rescue objectives and field practice | Cast exposure, resource cost, anti-stacking, support contribution credit |

Eye progression separates acquisition, awakening, mastery and sustained use. Advanced stages require an authored story/trial rather than repeated self-harm, a payment or unexplained kill count. Provide multiplayer-consensual alternatives to player-sacrifice requirements. Dojutsu, sage, curse and beast transformations have explicit compatibility slots and conflicts; never assume all can stack. Save cooldown/strain through relogging. Respec retains earned story flags, clearly identifies refundable points and cannot duplicate one-time grants.

## 7. Nine ward paths, sage forms and large silhouettes

Treat each of the nine beast-inspired paths as a unique movement/combat/support identity with an original visual design: sand/defense, blue-flame agility, shell/tide, molten strength, steam mobility, corrosive mist, aerial versatility, ink/tentacle control, and powerful multi-tail coordination. These thematic targets require authored designs and behavior review; they do not assert reuse rights to another mod's named models.

Progression stages: discovery/contract → unstable cloak → trained partial manifestation → mastered mantle → conditional full manifestation. Each step has a mentor/inner trial, a distinct tactical action, cost curve, counterplay and visual state. Full manifestation is restricted by terrain fit, encounter rules and performance budget. A transformation cannot clip the camera, leave an invisible hitbox or turn an entire dungeon unreadable.

Tail and wing geometry follows a documented rig: body-space anchor, local parent chain, animation tick/frame separation, collision-aware displacement, crouch compensation and interpolation reset. Test all nine at idle, sprint, backward strafe, crouch, swim, flight if supported, hit reaction, death, mount/dismount, teleport and dimension transfer. Test 30/60/144 FPS and a low-tick-rate observer. Do not replace a collision-corrected pose with an uncorrected reconstruction later in the frame. Current original Vale wards and the separate recovered `hvbijuu` Matatabi/tail renderer are distinct implementations; neither silently replaces the other.

Sage and curse paths add preparation and risk, not free stat inflation. Balance them against ordinary specialists and support builds. Gated final-stage moves show cost and consequence before activation. Co-op design must not require every player to bring a legendary mode.

## 8. Equipment, crafting and provisions

Keep six physical weapon rack slots with exact stack metadata, ownership serials and server validation. Each weapon family has a role and at least one characteristic action: precision blade, reach weapon, sweeping polearm, defensive staff, committed heavy weapon, claws, thrown tools, bow/launcher, chains and specialist artifacts. Legendary weapons need actual mechanics—resource exchange, sustained pressure, trap setup, defense break or controlled traversal—not only damage multipliers.

Maintain a complete item parity ledger: acquire, equip/use, craft, repair/upgrade, trade/drop, store, die/recover, reconnect, migrate and render. The ledger distinguishes a registered counterpart from a verified behavioral counterpart. Recipe book and mission guidance must expose legitimate acquisition. Preserve or compensate obsolete content through explicit migration receipts rather than deleting registries from a saved world.

Cooking should make village life useful: gather rice/vegetables/protein, process at authored workstations, cook regional dishes, share a team meal and prepare field rations. Machine or workstation state survives unload/restart with exact ingredients and results; full outputs do not void paid inputs. Food benefits have categories and anti-stacking rules. Backpack upgrades preserve actual stacks, reject nested-container duplication and reconcile death/drop behavior with rack storage.

Currency sources are missions, production services, selected drops and cooperative milestones. Sinks are provisions, repair, travel, cosmetic crafting, village projects and bounded respec. Keep power progression available through play. Rewards use deterministic transaction IDs, committed costs and a paid-delivery outbox; crashes or a full inventory produce exactly one payable outcome. Display costs and reward rules before commitment.

## 9. Missions, NPCs and friend-created content

Mission schema: stable ID/version; prerequisites; offered-by role; participants; objective graph; optional branches; timers; fail/retry policy; protected areas; contribution rules; rewards; dialogue/localization; repeatability; idempotency and migration. Objectives emit server-observed events such as escort arrival, evidence secured, guarded ally surviving or an encounter completion receipt. A client notification never grants mission credit by itself.

Party state handles leader departure, member disconnect, late join, role changes, shared objectives, individual rewards and already-completed members. Replay assists receive appropriate credit without duplicating first-clear rewards. Failed escort pathing or an NPC death must yield a recoverable objective rather than a permanently stalled journal. Make objectives readable without opening a debug console.

Residents have authored identities, appearance, homes/workplaces, schedules, service roles, relationships, alert/flee/recover states, dialogue conditions and bounded navigation. At minimum the village needs academy mentor, mission officer, quartermaster, medic, cook, blacksmith, archivist, local civilian contacts and travel official. A schedule that moves an essential mentor must provide a findable destination and fallback interaction.

Friend NPC intake requires world `customnpcs/`, profile `customnpcs/`, relevant config, custom assets, dependency versions, changed NPC list and pre-edit backup when available. Preserve source files privately, build an ID/reference graph, compare new versus changed definitions, remap collisions consistently and keep an explicit mapping report. Clone JSON is not the whole world; placed NPCs and scripted blocks can require region/entity data. Never overwrite an entire live world's NPC folder to merge a few actors.

Two supported outcomes are distinct: retain a compatible optional CustomNPCs profile with original authored definitions, or translate approved behavior/content into the owned Residents runtime. Track unsupported scripted APIs and dependencies; do not claim the translation preserves them until tested. Test dialogue branches, quest transitions, trades, factions, skins, sounds, equipment, script errors, navigation and save/restart. Only reviewed original definitions/scripts and permitted assets enter public Git; private player progress and full saves do not.

## 10. Villages, travel, faction play and exploration

Five villages have distinct arrival silhouettes, street structure, service placement, region materials, ambient sound, food, craft specialties and mission identities. Civilian spaces contain homes, storage, repairs and work traces rather than repetitive empty facades. Give villages at least two public destinations and one quiet route between major services. The player's house/building remains meaningful.

Travel begins by walking and discovering routes. A bound travel network unlocks through exploration or services; it validates destination support, dimension, protection and combat lock before charging. Failure restores the correct reservation and preserves the discovered destination. Rescue travel has a separate bounded policy. Maps reveal discovered places and party waypoints; do not expose enemy players, hidden rooms or undiscovered objectives by default.

Opt-in village conflict uses declared windows, objectives, nonparticipating safe zones, defender recovery and a recorded end condition. No offline village erasure or player-build destruction by default. Exams use scheduled or on-demand brackets, role diversity, spectator bounds, disconnect recovery and rematch rules. Rewards emphasize achievement and identity without invalidating ordinary missions.

## 11. HUD, controls, codex and accessibility

Keep E as Minecraft inventory and preserve standard hotbar/building behavior. Retain current configurable focus/rack actions and F8 Controls until a versioned redesign is tested. New bindings must be conflict-checked and remappable; onboarding demonstrates only a few at a time. Never trigger an ability while entering chat, searching JEI or using a text field. Respect press/release, focus loss and charge cancellation.

HUD shows only contextually useful health/chakra, active technique, recovery, party distress and current mission. Use consistent shapes plus text for status rather than color alone. First/third-person VFX should not cover crosshair, target tells or building placement. Reduce camera shake, flashes, particles and motion separately; offer readable text scale, subtitles, effect contrast and a stable high-contrast HUD.

The original codex replaces fragmented helper systems with technique prerequisites, crafting/resource information, enemy observations, regional notes, training feedback and mission history. Mark unknown information as unknown. Search supports keyboard input and real controller focus if implemented. JEI remains an optional test/integration aid, with the observed empty-list hide bug queued for a tested fix.

## 12. Networking, persistence and performance contracts

Requests are versioned and bounded. Validate message size, finite values, sender ownership, actual selected item, known ability, dimension, world tick/deadline, state version, line of sight and rate limit. Use small server snapshots and sequence numbers for confirmed actions. Never accept client-provided rewards, raw NBT edits, arbitrary script paths or unbounded target lists.

Persistent capabilities and world-saved data need explicit migrations and atomic/replaceable writes where supported. Exercise partial write, stale snapshot, relog during action, crash between debit and delivery, missing optional mod, unavailable texture, removed content and a server restart inside a dungeon. Log useful IDs and state transitions without logging authentication secrets or personal data.

World-editing techniques and dungeon resets keep a bounded journal or use reserved encounter spaces. Never regenerate arbitrary production chunks to reset a fight. Entity/particle/sound budgets are configurable per player, encounter and region. Distant actors degrade animation/effects but retain authoritative logic. Use bounded task queues rather than scanning all worlds every tick. Match a production client's and server's shared JAR bytes; optional client graphics mods must not become dedicated-server hard dependencies.

## 13. Recognizable Naruto repertoire, with actual distinct mechanics

The requested experience should visibly communicate Naruto techniques, clans, village roles and progression. Neutral legacy/internal codenames must not become an excuse to deliver an unrelated fantasy game. Keep stable technical IDs and an explicit content/localization mapping to the requested presentation; independently author implementation and visuals, and keep unresolved publication/provenance questions visible. This table is a proposed gameplay interpretation, not a claim that all entries exist in the supplied pack or currently work.

| Requested technique/family | Concrete original implementation target | Acceptance obligation |
| --- | --- | --- |
| Shadow Clone | Owned, time-bounded decoys/support actors with a deliberate command/attention role and finite resource cost | Correct attribution and expiry; no item/XP duplication, infinite actor count or stale owner reuse |
| Substitution | Reactive, limited reposition with an understandable opening and retained consequences | Forge protection/collision and actual movement determine settlement; no free damage cancellation on a failed move |
| Transformation | Bounded visual disguise with declared interaction/hitbox/information limits | Correct first/third-person rendering and observer identity; no unauthorized player impersonation or changed damage authority |
| Rasengan family | Committed close-range chakra shaping with charge/approach/impact and a meaningful recovery window | Cannot hit through cover; interrupted or invalid approach has a documented cost; visual impact matches actual contact |
| Chidori family | A precise committed lightning approach with readable direction and counterplay | Server-valid path/endpoint; enemy can evade; no homing through walls or unintended ally damage |
| Fireball/Phoenix-style patterns | Different projectile formation and timing rather than a shared recolored bolt | Distinct spacing/commitment; correct protection and no permanent accidental village fires |
| Water Dragon/Shark patterns | Distinct travel, turn/commitment and water-context behavior | Dry/wet cost rules, collision, target eligibility and actor cleanup are explicit |
| Earth Wall/earth armor | Temporary geometry/cover versus personal defensive stance, implemented as different engines | Restore only owned temporary blocks; preserve containers, protection and actual collision |
| Gentle Fist / rotation | Precision close-range pressure and a separate committed defensive action | Angle/range and pressure/defense conditions are server-observed; no permanent ability denial |
| Shadow Possession | Visible bounded tether with a sustainable resource/position contest | Escape/line-break and repeated-control limits; no indefinite freeze or control through unloaded geometry |
| Mind Transfer | An opt-in PvP-compatible information/control encounter with protected player agency | Target restrictions, breakout, owner vulnerability and disconnect recovery; no unconstrained client input takeover |
| Puppet techniques | A controlled actor with distinct modules/attacks and a vulnerable controller | Leash/line constraints, module state, owner cleanup and exact equipment preservation |
| Insect/hound techniques | Distinct owned support ecosystems and coordinated attacks | No unbounded swarm entities; actual companion contribution, owner limits and cleanup |
| Sealing chains/barriers | Placement, anchors, binding and cooperative utility rather than generic potion icons | Ownership, geometry, dispel/counter, expiry and reload behavior are demonstrated |
| Flying-mark/space-time techniques | Authored discovered/placed marks with strict relocation and combat policies | Safe loaded destination, protection, costs, stale marks and movement callback exceptions are settled correctly |
| Genjutsu | Readable illusion/deception with counter skills and bounded sensory presentation | Reduced-effects option, escape conditions, no permanent blindness or fake client-authoritative damage |
| Susanoo-style manifestation | Original large protective/weapon silhouette with staged commitment and camera/terrain fit | Model and hitbox coherence, cost/strain, crouch/teleport/relog and observer interpolation pass |
| Sage/curse/gates forms | Different preparation, risk, support and physical pressure paths | Each changes choices; explicit strain/conflict rules; meaningful normal-player mastery route |
| Beast cloaks/full forms | Nine distinct rigs and kits with partial-to-full progression | Every path has a signature action, weakness, acquisition and tested tail/body/camera behavior |
| Advanced combined natures/eyes | A traceable expansion of the complete reference obligations and requested roster | Each unique effect closes its own behavior/evidence row; a generic label or shared placeholder cannot close it |

The same discipline applies to recognizable weapons, village dress, mission types, ranks and environmental motifs. The owner should be able to point to a feature in-game and understand its intended shinobi role from behavior and presentation, while the repository retains original authored source and an honest rights/provenance record.


---

## Repository source: docs/build-modules.md

# Build the recovered source workspace

The source build assembles the recovered modules in dependency order and checks
the artifacts actually produced by their Gradle tasks. It does not start
Minecraft, modify `Naruto.test`, connect to Bisect, or produce an accepted full
server/client package.

Use JDK 25 to run the pinned Gradle 9.5.0 wrapper and an Azul JDK 8 to compile the
Minecraft components. See [Core build setup](build-core.md) for installation and
wrapper checksum details. Python 3.10 or newer is required by the orchestration
script; it uses only the Python standard library.

## Build all source areas

From the repository root on Linux or macOS:

```sh
export JAVA_HOME='/path/to/zulu-jdk25'
export VALE_JAVA8_HOME='/path/to/zulu-jdk8'
export PATH="$JAVA_HOME/bin:$PATH"
python tools/build_modules.py
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\zulu-jdk25'
$env:VALE_JAVA8_HOME = 'C:\path\to\zulu-jdk8'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
python .\tools\build_modules.py
```

The script builds Core first, reads the exact development artifact reported by
Gradle, and supplies it to Encounters, Rack, Residents, the world compatibility
module, and the server compatibility adapter. It similarly supplies the freshly
built Residents development artifact to the Residents server bridge. There are
no hard-coded historical Core filenames and no private compile-only shim JAR.

After the first Core build prepares the Minecraft development API, the other
Forge modules reuse that same API. Its complete SHA-256 is recorded in the build
report. Its classes are not included in the mod outputs. If a separately prepared
Forge 1.12.2 stable-39 development API is already available, select it explicitly:

```sh
python tools/build_modules.py --prebuilt-minecraft /absolute/path/to/recompiled_minecraft-1.12.2.jar
```

This is an optional build input, not a replacement for testing the production
mods against the intended Forge/Magma runtime.

To inspect the order or build a subset, including its dependencies:

```sh
python tools/build_modules.py --list
python tools/build_modules.py --modules vale-residents-server-bridge vale-encounters
```

An environment with an existing approved Gradle launcher can pass
`--gradle-launcher /absolute/path/to/launcher.py`. Arguments are passed as process
arguments without evaluating shell command text. The launcher remains
responsible for the environment's Java, proxy, and trust-store settings.
Additional wrapper arguments use `--gradle-arg=-Pname=value`.

## Artifact roles

| Source area | Built output and limit |
|---|---|
| `vale-orders` | Production Core mod, development API and source archive; component tests run through `check`. |
| `vale-encounters` | Production Encounters mod, development API and source archive, compiled against the current Core API. Historical native checks are separate evidence. |
| `vale-rack` | Production Rack 0.1.2 mod and development API, including six-slot quick stow/equip. Its native integration checks belong to Core's separate private recovery fixture. |
| `vale-residents` | Production Residents mod, development API and source archive; resident/dialogue component checks run. |
| `vale-residents-server-bridge` | Separate server-only adapter compiled against the current Residents API. |
| `vale-world-compat` | World compatibility mod and development/source artifacts. The newly implemented `CompatibilityChecks` verifies opaque NBT preservation, copy isolation, cooking progress, all legacy metadata values, and cleanup of restart state. It does not load the actual saved world. |
| `field-hud` | Client HUD mod and development/source artifacts. Actual rendering and input acceptance remain separate. |
| `field-controls` | Client Controls mod and development/source artifacts; the six recovered component checks run. |
| `vale-compatibility-helpers` | Three independently built adapters: client lifecycle, server damage compatibility, and server command bridge. They must remain separate JARs with their own side requirements. |
| `server-bootstrap` | Java 8 wrapper library and source archive. It contains neither Magma nor an embedded deployment plan and is not a runnable server distribution. |
| `village-standards` | Complete newly authored 0.3.1 runtime mod, development API and source archive. Component checks cover geometry, saved records, the verified historical entity ID and integer village format, and exact recovered-resource hashes. Actual saved-world and graphical acceptance remain separate. |

Each successful module writes `build/vale-build.json`. The orchestrator rejects
outputs outside that module, duplicate declarations, invalid archives,
non-Java-8 class files, bundled upstream/API classes, and known native test
classes. It hashes every selected output and checks the copied bytes again.
Stale metadata is removed before invoking the build, so a successful process
without fresh artifact declarations cannot reuse an earlier result.

The per-run report, logs, and checked output copies are under
`build/source-builds/<run-id>/`; `build/source-build-report.json` points to the
latest run. `source-inputs.json` pins the source, build scripts, wrapper, and
component test inputs. Generated files and isolated native fixtures are excluded;
their evidence belongs to their own run. A changed source input during the build
causes the aggregate result to fail rather than describing mixed inputs as one
verified snapshot. A failed dependency prevents its dependents from being built, while
independent modules are still checked. Any failed or skipped module causes a
nonzero final exit status. A `PASS` entry establishes the stated component build
only. The report always records that Minecraft and hosted acceptance did not run.

The source snapshot's digest is the SHA-256 of its `files` array serialized as
compact JSON with sorted object keys. It is not the byte hash of the enclosing
pretty-printed manifest. `git_commit` records the current parent commit; when
working changes are present, the per-file manifest identifies the built source.

## Direct companion builds

For a direct build outside the orchestrator, first build Core and supply the
actual development JAR reported in `modules/vale-orders/build/vale-build.json`:

```sh
./gradlew -p modules/vale-orders writeValeBuildMetadata
./gradlew -p modules/vale-encounters \
  -PvaleCoreDevJar=/absolute/path/to/current-core-dev.jar writeValeBuildMetadata
```

Rack, Residents, the world compatibility module, and the compatibility-helper
project use the same `valeCoreDevJar` property. Residents also accepts its old
`valeApiJar` argument for existing local scripts. The Residents server bridge
uses `residentApiJar` with the actual current Residents development JAR.
Missing inputs fail with a descriptive error when the compile classpath resolves;
read-only task discovery remains available before dependencies are built.

## Isolated native acceptance fixture

The [disposable Forge runner](native-checks.md) compiles the separate helpers,
verifies exact production inputs, launches a fresh synthetic world and retains
its measured result. CI runs it after a successful component build. The
following manual steps describe the same private fixture boundaries.

Core 0.3.9's recovery fixture is a separate mod under `recoverychecks/`. Its Rack
checks compile against the actual current Rack development JAR. First build the
production modules with the orchestrator, then use the development API path
reported in `modules/vale-rack/build/vale-build.json`:

```sh
./gradlew -p modules/vale-orders \
  -PvaleRackDevJar=/absolute/path/to/current-rack-development.jar \
  reobfRecoveryChecksJar
./gradlew -p modules/village-standards reobfStandardsNativeChecksJar
```

The `valeRackDevJar` input is required for this private Core fixture, not for
production Core. It is a compile input only; Rack classes are not copied into
the fixture or Core. The optional `prebuiltMinecraft` property described above
can also be supplied to these commands.

Each module writes its reobfuscated helper to its own
`build/private-native-checks/` directory. Use the helpers without the `-dev`
classifier when testing the reobfuscated runtime mods. The current Core 0.3.9 server fixture roster contains these nine production
mods. Its exact version-bound results belong in the current verification
record. The historical Core 0.3.8 656-check result used Standards 0.3.0;
Standards 0.3.1 later passed its own 601-check native suite. Those historical
records remain unchanged.

| Production server mod | Candidate runtime file |
| --- | --- |
| Core | `vale-orders-0.3.9.jar` |
| Encounters | `vale-encounters-0.3.6.jar` |
| Rack | `Vale-Rack-0.1.2.jar` |
| Residents | `vale-residents-0.2.0.jar` |
| Residents server bridge | `vale-residents-server-bridge-0.1.0.jar` |
| World compatibility | `vale-world-compat-1.0.0.jar` |
| Forge damage compatibility | `Vale-Forge-Damage-Compat-0.1.0.jar` |
| Server bridge | `Vale-Server-Bridge-0.2.0.jar` |
| Village Standards | `Village-Standards-0.3.1.jar` |

Add both private QA mods to that disposable instance's `mods/` directory:

- `vale-recoverychecks-0.3.9.jar`
- `vale-standards-checks-0.3.1.jar`

Use the exact production bytes from the candidate component build. The fixture
records the loaded mod filenames, versions and SHA-256 digests; a matching name
alone does not establish that the tested bytes are the release candidate. Field
HUD, Controls, graphical client adapters and shader packs are not server fixture
dependencies.

Run the server with Java 8 in a separate disposable directory. Set these fixture
properties in its `server.properties`:

```properties
level-name=vale-recovery-test
spawn-monsters=true
spawn-animals=true
difficulty=1
```

The hostile and livestock fixtures require those spawn policies; Magma applies
them to scripted entity spawning as well. Create an empty file named
`.vale-recovery-test` in the server's working directory, and supply both JVM
properties before the server launch entry point:

```text
-Dvaleorders.recoverycheck=true
-Dvalestandards.nativecheck=true
```

The instance must have no real connected players. Core creates its synthetic
player after checking that condition; the Standards helper permits that caller
and rejects other connected players. The fixture modifies its disposable world
and synthetic inventories, writes `native-recovery039-result.json`, then shuts
the server down. Inspect the report's `passed` result and loaded artifact hashes
before citing a native pass. Compiling the helpers or completing a component
build does not establish that result.

Neither helper is included in a production JAR, source JAR, default component
metadata, or CI distribution artifact. They are not part of a normal server
installation. A disposable native pass does not establish saved-world migration,
matching graphical-client behavior, visual quality, or hosted acceptance. In
particular, Standards 0.3.1 handles the verified historical entity ID and
variant NBT, while actual saved records still require checks against a copy
of the real world. See the
[Standards runtime contract](../modules/village-standards/README.md).

For the focused Standards-only native run, retain the same nine production
mods, include only `vale-standards-checks-0.3.1.jar` as private QA, and use
`-Dvalestandards.nativecheck=true -Dvalestandards.nativecheck.standalone=true`.
The marker, disposable world name and absence of real players remain required.
This mode writes `vale-standards-native-result.json` and shuts down after the
single checked run. The exact PC Magma base used for the 0.3.1 evidence has
SHA-256 `2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`;
the same version filename from a fresh upstream download has different bytes.

## Continuous integration

`.github/workflows/source-checks.yml` defines two read-only jobs:

- The Python release and bootstrap regression suite, using an Azul Java 8 compiler.
- The source component builds, using JDK 25 plus Azul JDK 8, followed by archived
  build reports and only the checked project-owned outputs.

The workflow pins action commit identities, grants no repository write
permission, and does not publish a release or deploy a server. A component-build
artifact remains a candidate until the separate release gates are satisfied.
