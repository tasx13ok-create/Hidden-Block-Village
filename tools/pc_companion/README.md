# Vale Native Companion 0.1.0

This is a locally started Windows companion for the Vale File Link page. It gives the connected session a small set of file and Minecraft window operations. It is Python 3.8+ code using the standard library. It does not require an OpenAI API key, spend API credits, install a Windows service, open a public port, or install Minecraft.

**Status:** the HTTP/file coordinator and desktop guard logic have automated tests using temporary folders and a fake Windows adapter. Actual Windows capture/input, the 11-mod Forge client, the preserved `Naruto.test` world, and the Bisect import still require runtime acceptance. Successful source tests do not establish those outcomes.

## Start on the PC

1. Extract the complete companion directory to a new output folder. Keep `vale_companion.py`, `vale_windows.py`, and `START_VALE_COMPANION.cmd` together.
2. Double-click `START_VALE_COMPANION.cmd` in your normal, unlocked Windows desktop session. It uses an existing Python 3 installation. No administrator launch is needed. The console remains visible.
3. Add up to eight distinct folders you want this session to see. Folder access defaults to read-only. The optional edit grant permits text creation and backed-up text replacement only inside that folder's `Vale-Export` subfolder. Select the parent folder, for example `D:\Codex`, to write under `D:\Codex\Vale-Export`; selecting `Vale-Export` itself would create a nested output scope.
4. Choose **Start this session**. The companion opens the Vale File Link page with a one-session pairing fragment. The page consumes and removes that fragment. Use the page's **Connect PC** control and allow its normal local-network prompt if the browser asks.
5. Open the intended Minecraft profile normally. In File Link, select the actual Java/LWJGL window from the current window list. Keep the game visible and unobstructed. A normal focus request is available; if Windows declines it, click the game window yourself.
6. Leave the companion console and connected File Link tab open while using the session. Press **Ctrl+C** in the console to stop it and release companion-held input.

Folder grants and the random token belong to this running session. Restarting creates a new token and listener port. A local token or connection URL is a capability: do not paste it into chat, screenshots, repositories, or unrelated websites.

If Tk is unavailable, the visible console asks for folders instead. A blank folder finishes setup; a write grant requires typing `YES`. Starting with no folders still permits the Minecraft window operations.

Owner-supplied command-line grants are also possible:

```powershell
py -3 .\vale_companion.py --root "D:\Downloads" --writable-root "D:\Codex"
```

That example reads Downloads and allows output text writes only under `D:\Codex\Vale-Export`. It does not grant edits to a world or a live profile elsewhere.

## File behavior

The available operations are `info`, `list`, `read_range`, `create_text`, and `replace_text`. They take a root ID returned by `status` and a relative path using forward slashes. Reads are limited to the roots the owner selected. Absolute paths, traversal, backslashes, Windows device names, alternate data streams, symbolic links, junctions, and special files are rejected. File Link's protected paths, such as `.git`, `.ssh`, `.aws`, `.env*`, private-key names, and key-container extensions, are excluded here as well. Directory scans stop at 10,000 entries and explicitly report truncation.

- `read_range` returns at most 65,536 bytes, base64 encoded, with the range hash, offset, file size, and modification time. It rejects a file that changes during the read.
- `info` hashes ordinary files by default; `hash:false` requests metadata only. Hashing is limited to files of at most 512 MiB.
- `create_text` writes UTF-8 text without NUL characters, at most 128 KiB, to a **new** output path. Only the same explicit text/code extensions accepted by File Link can be edited. It never overwrites or resumes an existing candidate.
- `replace_text` requires the exact lower-case SHA-256 returned for the file being reviewed. It rejects a mismatched or changing source. Before replacing, it creates an original-byte backup in the adjacent `.vale-backups` directory, stages a new `.vale-pending-*` output, and rechecks the source hash and identity.
- A failed preparation retains its backup and pending output for inspection. Those files can be listed and read but cannot be edited or resumed through the RPC interface.

Stop other programs that are writing a file before editing it. Repeated hash/identity checks detect observed changes; they do not form a transaction with an unrelated program modifying or renaming that same path between checks. Existing world saves and original archives should stay in read-only grants, while candidate work goes under a new output path.

The companion provides no delete, rename-world, arbitrary binary write, arbitrary shell, upload-to-Bisect, or credential-reading operation. Existing File Link browser operations retain their separate protocol and grants.

## Desktop behavior

Selection records the window handle, PID, process creation time, and executable path. The backend validates that identity again before capture or input. It requires a visible Java/LWJGL window in the active interactive desktop. A title alone does not establish identity.

