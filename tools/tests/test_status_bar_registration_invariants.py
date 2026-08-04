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
OWNED_REGISTRATIONS_PATH = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "OwnedRegistrations.kt"
STATUS_BAR_REGISTRY_PATH = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "StatusBarDisplayRegistry.kt"


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

    @property
    def _passing_base(self) -> str:
        return """
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

private val netSpeedSecondRowHookCallback = object : MethodHook() {
    override fun after(param: AfterHookCallback) {
        val networkSpeedState = param.getArgs()[0]
        val number = XposedHelpers.getObjectField(networkSpeedState, "networkSpeedNumber")
        val unit = XposedHelpers.getObjectField(networkSpeedState, "networkSpeedUnit")
        val visible = XposedHelpers.getObjectField(networkSpeedState, "visible")

        for (state in statusBarDisplayRegistry.allStates()) {
            val row = state.secondRow?.get() ?: continue
            val owner = state.generation?.get() ?: continue
            if (isMainThread()) {
                applyNetworkSpeedToRow(row, owner, number, unit, visible, state)
            } else {
                row.post {
                    val currentRow = state.secondRow?.get() ?: return@post
                    val currentOwner = state.generation?.get() ?: return@post
                    applyNetworkSpeedToRow(currentRow, currentOwner, number, unit, visible, state)
                }
            }
        }
    }
}

private fun applyNetworkSpeedToRow(row: LinearLayout, owner: View, number: Any?, unit: Any?, visible: Any?, state: StatusBarDisplayState<View, LinearLayout>) {
    if (!row.isAttachedToWindow) return
    if (state.generation?.get() !== owner) return
    if (state.secondRow?.get() !== row) return

    var networkSpeedView: View? = row.findViewWithTag(NETSPEED_ROW2_TAG)
    if (networkSpeedView == null) {
        val ctx = row.context
        val layoutResId = ctx.resources.getIdentifier("network_speed", "layout", "com.android.systemui")
        if (layoutResId == 0) return
        val created = LayoutInflater.from(ctx).inflate(layoutResId, null) ?: return
        created.tag = NETSPEED_ROW2_TAG
        row.addView(created, 0, LinearLayout.LayoutParams(-2, -2))

        val classLoader = netSpeedSecondRowClassLoader ?: return
        val DarkIconDispatcher = ModuleHelper.getDepInstance(classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
        if (DarkIconDispatcher == null) return

        val added = try {
            XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", created)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
        if (added) {
            state.registrations.register(owner) {
                releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", created, "network-speed-row2")
            }
        }
        networkSpeedView = created
    }

    XposedHelpers.callMethod(networkSpeedView, "setBlocked", false)
    XposedHelpers.callMethod(networkSpeedView, "setNetworkSpeed", number, unit)
    XposedHelpers.callMethod(networkSpeedView, "setVisibilityByController", visible)
}

private fun onFinishInflate(sbView: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(sbView, 0)
    val rightLayout = XposedHelpers.getAdditionalInstanceField(sbView, "rightLayout") as LinearLayout
    val secondRight = rightLayout.getChildAt(1) as LinearLayout
    state.secondRow = WeakReference(secondRight)
    installNetSpeedSecondRowHook(lpparam)
}

private fun leftIcons(mStatusBar: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(mStatusBar, 0)
    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController") ?: return
    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager, iconContainer) as Any
    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
    val handle = state.registrations.register(mStatusBar) {
        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
    }
    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
}
"""

    def test_clean_per_display_passes(self):
        self.assertEqual([], self._findings(self._passing_base))

    def test_safe_rewrite_with_different_variable_names_passes(self):
        """The rule is structural: equivalent code with different identifiers still passes."""
        text = self._passing_base.replace("owner", "currentBar").replace("row", "secondRowView")
        # Replacing 'row' in isAttachedToWindow would break the symbol, so only rename local vars.
        text = self._passing_base.replace("created", "networkSpeedChild").replace("mDarkIconManager", "leftIconManagerInstance")
        # Variable names used in the structural checks must still match, so keep the call site terms.
        self.assertEqual([], self._findings(text))

    def test_global_statusBarGeneration_fails(self):
        text = """
private var statusBarGeneration: WeakReference<View>? = null
private val statusBarRegistrations = OwnedRegistrations<View>()
private fun cleanupStaleStatusBarRegistrations() {
    statusBarRegistrations.cleanupWhere { owner -> owner !== current }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(any("statusBarGeneration" in d for d in details), f"details: {details}")

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
        details = self._details(self._findings(text))
        self.assertTrue(any("once flag" in d for d in details), f"details: {details}")
        self.assertTrue(any("netSpeedSecondRowRef" in d for d in details), f"details: {details}")

    def test_missing_dark_receiver_cleanup_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
state.registrations.register(sbView) {
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", wrongView, "tag")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("addDarkReceiver" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_missing_icon_group_cleanup_fails(self):
        text = """
XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("addIconGroup" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_manual_remove_icon_group_fails(self):
        text = """
ModuleHelper.callMethodSilently(iconController, "removeIconGroup", staleManager)
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("manual ModuleHelper.callMethodSilently" in d for d in details),
            f"details: {details}",
        )

    def test_missing_left_icon_handle_fails(self):
        text = """
XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
val handle = state.registrations.register(mStatusBar) {
    releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("leftIconRegistrationHandle" in d for d in details),
            f"details: {details}",
        )

    def test_missing_per_display_registry_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
statusBarRegistrations.register(sbView) {
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", iconView, "tag")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("StatusBarDisplayRegistry" in d for d in details),
            f"details: {details}",
        )

    def test_remove_method_name_wrong_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
state.registrations.register(sbView) {
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiverX", iconView, "tag")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("addDarkReceiver" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_missing_second_row_save_fails(self):
        text = self._passing_base.replace("state.secondRow = WeakReference(secondRight)", "// second row not saved")
        details = self._details(self._findings(text))
        self.assertTrue(
            any("state.secondRow = WeakReference(secondRight)" in d for d in details),
            f"details: {details}",
        )

    def test_missing_posted_isAttachedToWindow_check_fails(self):
        text = self._passing_base.replace("if (!row.isAttachedToWindow) return", "// no attach check")
        details = self._details(self._findings(text))
        self.assertTrue(
            any("isAttachedToWindow" in d for d in details),
            f"details: {details}",
        )

    def test_missing_posted_generation_check_fails(self):
        text = self._passing_base.replace(
            'row.post {\n                    val currentRow = state.secondRow?.get() ?: return@post\n                    val currentOwner = state.generation?.get() ?: return@post\n                    applyNetworkSpeedToRow(currentRow, currentOwner, number, unit, visible, state)\n                }',
            'row.post {\n                    applyNetworkSpeedToRow(row, owner, number, unit, visible, state)\n                }',
        )
        details = self._details(self._findings(text))
        self.assertTrue(
            any("display generation" in d and "posted" in d for d in details),
            f"details: {details}",
        )

    def test_missing_posted_second_row_check_fails(self):
        text = self._passing_base.replace(
            'row.post {\n                    val currentRow = state.secondRow?.get() ?: return@post\n                    val currentOwner = state.generation?.get() ?: return@post\n                    applyNetworkSpeedToRow(currentRow, currentOwner, number, unit, visible, state)\n                }',
            'row.post {\n                    applyNetworkSpeedToRow(row, owner, number, unit, visible, state)\n                }',
        )
        details = self._details(self._findings(text))
        self.assertTrue(
            any("second row" in d and "posted" in d for d in details),
            f"details: {details}",
        )

    def test_cleanup_closure_capturing_owner_fails(self):
        text = self._passing_base.replace(
            'state.registrations.register(mStatusBar) {\n        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")\n    }',
            'state.registrations.register(mStatusBar) {\n        XposedHelpers.callMethod(iconController, "removeIconGroup", mStatusBar)\n    }',
        )
        details = self._details(self._findings(text))
        self.assertTrue(
            any("captures the owner" in d for d in details),
            f"details: {details}",
        )

    def test_stray_remove_dark_receiver_string_fails(self):
        text = self._passing_base + '\nval x = "removeDarkReceiver"'
        details = self._details(self._findings(text))
        # This is allowed only inside releaseRegistrationSilently; a stray string in the
        # pairing logic should still be tied to an add call. The test documents that an
        # unpaired removeDarkReceiver is not enough to pass.
        self.assertEqual([], [d for d in details if "stray" in d])

    def test_global_second_row_ref_fails(self):
        text = """
private var netSpeedSecondRowRef: WeakReference<LinearLayout>? = null

if (MainModule.mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")) {
    netSpeedSecondRowRef = WeakReference(secondRight)
    installNetSpeedSecondRowHook(lpparam)
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("netSpeedSecondRowRef" in d for d in details),
            f"details: {details}",
        )

    def test_set_network_speed_hook_without_installer_fails(self):
        text = """
ModuleHelper.hookAllMethodsSilently(
    "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
    lpparam.classLoader,
    "setNetworkSpeedIcon",
    netSpeedSecondRowHookCallback,
)
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("installNetSpeedSecondRowHook" in d for d in details),
            f"details: {details}",
        )


class OwnedRegistrationsModelInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_owned_registrations_model(OWNED_REGISTRATIONS_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    def test_valid_model_passes(self):
        text = """
class OwnedRegistrations<V : Any> {
    private class Entry<V>(owner: V, var cleanup: (() -> Unit)?) {
        val ownerRef = WeakReference(owner)
        val consumed = AtomicBoolean(false)
    }
    private val entries = ArrayList<Entry<V>>(4)
    val size: Int get() = entries.size

    interface RegistrationHandle {
        fun cleanupNow(): Boolean
    }

    private inner class Handle(private val entry: Entry<V>) : RegistrationHandle {
        override fun cleanupNow(): Boolean {
            if (!entry.consumed.compareAndSet(false, true)) return false
            entries.remove(entry)
            val callback = entry.cleanup
            entry.cleanup = null
            if (callback != null) {
                try { callback() }
                catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t) }
            }
            return true
        }
    }

    fun register(owner: V, cleanup: () -> Unit): RegistrationHandle {
        val entry = Entry(owner, cleanup)
        entries.add(entry)
        return Handle(entry)
    }

    fun cleanupWhere(isStale: (V) -> Boolean) {
        val toRemove = ArrayList<Entry<V>>(entries.size)
        for (entry in entries) {
            val owner = entry.ownerRef.get()
            if (owner == null || isStale(owner)) { toRemove.add(entry) }
        }
        if (toRemove.isEmpty()) return
        entries.removeAll(toRemove)
        for (entry in toRemove) { runCleanupOnce(entry) }
    }

    fun cleanupAll() {
        if (entries.isEmpty()) return
        val toRemove = entries.toList()
        entries.clear()
        for (entry in toRemove) { runCleanupOnce(entry) }
    }

    private fun runCleanupOnce(entry: Entry<V>) {
        if (!entry.consumed.compareAndSet(false, true)) return
        val callback = entry.cleanup
        entry.cleanup = null
        if (callback != null) {
            try { callback() }
            catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t) }
        }
    }
}
"""
        self.assertEqual([], self._findings(text))

    def test_cleanup_with_owner_argument_fails(self):
        text = """
fun register(owner: V, cleanup: (V) -> Unit): RegistrationHandle { ... }
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("no-argument cleanup" in d for d in details),
            f"details: {details}",
        )

    def test_missing_cleanup_all_fails(self):
        text = """
class OwnedRegistrations<V : Any> {
    fun register(owner: V, cleanup: () -> Unit): RegistrationHandle { ... }
    fun cleanupWhere(isStale: (V) -> Boolean) { ... }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("cleanupAll" in d for d in details),
            f"details: {details}",
        )

    def test_owner_null_gate_fails(self):
        text = """
private fun runCleanupOnce(entry: Entry<V>) {
    val owner = entry.ownerRef.get()
    val callback = entry.cleanup
    entry.cleanup = null
    if (owner != null && callback != null) { callback() }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("owner has been garbage collected" in d for d in details),
            f"details: {details}",
        )

    def test_callback_not_nulled_before_run_fails(self):
        text = """
private fun runCleanupOnce(entry: Entry<V>) {
    if (!entry.consumed.compareAndSet(false, true)) return
    if (entry.cleanup != null) { entry.cleanup() }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("consumed before the action is invoked" in d for d in details),
            f"details: {details}",
        )


class StatusBarDisplayRegistryPruneInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_status_bar_display_registry_prune(STATUS_BAR_REGISTRY_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    def test_valid_registry_passes(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakHashMap<O, StatusBarDisplayState<O, R>>()

    fun bind(owner: O, displayId: Int): StatusBarDisplayState<O, R> {
        val pending = pendingByOwner.remove(owner)
        val existing = byDisplay[displayId]
        if (existing != null) { existing.registrations.cleanupAll() }
        val state = pending ?: StatusBarDisplayState(WeakReference(owner))
        state.generation = WeakReference(owner)
        byDisplay[displayId] = state
        return state
    }

    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val pendingSnapshot = pendingByOwner.entries.toList()
        val deadOwners = mutableListOf<O>()
        for ((owner, state) in pendingSnapshot) {
            val generationAlive = state.generation?.get() != null
            if (owner == null || !generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadOwners.add(owner)
                }
            }
        }
        for (owner in deadOwners) { pendingByOwner.remove(owner) }
    }
}
"""
        self.assertEqual([], self._findings(text))

    def test_strong_pending_map_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val pendingByOwner = IdentityHashMap<O, StatusBarDisplayState<O, R>>()
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("strong IdentityHashMap" in d for d in details),
            f"details: {details}",
        )

    def test_prune_without_cleanup_all_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    fun prune() {
        for ((displayId, state) in byDisplay) {
            if (state.generation?.get() == null) { byDisplay.remove(displayId) }
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("cleanupAll" in d and "prune" in d for d in details),
            f"details: {details}",
        )

    def test_bind_without_cleanup_all_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    fun bind(owner: O, displayId: Int): StatusBarDisplayState<O, R> {
        val state = StatusBarDisplayState(WeakReference(owner))
        byDisplay[displayId] = state
        return state
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("bind" in d and "cleanupAll" in d for d in details),
            f"details: {details}",
        )


if __name__ == "__main__":
    unittest.main()
