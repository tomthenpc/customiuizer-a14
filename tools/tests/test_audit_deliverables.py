import csv
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent

PROCESS_EXCEPTIONS = REPO_ROOT / "docs" / "rom-intelligence" / "A14_PROCESS_EXCEPTIONS.md"
FEATURE_RETIREMENT_MD = REPO_ROOT / "docs" / "audit" / "A14_FEATURE_RETIREMENT.md"
FEATURE_RETIREMENT_CSV = REPO_ROOT / "docs" / "audit" / "A14_FEATURE_RETIREMENT.csv"


class ProcessExceptionsTest(unittest.TestCase):
    def test_file_is_not_placeholder(self):
        text = PROCESS_EXCEPTIONS.read_text(encoding="utf-8")
        self.assertNotIn("TEST EXCEPTIONS", text)
        self.assertIn("Settings main process", text)
        self.assertIn("SystemUI main process", text)
        self.assertIn("Launcher main process", text)

    def test_required_processes_are_documented(self):
        text = PROCESS_EXCEPTIONS.read_text(encoding="utf-8")
        required = [
            "Settings",
            "SecurityCenter",
            "SystemUI",
            "Launcher",
            "PowerKeeper",
            "Wallpaper",
            "NetworkStack",
            "Input method",
            "Generic ANY application",
        ]
        for proc in required:
            self.assertIn(proc, text, f"Missing process section for {proc}")


class FeatureRetirementConsistencyTest(unittest.TestCase):
    def test_markdown_summary_counts_match_csv(self):
        md_text = FEATURE_RETIREMENT_MD.read_text(encoding="utf-8")
        with open(FEATURE_RETIREMENT_CSV, newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))

        counts = {}
        for row in rows:
            verdict = row.get("verdict", "").strip()
            counts[verdict] = counts.get(verdict, 0) + 1

        total = sum(counts.values())

        # Markdown summary is the first block after the initial heading.
        # It uses lines like "- Total features: 240" and "- **KEEP**: 21".
        md_total = re.search(r"Total features:\s*(\d+)", md_text)
        self.assertIsNotNone(md_total, "Total features count not found in markdown")
        self.assertEqual(int(md_total.group(1)), total, "Markdown total does not match CSV")

        for category in ("KEEP", "KEEP_GUARDED", "EXPERIMENTAL", "FREEZE_LEGACY", "DELETE_DEAD"):
            md_match = re.search(rf"\*\*{category}\*\*:\s*(\d+)", md_text)
            csv_count = counts.get(category, 0)
            if md_match is not None:
                self.assertEqual(
                    int(md_match.group(1)),
                    csv_count,
                    f"{category} count in markdown does not match CSV"
                )
