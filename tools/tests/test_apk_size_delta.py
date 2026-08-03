#!/usr/bin/env python3
"""Mechanical validation for the A14 APK size/R8 delta report."""

from __future__ import annotations

import copy
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
PERF_DIR = REPO_ROOT / "docs" / "performance"
DELTA_JSON = PERF_DIR / "A14_APK_SIZE_DELTA.json"
MD_FILE = PERF_DIR / "A14_APK_SIZE_DELTA.md"
TASK_STATE = REPO_ROOT / "TASK_STATE.md"
GRADLE_FILE = REPO_ROOT / "app" / "build.gradle.kts"

EXPECTED_CURRENT_COMMIT = "1856c4e229213dfae47ff575aee446ce6a7b5f22"
EXPECTED_BASELINE_COMMIT = "55fc2a21d0e96f9ef643f53fcc9b74374bd959db"

REQUIRED_METRICS = [
    "apkFileBytes",
    "zipEntriesUncompressedBytes",
    "zipEntriesCompressedBytes",
    "fileCount",
    "dexUncompressedBytes",
    "dexCompressedBytes",
    "resourcesArscUncompressedBytes",
    "resourcesArscCompressedBytes",
    "libUncompressedBytes",
    "libCompressedBytes",
    "resUncompressedBytes",
    "resCompressedBytes",
    "assetsUncompressedBytes",
    "assetsCompressedBytes",
    "metaUncompressedBytes",
    "metaCompressedBytes",
    "manifestUncompressedBytes",
    "manifestCompressedBytes",
]

CONCLUSIONS = {
    "NO_MEANINGFUL_CHANGE",
    "EXPLAINED_INCREASE",
    "EXPLAINED_DECREASE",
    "MIXED_CHANGE",
    "NEEDS_INVESTIGATION",
}


def git_run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args, cwd=REPO_ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def _normalized(path: str) -> str:
    return path.replace("\\", "/")


def _expected_apk_path(variant: str, version_name: str) -> str:
    suffix = "debug" if variant == "debug" else "develop-unsigned"
    return f"app/build/outputs/apk/{variant}/CustoMIUIzer-A14-{version_name}-{suffix}.apk"


