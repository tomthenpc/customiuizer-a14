package tv.withaibuild.customiuizer.mods

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandEventAdapter
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandHost
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicReference

/**
 * StrongToast presentation hooks for HyperOS 1.
 *
 * MATCH_STATUS_BAR_HEIGHT continues to mutate the ROM row. Dynamic Island deliberately does not:
 * its event content is moved into [DynamicIslandHost.shared], the module-owned surface. This keeps
 * the island independent from the ROM StrongToast window and its status-bar inset crop.
 */
object SystemUIStrongToastHooks {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
    private const val STRONG_TOAST_CONTROL_CLASS = "com.android.systemui.toast.MIUIStrongToastControl"
    private const val BATTERY_CALLBACK_CLASS = "com.android.systemui.toast.MIUIStrongToastControl\$6"
    private const val KEYGUARD_STATE_CLASS = "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"
    private const val STATUS_BAR_VIEW_CLASS = "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"
    private const val STATUS_BAR_CONTENTS_ID = "status_bar_contents"
    private const val MESSAGE_CONTAINER_ID = "cl_strong_toast_msg"
    private const val FOREHEAD_BOTTOM_ID = "strong_toast_bottom_view"
    private const val RUNTIME_SNAPSHOT_FIELD = "customiuizer_strong_toast_runtime_snapshot"
    private const val MATCH_BASELINE_FIELD = "customiuizer_match_mode_baseline"
    private const val HOST_STATE_FIELD = "customiuizer_dynamic_island_host_state"
    private const val CAPSULE_TOP_MARGIN_DP = 6f
    private const val CAPSULE_BOTTOM_CLEARANCE_DP = 8f
    private const val MAX_EDGE_TRAVEL_DP = 28f

    private var statusBarContentsRef = WeakReference<View>(null)
    private var statusBarHiddenOwnerRef = WeakReference<View>(null)
    private var statusBarContentsOriginalAlpha = 1f
    private val pendingSourceHint = ThreadLocal<String?>()

    @JvmField internal var snapshotRef: AtomicReference<StrongToastRuntimeSnapshot>? = null
    @JvmField internal var installed = false

    internal fun currentSnapshot(): StrongToastRuntimeSnapshot? = snapshotRef?.get()

    internal fun storeSnapshot(view: Any, snapshot: StrongToastRuntimeSnapshot) {
        XposedHelpers.setAdditionalInstanceField(view, RUNTIME_SNAPSHOT_FIELD, snapshot)
    }

    internal fun resolveSnapshot(view: Any?): StrongToastRuntimeSnapshot? {
        val stored = view?.let {
            XposedHelpers.getAdditionalInstanceField(it, RUNTIME_SNAPSHOT_FIELD)
                as? StrongToastRuntimeSnapshot
        }
        return stored ?: currentSnapshot()
    }

    private data class DynamicIslandHostState(
        val content: View,
        val parent: ViewGroup,
        val index: Int,
        val layoutParams: ViewGroup.LayoutParams?,
        val rootAlpha: Float,
    )

    @JvmStatic
    internal fun install(
        lpparam: PackageReadyParam,
        snapshotRef: AtomicReference<StrongToastRuntimeSnapshot>,
    ) {
        this.snapshotRef = snapshotRef
        if (installed) return
        installed = true
        installHeightMatch(lpparam)
        installDynamicIslandHostHooks(lpparam)
        installStatusBarContentsCapture(lpparam)
        installControlClassHooks(lpparam)
    }

