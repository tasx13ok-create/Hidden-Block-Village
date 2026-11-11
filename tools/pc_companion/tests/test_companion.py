import base64
import concurrent.futures
import hashlib
import http.client
import json
import os
import pathlib
import sys
import tempfile
import threading
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import vale_companion as vc


class FakeBackend:
    def __init__(self):
        self.actions = []

    def status(self):
        return {"supported": False, "fixture": True}

    def windows(self):
        return [{"hwnd": 123, "pid": 456, "creation_time_100ns": "123456789012345678", "exe": "C:\\Java\\javaw.exe"}]

    def processes(self):
        return {"processes": [{"pid": 456, "role": "minecraft_client", "cpu_seconds": 1.0}], "read_only": True}

    def select(self, args):
        return {"selected": args}

    def screenshot(self, args):
        return {"mime_type": "image/png", "data_base64": "fixture", "capture_verified": False, "frame_id": "frame-1"}

    def action(self, args):
        self.actions.append(args)
        return {"released": True}

    def release(self):
        return {"released": True}

    def focus(self):
        return {"focused": True}

    def stop(self):
        return {"stopped": True}


class Fixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name) / "granted"
        self.root.mkdir()
        self.grant = vc.RootGrant(self.root, writable=True, root_id="root-1")
        self.files = vc.FileAccess([self.grant])
        self.backend = FakeBackend()
        self.companion = vc.Companion(self.backend, [self.grant], token="test-session-token")

    def tearDown(self):
        self.temp.cleanup()

    def args(self, path, **kwargs):
        return dict(root_id="root-1", path=path, **kwargs)

    def assert_code(self, expected, function, *args):
        with self.assertRaises(vc.CompanionError) as caught:
            function(*args)
        self.assertEqual(caught.exception.code, expected)


