# Naruto chakra and combat API: exact pinned observations

Read `START-HERE.md` first. This file adds exact ABI descriptors and combat identities; it does not implement or replace Claude's `ChakraResource` facade. The canonical default is **base NarutoMod only**. No frozen 0.3.15 source, live client or player state was changed. Raw third-party source, bytecode listings, artwork and JARs remain private. The adjacent JSON is safe metadata, not an acceptance receipt for a working bridge.

## Exact original identities

| Private local JAR | SHA256 |
| --- | --- |
| NarutoMod-1.12.2-0.3.1.beta.jar | `6cb62642e995b90f1cf1fc3eecd8b8b68361469460b681384f1fd16d69e378c0` |
| kabutoaddon 5.5.8-beta.jar | `c5c56886982f266fac68898b86b9261a34a13ea9818216d221e212f34c7b8b59` |
| BorutoMod Addon byAnimeSupremeTeam v1.0.jar | `633c49ebab5b815e02eb6fb39b3a0b0b17adc89d002968a3627c38738f9aca28` |
| SpringAddon7.0.jar | `49e08a36c9b5584a637743ac3cf2c53de00da1b3fb64e47bb51d67d0aba8760c` |
| ahznb weapons plus 1.2.1.jar | `e9dd2d94303301184b8f42a8827df48abf4a43da4d944d9fdb404b1814d08ea3` |

These match the supplied original pack, not a generic version-family promise. Spring's filename is 7.0; its embedded metadata may use a different version label. Bind the hash. Native and addon library classes must be loaded only when their provider is present; never add a hard optional-mod class literal to the common/server classloader path.

## Server chakra ABI

| Owner and member | JVM descriptor | Meaning |
| --- | --- | --- |
| `net.narutomod.Chakra.pathway` | `(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/narutomod/Chakra$PathwayPlayer;` | public static; Server accessor lazily creates and caches; not a read-only readiness probe. |
| `net.narutomod.Chakra.pathway` | `(Lnet/minecraft/entity/EntityLivingBase;)Lnet/narutomod/Chakra$Pathway;` | public static; Dispatches player overload, native NinjaMob pathway, otherwise separate living cache. |
| `net.narutomod.Chakra.isInitialized` | `(Lnet/minecraft/entity/player/EntityPlayer;)Z` | public static; CLIENT cache identity only; never server readiness. |
| `net.narutomod.PlayerTracker.isNinja` | `(Lnet/minecraft/entity/player/EntityPlayer;)Z` | public static; Naruto battle-XP identity >0; not Vale or vanilla level. |
| `net.narutomod.PlayerTracker.getBattleXp` | `(Lnet/minecraft/entity/player/EntityPlayer;)D` | public static; Native battle-XP read; max normally recalculates to this*0.5. |
| `net.narutomod.Chakra$Pathway.getAmount` | `()D` | public inherited; Absolute live amount. |
| `net.narutomod.Chakra$Pathway.getMax` | `()D` | public inherited; Live maximum; over-cap amount can exist. |
| `net.narutomod.Chakra$Pathway.consume` | `(D)Z` | public inherited; Absolute delta; calls double,bool with false; changed-state result. |
| `net.narutomod.Chakra$Pathway.consume` | `(DZ)Z` | public virtual; Player override; Absolute delta; bool permits over-cap result, NOT simulate; refuses candidate<=0. |
| `net.narutomod.Chakra$Pathway.consume` | `(F)V` | public inherited; FRACTION OF MAX, not absolute. |
| `net.narutomod.Chakra$Pathway.consume` | `(FZ)V` | public inherited; FRACTION OF MAX, not absolute. |
| `net.narutomod.Chakra$Pathway.clear` | `()V` | public inherited; Calls virtual set(0), reaches player persist/sync path. |
| `net.narutomod.Chakra$Pathway.set` | `(D)V` | protected; Not a public integration API; do not bypass access. |

`PathwayPlayer` overrides `consume(DZ)Z` and the protected `set(D)V`. Resolve inherited public methods with their exact parameter classes, especially `double.class`; the float overload multiplies by maximum. Neither boolean overload is a simulation/check-only operation. Positive double consumption requires a strictly positive resulting balance. Exact finite balance==cost therefore needs the public `clear()` route followed by a verified zero postcondition, rather than an epsilon debit or a private-field write. `consume(0)` can return false for a legitimate no-op.

The live amount and max are doubles. Native player max normally tracks `PlayerTracker.getBattleXp(player)*0.5`; do not copy a 0..100 resolve wire byte onto this pool. Vanilla XP level>=10 appears in upstream normal initialization hooks but is not the Naruto battle-XP identity check, and addons can change readiness. `Chakra.isInitialized` consults the client singleton and must never gate a server debit. Server `pathway(player)` creates/caches on demand, so it must not be called on a non-ninja merely to see whether a HUD should show.

