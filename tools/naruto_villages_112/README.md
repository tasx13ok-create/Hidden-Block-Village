# Naruto villages 1.12.2 compatibility work

This is an **unfinished adapter source checkpoint**, not an installable villages
mod or a claim that the requested port preserves every feature. The supplied
addon targets Minecraft 1.7.10 and requires a different base mod. The owner chose
the installed AHZNB NarutoMod as the 1.12.2 target.

Only original adapter and acceptance-fixture code is stored here. The supplied
addon, reference base mod, decompiled bodies, transformed addon bodies, SQLite
binaries and private test worlds remain outside this public repository.

## Exact input and target

- Input: `NarutoDestinationVillages-1.4.2.jar`, 284,189 bytes, SHA256
  `c4a5e4c96b597efe4b76a4d562307c4ae837fe299abe91a127d0169b8d3e8fe3`.
- Input metadata says Minecraft 1.7.10/version 1.4.2; its actual mod annotation
  says version 1.2.0 and requires `NarutoDestination@[1.24.0,)`.
- Target: Forge/Magma Minecraft 1.12.2, Java 8, stable-39 mappings,
  RetrofuturaGradle 2.0.3, existing `narutomod` and `variedcommodities`.
- Current private candidate version: `1.4.2-hbv112.4`. Modern FML mod ID is lowercase
  `narutodestinationvillages`; the original configuration directory is retained.

## What the original adapters do

`compat-src` implements the old nullable-stack API at the native nonnull
`ItemStack.EMPTY` boundary, inventory/container click protocols, command
signatures, sound/window/registry lookups, real SQLite-backed village/rank
identity, and an optional server-to-client identity mirror. Identity changes
remain server-authoritative. The additional affiliation command permits a first
village selection and requires an operator for subsequent transfers.

Chakra, ninja level, battle XP and Jinchuriki tails come from the installed
NarutoMod's actual public APIs. Old strength comparisons use native battle XP.
Several legacy-only character statistics still have no native equivalent and
are **not completed native integrations**. Do not interpret retained legacy
fields as a working NarutoMod clan system. The supported SP disciplines and JP
learning adaptation are described below; other old disciplines remain unfinished.

The original payment adapter accepts the five Varied Commodities coins with the
retained values 1/9/81/729/6561, plus installed Naruto Ryo notes at their face
values. It plans the entire deduction and change in copies before changing the
live inventory; insufficient funds or change space leave the inventory intact.
The currently tested private port also preserves carried items when opening a
GUI, fixes the original repeated sort-lock acquisition, and drops only remaining
loot after a partial inventory insert.

The XP token adapter retains denominations1/5/10/50, the original45rank bonus
defaults, integer rounding and sneak-to-consume behavior. Rewards grant actual
AHZNB battle XP, its native progression resource, through the public API. The
old base's chakra-experience curve is a different model; this is a documented
target-base adaptation, not a port of that base mod. The token is a server-owned
marked vanilla paper stack with its own name/lore. It needs no replacement
resource pack or client item registry. Unmarked paper and invalid token values
cannot redeem. The operator issuer is `/villagereward xp <1|5|10|50> [count]
[player]`; `/villagereward reload` reloads the new NativeRewards.cfg. The general
villages reload also reloads these settings.

SP, JP and reset credits have distinct persisted, spendable balances. Their
marked-paper tokens also support single and sneak whole-stack redemption.
`/shinobitraining` opens a server-owned menu: left-click spends one SP,
right-click spends ten; reset consumes one reset credit and refunds the four
supported disciplines. The village hub has a training button. Operator issuance
also accepts `sp`, `jp` and `reset` with the same denominations and count syntax.

The deliberate native training adaptation implements ninjutsu, taijutsu,
kenjutsu and speed, capped at500points each. A point adds1% corresponding damage;
speed adds0.5% movement per point, taijutsu adds0.5health per point. Maximum chakra
adds1per ninjutsu point and0.25per taijutsu point. An original, structurally
guarded ASM hook adds this at NarutoMod's two canonical capacity calculations;
the update hook changes the desired maximum before its cached-value comparison.
It does not maintain a competing chakra pool. Unsupported provider bytecode
leaves that provider unchanged and refuses ninjutsu allocation. Reset clears the
actual native chakra amount. Owned transient attribute modifiers are reapplied
on login/ticks and after native respawn without deleting unrelated modifiers.

JP learns a real technique on the player's held, owned native jutsu item. The
menu's learn button cycles techniques on right-click and learns on left-click.
It uses the public item/provider APIs and preserves native chakra, mode and
casting requirements. Rank costs are a target-game design: D/E1,C3,B5,A10,S20.
Learning enables the technique and supplies its required native knowledge XP;
this is not a reproduction of the old base's separate jutsu-learning model.
Duplicate learning does not charge. Ownership, held-stack identity and current
balance are checked before committing the changed stack and JP debit.