Screenshots are PNG images of the visible client rectangle, obtained through Windows GDI and resized to a bounded payload. The default compressed image cap is 393,216 bytes; the maximum accepted cap is 524,288 bytes. Captures explicitly report `capture_verified:false` and `occlusion_possible:true` until actual-PC acceptance is recorded separately. An overlay or capture incompatibility can still affect the image. Compare the first images to the real display and to Minecraft's own screenshot if needed.

Each screenshot returns a random `frame_id`. An input action must name the latest frame, no older than 30 seconds. The frame is consumed by one attempted action. Selection or geometry changes invalidate it. Actions hold approved keys/buttons for no more than 500 ms; the backend releases its inputs after the action, on error, on focus loss, and when stopped. A local watchdog manages release independently of the network response.

The current controls cover basic movement, inventory/interactions, hotbar selection, screenshots/debug view, and bounded mouse movement. For menu clicks, paired `image_x`/`image_y` coordinates refer to pixels in the latest returned PNG; the backend maps them to that same client rectangle. They cannot be combined with relative camera movement. Screenshots also report the cursor location when available. Check the backend's current key list before testing a custom mod binding. Selecting a process and accepting an input request do not prove that Minecraft handled the input: verify the next image and game state.

The read-only `processes` operation uses a fixed local query to report relevant Python/Java process roles, PIDs, and activity counters. It accepts no query or command arguments and does not return raw command lines. It can help distinguish a running assembly process from an idle terminal; counters alone do not establish successful assembly or gameplay.

### Actual-client acceptance order

1. Inventory the intended Java 8, Minecraft 1.12.2, Forge 14.23.5.2859 profile and its exact 11 mod files/configuration. Record identities before launching. Use the existing signed-in launcher; authentication stays on the PC.
2. Launch the reviewed profile in a visible window. Prove that the selected PID/window belongs to that launch, and inspect a current title/menu screenshot and the new client log.
3. Verify a real screenshot, a small camera movement, a short movement press, and complete key/button release. Check focus loss and stop behavior before sustained control.
4. Test the client in a disposable acceptance world or a copy of the intended server world. Leave the original `Naruto.test` stage unchanged. A test world is expected to change during play; verify the untouched source manifest separately.
5. Join the intended server with the real client. Require a visible loaded world, matching client/server player-join evidence, the expected mod handshake, and no new crash or disconnect. A server-ready message or a bot join alone does not establish graphical-client acceptance.
6. Exercise the actual Vale HUD, Controls, gameplay features, Essential/OptiFine configuration, and intended shader state. Record what was tested. A graphics-disabled diagnostic pass does not accept the full graphics profile.
7. Keep full-package, live-world, latest-Magma, and Bisect acceptance separate. Do not upload an incomplete or failed assembly candidate.

## Optional fixed launcher registration

No process-launch action is enabled by default. An owner may provide a local JSON array with `--actions-file`. Each registration contains `id`, optional `label`, an absolute `.exe` path, an exact executable `sha256`, fixed `argv` strings, and an existing `cwd`. Fixed script/JAR arguments must also appear in `required_files` as `{path,sha256}` records. Other relevant profile inputs can be included there too. The RPC caller can supply only its `action_id`; it cannot supply an executable, command string, working directory, or extra arguments. All registered hashes are checked again before launch, and `shell=False` is used.

Only register a specific reviewed launcher command. This feature verifies the executable and the explicit required files, not every file the launcher may subsequently read. The launcher/profile still needs its own reviewed manifest. Wildcard and directory Java class paths are rejected for this reason; use explicit hashed JAR inputs or the normal reviewed launcher. Keep registrations outside writable grants. Closing the companion does not force-kill a launched game or launcher, which could interrupt a world save.

## Local protocol

The server binds only `127.0.0.1` on an OS-assigned port. It accepts `POST /rpc` with all of:

- `Host: 127.0.0.1:<port>` or `localhost:<port>` matching the listener;
- `Origin: https://vale-file-link.neontick37.chatgpt.site` exactly;
- `Authorization: Bearer <current-session-token>`;
- `Content-Type: application/json` and one bounded `Content-Length`.

CORS permits only that origin, `POST`, and the authorization/content-type headers. The preflight supports the private-network request header. There is no static file route and no URL-based authentication. Requests are finite UTF-8 JSON without duplicate keys, at most 1 MiB:

```json
{"id":"unique-request-id","op":"status","args":{},"timeout_ms":12000}
```

Responses are either `{"id":"...","ok":true,"result":...}` or `{"id":"...","ok":false,"error":{"code":"...","message":"..."}}`. Transport/auth/parse failures use an appropriate HTTP failure status and the same error envelope without an ID.

