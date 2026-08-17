package tv.withaibuild.customiuizer.mods

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarContentGeometry
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/**
 * SystemUI view-layer vertical geometry for the status bar.
 *
 * Dual-row (custom layout) may size [MiuiPhoneStatusBarView] to the already-resized
 * window. Single-row keeps the inflated height and, when the window is taller,
 * centres that native block with FrameLayout gravity. User fine-offset is
 * `translationY` on `status_bar_contents` only. Optical leaf scanning is not used.
 */
object StatusBarContentGeometryHooks {

    private const val CONTENTS_ID = "status_bar_contents"
    private const val BIND_FIELD = "customiuizer_sb_content_geometry"
    private const val ORIGINAL_HEIGHT_FIELD = "customiuizer_sb_owner_orig_h"
    private const val PACKAGE_SYSTEMUI = "com.android.systemui"

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }
    private val attachedBars = ArrayList<WeakReference<View>>(2)

    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key != null &&
                key != StatusBarContentGeometry.PREF_KEY &&
                key != StatusBarHeightConfig.PREF_KEY &&
                key != StatusBarContentGeometry.DUAL_ROWS_PREF
            ) {
                return
            }
            mainHandler.post { applyToAttached() }
        }
    }

    @JvmStatic
    fun hook(lpparam: PackageReadyParam) {
        ModuleHelper.observePreferenceChange(preferenceObserver, this)
        val afterBind = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val sbView = param.getThisObject() as? View ?: return
                bind(sbView)
            }
        }
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onFinishInflate",
            afterBind,
        )
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onAttachedToWindow",
            afterBind,
        )
    }

    internal fun bind(statusBarView: View) {
        if (XposedHelpers.getAdditionalInstanceField(statusBarView, BIND_FIELD) == null) {
            XposedHelpers.setAdditionalInstanceField(statusBarView, BIND_FIELD, true)
            statusBarView.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@addOnLayoutChangeListener
                applyToView(v)
            }
            register(statusBarView)
        }
        applyToView(statusBarView)
    }

    internal fun applyToView(statusBarView: View) {
        try {
            val heightMutated = applyOwnerHeight(statusBarView)
            if (!heightMutated) {
                applyContentOffset(statusBarView)
            }
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (td: ThreadDeath) {
            throw td
        } catch (vme: VirtualMachineError) {
            throw vme
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StatusBarContentGeometry: apply failed: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Dual-row fills the window so the two custom rows share the extra pixels.
     * Single-row restores the inflated height and centres the WRAP_CONTENT container.
     *
     * @return true when owner height changed and a later layout pass must apply offset.
     */
    internal fun applyOwnerHeight(statusBarView: View): Boolean {
        val window = findStatusBarWindowView(statusBarView)
        val windowHeight = window?.height ?: 0
        val lp = statusBarView.layoutParams ?: return false
        captureOriginalHeight(statusBarView, lp.height)
        val dualRows = MainModule.mPrefs.getBoolean(StatusBarContentGeometry.DUAL_ROWS_PREF)
        val targetHeight = if (StatusBarContentGeometry.shouldFillWindowForDualRows(dualRows, windowHeight)) {
            windowHeight
        } else {
            originalHeight(statusBarView, lp.height)
        }
        var heightMutated = false
        if (lp.height != targetHeight) {
            lp.height = targetHeight
            statusBarView.layoutParams = lp
            heightMutated = true
        }
        applyContainerGravity(statusBarView, window, dualRows, windowHeight)
        return heightMutated
    }

    internal fun applyContentOffset(statusBarView: View) {
        val contents = findContents(statusBarView) ?: return
        val density = contents.resources.displayMetrics.density
        val raw = MainModule.mPrefs.getInt(
            StatusBarContentGeometry.PREF_KEY,
            StatusBarContentGeometry.RAW_DEFAULT,
        )
        val userOffsetPx = StatusBarContentGeometry.resolveOffsetPx(raw, density)
        val translation = StatusBarContentGeometry.resolveUserTranslationY(
            statusBarView.height,
            contents.height,
            userOffsetPx,
        )
        if (contents.translationY != translation) {
            contents.translationY = translation
        }
    }

    private fun applyContainerGravity(
        statusBarView: View,
        window: View?,
        dualRows: Boolean,
        windowHeight: Int,
    ): Boolean {
        val container = statusBarView.parent as? View ?: return false
        if (container === window) return false
        val plp = container.layoutParams as? FrameLayout.LayoutParams ?: return false
        val center = StatusBarContentGeometry.shouldCenterNativeBlock(
            dualRows,
            windowHeight,
            statusBarView.height,
        )
        val gravity = if (center) Gravity.CENTER_VERTICAL else Gravity.TOP
        if (plp.gravity == gravity) return false
        plp.gravity = gravity
        container.layoutParams = plp
        return true
    }

    private fun captureOriginalHeight(statusBarView: View, currentLpHeight: Int) {
        if (XposedHelpers.getAdditionalInstanceField(statusBarView, ORIGINAL_HEIGHT_FIELD) != null) return
        XposedHelpers.setAdditionalInstanceField(statusBarView, ORIGINAL_HEIGHT_FIELD, currentLpHeight)
    }

    private fun originalHeight(statusBarView: View, fallback: Int): Int {
        return XposedHelpers.getAdditionalInstanceField(statusBarView, ORIGINAL_HEIGHT_FIELD) as? Int ?: fallback
    }

    private fun applyToAttached() {
        for (i in attachedBars.indices.reversed()) {
            val view = attachedBars[i].get()
            if (view == null || !view.isAttachedToWindow) {
                attachedBars.removeAt(i)
                continue
            }
            applyToView(view)
        }
    }

    private fun register(statusBarView: View) {
        for (i in attachedBars.indices.reversed()) {
            val existing = attachedBars[i].get()
            if (existing == null || existing === statusBarView) attachedBars.removeAt(i)
        }
        attachedBars.add(WeakReference(statusBarView))
    }

    private fun findContents(statusBarView: View): View? {
        val id = statusBarView.resources.getIdentifier(CONTENTS_ID, "id", PACKAGE_SYSTEMUI)
        if (id != 0) {
            val found = statusBarView.findViewById<View>(id)
            if (found != null) return found
        }
        return (statusBarView as? ViewGroup)?.getChildAt(0)
    }

    private fun findStatusBarWindowView(start: View): View? {
        var current: View? = start
        while (current != null) {
            if (current.javaClass.name.endsWith("StatusBarWindowView")) return current
            current = current.parent as? View
        }
        return start.rootView
    }
}
