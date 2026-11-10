# Build the recovered source workspace

The source build assembles the recovered modules in dependency order and checks
the artifacts actually produced by their Gradle tasks. It does not start
Minecraft, modify `Naruto.test`, connect to Bisect, or produce an accepted full
server/client package.

Use JDK 25 to run the pinned Gradle 9.5.0 wrapper and an Azul JDK 8 to compile the
Minecraft components. See [Core build setup](build-core.md) for installation and
wrapper checksum details. Python 3.10 or newer is required by the orchestration
script; it uses only the Python standard library.

## Build all source areas

From the repository root on Linux or macOS:

```sh
export JAVA_HOME='/path/to/zulu-jdk25'
export VALE_JAVA8_HOME='/path/to/zulu-jdk8'
export PATH="$JAVA_HOME/bin:$PATH"
python tools/build_modules.py
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\zulu-jdk25'
$env:VALE_JAVA8_HOME = 'C:\path\to\zulu-jdk8'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
python .\tools\build_modules.py
```

The script builds Core first, reads the exact development artifact reported by
Gradle, and supplies it to Encounters, Rack, Residents, the world compatibility
module, and the server compatibility adapter. It similarly supplies the freshly
built Residents development artifact to the Residents server bridge. There are
no hard-coded historical Core filenames and no private compile-only shim JAR.

After the first Core build prepares the Minecraft development API, the other
Forge modules reuse that same API. Its complete SHA-256 is recorded in the build
report. Its classes are not included in the mod outputs. If a separately prepared
Forge 1.12.2 stable-39 development API is already available, select it explicitly:

```sh
python tools/build_modules.py --prebuilt-minecraft /absolute/path/to/recompiled_minecraft-1.12.2.jar
```

This is an optional build input, not a replacement for testing the production
mods against the intended Forge/Magma runtime.

To inspect the order or build a subset, including its dependencies:

```sh
python tools/build_modules.py --list
python tools/build_modules.py --modules vale-residents-server-bridge vale-encounters
```

An environment with an existing approved Gradle launcher can pass
`--gradle-launcher /absolute/path/to/launcher.py`. Arguments are passed as process
arguments without evaluating shell command text. The launcher remains
responsible for the environment's Java, proxy, and trust-store settings.
Additional wrapper arguments use `--gradle-arg=-Pname=value`.

## Artifact roles

| Source area | Built output and limit |
|---|---|
| `vale-orders` | Production Core mod, development API and source archive; component tests run through `check`. |
| `vale-encounters` | Production Encounters mod, development API and source archive, compiled against the current Core API. Historical native checks are separate evidence. |
| `vale-rack` | Production Rack 0.1.2 mod and development API, including six-slot quick stow/equip. Its native integration checks belong to Core's separate private recovery fixture. |
| `vale-residents` | Production Residents mod, development API and source archive; resident/dialogue component checks run. |
| `vale-residents-server-bridge` | Separate server-only adapter compiled against the current Residents API. |
| `vale-world-compat` | World compatibility mod and development/source artifacts. The newly implemented `CompatibilityChecks` verifies opaque NBT preservation, copy isolation, cooking progress, all legacy metadata values, and cleanup of restart state. It does not load the actual saved world. |
| `field-hud` | Client HUD mod and development/source artifacts. Actual rendering and input acceptance remain separate. |
| `field-controls` | Client Controls mod and development/source artifacts; the six recovered component checks run. |
| `vale-compatibility-helpers` | Three independently built adapters: client lifecycle, server damage compatibility, and server command bridge. They must remain separate JARs with their own side requirements. |
| `server-bootstrap` | Java 8 wrapper library and source archive. It contains neither Magma nor an embedded deployment plan and is not a runnable server distribution. |
| `village-standards` | Complete newly authored 0.3.1 runtime mod, development API and source archive. Component checks cover geometry, saved records, the verified historical entity ID and integer village format, and exact recovered-resource hashes. Actual saved-world and graphical acceptance remain separate. |

Each successful module writes `build/vale-build.json`. The orchestrator rejects
outputs outside that module, duplicate declarations, invalid archives,
non-Java-8 class files, bundled upstream/API classes, and known native test
classes. It hashes every selected output and checks the copied bytes again.
Stale metadata is removed before invoking the build, so a successful process
without fresh artifact declarations cannot reuse an earlier result.

The per-run report, logs, and checked output copies are under
`build/source-builds/<run-id>/`; `build/source-build-report.json` points to the
latest run. `source-inputs.json` pins the source, build scripts, wrapper, and
component test inputs. Generated files and isolated native fixtures are excluded;
their evidence belongs to their own run. A changed source input during the build
causes the aggregate result to fail rather than describing mixed inputs as one
verified snapshot. A failed dependency prevents its dependents from being built, while
independent modules are still checked. Any failed or skipped module causes a
nonzero final exit status. A `PASS` entry establishes the stated component build
only. The report always records that Minecraft and hosted acceptance did not run.

The source snapshot's digest is the SHA-256 of its `files` array serialized as
compact JSON with sorted object keys. It is not the byte hash of the enclosing
pretty-printed manifest. `git_commit` records the current parent commit; when
working changes are present, the per-file manifest identifies the built source.

## Direct companion builds

