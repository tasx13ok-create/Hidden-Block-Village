# Claude: start the shared Naruto chakra bridge now

Owner authorization: implement Naruto integration now; no further upload/permission question is needed. Public branding is Naruto, free/fan-made. Preserve internal registry/save/wire identifiers so existing worlds work. Permission/licensing status is separately pending; free distribution is not a rights grant.

## Exact source and ownership

Accepted source: GitHub main28b3e4df371c12379893e6c587c1a602dc9fc7ed, Core0.3.15, native3422. New integration checkout is the managed vale-campaigns/Hidden-Block-Village checkout on this PC, not the old original-checkout Core0.3.13 WIP. The current live015 session is owned by the player; do not replace its JARs, teleport, edit its state or take UI control.

Claude owns the NEW optional common chakra adapter and its focused tests only, proposed path `modules/vale-orders/src/main/java/village/vale/interop/ChakraResource.java`. No hard Naruto compile dependency; discover through the Forge mod classloader and public signatures. Coordinate the public method contract before changing callers. Codex owns integration of the existing spending/escrow callers, wire/HUD integration, mappings, combat events and encounter/reward adapters. Hotbar appearance remains Claude-owned. Do not wholesale restore older ValeOrders, proxy, HUD or weapon files over hardened current source.

## Inputs already present

Five exact owner-supplied JARs were copied into the private `local/claude-naruto-bridge-intake/20261113-pinned49-01/jars/` beneath the original shared checkout. This private intake includes hashes, metadata and README. They are the original NarutoMod0.3.1.beta, Kabuto5.5.8-beta, BorutoAST1.0, SpringAddon7.0/cursemark metadata5.0, and AHZNB swordsplus1.2.1. No public distribution permission is inferred; never add JARs, original code/art or raw private payloads to GitHub. Exact identity receipt: `release/evidence/naruto-bridge-five-jar-intake.json`.

If Claude Desktop lacks a connected local-files tool, the owner must attach the files; a filesystem path by itself does not grant the app file access. The API/registry fact documents can be read independently of loading the JARs.

## Critical observed public API semantics (specific to these hashes)

- `net.narutomod.Chakra.pathway(EntityPlayer)` returns `Chakra$PathwayPlayer`. The server call lazily creates/caches a pathway; it is not a read-only probe.
- `net.narutomod.PlayerTracker.isNinja(EntityPlayer):boolean` reads Naruto battle-XP identity. Do not substitute Vale/Shinobi level or vanilla XP for this field. The upstream normal automatic-init hook also gates on vanilla experienceLevel>=10; addons can alter initialization, so this is not a universal cross-addon readiness claim.
- `Chakra.isInitialized(EntityPlayer):boolean` checks the static CLIENT pathway. DO NOT use it as server readiness.
- Public inherited `getAmount():double`, `getMax():double` return live chakra values. Validate finite values and positive max; do not cap them to100.
- `consume(double):boolean` and `consume(double,boolean):boolean` use ABSOLUTE amounts. The boolean is over-cap policy, not simulation. The returned boolean reports whether amount changed; it is not a general dry-run/affordability API.
- `consume(float):void` consumes a FRACTION OF MAXIMUM. Never accidentally choose that overload for an absolute cost.
- The double consume call refuses a candidate amount<=0. Spending exactly the balance via consume returnsfalse and leaves it unchanged. Public `clear():void` calls the virtual setter and is the observed exact-zero route; verify the postcondition if using it.
- Negative DOUBLE consume is the public additive refund route. Native over-cap values can exist: a bounded same-body/same-pathway escrow refund may need the `true` over-cap option to restore the actual recorded debit. Never accept a client-supplied negative cost or credit more than the recorded debit. Preserve independent credits/spends; do not clamp a later legitimate balance to a stale pre-balance. Native excessive overfill can be punished by the original mod.
- Player pathways are cached by ENTITY BODY. Never retain one across clone, reconnect, dimension/body replacement or account reset.
- Native player mutation persists ROOT entityData double `ChakraPathwaySystem` and synchronizes the player. A direct NBT write alone does not update the cached pathway. Do not write that field as a substitute for the public API.
- SpringAddon contains a separate duplicated Chakra/Pathway cache. Do not spend/refund both backends, assume they are aliases or use one cache to overwrite the other. Backend selection and addon semantics must be explicit and tested.

These are signature/behavior observations, not copied implementation code or proof of runtime compatibility of a new adapter. See the forthcoming exact API facts table for method descriptors and pin identities.

## Adapter requirements

1. Only use resolve fallback when NarutoMod is genuinely absent. If installed but unsupported/unready, fail closed with a useful status; never silently grant a second cheap pool. Do not construct a non-ninja pathway merely to inspect a HUD.
2. All mutations run on the actual server thread and current registered player body. Hold a bounded per-player reentry guard around reserve/external callbacks/settlement; no client amount or ownership authority.
3. Give callers a transaction receipt with backend/body/pathway identity and actual debit. Commit accepted irreversible gameplay; refund only that receipt once on a canceled/rejected attempt. Additive refund must preserve unrelated external chakra changes. Cosmetic failure does not refund accepted actions.
4. Handle zero-cost without treating consume(0)'s false result as insufficient chakra. Reject NaN/infinite/negative/unsupported costs. Cost scaling must be explicit because old resolve0..100 and Naruto absolute chakra are different units.
5. Expose a double amount/max/provider/status view for HUD; never squeeze real Naruto values into the existing0..100 resolve wire byte. Keep old resolve save fields for the genuinely absent-mod path.
6. Cover absent mod, wrong public API, unready/non-ninja, body replacement, fractional overload trap, strict exact-zero/clear, rejected mutation, reentry, over-cap refund, cancellation and zero-cost cases. Run native absent-mod checks plus a separately bound full-mod runtime test; fake APIs alone do not establish real compatibility.

Do not make the old62 caller sites a blanket `PlayerState.setResolve` replacement: existing dodge, jutsu, hand modes, weapons, upkeep and refunds already have separate authority/settlement boundaries. Codex will integrate those after the adapter contract is agreed and tested.

## Other bridge parts

Mapping files must use exact hashed namespace/item/entity IDs, not display-name guesses. Cosmetic observation of a third-party cast never casts it again or charges chakra again. Keep original damage authoritative.

CustomNPC-specific events use `NpcAPI.Instance().events()` (custom EventBus), not just MinecraftForge.EVENT_BUS. Native LivingDamage/LivingDeath are Forge events. De-duplicate contributions/rewards across both event sources. Naruto's own PlayerTracker has existing damage/death XP hooks; do not award native battle XP twice. Itachi appearance copies and split-beast entities require actual encounter/run identity; their names do not prove reward eligibility.
