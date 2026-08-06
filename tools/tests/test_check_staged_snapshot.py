import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools import check_staged_snapshot as c


class StagedSnapshotV2Tests(unittest.TestCase):

    def _make_git_repo(self):
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)

        if not shutil.which("git"):
            self.skipTest("git not available")

        try:
            subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
            subprocess.run(
                ["git", "-c", "user.email=test@test.com", "-c", "user.name=Test", "commit", "--allow-empty", "-m", "init"],
                cwd=root,
                check=True,
                capture_output=True,
            )
        except (subprocess.CalledProcessError, OSError) as e:
            self.skipTest(f"git init failed: {e}")

        old_root = c.REPO_ROOT

        def restore():
            c.REPO_ROOT = old_root

        self.addCleanup(restore)
        c.REPO_ROOT = root
        return root

    def _stage(self, root: Path, rel_path: str, content: str) -> None:
        p = root / rel_path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        subprocess.run(["git", "add", rel_path], cwd=root, check=True, capture_output=True)

    def test_empty_staged(self):
        root = self._make_git_repo()
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, ["No staged files"])

    def test_qualifying_requires_source(self):
        root = self._make_git_repo()
        self._stage(root, "tasks/active/T1.md", "# T1\n\n- Status: Active\n- Priority: P0\n\n## 目标\n\nX\n")
        errors = c.check_staged_snapshot(is_qualifying=True)
        self.assertTrue(any("state" in e.lower() for e in errors))

    def test_state_only_checkpoint(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n\n## Now\n\n- T1\n")
        errors = c.check_staged_snapshot()
        self.assertTrue(any("state-only" in e.lower() for e in errors))

    def test_state_with_source_is_ok(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "tools/foo.py", "print(1)\n")
        errors = c.check_staged_snapshot()
        self.assertEqual(errors, [])

    def test_untracked_local_files_not_repo_facts(self):
        root = self._make_git_repo()
        # Create a state file plus an untracked doc-like file.
        self._stage(root, "tasks/active/T1.md", "# T1\n\n- Status: Active\n- Priority: P0\n\n## 目标\n\nX\n")
        untracked = root / "docs" / "untracked_evidence.md"
        untracked.parent.mkdir(parents=True, exist_ok=True)
        untracked.write_text("# evidence", encoding="utf-8")

        # staged_files must not include the untracked file.
        staged = c.staged_files()
        self.assertNotIn(untracked, staged)

        # The state-only checkpoint is still reported because the untracked file
        # must not count as a work product.
        errors = c.check_staged_snapshot()
        self.assertTrue(any("state-only" in e.lower() for e in errors))

    def test_commit_msg_warns_for_state_without_keyword(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "tools/foo.py", "print(1)\n")

        msg = root / "commit.msg"
        msg.write_text("add foo", encoding="utf-8")
        errors = c.check_staged_snapshot(commit_msg_path=str(msg))
        self.assertTrue(any("commit message" in e.lower() for e in errors))

    def test_commit_msg_ok_for_state_with_keyword(self):
        root = self._make_git_repo()
        self._stage(root, "ROADMAP.md", "# Roadmap\n")
        self._stage(root, "tools/foo.py", "print(1)\n")

        msg = root / "commit.msg"
        msg.write_text("docs: update roadmap", encoding="utf-8")
        errors = c.check_staged_snapshot(commit_msg_path=str(msg))
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
