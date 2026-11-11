"""Bounded Windows desktop backend for the Vale local companion.

Python 3.8+, standard library only. Importing this module does not touch Win32.
The native backend must run in the owner's unlocked, interactive console session.
GDI capture is deliberately reported as unverified until tested on the real PC.
This module does not listen on a port, accept arbitrary commands, or modify files.
"""

import atexit
import base64
import ctypes
import datetime
import json
import ntpath
import os
import re
import secrets
import struct
import subprocess
import threading
import time
import zlib


class BackendError(Exception):
    def __init__(self, code, message, details=None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details or {}

    def to_dict(self):
        return {"code": self.code, "message": self.message, "details": self.details}


# Explicit Windows-width types also permit meaningful structure tests on Linux.
DWORD = ctypes.c_uint32
LONG = ctypes.c_int32
WORD = ctypes.c_uint16
BOOL = ctypes.c_int32
HANDLE = ctypes.c_void_p
ULONG_PTR = ctypes.c_size_t
LPARAM = ctypes.c_ssize_t


class POINT(ctypes.Structure):
    _fields_ = [("x", LONG), ("y", LONG)]


class RECT(ctypes.Structure):
    _fields_ = [("left", LONG), ("top", LONG), ("right", LONG), ("bottom", LONG)]


class FILETIME(ctypes.Structure):
    _fields_ = [("low", DWORD), ("high", DWORD)]


class CURSORINFO(ctypes.Structure):
    _fields_ = [("cbSize", DWORD), ("flags", DWORD), ("hCursor", HANDLE), ("ptScreenPos", POINT)]


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", LONG), ("dy", LONG), ("mouseData", DWORD),
                ("dwFlags", DWORD), ("time", DWORD), ("dwExtraInfo", ULONG_PTR)]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", WORD), ("wScan", WORD), ("dwFlags", DWORD),
                ("time", DWORD), ("dwExtraInfo", ULONG_PTR)]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [("uMsg", DWORD), ("wParamL", WORD), ("wParamH", WORD)]


