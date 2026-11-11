# Core 0.3.9 gameplay changes

Core 0.3.9 continues the [0.3.8 weapons, companions, rack and picture work](gameplay-038.md).
Its changes cover native scroll callbacks, protected movement and the original
ward armor's cosmetic state. The client and server must use the same exact Core
JAR. Runtime results and artifact identities belong to the
[version-bound verification record](../release/candidate039-verification.json).

## Scroll costs and native callbacks

A cast reserves its entire resolve cost before a potion, healing, entity-spawn
or teleport callback runs. An owner can have only one scroll cast in progress,
so a callback cannot recursively cast the same lesson or another owned lesson.
The guard is temporary runtime state and is released when the attempt finishes.

River Serpent's existing costs remain 16 resolve in water and 48 on dry land.
Practice costs 2 resolve in either case. The dry surcharge is now included in
the initial affordability check and reservation. A successful exact-cost cast
does not pay the surcharge a second time.

Denied potion applications, canceled healing, healing that changes no health,
and canceled spawns return the reservation. Refunds add back only that attempt's
cost to the account that was actually debited. They preserve independent
callback debits and do not top up a newly replaced player account. A successful
native effect stays charged even when a callback replaces its lesson state or
held item. Mastery is merged into the current learned lesson, and a later saved
deadline is retained.

Native item handlers return the stack currently held after callbacks. They do
not put a removed technique or codex back into the hand. Cooldowns refer to the
original item, and codex state is not mirrored into an unrelated replacement
stack. Disguise identity changes only after the disguise actor spawns.

## Protected movement

Waymark Step still marks safe ground on its first cast and returns to that mark
within 64 blocks on a later cast. Returning now posts `EnderTeleportEvent` and
checks the captured player, world, issued item, learned state, marker and
position after callbacks, including the final collision-query callback. Event retargets must have finite coordinates, remain
within range and height limits, fit inside the world border, avoid solid or
liquid collision, have solid support, and use already loaded destination
geometry.

A canceled or invalid return preserves the marker and refunds the reservation.
A connection that rejects movement does not consume the return. If the
connection actually moves the player and then redirects it, the cast remains
charged and preserves the final position chosen by that callback.
The same settlement applies when a connection moves and then throws: the
observed move stays charged, its captured marker is consumed, and its deadline
is committed. A throwing callback that makes no move is refunded. The original
exception still propagates, and the temporary cast guard is released.

Reed Escape's reactive movement uses the same Forge teleport-protection event
and retains its five-block reach. Its owner guard covers protection, clone
spawning and connection callbacks. The reaction rechecks the captured active
escape state and player position before moving. Rejected movement does not
cancel damage or consume the activation, and only that attempt's temporary
clone is removed. A completed relocation consumes the captured activation
once; independent callback changes are retained.

## Original ward armor presentation

Rendering can create a cosmetic state before the first server snapshot arrives.
That first snapshot now establishes a baseline instead of looking like a new
strike. A snapshot arriving after an expired tracking window also establishes
a new baseline. Duplicate snapshots and repeated render passes preserve the
existing animation time.

Wing interpolation now resets stale state after a respawn clock reset or long
absence. Its regression checks call the actual `approachWing` function used by
the renderer at several frame rates. Geometry checks cover all 895 existing
ward-model cuboids, including outward face winding. Generated meshes and raster
assets are unchanged.

These are the nine original Vale ward profiles. They do not contain the
separate `hvbijuu` mod's Matatabi or player-tail implementation. This Core
change does not claim a repair to that separate renderer.

## Acceptance boundaries

The component suite checks cost boundaries, state transitions, mesh geometry
and existing gameplay contracts. The private dedicated-server fixture checks
actual native item handlers, event cancellation, recursive callbacks, state
and held-item replacement, movement rejection, and the retained weapon,
companion, rack and Standards behaviors. Its synthetic players and marked
disposable world are separate from the saved server.

Read the exact production hashes, runtime identities and measured assertion
counts in the verification record. A source build or disposable server pass
does not establish the saved `Naruto.test` world's load/save/restart, a real
network client connection, GPU rendering, Solas behavior or Bisect acceptance.
Use the [matching client and server setup guide](client-and-bisect-039.md) only
after the fresh complete assembly has its own successful receipts.
