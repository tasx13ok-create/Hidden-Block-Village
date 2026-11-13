"""Control-contract checks using an injected Win32 fake, not Windows acceptance."""

import base64
import ctypes
import json
import os
import struct
import subprocess
import sys
import threading
import time
import unittest
from unittest import mock
import zlib

from vale_windows import (BackendError, BITMAPINFOHEADER, HANDLE, INPUT,
                          KEYBDINPUT, MOUSEINPUT, PROCESS_QUERY, PROCESS_RESPONSE_BYTES,
                          Win32API, WindowsBackend, _parse_process_response,
                          _png_from_bgra, _query_processes)


class FakeWin32:
    def __init__(self):
        self.meta = {"hwnd": 101, "pid": 202, "creation_time_100ns": "133778991234567891",
                     "exe": r"C:\Java\bin\javaw.exe", "title": "Minecraft 1.12.2",
                     "class_name": "LWJGL", "visible": True, "minimized": False,
                     "running": True, "minecraft_candidate": True}
        self.foreground = 101
        self.ready = True
        self.allow_focus = True
        self.rect = {"x": 10, "y": 20, "width": 128, "height": 96}
        self.events = []
        self.held = set()
        self.fail_down = None
        self.fail_up = False
        self.noise = False
        self.captures = []
        self.block_checks_after_down = False
        self.release_seen = threading.Event()

    def desktop_state(self):
        if self.block_checks_after_down and any(event[1].get("keys_down") for event in self.events):
            time.sleep(0.3)
        return {"ready": self.ready, "reason": "locked" if not self.ready else None}

    def enumerate_windows(self):
        return [dict(self.meta)]

    def window_metadata(self, hwnd):
        if hwnd != self.meta["hwnd"]:
            raise BackendError("WINDOW_UNAVAILABLE", "closed")
        return dict(self.meta)

    def foreground_window(self):
        return self.foreground

    def focus_window(self, hwnd):
        if self.allow_focus:
            self.foreground = hwnd
        return self.allow_focus

    def client_rect(self, hwnd):
        return dict(self.rect)

    def capture_bgra(self, rect, width, height):
        self.captures.append((width, height))
        return os.urandom(width * height * 4) if self.noise else b"\x10\x20\x30\xff" * width * height

    def key_is_down(self, vk):
        return vk in self.held

    def cursor_position(self):
        return {"x": 42, "y": 44, "visible": True}

    def powershell_executable(self):
        return r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"

    def send(self, **kwargs):
        self.events.append((time.monotonic(), kwargs))
        if kwargs.get("keys_up") or kwargs.get("buttons_up"):
            self.release_seen.set()
            if self.fail_up:
                raise BackendError("INPUT_REJECTED", "release failed")
        if (kwargs.get("keys_down") or kwargs.get("buttons_down")) and self.fail_down:
            raise self.fail_down
        return 1


