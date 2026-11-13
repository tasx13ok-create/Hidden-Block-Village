# Combat hotbar: Rack 0.1.3 / Field HUD 0.15.0

The owner requested two retained loadouts: the normal nine-slot Minecraft hotbar and a separate six-slot combat hotbar. Holding F for 3,000 ms switches the entire active bar. Holding again restores the normal bar. This is implemented as real server inventory movement, so attacks and item use follow the normal held-item combat path.

Open the survival inventory to put items in the right-side Combat slots. Left click picks up, places, merges or swaps; right click takes half or places one. Dragging back into native inventory uses its existing drop handling after the server acknowledges pickup. Slot requests carry only slot/button/context/sequence, with nonce, revision, window and admission checks on the server. Snapshots cannot acknowledge an unrelated outstanding transaction.

The inactive normal bank retains all nine stacks, including NBT, damage and counts. The six combat stacks retain consumption/damage when parked again. Each bar remembers selection. Schema 1 storage migrates; schema 2 saves both bank state and selections; future data stays opaque/locked. Full inventories, extra items inserted into inactive native cells, death, clone, keepInventory and Vanishing receive conservation checks. Ordinary items, crafted garments/meals/tonics are accepted; reward-bound Core gear requires a valid owner receipt.

The client uses raw key state for the hold, with the configurable key context/modifiers applied to eligibility. Early release, menus, lost focus or active use cancel the gesture and require a release. Vanilla swap-hands is drained while F is the combat binding. Keys 1–6 and wheel selection stay within the six combat cells; a fallback bottom bar is drawn when the main Shinobi HUD is disabled.

## Ability correction and optimization

Changing from one hand mode to a different mode previously consumed the new ability's cost while merely removing the old mode. `ScrollEffects.mode` now replaces different modes and only toggles off when the same kind is requested. Native checks activate all six actual technique items from the combat hotbar, checking costs, modes, carriers and cooldowns.

HUD inventory copies now refresh only when a new server snapshot generation arrives. Internal rendering reuses the defensive cached copies instead of deep-copying NBT every frame/tick. This is a bounded optimization; no FPS benchmark or whole-pack performance claim is made.

## Evidence and remaining checks

See `release/evidence/combat-hotbar-013-verification.json` for the final isolated source/build/native identities. The candidate includes the concurrent Core 0.3.13 weapon/gate work; those other-worker files are not silently included in this independent source checkpoint. Thirteen pure hold-gesture checks cover the duration boundary, interruptions and one-toggle-per-press behavior. Native Forge checks cover inventory conservation, cursor operations, stale request rejection, ability integration and existing combat suites.

A separate 12-JAR client / nine-JAR server preview on loopback 25584 joined successfully. Its visible client showed the six-slot bottom bar and active Cinder Edge. The owner was interacting with the client, so automated input stopped. Physical three-second F timing and the complete graphical drag-out/drag-in flow are still manual acceptance items. Pure hold and native cursor checks do not substitute for that acceptance.

The live preview uses the earlier 2,391-assertion candidate. A subsequent final policy correction accepts freely traded crafted garments; the final verified artifacts are retained separately and were not substituted into the live session. Preserve the owner's client. Next, use the final artifacts in a new session or an owner-chosen restart, verify inventory drag in both directions, switch each way with F, exercise keys/wheel and restart with both bank states. Full original-pack, production-world and hosted acceptance remain separate.
