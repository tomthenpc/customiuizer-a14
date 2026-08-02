import unittest
from pathlib import Path

from tools import progress_snapshot


class ProgressSnapshotTest(unittest.TestCase):

    def test_parses_smart_state(self):
        state = progress_snapshot.parse_smart_state()
        self.assertIn("CheckpointCount", state)

    def test_parses_task_state(self):
        tasks = progress_snapshot.parse_task_state()
        self.assertTrue(len(tasks) > 0)

    def test_generates_files(self):
        progress_snapshot.main()
        self.assertTrue(progress_snapshot.OUT_JSON.is_file())
        self.assertTrue(progress_snapshot.OUT_MD.is_file())


if __name__ == "__main__":
    unittest.main()
