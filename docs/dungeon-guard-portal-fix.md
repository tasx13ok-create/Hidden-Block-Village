# Dungeon Guard 1.4.3: Magma portal travel

## Problem and cause

The owner reported that entering a Nether portal returned them to the trading
hub. The inspected uploaded Dungeon Guard 1.4.2 artifact is 1,023,346 bytes,
SHA256 `6f25789a73f6d392c1333fb2f57fbe6147596244cbed375c00af66cd772e6329`.
Its forced-spawn transformer treats every completed `PlayerList.recreatePlayerEntity`
call as a respawn: it overwrites the requested dimension with zero and then
teleports the player to the bound hub.

The project's recorded Magma file was named `8cc35ed`, but its inspected binary
identifies itself as `1.12.2-b4c01d2`. The correct pinned binary is 7,056,996 bytes,
SHA256 `2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`.
Both matching source and offline reconstructed release bytecode show that
ordinary Nether travel and Bukkit cross-world teleports call
`prepareRecreatePlayerEntity(destination, true)` followed by that same recreation
method. Magma also fires Forge's respawn event for these live transfers.

This is a concrete integration conflict that matches the report. It does not
depend on the portal landing inside a dungeon. It does not establish which
exact binary the live host is currently running.

## Change

The transformer recognizes the pinned Magma prepared-destination contract and
captures its `location` field in a local variable before Magma clears it. A
recreation with that prepared destination skips both forced-spawn hooks. A true
respawn without a prepared destination retains the existing hub override.

The third boolean argument alone is insufficient: Magma uses it for portal
transfers and End return. The fix keeps ordinary Forge behavior, preserves End
return without a prepared destination, and does not introduce a global or
thread-local protection bypass. Existing block protection remains active.

The capture is per invocation. Nested calls and exceptions cannot leave a
transfer flag behind. Expanded stack-map frames retain the new local, including
frames with two-slot long/double locals. Repeated transformation verifies the
exact existing guard; malformed or partial guard shapes are rejected.

## Artifact construction and evidence boundaries

`modules/dungeon-guard/tools/build_portal_hotfix.py` deliberately builds this
narrow patch from the exact inspected 1.4.2 JAR. It compiles the changed
transformer using an actual Java 8 JDK, replaces its class entries, and updates
the mod's shared version constant and metadata to 1.4.3. Every unrelated
implementation and resource entry is checked byte-for-byte against that input.
The original input is never overwritten. No Minecraft, Magma, ASM, private
test fixture, player data, or world file is bundled in the output.

The builder runs an old-bug reproduction against 1.4.2, then runs the focused
JVM regressions and pinned-Magma static checks against the assembled output JAR,
with JVM verification enabled. The JSON receipt records actual results and
exact artifact/source hashes. A receipt is written only after all gates pass.
The ordinary artifact checker also verifies Java 8 class versions, metadata,
archive integrity, original transparent banner, and absence of foreign/test
classes.

This is **not** a full Gradle rebuild, a full Minecraft/Magma startup test, a
graphical-client portal playthrough, or acceptance on the owner's Bisect world.
Synthetic JVM tests execute representative original test code; offline release
checks inspect the real reconstructed Magma implementation without starting it.

### Reproduction and build

External inputs stay private and are not committed. Provide the pinned 1.4.2
JAR above, official ASM 5.2 (SHA256
`7fbffbc1db3422e2101689fd88df8384b15817b52b9b2b267b9f6d2511dc198d`),
a Java 8 JDK, and the reconstructed/remapped pinned Magma class archive.

```text
python modules/dungeon-guard/tools/build_portal_hotfix.py --base-jar /path/to/Dungeon-Guard-1.12.2-1.4.2.jar --asm-jar /path/to/asm-all-5.2.jar --java8 /path/to/jdk8 --magma-classes /path/to/magma-patched-srg-player-classes.jar --output /path/to/Dungeon-Guard-1.12.2-1.4.3.jar
```

The external test pins the reconstructed `PlayerList.class` hash. The offline
recovery applies the published Magma binary patch to the official Minecraft
1.12.2 server after checking its SHA1
`886945bfb2b978778c3a0288fd7fab09d315b25f` and the patch's original-class Adler32.
Class names and direct member names use the pinned Magma JAR's embedded SRG
mapping. This is sufficient for the targeted transformation and frame analysis;
it is not represented as a complete recreation of FML's inheritance-aware
runtime remapping. Keep the extraction receipt with the private inputs.

## Installation and owner test

1. Stop the server and back up its world and current Dungeon Guard JAR.
2. Move the old Dungeon Guard JAR out of `mods`; install only the new 1.4.3 JAR.
3. Keep the complete existing `<active-world>/dungeon-guard/` folder unchanged.
4. Start the server. The normal bound trading hub may remain enabled.
5. Test Nether entry and return. Then test death respawn and ordinary Waystone
   travel. A dungeon still saving/restoring is a separate entry restriction.

No dungeon re-registration or new snapshot is required. The patch is on the
server; client-side HUD behavior is unchanged. For consistent installations,
replace the client copy too if the addon is present there. Keep one version in
each `mods` folder.

The earlier invalid/missing-hub login kick is a separate known issue and is not
changed by this portal patch. If the stored Waystone is invalid, stop the server,
back up the active world's `dungeon-guard/settings.json`, change only
`spawn.bound` to `false`, retain top-level `enabled=true`, and restart. Later
bind the correct intact Overworld hub with `/dg setspawn`. Merely setting the
old `bound` flag back to true does not repair saved coordinates.

The patch also does not claim to repair WorldEdit compatibility or add NPC
snapshot/respawn support. Those implementation bytes are preserved from the
uploaded base.

## Primary source references

- [Pinned Magma PlayerList patch](https://github.com/TonimatasDEV-Storage/MagmaMaintained-1.12.2/blob/48045966a3f0845f67be42faad1716cdcd0a4512/patches/minecraft/net/minecraft/server/management/PlayerList.java.patch)
- [Pinned Magma EntityPlayerMP patch](https://github.com/TonimatasDEV-Storage/MagmaMaintained-1.12.2/blob/48045966a3f0845f67be42faad1716cdcd0a4512/patches/minecraft/net/minecraft/entity/player/EntityPlayerMP.java.patch)
- [Pinned CraftPlayer cross-world teleport](https://github.com/TonimatasDEV-Storage/MagmaMaintained-1.12.2/blob/48045966a3f0845f67be42faad1716cdcd0a4512/src/main/java/org/bukkit/craftbukkit/v1_12_R1/entity/CraftPlayer.java)
- [Official Forge 1.12 EntityPlayerMP patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/patches/minecraft/net/minecraft/entity/player/EntityPlayerMP.java.patch)