class BackendContractTests(unittest.TestCase):
    def setUp(self):
        self.api = FakeWin32()
        self.backend = WindowsBackend(api=self.api)
        self.addCleanup(self.backend.stop)
        self.select()

    def select(self):
        return self.backend.select({key: self.api.meta[key] for key in
                                    ("hwnd", "pid", "creation_time_100ns", "exe")})

    def frame(self, **kwargs):
        return self.backend.capture(**kwargs)["frame_id"]

    def assert_code(self, code, function, *args, **kwargs):
        with self.assertRaises(BackendError) as caught:
            function(*args, **kwargs)
        self.assertEqual(code, caught.exception.code)

    def test_win64_input_layout(self):
        pointer_size = ctypes.sizeof(HANDLE)
        self.assertEqual(40 if pointer_size == 8 else 28, ctypes.sizeof(INPUT))
        self.assertEqual(32 if pointer_size == 8 else 24, ctypes.sizeof(MOUSEINPUT))
        self.assertEqual(24 if pointer_size == 8 else 16, ctypes.sizeof(KEYBDINPUT))
        self.assertEqual(8 if pointer_size == 8 else 4, INPUT.u.offset)
        self.assertEqual(40, ctypes.sizeof(BITMAPINFOHEADER))

    def test_native_absolute_move_precedes_click_and_covers_negative_monitor(self):
        captured = []

        class User32:
            @staticmethod
            def GetSystemMetrics(metric):
                return {76: -1920, 77: 0, 78: 3840, 79: 1080}[metric]

            @staticmethod
            def SendInput(count, inputs, size):
                self.assertEqual(ctypes.sizeof(INPUT), size)
                captured.extend((inputs[i].type, inputs[i].mi.dwFlags, inputs[i].mi.dx, inputs[i].mi.dy)
                                for i in range(count))
                return count

        native = Win32API.__new__(Win32API)
        native.user32 = User32()
        with mock.patch.object(ctypes, "set_last_error", create=True):
            native.send(screen_x=-1920, screen_y=0, buttons_down=("left",))
            native.send(screen_x=1919, screen_y=1079)
        self.assertEqual((0, 0xC001, 0, 0), captured[0])
        self.assertEqual((0, 0x0002, 0, 0), captured[1])
        self.assertEqual((0, 0xC001, 65535, 65535), captured[2])

    def test_native_window_read_detects_hwnd_owner_race(self):
        class User32:
            count = 0

            @staticmethod
            def IsWindow(_): return True

            def GetWindowThreadProcessId(self, _, pid):
                self.count += 1
                pid._obj.value = 202 if self.count == 1 else 203
                return 1

            @staticmethod
            def GetWindowTextW(_, buffer, __): buffer.value = "Minecraft"

            @staticmethod
            def GetClassNameW(_, buffer, __): buffer.value = "LWJGL"

            @staticmethod
            def IsWindowVisible(_): return True

            @staticmethod
            def IsIconic(_): return False

        native = Win32API.__new__(Win32API)
        native.user32 = User32()
        native.process_metadata = lambda _: dict(self.api.meta)
        self.assert_code("TARGET_CHANGED", native.window_metadata, 101)

    def test_creation_identity_remains_exact_decimal_string(self):
        record = self.backend.windows()[0]
        self.assertIsInstance(record["creation_time_100ns"], str)
        self.assertEqual("133778991234567891", record["creation_time_100ns"])
        self.assert_code("INVALID_ARGUMENT", self.backend.select_minecraft, 101, 202, 133778991234567891, self.api.meta["exe"])

    def test_reselection_fails_closed_on_pid_or_creation_change(self):
        expected = {key: self.api.meta[key] for key in ("hwnd", "pid", "creation_time_100ns", "exe")}
        self.api.meta["creation_time_100ns"] = "133778991234567892"
        self.assert_code("TARGET_CHANGED", self.backend.select, expected)
        self.assertIsNone(self.backend.status()["target"])

    def test_rejects_non_minecraft_despite_title(self):
        self.api.meta["exe"] = r"C:\Windows\System32\cmd.exe"
        self.assert_code("NOT_MINECRAFT", self.select)

    def test_capture_is_valid_png_and_explicitly_unverified(self):
        frame = self.backend.capture()
        png = base64.b64decode(frame["data_base64"])
        self.assertEqual(b"\x89PNG\r\n\x1a\n", png[:8])
        self.assertEqual((128, 96), struct.unpack(">II", png[16:24]))
        self.assertFalse(frame["capture_verified"])
        self.assertTrue(frame["occlusion_possible"])
        self.assertLessEqual(frame["png_bytes"], 393216)
        self.assertTrue(frame["frame_id"])

    def test_png_channel_order(self):
        png = _png_from_bgra(1, 1, b"\x11\x22\x33\xff")
        start = png.index(b"IDAT")
        length = struct.unpack(">I", png[start - 4:start])[0]
        self.assertEqual(b"\x00\x33\x22\x11", zlib.decompress(png[start + 4:start + 4 + length]))

    def test_capture_downsamples_to_transport_budget(self):
        self.api.noise = True
        frame = self.backend.capture(max_png_bytes=16384)
        self.assertLessEqual(frame["png_bytes"], 16384)
        self.assertGreater(len(self.api.captures), 1)
        self.assertLess(frame["width"], self.api.rect["width"])

    def test_no_input_without_fresh_frame(self):
        self.assert_code("FRAME_REQUIRED", self.backend.play_step, keys=["w"])
        self.assertEqual([], self.api.events)

    def test_frame_is_one_use_and_releases_after_bounded_action(self):
        frame_id = self.frame()
        result = self.backend.play_step(frame_id=frame_id, keys=["w"], buttons=["left"], duration_ms=5)
        self.assertTrue(result["released"])
        self.assertEqual(("w",), self.api.events[0][1]["keys_down"])
        self.assertEqual(("w",), self.api.events[-1][1]["keys_up"])
        self.assertEqual(("left",), self.api.events[-1][1]["buttons_up"])
        self.assert_code("FRAME_REQUIRED", self.backend.play_step, frame_id=frame_id, keys=["w"])

    def test_new_screenshot_invalidates_older_frame(self):
        old = self.frame()
        self.frame()
        self.assert_code("FRAME_REQUIRED", self.backend.play_step, frame_id=old, mouse_dx=5)

    def test_menu_coordinate_maps_from_png_to_source_pixels(self):
        frame = self.backend.capture(max_width=64, max_height=48)
        self.assertEqual({"image_x": 16, "image_y": 12, "visible": True}, frame["cursor"])
        self.backend.play_step(frame_id=frame["frame_id"], image_x=31, image_y=23, buttons=["left"], duration_ms=0)
        self.assertEqual(73, self.api.events[0][1]["screen_x"])
        self.assertEqual(67, self.api.events[0][1]["screen_y"])
        self.assertEqual(("left",), self.api.events[-1][1]["buttons_up"])

    def test_menu_coordinate_requires_pair_and_disallows_relative_motion(self):
        self.assert_code("INVALID_ARGUMENT", self.backend.play_step, image_x=1)
        self.assert_code("INVALID_ARGUMENT", self.backend.play_step, image_x=1, image_y=1, mouse_dx=1)
        frame_id = self.frame()
        self.assert_code("INVALID_ARGUMENT", self.backend.play_step, frame_id=frame_id, image_x=128, image_y=0)
        self.assertEqual([], self.api.events)

    def test_expired_frame_is_rejected(self):
        frame_id = self.frame()
        self.backend._last_frame["created_monotonic"] -= 31
        self.assert_code("FRAME_EXPIRED", self.backend.play_step, frame_id=frame_id, keys=["w"])
        self.assertEqual([], self.api.events)

    def test_frame_cannot_target_moved_window(self):
        frame_id = self.frame()
        self.api.rect["x"] += 50
        self.assert_code("TARGET_CHANGED", self.backend.play_step, frame_id=frame_id, keys=["w"])
        self.assertEqual([], self.api.events)

    def test_frame_cannot_target_reused_pid(self):
        frame_id = self.frame()
        self.api.meta["creation_time_100ns"] = "133778991234567899"
        self.assert_code("TARGET_CHANGED", self.backend.play_step, frame_id=frame_id, keys=["w"])
        self.assertEqual([], self.api.events)

    def test_focus_and_desktop_are_required(self):
        self.api.foreground = 999
        self.assert_code("NEEDS_FOCUS", self.backend.capture)
        self.api.foreground = 101
        self.api.ready = False
        self.assert_code("DESKTOP_UNAVAILABLE", self.backend.capture)

    def test_focus_attempt_honors_windows_denial(self):
        self.api.foreground, self.api.allow_focus = 999, False
        self.assert_code("NEEDS_FOCUS", self.backend.focus)
        self.api.allow_focus = True
        self.assertTrue(self.backend.focus()["foreground"])

    def test_physical_modifier_blocks_injection(self):
        frame_id = self.frame()
        self.api.held.add(0x12)
        self.assert_code("USER_INPUT_ACTIVE", self.backend.play_step, frame_id=frame_id, keys=["w"])
        self.assertEqual([], self.api.events)

    def test_partial_injection_and_keyboard_interrupt_release(self):
        for exception in (BackendError("INPUT_REJECTED", "partial"), KeyboardInterrupt()):
            self.api.events = []
            self.api.fail_down = exception
            frame_id = self.frame()
            with self.assertRaises(type(exception)):
                self.backend.play_step(frame_id=frame_id, keys=["w"], duration_ms=0)
            self.assertEqual(("w",), self.api.events[-1][1]["keys_up"])
            self.assertFalse(self.backend.status()["release_pending"])

    def test_focus_loss_releases_without_waiting_for_network(self):
        frame_id = self.frame()
        timer = threading.Timer(0.035, setattr, args=(self.api, "foreground", 999))
        timer.start()
        self.addCleanup(timer.cancel)
        with self.assertRaises(BackendError):
            self.backend.play_step(frame_id=frame_id, keys=["w"], duration_ms=500)
        self.assertTrue(self.api.release_seen.is_set())
        self.assertLess(self.api.events[-1][0] - self.api.events[0][0], 0.3)

    def test_watchdog_releases_when_action_validation_stalls(self):
        frame_id = self.frame()
        self.api.block_checks_after_down = True
        errors = []

        def run():
            try:
                self.backend.play_step(frame_id=frame_id, keys=["w"], duration_ms=50)
            except BackendError as exc:
                errors.append(exc.code)

        worker = threading.Thread(target=run)
        worker.start()
        self.assertTrue(self.api.release_seen.wait(0.25))
        worker.join(1)
        self.assertFalse(worker.is_alive())
        self.assertLess(self.api.events[-1][0] - self.api.events[0][0], 0.25)
        self.assertEqual(["ACTION_INTERRUPTED"], errors)

    def test_stop_rejects_further_input(self):
        frame_id = self.frame()
        self.backend.stop()
        self.assert_code("STOPPED", self.backend.play_step, frame_id=frame_id, keys=["w"])

    def test_action_bounds_and_unknown_arguments(self):
        self.assert_code("INVALID_ARGUMENT", self.backend.action, {"duration_ms": 501})
        self.assert_code("INVALID_ARGUMENT", self.backend.action, {"keys": ["alt"]})
        self.assert_code("INVALID_ARGUMENT", self.backend.action, {"mouse_dx": True})
        self.assert_code("INVALID_ARGUMENT", self.backend.action, {"command": "anything"})

    @staticmethod
    def process_row(pid=202, name="python.exe", role="assemble_release"):
        return {"pid": pid, "name": name, "role": role, "creation_time_utc": "2026-11-11T12:30:01.1234567Z",
                "kernel_time_100ns": "18446744073709551615", "user_time_100ns": "9007199254740993",
                "read_bytes": "12345678901234567", "write_bytes": "0"}

    def test_process_parser_redacts_unknown_fields_and_preserves_exact_counters(self):
        row = self.process_row()
        row["CommandLine"] = "javaw.exe --accessToken NEVER_RETURN_THIS"
        row["accessToken"] = "NEVER_RETURN_THIS"
        java = self.process_row(203, "javaw.exe", "NEVER_RETURN_THIS")
        java["read_bytes"] = "unreadable"
        raw = json.dumps({"status": "ok", "unreadable_count": 1, "truncated": False,
                          "processes": [row, java], "stderr": "NEVER_RETURN_THIS"}).encode()
        parsed = _parse_process_response(raw)
        self.assertTrue(parsed["available"])
        self.assertNotIn("NEVER_RETURN_THIS", json.dumps(parsed))
        self.assertNotIn("CommandLine", json.dumps(parsed))
        self.assertEqual("18446744073709551615", parsed["processes"][0]["kernel_time_100ns"])
        self.assertEqual("9007199254740993", parsed["processes"][0]["user_time_100ns"])
        self.assertEqual("java", parsed["processes"][1]["role"])
        self.assertIsNone(parsed["processes"][1]["read_bytes"])
        self.assertTrue(parsed["partial"])

    def test_process_parser_enforces_row_and_output_caps_without_false_healthy(self):
        raw = json.dumps({"status": "ok", "unreadable_count": 0, "truncated": False,
                          "processes": [self.process_row(pid=i + 1) for i in range(250)]}).encode()
        parsed = _parse_process_response(raw)
        self.assertEqual(200, len(parsed["processes"]))
        self.assertTrue(parsed["truncated"])
        self.assertTrue(parsed["partial"])
        for invalid in (b"NEVER_RETURN_THIS", b"x" * (PROCESS_RESPONSE_BYTES + 1),
                        json.dumps({"status": "ok", "unreadable_count": 0, "truncated": False,
                                    "processes": [self.process_row(name="cmd.exe")]}).encode()):
            unavailable = _parse_process_response(invalid)
            self.assertFalse(unavailable["available"])
            self.assertEqual("unavailable", unavailable["status"])
            self.assertEqual([], unavailable["processes"])
            self.assertNotIn("NEVER_RETURN_THIS", json.dumps(unavailable))

    def test_process_query_is_fixed_bounded_and_does_not_expose_subprocess_errors(self):
        raw = json.dumps({"status": "ok", "unreadable_count": 0, "truncated": False,
                          "processes": [self.process_row()]}).encode()
        runner = mock.Mock(return_value=subprocess.CompletedProcess([], 0, stdout=raw))
        with mock.patch("vale_windows.subprocess.run", runner):
            result = self.backend.processes()
        self.assertTrue(result["available"])
        args, kwargs = runner.call_args
        self.assertEqual(self.api.powershell_executable(), args[0][0])
        self.assertEqual(["-NoLogo", "-NoProfile", "-NonInteractive", "-Command", PROCESS_QUERY], args[0][1:])
        self.assertFalse(kwargs["shell"])
        self.assertEqual(8, kwargs["timeout"])
        self.assertEqual(subprocess.DEVNULL, kwargs["stderr"])
        runner.side_effect = subprocess.TimeoutExpired("NEVER_RETURN_THIS", 8, output=b"NEVER_RETURN_THIS")
        timed_out = _query_processes(self.api, runner=runner)
        self.assertFalse(timed_out["available"])
        self.assertEqual("PROCESS_QUERY_TIMED_OUT", timed_out["error"]["code"])
        self.assertNotIn("NEVER_RETURN_THIS", json.dumps(timed_out))