Player mutation goes through the virtual setter: it updates **root** `player.getEntityData().double["ChakraPathwaySystem"]`, synchronizes the player and resets the native motionless-regeneration timer. That is not `PlayerPersisted/ValeOrders`. Editing only NBT leaves the cached in-memory amount stale. The native cache keys actual player entity bodies, not UUID accounts. Native clone hooks migrate/reset bodies; integrations must reacquire the current pathway after body replacement rather than caching one across reconnect/death/dimension events.

All new mutations belong on the actual server thread and current registered player body. Native maps/mutations provide no transaction lock or general check-only call. The facade must own its bounded per-player reentry/escrow guard before any external callback. Do not call a client ConsumeMessage handler as a server spending API. Existing HUD `ChakraBridge.current()` is a client-only observational snapshot and proves no server debit.

## Exact-zero, refund and failure boundaries

Use one selected provider per action. Naruto present but unsupported/unready must fail closed with a useful status; resolve fallback is only for genuinely absent Naruto. Reserve once, hold a receipt of provider/body/pathway/prebalance/actual debit, and commit once after irreversible accepted gameplay. Original Naruto item casts already own their charge/cooldown; observing their effects must never cast or charge them again.

Negative **double** consume is the observed public additive refund mechanism. The false over-cap policy can refuse to restore a legitimate preexisting over-cap balance: before110/max100, debit10 gives100, refund(-10,false) remains100. A bounded same-body/same-pathway refund may use true to restore only the recorded debit, never exceeding the original receipt/prebalance or inventing chakra. Preserve unrelated external spending/regeneration; never restore a whole old balance snapshot indiscriminately. Native amount>4*max has a lethal upstream overfill path, making unbounded refunds especially wrong.

Reject negative/client-chosen costs, NaN and infinities before calling the provider. Finite positive max/amount and verified postconditions are required. If reflection throws after possible mutation, inspect the actual same-pathway delta if possible; do not blindly retry, debit again, fall back to resolve, or mint a refund when the mutation is unknown. Record/refuse the failed transaction for controlled recovery. Cosmetic/VFX failure after accepted gameplay does not refund the paid action. Account/body replacement invalidates an outstanding receipt.

## Spring/addon collision evidence and limits

SpringAddon has a separately defined `net.mcreator.ahznbcursemarkaddon.Chakra` and `$Pathway`/`$PathwayPlayer`, with its own playerMap/livingMap/client singleton and private amount/max fields. The independent getAmount/getMax read those fields, not the base Naruto cache. Private observation confirms both the Spring and base player constructors/setters read/write the exact same root NBT string **ChakraPathwaySystem**. This is a concrete cache/NBT collision risk. It is **not** proof that both sets of listeners are active or unreconciled in the live patched pack; no such runtime/mixin test was run.

Never adopt the Spring cache as an alias, synchronize the two by overwriting balances, or debit/refund both. Base Naruto is the only default provider. Spring compatibility requires a separate explicitly tested policy. Metadata references show Kabuto/Boruto/Spring use base Naruto APIs in multiple classes, while Spring also references its own duplicate pool. Reference counts prove link dependencies, not runtime execution or coverage. The swords addon has no Chakra/Pathway method references in this scan.

Kabuto exposes `ProcedureSetChakraCommand.setChakra(EntityLivingBase,double):void`, descriptor `(Lnet/minecraft/entity/EntityLivingBase;D)V`. It is only a base-Naruto delta/consume wrapper and inherits the exact-zero restriction; it is not a safer transactional absolute setter. Do not invoke command procedures with arbitrary maps as bridge APIs.

## Jutsu damage and owner identity

| Public base Naruto member | JVM descriptor |
| --- | --- |
| `net.narutomod.item.ItemJutsu.causeJutsuDamage` | `(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/DamageSource;` |
| `net.narutomod.item.ItemJutsu.causeSenjutsuDamage` | `(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/DamageSource;` |
| `net.narutomod.item.ItemJutsu.isDamageSourceNinjutsu` | `(Lnet/minecraft/util/DamageSource;)Z` |
| `net.narutomod.item.ItemJutsu.isDamageSourceSenjutsu` | `(Lnet/minecraft/util/DamageSource;)Z` |
| `net.narutomod.item.ItemJutsu.getCurrentJutsu` | `(Lnet/minecraft/item/ItemStack;)Lnet/narutomod/item/ItemJutsu$JutsuEnum;` |

`causeJutsuDamage(immediateEntity, ownerEntity)` constructs an indirect native source when owner is non-null and a direct entity source otherwise. Its type is `ninjutsu`; the corresponding sage classifier compares `senjutsu`. These constructors do **not** automatically make every jutsu `isMagicDamage()` or `isProjectile()`. The public classifiers compare type strings, so they identify a category, not a trusted owner, faction, permission or successful contact.

