import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import check_staged_snapshot as c


class StagedSnapshotTests(unittest.TestCase):

    def test_empty_staged(self):
        with patch("tools.check_staged_snapshot.staged_files", return_value=[]):
            errors = c.check_staged_snapshot()
        self.assertEqual(errors, ["No staged files"])

    def test_qualifying_requires_source(self):
        with patch("tools.check_staged_snapshot.staged_files", return_value=[Path("TASK_STATE.md")]):
            errors = c.check_staged_snapshot(is_qualifying=True)
        self.assertTrue(any("state" in e.lower() for e in errors))


if __name__ == "__main__":
    unittest.main()
