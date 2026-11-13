"""Diagnostic observations only; no real assembly, source, or saved world."""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import check_assembly_status as cs


def sample(pid=101, created="2026-11-10T20:00:00.0000000Z", counters=100):
    return {"status": "ok", "unreadable_python_command_lines": 0,
            "processes": [{"pid": pid, "started_utc": created, "role": "prepare_connected_release",
                           **{key: counters for key in cs.COUNTERS}}]}


class AssemblyStatusTests(unittest.TestCase):
    def test_observed_activity_is_reported_from_counter_increase(self):
        first, second = sample(counters=10), sample(counters=40)
        observed = cs.compare_samples(first, second)
        self.assertEqual("ACTIVITY_OBSERVED", observed["state"])
        self.assertEqual(30, observed["deltas"][0]["read_bytes"])

    def test_zero_deltas_do_not_claim_hang_or_completion(self):
        observed = cs.compare_samples(sample(), sample())
        self.assertEqual("NO_ACTIVITY_IN_SAMPLE", observed["state"])
        self.assertIn("does not establish a hang", cs.MESSAGES[observed["state"]])

    def test_reused_pid_is_not_compared_across_creation_times(self):
        observed = cs.compare_samples(sample(), sample(created="2026-11-10T20:01:00.0000000Z", counters=900))
        self.assertEqual("MATCHING_PROCESS_SET_CHANGED", observed["state"])
        self.assertEqual([], observed["deltas"])
        self.assertEqual([101], observed["new_process_ids"])
        self.assertEqual([101], observed["ended_process_ids"])

    def test_missing_identity_or_invalid_counters_remain_inconclusive(self):
        for altered in (sample(created=None), sample(counters=None), sample(counters=1)):
            with self.subTest(altered=altered):
                other = altered if altered["processes"][0]["started_utc"] is None else sample()
                observed = cs.compare_samples(other, altered)
                self.assertEqual("INCONCLUSIVE_COUNTERS", observed["state"])
                self.assertNotIn("cpu_seconds", observed["deltas"][0] if observed["deltas"] else {})

    def test_unavailable_visibility_does_not_become_absent_process(self):
        empty = {"status": "ok", "unreadable_python_command_lines": 0, "processes": []}
        self.assertEqual("NO_MATCHING_PROCESS", cs.compare_samples(empty, empty)["state"])
        partial = {**empty, "unreadable_python_command_lines": 1}
        self.assertEqual("NO_VISIBLE_MATCH_INSPECTION_PARTIAL", cs.compare_samples(empty, partial)["state"])
        unavailable = {"status": "unavailable", "processes": []}
        self.assertEqual("INSPECTION_UNAVAILABLE", cs.compare_samples(empty, unavailable)["state"])

    def test_query_output_drops_unapproved_fields_and_preserves_missing_counters(self):
        raw = sample()
        raw["CommandLine"] = "DO-NOT-SAVE-PRIVATE-ARGUMENTS"
        raw["processes"][0].update({"CommandLine": "DO-NOT-SAVE-PRIVATE-ARGUMENTS", "read_bytes": ""})
        parsed = cs.parse_sample(json.dumps(raw))
        self.assertNotIn("DO-NOT-SAVE", json.dumps(parsed))
        self.assertIsNone(parsed["processes"][0]["read_bytes"])

    def test_timeout_does_not_report_absent_process(self):
        fixture_path = Path("unused")
        with mock.patch.object(cs.os, "name", "nt"), mock.patch.object(cs.subprocess, "run", side_effect=subprocess.TimeoutExpired("powershell.exe", 12)):
            observed = cs.query_processes(fixture_path)
        self.assertEqual("unavailable", observed["status"])
        self.assertEqual("query_timed_out", observed["reason"])

    def test_receipts_require_matching_contract_and_missing_incomplete_markers(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            handoff = root / "handoff"
            handoff.mkdir()
            source = root / "source"
            source.mkdir()
            export = root / "export"
            candidate = export / cs.CANDIDATE_ID
            preparation = export / (cs.CANDIDATE_ID + "-preparation")
            candidate.mkdir(parents=True)
            preparation.mkdir()
            contract = json.dumps({"candidate_id": cs.CANDIDATE_ID}).encode()
            contract_hash = hashlib.sha256(contract).hexdigest()
            (handoff / "connected-contract.json").write_bytes(contract)
            receipt = {"candidate_id": cs.CANDIDATE_ID, "status": "CANDIDATE_PENDING_ACCEPTANCE",
                       "runtime_acceptance": "NOT_RUN", "contract_sha256": contract_hash}
            (candidate / "ASSEMBLY.json").write_text(json.dumps(receipt), encoding="utf-8")
            (preparation / "PREPARATION.json").write_text(json.dumps(receipt), encoding="utf-8")
            (candidate / "Client-Candidate.zip").write_bytes(b"metadata-only-fixture")
            (candidate / (cs.CANDIDATE_ID + "-server-candidate.zip")).write_bytes(b"metadata-only-fixture")
            with mock.patch.object(cs, "CONTRACT_SHA256", contract_hash):
                observed = cs.collect_paths(handoff, export, source)
                self.assertEqual("SUCCESS_RECEIPTS_PRESENT_NOT_REVERIFIED", observed["completion_observation"])
                (preparation / "INCOMPLETE.json").write_text("{}", encoding="utf-8")
                self.assertEqual("INCOMPLETE_OR_UNAVAILABLE_MARKERS", cs.collect_paths(handoff, export, source)["completion_observation"])
                (preparation / "INCOMPLETE.json").unlink()
                receipt["candidate_id"] = "older-candidate"
                (candidate / "ASSEMBLY.json").write_text(json.dumps(receipt), encoding="utf-8")
                self.assertEqual("COMPLETION_NOT_ESTABLISHED", cs.collect_paths(handoff, export, source)["completion_observation"])

    def test_report_creation_does_not_overwrite_and_is_limited_to_handoff_folder(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaises(ValueError):
                cs.save_report(root, {"fixture": True})
            handoff = root / "Vale-Export" / "vale-038"
            handoff.mkdir(parents=True)
            fixed_uuid = mock.Mock(hex="a" * 32)
            fixed_date = mock.Mock()
            fixed_date.now.return_value.strftime.return_value = "20261110T000000Z"
            with mock.patch.object(cs.uuid, "uuid4", return_value=fixed_uuid), mock.patch.object(cs, "datetime", fixed_date):
                saved = cs.save_report(handoff, {"first": True})
                before = saved.read_bytes()
                with self.assertRaises(FileExistsError):
                    cs.save_report(handoff, {"second": True})
                self.assertEqual(before, saved.read_bytes())

    @unittest.skipUnless(os.name == "nt", "Windows CIM smoke test; fixture only")
    def test_native_windows_query_finds_only_its_temporary_fixture(self):
        # The isolated child is the only process this test starts/stops. Neither
        # production paths nor a real preparation/assembly command are used.
        with tempfile.TemporaryDirectory(prefix="vale status ' ") as temporary:
            handoff = Path(temporary) / "Vale-Export" / "vale-038"
            tool = handoff / "tools" / "prepare_connected_release.py"
            tool.parent.mkdir(parents=True)
            worker_identity = tool.with_suffix('.pid')
            tool.write_text("import hashlib, os, time\nfrom pathlib import Path\n"
                            "Path(__file__).with_suffix('.pid').write_text(str(os.getpid()))\n"
                            "end = time.monotonic() + 45\npayload = b'x' * 1048576\n"
                            "while time.monotonic() < end:\n    hashlib.sha256(payload).digest()\n", encoding="utf-8")
            child = subprocess.Popen([sys.executable, str(tool), "DO-NOT-SAVE-PRIVATE-ARGUMENTS"],
                                     stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                deadline = time.monotonic() + 10
                while not worker_identity.exists() and child.poll() is None and time.monotonic() < deadline:
                    time.sleep(0.05)
                self.assertTrue(worker_identity.is_file(), "Fixture worker did not start")
                # A Windows venv python.exe can be a redirector which remains
                # alive beside the actual interpreter. Both exact-path matches
                # are valid; identify the worker instead of assuming one PID.
                expected_pids = {child.pid, int(worker_identity.read_text())}
                first = cs.query_processes(handoff)
                time.sleep(0.2)
                second = cs.query_processes(handoff)
                self.assertEqual("ok", first["status"], first)
                self.assertEqual("ok", second["status"], second)
                self.assertEqual(expected_pids, {row["pid"] for row in first["processes"]})
                self.assertEqual(expected_pids, {row["pid"] for row in second["processes"]})
                self.assertEqual("ACTIVITY_OBSERVED", cs.compare_samples(first, second)["state"])
                self.assertNotIn("DO-NOT-SAVE-PRIVATE-ARGUMENTS", json.dumps([first, second]))
            finally:
                if child.poll() is None:
                    # Terminate only this fixture's tree, including a venv
                    # interpreter behind the redirector, before removing it.
                    subprocess.run(["taskkill.exe", "/PID", str(child.pid), "/T", "/F"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   timeout=10, check=True)
                child.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()
