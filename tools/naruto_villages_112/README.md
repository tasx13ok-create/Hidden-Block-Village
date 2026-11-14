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
- Current private candidate version: `1.4.2-hbv112.3`. Modern FML mod ID is lowercase
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

Eleven of the16original default reward rows now have usable providers: XP4,
SP4,JP2,reset1. The five DNA/KG/nature/clan/sage rows remain unfinished, with
their original identifiers preserved. Recovered provider meanings and remaining
binding requirements are recorded in `reward-semantics.json`. Installed original
providers take precedence over fallback token aliases.

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

1. Adapt remaining original PvP reward entries whose item providers do not exist in
   AHZNB NarutoMod, preserving meaningful reward semantics rather than silently
   dropping entries or replacing them with unrelated filler.
2. Resolve the legacy character-stat/clan/senjutsu model against actual installed
   providers. Do not invent a native binding for an unsupported field.
3. Verify rank-cap enforcement, promotion authority, full transport execution,
   territory capture and PvP cooldown persistence across restart. Version-specific
   native XP/death/respawn/drop results must come from the matching release receipt.
4. Verify a real network client, chest-window interaction and optional client
   mirror. A synthetic admitted player and discarded packets prove only their
   documented server-side scope.
5. Review redistribution permission before putting derivative addon source or
   its runtime JAR in the public repository. A successful build is not licensing
   evidence. The owner's local preview is not production acceptance.

The existing client art fix is independent: remove the obsolete
`Vale-Reference-Flat-Icons-v3.zip`; original mod artwork is supplied by the
original JARs. No replacement server resource pack is necessary.