    private fun installHeightMatch(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(
            STRONG_TOAST_CLASS, lpparam.classLoader, "getWindowParam", object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    try {
                        val layoutParams = callback.getResult() as? WindowManager.LayoutParams ?: return
                        val strongToast = callback.getThisObject() as? View ?: return
                        val snapshot = resolveSnapshot(strongToast) ?: return
                        storeSnapshot(strongToast, snapshot)
                        when (snapshot.mode) {
                            StrongToastPresentationMode.SYSTEM_DEFAULT,
                            StrongToastPresentationMode.HIDE -> Unit
                            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> {
                                val statusBarHeightPx = currentStatusBarInsetPx(strongToast)
                                if (statusBarHeightPx <= 0) return
                                val chinHeightPx = foreheadChinHeightPx(strongToast)
                                if (applyMatchStatusBarHeight(
                                        strongToast,
                                        resolveMatchContentHeightPx(statusBarHeightPx, chinHeightPx),
                                        matchModeHidesChin(statusBarHeightPx, chinHeightPx),
                                    )
                                ) {
                                    layoutParams.height = resolveMatchWindowHeightPx(statusBarHeightPx)
                                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                }
                            }
                            StrongToastPresentationMode.DYNAMIC_ISLAND -> {
                                // This is only a transparent, non-contributing ROM trigger surface.
                                // The host window owns actual island geometry and never derives it
                                // from the status-bar inset.
                                layoutParams.width = 1
                                layoutParams.height = 1
                                layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                layoutParams.windowAnimations = 0
                                layoutParams.setFitInsetsTypes(0)
                            }
                        }
                    } catch (e: Exception) {
                        XposedHelpers.log("StrongToastPresentation", e)
                    }
                }
            }
        )
    }

    private fun installDynamicIslandHostHooks(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS, lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val root = callback.getThisObject() as? View ?: return
                    val snapshot = resolveSnapshot(root) ?: return
                    storeSnapshot(root, snapshot)
                    if (!snapshot.isDynamicIsland) return
                    attachDynamicIslandHost(root, snapshot)
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS, lpparam.classLoader, "onComplete", object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val root = callback.getThisObject() as? View ?: return
                    if (resolveSnapshot(root)?.isDynamicIsland == true) {
                        restoreStatusBarContents(root)
                    }
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS, lpparam.classLoader, "onDetachedFromWindow", object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val root = callback.getThisObject() as? View ?: return
                    try {
                        if (resolveSnapshot(root)?.isDynamicIsland == true) {
                            detachDynamicIslandHost(root)
                            restoreStatusBarContents(root)
                        } else {
                            resetMatchModeCapsule(root)
                        }
                    } finally {
                        XposedHelpers.removeAdditionalInstanceField(root, RUNTIME_SNAPSHOT_FIELD)
                    }
                }
            }
        )
    }

    private fun attachDynamicIslandHost(root: View, snapshot: StrongToastRuntimeSnapshot) {
        try {
            detachDynamicIslandHost(root)
            val content = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID) ?: return
            val parent = content.parent as? ViewGroup ?: return
            val visualWidth = strongToastDimensionPx(root, "strong_toast_width")
            val visualHeight = strongToastDimensionPx(root, "strong_toast_height")
            if (visualWidth <= 0 || visualHeight <= 0) return
            val event = DynamicIslandEventAdapter.fromStrongToast(root, snapshot, pendingSourceHint.get())
            val capsule = DynamicIslandHost.shared.attach(
                root.context,
                event,
                visualWidth,
                visualHeight,
                dpToPx(root, CAPSULE_TOP_MARGIN_DP),
                dpToPx(root, CAPSULE_BOTTOM_CLEARANCE_DP),
                dpToPx(root, MAX_EDGE_TRAVEL_DP),
            ) ?: return
            val index = parent.indexOfChild(content)
            if (index < 0) return
            val state = DynamicIslandHostState(content, parent, index, content.layoutParams, root.alpha)
            parent.removeView(content)
            capsule.addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            // Do not let the legacy StrongToast surface paint around the module host.
            root.alpha = 0f
            findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)?.visibility = View.GONE
            XposedHelpers.setAdditionalInstanceField(root, HOST_STATE_FIELD, state)
            hideStatusBarContents(root)
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastDynamicIslandHost", e)
            detachDynamicIslandHost(root)
        }
    }

    private fun detachDynamicIslandHost(root: View) {
        val state = XposedHelpers.getAdditionalInstanceField(root, HOST_STATE_FIELD)
            as? DynamicIslandHostState
        try {
            if (state != null) {
                val capsule = DynamicIslandHost.shared.capsule
                if (state.content.parent === capsule) capsule.removeView(state.content)
                state.content.layoutParams = state.layoutParams
                if (state.content.parent == null) {
                    state.parent.addView(state.content, state.index.coerceIn(0, state.parent.childCount))
                }
                root.alpha = state.rootAlpha
            }
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastDynamicIslandHostDetach", e)
        } finally {
            DynamicIslandHost.shared.detachImmediate()
            XposedHelpers.removeAdditionalInstanceField(root, HOST_STATE_FIELD)
        }
    }

    private fun installControlClassHooks(lpparam: PackageReadyParam) {
        try {
            val controlClass = XposedHelpers.findClassIfExists(STRONG_TOAST_CONTROL_CLASS, lpparam.classLoader)
            val keyguardClass = XposedHelpers.findClassIfExists(KEYGUARD_STATE_CLASS, lpparam.classLoader)
            val controllerField = if (controlClass != null && keyguardClass != null) {
                XposedHelpers.findFieldIfExists(controlClass, "mKeyguardStateController")
            } else null
            val showingField = keyguardClass?.let { XposedHelpers.findFieldIfExists(it, "mShowing") }
            ModuleHelper.hookAllMethods(
                STRONG_TOAST_CONTROL_CLASS, lpparam.classLoader, "showCustomStrongToast",
                StrongToastControlHook(
                    controllerField, showingField, null, true,
                    DynamicIslandEventAdapter.SOURCE_CUSTOM_SHOW,
                ),
            )
            val batteryClass = XposedHelpers.findClassIfExists(BATTERY_CALLBACK_CLASS, lpparam.classLoader)
            val outerField = batteryClass?.let { XposedHelpers.findFieldIfExists(it, "this\$0") }
            if (batteryClass != null && outerField != null) {
                ModuleHelper.hookAllMethods(
                    batteryClass, "onRefreshBatteryInfo",
                    StrongToastControlHook(
                        controllerField, showingField, outerField, false,
                        DynamicIslandEventAdapter.SOURCE_CHARGING_BATTERY,
                    ),
                )
            }
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastControlClassHooks", e)
        }
    }

    private data class LockscreenGateToken(val keyguardState: Any, val wasShowing: Boolean)

    internal class StrongToastControlHook(
        private val controllerField: Field?,
        private val showingField: Field?,
        private val outerControlField: Field?,
        private val allowHide: Boolean,
        private val sourceHint: String = DynamicIslandEventAdapter.SOURCE_CUSTOM_SHOW,
    ) : MethodHook() {
        @Throws(Throwable::class)
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val snapshot = currentSnapshot() ?: return chain.proceed()
            if (snapshot.mode == StrongToastPresentationMode.HIDE && allowHide) return null
            if (!snapshot.isDynamicIsland) return chain.proceed()
            val receiver = BeforeHookCallback(chain).getThisObject() ?: return chain.proceed()
            val control = try {
                outerControlField?.get(receiver) ?: receiver
            } catch (e: Exception) {
                XposedHelpers.log("StrongToastControlResolve", e)
                return chain.proceed()
            }
            val token = openLockscreenGate(control, controllerField, showingField)
            return try {
                // StrongToast is constructed synchronously under this control path, so its
                // lifecycle boundary captures this source identity without classifying text.
                pendingSourceHint.set(sourceHint)
                chain.proceed()
            } finally {
                pendingSourceHint.remove()
                if (token != null) closeLockscreenGate(token, showingField)
            }
        }
    }

    private fun openLockscreenGate(control: Any?, controllerField: Field?, showingField: Field?): LockscreenGateToken? {
        if (control == null || controllerField == null || showingField == null) return null
        val keyguard = try { controllerField.get(control) } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenRead", e); return null
        } ?: return null
        val showing = try { showingField.getBoolean(keyguard) } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenRead", e); return null
        }
        if (!showing) return null
        return try {
            showingField.setBoolean(keyguard, false)
            LockscreenGateToken(keyguard, true)
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenSet", e); null
        }
    }

    private fun closeLockscreenGate(token: LockscreenGateToken, showingField: Field?) {
        try {
            showingField?.setBoolean(token.keyguardState, token.wasShowing)
        } catch (e: Exception) {
            XposedHelpers.log("StrongToastLockscreenRestore", e)
        }
    }

    internal data class MatchModeBaseline(
        val width: Int, val height: Int, val topMargin: Int, val bottomMargin: Int,
        val layoutGravity: Int, val capsuleGravity: Int,
        val parentPaddingLeft: Int, val parentPaddingTop: Int,
        val parentPaddingRight: Int, val parentPaddingBottom: Int,
        val parentGravity: Int, val parentWidth: Int, val parentHeight: Int,
        val parentTopMargin: Int, val parentBottomMargin: Int, val parentLayoutGravity: Int,
        val bottomViewVisibility: Int,
    )

    internal fun captureMatchModeBaseline(capsule: View, parent: ViewGroup?, bottomView: View?): MatchModeBaseline? {
        val lp = capsule.layoutParams ?: return null
        val parentLp = parent?.layoutParams
        return MatchModeBaseline(
            lp.width, lp.height,
            (lp as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
            (lp as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0,
            (lp as? LinearLayout.LayoutParams)?.gravity ?: 0,
            (capsule as? LinearLayout)?.gravity ?: 0,
            parent?.paddingLeft ?: 0, parent?.paddingTop ?: 0, parent?.paddingRight ?: 0, parent?.paddingBottom ?: 0,
            (parent as? LinearLayout)?.gravity ?: 0, parentLp?.width ?: 0, parentLp?.height ?: 0,
            (parentLp as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
            (parentLp as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0,
            (parentLp as? LinearLayout.LayoutParams)?.gravity ?: 0, bottomView?.visibility ?: View.VISIBLE,
        )
    }

    internal fun applyMatchModeBaselineToViews(
        root: View, capsule: View, parent: ViewGroup?, bottomView: View?,
        targetContentHeightPx: Int, hideChin: Boolean,
    ): Boolean {
        if (XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) == null) {
            val baseline = captureMatchModeBaseline(capsule, parent, bottomView) ?: return false
            XposedHelpers.setAdditionalInstanceField(root, MATCH_BASELINE_FIELD, baseline)
        }
        capsule.layoutParams?.let { it.height = targetContentHeightPx; capsule.layoutParams = it }
        bottomView?.visibility = if (hideChin) View.GONE else View.VISIBLE
        parent?.setPadding(0, 0, 0, 0)
        (parent as? LinearLayout)?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        return true
    }

    internal fun applyMatchStatusBarHeight(root: View, targetContentHeightPx: Int, hideChin: Boolean): Boolean {
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID) ?: return false
        return applyMatchModeBaselineToViews(
            root, capsule, capsule.parent as? ViewGroup,
            findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID), targetContentHeightPx, hideChin,
        )
    }

    internal fun restoreMatchModeBaseline(
        root: View, capsule: View, parent: ViewGroup?, bottomView: View?, baseline: MatchModeBaseline,
    ) {
        capsule.layoutParams?.let {
            it.width = baseline.width; it.height = baseline.height
            (it as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = baseline.topMargin; bottomMargin = baseline.bottomMargin
            }
            (it as? LinearLayout.LayoutParams)?.gravity = baseline.layoutGravity
            capsule.layoutParams = it
        }
        (capsule as? LinearLayout)?.gravity = baseline.capsuleGravity
        parent?.layoutParams?.let {
            it.width = baseline.parentWidth; it.height = baseline.parentHeight
            (it as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = baseline.parentTopMargin; bottomMargin = baseline.parentBottomMargin
            }
            (it as? LinearLayout.LayoutParams)?.gravity = baseline.parentLayoutGravity
            parent.layoutParams = it
        }
        parent?.setPadding(baseline.parentPaddingLeft, baseline.parentPaddingTop, baseline.parentPaddingRight, baseline.parentPaddingBottom)
        (parent as? LinearLayout)?.gravity = baseline.parentGravity
        bottomView?.visibility = baseline.bottomViewVisibility
    }

    internal fun resetMatchModeBaselineToViews(
        root: View, capsule: View?, parent: ViewGroup?, bottomView: View?, baseline: MatchModeBaseline,
    ) {
        try {
            if (capsule != null) restoreMatchModeBaseline(root, capsule, parent, bottomView, baseline)
        } finally {
            XposedHelpers.removeAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        }
    }

    internal fun resetMatchModeCapsule(root: View) {
        val baseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) as? MatchModeBaseline ?: return
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID)
        resetMatchModeBaselineToViews(root, capsule, capsule?.parent as? ViewGroup, findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID), baseline)
    }

    private fun installStatusBarContentsCapture(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(STATUS_BAR_VIEW_CLASS, lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val statusBar = callback.getThisObject() as? View ?: return
                val id = statusBar.resources.getIdentifier(STATUS_BAR_CONTENTS_ID, "id", SYSTEM_UI_PACKAGE)
                if (id != 0) statusBar.findViewById<View>(id)?.let { statusBarContentsRef = WeakReference(it) }
            }
        })
        ModuleHelper.hookAllMethods(STATUS_BAR_VIEW_CLASS, lpparam.classLoader, "onDetachedFromWindow", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                val statusBar = callback.getThisObject() as? View ?: return
                if (statusBarContentsRef.get()?.parent === statusBar) statusBarContentsRef.clear()
            }
        })
    }

    private fun hideStatusBarContents(owner: View) {
        val contents = statusBarContentsRef.get() ?: return
        if (!contents.isAttachedToWindow || statusBarHiddenOwnerRef.get()?.isAttachedToWindow == true) return
        statusBarContentsOriginalAlpha = contents.alpha
        statusBarHiddenOwnerRef = WeakReference(owner)
        contents.alpha = 0f
    }

    private fun restoreStatusBarContents(owner: View) {
        if (statusBarHiddenOwnerRef.get() !== owner) return
        statusBarContentsRef.get()?.let { if (it.alpha == 0f) it.alpha = statusBarContentsOriginalAlpha }
        statusBarHiddenOwnerRef.clear()
    }

    private fun currentStatusBarInsetPx(view: View?): Int {
        val wm = view?.context?.getSystemService(WindowManager::class.java) ?: return 0
        return wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
    }

    private fun foreheadChinHeightPx(root: View): Int {
        val view = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID) ?: return 0
        return if (view.visibility == View.GONE) 0 else maxOf(view.measuredHeight, view.background?.intrinsicHeight ?: 0)
    }

    private fun findViewBySystemUiId(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE)
        return if (id == 0) null else root.findViewById(id)
    }

    private fun strongToastDimensionPx(root: View, name: String): Int {
        val id = root.resources.getIdentifier(name, "dimen", SYSTEM_UI_PACKAGE)
        return if (id == 0) 0 else root.resources.getDimensionPixelSize(id)
    }

    private fun dpToPx(view: View, dp: Float): Int = (dp * view.resources.displayMetrics.density + 0.5f).toInt()

    @JvmStatic internal fun resolveMatchContentHeightPx(statusBarHeightPx: Int, chinHeightPx: Int): Int {
        val status = statusBarHeightPx.coerceAtLeast(0)
        return if (chinHeightPx <= 0 || matchModeHidesChin(status, chinHeightPx)) status else status - chinHeightPx
    }

    @JvmStatic internal fun matchModeHidesChin(statusBarHeightPx: Int, chinHeightPx: Int): Boolean =
        statusBarHeightPx > 0 && chinHeightPx > statusBarHeightPx / 2

    @JvmStatic internal fun resolveMatchWindowHeightPx(statusBarHeightPx: Int): Int = statusBarHeightPx.coerceAtLeast(0)

    @JvmStatic internal fun resolveDynamicIslandWindowHeightPx(
        visualHeightPx: Int, topMarginPx: Int, bottomClearancePx: Int,
    ): Int = visualHeightPx.coerceAtLeast(0) + topMarginPx.coerceAtLeast(0) + bottomClearancePx.coerceAtLeast(0)
}
