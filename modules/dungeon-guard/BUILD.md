# Building Dungeon Guard

The server and the addon run on **Java 8, Minecraft 1.12.2, Forge 14.23.5.2859**.
The build tools use a separate Java 25 JDK. Java 25 is not the server runtime.

## Reproducible source build

Install a Java 25 JDK, a Java 8 JDK, and Python 3.8 or later, then run:

```text
python tools/build.py --java25 /path/to/jdk25 --java8 /path/to/jdk8
```

On Windows, use the actual JDK installation paths in quotes. The included
Gradle wrapper selects Gradle 9.5.0 and verifies its distribution SHA-256.
RetroFuturaGradle 2.0.3 prepares the Forge 1.12.2/14.23.5.2847 development API
with MCP stable-39 names, compiles the addon with the Java 8 compiler, and
remaps game references to SRG names for the runtime. Native validation runs
against the separate, exact Forge 14.23.5.2859 runtime.

Install only the production output:

```text
build/libs/Dungeon-Guard-1.12.2-1.1.1.jar
```

The development, sources, and private native-test JARs are not install files.
Check the actual distributable with:

```text
python tools/verify_artifact.py build/libs/Dungeon-Guard-1.12.2-1.1.1.jar
```

This checks the Java 8 class version, the coremod manifest and 1.1.1 metadata,
archive integrity, required area/HUD implementation classes, and that the addon
contains neither upstream game classes nor test classes. It also verifies the
embedded transparent PNG's format, dimensions, and exact original image hash.

## Private native validation

Build the private native tests:

```text
python tools/build.py --java25 /path/to/jdk25 --java8 /path/to/jdk8 --native-tests
```

Install the official Forge 1.12.2/14.23.5.2859 server into an empty temporary
directory with its installer. The following command uses that directory only
as a source of the official server JAR and libraries:

```text
python tools/run_native.py --server-template /path/to/empty-forge-install --java-home /path/to/jdk8 --mod build/libs/Dungeon-Guard-1.12.2-1.1.1.jar --tests build/private-native-tests/dungeon-guard-native-tests-1.1.1.jar
```

The runner verifies the Forge and Minecraft binary hashes and creates a new
disposable flat world under `build/native-runs/`. It writes the EULA acceptance
only in that disposable fixture. Networking binds only to `127.0.0.1` on an
ephemeral port, and online account authentication is disabled only there. It
never reads or writes a real server world. Each run saves `console.log`, the
native fixture result, and `runner-result.json`. A passing result requires a
zero server exit code, the explicit native pass marker, and a passing JSON
result; startup alone is insufficient.

The runner creates `DUNGEON_GUARD_DISPOSABLE_TEST_WORLD` and sets the JVM
property `dungeonguard.runNativeChecks=true` only for the private test process.
The fixture requires both gates before it can change a world or stop a server.

The private test mod must never be installed on a real server. The fixture
does not establish compatibility with every other coremod, the user's Magma
binary, or the complete saved world/modpack.

## Separate tests with the real Naruto and Waystones mods

Build this isolated fixture with:

```text
python tools/build.py --java25 /path/to/jdk25 --java8 /path/to/jdk8 --actual-mod-tests
```

It uses the original, externally supplied NarutoMod 0.3.1-beta and Waystones
4.1.0 JARs. The test verifies their SHA-256 identities before proceeding. Those
upstream binaries are not included in the source or installation package.

```text
python tools/run_native.py --server-template /path/to/empty-forge-install --java-home /path/to/jdk8 --mod build/libs/Dungeon-Guard-1.12.2-1.1.1.jar --tests build/private-native-tests/dungeon-guard-actual-mod-tests-1.1.1.jar --extra-mod /path/to/NarutoMod-1.12.2-0.3.1.beta.jar --extra-mod /path/to/Waystones_1.12.2-4.1.0.jar --result-name actual-mod-result.json --pass-marker DUNGEON_GUARD_ACTUAL_MOD_PASS --timeout 300
```

This is a separate run from the ordinary native fixture. **Do not combine the
two private fixtures:** the ordinary suite registers a minimal Waystones test
double, whereas this profile loads the actual Waystones mod. The actual-mod
profile contains only its own original harness, real-Naruto checks and
real-Waystones checks. It uses the same two disposable-world gates.

The focused Naruto checks invoke the real Kamui helper on protected blocks,
permitted arena terrain and protected tile entities, and distinguish emitted
item-stack counts from block removal. The Waystones checks exercise its real
block, item and tile implementation with Dungeon Guard's spawn binding and OP
construction policy. These targeted checks are narrower than an entire modpack
playthrough with a live client.

## Explicit checks against external release bytecode

The optional Gradle tasks `verifyNarutoRelease` and `verifyMagmaRelease` inspect
the supplied, pinned release classes without bundling their implementation
bodies. Run Gradle with Java 25 and provide the Java 8 toolchain as above:

```text
./gradlew -Porg.gradle.java.installations.paths=/path/to/jdk8 -PnarutoReferenceJar=/path/to/NarutoMod-1.12.2-0.3.1.beta.jar verifyNarutoRelease
./gradlew -Porg.gradle.java.installations.paths=/path/to/jdk8 -PmagmaReferenceJar=/path/to/Magma-1.12.2-b4c01d2-server.jar verifyMagmaRelease
```

Use `gradlew.bat` on Windows. Missing required external inputs are errors;
these tasks do not silently download a different release or count a skipped
positive test as a pass. Default `check` retains independent synthetic and
malformed-input checks, plus pure HUD placement checks.

The exact Magma base matching the recorded PC server has SHA-256
`2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`.
Its manifest identifies `b4c01d2`, although the recorded PC filename used
`8cc35ed`. The external bytecode check does not start Magma. A live 1.1.1 Magma
run could not start because required runtime dependencies were unavailable;
consult the version-specific receipt rather than inferring acceptance from
a matching filename or successful class transformation.

## Installation archive

After the exact production JAR passes its recorded checks and its original
source is committed, `tools/package_release.py` creates the installation ZIP
and standalone JAR. It requires a passing receipt for that exact JAR and a
full immutable commit URL. It packages setup notes, the original transparent
PNG and the acceptance receipt, and links to the committed source instead of
duplicating the repository. Dependency and private QA JARs are excluded.

## Upstream build references

- Forge downloads: <https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html>
- Official Forge Maven: <https://maven.minecraftforge.net/>
- RetroFuturaGradle: <https://github.com/GTNewHorizons/RetroFuturaGradle>
- Gradle wrapper verification: <https://docs.gradle.org/current/userguide/gradle_wrapper.html>
- Azul Java builds: <https://www.azul.com/downloads/>
