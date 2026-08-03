"""Tests for the status bar registration cleanup invariants."""

import importlib.util
import sys
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"
TARGET_PATH = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemUIStatusBarHooks.kt"


def _load_check_invariants() -> types.ModuleType:
    spec = importlib.util.spec_from_file_location("check_invariants_status_bar", CHECK_INVARIANTS)
    module = importlib.util.module_from_spec(spec)
    sys.modules["check_invariants_status_bar"] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


class StatusBarRegistrationInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_status_bar_registration_cleanup(TARGET_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    def test_clean_per_display_passes(self):
        text = """
private val statusBarDisplayRegistry = StatusBarDisplayRegistry<View, LinearLayout>()
private val netSpeedSecondRowHookInstaller = HookInstallStateMachine()

private fun installNetSpeedSecondRowHook(lpparam: PackageReadyParam) {
    netSpeedSecondRowClassLoader = lpparam.classLoader
    netSpeedSecondRowHookInstaller.install {
        ModuleHelper.hookAllMethodsSilently(
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
            lpparam.classLoader,
            "setNetworkSpeedIcon",
            netSpeedSecondRowHookCallback,
        )
    }
}

private fun onFinishInflate(sbView: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(sbView, 0)
    state.secondRow = WeakReference(secondRight)
    installNetSpeedSecondRowHook(lpparam)
}

private fun applyNetworkSpeedToRow(row: LinearLayout, owner: View, number: Any?, unit: Any?, visible: Any?, state: StatusBarDisplayState<View, LinearLayout>) {
    var networkSpeedView: View? = row.findViewWithTag(NETSPEED_ROW2_TAG)
    if (networkSpeedView == null) {
        val created = LayoutInflater.from(row.context).inflate(0, null)!!
        created.tag = NETSPEED_ROW2_TAG
        row.addView(created, 0, LinearLayout.LayoutParams(-2, -2))
        val DarkIconDispatcher = ModuleHelper.getDepInstance("classloader", "com.android.systemui.plugins.DarkIconDispatcher")
        XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", created)
        state.registrations.register(owner) { _ ->
            releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", created, "network-speed-row2")
        }
    }
    XposedHelpers.callMethod(networkSpeedView, "setNetworkSpeed", number, unit)
}

private fun leftIcons(mStatusBar: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(mStatusBar, 0)
    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController")
    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager, iconContainer) as Any
    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
    val handle = state.registrations.register(mStatusBar) { _ ->
        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
    }
    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
}
"""
        self.assertEqual([], self._findings(text))

    def test_global_statusBarGeneration_fails(self):
        text = """
private var statusBarGeneration: WeakReference<View>? = null
private val statusBarRegistrations = OwnedRegistrations<View>()
private fun cleanupStaleStatusBarRegistrations() {
    statusBarRegistrations.cleanupWhere { owner -> owner !== current }
}
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("statusBarGeneration" in d for d in details), f"details: {details}"
        )

    def test_old_netspeed_once_flag_fails(self):
        text = """
private var netSpeedSecondRowHookInstalled = false
private var netSpeedSecondRowRef: WeakReference<LinearLayout>? = null

if (MainModule.mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")) {
    netSpeedSecondRowRef = WeakReference(secondRight)
    if (!netSpeedSecondRowHookInstalled) {
        netSpeedSecondRowHookInstalled = true
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setNetworkSpeedIcon", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val row = netSpeedSecondRowRef?.get() ?: return
            }
        })
    }
}
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(any("once flag" in d for d in details), f"details: {details}")
        self.assertTrue(any("netSpeedSecondRowRef" in d for d in details), f"details: {details}")

    def test_missing_dark_receiver_cleanup_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
state.registrations.register(sbView) { _ ->
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", wrongView, "tag")
}
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("addDarkReceiver" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_missing_icon_group_cleanup_fails(self):
        text = """
XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("addIconGroup" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_manual_remove_icon_group_fails(self):
        text = """
ModuleHelper.callMethodSilently(iconController, "removeIconGroup", staleManager)
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("manual ModuleHelper.callMethodSilently" in d for d in details),
            f"details: {details}",
        )

    def test_missing_left_icon_handle_fails(self):
        text = """
XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
val handle = state.registrations.register(mStatusBar) { _ ->
    releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
}
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("leftIconRegistrationHandle" in d for d in details),
            f"details: {details}",
        )

    def test_missing_per_display_registry_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
statusBarRegistrations.register(sbView) { _ ->
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", iconView, "tag")
}
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("StatusBarDisplayRegistry" in d for d in details),
            f"details: {details}",
        )

    def test_remove_method_name_wrong_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
state.registrations.register(sbView) { _ ->
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiverX", iconView, "tag")
}
"""
        findings = self._findings(text)
        details = self._details(findings)
        self.assertTrue(
            any("addDarkReceiver" in d and "no matching" in d for d in details),
            f"details: {details}",
        )


if __name__ == "__main__":
    unittest.main()