| Operation | Arguments/result |
| --- | --- |
| `status` | Returns protocol, source version, session instance ID, root grants, native status, optional named actions, and limits. |
| `windows` | Returns an array of current eligible window identities. |
| `processes` | `{}`; fixed read-only Python/Java process role and activity query, without raw command lines. |
| `select` | `{hwnd,pid,creation_time_100ns,exe}` copied from the current window result. |
| `focus` | `{}`; a normal Windows foreground request. No focus-policy bypass. |
| `screenshot` | Optional `max_width`, `max_height`, `max_png_bytes`; returns base64 PNG, geometry, target identity, timestamp, and frame lease. |
| `action` | A fresh `frame_id`, approved `keys`, `buttons`, `duration_ms` up to 500, and either relative `mouse_dx`/`mouse_dy` or paired menu `image_x`/`image_y` coordinates. |
| `release` | `{}`; releases companion-held inputs. |
| `info` | `{root_id,path,hash?:true}`. Empty path is the selected root. |
| `list` | `{root_id,path,offset?:0,limit?:200}`; maximum page size 500. |
| `read_range` | `{root_id,path,offset?:0,length?:65536}`. |
| `create_text` | `{root_id,path,text}`; output must not exist. |
| `replace_text` | `{root_id,path,text,expected_sha256}`. |
| `launch` | `{action_id}` from the owner's local registration. |

The optional top-level `timeout_ms` is an integer from 1 to 12,000, defaulting to 12,000. Its deadline starts when the RPC request enters the coordinator, before waiting for another operation. A request that expires in that queue returns a cached `REQUEST_EXPIRED` failure without dispatching the operation. The browser passes its actual request budget. This deadline prevents late queued work; it does not cancel file work already in progress.

Request IDs are cached for 600 seconds. An identical retry returns its prior receipt, even if that retry's queue budget expired; reuse with different arguments or a different timeout fails. Concurrent duplicate actions execute once within that window. Large result bodies may be discarded to keep memory bounded; a retry then returns `REPLAY_RESULT_EXPIRED` without executing it again. The 600-second window is not an indefinite exactly-once guarantee. After an uncertain input outcome, inspect a new screenshot instead of creating a new ID to replay movement.

## Verification

Run both suites from this folder:

```powershell
py -3 -m unittest discover -s tests -v
py -3 -m unittest -v test_vale_windows.py
```

The coordinator suite covers real loopback HTTP requests, denied origins and tokens, CORS preflight, malformed request limits, unregistered execution denial, folder traversal and links, read-only grants, source changes, original-byte backups, bounded reads, and duplicate-request behavior. The desktop suite uses a fake Win32 adapter to exercise input/capture guards and releases. Neither suite starts a real Minecraft process or accepts a live world.

## Research sources and design basis

- [Mojang's exact 1.12.2 version metadata](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) specifies Java 8 and the Windows LWJGL `2.9.4-nightly-20150209` dependencies.
- [LWJGL 2 Mouse API](https://legacy.lwjgl.org/javadoc/org/lwjgl/input/Mouse.html) and [Keyboard API](https://legacy.lwjgl.org/javadoc/org/lwjgl/input/Keyboard.html) describe polling, buffered events, and captured/relative mouse behavior. The [historical Windows mouse source](https://github.com/LWJGL/lwjgl/blob/e4b098c5e20b9f4465e3a5efc34cc40684df259d/src/java/org/lwjgl/opengl/WindowsMouse.java) recenters a grabbed cursor for an active, visible display. This motivates bounded relative movement and actual-PC calibration.
- [Minecraft's screenshot guide](https://www.minecraft.net/en-us/article/screenshotting-guide) documents F2 for Java screenshots. The [Forge 1.12.x screenshot patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/patches/minecraft/net/minecraft/util/ScreenShotHelper.java.patch) shows that a mod can cancel or redirect the screenshot, so validate the actual output path.
- [Mineflayer](https://github.com/PrismarineJS/mineflayer) is a protocol bot with 1.12 support. Its [Forge helper](https://github.com/PrismarineJS/node-minecraft-protocol-forge) implements handshake/mod-list presentation. [Maintainer clarification](https://github.com/PrismarineJS/mineflayer/issues/2974) distinguishes that from arbitrary mod functionality. A Mineflayer connection would not prove this client's HUD, shader, rendering, or input behavior.
- [Malmo's build](https://github.com/microsoft/malmo/blob/master/Minecraft/build.gradle) targets Minecraft/Forge `1.11.2-13.20.0.2228`. [MineDojo's build](https://github.com/MineDojo/MineDojo/blob/main/minedojo/sim/Malmo/Minecraft/build.gradle) targets `1.11.2-13.20.1.2588`; its [installation documentation](https://docs.minedojo.org/sections/getting_started/install.html) names Ubuntu and macOS as tested direct-install platforms. Those simulators are not evidence that the user's 1.12.2 Forge pack works.
