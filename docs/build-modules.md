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
| `vale-rack` | Production Rack mod and development API. No recovered automated tests are included in this source module. |
| `vale-residents` | Production Residents mod, development API and source archive; resident/dialogue component checks run. |
| `vale-residents-server-bridge` | Separate server-only adapter compiled against the current Residents API. |
| `vale-world-compat` | World compatibility mod and development/source artifacts. The newly implemented `CompatibilityChecks` verifies opaque NBT preservation, copy isolation, cooking progress, all legacy metadata values, and cleanup of restart state. It does not load the actual saved world. |
| `field-hud` | Client HUD mod and development/source artifacts. Actual rendering and input acceptance remain separate. |
| `field-controls` | Client Controls mod and development/source artifacts; the six recovered component checks run. |
| `vale-compatibility-helpers` | Three independently built adapters: client lifecycle, server damage compatibility, and server command bridge. They must remain separate JARs with their own side requirements. |
| `server-bootstrap` | Java 8 wrapper library and source archive. It contains neither Magma nor an embedded deployment plan and is not a runnable server distribution. |
| `village-standards` | Verified art/metadata overlay ZIP. The excluded renderer source is unavailable; this ZIP is not a complete mod and cannot replace the existing runtime JAR. |

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

Core's recovery fixture is a separate mod under `recoverychecks/`:

```sh
./gradlew -p modules/vale-orders reobfRecoveryChecksJar
```

The reobfuscated fixture is written to `build/private-native-checks/`. It is not
included in Core's production JAR, source JAR, default component metadata, or CI
distribution artifacts. The fixture requires its explicit test flag and isolated
world contract. Run it only in a disposable test instance with the exact Core
production JAR. Its success cannot substitute for the saved-world, matching
client, visual, or hosted tests.

## Continuous integration

`.github/workflows/source-checks.yml` defines two read-only jobs:

- The Python release and bootstrap regression suite, using an Azul Java 8 compiler.
- The source component builds, using JDK 25 plus Azul JDK 8, followed by archived
  build reports and only the checked project-owned outputs.

The workflow pins action commit identities, grants no repository write
permission, and does not publish a release or deploy a server. A component-build
artifact remains a candidate until the separate release gates are satisfied.
