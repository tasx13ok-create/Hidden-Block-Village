# Vale Rack 0.1.2

Vale Rack provides six persistent weapon storage slots outside the player's
normal inventory. This source candidate accompanies Core 0.3.8 and Field HUD
0.14.1. Install `Vale-Rack-0.1.2.jar` on both the client and server: its network
handshake requires the matching Rack version. The public `RackClientApi`
signatures remain version 1; `requestEquip` now also handles an empty selection
by requesting that the server stow the held weapon.

## Quick equip and stow

In Field HUD, focus the weapon rail with its configurable focus binding
(default F), select a slot, then use its action. Ordinary use and the compatible
Controls cast binding route to that focused slot. Storage in the HUD settings
still opens the native six-slot rack container for direct inventory management.

| Selected rack slot | Held stack | Result |
| --- | --- | --- |
| Filled | Empty hand | Equip the stored weapon; the selected rack slot becomes empty. |
| Filled | Accepted weapon | Swap the two physical stacks, including when the main inventory is full. |
| Empty | Accepted weapon | Stow the held weapon directly; no spare main-inventory cell is needed. |
| Filled | Food or another nonweapon | Move the held stack into a free main-inventory cell, then equip the stored weapon. Refuse the operation if there is no free cell. |
| Empty | Empty hand or an ineligible stack | Refuse the operation and preserve the inventory. |

Damage, enchantments, display names and custom tags travel with each stack.
Core ownership serials are preserved. Server-side replacement of a charging
main-hand stack cancels that active use without releasing its charged action;
it does not reset an unrelated active offhand. The HUD requires active item use
to finish before sending a focused rack action.

## Ownership, storage and request rules

Core swords qualify only when the registered item is the exact object found
under the same key in `Gear.ITEMS` and the native item registry. The three
explicit Core throwable IDs retain the same identity check. Server admission
also requires `RewardLedger.valid` for the current owner. This includes issued
specialist swords such as Crescent Talons; a forged or unissued copy is rejected.
A crafted specialist must first complete Core's authoritative crafting-result
binding transaction.

Ordinary non-Core swords, axes, bows and shields retain their existing admission
policy. Core armor, food, scrolls and generic items remain ineligible for these slots.
Stack count and NBT-size limits remain enforced.

Every quick action validates the server thread, live player, session nonce,
world/dimension context, rack revision, container, cursor and applicable item
ownership before changing slots. The server performs the validated assignments
within one task, then publishes a new rack revision. Client snapshots remain
defensive copies and expire when their player, world or connection changes.

Storage schema 1, opaque saved-data handling, clone transfer, death drops,
`keepInventory` and Curse of Vanishing behavior retain their existing contracts.
The 0.1.2 changes add quick stow and active-use cancellation; they do not change
the saved slot layout or mint a second item during an equip operation.

## Build and acceptance

Build through the repository's [module orchestrator](../../docs/build-modules.md),
which supplies the newly built Core development API. For a direct module build,
use the actual development API path reported by Core's `build/vale-build.json`:

```sh
./gradlew -p modules/vale-rack \
  -PvaleCoreDevJar=/absolute/path/to/current-core-development.jar \
  writeValeBuildMetadata
```

The production mod and development JAR are declared in `build/vale-build.json`.
Core's separate `RackNativeChecks038` helper covers all six quick-stow cells,
full-inventory swaps and refusal paths, custom stack data, active-use handling,
ownership, session/revision replay rejection, serialization and death-drop
conservation. It is compiled into the private Core QA mod using
`-PvaleRackDevJar=/absolute/path/to/current-rack-development.jar`. Follow the
[complete disposable fixture setup](../../docs/build-modules.md#isolated-native-acceptance-fixture)
for its nine production server mods, both QA JARs and guarded world/flag setup.

The guarded Forge 14.23.5.2859 and Magma (Forge 14.23.5.2860) runs each passed
78 Rack assertions within the combined 656-assertion Core 0.3.8 fixture. Consult
the [version-bound candidate evidence](../../release/candidate038-verification.json)
for the measured artifact hashes and runtime identities. Saved-world
restart/respawn, the real client/server HUD and container workflow, and hosted
integration require their own acceptance. Earlier Core 0.3.5/Rack 0.1.1 or
frozen Rack 0.1.0 receipts do not establish acceptance for this pair.

`SOURCE-PROVENANCE.json`, `SOURCE-MANIFEST.json` and `REQUIRED-VERIFICATION.json`
retain the recovered candidate's historical record; their older version and
`NOT_RUN` fields are not current 0.1.2 result receipts. No compiled dependencies,
game binaries, private native test classes, launchers or worlds are packaged
into the production Rack JAR. The module's license remains MIT.