def _regenerate(out_json: Path, out_md: Path) -> None:
    result = subprocess.run(
        [
            sys.executable,
            "tools/apk_size_delta.py",
            "--baseline-commit",
            EXPECTED_BASELINE_COMMIT,
            "--current-commit",
            EXPECTED_CURRENT_COMMIT,
            "--out-json",
            str(out_json),
            "--out-md",
            str(out_md),
        ],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        raise AssertionError(f"apk_size_delta.py failed:\n{result.stdout}\n{result.stderr}")


class ApkSizeDeltaTest(unittest.TestCase):
    def setUp(self) -> None:
        self.data = load_json(DELTA_JSON)
        self.gradle_text = GRADLE_FILE.read_text(encoding="utf-8")
        self.md_text = MD_FILE.read_text(encoding="utf-8")

    # ---- internal validation used for positive and mutation tests ----

    def _assert_apk_paths_exact(self, data: dict) -> None:
        version_name = data["buildConfig"]["versionName"]
        for variant in ("debug", "develop"):
            current = data["variants"][variant]["current"]
            self.assertEqual(
                _expected_apk_path(variant, version_name),
                _normalized(current["apkPath"]),
                f"{variant} apkPath does not match the expected Gradle-derived artifact path",
            )

    def _check_input_consistency(self, data: dict, inputs: dict[str, dict]) -> None:
        for variant, file_name in (("debug", "A14_APK_SIZE_CURRENT.json"), ("develop", "A14_APK_SIZE_CURRENT_DEVELOP.json")):
            raw = inputs[file_name]
            current = data["variants"][variant]["current"]
            self.assertEqual(raw["sha256"], current["sha256"], f"{variant} sha256 mismatch with {file_name}")
            self.assertEqual(raw["apkFileBytes"], current["apkFileBytes"], f"{variant} apkFileBytes mismatch with {file_name}")
            self.assertEqual(raw["apkPath"], current["apkPath"], f"{variant} apkPath mismatch with {file_name}")

    def _validate_report(self, data: dict) -> None:
        # 1. four input JSONs exist
        for name in (
            "A14_APK_SIZE_BASELINE.json",
            "A14_APK_SIZE_CURRENT.json",
            "A14_APK_SIZE_BASELINE_DEVELOP.json",
            "A14_APK_SIZE_CURRENT_DEVELOP.json",
        ):
            self.assertTrue((PERF_DIR / name).is_file(), f"Missing input JSON: {name}")

        # 2. current JSON paths are relative
        for variant in ("debug", "develop"):
            current = data["variants"][variant]["current"]
            self.assertNotRegex(current["apkPath"], r"^[A-Za-z]:\\|/home/|/Users/|~\\")
            self.assertFalse(Path(current["apkPath"]).is_absolute())

        # 2a. exact APK path from Gradle versionName
        self._assert_apk_paths_exact(data)

        # 3. SHA-256 64 hex
        for variant in ("debug", "develop"):
            for key in ("baseline", "current"):
                sha = data["variants"][variant][key]["sha256"]
                self.assertRegex(sha, r"^[0-9a-f]{64}$", f"Invalid sha256 in {variant}.{key}")

        # 4. required fields complete for all variants and metrics
        for variant in ("debug", "develop"):
            v = data["variants"][variant]
            for key in ("baseline", "current"):
                for field in ("sourceCommit", "variant", "sha256", "apkFileBytes", "apkPath"):
                    self.assertIn(field, v[key], f"Missing {field} in {variant}.{key}")
            for field in ("versionName", "versionCode"):
                self.assertIn(field, v["current"], f"Missing {field} in {variant}.current")
            for metric in REQUIRED_METRICS:
                self.assertIn(metric, v["metrics"], f"Missing metric {metric} in {variant}")
                for key in ("baseline", "current", "delta", "deltaPercent", "trend"):
                    self.assertIn(key, v["metrics"][metric], f"Missing {key} for {metric}")

        # 5. non-negative
        for variant in ("debug", "develop"):
            for metric in REQUIRED_METRICS:
                m = data["variants"][variant]["metrics"][metric]
                for key in ("baseline", "current"):
                    self.assertGreaterEqual(m[key], 0, f"{variant}.{metric}.{key} negative")
                self.assertGreaterEqual(m["delta"], -m["baseline"], f"{variant}.{metric}.delta too negative")
                self.assertGreaterEqual(m["current"], 0, f"{variant}.{metric}.current negative")

        # 6. delta math
        for variant in ("debug", "develop"):
            for metric in REQUIRED_METRICS:
                m = data["variants"][variant]["metrics"][metric]
                self.assertEqual(m["current"] - m["baseline"], m["delta"], f"{variant}.{metric} delta mismatch")

        # 7. percent zero-div semantics
        for variant in ("debug", "develop"):
            for metric in REQUIRED_METRICS:
                m = data["variants"][variant]["metrics"][metric]
                if m["baseline"] == 0:
                    if m["delta"] == 0:
                        self.assertEqual(m["deltaPercent"], 0.0)
                    else:
                        self.assertTrue(m["deltaPercent"] > 0 or str(m["deltaPercent"]).lower() == "inf")
                else:
                    self.assertAlmostEqual(
                        m["deltaPercent"],
                        (m["delta"] / m["baseline"]) * 100.0,
                        places=4,
                        msg=f"{variant}.{metric} percent mismatch",
                    )

        # 8 & 9. debug vs debug, develop vs develop
        for variant in ("debug", "develop"):
            v = data["variants"][variant]
            self.assertEqual(v["baseline"]["variant"], variant)
            self.assertEqual(v["current"]["variant"], variant)

        # 10. SHAs 40
        self.assertRegex(data["baselineCommit"], r"^[0-9a-f]{40}$")
        self.assertRegex(data["currentSourceCommit"], r"^[0-9a-f]{40}$")

        # 11. current source is task commit ancestor
        self.assertEqual(EXPECTED_CURRENT_COMMIT, data["currentSourceCommit"])
        ancestor = git_run(["git", "merge-base", "--is-ancestor", data["currentSourceCommit"], "HEAD"])
        self.assertEqual(0, ancestor.returncode, "currentSourceCommit is not an ancestor of HEAD")

        # 12. version consistent with gradle
        last_version = re.search(r'val\s+lastVersion\s*=\s*(\d+)', self.gradle_text)
        last_version_name = re.search(r'val\s+lastVersionName\s*=\s*"([^"]+)"', self.gradle_text)
        self.assertIsNotNone(last_version)
        self.assertIsNotNone(last_version_name)
        self.assertEqual(int(last_version.group(1)), data["buildConfig"]["versionCode"])
        self.assertEqual(last_version_name.group(1), data["buildConfig"]["versionName"])

        # 13. build config consistent
        self.assertEqual(
            re.search(r'applicationId\s*=\s*"([^"]+)"', self.gradle_text).group(1),
            data["buildConfig"]["applicationId"],
        )
        self.assertEqual(
            int(re.search(r'minSdk\s*=\s*(\d+)', self.gradle_text).group(1)),
            data["buildConfig"]["minSdk"],
        )
        self.assertEqual(
            int(re.search(r'targetSdk\s*=\s*(\d+)', self.gradle_text).group(1)),
            data["buildConfig"]["targetSdk"],
        )
        self.assertEqual(
            re.search(r'abiFilters\s*\+?=\s*"([^"]+)"', self.gradle_text).group(1),
            data["buildConfig"]["abi"],
        )

        # 14. added/removed/changed mutually exclusive
        for variant in ("debug", "develop"):
            diffs = data["variants"][variant]["entryDiffs"]
            added = {e["name"] for e in diffs["added"]}
            removed = {e["name"] for e in diffs["removed"]}
            changed = {e["name"] for e in diffs["changed"]}
            self.assertEqual(set(), added & removed, f"{variant}: added and removed overlap")
            self.assertEqual(set(), added & changed, f"{variant}: added and changed overlap")
            self.assertEqual(set(), removed & changed, f"{variant}: removed and changed overlap")

        # 15. changed entry math
        for variant in ("debug", "develop"):
            for e in data["variants"][variant]["entryDiffs"]["changed"]:
                self.assertEqual(
                    e["currentCompressed"] - e["baselineCompressed"],
                    e["compressedDelta"],
                )

        # 16. no absolute Windows paths in report
        self.assertNotRegex(json.dumps(data), r"[A-Za-z]:\\[^/\\\s]+")
        self.assertNotRegex(self.md_text, r"[A-Za-z]:\\[^/\\\s]+")

        # 17. no keystore/password
        full = json.dumps(data) + "\n" + self.md_text
        for bad in ("storePassword", "keyPassword", "keystore", "storeFile"):
            self.assertNotIn(bad, full, f"Report contains sensitive token: {bad}")

        # 18. APKs not tracked
        tracked = git_run(["git", "ls-files", "*.apk", "*.aab"])
        self.assertEqual("", tracked.stdout.strip(), "APK/AAB files are tracked")

        # 19. develop not described as official release
        full_lower = full.lower()
        self.assertNotIn("official release", full_lower)
        self.assertNotIn("is a signed release", full_lower)
        self.assertNotIn("officialrelease=true", full_lower)

        # 20. conclusion is one of allowed
        self.assertIn(data["conclusion"], CONCLUSIONS)

    # ---- positive tests ----

    def test_01_input_jsons_exist(self) -> None:
        for name in (
            "A14_APK_SIZE_BASELINE.json",
            "A14_APK_SIZE_CURRENT.json",
            "A14_APK_SIZE_BASELINE_DEVELOP.json",
            "A14_APK_SIZE_CURRENT_DEVELOP.json",
            "A14_APK_SIZE_DELTA.json",
        ):
            self.assertTrue((PERF_DIR / name).is_file(), f"Missing {name}")

    def test_02_report_is_valid(self) -> None:
        self._validate_report(self.data)

    def test_03_device_validation_not_marked_complete(self) -> None:
        state_text = TASK_STATE.read_text(encoding="utf-8")
        section = re.search(
            r"## P12\.4 APK delta.*?(?=\n---\s*\n|\n## |\Z)", state_text, re.S
        )
        self.assertIsNotNone(section)
        text = section.group(0)
        self.assertNotIn("VERIFIED_DEVICE", text)
        self.assertNotIn("State: `COMPLETE`", text)
        self.assertIn("VERIFIED_BUILD", text)

    def test_04_apk_paths_exact_by_version(self) -> None:
        self._assert_apk_paths_exact(self.data)

    def test_05_inputs_match_report(self) -> None:
        inputs = {
            "A14_APK_SIZE_CURRENT.json": load_json(PERF_DIR / "A14_APK_SIZE_CURRENT.json"),
            "A14_APK_SIZE_CURRENT_DEVELOP.json": load_json(PERF_DIR / "A14_APK_SIZE_CURRENT_DEVELOP.json"),
        }
        self._check_input_consistency(self.data, inputs)

    def test_06_deterministic_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            out_json = tmp / "A14_APK_SIZE_DELTA.json"
            out_md = tmp / "A14_APK_SIZE_DELTA.md"
            _regenerate(out_json, out_md)
            self.assertEqual(
                out_json.read_bytes(),
                DELTA_JSON.read_bytes(),
                "A14_APK_SIZE_DELTA.json is not the deterministic output of apk_size_delta.py",
            )
            self.assertEqual(
                out_md.read_bytes(),
                MD_FILE.read_bytes(),
                "A14_APK_SIZE_DELTA.md is not the deterministic output of apk_size_delta.py",
            )

    # ---- mutation tests ----

    def _mutated(self, mutator) -> dict:
        d = copy.deepcopy(self.data)
        mutator(d)
        return d

    def test_07_mutation_swap_debug_with_develop_baseline(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["baseline"] = d["variants"]["develop"]["baseline"]
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_08_mutation_inject_wrong_delta(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["metrics"]["apkFileBytes"]["delta"] += 1000
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_09_mutation_inject_absolute_path(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["current"]["apkPath"] = "/home/someone/app-debug.apk"
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_10_mutation_debug_wrong_filename(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["current"]["apkPath"] = "app/build/outputs/apk/debug/app-debug.apk"
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_11_mutation_foo_bar_fake_apk(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["current"]["apkPath"] = "foo/bar/fake.apk"
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_12_mutation_delete_sha256(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["current"]["sha256"] = ""
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_13_mutation_describe_develop_as_official_release(self) -> None:
        def mutate(d):
            d["variants"]["develop"]["description"] = "This is an official release APK"
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_14_mutation_bad_current_source_commit(self) -> None:
        def mutate(d):
            d["currentSourceCommit"] = "0000000000000000000000000000000000000000"
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_15_mutation_version_code_mismatch(self) -> None:
        def mutate(d):
            d["buildConfig"]["versionCode"] = 999
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_16_mutation_overlap_added_changed(self) -> None:
        def mutate(d):
            changed = d["variants"]["debug"]["entryDiffs"]["changed"]
            if changed:
                d["variants"]["debug"]["entryDiffs"]["added"].append(
                    {"name": changed[0]["name"], "bucket": changed[0]["bucket"], "compressedSize": 0}
                )
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_17_mutation_tilde_user_path(self) -> None:
        def mutate(d):
            d["variants"]["debug"]["current"]["apkPath"] = r"~\Downloads\app-debug.apk"
        with self.assertRaises(AssertionError):
            self._validate_report(self._mutated(mutate))

    def test_18_mutation_input_sha_mismatch(self) -> None:
        inputs = {
            "A14_APK_SIZE_CURRENT.json": load_json(PERF_DIR / "A14_APK_SIZE_CURRENT.json"),
            "A14_APK_SIZE_CURRENT_DEVELOP.json": load_json(PERF_DIR / "A14_APK_SIZE_CURRENT_DEVELOP.json"),
        }
        inputs["A14_APK_SIZE_CURRENT.json"]["sha256"] = "0" * 64
        with self.assertRaises(AssertionError):
            self._check_input_consistency(self.data, inputs)

    def test_19_mutation_stale_json_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            out_json = tmp / "A14_APK_SIZE_DELTA.json"
            out_md = tmp / "A14_APK_SIZE_DELTA.md"
            _regenerate(out_json, out_md)
            stale = load_json(out_json)
            stale["buildConfig"]["versionCode"] = 999
            stale_bytes = (json.dumps(stale, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
            with self.assertRaises(AssertionError):
                self.assertEqual(stale_bytes, out_json.read_bytes())

    def test_20_mutation_stale_md_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            out_json = tmp / "A14_APK_SIZE_DELTA.json"
            out_md = tmp / "A14_APK_SIZE_DELTA.md"
            _regenerate(out_json, out_md)
            fresh_md = out_md.read_bytes()
            stale_md = fresh_md.replace(b"r14.16.1", b"rXX.XX.XX")
            with self.assertRaises(AssertionError):
                self.assertEqual(stale_md, fresh_md)


if __name__ == "__main__":
    unittest.main()
