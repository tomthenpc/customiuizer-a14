#!/usr/bin/env python3
"""Mechanical invariants for A14 process routing and feature reachability.

This test replaces the old deliverable audit that read deleted docs
(A14_PROCESS_EXCEPTIONS.md, A14_FEATURE_RETIREMENT.md/.csv) with direct
source-level checks on installers, the process router, and the feature registry.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

from tools.extract_process_matrix import FEATURE_DIR, parse_feature_ids, parse_lazy_specs

REPO_ROOT = Path(__file__).resolve().parents[2]
APP_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"
PACKAGE_ROOT = APP_ROOT / "tv" / "withaibuild" / "customiuizer"
INSTALLERS_DIR = PACKAGE_ROOT / "installers"
MODS_UTILS_DIR = PACKAGE_ROOT / "mods" / "utils"
MAIN_MODULE = PACKAGE_ROOT / "MainModule.java"

FEATURE_TARGET_KT = MODS_UTILS_DIR / "FeatureTarget.kt"
INSTALL_PHASE_KT = MODS_UTILS_DIR / "InstallPhase.kt"
LAZY_FEATURE_SPEC_KT = MODS_UTILS_DIR / "LazyFeatureSpec.kt"
FEATURE_INSTALL_REGISTRY_KT = MODS_UTILS_DIR / "FeatureInstallRegistry.kt"


RETIRED_DOCS = (
    "A14_PROCESS_EXCEPTIONS.md",
    "A14_FEATURE_RETIREMENT.md",
    "A14_FEATURE_RETIREMENT.csv",
)


def _read_texts(
    *paths: Path,
    dirs: list[Path] | None = None,
    suffixes: tuple[str, ...] = (".kt", ".java"),
    exclude: set[str] | None = None,
) -> str:
    chunks: list[str] = []
    exclude = exclude or set()
    if dirs:
        for d in dirs:
            for p in d.rglob("*"):
                if p.is_file() and p.suffix in suffixes and p.name not in exclude:
                    chunks.append(p.read_text(encoding="utf-8"))
    for p in paths:
        chunks.append(p.read_text(encoding="utf-8"))
    return "\n".join(chunks)


def _enum_values(path: Path, enum_name: str) -> set[str]:
    text = path.read_text(encoding="utf-8")
    body_match = re.search(
        rf"enum class {re.escape(enum_name)} \{{(.*?)\}}", text, re.DOTALL
    )
    if not body_match:
        return set()
    body = re.sub(r"/\*.*?\*/", "", body_match.group(1), flags=re.DOTALL)
    values: set[str] = set()
    for line in body.splitlines():
        token = line.strip().rstrip(",")
        if re.fullmatch(r"[A-Z][A-Z_0-9]*", token):
            values.add(token)
    return values


def _all_lazy_specs() -> list[dict[str, str]]:
    specs: list[dict[str, str]] = []
    for f in sorted(FEATURE_DIR.glob("*Features.kt")):
        if f.name == "FeatureIds.kt":
            continue
        text = f.read_text(encoding="utf-8")
        for spec in parse_lazy_specs(text):
            spec["file"] = f.name
            specs.append(spec)
    return specs


def _direct_feature_specs() -> list[dict[str, str]]:
    """Find FeatureDefinitions that are registered directly (not via LazyFeatureSpec)."""
    direct: list[dict[str, str]] = []
    for p in PACKAGE_ROOT.rglob("*.kt"):
        text = p.read_text(encoding="utf-8")
        for m in re.finditer(r"registry\.register\((\w+)\(", text):
            class_name = m.group(1)
            cls_match = re.search(
                rf"internal class {re.escape(class_name)}\s*\([^)]*\)\s*:\s*FeatureDefinition\s*{{(.*?)}}",
                text,
                re.DOTALL,
            )
            if not cls_match:
                continue
            body = cls_match.group(1)
            id_m = re.search(r"override val id\s*=\s*(\w+FeatureId)", body)
            target_m = re.search(r"override val target\s*=\s*FeatureTarget\.(\w+)", body)
            phase_m = re.search(r"override val phase\s*=\s*InstallPhase\.(\w+)", body)
            if id_m and target_m and phase_m:
                direct.append(
                    {
                        "class": class_name,
                        "id": id_m.group(1),
                        "target": target_m.group(1),
                        "phase": phase_m.group(1),
                        "file": p.name,
                    }
                )
    return direct


class ProcessRoutingContractTest(unittest.TestCase):
    """A. Process routing contract — source coverage, not docs."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.source = _read_texts(MAIN_MODULE, dirs=[INSTALLERS_DIR, MODS_UTILS_DIR])

    def test_required_process_domains_are_covered(self) -> None:
        required: list[tuple[str, list[str]]] = [
            (
                "SystemUI",
                [
                    "com.android.systemui",
                    "ProcessScope.SYSTEM_UI",
                    "FeatureTarget.SYSTEM_UI",
                    "SystemUiInstaller",
                ],
            ),
            (
                "Launcher",
                [
                    "com.miui.home",
                    "ProcessScope.LAUNCHER",
                    "FeatureTarget.LAUNCHER",
                    "LauncherInstaller",
                ],
            ),
            (
                "SecurityCenter",
                [
                    "com.miui.securitycenter",
                    "ProcessScope.SECURITY_CENTER_MAIN",
                    "SecurityCenterInstaller",
                ],
            ),
            (
                "Settings",
                [
                    "com.android.settings",
                    "ProcessScope.SETTINGS_MAIN",
                    "SettingsInstaller",
                ],
            ),
            (
                "PowerKeeper",
                [
                    "com.miui.powerkeeper",
                    "ProcessScope.POWER_KEEPER",
                    "PowerKeeperInstaller",
                ],
            ),
            (
                "Wallpaper",
                [
                    "com.miui.miwallpaper",
                    "ProcessScope.WALLPAPER",
                    "MediaInstaller",
                ],
            ),
            (
                "NetworkStack",
                [
                    "com.android.networkstack",
                    "ProcessScope.NETWORK_STACK",
                ],
            ),
            (
                "Input method",
                [
                    "ProcessScope.INPUT_METHOD",
                    "InputMethodInstaller",
                    "com.baidu.input",
                ],
            ),
            (
                "Generic application",
                [
                    "ProcessScope.GENERIC_APP",
                    "GenericAppInstaller",
                ],
            ),
        ]
        for name, tokens in required:
            with self.subTest(process=name):
                self.assertTrue(
                    any(token in self.source for token in tokens),
                    f"{name} is not covered by any installer, router, or "
                    "FeatureId/registry source",
                )