@unittest.skipUnless(os.name == "nt", "Windows-only read-only ABI smoke; no gameplay acceptance")
class WindowsNoninteractiveCISmokeTests(unittest.TestCase):
    """Real DLL calls suitable for CI: never capture, focus, or inject input."""

    @classmethod
    def setUpClass(cls):
        cls.api = Win32API()

    def test_noninteractive_ci_initializes_actual_win32_signatures(self):
        self.assertEqual(os.getpid(), self.api.kernel32.GetCurrentProcessId())
        self.assertEqual(ctypes.c_void_p, self.api.user32.GetForegroundWindow.restype)
        self.assertEqual(ctypes.c_void_p, self.api.gdi32.CreateCompatibleDC.restype)
        self.assertEqual(40 if ctypes.sizeof(HANDLE) == 8 else 28, ctypes.sizeof(INPUT))

    def test_noninteractive_ci_reads_own_process_identity_only(self):
        info = self.api.process_metadata(os.getpid())
        repeated = self.api.process_metadata(os.getpid())
        self.assertEqual(os.getpid(), info["pid"])
        self.assertTrue(info["running"])
        self.assertRegex(info["creation_time_100ns"], r"^[0-9]{1,20}$")
        self.assertGreater(int(info["creation_time_100ns"]), 0)
        self.assertEqual(info["creation_time_100ns"], repeated["creation_time_100ns"])
        self.assertTrue(os.path.isabs(info["exe"]))
        # Store aliases and venv redirectors can give sys.executable a
        # different name from the running image. Cross-check the loaded
        # executable through a separate Win32 API, including drive aliases.
        module_name = ctypes.WinDLL("kernel32", use_last_error=True).GetModuleFileNameW
        module_name.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_uint32]
        module_name.restype = ctypes.c_uint32
        buffer = ctypes.create_unicode_buffer(32768)
        length = module_name(None, buffer, len(buffer))
        self.assertGreater(length, 0)
        self.assertLess(length, len(buffer))
        self.assertTrue(os.path.samefile(buffer.value, info["exe"]))

    def test_noninteractive_ci_reports_desktop_without_requiring_unlocked_session(self):
        state = self.api.desktop_state()
        self.assertIsInstance(state["ready"], bool)
        self.assertIsInstance(state["session_id"], int)
        self.assertIsInstance(state["active_console_session_id"], int)
        if not state["ready"]:
            self.assertTrue(state.get("reason"))

    def test_noninteractive_ci_enumerates_without_focusing_or_controlling_windows(self):
        windows = self.api.enumerate_windows()
        self.assertIsInstance(windows, list)
        self.assertLessEqual(len(windows), 512)
        for window in windows:
            self.assertIsInstance(window["hwnd"], int)
            self.assertGreater(window["pid"], 0)
            self.assertIsInstance(window["title"], str)
            self.assertRegex(window["creation_time_100ns"], r"^[0-9]{1,20}$")

    def test_noninteractive_ci_reads_filtered_processes_or_reports_unavailable(self):
        result = _query_processes(self.api)
        self.assertIsInstance(result["available"], bool)
        self.assertLessEqual(len(result["processes"]), 200)
        self.assertNotIn("CommandLine", json.dumps(result))
        if result["available"]:
            self.assertEqual("ok", result["status"])
            for row in result["processes"]:
                self.assertRegex(row["name"].lower(), r"^(?:python[a-z0-9_.-]*|py|java|javaw)\.exe$")
                self.assertIsInstance(row["pid"], int)
                for key in ("kernel_time_100ns", "user_time_100ns", "read_bytes", "write_bytes"):
                    self.assertTrue(row[key] is None or isinstance(row[key], str))
        else:
            self.assertEqual("unavailable", result["status"])
            self.assertEqual([], result["processes"])
            self.assertTrue(result["partial"])
            self.assertTrue(result["error"]["code"])


if __name__ == "__main__":
    unittest.main()
