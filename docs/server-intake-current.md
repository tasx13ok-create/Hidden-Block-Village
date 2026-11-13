# Full Bisect archive intake — November 13, 2026

The complete archive is now recovered and verified privately. **It has not been started, migrated or deployed.** The earlier failed strict extraction and original archives remain intact. This is a concrete recovery checkpoint, not production compatibility acceptance.

## Observed recovery

- Source: `D:/archive-2026-11-09T202203Z.zip`, 8,944,663,680 bytes.
- SHA-256: `bfdfbad54ead4cc0da9665cc413cc46285f7e36800cb01b90fcd216bfb8a9c3b`.
- New private output: `D:/HiddenBlockVillage-Sprint-20261113/server-intake-verified/`.
- 6,873 file entries, 8,943,018,418 expanded bytes. Every file passed ZIP CRC verification and independent disk SHA-256 readback. Source identity remained stable across the operation. Recovery took 178.250 seconds.
- File-map manifest SHA-256: `157738f691b1c3c3c0702f0e88381d60a477bc0c20d2f97b108c85cf616d6c0d`.
- Complete private mapping: `PRIVATE-SERVER-INTAKE.json`; static JAR/runtime identities: `PRIVATE-STATIC-INVENTORY.json`; local startup checks: `PRIVATE-STARTUP-CHECKS.json`. Keep all three outside Git.
- Public reviewed metadata: `release/evidence/server-intake-20261113.json`. No config contents, account credentials, player/NPC payloads or world data are published.
- `imported_map.zip` remains a single preserved nested archive, 2,777,371,551 bytes, SHA-256 `5854d0cfde492be547d83cb83fec62a653e7022fc66a771ca7df81a5efd453a7`. This recovery did not recursively extract it or establish which world revision it contains.

## Config collisions require an explicit decision

Both collision groups contain different bytes; neither is an equivalent duplicate. The extraction deliberately keeps the first payload in `server/` and every colliding payload in `quarantine/`. That placement is a preservation convention, **not a choice of the correct config**.

| Original ZIP member | Bytes | SHA-256 | Private relative destination |
| --- | ---: | --- | --- |
| `plugins/messages.yml` | 19,466 | `348342e345ad1ba02f23613820a865ce8194588eb3c0137164fe2cb08e1b45ba` | `server/plugins/messages.yml` |
| `plugins/Messages.yml` | 5,616 | `ae5d40b96a9eac0260f21d0dfb42f62fce6ce1629657350d52878bc24a3622bd` | `quarantine/006857/messages.yml` |
| `plugins/Locations.yml` | 14 | `cb190b977328167273fff4fe1d7c8d1a4c302ac8d584086c0421b57232057bc8` | `server/plugins/Locations.yml` |
| `plugins/locations.yml` | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | `quarantine/005752/Locations.yml` |

A case-sensitive Linux candidate can reconstruct both original spellings from the receipt. A Windows candidate requires a reviewed ownership/config-reference decision; selecting the larger file or concatenating YAML does not establish correctness. Preserve both original payloads and record every chosen mapping in the new candidate manifest.

## Actual runtime and roster

Static inspection read 20,096 class headers across 53 root/mod/plugin JARs without loading a class. No annotation or metadata parse warnings occurred.