All 16 original default reward rows now have usable providers (adapters
0.1.3-draft). `NativeRandomizers` binds the five DNA/kekkei genkai/nature/clan/sage
rows to what NarutoMod 1.12.2 actually uses for those powers: owned jutsu items
with their affinity flag, owned dojutsu helmets, sage-typed senjutsu and the
matching NarutoMod advancements, mirroring its own `ProcedureKGDistribution`.
Token damage values keep the legacy forms (0 = random, which rolls a specific
token as before). Holding an owned copy refuses and keeps the token; a power
whose item was lost is restored without consuming the token or paying the bonus
again. Crystal/Steel release and Dragon sage have no native counterpart, so they
are never rolled and an old specific token re-rolls. DNA identification needs a
native ninja level instead of the legacy Intelligence stat. Outcome tables were
read from NarutoDestination 1.25.0 (1.24.0 is unpublished); bonuses and DNA odds
are in the `NativeRewards.cfg` sections `kekkei_genkai_token`, `nature_token`,
`clan_token` and `dna_identification`. Operators issue them with
`/villagereward <dna|kg|nature|clan|sage> <form> [count] [player]`. Details per
row are in `reward-semantics.json`. Installed original providers still take
precedence over these aliases.

`PvPCooldownStore` keeps the PvP anti-farm kill history across restarts. The
addon held it in memory only, so every restart reset all cooldowns. It is saved
per world as `narutovillages_pvp_cooldowns.json` on world save and once a minute
when changed; kill times are wall-clock milliseconds, so cooldowns resume exactly.

One addon class carries a private patch (kept with the private port, not here):
transport ignored the saved destination dimension and set facing without
syncing it. It now changes dimension through Forge's teleporter, syncs position
and facing, and refuses a destination whose dimension is unavailable before any
Ryo is taken.

## Build boundary

The adapter library can be compiled with the repository Gradle wrapper using
`-PnarutoModJar=<private installed NarutoMod JAR>` and, optionally,
`-PprebuiltMinecraft=<verified prepared stable-39 API>`. It has no mod entrypoint
and must **not** be uploaded alone to a server's mods folder. The private addon
port includes these adapter classes in its own reobfuscated JAR.

The native fixture in `tests-src` additionally requires the private port's
development API through `-PvillagesPortDevJar`. `compileNativeChecksJava` checks
that source against the APIs; it does not launch Minecraft or prove native
acceptance. Actual tests use a separately reobfuscated private fixture JAR in a
new flat-world Magma process, a guarded loopback port and official synthetic
non-OP players. No owner's world or player data is used. Version-specific
results and missing checks belong in the release receipt and current handoff.

`verifyTrainingHook` checks the actual private provider class and incompatible
synthetic mutations: two additive hooks, no replacement methods, idempotence,
unrelated-class pass-through, missing calculation and wrong-receiver refusal.
The matching0.1.2-draft build and native fixture compilation passed. The .3
native server run passed163assertions, including54nonnull slot checks, and a
separate real process restart passed15training persistence assertions. Actual
saved SP/JP/reset balances, learned technique ownership/knowledge, battle XP and
speed effects were restored without reissuing rewards. No network client or
graphical acceptance is implied. See the version-bound safe receipt at
`release/evidence/naruto-villages-112-training.json`.

## Remaining work before an intact port can be claimed

The 1.4.2-hbv112.4 native run (`release/evidence/naruto-villages-112-randomizers-restart.json`)
passed 45 assertions and a separate restart of the same world passed 8: all five
reward bindings and their refusals/restores, promotion authority and caps, rank
cap enforcement, cross-dimension transport with cooldown and missing-dimension
refusal, territory capture and cooldown, and restart persistence of PvP
cooldowns, clan, powers, ranks, territory owner and transport destinations.
It ran on a RetroFuturaGradle development server with admitted synthetic
players, not the owner's Magma deployment or a graphical client.

1. Verify a real network client, chest-window interaction and optional client
   mirror. A synthetic admitted player and discarded packets prove only their
   documented server-side scope.
2. Legacy-only genjutsu, medical and senjutsu-chakra statistics have no native
   provider and read 0. Do not invent a native binding for an unsupported field.
3. Run the exact JAR on a copied owner deployment (Magma/plugins) before
   production use.
4. Review redistribution permission before putting derivative addon source or
   its runtime JAR in the public repository. A successful build is not licensing
   evidence. The owner's local preview is not production acceptance.

The existing client art fix is independent: remove the obsolete
`Vale-Reference-Flat-Icons-v3.zip`; original mod artwork is supplied by the
original JARs. No replacement server resource pack is necessary.