class INPUTUNION(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", DWORD), ("u", INPUTUNION)]


class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [("biSize", DWORD), ("biWidth", LONG), ("biHeight", LONG),
                ("biPlanes", WORD), ("biBitCount", WORD), ("biCompression", DWORD),
                ("biSizeImage", DWORD), ("biXPelsPerMeter", LONG),
                ("biYPelsPerMeter", LONG), ("biClrUsed", DWORD), ("biClrImportant", DWORD)]


class RGBQUAD(ctypes.Structure):
    _fields_ = [("blue", ctypes.c_ubyte), ("green", ctypes.c_ubyte),
                ("red", ctypes.c_ubyte), ("reserved", ctypes.c_ubyte)]


class BITMAPINFO(ctypes.Structure):
    _fields_ = [("bmiHeader", BITMAPINFOHEADER), ("bmiColors", RGBQUAD * 1)]


# Scan codes describe physical keys, which Minecraft's LWJGL input understands.
KEYS = {
    "w": (0x11, 0x57), "a": (0x1E, 0x41), "s": (0x1F, 0x53),
    "d": (0x20, 0x44), "e": (0x12, 0x45), "q": (0x10, 0x51),
    "f": (0x21, 0x46), "space": (0x39, 0x20), "shift": (0x2A, 0xA0),
    "ctrl": (0x1D, 0xA2), "esc": (0x01, 0x1B), "enter": (0x1C, 0x0D),
    "tab": (0x0F, 0x09), "f2": (0x3C, 0x71), "f3": (0x3D, 0x72),
    "f5": (0x3F, 0x74),
}
KEYS.update({str(i): (i + 1, 0x30 + i) for i in range(1, 10)})
BUTTONS = {"left": (0x0002, 0x0004, 0x01),
           "right": (0x0008, 0x0010, 0x02),
           "middle": (0x0020, 0x0040, 0x04)}
MODIFIER_VKS = (0x10, 0x11, 0x12, 0x5B, 0x5C)

# Fixed local read-only query. Java command lines are not requested. Python
# command lines exist only inside PowerShell long enough to classify script
# names; they are never serialized, logged, or returned to this process.
PROCESS_QUERY = r"""
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$WarningPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
try {
    $properties = @('Name','ProcessId','CreationDate','KernelModeTime','UserModeTime','ReadTransferCount','WriteTransferCount')
    $python = @(Get-CimInstance -ClassName Win32_Process -Filter "Name LIKE 'python%.exe' OR Name = 'py.exe'" -Property ($properties + @('CommandLine')) -OperationTimeoutSec 3 -ErrorAction Stop | Select-Object -First 201)
    $java = @(Get-CimInstance -ClassName Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -Property $properties -OperationTimeoutSec 3 -ErrorAction Stop | Select-Object -First 201)
    $all = @($python) + @($java)
    $truncated = $all.Count -gt 200
    $rows = @()
    $unreadable = 0
    foreach ($process in @($all | Sort-Object ProcessId | Select-Object -First 200)) {
        $name = [string]$process.Name
        $role = 'java'
        if ($name -ieq 'py.exe' -or $name -imatch '^python.*\.exe$') {
            $role = if ($name -ieq 'py.exe') { 'python_launcher' } else { 'python_other' }
            $line = [string]$process.CommandLine
            if ([string]::IsNullOrWhiteSpace($line)) {
                $unreadable++
                $role = 'unknown'
            } else {
                $line = $line.Replace('/', '\')
                if ($line -imatch '(?:^|[\\\s"])prepare_connected_release\.py(?=$|[\s"])') {
                    $role = 'prepare_connected_release'
                } elseif ($line -imatch '(?:^|[\\\s"])assemble_release\.py(?=$|[\s"])') {
                    $role = 'assemble_release'
                }
            }
            $line = $null
        }
        $created = $null
        if ($null -ne $process.CreationDate) {
            $created = $process.CreationDate.ToUniversalTime().ToString('o')
        }
        $rows += [pscustomobject]@{
            pid = [int]$process.ProcessId
            name = $name
            creation_time_utc = $created
            role = $role
            kernel_time_100ns = [string]$process.KernelModeTime
            user_time_100ns = [string]$process.UserModeTime
            read_bytes = [string]$process.ReadTransferCount
            write_bytes = [string]$process.WriteTransferCount
        }
    }
    [pscustomobject]@{status='ok'; unreadable_count=$unreadable; truncated=$truncated; processes=@($rows)} | ConvertTo-Json -Depth 4 -Compress
} catch {
    [pscustomobject]@{status='unavailable'; processes=@()} | ConvertTo-Json -Depth 3 -Compress
    exit 2
}
"""
PROCESS_RESPONSE_BYTES = 128 * 1024
PROCESS_COUNTERS = ("kernel_time_100ns", "user_time_100ns", "read_bytes", "write_bytes")
PYTHON_ROLES = ("prepare_connected_release", "assemble_release", "python_other", "python_launcher", "unknown")


def _process_unavailable(code):
    return {"available": False, "status": "unavailable", "processes": [],
            "unreadable_count": 0, "truncated": False, "partial": True,
            "observed_at_utc": _utc_now(),
            "error": {"code": code, "message": "The read-only Windows process query was unavailable; no process-health conclusion can be drawn"}}


def _process_unsigned(value):
    if type(value) is int and 0 <= value <= 2 ** 64 - 1:
        return str(value)
    if isinstance(value, str) and re.fullmatch(r"[0-9]{1,20}", value) and int(value) <= 2 ** 64 - 1:
        return value
    return None


def _parse_process_response(raw):
    """Project only approved metadata; unknown fields can never escape."""
    try:
        if not isinstance(raw, bytes) or len(raw) > PROCESS_RESPONSE_BYTES:
            raise ValueError("invalid response size")
        value = json.loads(raw.decode("utf-8-sig"))
        if not isinstance(value, dict) or value.get("status") != "ok":
            return _process_unavailable("CIM_QUERY_FAILED")
        rows = value.get("processes")
        unreadable = value.get("unreadable_count")
        if (not isinstance(rows, list) or type(unreadable) is not int or not 0 <= unreadable <= 1000000 or
                type(value.get("truncated")) is not bool):
            raise ValueError("invalid response shape")
        result, seen = [], set()
        partial = bool(unreadable)
        for row in rows[:200]:
            if not isinstance(row, dict):
                raise ValueError("invalid process row")
            pid, name = row.get("pid"), row.get("name")
            if (type(pid) is not int or not 0 < pid <= 0xFFFFFFFF or pid in seen or
                    not isinstance(name, str) or len(name) > 100 or
                    not re.fullmatch(r"(?:python[A-Za-z0-9_.-]*|py|java|javaw)\.exe", name, re.IGNORECASE)):
                raise ValueError("invalid process identity")
            seen.add(pid)
            role = row.get("role")
            if name.lower() in ("java.exe", "javaw.exe"):
                role = "java"
            elif role not in PYTHON_ROLES:
                role = "unknown"
            created = row.get("creation_time_utc")
            if created is not None:
                if not isinstance(created, str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,7})?(?:Z|\+00:00)", created):
                    created = None
                else:
                    try:
                        datetime.datetime.strptime(created[:19], "%Y-%m-%dT%H:%M:%S")
                    except ValueError:
                        created = None
            counters = {key: _process_unsigned(row.get(key)) for key in PROCESS_COUNTERS}
            partial = partial or created is None or role == "unknown" or any(v is None for v in counters.values())
            result.append({"pid": pid, "name": name, "creation_time_utc": created, "role": role, **counters})
        truncated = value["truncated"] or len(rows) > 200
        return {"available": True, "status": "ok", "processes": sorted(result, key=lambda row: row["pid"]),
                "unreadable_count": unreadable, "truncated": truncated, "partial": partial or truncated,
                "observed_at_utc": _utc_now()}
    except (ValueError, TypeError, UnicodeError):
        return _process_unavailable("INVALID_PROCESS_RESPONSE")


def _query_processes(api, runner=None):
    runner = subprocess.run if runner is None else runner
    try:
        executable = api.powershell_executable()
        completed = runner([executable, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", PROCESS_QUERY],
                           stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                           timeout=8, check=False, shell=False,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if completed.returncode != 0:
            return _process_unavailable("CIM_QUERY_FAILED")
        return _parse_process_response(completed.stdout)
    except subprocess.TimeoutExpired:
        return _process_unavailable("PROCESS_QUERY_TIMED_OUT")
    except (OSError, BackendError):
        return _process_unavailable("POWERSHELL_UNAVAILABLE")


def _integer(value, name, low, high):
    if isinstance(value, bool) or not isinstance(value, int) or not low <= value <= high:
        raise BackendError("INVALID_ARGUMENT", "%s must be an integer from %s to %s" % (name, low, high))
    return value


def _utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def _canonical_exe(path):
    if not isinstance(path, str) or not ntpath.isabs(path) or "\x00" in path:
        raise BackendError("INVALID_ARGUMENT", "exe must be the full path reported by windows")
    return ntpath.normcase(ntpath.normpath(path))


def _identity(metadata):
    return {key: metadata[key] for key in ("hwnd", "pid", "creation_time_100ns", "exe")}


def _is_minecraft(metadata):
    return (metadata.get("class_name") == "LWJGL" and
            ntpath.basename(metadata.get("exe", "")).lower() in ("java.exe", "javaw.exe"))


def _png_from_bgra(width, height, data):
    """Encode a top-down, tightly packed 32-bit BGRA frame as a valid RGB PNG."""
    if len(data) != width * height * 4:
        raise BackendError("CAPTURE_FAILED", "The native capture returned an invalid pixel buffer")
    rows = bytearray()
    stride = width * 4
    for y in range(height):
        row = data[y * stride:(y + 1) * stride]
        rgb = bytearray(width * 3)
        rgb[0::3], rgb[1::3], rgb[2::3] = row[2::4], row[1::4], row[0::4]
        rows.append(0)
        rows.extend(rgb)

    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" +
            chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(bytes(rows), 6)) + chunk(b"IEND", b""))


