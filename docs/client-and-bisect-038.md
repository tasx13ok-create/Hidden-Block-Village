# Install the Vale 0.3.8 client and test the Bisect server

This guide covers the matching client and complete server produced by the
reviewed `vale-038-standards031-connected1` handoff. It provides the next steps
after assembly finishes. The current candidate's runtime acceptance is
`NOT_RUN`; actual saved-world, graphical-client and Bisect results remain to be
recorded. [Assembly contract](../tools/ASSEMBLY.md) ·
[Source inventory and evidence](connected-stage-038.md)

Use this guide for the 0.3.8 handoff. Retained `START-HERE.txt` and client
installation notes describe earlier stages; their old version and acceptance
statements do not describe the new candidate.

## 1. Use the completed output

The expected output directory is:

```text
D:\Codex\Vale-Export\vale-038-standards031-connected1
```

These names describe the requested outputs, not a claim that assembly has
already completed. Require the successful `PREPARATION.json` in the sibling
`vale-038-standards031-connected1-preparation` directory and `ASSEMBLY.json` in
the candidate directory, with both `INCOMPLETE.json` markers absent. The final
receipt records `CANDIDATE_PENDING_ACCEPTANCE`, the server ZIP's byte count and
SHA-256, and the complete preserved world identity. A ZIP that appears during
an unfinished run is still incomplete; keep it for inspection and do not upload
it. [Completion checks](../tools/ASSEMBLY.md#output-and-evidence-invalidation)

| Output | Use |
| --- | --- |
| `Client-Candidate.zip` | Import this matching profile into CurseForge. |
| `vale-038-standards031-connected1-server-candidate.zip` | Complete server candidate for an isolated installation test. Its entries start at the server root. |
| `server/` | Expanded server candidate; make a separate copy before runtime tests or hosting edits. |
| `server/client-install/Client.zip` | The same matching client bytes as `Client-Candidate.zip`. |
| `ASSEMBLY.json`, `candidate-inventory.json`, `client-inventory.json` | Package identity, complete file inventories and the pending runtime checks. |
| `prior-evidence/` | Preserved records from the earlier runtime, outside the new server ZIP. |

The GitHub repository contains source and release tools. Its source-download
ZIP is not one of these runtime packages. Keep the completed candidate,
original source stage and original Downloads archive unchanged while testing a
copy. Use the measured final client identity from the candidate's embedded
release plan; the old source client's hash identifies different bytes.

### Retained installation notes describe earlier stages

The assembler preserves these original text files. Their contents were read
from the source server and CRC-verified source client ZIP during review:

| Retained file | Historical scope |
| --- | --- |
| Server root `START-HERE.txt` | Claims a prior integrated local actual-world startup/restart and refers to `release/VERIFICATION.json`. |
| Client ZIP root `START-HERE.txt` | Describes an older Core 0.3.3 test upload, a fresh test world, separate runtime/shader inputs and an optional wing preview. |
| Client ZIP root `CLIENT-INSTALL.txt` | Names Core 0.3.6 with Encounters 0.3.6 and discusses that stage's artwork and item-counterpart coverage. |

Those notes do not establish acceptance for Core 0.3.8, Rack 0.1.2, HUD 0.14.1
or Standards 0.3.1. Use the exact contents and preserved `Naruto.test` workflow
below for this handoff. The current profile already carries the listed client
JARs and Solas files; no older test-upload instructions should replace this
inventory or select a new world for the recovered server.

The assembler moves the old `release/` directory into
`prior-evidence/server/release/`; the new runtime contains its own
`release/CANDIDATE-ASSEMBLY.json` with `runtime_acceptance: NOT_RUN`.

The retained note's `client-install/Client.zip` location remains usable in a
completed new server, because assembly replaces that nested ZIP with the
matching new client. Prefer the separately named `Client-Candidate.zip` when
giving players the new profile. [Evidence relocation](../tools/ASSEMBLY.md#output-and-evidence-invalidation)

## 2. What the client and server contain

The client selects **Minecraft 1.12.2 and Forge 14.23.5.2859**. The server uses
the provided **Magma 1.12.2-8cc35ed**, whose embedded Forge reports
14.23.5.2860, with **Java 8**. Preserve this reviewed pair and the exact supplied
JARs. The differing Forge build numbers are already recorded in the native
evidence; matching-client connection on the complete package still needs its
own result. [Runtime evidence](../release/candidate038-verification.json) ·
[Actual PC Magma and Standards evidence](../release/connected038-verification.json)

Client JARs are under `overrides/mods/` inside the profile ZIP and become
`mods/` inside the installed profile. Server mod JARs are under `mods/`.

| JAR filename | Client | Server | Purpose |
| --- | :---: | :---: | --- |
| `vale-orders-0.3.8.jar` | Yes | Yes | Core gameplay, weapons, progression and companions. |
| `vale-encounters-0.3.6.jar` | Yes | Yes | Encounter runtime. |
| `Vale-Rack-0.1.2.jar` | Yes | Yes | Six server-owned weapon storage slots. |
| `vale-residents-0.2.0.jar` | Yes | Yes | Residents and dialogue runtime. |
| `vale-world-compat-1.0.0.jar` | Yes | Yes | Reviewed world compatibility. |
| `Village-Standards-0.3.1.jar` | Yes | Yes | Five village pictures and historical entity compatibility. |
| `field-hud-0.14.1.jar` | Yes | No | HUD and rack interaction. |
| `field-controls-1.4.0-simple-candidate.jar` | Yes | No | Controls and settings screens. |
| `Vale-Client-Lifecycle-Compat-0.1.0.jar` | Yes | No | Client lifecycle compatibility. |
| `Essential_1-5-1_forge_1-12-2.jar` | Yes | No | Essential client integration. |
| `OptiFine_1.12.2_HD_U_G5.jar` | Yes | No | Client graphics and shader support. |
| `Vale-Forge-Damage-Compat-0.1.0.jar` | No | Yes | Server damage compatibility. |
| `Vale-Server-Bridge-0.2.0.jar` | No | Yes | Server integration. |
| `vale-residents-server-bridge-0.1.0.jar` | No | Yes | Residents server integration. |

This is **11 client mod JARs and 9 server mod JARs**. The six shared modules
must have identical whole JAR bytes and versions on both sides; assembly checks
that equality. The source/build manifests and connected contract establish the
roster; a similarly named download is not a substitute.
[Component build identities](../release/candidate038-component-builds.json) ·
[Standards 0.3.1 identity](../release/connected038-verification.json) ·
[Retained component identities](../release/component-lock.json)

The server also contains `plugins/CrazyCrates-1.10.2.2.jar` and the retained
CrazyCrates configuration, crate definitions, data and schematics. Its complete
`Naruto.test` save includes player data and dimensions: the reviewed input has
1,771 world files, 5,702,815,313 bytes, 1,294 overworld region files and 6 Nether
region files. The assembler compares every world file before and after copying.
[Measured source](connected-stage-038.md#measured-source)

The client retains `Solas-Shader-V3.9.zip`, its shader settings and the world
destination configuration. It contains no singleplayer world, separate resource
pack or preconfigured `servers.dat`; players add the server address themselves.
Gameplay changes and controls are described in [the current gameplay guide](gameplay-038.md).

## 3. Import the matching client

1. In CurseForge's Minecraft area, choose **Import**, then **Import Profile
   .zip** and **Choose .zip file**. Select the completed `Client-Candidate.zip`
   to create a new profile.
2. If the app flags bundled files, compare them with the reviewed JAR roster
   above and the candidate's file identities. For this reviewed custom package,
   use **All Files** after acknowledging the prompt. The manifest has
   `files: []` and carries its JARs in `overrides`; **CurseForge Files Only** can
   omit those custom files and leave the profile incomplete.
3. Confirm Minecraft **1.12.2**, Forge **14.23.5.2859** and all **11** expected
   mod JARs in the new profile. Retain the supplied versions together.
4. Launch the profile with a compatible Java 8 client runtime. Once the server
   is running, use Multiplayer to add its actual address and port.
5. Check **F8** opens the controls page, the HUD shows the rack, and the client
   returns normally from its settings screens. Inspect the intended Solas
   shader setting on the actual GPU and record the result separately from
   server startup.

The current import menu and bundled-file choices are documented by
[CurseForge: Sharing Modpacks/Custom Profiles](https://support.curseforge.com/support/solutions/articles/9000197912).
The F8 binding comes from the current
[Controls runtime](../modules/field-controls/src/main/java/village/controls/FieldControls.java).

## 4. Make a separate server test copy

Create a new folder outside the completed candidate and copy the complete
`server/` tree into it. For example, the new runtime directory could be:

```text
D:\Codex\Vale-Export\vale-038-host-test\server
```

Keep runtime logs, configuration diffs and new inventories outside the copied
server root when possible. Record the candidate ZIP hash and the pretest copy's
inventory before Minecraft changes world files. Use
[`release_pack.py audit`](../tools/RELEASE-TOOLS.md) for complete file identities.
Retain the full world and both identical launcher aliases; do not merge the
candidate into an unrelated active mod/plugin folder.

Use Java 8 for the server. `java -version` must report `1.8`; if it does not,
select an installed Java 8 executable explicitly. The retained `CustomJAVA`
directory contains a Linux runtime and does not supply a Windows `java.exe`.
The archive normalizes ordinary file permissions to 0644, so hosting should
use the panel's managed Java runtime to launch `server.jar`.

### First installation and host configuration

The observed source `server.properties` contains these values:

| Setting | Reviewed value | Deployment handling |
| --- | --- | --- |
| `level-name` | `Naruto.test` | Preserve this name and the complete matching world directory. |
| `online-mode` | `true` | Preserve the reviewed authentication setting. |
| `server-port` | `25565` | Compare with the actual Bisect allocation; edit only if required. |
| `server-ip` | Empty | Retain the blank bind setting unless the host explicitly requires a different value. |
| `enable-query` | `false` | Query is disabled. |
| `query.port` | `25565` | This is the query setting; it is not the player connection port. |

The observed file is 875 bytes, SHA-256
`c7dbd64e2a8656f39be95ab0bca687bc70e2a47239478409461a87768bea6d32`.
Preparation pins those exact original bytes. On the first bootstrap invocation,
every expected file must match its pin, including `server.properties` and the
nested client ZIP. Editing pinned configuration first causes a deliberate
`Release file is missing or changed` refusal.

Use this sequence for a hosting copy that needs configuration changes:

1. Before editing the copy, change into its server root and run Java 8:

   ```text
   java -jar server.jar --vale-upgrade-check-only
   ```

2. Require exit code 0 and retain the `VALE_DEPLOYMENT` installation message.
   This is a state-writing installation preflight. It checks the initial pins
   and active JAR roster, preserves any eligible retired files in its rollback
   area, and creates a genuine marker under `vale-update-state/`. It returns
   before starting Magma. The assembled candidate should already have no
   retired files in active locations, so zero retirement moves is normal.
3. Apply only the host settings actually needed on this copy. Record their
   exact diff, preserve `level-name=Naruto.test` and `online-mode=true`, and
   audit the resulting complete tree to a new inventory outside the runtime.
4. Compare the derived inventory with the completed candidate. Before any
   gameplay test, all world and runtime JAR bytes must still match. Expected
   differences are the genuine installation state, any documented retirement
   backups and the reviewed hosting configuration changes.
5. Keep the genuine marker with this complete deployment tree when uploading
   it. If making another ZIP, give that deployment ZIP its own filename, byte
   count and SHA-256. The original candidate receipt still identifies only the
   original candidate bytes.

After a successful installation, the bootstrap intentionally permits edits to
expected non-JAR files. It continues to verify all expected JARs and rejects
extra active JARs on every start. Its marker binds the release ID and embedded
plan hash; a changed plan cannot reuse it. A later check-only run therefore
checks the marker and runtime JARs but does not certify the edited configuration
or a Minecraft startup. Never manufacture or edit a marker to avoid the initial
checks. [Bootstrap implementation](../modules/server-bootstrap/src/main/java/village/deployment/ServerBootstrap.java) ·
[Behavior checks](../tests/test_bootstrap.py)

For a local saved-world test, launch the copied server normally from its own
root after the preflight/configuration steps:

```text
java -jar server.jar nogui
```

Record the world/client checks below and use `stop` for a clean shutdown.
Minecraft will modify the test world's files. Retain its pretest inventory and
logs; do not silently substitute a post-test world for the original candidate's
world. A separate fresh copy can supply the later hosted test.

## 5. Import the server into Bisect

Use a stopped test instance or retain a recoverable backup of the target's
existing files. Keep the complete candidate or derived deployment tree together.
The client ZIP is the player profile; the server ZIP contains the world, runtime,
plugins and libraries that the host needs.

1. In Starbase, open **Minecraft Tools → Modloader → Custom**. For Minecraft
   1.12.2, the current option is **Custom JAR (Non-NeoForge/Forge 1.17-1.20.1)**.
   Choose the existing-file option that preserves the required rollback and
   leaves a clean target for this package, then finish that setup.
2. Upload the completed server ZIP, or the separately identified deployment
   ZIP from the preceding section, through SFTP. Use the panel's **Files → SFTP
   Credentials** in the owner's SFTP client. Bisect requires SFTP for files
   larger than 500 MB.
3. In the Files tab, unarchive the uploaded ZIP. Check that `server.jar`,
   `server.properties`, `mods/`, `plugins/`, `libraries/` and `Naruto.test/` are
   directly in the server's working root. Do not add another outer `server/`
   directory around those entries.
4. Under **Settings → Startup**, set **Server Jar File** to `server.jar`.
   Select **Java 8** on the Home panel. Preserve the supplied exact Magma JAR
   and its libraries; the panel's generic Forge installation workflow does
   not require replacing this package's runtime.
5. Confirm the world name and actual network allocation. If the panel has
   changed `server.properties`, account for that change in the deployment
   inventory and first-install sequence above. Do not regenerate `Naruto.test`
   or replace it with a new default world.
6. Start the server, retain the fresh console log, and perform the tests below.
   Add the actual Bisect address to the newly imported client profile.

Official panel references, checked 10 November 2026:

- [Bisect: custom JAR setup and Java version selection](https://help.bisecthosting.com/hc/en-us/articles/40452990052763-How-to-Install-Forge-on-a-Minecraft-Server)
- [Bisect: SFTP setup and upload-size rule](https://help.bisecthosting.com/hc/en-us/articles/40110764299931-How-to-Configure-an-SFTP-Client-for-Minecraft)
- [Bisect: editing server settings](https://help.bisecthosting.com/hc/en-us/articles/40164769512219-How-to-Change-the-Server-Settings-on-a-Minecraft-Server)

## 6. Record actual test results

**Current candidate runtime acceptance: `NOT_RUN`.** Fill in these results only
from the completed candidate's actual tests.

Keep results tied to the exact pretest server payload, its matching client and
the configuration actually used. A bootstrap marker or a `Done` console line
alone does not establish world preservation, player persistence or client
compatibility. [Release evidence requirements](../tools/RELEASE-TOOLS.md#acceptance-evidence-contract)

| Check | Record from the actual run |
| --- | --- |
| Dedicated-server startup | Fresh runtime/version log, all 9 expected server mod JARs and CrazyCrates loading, completed startup and any failures. |
| Saved world | `Naruto.test` loads; inspect known builds and player state; resolve missing registry, entity or block reports before accepting a save. |
| Save and restart | Clean save/stop, successful restart and persistence of the inspected world/player state. |
| Matching client | The new 11-mod profile connects to the exact server and stays connected without missing-mod or network errors. |
| Rack and input | Six physical slots; stow, equip and swap; full-inventory behavior; reconnect persistence; F8 controls and settings navigation. |
| Gameplay and services | Bounded checks for Crescent Talons, companion orders/support, residents, crate services and the remaining Quartermaster/Spirit provisions requirements. |
| Pictures and visuals | Correct historical village pictures, item/armor presentation and an observed Solas result on the actual graphical client. |
| Bisect | Actual hosted start, save/stop/restart and matching-client connection, bound to the deployed copy's identity. |

Record failures and untested items explicitly. The earlier native suites use
disposable worlds and synthetic players. They support their documented scope;
they do not fill in these saved-world, graphical-client or hosted results.
[Gameplay acceptance boundaries](gameplay-038.md#acceptance-boundaries) ·
[Remaining release gates](recovery-status.md#remaining-release-gates)