class FileGuards(Fixture):
    def test_root_grants_are_bounded_and_unique(self):
        duplicate = vc.RootGrant(self.root, root_id="duplicate-path")
        self.assert_code("DUPLICATE_ROOT", vc.Companion, self.backend, [self.grant, duplicate])
        grants = []
        for number in range(9):
            folder = pathlib.Path(self.temp.name) / ("root-" + str(number))
            folder.mkdir()
            grants.append(vc.RootGrant(folder))
        self.assertEqual(len(vc.Companion(self.backend, grants[:8]).files.grants), 8)
        self.assert_code("ROOT_LIMIT", vc.Companion, self.backend, grants)

    def test_default_grant_is_read_only(self):
        files = vc.FileAccess([vc.RootGrant(self.root, root_id="readonly")])
        self.assert_code("READ_ONLY", files.create_text, {"root_id": "readonly", "path": "Vale-Export/a.txt", "text": "x"})

    def test_output_create_never_resumes(self):
        args = self.args("Vale-Export/candidate/a.txt", text="first")
        result = self.files.create_text(args)
        self.assertEqual(result["sha256"], hashlib.sha256(b"first").hexdigest())
        with self.assertRaises(FileExistsError):
            self.files.create_text(dict(args, text="second"))
        self.assertEqual((self.root / args["path"]).read_bytes(), b"first")

    def test_replacement_requires_current_hash_and_preserves_backup(self):
        args = self.args("Vale-Export/config.json", text='{"version":1}\n')
        created = self.files.create_text(args)
        target = self.root / args["path"]
        self.assert_code("HASH_REQUIRED", self.files.replace_text, dict(args, text="changed"))
        self.assert_code("SOURCE_CHANGED", self.files.replace_text, dict(args, text="changed", expected_sha256="0" * 64))
        self.assertEqual(target.read_text(), '{"version":1}\n')
        result = self.files.replace_text(dict(args, text='{"version":2}\n', expected_sha256=created["sha256"]))
        self.assertEqual(target.read_text(), '{"version":2}\n')
        backup = self.root / result["backup_path"]
        self.assertEqual(backup.read_text(), '{"version":1}\n')
        self.assertEqual(self.files.info(self.args(result["backup_path"]))["sha256"], created["sha256"])
        self.assert_code("RESERVED_PATH", self.files.replace_text, self.args(result["backup_path"], text="overwrite", expected_sha256=created["sha256"]))

    def test_source_change_during_prepare_keeps_pending_and_backup(self):
        args = self.args("Vale-Export/config.txt", text="original")
        created = self.files.create_text(args)
        target = self.root / args["path"]
        real_hash = vc.stable_hash
        count = [0]

        def mutate_before_second_hash(path):
            count[0] += 1
            if count[0] == 2:
                target.write_text("external writer", encoding="utf-8")
            return real_hash(path)

        with mock.patch.object(vc, "stable_hash", side_effect=mutate_before_second_hash):
            self.assert_code("SOURCE_CHANGED", self.files.replace_text, dict(args, text="proposed", expected_sha256=created["sha256"]))
        self.assertEqual(target.read_text(), "external writer")
        backups = list(target.parent.glob(".vale-backups/*.bak"))
        pending = list(target.parent.glob(".vale-pending-*"))
        self.assertEqual([p.read_text() for p in backups], ["original"])
        self.assertEqual([p.read_text() for p in pending], ["proposed"])

    def test_rejects_windows_path_escape_and_special_names(self):
        paths = ["../secret", "Vale-Export/../../secret", "D:/secret", "D:\\secret", "\\\\server\\share", "/etc/passwd",
                 "Vale-Export/a:stream", "Vale-Export/CON", "Vale-Export/NUL.txt", "Vale-Export/COM1.log", "Vale-Export/x.",
                 "Vale-Export/x ", "Vale-Export//x", "Vale-Export/./x", "Vale-Export/a\x00b"]
        for path in paths:
            with self.subTest(path=path):
                self.assert_code("BAD_PATH", self.files.create_text, self.args(path, text="x"))
        self.assert_code("WRITE_SCOPE", self.files.create_text, self.args("actual-world/config.txt", text="x"))

    def test_matches_file_link_text_and_secret_path_policy(self):
        for path in [".git/config", ".ssh/config", ".aws/credentials", ".azure/token.json", ".gnupg/config",
                     "Vale-Export/.env", "Vale-Export/.env.local", "Vale-Export/id_rsa", "Vale-Export/cert.pem",
                     "Vale-File-Link-Backups/original.txt"]:
            with self.subTest(path=path):
                self.assert_code("EXCLUDED_PATH", self.files.info, self.args(path))
        self.assert_code("BAD_PATH", self.files.create_text, self.args("Vale-Export\\safe.txt", text="x"))
        self.assert_code("NOT_TEXT", self.files.create_text, self.args("Vale-Export/client.jar", text="x"))
        self.assert_code("BAD_ARGUMENT", self.files.create_text, self.args("Vale-Export/a.txt", text="x\0y"))
        self.assert_code("FILE_LIMIT", self.files.create_text, self.args("Vale-Export/a.txt", text="x" * 131073))
        self.assert_code("WRITE_SCOPE", self.files.create_text, self.args("vale-export/a.txt", text="x"))

    def test_directory_scan_is_bounded_and_truncation_is_explicit(self):
        for number in range(5):
            (self.root / (str(number) + ".txt")).write_text(str(number))
        with mock.patch.object(vc, "MAX_DIRECTORY_ENTRIES", 3):
            result = self.files.list(self.args("", limit=10))
        self.assertEqual(result["scanned"], 3)
        self.assertTrue(result["truncated"])
        self.assertIsNone(result["total"])
        self.assertEqual(len(result["entries"]), 3)

    def test_swap_between_path_inspection_and_open_is_rejected(self):
        # Windows TEMP may use an 8.3 alias (RUNNER~1), while RootGrant uses
        # the resolved long path. Inject at the same canonical path used by
        # read_range so the mock actually swaps the file on every platform.
        target = (self.root / "swap.txt").resolve()
        replacement = (self.root / "replacement.txt").resolve()
        original_open = vc.os.open
        operations = [("stable_hash", lambda: vc.stable_hash(target)),
                      ("read_range", lambda: self.files.read_range(self.args("swap.txt")))]
        for name, operation in operations:
            with self.subTest(operation=name):
                target.write_text("old")
                replacement.write_text("replacement")
                swapped = [False]

                def swap_then_open(path, flags, *args, **kwargs):
                    if str(path) == str(target) and not swapped[0]:
                        swapped[0] = True
                        os.replace(str(replacement), str(target))
                    return original_open(path, flags, *args, **kwargs)

                with mock.patch.object(vc.os, "open", side_effect=swap_then_open):
                    try:
                        self.assert_code("SOURCE_CHANGED", operation)
                    finally:
                        self.assertTrue(swapped[0], "The fixture must swap the file before this operation opens it")
                self.assertEqual(target.read_text(), "replacement")

    def test_windows_birthtime_identity_does_not_confuse_legacy_ctime(self):
        class Entry:
            st_dev, st_ino, st_size, st_mtime_ns, st_birthtime_ns = (1, 2, 3, 4, 5)
            st_ctime_ns = 6
        first, second = Entry(), Entry()
        second.st_ctime_ns = 99
        with mock.patch.object(vc.os, "name", "nt"):
            self.assertEqual(vc.identity(first), vc.identity(second))
            second.st_birthtime_ns = 7
            self.assertNotEqual(vc.identity(first), vc.identity(second))
        second.st_birthtime_ns = 5
        with mock.patch.object(vc.os, "name", "posix"):
            self.assertNotEqual(vc.identity(first), vc.identity(second))

    @unittest.skipUnless(hasattr(os, "symlink"), "Symlinks unavailable")
    def test_never_follows_symlink_outside_root(self):
        outside = pathlib.Path(self.temp.name) / "outside"
        outside.mkdir()
        (outside / "secret.txt").write_text("secret")
        (self.root / "Vale-Export").mkdir()
        try:
            os.symlink(str(outside), str(self.root / "Vale-Export" / "junction"), target_is_directory=True)
        except OSError:
            self.skipTest("OS does not permit symlink creation")
        for operation, args in [(self.files.read_range, self.args("Vale-Export/junction/secret.txt")),
                                (self.files.create_text, self.args("Vale-Export/junction/new.txt", text="x"))]:
            self.assert_code("REPARSE_POINT", operation, args)
        self.assertFalse((outside / "new.txt").exists())

    def test_replaced_root_is_not_an_implicit_new_grant(self):
        moved = self.root.with_name("original-root")
        self.root.rename(moved)
        self.root.mkdir()
        self.assert_code("ROOT_CHANGED", self.files.list, self.args(""))

    def test_read_ranges_are_binary_safe_bounded_and_report_eof(self):
        data = bytes(range(256)) * 1000
        (self.root / "binary.dat").write_bytes(data)
        result = self.files.read_range(self.args("binary.dat", offset=7, length=97))
        self.assertEqual(base64.b64decode(result["data_base64"]), data[7:104])
        self.assertEqual(result["range_sha256"], hashlib.sha256(data[7:104]).hexdigest())
        self.assertFalse(result["eof"])
        result = self.files.read_range(self.args("binary.dat", offset=len(data), length=1))
        self.assertTrue(result["eof"])
        self.assertEqual(result["length"], 0)
        self.assert_code("BAD_ARGUMENT", self.files.read_range, self.args("binary.dat", length=65537))


