# Vale Rack 0.1.3

Vale Rack provides a persistent six-slot combat hotbar alongside the normal nine-slot Minecraft hotbar. Install Rack 0.1.3 on both client and server, with Field HUD 0.15.0 for the inventory overlay and hold gesture. The Rack network handshake requires matching versions.

## Playing

Open the normal survival inventory and drag items into the six Combat slots on the right. Left click picks up, places, merges or exchanges a stack; right click takes half or places one. Drag back onto an inventory cell to remove an item. Weapons, owned scrolls/techniques, ordinary items, crafted meals and chakra tonics are supported. Core reward-bound equipment still requires its owner's valid receipt.

Close the inventory and hold the configurable combat-hotbar key (default F) for three seconds. The whole six-slot combat loadout becomes the active bottom hotbar and held items. Hold again to restore the original nine-slot normal loadout. Both selections are remembered. Keys 1–6 and the mouse wheel select combat items. Early release, a menu, lost gameplay focus or active item use cancels the hold; release before trying again. F takes precedence over vanilla swap-hands while assigned to combat; rebind swap-hands in Controls if needed.

The HUD settings storage button also opens the native six-slot container when the normal bar is active. The former public quick-equip/stow API remains available to older integrations, with its weapon-only policy; current HUD input uses whole-bar switching.

## Inventory and network behavior

There is one physical active inventory. During combat, Rack parks all nine normal stacks and moves the six combat stacks into native hotbar cells 0–5; cells 6–8 start empty. On return, changed/consumed combat stacks go back into Rack and the parked normal stacks return. No ownership serials or items are minted by switching. An item another mod inserts into cells 6–8 is relocated to a free main-inventory cell if needed; an insufficient-space return is refused before mutation.

The server validates player/context, nonce, revision, window, cursor and item admission. Cursor requests contain a slot/button and transaction sequence, never client-supplied item data. Full native inventory/cursor correction precedes the acknowledged Rack snapshot. Periodic snapshots cannot acknowledge an outstanding drag. Changing a held stack cancels its native charge without firing a stale attack; an unrelated offhand use remains intact.

Storage schema 2 parks the inactive normal bank and remembers selections. Schema 1 saves migrate without changing existing stacks; unknown schemas remain opaque and locked. Vanilla owns active combat inventory persistence/drops. Rack transfers or drops the parked bank once during clone/death, respecting keepInventory and Vanishing. Existing opaque item records stay preserved.

HUD snapshots remain defensive at the public API. The HUD refreshes copies only when the snapshot generation changes and uses those copies directly for rendering, removing repeated per-tick/per-frame deep NBT copies.

## Build and evidence

Use the [module orchestrator](../../docs/build-modules.md), which supplies Core's current development API. Private Forge checks exercise real item movement, reload, full-inventory refusal, cursor split/merge, replay rejection, clone/death conservation and casting all six hand techniques from the combat inventory. A separate pure Java check verifies the 3,000 ms hold boundary and interruption/release behavior.

Read [the combat hotbar handoff](../../docs/combat-hotbar-013.md) for actual results and remaining graphical acceptance. Earlier Rack 0.1.2/Core 0.3.8 receipts do not prove this version. Recovered provenance/manifest files retain historical scope; no game binaries, private QA runtime or world files are distributed by this source checkpoint. License: MIT.
