# Playable changes in Core 0.3.8

This continuation adds Crescent Talons, companion orders and support behavior,
six-slot quick stow/equip, projectile fixes, and a complete Village Standards
runtime. The matching changed modules are Core 0.3.8, Rack 0.1.2, Field HUD
0.14.1, and Village Standards 0.3.0. These are source-built candidates; the
actual saved-world, matching graphical client, and hosted release gates remain
separate from the disposable native checks.

## Crescent Talons

`valeorders:crescent_talons` is the twenty-seventh original specialist weapon.
It has its own three-dimensional cuff-and-three-talon item model. Its native
crafting recipe uses an iron sword, a diamond and rabbit hide, through the same
owner-binding result-slot transaction as the other specialists. Specialist
actions require the issued weapon, level 8, and the matching issued Dusk set.

| Action | Control and behavior |
| --- | --- |
| Acquire a lock | Aim at a visible, permitted living target within five blocks and use the item. Survival middle-click is an additional input when gameplay owns the mouse. The server chooses the target. |
| Start a lunge | Use again or left-click the locked target within five seconds. The one-second windup has sound and particle cues. |
| Dodge the lunge | Move more than 1.2 blocks from the captured strike point before impact. The attacker also requires a clear, bounded movement path. |
| Cancel | Sneak + use. Changing or stowing the active weapon also invalidates its pending lock and lunge. |
| Rear attack | Rear lock applies five seconds of blindness through native potion permission checks. Rear melee gains 10% damage; a rear lunge deals 8.8 instead of 8 before native mitigation. |
| Bleed | Every five confirmed talon hits on one target adds a seventeen-second bleed stack, up to three. It ticks once a second while the same issued talons remain held and the target remains permitted and within range. |

A successful lock costs 18 resolve and records a five-second specialist
cooldown. Denied, occluded, out-of-range, or otherwise invalid acquisitions do
not spend resolve. Native use and middle-click both pass through the actual
server interaction manager and Forge right-click protection. The native item
cooldown does not suppress the second use needed to start an existing lock;
the persisted specialist cooldown still prevents reacquisition.

The middle-click request contains a version and dimension, with no client
target, damage or position. The server validates its current player/session,
held item, armor, target, timing, and protection state. Client routing yields
to HUD focus, open screens, active use, creative pick-block, and competing
middle-button bindings. Normal item use remains the configurable fallback.

Damage cancellation prevents confirmed-hit rewards and bleed buildup even if
vanilla already consumed absorption. Bleed and lunge damage continue through
the shared damage-protection gateway. Putting the weapon away stops its
pending actions and damage-over-time authority.

## Companion orders and support

An issued puppet or Ancestral Seal requires level 4 and 20 resolve to deploy.
Use it on a solid upper face with clear space. Use in the air to recall.
Recall can close an unloaded deployment without forcing its chunk to load;
the companion keeps its last recorded health.

Sneak + use while deployed cycles these saved orders:

| Order | Behavior |
| --- | --- |
| Assist | Follows and joins the owner's recent ten-second combat window. |
| Follow | Stays near the owner without initiating attacks. |
| Guard | Defends an anchored area against permitted nearby hostiles. It does not initiate PvP or attack ordinary livestock. |
| Hold | Returns to its anchor and does not initiate attacks. |

Sealed puppets can cycle orders before deployment. A sealed Ancestral Seal
uses sneak + use to select guard, scout or expedition medic. For combat roles,
left-click a permitted foe to issue a ten-second attack command; sneak-click
clears it. Explicit commands temporarily override movement orders. Thread
Command uses the same permission and ownership rules.

The medic is a support role and refuses attack orders. It selects the most
injured eligible owner or team member within twelve blocks, approaches, and
heals within three blocks with line of sight every two seconds. Guard and
Hold constrain its patient area to eight blocks around the anchor; Hold does
not pursue. The guard summon also shares its resistance support with nearby
visible eligible team members.

The world journal binds deployment authority to actor UUID, owner and
dimension. A recalled or replaced actor cannot resume its custom behavior
when its old chunk loads. Damage and entity saves update recorded health.
Logout, dimension changes and confirmed death close deployments and clear
temporary scroll state. A cancelled death does not trigger that new cleanup.
Destroyed companions can be repaired using four iron ingots spread across
inventory stacks; an insufficient total does not consume partial materials.

## Six-slot weapon rack

Focus a weapon slot in the HUD and use its action:

- A filled slot equips its weapon. If the hand already contains an accepted
  weapon, the two physical stacks swap, even when the main inventory is full.
- An empty slot stows the held accepted weapon directly. It needs no spare
  main-inventory cell.
- When the hand contains food or another nonweapon, quick equip moves that
  item into a free main-inventory cell. With no free cell it refuses the
  operation and preserves both items.

Damage, enchantments, display names and ownership serials travel with each
stack. A removed main-hand charge is cancelled without releasing the weapon.
Offhand shield use is preserved. Session nonce, rack revision, cursor state,
container state, ownership and server-thread checks precede every mutation.
Rack 0.1.2 requires the matching version on both sides.

## Projectile corrections

Piercing tethers sweep their entire movement segment and process contacts in
physical order, with a maximum of three accepted contacts. A protected body
stops the shot rather than allowing it to reach an enemy behind that body.
Returning sickles can strike on their return path without repeatedly damaging
the same entity. Removed held weapons invalidate their projectiles, emitters
and pending effects. Multi-target loops revalidate weapon authority between
contacts so a callback that stows a weapon stops later hits in the same tick.

## Village Standards

Village Standards 0.3.0 supplies the five known picture item identifiers with
an original hanging entity and client renderer. Each picture occupies a five
by five wall area. Placement checks all supporting faces, loaded/editable
positions, collision and overlap; survival consumption follows accepted spawn.
Save/reload and breaking retain item names and reward metadata.

Unrecognized custom records reaching the new entity remain visible and
breakable as unassigned panels. Their opaque data is kept in a recovery item
and a saved-world archive. Operator tools can inspect, explicitly assign, or
recover those records. The old binary's entity registration and custom NBT
schema remain unverified, so this is not a claim of automatic migration of
every historically placed picture. See the [module's runtime and recovery
contract](../modules/village-standards/README.md).

## Acceptance boundaries

The version-bound [candidate verification](../release/candidate038-verification.json)
records the final measured component and disposable native results. The native
fixture uses synthetic players and the real reobfuscated candidate mods; it
does not load `Naruto.test`, connect a graphical client, exercise the full
historical plugin roster, or connect to Bisect. Native movement checks do not
establish visual quality, pathfinding feel or multiplayer balance. The broader
reference ability audit is still historical and has not been recounted as a
completion percentage for these changes.