For boss contribution, first use the final native DamageSource true source and accepted HP/absorption result. Require the actual registered server player, same world/run, permitted target, final uncanceled event references and bounded contribution/reward deduplication. Do not award from source labels, particles, class prefixes, entity spawn, displayed name, client messages or an owner's mere proximity. Untyped/world damage with no accountable owner stays unattributed. Any clone-to-owner credit requires an explicitly verified public owner accessor and run identity; do not guess NBT fields. Original PlayerTracker already has XP hooks; separate Vale merit from Naruto XP to avoid awarding the latter twice.

For Dodge/guard, distinguish tagged ninjutsu/senjutsu from native physical contact before adding a physical grace/counter. Chidori's carrier is not IProjectile; a universal projectile-only test misses it. Conversely, do not cancel or reflect every entity in an addon namespace. Read-only classification must not replace native damage or replay the original cast. Protection and final-cancellation outcomes remain authoritative.

## Verified representative entity registry IDs

These are string ResourceLocations observed in the original registration methods; do not persist or protocol-match the mod's numeric registration indices. Use runtime `EntityList.getKey(entity)` plus the pinned supported type/accessor contract. This is a representative list, not an exhaustive projectile registry.

| Registry ID | Registered runtime class |
| --- | --- |
| `narutomod:rasengan` | `net.narutomod.entity.EntityRasengan$EC` |
| `narutomod:rasenshuriken` | `net.narutomod.entity.EntityRasenshuriken$EC` |
| `narutomod:chidori` | `net.narutomod.entity.EntityChidori$EC` |
| `narutomod:chidori_spear` | `net.narutomod.entity.EntityChidori$Spear` |
| `narutomod:entitybulletkunai` | `net.narutomod.item.ItemKunai$EntityArrowCustom` |
| `narutomod:entitybulletshuriken` | `net.narutomod.item.ItemShuriken$EntityArrowCustom` |
| `narutomod:entitygunbai_chakraball` | `net.narutomod.item.ItemGunbai$EntityChakraball` |
| `narutomod:entitygunbai` | `net.narutomod.item.ItemGunbai$EntityCustom` |

Base `net.narutomod.entity.EntityScalableProjectile$Base` extends Entity, implements `net.minecraft.entity.IProjectile`, and exposes public `shootingEntity:Lnet/minecraft/entity/EntityLivingBase;`. Kabuto's `net.mcreator.kabutoaddon.entity.EntityKabutoScalableProjectile$Base` exposes the same public field descriptor on its independent base. Boruto and Spring each also ship independent scalable projectile bases, rather than subclasses of the base Naruto one. Verify those optional types separately; do not assume a single `instanceof` catches them all.

`EntityRasengan$EC` extends the base scalable projectile. `EntityChidori$EC` extends plain Entity, implements ItemJutsu.IJutsu, and exposes `getOwner()Lnet/minecraft/entity/EntityLivingBase;`. Kunai and shuriken inherit native Arrow/TippedArrow ownership fields. Read owners only; changing shootingEntity/motion does not establish reflection permission, collision safety or complete original jutsu state.

## Cast entrypoint warning and acceptance cases

`ItemJutsu$IJutsuCallback.createJutsu(ItemStack,EntityLivingBase,float):boolean` is public with descriptor `(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;F)Z`, but it is a callback, not a complete permission/cost/use pathway. The base item owns a **protected** `executeJutsu` with the same arguments, plus its own activation/cooldown machinery. `JutsuEnum` exposes public `index:I`, `chakraUsage:D` and `jutsu:Lnet/narutomod/item/ItemJutsu$IJutsuCallback;`; these are catalogue facts, not authority to bypass acquisition or call the callback directly. Preserve original native item-use flow unless an original public complete cast API is separately established/tested.

Required adversarial cases: absent provider versus present/wrong API; non-ninja/readiness without lazy mutation; positive/fractional/max-scaled cost units; zero cost; exact balance clear; insufficient/NaN/infinite values; over-cap debit/refund; callback reentry; throw after debit; native regen/other-cast spending before refund; body/connection/dimension replacement; native saved-player reload and client self sync; Spring duplicate cache active/inactive observations; original item cast observed once without another debit; ninjutsu versus senjutsu versus physical/fall/fire; final Hurt/Damage cancellation including absorption-only; native owner/run boss contribution once across Forge/CustomNPC buses. Synthetic fake providers alone do not establish actual full-mod compatibility.

Observation tools were javap signatures and private class-file/bytecode metadata inspection. No original implementation or artifact is reproduced here, no provider code was run in the live game, and no new bridge/runtime acceptance is claimed. Raw observation listings remain in ignored private space in the earlier worn-armor worktree; only this authored document and safe JSON are intended for Claude/GitHub.
