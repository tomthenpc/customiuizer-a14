"""Tests for tools/audit_hook_ownership.py."""

import subprocess
import sys
import unittest
from pathlib import Path

import importlib.util

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT = REPO_ROOT / "tools" / "audit_hook_ownership.py"
OUTPUT = REPO_ROOT / "docs" / "audit" / "A14_HOOK_OWNERSHIP_INVENTORY.md"


def _load_module():
    spec = importlib.util.spec_from_file_location("audit_hook_ownership", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class AuditHookOwnershipTest(unittest.TestCase):
    def test_script_runs_successfully(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(OUTPUT.exists())
        text = OUTPUT.read_text(encoding="utf-8")
        self.assertIn("Total hook call sites scanned:", text)
        # The inventory should not contain an UNKNOWN category table row.
        self.assertNotIn("| UNKNOWN", text)

    def test_classify_known_categories(self):
        mod = _load_module()
        source = REPO_ROOT / "app" / "src" / "main" / "java"

        def rel(rel_path: str) -> Path:
            return source / rel_path

        self.assertEqual("API_BRIDGE", mod.classify(rel("tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java"), 1, ""))
        self.assertEqual("API_BRIDGE", mod.classify(rel("tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt"), 1, ""))
        self.assertEqual("INSTALLER_INFRASTRUCTURE", mod.classify(rel("tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"), 1, ""))
        self.assertEqual("RESOURCE_INFRASTRUCTURE", mod.classify(rel("tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt"), 1, ""))
        self.assertEqual("REGISTRY_FEATURE", mod.classify(rel("tv/withaibuild/customiuizer/mods/System.kt"), 1, ""))
        self.assertEqual("REGISTRY_FEATURE", mod.classify(rel("tv/withaibuild/customiuizer/mods/Controls.kt"), 1, ""))
        self.assertEqual("INSTALLER_INFRASTRUCTURE", mod.classify(rel("tv/withaibuild/customiuizer/installers/SystemUiInstaller.java"), 1, ""))
        self.assertEqual("INSTALLER_INFRASTRUCTURE", mod.classify(rel("tv/withaibuild/customiuizer/MainModule.java"), 1, ""))


if __name__ == "__main__":
    unittest.main()
