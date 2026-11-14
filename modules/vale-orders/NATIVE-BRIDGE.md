# Optional native-effect presentation bridge

This bridge keeps installed mods in charge of their jutsu, damage, chakra and XP. It observes a bounded set of accepted effects and can attach our existing hand poses or weapon presentation to an exact, reviewed registry ID. It neither casts a second jutsu nor charges a second cost. It does not replace original mod icons, armor, entities or resources.

The first configuration contains **zero active mappings**. Discovery and a matching name are not proof that a jutsu was cast, paid for, or owned by a player.

## Operator workflow

Permission level 2 is required for `/narutobridge`, including direct command execution and tab completion.

1. Run `/narutobridge discover`. `config/valeorders/bridge-registry.json` contains exactly `items` and `entities`, each a sorted array of namespaced ID strings. Optional mods may be absent. Installed Naruto namespaces and explicitly mapped namespaces are included.
2. Review discovery in `bridge-discovered.json`. Rows distinguish item-use **intent**, spawn observations, and accepted health/absorption outcomes. These observations never become active mappings automatically. Counts are bounded and an explicit dropped counter reports saturation. Details exclude player names, UUIDs and coordinates.
3. A proposal generator may produce `bridge-moves.proposed.json`. It is deliberately marked `verified:false`. Renaming that file to `bridge-moves.json` fails validation. Review each exact ID, evidence type, ownership contract and style before accepting it.
4. Use `/narutobridge map <style>` for the held item's accepted direct melee, `/narutobridge mapdamage <entity-id> <style>` for accepted damage with that immediate source, or `/narutobridge mapentity <entity-id> <style> <owner-policy>` for an admitted owned entity. These commands validate and atomically write the complete accepted file before publishing the new snapshot.
5. `/narutobridge reload` validates the entire file. An invalid file retains the previous active snapshot and stays read-only until corrected and reloaded. `/narutobridge status` shows mapping/discovery counts and the existing resource provider's read-only state. `/narutobridge test <style>` is a throttled **cosmetic admin preview**, not a native cast.

`unmap` removes the held item's direct-melee row. `unmap <kind> <when> <id>` removes one exact row. Missing optional registry IDs reject a reload; they do not silently attach to another ID or a different kind.

## Accepted schema

The top level has exactly `schema`, `verified` and `moves`. Every row has exactly the five fields shown here; duplicate and unknown fields are rejected.

```json
{
  "schema": 1,
  "verified": true,
  "moves": [
    {
      "kind": "entity",
      "id": "minecraft:arrow",
      "when": "spawn",
      "owner": "arrow",
      "style": "charge"
    }
  ]
}
```

This is an explanatory vanilla example, **not a default mapping or a verified Naruto jutsu mapping**. IDs are case-sensitive, ASCII namespaced strings, at most 128 characters total. The namespace is at most 64 characters and the path at most 96. Configuration is at most 128 KiB and 512 rows. Row identity includes kind, evidence type and ID; the same string in the item and entity registries remains distinct.

Allowed combinations are:

| Kind | Evidence | Owner policy |
| --- | --- | --- |
| `item` | `melee` | `direct_player` |
| `entity` | `damage` | `damage_source` |
| `entity` | `spawn` | `thrower`, `arrow`, `ownable`, `naruto_projectile`, or `naruto_flow` |

The first three spawn policies use public Minecraft ownership interfaces. The last two use only the fixed public `EntityScalableProjectile.Base.shootingEntity` field and `EntityChakraFlow.Base.getUser()` method when those optional classes and their exact expected types exist. There are no guessed NBT owners, configurable reflection calls or hard compile dependencies on a Naruto JAR.

Hand styles are `spiral`, `charge`, `fireedge`, `windedge`, `lava` and `lightmode`. `weapon:<key>` must match a current `ArsenalSpec.key`. `none` and `dodge` are explicit no-op styles; this bridge does not replay a paid dodge. Styles are at most 64 characters. `MoveVisuals.styles()` exposes the immutable accepted list for tooling.

## What a receipt means

An entity cue requires the final, uncanceled `EntityJoinWorldEvent`, the same entity UUID/body, actual native world membership, an unchanged proven owner, a current registered server player and settlement within two world ticks. It means **an owned effect was admitted**. It does not prove a chakra debit occurred, distinguish a loaded projectile from every possible new cast, or award XP/rewards.

A damage cue requires the same native Attack/Hurt/Damage chain and source objects, final uncanceled finite amounts, and observed health or absorption loss after the native call unwinds. A fully absorbed Magma contact can have final Damage amount zero and still qualify through actual absorption loss. Zero damage, canceled contacts, synthetic events without a real outcome, an unrelated later loss, a changed held stack and unattributed sources do not qualify. Native healing, a subsequent Attack boundary and the existing academy health-reset hook seal the earlier contact before it can inherit another outcome.

Callbacks retain the exact server, MP body, UUID/entity ID, connection, world/dimension, map snapshot and lifecycle epoch. Clone, respawn, logout, dimension change, unload and stop discard the appropriate pending work. Same-object respawn invalidates the epoch too. Unknown or unmapped effects remain unattributed.

Cosmetics retain that same lease before spawn, after callbacks and on each server tick. They never retarget to a replacement player by UUID. Hand cosmetics occupy a separate raw carrier state, are excluded from gameplay lookup and cannot replace an earned hand technique. Weapon cosmetics are tagged independently from earned pulses. Cleanup only closes those tagged cosmetic objects for the exact captured body. A canceled death alone is not a lifecycle transition. Neither carrier persists or replays on reload.

Accepted receipts are throttled to one owner cue every four world ticks and eight global cues per tick, with bounded pending work, owner history and deduplication storage. The current readout is the owner's vanilla actionbar. There is no invented native fee or cooldown, and this change does not edit the HUD/menu source or existing hardened resource telemetry.

## Deliberate scope and verification

This is accepted-effect presentation, not universal native-cast coverage. A jutsu that produces neither an attributable admitted entity nor a matching accepted damage chain needs a separately reviewed public observer. Arbitrary direct health writes outside these Forge events/reset hooks are not universally observable. Native boss contribution and rewards must use the Core's separate accepted-combat authority; this bridge makes no boss-credit claim.

Headless production-engine checks exercise strict parsing, atomic publication and failed reload preservation, proposal-renaming rejection, immutable mappings, typed discovery persistence and saturation, mutable final cancellation, absorption outcomes, callback context invalidation, deduplication, budgets and command permission guards. These checks compile with optional native mods absent.

`recoverychecks/.../BridgeNativeChecks.java` is a separate guarded native helper. It must run **last** in a disposable `vale-recovery-test` fixture with its marker, explicit recovery flag, zero genuine online players and only the supplied synthetic MP. It uses a new temporary configuration, actual Forge contacts/spawns and the actual carrier factories. It checks cancellation, absorption, lifecycle/callback replacement, serialization and earned-carrier isolation. It stops the bridge on completion; the disposable server should then shut down. Do not invoke it in an owner's world or normal inspection session.

Private compile artifacts are not installable mods. Full merged Core compilation, matching native fixture execution, optional Naruto owner-contract runtime probes and a real client pose/render/actionbar check remain separate gates. A passing pure receipt test or a source compile is not evidence that those endpoints passed.
