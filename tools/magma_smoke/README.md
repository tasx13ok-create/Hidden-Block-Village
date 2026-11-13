# Disposable Magma bootstrap/restart

This tool stages a **new** private candidate from verified server intake, preserving archived JAR identities. It deliberately excludes recovered worlds, archived credentials/configs, global NPC scripts and exploded loose classes. Only mods explicitly declaring `clientSideOnly` are omitted automatically; every omission is recorded.

```powershell
python -m tools.magma_smoke stage --recovery D:/verified-recovery --candidate D:/new-flat-candidate --dependencies D:/reviewed-dependencies.json
python -m tools.magma_smoke boot --candidate D:/new-flat-candidate --java-home C:/approved-windows-java8 --label boot-01 --timeout 300
python -m tools.magma_smoke boot --candidate D:/new-flat-candidate --java-home C:/approved-windows-java8 --label boot-02-restart --timeout 300
python -m unittest discover -s tests -p test_magma_smoke.py -v
```

The dependency plan is a reviewed JSON array of `{ "target": "libraries/...jar", "source_path": "<approved local file>", "sha1": "<published pin>" }`, or `url` instead of `source_path`. Only HTTPS endpoints on the explicit official repository allowlist are accepted; final redirect hosts and size/hash are checked. Dependencies cannot overwrite archived payloads. The Minecraft server target `minecraft_server.1.12.2.jar` is also permitted. Stage receipts record SHA-256 and independent disk readback. Raw JARs are not public release artifacts.

Boot requires a disposable marker, exact archived Magma SHA, fresh-profile flat-world name, loopback `127.0.0.1:25579`, offline local mode, RCON/query off and a Windows PE Java 8 executable. JVM memory is fixed at `-Xms512M -Xmx2G`. Java starts without a visible process window. Console logs and receipts remain private. The server receives `list`, `save-all`, `stop` after `Done`; boot completion, saved chunks, shutdown, exit status and any forced termination are distinguished. It never launches archived startup scripts or Linux Java. Each run label is new so earlier logs survive.

The explicit `enhance` action instead creates another new candidate on loopback port **25580**. It accepts `--recovery`, `--candidate`, `--dependencies`, `--source-root`, `--build-report` and `--native-report`. Passing source/native receipts must bind the exact nine `run_native_checks.PRODUCTION` server components and the requested Core 0.3.11 hash. It retains the archived `hvdungeonpictures` JAR under `retained-originals/`, installs tested Village Standards as its sole active registry owner and pins `canonicalEquipmentCatalog=false` for legacy equipment coexistence. The private source/native reports are copied with verified hashes. Before each enhanced boot, the added artifacts, retained original and compatibility setting are verified. `boot` uses the reviewed profile's port; other bind/port changes are rejected.

This tests bootstrap and clean restart only. It does not establish normal-client gameplay, every addon hook, crafting recipes, restored server settings, NPC scripts, copied production worlds, historical loose-class patches or hosted deployment compatibility. See `docs/magma-smoke-current.md` for observed results and remaining defects.