class ReplayGuards(Fixture):
    def test_queued_mutation_expires_before_dispatch_and_failure_is_cached(self):
        now = [100.0]
        entered = threading.Event()

        def clock():
            value = now[0]
            entered.set()
            return value

        self.companion.clock = clock
        payload = {"id": "expired-create", "op": "create_text", "args": self.args("Vale-Export/late.txt", text="must not be written"), "timeout_ms": 100}
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            with self.companion.lock:
                pending = pool.submit(self.companion.rpc, payload)
                self.assertTrue(entered.wait(1), "RPC did not record its deadline before waiting for the lock")
                now[0] = 100.101
            result = pending.result(timeout=1)
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "REQUEST_EXPIRED")
        self.assertFalse((self.root / "Vale-Export/late.txt").exists())
        self.assertEqual(self.companion.rpc(payload), result)
        self.assertFalse((self.root / "Vale-Export/late.txt").exists())

    def test_known_receipt_survives_expired_retry_budget_without_reexecution(self):
        now = [100.0]
        self.companion.clock = lambda: now[0]
        payload = {"id": "known-action", "op": "action", "args": {"frame_id": "fresh", "keys": ["w"], "duration_ms": 100}, "timeout_ms": 100}
        first = self.companion.rpc(payload)
        entered = threading.Event()

        def clock():
            value = now[0]
            entered.set()
            return value

        self.companion.clock = clock
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            with self.companion.lock:
                pending = pool.submit(self.companion.rpc, payload)
                self.assertTrue(entered.wait(1))
                now[0] = 100.101
            self.assertEqual(pending.result(timeout=1), first)
        self.assertEqual(len(self.backend.actions), 1)

    def test_timeout_is_bounded_and_part_of_replay_identity(self):
        payload = {"id": "timeout", "op": "status", "args": {}}
        for value in [True, 0, -1, 12001, 1.5, "100"]:
            with self.subTest(value=value):
                self.assert_code("BAD_ARGUMENT", self.companion.rpc, dict(payload, timeout_ms=value))
        first = self.companion.rpc(payload)
        self.assertEqual(first, self.companion.rpc(dict(payload, timeout_ms=12000)))
        self.assert_code("REPLAY_CONFLICT", self.companion.rpc, dict(payload, timeout_ms=100))

    def test_concurrent_duplicate_actions_execute_once(self):
        payload = {"id": "action-once", "op": "action", "args": {"frame_id": "fresh", "keys": ["W"], "duration_ms": 100}}
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda _: self.companion.rpc(payload), range(8)))
        self.assertEqual(len(self.backend.actions), 1)
        self.assertTrue(all(result == results[0] for result in results))
        self.assert_code("REPLAY_CONFLICT", self.companion.rpc, dict(payload, args={"keys": ["A"]}))

    def test_cached_failure_is_not_retried_against_changed_state(self):
        payload = {"id": "created-later", "op": "info", "args": self.args("new.txt")}
        first = self.companion.rpc(payload)
        (self.root / "new.txt").write_text("created")
        self.assertEqual(first, self.companion.rpc(payload))
        self.assertFalse(first["ok"])
        self.assertTrue(self.companion.rpc(dict(payload, id="fresh-id"))["ok"])

    def test_ttl_is_bounded_and_large_results_get_tombstones(self):
        now = [1000.0]
        self.companion.clock = lambda: now[0]
        payload = {"id": "status-once", "op": "status", "args": {}}
        self.companion.rpc(payload)
        now[0] += vc.REPLAY_TTL_SECONDS + 1
        self.companion.rpc({"id": "new-status", "op": "status", "args": {}})
        self.assertNotIn("status-once", self.companion.replays)
        self.backend.screenshot = lambda args: {"data_base64": "A" * 20000}
        with mock.patch.object(vc, "MAX_REPLAY_BYTES", 1000):
            self.companion.rpc({"id": "large", "op": "screenshot", "args": {}})
            self.assert_code("REPLAY_RESULT_EXPIRED", self.companion.rpc, {"id": "large", "op": "screenshot", "args": {}})