| Input | Observed identity |
| --- | --- |
| Mod roster | Exactly 49 JARs under `mods/`; per-JAR SHA, IDs, declarations and dependencies are in the public receipt |
| Plugin roster | Exactly one archived plugin JAR: `CrazyCrates-1.10.2.2.jar`; plugin metadata name `CrazyCrates`, version `1.10.2.2`, API declaration `1.13` |
| Magma | Filename `Magma-1.12.2-8cc35ed-server.jar`; embedded implementation version **`1.12.2-b4c01d2`**; SHA-256 `2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce` |
| Magma entry point | `net.minecraftforge.fml.relauncher.ServerLaunchWrapper` |
| Separate Forge JAR | `forge-1.12.2-14.23.5.2859.jar`; SHA-256 `bd8314729d787e35164476d75afb337afb9783917e4f6fa1f77f049adba22f2d` |
| Root extra mod JAR | `stg-1.12.2-1.2.3.jar` is byte-identical to its `mods/` copy; SHA-256 `0ab0cb80822dd58ed83393edff83ac0497e1f12cbf4ceb9dacd963e2a949d2cb` |
| Archived Java | `CustomJAVA/release`: Java `1.8.0_252`, Linux `amd64`; `CustomJAVA/bin/java` is an ELF executable, not a Windows Java binary |
| Launch history | Archived `.minecraft-startup-history.json` contains timing telemetry, not a preserved launch command. No root `.bat`, `.cmd`, `.sh` or `.ps1` startup script is present |

Config folders are not proof their plugin implementations were archived. Only the one plugin JAR above is available in the top-level plugin roster; optional CrazyCrates integrations named in its metadata are not established as installed. Its API declaration also needs a real 1.12.2 Magma compatibility check rather than assuming the filename proves compatibility.

The root Magma and Forge launchers have declared manifest classpath targets absent from this archive. Forge is missing `minecraft_server.1.12.2.jar`. Magma is missing that file plus these ten targets:

```text
libraries/org/bukkit/bukkit/1.12.2-R0.1-SNAPSHOT/bukkit-1.12.2-R0.1-SNAPSHOT.jar
libraries/com/conversantmedia/disruptor/1.2.14/disruptor-1.2.14.jar
libraries/org/projectlombok/lombok/1.16.12/lombok-1.16.12.jar
libraries/com/mojang/authlib/1.5.25/authlib-1.5.25.jar
libraries/com/google/code/findbugs/jsr305/3.0.1/jsr305-3.0.1.jar
libraries/com/google/code/gson/gson/2.8.0/gson-2.8.0.jar
libraries/com/google/guava/guava/21.0/guava-21.0.jar
libraries/it/unimi/dsi/fastutil/7.1.0/fastutil-7.1.0.jar
libraries/io/netty/netty-all/4.1.9.Final/netty-all-4.1.9.Final.jar
libraries/me/tongfei/progressbar/0.9.3/progressbar-0.9.3.jar
```

The archive also includes exploded class directories. The absence of a declared classpath file is an observed packaging gap; static inspection does not prove which embedded/exploded definitions or runtime downloader would supply it. Resolve this explicitly before claiming reproducible startup. Do not substitute a different Magma revision under the existing filename.

## Client/server findings and JEI

`JustEnoughResources-1.12.2-0.9.2.60.jar`, `colorfulhealthbar-0.0.4a.jar` and `Controlling-3.0.12.4.jar` explicitly declare Forge `clientSideOnly=true`. Keep their archived payloads and include them in the client roster; dedicated-server omission should be recorded as a side-specific assembly decision. An absent side flag is not proof that every other JAR works on a dedicated server. Essential is a stage-zero tweak bootstrap with no top-level `@Mod` declaration in the scanned JAR, so its nested/downloaded runtime is outside this scan. Xaero and the other coremod/tweaker entries need a controlled classloading test.

The archived JEI identity is `e069347966155efd050fe18f060162a0406c194528c7c16974263c8879c3a8a2`. Its 398 normal Java 8 class headers coexist with a Java 21 header at **`java/mezz/jei/config/Tags.class`**. This extra `java/` resource path is not a multi-release module descriptor. The scan establishes its presence, not that the JVM loads it or that it causes the reported JEI problem. Compare this exact JAR against the already tested JEI repair, preserve both identities, and use the existing repair's exact client/network receipt before replacing anything. Raising the entire 1.12.2 runtime to Java 21 is not an evidenced fix. CrazyCrates' single Java 9 class is `META-INF/versions/9/module-info.class`, recorded separately rather than equated with an ordinary game class.

