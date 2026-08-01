"""Tests for the A14-6G static invariants in tools/check-invariants.py."""

import importlib.util
import sys
import tempfile
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"


def _load_check_invariants() -> types.ModuleType:
    spec = importlib.util.spec_from_file_location("check_invariants_a14_6g", CHECK_INVARIANTS)
    module = importlib.util.module_from_spec(spec)
    sys.modules["check_invariants_a14_6g"] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


class A14_6GInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _source_path(self, rel: str) -> Path:
        return SOURCE_ROOT / rel

    def test_feature_install_oom_cleanup_ok(self):
        text = """
    private fun installOne(spec: FeatureSpec, prefs: PrefMap): FeatureInstallResult {
        FeatureInstallState.set(id, FeatureState.INSTALLING)
        val (definition, result) = try {
            val created = spec.create()
            created.install()
        } catch (oom: OutOfMemoryError) {
            FeatureInstallState.set(id, FeatureState.FAILED_TRANSIENT)
            throw oom
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            recordInstallFailure(spec, t)
            Pair(null, FeatureInstallResult.FAILED_TRANSIENT)
        }
        return result
    }
"""
        findings = self.mod.check_feature_install_oom_cleanup(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"),
            text,
        )
        self.assertEqual([], findings)

    def test_feature_install_oom_cleanup_missing_state_rollback(self):
        text = """
    private fun installOne(spec: FeatureSpec, prefs: PrefMap): FeatureInstallResult {
        val (definition, result) = try {
            val created = spec.create()
            created.install()
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            Pair(null, FeatureInstallResult.FAILED_TRANSIENT)
        }
        return result
    }
"""
        findings = self.mod.check_feature_install_oom_cleanup(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"),
            text,
        )
        self.assertEqual(1, len(findings))
        self.assertIn("FAILED_TRANSIENT", findings[0].detail)

    def test_feature_definition_delegates_throwable_boundary(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt"
        )
        clean = """
    override fun install(): FeatureInstallResult {
        installHook()
        return FeatureInstallResult.INSTALLED
    }
"""
        swallowed = """
    override fun install(): FeatureInstallResult = try {
        installHook()
        FeatureInstallResult.INSTALLED
    } catch (t: Throwable) {
        FeatureInstallResult.FAILED_TRANSIENT
    }
"""

        self.assertEqual([], self.mod.check_feature_install_boundary(path, clean))
        findings = self.mod.check_feature_install_boundary(path, swallowed)
        self.assertEqual(1, len(findings))
        self.assertIn("FeatureInstallRegistry", findings[0].detail)

    def test_dexkit_close_requires_oom_rethrow(self):
        with tempfile.TemporaryDirectory() as tmp:
            helper = Path(tmp) / "XposedHelpers.java"
            helper.write_text(
                """
public static void closeBridge() {
    try { bridge.close(); }
    catch (OutOfMemoryError oom) { throw oom; }
    catch (Throwable t) { log(t); }
}
""",
                encoding="utf-8",
            )
            self.assertEqual([], self.mod.check_dexkit_close_oom(helper))

            helper.write_text(
                """
public static void closeBridge() {
    try { bridge.close(); }
    catch (Throwable t) { log(t); }
}
""",
                encoding="utf-8",
            )
            findings = self.mod.check_dexkit_close_oom(helper)
            self.assertEqual(1, len(findings))
            self.assertIn("rethrow", findings[0].detail)

    def test_java_runtime_boundary_requires_oom_rethrow(self):
        with tempfile.TemporaryDirectory() as tmp:
            boundary = Path(tmp) / "Boundary.java"
            boundary.write_text(
                """
void safe() {
    try { work(); }
    catch (OutOfMemoryError oom) { throw oom; }
    catch (Throwable ignored) {}
}
void propagating() {
    try { work(); }
    catch (Throwable t) { log(t); throw t; }
}
""",
                encoding="utf-8",
            )
            self.assertEqual([], self.mod.check_java_fatal_boundaries((boundary,)))

            boundary.write_text(
                """
void unsafe() {
    try { work(); }
    catch (Throwable ignored) {}
}
""",
                encoding="utf-8",
            )
            findings = self.mod.check_java_fatal_boundaries((boundary,))
            self.assertEqual(1, len(findings))
            self.assertIn("OOM", findings[0].detail)

    def test_device_monitor_hot_path_rejects_formatter_and_swallowed_oom(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt"
        )
        clean = """
val text = formatMonitorOneDecimal(value)
try { read() }
catch (oom: OutOfMemoryError) { throw oom }
catch (_: Throwable) { return null }
"""
        self.assertEqual([], self.mod.check_device_info_monitor_hot_path(path, clean))

        unsafe = """
val text = String.format(Locale.ROOT, "%.1f", value)
try { read() }
catch (_: Throwable) { return null }
"""
        findings = self.mod.check_device_info_monitor_hot_path(path, unsafe)
        self.assertEqual(2, len(findings))

    def test_method_hook_callbacks_require_oom_rethrow(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt"
        )
        clean = """
override fun beforeHook(callback: BeforeHookCallback) {
    try { before(callback) }
    catch (oom: OutOfMemoryError) { throw oom }
    catch (t: Throwable) { log(t) }
}
override fun afterHook(callback: AfterHookCallback) {
    try { after(callback) }
    catch (oom: OutOfMemoryError) { throw oom }
    catch (t: Throwable) { log(t) }
}
"""
        self.assertEqual([], self.mod.check_method_hook_fatal_boundary(path, clean))

        unsafe = """
override fun beforeHook(callback: BeforeHookCallback) {
    try { before(callback) }
    catch (t: Throwable) { log(t) }
}
override fun afterHook(callback: AfterHookCallback) {
    try { after(callback) }
    catch (t: Throwable) { log(t) }
}
"""
        findings = self.mod.check_method_hook_fatal_boundary(path, unsafe)
        self.assertEqual(2, len(findings))
        self.assertTrue(all("OutOfMemoryError" in item.detail for item in findings))

    def test_module_helper_requires_oom_rethrow_before_generic_catch(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"
        )
        clean = """
fun silent(): Any? = try { work() }
catch (oom: OutOfMemoryError) { throw oom }
catch (_: Throwable) { null }
fun propagating() = try { work() }
catch (t: Throwable) { throw t }
"""
        self.assertEqual([], self.mod.check_module_helper_fatal_boundaries(path, clean))

        unsafe = """
fun silent(): Any? = try { work() }
catch (_: Throwable) { null }
"""
        findings = self.mod.check_module_helper_fatal_boundaries(path, unsafe)
        self.assertEqual(1, len(findings))
        self.assertIn("OOM", findings[0].detail)

    def test_reflection_cache_get_declared_method_oom_ok(self):
        text = """
    private fun resolveDependencyMethod(loaderState: LoaderState, classLoader: ClassLoader?): Method? {
        val depClass = XposedHelpers.findClassIfExists(dependencyClassName, classLoader)
        val method = if (depClass != null) {
            try {
                depClass.getDeclaredMethod("get", Class::class.java).apply { isAccessible = true }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            }
        } else null
        loaderState.dependencyMethod = method
        loaderState.dependencyMethodResolved = true
        return method
    }
"""
        findings = self.mod.check_reflection_cache_get_declared_method_oom(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
            text,
        )
        self.assertEqual([], findings)

    def test_reflection_cache_get_declared_method_oom_wrong_order(self):
        text = """
    private fun resolveDependencyMethod(loaderState: LoaderState, classLoader: ClassLoader?): Method? {
        val depClass = XposedHelpers.findClassIfExists(dependencyClassName, classLoader)
        val method = if (depClass != null) {
            try {
                depClass.getDeclaredMethod("get", Class::class.java).apply { isAccessible = true }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            } catch (oom: OutOfMemoryError) {
                throw oom
            }
        } else null
        loaderState.dependencyMethodResolved = true
        return method
    }
"""
        findings = self.mod.check_reflection_cache_get_declared_method_oom(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
            text,
        )
        self.assertEqual(1, len(findings))
        self.assertIn("precede", findings[0].detail)

    def test_docs_zero_object_wording(self):
        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp) / "docs"
            docs_dir.mkdir()
            bad = docs_dir / "bad.md"
            bad.write_text("A disabled feature zero running objects is not allowed.\n", encoding="utf-8")
            good = docs_dir / "good.md"
            good.write_text("Zero FeatureDefinition / zero installer / zero Hook.\n", encoding="utf-8")

            findings = self.mod.check_docs_zero_object_wording(docs_dir)
            self.assertEqual(1, len(findings))
            self.assertIn(bad.name, str(findings[0].path))

            clean_dir = Path(tmp) / "clean"
            clean_dir.mkdir()
            (clean_dir / "ok.md").write_text("Only LazyFeatureSpec metadata and lightweight lambdas.\n", encoding="utf-8")
            self.assertEqual([], self.mod.check_docs_zero_object_wording(clean_dir))


if __name__ == "__main__":
    unittest.main()
