# Naruto identity, power and a living Minecraft world

## Latest integration direction,14November2026

Keep the supplied original mods as the base experience. Our Core, controls, HUD,
missions, compatibility and encounter systems sit on top and connect them.
Restore the original item icons and worn armor designs of mods we did not make,
especially Varied Commodities. Do not enable the reference flat-icon replacement
pack by default or let compatibility resources/registries shadow an installed
original provider. Keep our own items, armor, controls and visual effects in
their project namespaces. Earlier broad replacement/reskin plans are historical
where they conflict with this direction; preserve the complete functional scope
through integration and enhancements instead of silently cutting features.

The next five boss dungeons are NEW coliseum-scale arenas, each500×500×250.
They supplement the preserved v17 campaigns. The independent-chat execution
handoff is `collaboration/arena-build-orchestration/START-HERE.md`.

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