For a direct build outside the orchestrator, first build Core and supply the
actual development JAR reported in `modules/vale-orders/build/vale-build.json`:

```sh
./gradlew -p modules/vale-orders writeValeBuildMetadata
./gradlew -p modules/vale-encounters \
  -PvaleCoreDevJar=/absolute/path/to/current-core-dev.jar writeValeBuildMetadata
```

Rack, Residents, the world compatibility module, and the compatibility-helper
project use the same `valeCoreDevJar` property. Residents also accepts its old
`valeApiJar` argument for existing local scripts. The Residents server bridge
uses `residentApiJar` with the actual current Residents development JAR.
Missing inputs fail with a descriptive error when the compile classpath resolves;
read-only task discovery remains available before dependencies are built.

## Isolated native acceptance fixture

Core 0.3.8's recovery fixture is a separate mod under `recoverychecks/`. Its Rack
checks compile against the actual current Rack development JAR. First build the
production modules with the orchestrator, then use the development API path
reported in `modules/vale-rack/build/vale-build.json`:

```sh
./gradlew -p modules/vale-orders \
  -PvaleRackDevJar=/absolute/path/to/current-rack-development.jar \
  reobfRecoveryChecksJar
./gradlew -p modules/village-standards reobfStandardsNativeChecksJar
```

The `valeRackDevJar` input is required for this private Core fixture, not for
production Core. It is a compile input only; Rack classes are not copied into
the fixture or Core. The optional `prebuiltMinecraft` property described above
can also be supplied to these commands.

Each module writes its reobfuscated helper to its own
`build/private-native-checks/` directory. Use the helpers without the `-dev`
classifier when testing the reobfuscated runtime mods. The complete 0.3.8 server
fixture roster contains these nine production mods. The frozen 656-check result
used Standards 0.3.0; the current source builds 0.3.1. Its later focused
601-check native results are recorded separately and do not claim that the
whole Core suite was rerun.

| Production server mod | Candidate runtime file |
| --- | --- |
| Core | `vale-orders-0.3.8.jar` |
| Encounters | `vale-encounters-0.3.6.jar` |
| Rack | `Vale-Rack-0.1.2.jar` |
| Residents | `vale-residents-0.2.0.jar` |
| Residents server bridge | `vale-residents-server-bridge-0.1.0.jar` |
| World compatibility | `vale-world-compat-1.0.0.jar` |
| Forge damage compatibility | `Vale-Forge-Damage-Compat-0.1.0.jar` |
| Server bridge | `Vale-Server-Bridge-0.2.0.jar` |
| Village Standards | `Village-Standards-0.3.1.jar` |

Add both private QA mods to that disposable instance's `mods/` directory:

- `vale-recoverychecks-0.3.8.jar`
- `vale-standards-checks-0.3.1.jar`

Use the exact production bytes from the candidate component build. The fixture
records the loaded mod filenames, versions and SHA-256 digests; a matching name
alone does not establish that the tested bytes are the release candidate. Field
HUD, Controls, graphical client adapters and shader packs are not server fixture
dependencies.

Run the server with Java 8 in a separate disposable directory. Set these fixture
properties in its `server.properties`:

```properties
level-name=vale-recovery-test
spawn-monsters=true
spawn-animals=true
difficulty=1
```

The hostile and livestock fixtures require those spawn policies; Magma applies
them to scripted entity spawning as well. Create an empty file named
`.vale-recovery-test` in the server's working directory, and supply both JVM
properties before the server launch entry point:

```text
-Dvaleorders.recoverycheck=true
-Dvalestandards.nativecheck=true
```

The instance must have no real connected players. Core creates its synthetic
player after checking that condition; the Standards helper permits that caller
and rejects other connected players. The fixture modifies its disposable world
and synthetic inventories, writes `native-recovery038-result.json`, then shuts
the server down. Inspect the report's `passed` result and loaded artifact hashes
before citing a native pass. Compiling the helpers or completing a component
build does not establish that result.

Neither helper is included in a production JAR, source JAR, default component
metadata, or CI distribution artifact. They are not part of a normal server
installation. A disposable native pass does not establish saved-world migration,
matching graphical-client behavior, visual quality, or hosted acceptance. In
particular, Standards 0.3.1 handles the verified historical entity ID and
variant NBT, while actual saved records still require checks against a copy
of the real world. See the
[Standards runtime contract](../modules/village-standards/README.md).

For the focused Standards-only native run, retain the same nine production
mods, include only `vale-standards-checks-0.3.1.jar` as private QA, and use
`-Dvalestandards.nativecheck=true -Dvalestandards.nativecheck.standalone=true`.
The marker, disposable world name and absence of real players remain required.
This mode writes `vale-standards-native-result.json` and shuts down after the
single checked run. The exact PC Magma base used for the 0.3.1 evidence has
SHA-256 `2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`;
the same version filename from a fresh upstream download has different bytes.

## Continuous integration

`.github/workflows/source-checks.yml` defines two read-only jobs:

- The Python release and bootstrap regression suite, using an Azul Java 8 compiler.
- The source component builds, using JDK 25 plus Azul JDK 8, followed by archived
  build reports and only the checked project-owned outputs.

The workflow pins action commit identities, grants no repository write
permission, and does not publish a release or deploy a server. A component-build
artifact remains a candidate until the separate release gates are satisfied.
