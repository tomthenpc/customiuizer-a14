import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
_VERIFY_PATH = _REPO_ROOT / "tools" / "verify.py"
_AUDIT_PATH = _REPO_ROOT / "tools" / "audit-feature-semantics.py"
_INVENTORY_PATH = _REPO_ROOT / "feature-semantics" / "a14.json"
_SCHEMA_PATH = _REPO_ROOT / "feature-semantics" / "schema.json"

_spec_verify = importlib.util.spec_from_file_location("verify_local", _VERIFY_PATH)
_verify = importlib.util.module_from_spec(_spec_verify)
_spec_verify.loader.exec_module(_verify)

_spec_audit = importlib.util.spec_from_file_location("audit_feature_semantics", _AUDIT_PATH)
_audit = importlib.util.module_from_spec(_spec_audit)
_spec_audit.loader.exec_module(_audit)


class VerifyFeatureSemanticsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo_root = _REPO_ROOT
        cls.schema = _audit.load_schema(_SCHEMA_PATH)

    def _load_inventory(self):
        with _INVENTORY_PATH.open("r", encoding="utf-8") as f:
            return json.load(f)

    def test_verifyRunsFeatureSemanticsValidation(self):
        """verify.check_feature_semantics runs audit-feature-semantics.py --validate."""
        code = _verify.check_feature_semantics()
        self.assertEqual(code, 0)

    def test_javaVersionParserAcceptsJdk25(self):
        self.assertEqual(
            _verify.parse_java_major('java version "25.0.4" 2026-07-21 LTS'),
            25,
        )
        self.assertEqual(
            _verify.parse_java_major('openjdk version "25.0.2" 2026-01-20'),
            25,
        )

    def test_javaVersionParserRejectsUnrelatedOutput(self):
        self.assertIsNone(_verify.parse_java_major("not a java runtime"))

    def test_commandEnvironmentNormalizesBinJavaHome(self):
        java_name = "java.exe" if _verify.sys.platform == "win32" else "java"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "jdk-25"
            binary_dir = root / "bin"
            binary_dir.mkdir(parents=True)
            (binary_dir / java_name).touch()
            env = _verify.command_environment({"JAVA_HOME": str(binary_dir)})
            self.assertEqual(env["JAVA_HOME"], str(root))

    def test_successfulValidationReturnsZero(self):
        """audit-feature-semantics --validate exits 0 on the real inventory."""
        code = _audit.main(["--validate"])
        self.assertEqual(code, 0)

    def test_missingDiscoveredPreferenceFails(self):
        """A discovered preference absent from the inventory and exclusions fails validation."""
        inventory = self._load_inventory()
        # Remove the system_charginginfo_fontsize entry so the real discovery fails.
        before = len(inventory["entries"])
        inventory["entries"] = [
            e for e in inventory["entries"]
            if "system_charginginfo_fontsize" not in e.get("preferenceKeys", [])
        ]
        self.assertLess(len(inventory["entries"]), before)

        discovered = {"system_charginginfo_fontsize"}
        code, errors = _audit.validate_inventory(
            self.repo_root, inventory, self.schema, discovered=discovered
        )
        self.assertEqual(code, 1)
        self.assertTrue(
            any("discovered" in e.lower() and "not in inventory" in e.lower() for e in errors),
            f"expected missing discovered key error, got: {errors}"
        )

    def test_chargingFontSizePreferenceIsCovered(self):
        """The real inventory covers the new charging font size preference."""
        inventory = self._load_inventory()
        entries = inventory["entries"]

        found_code = [e for e in entries if "system_charginginfo_fontsize" in e.get("preferenceKeys", [])]
        self.assertEqual(len(found_code), 1, "system_charginginfo_fontsize must appear exactly once")
        entry = found_code[0]

        self.assertEqual(entry.get("defaultValue"), "16")
        self.assertEqual(entry.get("xmlSource"), "app/src/main/res/xml/prefs_system_charginginfo.xml")
        self.assertIn("SystemLockScreenHooks.kt", entry.get("sourceFile", ""))
        self.assertEqual(entry.get("targetPackage"), "com.android.systemui")
        self.assertEqual(entry.get("installPhase"), "PACKAGE_READY")
        self.assertEqual(entry.get("restartTarget"), "SYSTEMUI")
        self.assertEqual(entry.get("hotReloadable"), False)

        # The XML key is also covered, matching the charging detail entries.
        found_xml = [e for e in entries if "pref_key_system_charginginfo_fontsize" in e.get("preferenceKeys", [])]
        self.assertEqual(len(found_xml), 1, "pref_key_system_charginginfo_fontsize must appear exactly once")
        self.assertEqual(found_xml[0].get("defaultValue"), "16")


if __name__ == "__main__":
    unittest.main()
