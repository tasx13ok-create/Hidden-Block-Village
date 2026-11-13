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