## Concrete next controlled acceptance

1. Freeze this recovered tree as an input. Create a **new** private candidate, for example `D:/HiddenBlockVillage-Sprint-20261113/magma-copied-world-candidate-01/`; do not boot `server-intake-verified/server` or the earlier world recovery. Record source archive hash, every copy/omission/replacement, runtime version and artifact SHA in a candidate manifest.
2. Choose the runtime OS. On Linux, restore both conflicting config spellings to their original paths using the mapping. On Windows, review which plugin/config owner consumes each spelling and resolve both groups explicitly before assembly. Select a known local Windows Java 8 runtime if testing on Windows; the archived ELF Java binary cannot serve that role.
3. Resolve the exact Magma classpath above from approved local/runtime inputs and verify their hashes/provenance. Retain the archived Magma's identity and entry point; document any intentional launcher replacement as a different test. The archived timing file cannot establish historical JVM memory flags or panel startup arguments; those remain missing inputs.
4. Assemble a baseline from the 49 preserved original mod identities. Preserve registry owners for existing `Naruto.test` content: especially NarutoMod and its addons, CustomNPCs/SpringAddon compatibility JAR, VariedCommodities, Armourer's Workshop, Fluidlogged API, and existing project content JARs. Record the three declared client-only JARs separately in the server/client assembly manifest. Review remaining tweakers/side assumptions against startup logs in the disposable candidate.
5. Treat the original-content baseline and a Vale-enhanced candidate as separate manifests. Add only the exact tested Vale artifacts from the current source checkpoint to the latter. Do not remove all legacy mods to claim a 49-mod replacement: registry IDs, blocks, entities, NPC data, recipes and saved capabilities have not been migrated. The currently implemented Vale slice is an enhancement scope, not a proven drop-in conversion of every archived namespace.
6. Copy the archived world and matching private configuration/data directories into the candidate after reviewing which `level-name` and relative plugin paths the local profile will use. Preserve CustomNPCs global/world data, scripts/dependencies, quest data, Armourer's Workshop library/assets and plugin data together. Fable's world-merge tooling must separately establish v17 schematic revisions and placement/rollback before applying them. A schematic paste alone does not migrate NPC/dialog/quest IDs.
7. Create reviewed local network overrides in the candidate: loopback bind, a non-production port, local access control, query/RCON disabled, and no archived network credentials. Keep original properties/configs untouched in recovery. Use an explicit candidate working directory, selected Java binary and exact Magma `-jar` target. This subtask did **not** execute that command.
8. First boot must be observed for loader/dependency failures, missing mappings, removed registries, CustomNPCs/SpringAddon linkage, Fluidlogged ASM/Magma hooks, plugin loading and world conversion. Save logs privately and stop cleanly. Abort destructive missing-mapping acceptance rather than letting the runtime discard saved content.
9. Boot the same disposable candidate again; connect a matching normal client using the exact repaired JEI/client manifest. Verify academy entry and first mission, combat and inventory, NPC/dialog availability, existing builds/water, dungeon entry/exit and return state, and a disconnect/reconnect/restart. Record version-bound evidence. Only this later phase can establish copied-world and Magma acceptance; publishing to Bisect is a separate step.

## Verification of this implementation

`python -m unittest discover -s tests -p test_server_intake.py -v`: **12 tests passed**. Fixtures cover path traversal/Windows aliases and reserved names, symlink/encryption/size limits, nonempty directory payload rejection, distinct case conflicts, exact duplicate equivalence with separate preservation, parent-file conflicts in both orders, parent spelling aliases, existing destination protection, bad CRC failure receipts, static Forge annotation parsing and public-summary redaction.

The final stricter preflight was also rerun against all 6,873 actual archive entries successfully after adding nonempty-directory/raw-name checks. The complete recovery contains no directory entries. No production server process, arbitrary bundled code, deployment, world mutation or shared Gradle build was run by this intake task.