class Win32API:
    """Small Win32 adapter. All handle-bearing functions have pointer-safe types."""

    def __init__(self):
        if os.name != "nt":
            raise BackendError("UNSUPPORTED_PLATFORM", "Native desktop operations require Windows")
        self.user32 = ctypes.WinDLL("user32", use_last_error=True)
        self.kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        self.gdi32 = ctypes.WinDLL("gdi32", use_last_error=True)
        self.WNDENUMPROC = ctypes.WINFUNCTYPE(BOOL, HANDLE, LPARAM)
        self._configure_signatures()
        # A manifest is preferred for a future packaged executable. This script
        # sets DPI awareness before making any window/coordinate dependent calls.
        try:
            setter = self.user32.SetProcessDpiAwarenessContext
            setter.argtypes, setter.restype = [HANDLE], BOOL
            setter(HANDLE(-4))  # DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
        except AttributeError:
            setter = self.user32.SetProcessDPIAware
            setter.argtypes, setter.restype = [], BOOL
            setter()

    def _configure_signatures(self):
        def sig(dll, name, args, result):
            fn = getattr(dll, name)
            fn.argtypes, fn.restype = args, result

        u, k, g = self.user32, self.kernel32, self.gdi32
        sig(u, "EnumWindows", [self.WNDENUMPROC, LPARAM], BOOL)
        sig(u, "IsWindow", [HANDLE], BOOL)
        sig(u, "IsWindowVisible", [HANDLE], BOOL)
        sig(u, "IsIconic", [HANDLE], BOOL)
        sig(u, "GetWindowThreadProcessId", [HANDLE, ctypes.POINTER(DWORD)], DWORD)
        sig(u, "GetWindowTextW", [HANDLE, ctypes.c_wchar_p, ctypes.c_int], ctypes.c_int)
        sig(u, "GetClassNameW", [HANDLE, ctypes.c_wchar_p, ctypes.c_int], ctypes.c_int)
        sig(u, "GetForegroundWindow", [], HANDLE)
        sig(u, "SetForegroundWindow", [HANDLE], BOOL)
        sig(u, "GetClientRect", [HANDLE, ctypes.POINTER(RECT)], BOOL)
        sig(u, "ClientToScreen", [HANDLE, ctypes.POINTER(POINT)], BOOL)
        sig(u, "GetSystemMetrics", [ctypes.c_int], ctypes.c_int)
        sig(u, "OpenInputDesktop", [DWORD, BOOL, DWORD], HANDLE)
        sig(u, "CloseDesktop", [HANDLE], BOOL)
        sig(u, "GetThreadDesktop", [DWORD], HANDLE)
        sig(u, "GetUserObjectInformationW", [HANDLE, ctypes.c_int, HANDLE, DWORD, ctypes.POINTER(DWORD)], BOOL)
        sig(u, "SendInput", [ctypes.c_uint32, ctypes.POINTER(INPUT), ctypes.c_int], ctypes.c_uint32)
        sig(u, "GetAsyncKeyState", [ctypes.c_int], ctypes.c_int16)
        sig(u, "GetCursorInfo", [ctypes.POINTER(CURSORINFO)], BOOL)
        sig(u, "GetDC", [HANDLE], HANDLE)
        sig(u, "ReleaseDC", [HANDLE, HANDLE], ctypes.c_int)
        sig(k, "OpenProcess", [DWORD, BOOL, DWORD], HANDLE)
        sig(k, "CloseHandle", [HANDLE], BOOL)
        sig(k, "QueryFullProcessImageNameW", [HANDLE, DWORD, ctypes.c_wchar_p, ctypes.POINTER(DWORD)], BOOL)
        sig(k, "GetProcessTimes", [HANDLE, ctypes.POINTER(FILETIME), ctypes.POINTER(FILETIME), ctypes.POINTER(FILETIME), ctypes.POINTER(FILETIME)], BOOL)
        sig(k, "GetExitCodeProcess", [HANDLE, ctypes.POINTER(DWORD)], BOOL)
        sig(k, "GetCurrentProcessId", [], DWORD)
        sig(k, "GetCurrentThreadId", [], DWORD)
        sig(k, "ProcessIdToSessionId", [DWORD, ctypes.POINTER(DWORD)], BOOL)
        sig(k, "WTSGetActiveConsoleSessionId", [], DWORD)
        sig(k, "GetSystemDirectoryW", [ctypes.c_wchar_p, ctypes.c_uint32], ctypes.c_uint32)
        sig(g, "CreateCompatibleDC", [HANDLE], HANDLE)
        sig(g, "DeleteDC", [HANDLE], BOOL)
        sig(g, "CreateDIBSection", [HANDLE, ctypes.POINTER(BITMAPINFO), ctypes.c_uint32, ctypes.POINTER(HANDLE), HANDLE, DWORD], HANDLE)
        sig(g, "SelectObject", [HANDLE, HANDLE], HANDLE)
        sig(g, "DeleteObject", [HANDLE], BOOL)
        sig(g, "SetStretchBltMode", [HANDLE, ctypes.c_int], ctypes.c_int)
        sig(g, "StretchBlt", [HANDLE, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int,
                               HANDLE, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, DWORD], BOOL)
        sig(g, "GdiFlush", [], BOOL)

    @staticmethod
    def _failure(code, message):
        return BackendError(code, message, {"winerror": ctypes.get_last_error()})

    def powershell_executable(self):
        system = ctypes.create_unicode_buffer(32768)
        length = self.kernel32.GetSystemDirectoryW(system, len(system))
        if not length or length >= len(system):
            raise BackendError("POWERSHELL_UNAVAILABLE", "The Windows system directory could not be located")
        return ntpath.join(system.value, "WindowsPowerShell", "v1.0", "powershell.exe")

    def process_metadata(self, pid):
        pid = _integer(pid, "pid", 1, 0xFFFFFFFF)
        handle = self.kernel32.OpenProcess(0x1000, False, pid)
        if not handle:
            raise self._failure("PROCESS_UNAVAILABLE", "Could not inspect the selected process")
        try:
            length = DWORD(32768)
            path = ctypes.create_unicode_buffer(length.value)
            if not self.kernel32.QueryFullProcessImageNameW(handle, 0, path, ctypes.byref(length)):
                raise self._failure("PROCESS_UNAVAILABLE", "Could not read the process executable path")
            created, exited, kernel, user = FILETIME(), FILETIME(), FILETIME(), FILETIME()
            if not self.kernel32.GetProcessTimes(handle, ctypes.byref(created), ctypes.byref(exited), ctypes.byref(kernel), ctypes.byref(user)):
                raise self._failure("PROCESS_UNAVAILABLE", "Could not read the process creation identity")
            exit_code = DWORD()
            if not self.kernel32.GetExitCodeProcess(handle, ctypes.byref(exit_code)):
                raise self._failure("PROCESS_UNAVAILABLE", "Could not read process status")
            return {"pid": pid, "exe": path.value,
                    "creation_time_100ns": str((created.high << 32) | created.low),
                    "running": exit_code.value == 259, "exit_code": exit_code.value}
        finally:
            self.kernel32.CloseHandle(handle)

    def window_metadata(self, hwnd):
        if not self.user32.IsWindow(hwnd):
            raise BackendError("WINDOW_UNAVAILABLE", "The selected window no longer exists")
        pid = DWORD()
        if not self.user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid)):
            raise self._failure("WINDOW_UNAVAILABLE", "Could not inspect the window owner")
        title, klass = ctypes.create_unicode_buffer(1024), ctypes.create_unicode_buffer(256)
        self.user32.GetWindowTextW(hwnd, title, len(title))
        self.user32.GetClassNameW(hwnd, klass, len(klass))
        result = {"hwnd": int(hwnd), "pid": pid.value, "title": title.value,
                  "class_name": klass.value, "visible": bool(self.user32.IsWindowVisible(hwnd)),
                  "minimized": bool(self.user32.IsIconic(hwnd))}
        result.update(self.process_metadata(pid.value))
        # HWNDs can be destroyed/reused while metadata is read. Reject a window
        # whose owning PID changes during this read, then recheck again at input.
        checked_pid = DWORD()
        if (not self.user32.IsWindow(hwnd) or
                not self.user32.GetWindowThreadProcessId(hwnd, ctypes.byref(checked_pid)) or
                checked_pid.value != pid.value):
            raise BackendError("TARGET_CHANGED", "The window owner changed while its identity was being read")
        result["minecraft_candidate"] = _is_minecraft(result)
        return result

    def enumerate_windows(self):
        handles = []

        @self.WNDENUMPROC
        def collect(hwnd, _):
            if self.user32.IsWindowVisible(hwnd):
                handles.append(int(hwnd))
            return len(handles) < 512

        self.user32.EnumWindows(collect, 0)
        result = []
        for hwnd in handles:
            try:
                result.append(self.window_metadata(hwnd))
            except BackendError:
                # A closing or inaccessible window is not a Minecraft candidate.
                continue
        return result

    def foreground_window(self):
        return int(self.user32.GetForegroundWindow() or 0)

    def focus_window(self, hwnd):
        # Windows may legitimately deny this. Do not attach input threads, fake
        # Alt presses, change focus lock settings, or repeatedly steal focus.
        return bool(self.user32.SetForegroundWindow(hwnd))

    def _desktop_name(self, desktop):
        name = ctypes.create_unicode_buffer(256)
        needed = DWORD()
        if not self.user32.GetUserObjectInformationW(desktop, 2, name, ctypes.sizeof(name), ctypes.byref(needed)):
            raise self._failure("DESKTOP_UNAVAILABLE", "Could not inspect the input desktop")
        return name.value

    def desktop_state(self):
        session = DWORD()
        if not self.kernel32.ProcessIdToSessionId(self.kernel32.GetCurrentProcessId(), ctypes.byref(session)):
            raise self._failure("DESKTOP_UNAVAILABLE", "Could not inspect the current Windows session")
        console = self.kernel32.WTSGetActiveConsoleSessionId()
        state = {"session_id": session.value, "active_console_session_id": console,
                 "ready": False, "input_desktop": None}
        if session.value == 0 or session.value != console:
            state["reason"] = "The companion must run in the owner's active console session"
            return state
        desktop = self.user32.OpenInputDesktop(0, False, 0x0001)
        if not desktop:
            state["reason"] = "The input desktop is unavailable; the PC may be locked or showing a secure prompt"
            return state
        try:
            name = self._desktop_name(desktop)
            thread_desktop = self.user32.GetThreadDesktop(self.kernel32.GetCurrentThreadId())
            current_name = self._desktop_name(thread_desktop)
            state.update({"input_desktop": name, "ready": name.lower() == "default" and name == current_name})
            if not state["ready"]:
                state["reason"] = "The companion is not on the current interactive input desktop"
            return state
        finally:
            self.user32.CloseDesktop(desktop)

    def client_rect(self, hwnd):
        rect, origin = RECT(), POINT()
        if not self.user32.GetClientRect(hwnd, ctypes.byref(rect)) or not self.user32.ClientToScreen(hwnd, ctypes.byref(origin)):
            raise self._failure("CAPTURE_FAILED", "Could not locate the Minecraft client area")
        width, height = rect.right - rect.left, rect.bottom - rect.top
        vx, vy, vw, vh = [self.user32.GetSystemMetrics(i) for i in (76, 77, 78, 79)]
        if width <= 0 or height <= 0 or width > 16384 or height > 16384:
            raise BackendError("CAPTURE_FAILED", "The Minecraft client area has invalid dimensions")
        if origin.x < vx or origin.y < vy or origin.x + width > vx + vw or origin.y + height > vy + vh:
            raise BackendError("WINDOW_OFFSCREEN", "Move the full Minecraft window onto the visible desktop")
        return {"x": origin.x, "y": origin.y, "width": width, "height": height}

    def capture_bgra(self, rect, width, height):
        screen = memory = bitmap = old_bitmap = None
        try:
            screen = self.user32.GetDC(None)
            if not screen:
                raise self._failure("CAPTURE_FAILED", "Could not open the visible desktop")
            memory = self.gdi32.CreateCompatibleDC(screen)
            if not memory:
                raise self._failure("CAPTURE_FAILED", "Could not create the capture device context")
            info = BITMAPINFO()
            info.bmiHeader.biSize = ctypes.sizeof(BITMAPINFOHEADER)
            info.bmiHeader.biWidth, info.bmiHeader.biHeight = width, -height
            info.bmiHeader.biPlanes, info.bmiHeader.biBitCount = 1, 32
            bits = HANDLE()
            bitmap = self.gdi32.CreateDIBSection(screen, ctypes.byref(info), 0, ctypes.byref(bits), None, 0)
            if not bitmap or not bits.value:
                raise self._failure("CAPTURE_FAILED", "Could not create the capture bitmap")
            old_bitmap = self.gdi32.SelectObject(memory, bitmap)
            if not old_bitmap or old_bitmap == ctypes.c_void_p(-1).value:
                raise self._failure("CAPTURE_FAILED", "Could not select the capture bitmap")
            self.gdi32.SetStretchBltMode(memory, 4)
            if not self.gdi32.StretchBlt(memory, 0, 0, width, height, screen,
                                       rect["x"], rect["y"], rect["width"], rect["height"], 0x40CC0020):
                raise self._failure("CAPTURE_FAILED", "GDI could not capture the visible Minecraft region")
            if not self.gdi32.GdiFlush():
                raise self._failure("CAPTURE_FAILED", "The capture bitmap could not be synchronized")
            return ctypes.string_at(bits, width * height * 4)
        finally:
            if old_bitmap and old_bitmap != ctypes.c_void_p(-1).value and memory:
                self.gdi32.SelectObject(memory, old_bitmap)
            if bitmap:
                self.gdi32.DeleteObject(bitmap)
            if memory:
                self.gdi32.DeleteDC(memory)
            if screen:
                self.user32.ReleaseDC(None, screen)

    def key_is_down(self, vk):
        return bool(self.user32.GetAsyncKeyState(vk) & 0x8000)

    def cursor_position(self):
        info = CURSORINFO()
        info.cbSize = ctypes.sizeof(CURSORINFO)
        if not self.user32.GetCursorInfo(ctypes.byref(info)):
            return None
        return {"x": info.ptScreenPos.x, "y": info.ptScreenPos.y, "visible": bool(info.flags & 1)}

    def send(self, keys_down=(), keys_up=(), buttons_down=(), buttons_up=(), mouse_dx=0, mouse_dy=0,
             screen_x=None, screen_y=None):
        events = []
        # Move before button-down so a menu click lands on its intended pixel.
        if screen_x is not None and screen_y is not None:
            vx, vy, vw, vh = [self.user32.GetSystemMetrics(i) for i in (76, 77, 78, 79)]
            if not (vw > 1 and vh > 1 and vx <= screen_x < vx + vw and vy <= screen_y < vy + vh):
                raise BackendError("WINDOW_OFFSCREEN", "The requested point is outside the visible desktop")
            event = INPUT()
            event.type = 0
            event.mi.dx = round((screen_x - vx) * 65535 / (vw - 1))
            event.mi.dy = round((screen_y - vy) * 65535 / (vh - 1))
            event.mi.dwFlags = 0x0001 | 0x8000 | 0x4000  # MOVE | ABSOLUTE | VIRTUALDESK
            events.append(event)
        elif mouse_dx or mouse_dy:
            event = INPUT()
            event.type, event.mi.dx, event.mi.dy, event.mi.dwFlags = 0, mouse_dx, mouse_dy, 0x0001
            events.append(event)
        for names, up in ((keys_down, False), (keys_up, True)):
            for name in names:
                event = INPUT()
                event.type, event.ki.wScan = 1, KEYS[name][0]
                event.ki.dwFlags = 0x0008 | (0x0002 if up else 0)
                events.append(event)
        for names, up in ((buttons_down, False), (buttons_up, True)):
            for name in names:
                event = INPUT()
                event.type, event.mi.dwFlags = 0, BUTTONS[name][1 if up else 0]
                events.append(event)
        if not events:
            return 0
        array = (INPUT * len(events))(*events)
        ctypes.set_last_error(0)
        inserted = self.user32.SendInput(len(events), array, ctypes.sizeof(INPUT))
        if inserted != len(events):
            raise BackendError("INPUT_REJECTED", "Windows did not insert every input event; check focus and run Minecraft and the companion as the same normal user",
                               {"requested": len(events), "inserted": inserted, "winerror": ctypes.get_last_error()})
        return inserted


