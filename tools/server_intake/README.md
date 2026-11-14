# Private server archive intake

This standard-library tool recovers an untrusted ZIP into a **new** private directory. It never executes Java, class files, plugins or scripts. Inputs and existing destinations are preserved.

```powershell
python -m tools.server_intake D:/server.zip D:/new-private-recovery
python -m tools.server_intake.inventory D:/new-private-recovery
python -m unittest discover -s tests -p test_server_intake.py -v
```

The first command checks every path before creating the output, hashes the same source handle used for extraction, verifies each ZIP CRC and independently reads each written file back for SHA-256 comparison. Windows reserved names, traversal, symlinks, special files, nonempty directory payloads, encryption and size/count limits are checked. A failed partial recovery is retained with a failure receipt; rerun into a new destination.

The first spelling of a portable path materializes under `server/`. Every later conflicting payload gets a unique archive-index path under `quarantine/`. Exact duplicates, case/Unicode aliases, file-versus-directory and ancestor-file conflicts have explicit private mappings. All payloads remain available even when their hashes match. Distinct config choices are never silently merged. A quarantined payload is not automatically part of a runnable server.

`PRIVATE-SERVER-INTAKE.json` is the complete private source-to-output map. `PRIVATE-STATIC-INVENTORY.json` inventories root/mod/plugin JARs, bounded metadata, Java class headers and Forge `@Mod` annotations without classloading. Both receipts can contain private paths; keep them outside Git. Nested archives are hashed/preserved as files and are not recursively executed or extracted. Internal JAR parse failures are reported, not reinterpreted as compatibility.

An optional `--public-report path.json` on the inventory command exports a restricted metadata summary: aggregate archive proof, anonymous collision payload identities, Java release/native formats, JAR identities and static declarations. Review all third-party JAR metadata before committing it. It excludes configs, player/NPC/world payloads, startup history contents and source/destination machine paths.

The intake result is **recovered, not started**. Resolve every required canonical choice in another private candidate; supply the intended OS/runtime and missing runtime dependencies; retain registry owners while testing copied saves. Perform first boot, clean stop, second boot, client join and migration acceptance separately. Never launch a recovered production world in place.
