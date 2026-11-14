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
- Private port version: `1.4.2-hbv112.1`. Modern FML mod ID is lowercase
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
fields as a working NarutoMod clan or skill-point system.

The original payment adapter accepts the five Varied Commodities coins with the
retained values 1/9/81/729/6561, plus installed Naruto Ryo notes at their face
values. It plans the entire deduction and change in copies before changing the
live inventory; insufficient funds or change space leave the inventory intact.
The currently tested private port also preserves carried items when opening a
GUI, fixes the original repeated sort-lock acquisition, and drops only remaining
loot after a partial inventory insert.

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

## Remaining work before an intact port can be claimed

1. Adapt all original PvP reward entries whose item providers do not exist in
   AHZNB NarutoMod, preserving meaningful reward semantics rather than silently
   dropping entries or replacing them with unrelated filler.
2. Resolve the legacy character-stat/clan/senjutsu model against actual installed
   providers. Do not invent a native binding for an unsupported field.
3. Verify rank-cap enforcement, promotion authority, full transport execution,
   PvP event/drop/anti-farm behavior and territory capture, including restart.
4. Verify a real network client, chest-window interaction and optional client
   mirror. A synthetic admitted player and discarded packets prove only their
   documented server-side scope.
5. Review redistribution permission before putting derivative addon source or
   its runtime JAR in the public repository. A successful build is not licensing
   evidence. The owner's local preview is not production acceptance.

The existing client art fix is independent: remove the obsolete
`Vale-Reference-Flat-Icons-v3.zip`; original mod artwork is supplied by the
original JARs. No replacement server resource pack is necessary.