class WindowsBackend:
    """Selection-pinned controls with a local release deadline and focus guard."""

    def __init__(self, api=None):
        self.api = api if api is not None else Win32API()
        self._selection = None
        self._state_lock = threading.RLock()
        self._action_lock = threading.RLock()
        self._input_lock = threading.RLock()
        self._pressed_keys, self._pressed_buttons = (), ()
        self._release_deadline = None
        self._stopped = threading.Event()
        self._closed = threading.Event()
        self._last_release_error = None
        self._last_frame = None
        self._action_cancel_reason = None
        self._watchdog = threading.Thread(target=self._watch, name="ValeInputRelease", daemon=True)
        self._watchdog.start()
        atexit.register(self.stop)

    def _check_desktop(self):
        if self._stopped.is_set():
            raise BackendError("STOPPED", "The local companion was stopped; restart it locally to enable controls")
        state = self.api.desktop_state()
        if not state.get("ready"):
            raise BackendError("DESKTOP_UNAVAILABLE", state.get("reason", "The interactive desktop is unavailable"), state)
        return state

    def enumerate_windows(self):
        return self.api.enumerate_windows()

    def enumerate_processes(self):
        return _query_processes(self.api)

    def select_minecraft(self, hwnd, pid, creation_time_100ns, exe):
        _integer(hwnd, "hwnd", 1, min((1 << (ctypes.sizeof(HANDLE) * 8)) - 1, 2 ** 53 - 1))
        _integer(pid, "pid", 1, 0xFFFFFFFF)
        if not isinstance(creation_time_100ns, str) or not creation_time_100ns.isdecimal() or len(creation_time_100ns) > 20:
            raise BackendError("INVALID_ARGUMENT", "creation_time_100ns must be the exact string returned by windows")
        expected_exe = _canonical_exe(exe)
        with self._action_lock:
            self._check_desktop()
            self.release_all()
            with self._state_lock:
                self._selection, self._last_frame = None, None
            actual = self.api.window_metadata(hwnd)
            if (actual["pid"] != pid or actual["creation_time_100ns"] != creation_time_100ns or
                    _canonical_exe(actual["exe"]) != expected_exe):
                raise BackendError("TARGET_CHANGED", "The window or process identity changed; list windows and select again")
            if not _is_minecraft(actual):
                raise BackendError("NOT_MINECRAFT", "Select the Java Minecraft 1.12.2 LWJGL game window")
            if not actual.get("running", True):
                raise BackendError("PROCESS_UNAVAILABLE", "The selected Minecraft process has exited")
            with self._state_lock:
                self._selection = _identity(actual)
            return {"selected": True, "target": _identity(actual), "title": actual["title"],
                    "foreground": self.api.foreground_window() == hwnd,
                    "capture_verified": False, "input_verified": False}

    def _validate_selection(self, foreground=False):
        self._check_desktop()
        with self._state_lock:
            target = dict(self._selection) if self._selection else None
        if not target:
            raise BackendError("NO_SELECTION", "List windows and explicitly select the Minecraft window first")
        actual = self.api.window_metadata(target["hwnd"])
        if (actual["pid"] != target["pid"] or actual["creation_time_100ns"] != target["creation_time_100ns"] or
                _canonical_exe(actual["exe"]) != _canonical_exe(target["exe"]) or not _is_minecraft(actual) or
                not actual.get("running", True)):
            raise BackendError("TARGET_CHANGED", "The selected Minecraft identity changed; controls were stopped")
        if not actual.get("visible") or actual.get("minimized"):
            raise BackendError("WINDOW_NOT_VISIBLE", "Restore the Minecraft window before capture or input")
        if foreground and self.api.foreground_window() != target["hwnd"]:
            raise BackendError("NEEDS_FOCUS", "Click the selected Minecraft window to put it in the foreground")
        return actual

    def inspect_selection(self):
        result = {"platform": "windows" if os.name == "nt" else "test-adapter", "stopped": self._stopped.is_set(),
                  "capture_verified": False, "input_verified": False,
                  "capture_backend": "gdi-visible-client-crop", "max_input_duration_ms": 500,
                  "frame_max_age_seconds": 30}
        with self._state_lock:
            result["target"] = dict(self._selection) if self._selection else None
            result["release_pending"] = bool(self._pressed_keys or self._pressed_buttons)
            result["last_release_error"] = self._last_release_error
            result["frame_id"] = self._last_frame["frame_id"] if self._last_frame else None
        try:
            result["desktop"] = self.api.desktop_state()
            if result["target"]:
                current = self._validate_selection()
                result["selection_valid"] = True
                result["foreground"] = self.api.foreground_window() == current["hwnd"]
        except BackendError as exc:
            result["selection_valid"] = False
            result["error"] = exc.to_dict()
        return result

    def focus(self):
        with self._action_lock:
            current = self._validate_selection()
            self.release_all()
            self.api.focus_window(current["hwnd"])
            if self.api.foreground_window() != current["hwnd"]:
                raise BackendError("NEEDS_FOCUS", "Windows declined the focus request. Click the selected Minecraft window, keep it visible, then request a fresh screenshot")
            return {"foreground": True, "target": _identity(current)}

    def capture(self, max_width=1280, max_height=720, max_png_bytes=393216):
        _integer(max_width, "max_width", 16, 1920)
        _integer(max_height, "max_height", 16, 1080)
        _integer(max_png_bytes, "max_png_bytes", 16384, 524288)
        with self._action_lock:
            current = self._validate_selection(foreground=True)
            rect = self.api.client_rect(current["hwnd"])
            scale = min(1.0, max_width / rect["width"], max_height / rect["height"])
            width, height = max(1, int(rect["width"] * scale)), max(1, int(rect["height"] * scale))
            png = None
            for attempt in range(5):
                self._validate_selection(foreground=True)
                data = self.api.capture_bgra(rect, width, height)
                self._validate_selection(foreground=True)
                # If the window moved/resized during capture, never return the
                # former rectangle as though it were a current Minecraft image.
                if self.api.client_rect(current["hwnd"]) != rect:
                    raise BackendError("WINDOW_MOVED", "The Minecraft window moved during capture; retry")
                png = _png_from_bgra(width, height, data)
                if len(png) <= max_png_bytes:
                    break
                width, height = max(1, width * 3 // 4), max(1, height * 3 // 4)
            else:
                raise BackendError("IMAGE_TOO_LARGE", "The screenshot exceeds the configured size limit")
            captured_at = _utc_now()
            frame_id = secrets.token_urlsafe(24)
            cursor = self.api.cursor_position()
            image_cursor = None
            if cursor and (rect["x"] <= cursor["x"] < rect["x"] + rect["width"] and
                           rect["y"] <= cursor["y"] < rect["y"] + rect["height"]):
                image_cursor = {"image_x": min(width - 1, (cursor["x"] - rect["x"]) * width // rect["width"]),
                                "image_y": min(height - 1, (cursor["y"] - rect["y"]) * height // rect["height"]),
                                "visible": bool(cursor.get("visible"))}
            with self._state_lock:
                self._last_frame = {"frame_id": frame_id, "created_monotonic": time.monotonic(),
                                    "target": _identity(current), "client_rect": dict(rect), "width": width, "height": height}
            return {"mime_type": "image/png", "data_base64": base64.b64encode(png).decode("ascii"),
                    "width": width, "height": height, "source_width": rect["width"], "source_height": rect["height"],
                    "client_rect": rect, "target": _identity(current), "png_bytes": len(png),
                    "capture_backend": "gdi-visible-client-crop", "capture_verified": False,
                    "occlusion_possible": True, "captured_at_utc": captured_at,
                    "time": captured_at, "frame_id": frame_id, "frame_max_age_seconds": 30,
                    "cursor": image_cursor}

    def _consume_frame(self, frame_id, target):
        if not isinstance(frame_id, str) or not frame_id or len(frame_id) > 128:
            raise BackendError("FRAME_REQUIRED", "Request a fresh screenshot and pass its frame_id with the action")
        with self._state_lock:
            frame = self._last_frame
            if not frame or not secrets.compare_digest(frame["frame_id"], frame_id):
                raise BackendError("FRAME_REQUIRED", "This frame is unavailable or was already used; request a fresh screenshot")
            if time.monotonic() - frame["created_monotonic"] > 30:
                self._last_frame = None
                raise BackendError("FRAME_EXPIRED", "The screenshot is older than 30 seconds; request a fresh screenshot")
            if frame["target"] != _identity(target) or self.api.client_rect(target["hwnd"]) != frame["client_rect"]:
                self._last_frame = None
                raise BackendError("TARGET_CHANGED", "The Minecraft target or client area changed after the screenshot")
            # Consumed before injection: a failed/partial action cannot replay.
            self._last_frame = None
            return frame

    @staticmethod
    def _names(value, allowed, name):
        if not isinstance(value, (list, tuple)) or len(value) > len(allowed):
            raise BackendError("INVALID_ARGUMENT", "%s must be a bounded list" % name)
        if any(not isinstance(item, str) or item not in allowed for item in value) or len(set(value)) != len(value):
            raise BackendError("INVALID_ARGUMENT", "%s contains an unsupported or duplicate control" % name,
                               {"allowed": sorted(allowed)})
        return tuple(value)

    def play_step(self, frame_id=None, keys=(), mouse_dx=0, mouse_dy=0, buttons=(), duration_ms=100,
                  image_x=None, image_y=None):
        keys = self._names(keys, KEYS, "keys")
        buttons = self._names(buttons, BUTTONS, "buttons")
        _integer(mouse_dx, "mouse_dx", -2000, 2000)
        _integer(mouse_dy, "mouse_dy", -2000, 2000)
        _integer(duration_ms, "duration_ms", 0, 500)
        if image_x is not None or image_y is not None:
            _integer(image_x, "image_x", 0, 1919)
            _integer(image_y, "image_y", 0, 1079)
            if mouse_dx or mouse_dy:
                raise BackendError("INVALID_ARGUMENT", "Image coordinates and relative camera movement cannot be combined")
        with self._action_lock:
            current = self._validate_selection(foreground=True)
            self.release_all()
            frame = self._consume_frame(frame_id, current)
            screen_x = screen_y = None
            if image_x is not None:
                if image_x >= frame["width"] or image_y >= frame["height"]:
                    raise BackendError("INVALID_ARGUMENT", "The image coordinate is outside the last screenshot")
                rect = frame["client_rect"]
                screen_x = rect["x"] + min(rect["width"] - 1, int((image_x + 0.5) * rect["width"] / frame["width"]))
                screen_y = rect["y"] + min(rect["height"] - 1, int((image_y + 0.5) * rect["height"] / frame["height"]))
            # Existing physical modifiers or requested controls can change the
            # meaning of an injected action and must not be released as ours.
            physical_vks = set(MODIFIER_VKS)
            physical_vks.update(KEYS[name][1] for name in keys)
            physical_vks.update(BUTTONS[name][2] for name in buttons)
            if any(self.api.key_is_down(vk) for vk in physical_vks):
                raise BackendError("USER_INPUT_ACTIVE", "Release held keys and mouse buttons before a companion action")
            started = time.monotonic()
            deadline = started + duration_ms / 1000.0
            with self._state_lock:
                self._pressed_keys, self._pressed_buttons = keys, buttons
                self._release_deadline = deadline + 0.075
                self._action_cancel_reason = None
            primary_error = None
            try:
                with self._input_lock:
                    self._validate_selection(foreground=True)
                    if self.api.client_rect(current["hwnd"]) != frame["client_rect"]:
                        raise BackendError("TARGET_CHANGED", "The Minecraft client area moved before input")
                    self.api.send(keys_down=keys, buttons_down=buttons, mouse_dx=mouse_dx, mouse_dy=mouse_dy,
                                  screen_x=screen_x, screen_y=screen_y)
                while time.monotonic() < deadline:
                    if self._stopped.wait(min(0.020, max(0, deadline - time.monotonic()))):
                        raise BackendError("STOPPED", "The local companion was stopped")
                    with self._state_lock:
                        cancel_reason = self._action_cancel_reason
                    if cancel_reason:
                        raise BackendError("ACTION_INTERRUPTED", cancel_reason)
                    self._validate_selection(foreground=True)
                with self._state_lock:
                    cancel_reason = self._action_cancel_reason
                if cancel_reason:
                    raise BackendError("ACTION_INTERRUPTED", cancel_reason)
            except BaseException as exc:
                primary_error = exc
                raise
            finally:
                try:
                    self.release_all()
                except BackendError:
                    if primary_error is None:
                        raise
            return {"completed": True, "target": _identity(current), "keys": list(keys),
                    "buttons": list(buttons), "mouse_dx": mouse_dx, "mouse_dy": mouse_dy,
                    "image_x": image_x, "image_y": image_y,
                    "requested_duration_ms": duration_ms,
                    "frame_id": frame_id,
                    "elapsed_ms": int((time.monotonic() - started) * 1000),
                    "released": True, "input_verified": False}

    def release_all(self):
        with self._input_lock:
            with self._state_lock:
                keys, buttons = self._pressed_keys, self._pressed_buttons
            if not keys and not buttons:
                return {"released": True, "keys": [], "buttons": []}
            try:
                self.api.send(keys_up=keys, buttons_up=buttons)
            except BackendError as exc:
                with self._state_lock:
                    self._last_release_error = exc.to_dict()
                    self._release_deadline = time.monotonic()
                raise
            with self._state_lock:
                self._pressed_keys, self._pressed_buttons = (), ()
                self._release_deadline, self._last_release_error = None, None
            return {"released": True, "keys": list(keys), "buttons": list(buttons)}

    def _watch(self):
        while not self._closed.wait(0.025):
            with self._state_lock:
                deadline, target = self._release_deadline, self._selection
                pressed = bool(self._pressed_keys or self._pressed_buttons)
            if not pressed:
                continue
            try:
                lost_focus = not target or self.api.foreground_window() != target["hwnd"]
                if self._stopped.is_set() or lost_focus or (deadline is not None and time.monotonic() >= deadline):
                    with self._state_lock:
                        self._action_cancel_reason = "Input was released by the local focus/timeout watchdog"
                    self.release_all()
            except Exception as exc:
                with self._state_lock:
                    self._last_release_error = (exc.to_dict() if isinstance(exc, BackendError)
                                                else {"code": "RELEASE_FAILED", "message": str(exc)})

    def stop(self):
        self._stopped.set()
        with self._state_lock:
            self._last_frame = None
        result = {"stopped": True}
        try:
            result.update(self.release_all())
        except BackendError as exc:
            result.update({"released": False, "error": exc.to_dict()})
        self._closed.set()
        if threading.current_thread() is not self._watchdog:
            self._watchdog.join(timeout=0.2)
        return result

    # JSON RPC adapter aliases. Unknown arguments are rejected by the service.
    def status(self):
        return self.inspect_selection()

    def windows(self):
        return self.enumerate_windows()

    def processes(self):
        return self.enumerate_processes()

    def select(self, args):
        if not isinstance(args, dict) or set(args) != {"hwnd", "pid", "creation_time_100ns", "exe"}:
            raise BackendError("INVALID_ARGUMENT", "select requires hwnd, pid, creation_time_100ns and exe")
        return self.select_minecraft(**args)

    def screenshot(self, args=None):
        args = {} if args is None else args
        if not isinstance(args, dict) or set(args) - {"max_width", "max_height", "max_png_bytes"}:
            raise BackendError("INVALID_ARGUMENT", "Unsupported screenshot arguments")
        return self.capture(**args)

    def action(self, args):
        if not isinstance(args, dict) or set(args) - {"frame_id", "keys", "mouse_dx", "mouse_dy", "buttons", "duration_ms", "image_x", "image_y"}:
            raise BackendError("INVALID_ARGUMENT", "Unsupported action arguments")
        return self.play_step(**args)

    def release(self):
        return self.release_all()


def create_backend():
    return WindowsBackend()
