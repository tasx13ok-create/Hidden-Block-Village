# Dungeon Guard 1.1.1 compatibility notes

## Inspected dependencies

These dependencies are external inspection/test inputs. Their implementations, sources and artwork are not included in this project.

| Dependency | Inspected artifact SHA-256 | Evidence scope |
| --- | --- | --- |
| AHZNB Naruto 0.3.1-beta | `6cb62642e995b90f1cf1fc3eecd8b8b68361469460b681384f1fd16d69e378c0` | Actual release class inspection, external-byte transformer execution, optional real-mod native fixture |
| Waystones 4.1.0 | `5e79f329154d9a6225130f2b29dbcb0f603e2e622637e39ca566f8179b02ca15` | Actual block, item, tile, discovery and travel APIs in the isolated real-mod native fixture |
| Magma 1.12.2 b4c01d2 | `2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce` | Actual wrapper bytecode, official mapping/hierarchy inspection, independent JVM fixtures; no live Magma acceptance |
| WorldEdit Forge 6.1.10 | `cd4768ded9e2b527e3fc093fdf4ce07f058187de7961bdc0d7fa821c1b756f75` | Actual release and official source audit; no WorldEdit integration in 1.1.1 |
| WorldEdit Bukkit 6.1.9 | `5a7b88f6f75b4a0b6efd47ae03e9c26a2036ecd122b03a45ca635dde386d6186` | Actual release available; Bukkit classloader/integration design audit only |

Final acceptance counts and artifact identities are recorded separately in the release verification receipts.

The final real-mod run passed 60 assertions: 25 for Naruto's helper, 33 for
Waystones behavior and two exact binary identity checks. The first run retained
two failed travel/reset assertions and a subsequent fixture-completion failure
because its test player had not discovered the destination stone. The original fixture was corrected to register and check
discovery through the real Waystones API, then both original assertions passed.
Production code and the production JAR stayed unchanged during that correction.

## Kamui block helper

The public `ProcedureKamuiTeleportEntity.eBlock(World, BlockPos, int)` helper creates item entities and requests their dimension transfer before attempting terrain removal. Blocking only the final block write leaves those earlier side effects possible.

The new method-entry hook checks the source block before any of those effects. Protected outside blocks, locked regions and protected dungeon tile entities are left intact. In an active dungeon with terrain drops suppressed, the hook performs the permitted terrain removal without emitting or transferring block items. The original integer return value counts emitted item stacks: zero is therefore the correct result for this no-loot path. Disabled policies and worlds without an active guard runtime retain the original helper behavior.

A canonical SHA-256 digest verifies the known helper's complete instruction structure before injection. It normalizes MCP/SRG names and ignores debug metadata, while retaining calls, operands, locals and branch destinations. A changed implementation fails with a compatibility error instead of silently receiving a potentially incomplete patch. Positive transformer tests read the pinned external JAR at execution time; no reference method body is stored in the test sources.

A constant-pool scan of the inspected release found no in-JAR method references to this public helper. The acceptance fixture calls it directly. This establishes the helper's behavior and containment; it does not establish that a particular player hotkey in that release reaches it.

Primary release/source references: [original AHZNB release artifact](https://mediafilez.forgecdn.net/files/5967/126/NarutoMod-1.12.2-0.3.1.beta.jar), [author's 0.3.1-beta source branch](https://github.com/AHZNB/naruto_mod/tree/0.3.1-beta).

## Magma vanilla-command routing

Magma's `VanillaCommandWrapper.dispatchVanillaCommand` has two direct native command calls that bypass Forge's normal `CommandHandler` route. The optional adapter verifies both invocation signatures and operand layouts, then routes them through the existing `OperatorAccess.runCommand` helper. This preserves the actual native sender and arguments and applies bounded edit scopes and locked-region preflight before vanilla commands clear containers.

The inspected wrapper supplies a native player handle for a Bukkit player and the native server identity for the local console. The existing permission checks require a connected operator or that server identity. Command blocks, command minecarts, proxy/function wrappers and RCON senders do not receive an operator edit scope merely by claiming command permission.

Static inspection found no hardcoded Bukkit transformer exclusion in the inspected Magma launch classes. Startup also checks for the wrapper resource, loads it without initialization and requires the observed `Magma.vanillaCommands` hook. A present wrapper that did not pass through the expected transformer chain stops startup. Normal Forge without that wrapper skips the optional check.

The adapter was checked against the exact b4c01d2 wrapper using official mappings and native class hierarchy information. Live Magma startup remains unverified because required external dependencies could not be obtained. Wrapper equality across another Magma build alone would not establish whole-runtime compatibility.

## WorldEdit remains unsupported for live protected edits

WorldEdit does not inherit the bounded operator scope in this release. Its schematic paste, undo and redo paths need their own integration; a command-wide or timed global bypass would also expose unrelated ability writes.

The audit identified a concrete reason not to rely on the low-level block-write gates for WorldEdit: `BlockQuirkExtent` may clear a container before attempting the guarded block replacement. The final Forge 6.1.10 adapter can also apply tile NBT when the old and new block states are equal, even when a block-write hook returns no changed state. Live WorldEdit edits can therefore alter protected container data before a rejected terrain write. Prepare schematics in a staging save before enabling protection, or use the supported bounded native editing commands in the protected save.

A future bridge should use WorldEdit's `EditSessionEvent` extension at both `BEFORE_HISTORY` and `BEFORE_CHANGE`. It should reject unauthorized or locked targets before the delegate touches them and open a permission scope for exactly one verified world cell during the final delegated write, closing it in `finally`. The actual connected operator must be revalidated when queued writes flush. Bukkit-only WorldEdit additionally needs a bridge that handles its separate plugin classloader. Biome/entity mutations, asynchronous forks and operation atomicity require their own explicit contracts.

Primary sources: [official Forge 1.12.2 archive](https://github.com/EngineHub/WorldEdit/tree/forge-archive/1.12.2), [EditSessionEvent](https://github.com/EngineHub/WorldEdit/blob/forge-archive/1.12.2/worldedit-core/src/main/java/com/sk89q/worldedit/event/extent/EditSessionEvent.java), [BlockQuirkExtent](https://github.com/EngineHub/WorldEdit/blob/forge-archive/1.12.2/worldedit-core/src/main/java/com/sk89q/worldedit/extent/world/BlockQuirkExtent.java), [ForgeWorld](https://github.com/EngineHub/WorldEdit/blob/forge-archive/1.12.2/worldedit-forge/src/main/java/com/sk89q/worldedit/forge/ForgeWorld.java).