class NamedLaunchGuards(Fixture):
    def test_fixed_script_requires_hash_and_changed_input_cannot_launch(self):
        executable = self.root / "launcher.exe"
        script = self.root / "reviewed.py"
        executable.write_bytes(b"test executable; never executed")
        script.write_text("print('reviewed')\n")
        item = {"id": "reviewed-launch", "executable": str(executable), "cwd": str(self.root), "argv": [str(script)],
                "sha256": hashlib.sha256(executable.read_bytes()).hexdigest()}
        self.assert_code("BAD_ACTION", vc.NamedActions, [item])
        item["required_files"] = [{"path": str(script), "sha256": hashlib.sha256(script.read_bytes()).hexdigest()}]
        actions = vc.NamedActions([item])
        script.write_text("print('changed')\n")
        with mock.patch.object(vc.subprocess, "Popen") as launch:
            self.assert_code("SOURCE_CHANGED", actions.launch, {"action_id": "reviewed-launch"})
            launch.assert_not_called()

    def test_remote_cannot_change_fixed_arguments_or_launch_twice(self):
        executable = self.root / "launcher.exe"
        executable.write_bytes(b"test executable; never executed")
        item = {"id": "launcher", "executable": str(executable), "cwd": str(self.root), "argv": ["reviewed-profile"],
                "sha256": hashlib.sha256(executable.read_bytes()).hexdigest()}
        actions = vc.NamedActions([item])
        self.assert_code("UNKNOWN_ACTION", actions.launch, {"action_id": "launcher", "argv": ["override"]})
        fake_process = mock.Mock(pid=777)
        fake_process.poll.return_value = None
        with mock.patch.object(vc.subprocess, "Popen", return_value=fake_process) as launch:
            actions.launch({"action_id": "launcher"})
            repeated = actions.launch({"action_id": "launcher"})
            self.assertTrue(repeated["already_running"])
            self.assertEqual(launch.call_count, 1)
            self.assertEqual(launch.call_args.args[0], [str(executable.resolve(strict=True)), "reviewed-profile"])
            self.assertIs(launch.call_args.kwargs["shell"], False)


