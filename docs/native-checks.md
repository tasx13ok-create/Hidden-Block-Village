# Disposable Forge native checks

`tools/run_native_checks.py` runs the current Core 0.3.9 recovery fixture and
the Standards 0.3.1 helper against the exact production mods from a successful
source component build. It creates its own temporary Forge installation and
synthetic world, validates the fixture's result, then removes that installation
on both success and failure. It accepts no existing server or world directory.

The regular source workflow runs this command immediately after
`tools/build_modules.py` succeeds. A component build and a native fixture result
remain separate records. Adding this job is not evidence that a particular
revision has already passed it; inspect that revision's completed CI run.

## Run after a current source build

Use JDK 25 for the pinned Gradle wrapper and an Azul JDK 8 for compilation and
the Forge runtime, as in [the source build instructions](build-modules.md):

```sh
export JAVA_HOME='/path/to/zulu-jdk25'
export VALE_JAVA8_HOME='/path/to/zulu-jdk8'
export PATH="$JAVA_HOME/bin:$PATH"
python tools/build_modules.py
python tools/run_native_checks.py
```

The native runner itself supports Python 3.8 or later and uses the standard
library. It also requires `curl` unless the exact reviewed installer is already
available. `--build-report` selects a different successful component report;
its source snapshot must still match the current checkout. The selected copied
artifacts, their original build outputs and prepared Minecraft development API
must retain their recorded hashes.

An existing official installer can be supplied without downloading another:

```sh
python tools/run_native_checks.py --installer /absolute/path/to/forge-1.12.2-14.23.5.2859-installer.jar
```

Both pinned installer hashes are still checked before execution. An environment
using an approved Gradle launcher may pass the same `--gradle-launcher` and
`--gradle-arg=-Pname=value` arguments used for its source build. `curl` uses the
inherited HTTP(S) proxy and TLS settings. The Java installer needs its own JVM
proxy/trust-store configuration where the environment requires it, for example
through approved inherited `JAVA_TOOL_OPTIONS`; `HTTP_PROXY` alone does not
configure Java. The runner changes no network policy and does not disable TLS
verification. Failed official downloads fail the check.

## What the runner binds

The production roster contains exactly these nine mods:

| Component | Forge mod ID |
| --- | --- |
| Core | `valeorders` |
| Encounters | `valeencounters` |
| Rack | `valerack` |
| Residents | `valeresidents` |
| Residents server bridge | `residentserverbridge` |
| World compatibility | `valeworldcompat` |
| Forge damage compatibility | `valedamagecompat` |
| Server bridge | `valeserverbridge` |
| Village Standards | `hvdungeonpictures` |

Runtime filenames, versions and SHA-256 identities come from the actual checked
component artifacts. The runner independently inspects their mod declarations
and archive contents. HUD, Controls and the client lifecycle adapter are not
added to the server roster.

The two private helpers are built by `reobfRecoveryChecksJar` and
`reobfStandardsNativeChecksJar`. A temporary Gradle init script supplies the
checked Core and Standards development JARs to their private compile and
reobfuscation classpaths. The Core fixture also receives the checked Rack
development JAR. This changes no production build definitions. The helpers must
contain Java 8 classes from their own private source directories; Core's new
`ScrollCastNativeChecks039` and `ReedEscapeNativeChecks039` classes are both
required. Private fixture source hashes and
the runner's own hash are recorded separately from the production source
snapshot, which intentionally excludes these private tests.

The official Forge installer is downloaded from
[Forge's Maven repository](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-installer.jar),
along with its `.sha1`. The installer command uses the official
[`--installServer` entry point](https://github.com/MinecraftForge/Installer/blob/2.0/src/main/java/net/minecraftforge/installer/SimpleInstaller.java).

| Pinned input | Identity |
| --- | --- |
| Installer byte count | `4681890` |
| Installer SHA-256 | `dbba1cd1bf34a4b82148bac50a6150f63ffcb2d5dc7d88a0508c27fdcff15b76` |
| Official installer SHA1 | `6b3d5be8b58d5385fda9c4d285367f4cb6e697b2` |
| Installed Forge universal SHA-256 | `bd8314729d787e35164476d75afb337afb9783917e4f6fa1f77f049adba22f2d` |

The runtime must be Minecraft 1.12.2 with Forge 14.23.5.2859. It contains the
nine production mods and exactly two private QA mods: `valerecoverychecks`
0.3.9 and `valestandardschecks` 0.3.1. The server binds to loopback with an
ephemeral port, RCON and query disabled, and no real players. Its flat world is
named `vale-recovery-test`, with the existing `.vale-recovery-test` marker and
both required JVM flags. The runner writes `eula=true` only inside this
authorized disposable fixture. Hostile and livestock spawning remain enabled
at difficulty 1 for the native tests.

## Passing conditions and limits

A pass requires all of the following to agree:

- The server process exits with code zero within its timeout.
- `native-recovery039-result.json` identifies `native-recovery-0.3.9`, reports
  `passed: true`, has positive assertion counts, and includes completion of the
  new scroll-cast and Reed Escape callback checks. Earlier 0.3.9 helpers that
  lack the Reed Escape class or completion label are rejected.
- Its Minecraft and Forge versions match the selected runtime.
- Every loaded mod ID, version, filename and SHA-256 equals the complete staged
  roster, including both private helpers; Core's separate recorded hash agrees.
- The console contains the current native success marker without a failure
  marker, and the original source, fixture inputs and staged mod bytes remain
  unchanged.
- The disposable runtime is removed successfully.

Each helper build is limited to five minutes; Forge installation to ten
minutes; and server execution to ten minutes by default. `--server-timeout`
accepts 30–1800 seconds for an explicit local run. Downloads and Java version
checks have shorter bounds. On POSIX systems each subprocess starts in its own
process group; the runner terminates that group on timeout/interruption and
also closes remaining descendants after the parent exits. On Windows it uses
`taskkill /T /F` while the parent is still running; it cannot guarantee cleanup
of descendants that outlive an already-exited parent. The native CI job runs on
Linux and uses the stronger process-group cleanup. The runner prints a bounded
status update during long subprocesses.

A native pass checks the implemented behaviors in a new synthetic Forge world.
It does not establish migration of `Naruto.test`, a Magma runtime result,
graphical rendering or animation quality, real network protocol behavior,
matching-client play, Bisect deployment, or release acceptance.

## Retained evidence and CI artifacts

The latest result is `build/native-check-report.json`. Each invocation also
retains `build/native-checks/<unique-run-id>/report.json`, subprocess logs and,
when produced, the fixture's original result JSON. Missing results, failed
fixtures, nonzero exits, altered inputs and cleanup failures leave a failed
report. Existing native reports are never used as current execution evidence.

The workflow uploads only those explicit JSON/log paths as its separate native
artifact. The original component artifact still contains only the checked
project-owned outputs from `build/source-builds/`. Upstream runtime JARs,
downloaded libraries, private QA JARs, the temporary Gradle script, and generated
world files are excluded from both artifacts. The QA build outputs remain in
their existing ignored `build/private-native-checks/` directories locally;
they are never production installation files.
