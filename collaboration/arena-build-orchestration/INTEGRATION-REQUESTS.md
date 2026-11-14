# Arena integration requests

Arena branch: `codex/five-new-naruto-coliseums`, based on main8681bb9. Ownership
stays within the arena paths in the execution brief. The shared Core/HUD/Rack,
Encounters136-profile registry, original worlds, native distributions and global
Biju/vessel state are unchanged.

## Accepted combat contribution observer

The arena controller must credit actual accepted damage/protection/healing from
the correct run, participant and body. Forge LivingDamageEvent alone precedes
native HP/absorption settlement on this Magma runtime. The original prototype's
tick-later HP comparison was removed because another hit could inherit credit.
The current controller can credit validated physical objectives, which does not
accept damage/support contribution coverage.

Requested reviewed Core extension: a bounded read-only accepted-outcome callback
with immutable participant/session, true/immediate source, target UUID/body,
dimension and source-event identities; actual HP/absorption loss; final cancellation;
and an after-unwind sealed receipt. It must seal zero loss before another hit,
heal or health reset, reject stale/replaced bodies and deduplicate nested/cross-bus
observations. It must never replay a cast, spend chakra, award XP or alter global
native ownership. Core's existing owned `FocusTransactions` receipt logic is a
useful reference, but currently has no general listener API. This request does
not authorize the arena worker to edit that protected module.

Required acceptance includes absorbed-only accepted contact, final cancellation,
zero then unrelated loss, later healing/reset, same-body respawn, replacement UUID,
summon-owner attribution and multiple same-tick contacts. Until observed, the
arena branch will explicitly label that contribution scope pending.

## Native repeatable boss terminal contract

All five exact candidate IDs now resolve and actually spawn in the corrected
fresh Magma diagnostic. Gaara's prior rejection disappeared with
`spawn-monsters=true` while `doMobSpawning=false`; no global manager mutation was
needed. Failed diagnostic copies are retained.

The native terminal/revival tests remain separate from that spawn result. An
initial lethal probe showed rejected damage and lingering zero-HP bodies, and its
200server-tick observation had an invalid unloaded-body precondition. That probe
is preserved as failed evidence; a new loaded-neighborhood test is required.

Requested integration surface if the native mods do not expose one: a reviewed,
per-body/per-run terminal or sealing adapter for these exact classes/hashes,
including revival/replacement identity, summoned actors, native XP/drops and clean
second-run removal. An uncanceled death event, missing UUID, setNoAI or elapsed
timer is insufficient by itself. Never reset the global Biju/vessel manager or
repurpose imported NPC identities to manufacture repeatable copies.

Each arena's normal economy must remain closed while its specific terminal
contract is unproven. Preview carries no first-clear, rank or earned economy.
The separate bounded vanilla reward outbox has its own real inventory/save tests;
those tests do not certify the entitlement of an actual boss clear.