class HttpGuards(Fixture):
    def setUp(self):
        super().setUp()
        self.server = vc.CompanionServer(self.companion)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        super().tearDown()

    def request(self, method="POST", path="/rpc", body=None, **overrides):
        if body is None:
            body = json.dumps({"id": "request-1", "op": "status", "args": {}})
        headers = {"Origin": vc.APP_ORIGIN, "Authorization": "Bearer test-session-token", "Content-Type": "application/json"}
        headers.update(overrides)
        headers = {k: v for k, v in headers.items() if v is not None}
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        connection.request(method, path, body, headers)
        response = connection.getresponse()
        result = (response.status, dict(response.getheaders()), response.read())
        connection.close()
        return result

    def test_only_bound_loopback_and_authenticated_origin_can_read_status(self):
        self.assertEqual(self.server.server_address[0], "127.0.0.1")
        status, headers, body = self.request()
        self.assertEqual(status, 200)
        self.assertEqual(headers["Access-Control-Allow-Origin"], vc.APP_ORIGIN)
        self.assertEqual(headers["Cache-Control"], "no-store")
        result = json.loads(body)["result"]
        self.assertEqual(result["protocol"], 1)
        self.assertIn("instance_id", result)
        self.assertNotIn("test-session-token", body.decode())
        for origin in [None, "null", "https://evil.example", vc.APP_ORIGIN + ".evil.example"]:
            with self.subTest(origin=origin):
                status, headers, _ = self.request(Origin=origin)
                self.assertEqual(status, 403)
                self.assertNotIn("Access-Control-Allow-Origin", headers)
        self.assertEqual(self.request(Authorization="Bearer wrong")[0], 401)
        self.assertEqual(self.request(Authorization=None)[0], 401)
        self.assertEqual(self.request(Host="attacker.example")[0], 403)

    def test_explicit_preflight_with_private_network_support(self):
        status, headers, _ = self.request("OPTIONS", body="", Authorization=None,
                                        **{"Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "authorization, content-type",
                                           "Access-Control-Request-Private-Network": "true"})
        self.assertEqual(status, 204)
        self.assertEqual(headers["Access-Control-Allow-Private-Network"], "true")
        self.assertNotIn("Access-Control-Allow-Credentials", headers)
        self.assertEqual(self.request("OPTIONS", body="", **{"Access-Control-Request-Method": "DELETE"})[0], 403)
        self.assertEqual(self.request("OPTIONS", body="", **{"Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "x-arbitrary"})[0], 403)

    def test_malformed_oversized_and_non_json_requests_do_not_dispatch(self):
        for body in ['{"id":"a","id":"b","op":"action","args":{}}', '{"id":"a","op":"action","args":{"x":NaN}}', '[]', '{}', '{bad']:
            with self.subTest(body=body):
                self.assertEqual(self.request(body=body)[0], 400)
        self.assertEqual(self.request(**{"Content-Type": "text/plain"})[0], 415)
        self.assertEqual(self.request(body="x", **{"Content-Length": str(vc.MAX_REQUEST_BYTES + 1)})[0], 413)
        self.assertEqual(self.request(body="x", **{"Transfer-Encoding": "chunked"})[0], 400)
        self.assertEqual(self.request("GET", path="/secrets", body="")[0], 405)
        self.assertEqual(self.request(path="/rpc?token=ignored")[0], 404)
        self.assertEqual(self.backend.actions, [])

    def test_remote_cannot_register_or_execute_arbitrary_processes(self):
        for op, args in [("shell", {"command": "whoami"}), ("register", {"path": "C:/"}),
                         ("launch", {"action_id": "nonexistent", "argv": ["injected"]})]:
            status, _, body = self.request(body=json.dumps({"id": "blocked-" + op, "op": op, "args": args}))
            self.assertEqual(status, 200)
            self.assertFalse(json.loads(body)["ok"])

    def test_read_only_process_query_accepts_no_query_or_command_arguments(self):
        status, _, body = self.request(body=json.dumps({"id": "process-info", "op": "processes", "args": {}}))
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(body)["ok"])
        self.assertTrue(json.loads(body)["result"]["read_only"])
        with mock.patch.object(self.backend, "processes") as query:
            status, _, body = self.request(body=json.dumps({"id": "process-injection", "op": "processes", "args": {"command": "not accepted"}}))
            self.assertEqual(json.loads(body)["error"]["code"], "BAD_ARGUMENT")
            query.assert_not_called()


if __name__ == "__main__":
    unittest.main()
