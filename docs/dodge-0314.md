# Four-way Dodge / Flash Step

Core 0.3.14 adds a server-authorized four-way flash step with the existing Minecraft movement and collision path. The default key is **V**. Hold **W** for forward, **S** for backward, **A** for left or **D** for right; pressing the key without directional input steps backward. The dominant direction wins when two movement directions are held.

The same registered action appears as **Dodge / Flash Step** in settings/controls. Rebinding changes that existing binding; it does not create a second shortcut. Menus, chat, lost focus and binding capture flush its queued presses and require release before another dodge. If another active legacy binding shares the key, the older action wins that press and a short notice directs the player to rebind Dodge. Existing player bindings are not silently overwritten.

## What the action changes

The server grants a low native velocity impulse, clips it against real collision boxes, and leaves ordinary native movement/velocity synchronization in place. It never writes a teleport position, changes the player's hitbox or resets fall distance. A complete swept bounding box must remain inside the actual world-border min/max bounds, with a small collision margin, and in loaded geometry. Minecraft's `WorldBorder.contains(AxisAlignedBB)` only tests overlap, so the implementation uses explicit full-bounds containment instead.

Default cost is **6 chakra** with **16 ticks (0.8 seconds)** of recovery. One airborne dodge is available until an actual collision floor is observed; a client `onGround` flag alone cannot restore it. Cooldown and air use survive player-data reloads. Spectating, sleeping, riding, elytra/flying modes, death and stale player bodies cannot grant a new action. Unknown future state schemas are preserved.

A brief six-tick window can evade nearby physical melee/projectile contact. It does not block fall, fire, magic or environmental damage. Failed/canceled protection callbacks restore reserved cost and recovery for the same saved account/state before an impulse commits. Other callbacks' movement or velocity are preserved. Forge integrations can cancel `Dodge.Pre` before the native impulse.

## Presentation and local response

The four directions use distinct flash-step leans and limb stances: arms-back forward dash, guarded backward step, and mirrored lateral steps. Accepted movement leaves bounded blue Minecraft-style cuboid silhouettes sampled from actual observed positions, plus native ground chips and a short movement sound. They are cosmetic, have no entity/hitbox or damage authority, stop at expiry and clear on world/connection changes. A teleport or nonfinite sample breaks the trail.

Whole-body presentation runs inside vanilla's existing body matrix after its render cancellation gate. It adds no unmatched render-event matrix push and never recursively renders player copies. Model angles are restored before the next vanilla pose. First-person camera rotation, shake and FOV are unchanged. Local visible villagers can acknowledge an accepted dodge, with LOS/range, saved per-witness cooldown and future-schema preservation; this does not globally alter reputation.

## Integration contract

The client action is `village.vale.client.DodgeClient.DODGE`, also returned by `binding()`. Its description is `key.valeorders.dodge`, category `key.categories.valeorders`, default LWJGL `Keyboard.KEY_V` (47), and conflict context `IN_GAME`. A settings adapter can locate it in `Minecraft.gameSettings.keyBindings` by description and use the ordinary key/modifier reset and options-save flow. `conflict()` returns an active conflicting key description for optional UI hints.

Server configuration is `movement.dodgeChakraCost` (default 6, allowed 0-25) and `movement.dodgeCooldownTicks` (default 16, allowed 8-80). The protocol is explicitly versioned and bounded; clients require Core 0.3.14 or newer within the supported 0.3 family before requesting it. Animation messages bind entity ID, player UUID, dimension, connection, sequence and accepted cooldown. Old-world or stale-player messages cannot animate a replacement entity.

## Verification state

The current headless/component suite passes **242 assertions** covering native directional math, physical-edge/GUI/conflict input rules, exact packet bounds, bounded trails, the real border-overlap counterexample and native renderer cancellation/matrix ordering. The disposable full native fixture passed 2,739 assertions, including actual four-way dodge travel, protected/canceled/reentrant callbacks, saved reload/death state, physical grace and local reactions. Its border test caught the original overlap preflight defect, which was corrected and regression-tested.

The corrected full source build passed all 11 areas and 36 artifacts without source drift. The dedicated native report is `build/native-checks/20261113T223745Z-2d842630/report.json` (SHA-256 `202a78a70330e6527519c55cbd14d1c370e950759d5e68793a3985f6ac90d568`), bound to source `2981ed8c5b3736bde53485f6bf83b49a3f2289af73167428121113df19890ae1`. The unloaded-neighbor fixture now stages the learner in an already-loaded source chunk; native Forge position setters load their own destination, so an unloaded-body teleport was an invalid test precondition. This file does not claim actual key rebinding in a graphical client, visual acceptance, full-pack compatibility or a deployed server. Those results belong to their separate version-bound receipts.
