# Build Vale Core

The repository includes the standard Gradle Wrapper at its root. Use it with
`-p modules/vale-orders` to build Core. This command produces the Core mod and
its source JAR; it does not assemble a client/server pack or launch Minecraft.

For the dependent modules, checked output inventory, and continuous integration,
use the [complete source build instructions](build-modules.md).

## Required Java versions

| Purpose | Requirement |
|---|---|
| Gradle process | JDK 25 |
| Core compiler and component checks | Azul Zulu JDK 8 |
| Minecraft 1.12.2 runtime | Java 8, configured separately from the build shell |

RFG 2.0.3 itself requires Java 25. The recovered Core build explicitly selects
Java 8 from the AZUL vendor for its Minecraft code. Setting the build shell's
`JAVA_HOME` to Java 8 fails before the mod can compile.

The successful recovery build used Azul Zulu 25.38.109
(`25.0.5.0.3+1`) and Azul Zulu 8.98.0.111 (`1.8.0_512-b09`). Obtain a JDK
archive for the operating system and CPU architecture in use from
[Azul's official downloads](https://www.azul.com/downloads/), and verify that
archive against its vendor-provided checksum. A Linux archive checksum does
not apply to the corresponding Windows or macOS archive.

The module's Foojay resolver can provision the required Azul Java 8 toolchain
when it is not already detected. An explicit local-toolchain example is below
for machines that should not download a JDK automatically.

## Normal build

Run these commands from the repository root. Set `JAVA_HOME` to the actual
JDK 25 installation directory, containing `bin/java` or `bin/java.exe`.

### Windows PowerShell

```powershell
$env:JAVA_HOME = 'C:\path\to\zulu-jdk25'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat --version
.\gradlew.bat -p .\modules\vale-orders check reobfJar sourcesJar
```

### Linux or macOS shell

```sh
export JAVA_HOME='/path/to/zulu-jdk25'
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew --version
./gradlew -p modules/vale-orders check reobfJar sourcesJar
```

The wrapper reports Gradle 9.5.0. The build uses the recovered heap setting
of 3 GB and at most two Gradle workers. Its first run also resolves the
Minecraft, Forge, MCP, and plugin dependencies; later runs reuse Gradle's
local cache. Source and build dependencies need network access on the first
run. Keep any existing managed proxy and certificate settings intact.

## Explicit local Java 8 installation

For the pinned Gradle 9.5.0 build, these command-line properties select only
the supplied installations and disable automatic JDK downloads. Both paths
must identify JDK roots, not their `bin` directories.

PowerShell:

```powershell
$env:VALE_JAVA8_HOME = 'C:\path\to\zulu-jdk8'
.\gradlew.bat -p .\modules\vale-orders `
  "-Porg.gradle.java.installations.paths=$env:JAVA_HOME,$env:VALE_JAVA8_HOME" `
  -Porg.gradle.java.installations.auto-detect=false `
  -Porg.gradle.java.installations.auto-download=false `
  check reobfJar sourcesJar
```

Linux/macOS:

```sh
export VALE_JAVA8_HOME='/path/to/zulu-jdk8'
./gradlew -p modules/vale-orders \
  "-Porg.gradle.java.installations.paths=$JAVA_HOME,$VALE_JAVA8_HOME" \
  -Porg.gradle.java.installations.auto-detect=false \
  -Porg.gradle.java.installations.auto-download=false \
  check reobfJar sourcesJar
```

Replace the final three tasks with `javaToolchains` to inspect the JDKs
Gradle detects. It should list Azul Java 8 as a JDK. These `-P` toolchain
arguments are supported by the pinned Gradle 9.5.0; do not use them as a
reason to upgrade this legacy build to an unreviewed Gradle major version.

## Outputs and checks

Outputs are under `modules/vale-orders/build/libs/`. The version comes from
that module's `build.gradle`.

| File | Purpose |
|---|---|
| `vale-orders-<version>.jar` | Production reobfuscated mod from `reobfJar` |
| `vale-orders-<version>-dev.jar` | Development artifact; not the server/client distribution JAR |
| `vale-orders-<version>-sources.jar` | Main source/resource archive |

`check` runs the module's custom verification tasks. In the recovered 0.3.6
baseline, the standard `test` task has no sources; nine main-method test
classes and the ledger diagnostic provide ten JavaExec verification tasks.
The baseline build passed all ten tasks and reproduced this production JAR:

```text
vale-orders-0.3.6.jar
1,907,890 bytes
SHA-256 54879901cc9e5caed778ef3cf4ff7034d1951adad7dc7a94ee82466b78762acc
```

The recovered 0.3.6 startup diagnostic separately contains a stale item
total and stack-limit assertion. The component suite invokes its ledger
check, not its full registry check. A successful baseline build therefore
does not establish native startup acceptance. Candidate fixes and their
actual validation status belong in the release/recovery records.

Native checks must use a separate disposable instance and the exact
production JAR. They do not establish acceptance of the saved `Naruto.test`
world, a Magma/Bisect installation, or the full client pack. The build
commands above do not start those instances or modify world data.

## Wrapper integrity

The wrapper was generated by the official, SHA-verified Gradle 9.5.0
distribution in an empty temporary project. Its scripts were not rewritten.
`gradle/wrapper/gradle-wrapper.properties` pins:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
```

The generated `gradle-wrapper.jar` also matches Gradle's official published
checksum:

```text
497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7
```

The wrapper checks the distribution checksum when downloading it. Keep the
four wrapper files together: `gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`, and
`gradle/wrapper/gradle-wrapper.properties`.

Primary references:

- [Gradle 9.5.0 distribution checksum](https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256)
- [Gradle 9.5.0 wrapper checksum](https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar.sha256)
- [Gradle Wrapper checksum verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Gradle Java toolchains](https://docs.gradle.org/current/userguide/toolchains.html)
- [RFG 2.0.3 plugin Java requirement](https://github.com/GTNewHorizons/RetroFuturaGradle/blob/2.0.3/plugin/build.gradle.kts)
- [RFG 2.0.3 example wrapper](https://github.com/GTNewHorizons/RetroFuturaGradle/blob/2.0.3/example/gradle/wrapper/gradle-wrapper.properties)