class FeatureReachabilityContractTest(unittest.TestCase):
    """B. Feature reachability contract — source invariants, not retired docs."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_ids = parse_feature_ids()
        cls.lazy_specs = _all_lazy_specs()
        cls.direct_specs = _direct_feature_specs()
        cls.valid_targets = _enum_values(FEATURE_TARGET_KT, "FeatureTarget")
        cls.valid_phases = _enum_values(INSTALL_PHASE_KT, "InstallPhase")

    def test_feature_ids_are_unique(self) -> None:
        ints = [meta["featureIdInt"] for meta in self.feature_ids.values()]
        names = [meta["featureIdName"] for meta in self.feature_ids.values()]
        self.assertEqual(
            len(ints), len(set(ints)), "Duplicate FeatureId integer found"
        )
        self.assertEqual(
            len(names), len(set(names)), "Duplicate FeatureId name found"
        )

    def test_all_feature_specs_have_explicit_target_and_phase(self) -> None:
        missing: list[str] = []
        for spec in self.lazy_specs:
            target = spec.get("target", "")
            phase = spec.get("phase", "")
            if not target or not phase:
                missing.append(f"{spec['file']}: {spec.get('id', '?')}")
            if target and target not in self.valid_targets:
                self.fail(
                    f"{spec['file']} {spec.get('id', '?')} has invalid target {target!r}"
                )
            if phase and phase not in self.valid_phases:
                self.fail(
                    f"{spec['file']} {spec.get('id', '?')} has invalid phase {phase!r}"
                )
        self.assertFalse(missing, "LazyFeatureSpec missing target or phase: " + str(missing))

        for spec in self.direct_specs:
            self.assertIn(
                spec["target"],
                self.valid_targets,
                f"{spec['file']} {spec['class']} has invalid target {spec['target']!r}",
            )
            self.assertIn(
                spec["phase"],
                self.valid_phases,
                f"{spec['file']} {spec['class']} has invalid phase {spec['phase']!r}",
            )

    def test_enabled_false_does_not_create_definition(self) -> None:
        lazy_text = LAZY_FEATURE_SPEC_KT.read_text(encoding="utf-8")
        # LazyFeatureSpec only stores metadata and lightweight lambdas.
        self.assertIn(
            "override fun isEnabled(prefs: PrefMap): Boolean = enabled(prefs)",
            lazy_text,
        )
        self.assertIn(
            "override fun create(): FeatureDefinition = factory()",
            lazy_text,
        )

        registry_text = FEATURE_INSTALL_REGISTRY_KT.read_text(encoding="utf-8")
        self.assertIn("if (!spec.isEnabled(prefs))", registry_text)
        self.assertIn("return FeatureInstallResult.SKIPPED", registry_text)

        # Every factory at every LazyFeatureSpec call site is a lambda, so the
        # FeatureDefinition is only constructed when create() runs.
        for spec in self.lazy_specs:
            factory = spec.get("factory", "").strip()
            self.assertTrue(
                factory.startswith("{") and factory.endswith("}"),
                f"{spec['file']} factory must be a lambda, got {factory!r}",
            )

    def test_no_feature_id_is_registered_but_never_routed(self) -> None:
        lazy_ids = {spec["id"] for spec in self.lazy_specs}
        direct_ids = {spec["id"] for spec in self.direct_specs}
        used_ids = lazy_ids | direct_ids
        all_ids = set(self.feature_ids.keys())
        self.assertSetEqual(
            used_ids,
            all_ids,
            "Every FeatureId must be used in a FeatureSpec and every "
            "FeatureSpec must map to a known FeatureId",
        )

        # Each *Features.kt object with specs must be called by an installer.
        source = _read_texts(dirs=[PACKAGE_ROOT])
        called = set(re.findall(r"(\w+Features)\.(?:all|selected)\(", source))
        for f in sorted(FEATURE_DIR.glob("*Features.kt")):
            if f.name == "FeatureIds.kt":
                continue
            if parse_lazy_specs(f.read_text(encoding="utf-8")):
                self.assertIn(
                    f.stem,
                    called,
                    f"{f.name} declares FeatureSpecs but is not routed by any installer",
                )

    def test_no_two_feature_specs_reuse_feature_id(self) -> None:
        ids = [spec["id"] for spec in self.lazy_specs]
        for spec in self.direct_specs:
            ids.append(spec["id"])

        seen: dict[str, int] = {}
        for fid in ids:
            seen[fid] = seen.get(fid, 0) + 1
        duplicates = [fid for fid, count in seen.items() if count > 1]
        self.assertFalse(
            duplicates,
            f"FeatureIds reused by more than one FeatureSpec: {duplicates}",
        )

    def test_retired_docs_not_used_as_runtime_fact_source(self) -> None:
        # Scan Kotlin/Java source, Python tooling and JSON configs, but skip
        # this test file because it legitimately names the retired artifacts.
        source = _read_texts(
            dirs=[APP_ROOT, REPO_ROOT / "tools"],
            suffixes=(".kt", ".java", ".py", ".json"),
            exclude={"test_audit_deliverables.py"},
        )
        for name in RETIRED_DOCS:
            self.assertNotIn(
                name,
                source,
                f"Source still references deleted runtime-fact doc {name}",
            )


if __name__ == "__main__":
    unittest.main()
