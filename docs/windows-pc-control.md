# Windows PC access and Minecraft control

Research and implementation checkpoint: 11 November 2026.

## Implemented route

The Vale PC Companion extends the existing private Vale File Link with a locally started Python program. Its source is in `tools/pc_companion/`. The deployed Site distributes the same reviewed source ZIP through its **Download companion** control.

The connection has three parts:

1. ChatGPT calls the existing authenticated Vale plugin.
2. The owner-signed-in Vale page on the Windows PC retrieves one short-lived request and sends it to the local companion.
3. The companion performs a bounded operation in the owner's logged-in desktop session and returns a result. The page reports it to the plugin; captures are returned as actual MCP images.

The companion binds only to `127.0.0.1` on a random port. A fresh session token goes into the browser URL fragment, is consumed into memory, and is removed from the address bar. That token is sent only in an Authorization header to the local listener. The private Site's login stays in the browser. No OpenAI API key, paid model request, public listener, Windows service, firewall change, or persistent startup task is part of this implementation.

This is an implemented access path, not a claim that a remote PC is already connected. Source tests, Windows ABI checks, a device heartbeat, a valid screenshot, observed game input, and a successful Bisect import establish different things. Record each separately.

## First connection

Open [Vale File Link](https://vale-file-link.neontick37.chatgpt.site) on the Windows PC, download the companion, and extract it into a new folder. Double-click `START_VALE_COMPANION.cmd`; it uses an existing Python 3.8 or later installation. Keep the console visible and run as the normal user.

In the local picker, select the required project folders. Reads are confined to those grants. Writing is optional and restricted to a `Vale-Export/` child directory. For candidate work under `D:\Codex\Vale-Export`, select `D:\Codex` as the parent grant. Keep original stage/world/archive locations read-only. Selecting `Vale-Export` itself would produce a nested output scope.

Start the session. In the page it opens, press **Connect PC** and accept the browser's normal loopback/local-network permission for that Site. Keep both the page and companion open. Restarting the companion or reloading the page requires a fresh connection.

Open the intended Minecraft profile through its existing signed-in launcher. Refresh the window list and select the actual Minecraft Java/LWJGL window. **Capture window** makes one normal focus request and then captures; if Windows refuses focus, click the game and request the capture through chat. Keep Windows unlocked and the game visible. The companion's **Ctrl+C** path stops input and closes the listener; the page also has **Stop & disconnect**.

The initial setup does not enable a generic remote terminal. Named process launch is optional and uses an owner-local registration with a fixed executable, arguments, working directory, executable hash and required script/JAR hashes. The remote caller can supply only that registration's action ID.

## What this enables

| Capability | Implemented behavior | Required observation |
| --- | --- | --- |
| File inspection | Selected-root listings, metadata/hash and bounded binary reads | Actual completed native ticket and file identity |
| Text changes | New files or SHA-guarded replacements under writable `Vale-Export/`, with original-byte backups | Completed result and verified new hash |
| Assembly diagnosis | Bounded Python/Java process records; assembly roles and CPU/I/O counters without raw command lines | Two observations over time plus reports/output state; one quiet sample is not a hang diagnosis |
| Window selection | HWND, PID, executable and exact process-creation string | Live identity and visible Minecraft window |
| Screenshot | Bounded PNG of the visible Minecraft client rectangle | Actual image showing current game state |
| Menu click | Pixel coordinates from the latest PNG mapped to its unchanged client rectangle | Subsequent screenshot showing the intended menu result |
| Gameplay input | Approved keys/buttons and short relative camera movement, at most 500 ms | Subsequent frame/log/game evidence that the client received it |
| Launch | Locally registered fixed action, rehashed before execution | Actual process creation; then separate client/server acceptance |

Each input requires the latest one-use screenshot frame ID, no older than 30 seconds. The target identity, foreground state and geometry are rechecked. The backend releases injected keys/buttons in normal completion, error, focus-loss and stop paths, with a separate watchdog. A blocked request expires while waiting for the local dispatch lock instead of executing after its browser timeout.

The key set covers basic movement, inventory/interactions, hotbar selection and selected function keys. It is not unrestricted text entry. Custom mod bindings and server-address entry need their own verified configuration or a future reviewed extension. A successful input API return means the events were submitted; it does not prove Minecraft handled them.

## Why Python and CMD are useful here

CMD is the visible launcher. Python supplies filesystem operations, an authenticated local HTTP endpoint, and Windows API calls through its standard-library `ctypes`. The cloud conversation cannot make a Windows path accessible merely by running Python in its Linux container. A live process on the intended PC must provide the connection.

The companion runs in the owner's interactive session because ordinary Windows services run separately from the interactive desktop. Microsoft explicitly advises against new interactive-service designs. [Microsoft: Interactive Services](https://learn.microsoft.com/en-us/windows/win32/services/interactive-services)

Windows `SendInput` synthesizes desktop input but is restricted by User Interface Privilege Isolation: a lower-integrity process cannot inject into an elevated target. The intended setup runs both Minecraft and the companion normally. Foreground checks reduce mistakes, but `SendInput` is not an atomic process-addressed input channel. [Microsoft: SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput)

Windows can refuse a foreground request; the implementation reports `NEEDS_FOCUS` and lets the owner focus the game. It does not repeatedly steal focus or work around elevation/UAC. [Microsoft: SetForegroundWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setforegroundwindow)

The initial GDI capture path is practical for a visible window, but it can include overlays or return unsuitable OpenGL/fullscreen frames. Start windowed and compare actual images. Success of BitBlt/PNG encoding alone is insufficient. [Microsoft: BitBlt](https://learn.microsoft.com/en-us/windows/win32/api/wingdi/nf-wingdi-bitblt)

The browser must grant its real loopback permission. Chrome's current model separates `loopback-network` from `local-network`, in addition to ordinary CORS. The code does not disable browser protections. [Chrome: Local Network Access](https://developer.chrome.com/docs/modern-web-guidance/guides/security/local-network-access)

## Other supported routes investigated

| Route | What it supplies | Fit for this work |
| --- | --- | --- |
| ChatGPT desktop Computer Use | Native foreground Windows screenshots/input for permitted apps; local file/shell access follows the task's host and permissions | The simplest built-in route when the Windows host is actually attached and the feature is available |
| Work Cloud with local computer access | Approved resources through a connected desktop host in an eligible task | Useful across devices; older tasks retain their original mode, so enabling a setting does not prove attachment to this existing task |
| Secure MCP Tunnel | Outbound HTTPS transport from a local MCP server to supported OpenAI products | A supported browser-independent upgrade; requires tunnel/account setup and a running tunnel client plus the local server |
| Windows SSH over a private network | Authenticated file/process access | Useful for terminal work, but does not itself provide interactive Minecraft capture/input |
| RDP/VNC | A graphical remote session | A separate access/session choice, not an automatically attached tool in this chat; console/session behavior must be verified |

Official setup references: [Computer Use](https://learn.chatgpt.com/docs/computer-use), [Work across devices](https://learn.chatgpt.com/docs/get-started-with-work#continue-work-across-devices), [Remote connections](https://learn.chatgpt.com/docs/remote-connections), and [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels).

For native desktop use, the documented Windows app path is **Settings → Computer use → Any App**, followed by a task naming the intended app. Keep Windows unlocked. For an eligible Work Cloud task, start a new Cloud task from the desktop app using the same account/workspace. Availability and actual tool attachment must be checked on that PC.

Secure MCP Tunnel can carry stdio or HTTP MCP requests over outbound HTTPS without exposing an inbound public server. It transports requests; the local server still implements files, capture and input. The documented defaults include a 120-second request deadline and a 10 MiB serialized envelope limit. The Vale browser route was selected because it preserves the existing private plugin and needs no new tunnel/key setup.

## Why the actual Forge client remains necessary

The target remains Minecraft 1.12.2, Java 8 and Forge 14.23.5.2859 with the reviewed eleven-mod client. Mojang's exact 1.12.2 metadata specifies Java 8 and the Windows LWJGL 2.9.4 nightly runtime. [Mojang 1.12.2 metadata](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json)

LWJGL distinguishes GUI mouse position from grabbed relative movement. This motivates image-coordinate menu clicks, short relative camera steps and actual calibration. [LWJGL Mouse API](https://legacy.lwjgl.org/javadoc/org/lwjgl/input/Mouse.html)

Mineflayer's Forge helper implements connection-handshake behavior and mod-list presentation. A protocol bot does not exercise this graphical client's shaders, HUD or custom controls. Malmo's official build uses Minecraft/Forge 1.11.2-13.20.0.2228, while MineDojo's build uses 1.11.2-13.20.1.2588. Those are useful research systems, but success there would not accept this 1.12.2 client. [Forge protocol helper](https://github.com/PrismarineJS/node-minecraft-protocol-forge), [Malmo build](https://github.com/microsoft/malmo/blob/master/Minecraft/build.gradle), [MineDojo build](https://github.com/MineDojo/MineDojo/blob/main/minedojo/sim/Malmo/Minecraft/build.gradle).

## Deployment and acceptance sequence

First inspect the old assembly's process records, status report, preparation/assembly JSON and incomplete markers. Preserve the original stage, archive, original `Naruto.test`, and any incomplete output. A process existing or consuming CPU is an observation, not a successful assembly.

The fresh Core 039 preparation inputs remain separate from the earlier 038 attempt. Recover the original reviewed source inventory and verify its pinned identity before building a new candidate. Do not substitute a newly scanned inventory for the missing approved one. The complete client/Bisect contents and expected roster are documented in [client-and-bisect-039.md](client-and-bisect-039.md).

Then validate a real Minecraft capture, a small input step, a menu click, release/focus-loss behavior and the stop path. Use a disposable acceptance world or a copy of the intended world for initial play. Join the intended server with the actual client and require a visible loaded world plus matching join/handshake logs. Exercise the real Vale HUD, Controls, gameplay and intended OptiFine/Solas configuration.

Only a complete reviewed candidate should progress to Bisect import. The companion's publication and connection do not establish assembly, saved-world, latest-Magma, graphical-client or hosting acceptance. Keep those gates explicit in the recovery record.
