"""Synthetic stdlib checks for inactive bridge proposals; no runtime starts."""

import csv
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOL_PATH = Path(__file__).resolve().parents[1] / "propose_moves.py"
SPEC = importlib.util.spec_from_file_location("propose_moves", TOOL_PATH)
tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tool)
ARSENAL_SOURCE = Path(os.environ.get("HBV_ARSENAL_SOURCE", tool.DEFAULT_ARSENAL))


class ProposalChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Check the owning source, not a duplicated guessed key catalog.
        cls.keys = tool.load_arsenal_keys(ARSENAL_SOURCE)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.src = self.root / "synthetic-registry.json"
        self.out = self.root / "candidate"

    def input(self, data):
        self.src.write_text(json.dumps(data), encoding="utf-8")

    def generate(self, data, out=None):
        self.input(data)
        return tool.generate(self.src, out or self.out, ARSENAL_SOURCE)

    def rendered(self, data):
        return tool.build_candidates(tool.validate_registry(data), self.keys)

    def test_current_arsenal_rules_and_first_match(self):
        styles = tool.validate_rules(self.keys)
        self.assertIn("weapon:mist_censer", styles)
        self.assertEqual(tool.propose("sample:rasenshuriken_fire"), ("spiral", "rasenshuriken"))
        for keyword, style in tool.RULES:
            self.assertIn(style, styles, keyword)
        with self.assertRaisesRegex(tool.ProposalError, "ArsenalSpec"):
            tool.validate_rules(self.keys - {"mist_censer"})

    def test_schema_preserves_same_id_in_different_kinds(self):
        result = self.generate({"items": ["sample:rasengan"], "entities": ["sample:rasengan"]})
        doc = json.loads((self.out / tool.PROPOSAL_NAME).read_bytes())
        self.assertEqual(set(doc), {"schema", "verified", "moves"})
        self.assertEqual(type(doc["schema"]), int)
        self.assertEqual(doc["schema"], 1)
        self.assertIs(doc["verified"], False)
        self.assertEqual(result["proposed"], 2)
        for row, kind, when, owner in zip(doc["moves"], ("entity", "item"),
                                         ("damage", "melee"), ("damage_source", "direct_player")):
            self.assertEqual(set(row), {"kind", "id", "when", "owner", "style"})
            self.assertEqual((row["kind"], row["id"], row["when"], row["owner"], row["style"]),
                             (kind, "sample:rasengan", when, owner, "spiral"))
        self.assertFalse((self.out / "bridge-moves.json").exists())

    def test_deterministic_bytes_across_input_order(self):
        a = self.rendered({"items": ["sample:kunai", "sample:fire"], "entities": ["sample:water", "sample:chidori"]})
        b = self.rendered({"entities": ["sample:chidori", "sample:water"], "items": ["sample:fire", "sample:kunai"]})
        self.assertEqual(a, b)
        self.assertTrue(a[0].endswith(b"\n"))
        self.assertNotIn(b"\r", a[0] + a[1])

    def test_semantic_skip_boundaries(self):
        for path in ("forest_fire", "fire_dialog", "wind_geology", "water_foodless"):
            self.assertIsNotNone(tool.propose("sample:" + path)[0], path)
        for path in ("fire_ore", "fire.ore", "fire/log", "fire-block", "water_spawn_egg", "wind_scroll_empty", "fire_helmet", "fire_eye"):
            self.assertEqual(tool.propose("sample:" + path), (None, "skip"), path)
        for hint in tool.SKIP:
            self.assertEqual(tool.propose("sample:fire_" + hint.strip("_")), (None, "skip"), hint)

    def test_review_includes_nonproposals_and_limitations(self):
        _, review, proposed, total = self.rendered({"entities": ["sample:rasengan"],
            "items": ["sample:fire_ore", "sample:unknown", "minecraft:fire"]})
        rows = list(csv.DictReader(io.StringIO(review.decode("utf-8"))))
        self.assertEqual((proposed, total), (1, 4))
        self.assertEqual({r["matched"] for r in rows}, {"rasengan", "skip", "", "minecraft"})
        self.assertTrue(all(r["verified"] == "false" for r in rows))
        self.assertTrue(all("jutsu cast" in r["review_limit"] for r in rows))
        self.assertTrue(all(r["when"] != "spawn" for r in rows))

    def test_csv_formula_safety_keeps_json_id_exact(self):
        proposal, review, _, _ = self.rendered({"items": ["-sample:kunai"]})
        self.assertEqual(json.loads(proposal)["moves"][0]["id"], "-sample:kunai")
        rows = list(csv.DictReader(io.StringIO(review.decode("utf-8"))))
        self.assertEqual(rows[0]["registry_id"], "'-sample:kunai")
        for value in ("=1", "+1", "-1", "@SUM(1)", "\t1", "\r1", "\n1"):
            self.assertEqual(tool.csv_cell(value), "'" + value)

    def test_optional_empty_lists(self):
        self.assertEqual(json.loads(self.rendered({})[0]), {"schema": 1, "verified": False, "moves": []})

    def test_rejects_all_invalid_id_shapes(self):
        valid = ["a:b", "-a:b", "a" * 31 + ":" + "b" * 96, "a_.-:b/c.d-e_f"]
        for rid in valid:
            self.assertEqual(tool.validate_id(rid), rid)
        bad = [None, True, 3, [], {}, "", "a", "a:b:c", ":b", "a:", "A:b", "a:B", "a:b c", "a:b\n", "a:b\x00", "é:b", "a:é", "a/:b", "a" * 65 + ":b", "a:" + "b" * 97, "a" * 32 + ":" + "b" * 96]
        for rid in bad:
            with self.subTest(rid=rid), self.assertRaises(tool.ProposalError):
                tool.validate_registry({"items": [rid]})

    def test_rejects_unknown_keys_lists_types_and_same_kind_duplicates(self):
        bad = [[], None, True, {"items": "sample:fire"}, {"entities": {}},
               {"items": None}, {"extra": []}, {"items": ["sample:fire", "sample:fire"]},
               {"entities": ["sample:fire", "sample:fire"]}, {"items": ["minecraft:Bad"]}]
        for data in bad:
            with self.subTest(data=data), self.assertRaises(tool.ProposalError):
                self.generate(data)
            self.assertFalse(self.out.exists())

    def test_rejects_duplicate_keys_nonfinite_and_broken_json(self):
        bad = [b'{"items":[],"items":[]}', b'{"items":[NaN]}', b'{"items":[Infinity]}',
               b'{"items":[-Infinity]}', b'{"items":[{"x":1,"x":2}]}', b'{} trailing',
               b'\xff', b'\xef\xbb\xbf{}', (b'[' * 2000 + b']' * 2000),
               (b'{"items":[' + b'9' * 5000 + b']}')]
        for raw in bad:
            self.src.write_bytes(raw)
            with self.subTest(raw=raw[:40]), self.assertRaises(tool.ProposalError):
                tool.generate(self.src, self.out, ARSENAL_SOURCE)
            self.assertFalse(self.out.exists())

    def test_input_byte_and_list_bounds(self):
        self.src.write_bytes(b" " * (tool.MAX_INPUT_BYTES + 1))
        with self.assertRaisesRegex(tool.ProposalError, "bytes"):
            tool.generate(self.src, self.out, ARSENAL_SOURCE)
        with self.assertRaisesRegex(tool.ProposalError, "total IDs"):
            tool.validate_registry({"items": ["sample:unknown"] * (tool.MAX_INPUT_IDS + 1)})
        with self.assertRaisesRegex(tool.ProposalError, "total IDs"):
            tool.validate_registry({"entities": ["sample:x" + str(i) for i in range(tool.MAX_INPUT_IDS)], "items": ["sample:y"]})
        self.assertFalse(self.out.exists())

    def test_exact_row_limit_and_overflow_without_truncation(self):
        ids = ["sample:fire_" + str(i) for i in range(tool.MAX_MOVES)]
        self.assertEqual(self.rendered({"items": ids})[2], tool.MAX_MOVES)
        with self.assertRaisesRegex(tool.ProposalError, "513 proposals"):
            self.generate({"items": ids + ["sample:fire_extra"]})
        self.assertFalse(self.out.exists())

    def test_rendered_byte_limit_with_valid_row_count(self):
        ids = ["a" * 31 + ":water_" + "x" * 86 + f"{i:04d}" for i in range(512)]
        self.assertTrue(all(len(rid) == 128 for rid in ids))
        with self.assertRaisesRegex(tool.ProposalError, "UTF-8 bytes"):
            self.generate({"entities": ids})
        self.assertFalse(self.out.exists())

    def test_arsenal_source_failures_precede_writes(self):
        self.input({"items": ["sample:water"]})
        bad = self.root / "ArsenalSpec.java"
        for content in ("class ArsenalSpec {}", 'public enum ArsenalSpec { X("not_mist"); public enum Role {} }',
                        'public enum ArsenalSpec {\n X("x"),\n Y("x");\n public enum Role {} }'):
            bad.write_text(content, encoding="utf-8")
            with self.assertRaises(tool.ProposalError):
                tool.generate(self.src, self.out, bad)
            self.assertFalse(self.out.exists())
        bad.write_bytes(b" " * (tool.MAX_ARSENAL_BYTES + 1))
        with self.assertRaisesRegex(tool.ProposalError, "bytes"):
            tool.generate(self.src, self.out, bad)
        self.assertFalse(self.out.exists())

    def test_existing_candidates_are_never_overwritten(self):
        self.input({"items": ["sample:fire"]})
        for name in (tool.PROPOSAL_NAME, tool.REVIEW_NAME):
            out = self.root / name.replace(".", "-")
            out.mkdir()
            candidate = out / name
            candidate.write_bytes(b"")
            with self.assertRaisesRegex(tool.ProposalError, "already exists"):
                tool.generate(self.src, out, ARSENAL_SOURCE)
            self.assertEqual(list(out.iterdir()), [candidate])
            self.assertEqual(candidate.read_bytes(), b"")

    def test_failed_second_write_removes_only_own_new_file(self):
        self.input({"items": ["sample:fire"]})
        original_open = Path.open

        def guarded_open(path, *args, **kwargs):
            if path.name == tool.REVIEW_NAME and args == ("xb",):
                raise PermissionError("synthetic output failure")
            return original_open(path, *args, **kwargs)

        with patch.object(Path, "open", guarded_open), self.assertRaisesRegex(tool.ProposalError, "cannot create"):
            tool.generate(self.src, self.out, ARSENAL_SOURCE)
        self.assertEqual(list(self.out.iterdir()), [])

    def test_exclusive_create_refuses_a_candidate_created_after_preflight(self):
        self.input({"items": ["sample:fire"]})
        original_open = Path.open

        def racing_open(path, *args, **kwargs):
            if path.name == tool.REVIEW_NAME and args == ("xb",):
                with original_open(path, "wb") as stream:
                    stream.write(b"other-review-candidate")
            return original_open(path, *args, **kwargs)

        with patch.object(Path, "open", racing_open), self.assertRaisesRegex(tool.ProposalError, "cannot create"):
            tool.generate(self.src, self.out, ARSENAL_SOURCE)
        self.assertFalse((self.out / tool.PROPOSAL_NAME).exists())
        self.assertEqual((self.out / tool.REVIEW_NAME).read_bytes(), b"other-review-candidate")

    def test_existing_accepted_runtime_file_is_untouched(self):
        self.out.mkdir()
        accepted = self.out / "bridge-moves.json"
        accepted.write_bytes(b"preserved-accepted-runtime-mapping")
        self.generate({"items": ["sample:fire"]})
        self.assertEqual(accepted.read_bytes(), b"preserved-accepted-runtime-mapping")
        self.assertEqual({path.name for path in self.out.iterdir()},
                         {"bridge-moves.json", tool.PROPOSAL_NAME, tool.REVIEW_NAME})

    def test_cli_success_and_clear_invalid_input_failure(self):
        self.input({"items": ["sample:kunai"]})
        argv = [sys.executable, "-B", str(TOOL_PATH), str(self.src), str(self.out), "--arsenal-source", str(ARSENAL_SOURCE)]
        good = subprocess.run(argv, capture_output=True, text=True, check=False)
        self.assertEqual(good.returncode, 0, good.stderr)
        self.assertIn("inactive proposals", good.stdout)
        self.assertIn("verified=false", good.stdout)
        collision = subprocess.run(argv, capture_output=True, text=True, check=False)
        self.assertEqual(collision.returncode, 2)
        self.assertIn("candidate already exists", collision.stderr)
        self.src.write_text('{"items":[NaN]}', encoding="utf-8")
        rejected = subprocess.run(argv, capture_output=True, text=True, check=False)
        self.assertEqual(rejected.returncode, 2)
        self.assertIn("proposal rejected:", rejected.stderr)
        self.assertNotIn("Traceback", rejected.stderr)


if __name__ == "__main__":
    unittest.main()
