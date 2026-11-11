# Windows companion: primary sources and acceptance limits

Sources checked on 11 November 2026. These documents support the implementation choices; they do not establish that capture or controls have worked on Jack's PC.

## Process and desktop access

- [Microsoft: Interactive Services](https://learn.microsoft.com/en-us/windows/win32/services/interactive-services) explains why a Windows service is not the owner's interactive desktop. This companion runs visibly in the owner's logged-in console session. It does not install a service or attempt to access a secure desktop.
- [Microsoft: QueryFullProcessImageNameW](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-queryfullprocessimagenamew) and [GetProcessTimes](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getprocesstimes) supply executable identity and creation time. The companion pins HWND, PID, executable path and creation time, and rechecks them before control. Creation time is transported as a decimal string so JavaScript cannot round the 64-bit FILETIME value.
- [Microsoft: SetForegroundWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setforegroundwindow) documents restrictions on foreground activation. The companion makes one normal request. If Windows refuses, `NEEDS_FOCUS` asks the owner to click the visible Minecraft window.
- [Microsoft: Remote and console terminal differences](https://learn.microsoft.com/en-us/windows/win32/termserv/consoles-vs-terminals) describes why RDP may move the active session away from the physical console. The current companion requires the active console session.

## Keyboard and mouse

- [Microsoft: SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput) supports injected keyboard and mouse events, subject to equal-or-lower integrity levels. Run Minecraft and this companion as the same normal user. A failed return does not specifically identify UIPI as its cause. Existing pressed keys can affect injected events.
- [Microsoft: KEYBDINPUT](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-keybdinput) documents scan-code input and key-up flags. The companion sends bounded physical game keys, tracks its own pressed controls, and releases them in `finally` and through a local watchdog.
- [Microsoft: MOUSEINPUT](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-mouseinput) documents relative movement and normalized absolute movement across the virtual desktop. Relative movement is used for the game camera; fresh screenshot coordinates map to absolute menu positions. Mouse movement is inserted before button-down.

Actions require the selected game window to remain visible and foreground, a screenshot frame no older than 30 seconds, and at most 500 ms of input. Each frame permits one attempted action. These are companion limits, not guarantees made by Microsoft. Windows input targets the foreground desktop globally; the identity and focus checks reduce races but are not an atomic OS-level binding of SendInput to one process. No arbitrary text entry, privileged input, or shell command interface is supplied by this backend.

## Capture and coordinates

- [Microsoft: StretchBlt](https://learn.microsoft.com/en-us/windows/win32/api/wingdi/nf-wingdi-stretchblt) and [BitBlt](https://learn.microsoft.com/en-us/windows/win32/api/wingdi/nf-wingdi-bitblt) document GDI rectangle copying and resizing. The current implementation captures the visible screen rectangle occupied by Minecraft's client area and encodes a bounded PNG. It is not an isolated application capture: overlapping windows can appear in that rectangle. Hardware-rendered/OpenGL or exclusive-fullscreen capture must be checked on the actual PC; a successful GDI call does not prove the pixels show the current game frame.
- [Microsoft: SetProcessDpiAwarenessContext](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setprocessdpiawarenesscontext) documents setting DPI awareness before coordinate-dependent calls. The script attempts this before accessing windows. A future packaged executable should declare DPI awareness in its manifest.
- [Microsoft: Desktop Duplication API](https://learn.microsoft.com/en-us/windows/win32/direct3ddxgi/desktop-dup-api) and [DuplicateOutput](https://learn.microsoft.com/en-us/windows/win32/api/dxgi1_2/nf-dxgi1_2-idxgioutput1-duplicateoutput) describe a possible future GPU capture backend. Disconnected sessions and secure desktops have explicit access failures. This package does not include that backend.

Start actual acceptance with Minecraft visible in windowed mode. Inspect a real capture, request a small camera or menu movement, then capture again and verify the visible result. Until that succeeds, `capture_verified` and `input_verified` deliberately remain `false`.

## Runtime, relay and packaging

- [Python: ctypes](https://docs.python.org/3/library/ctypes.html) documents calling native DLL functions from Python. This backend uses the standard library and explicit Windows-width/pointer-width structures so it can run with the existing Python 3.8+ installation.
- [Chrome: Local Network Access](https://developer.chrome.com/docs/modern-web-guidance/guides/security/local-network-access) documents the loopback-network permission and normal CORS requirements for an HTTPS page connecting to localhost. The browser must grant this permission; no browser security flags are changed.
- [PyInstaller manual](https://pyinstaller.org/en/stable/) states that Windows executables must be built on Windows. A Python/CMD package is not falsely presented as a tested standalone Windows executable.
- [Python-MSS support](https://python-mss.readthedocs.io/stable/support.html) and [DXcam's project metadata](https://github.com/ra1nty/DXcam/blob/main/pyproject.toml) show why installing today's capture dependencies cannot be assumed to work with Python 3.8. The initial backend avoids those dependencies.

`test_vale_windows.py` separates fake-adapter control-contract checks from `WindowsNoninteractiveCISmokeTests`. The Windows smoke tests call the real DLLs only to initialize signatures, inspect the test process, report desktop state, and enumerate visible windows. They never inject input, focus another application, or capture the screen, and cannot establish Minecraft gameplay acceptance.
