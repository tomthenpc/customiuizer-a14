import unittest
from pathlib import Path
from unittest.mock import patch

from tools import check_document_contracts as c


class DocumentContractTests(unittest.TestCase):

    def test_allowed_kinds(self):
        self.assertIn("CURRENT", c.ALLOWED_KINDS)
        self.assertIn("SNAPSHOT", c.ALLOWED_KINDS)

    def test_parse_metadata_basic(self):
        text = "# Title\n\n```text\nDocumentKind: CURRENT\nBranch: devin/a14-rom-intelligence-audit\n```\n\nbody"
        meta = c.parse_metadata(text)
        self.assertEqual(meta, {"DocumentKind": "CURRENT", "Branch": "devin/a14-rom-intelligence-audit"})

    def test_parse_metadata_missing(self):
        self.assertIsNone(c.parse_metadata("# Title\n\nNo block"))

    def test_git_commit_exists_checks(self):
        # HEAD must exist.
        self.assertTrue(c.git_commit_exists("HEAD"))
        self.assertFalse(c.git_commit_exists("0000000000000000000000000000000000000000"))


if __name__ == "__main__":
    unittest.main()
