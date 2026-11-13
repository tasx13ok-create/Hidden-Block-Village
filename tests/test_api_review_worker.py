"""No network calls or API credentials: spending and source-isolation contracts."""
from pathlib import Path
import argparse
import contextlib
import io
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import api_review_worker as worker


class ApiReviewBudgetTests(unittest.TestCase):
    def test_budget_covers_input_output_and_rounds_up(self):
        self.assertEqual(worker.microdollars(1000, 2000, "gpt-6.1-sol"), 25000)
        self.assertEqual(worker.microdollars(1, 0, "gpt-6.1-terra"), 3)

    def test_reservations_prevent_overlapping_or_failed_requests_overspending(self):
        ledger = {"authorized_microdollars": 100000, "requests": []}
        first = worker.reserve(ledger, 100000, 60000, "first")
        first["status"] = "UNRESOLVED_RESERVATION_NO_AUTOMATIC_RETRY"
        with self.assertRaisesRegex(ValueError, "remaining"):
            worker.reserve(ledger, 100000, 50000, "second")
        worker.reserve(ledger, 100000, 40000, "third")
        self.assertEqual(sum(row["debit_microdollars"] for row in ledger["requests"]), 100000)

    def test_existing_budget_cannot_silently_expand(self):
        with self.assertRaisesRegex(ValueError, "authorization differs"):
            worker.reserve({"authorized_microdollars": 10000, "requests": []}, 100000, 1000, "new")

    def test_malformed_token_usage_is_not_a_zero_dollar_success(self):
        for incoming, outgoing in ((None, 1), (1, None), (-1, 0), (True, 1)):
            with self.subTest(incoming=incoming, outgoing=outgoing), self.assertRaises(ValueError):
                worker.microdollars(incoming, outgoing, "gpt-6.1-sol")

    def test_dry_run_makes_no_network_call_even_with_a_key(self):
        args = argparse.Namespace(model="gpt-6.1-sol", max_output_tokens=8000, budget_usd="15",
                                  task="task.md", file=[], execute=False)
        with mock.patch.object(worker, "read_context", return_value=("review this", [])), \
                mock.patch.object(worker, "request") as network, contextlib.redirect_stdout(io.StringIO()):
            worker.run(args)
        network.assert_not_called()


if __name__ == "__main__":
    unittest.main()
