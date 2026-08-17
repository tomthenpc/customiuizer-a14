package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarContentGeometry
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StatusbarViewMaths
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/**
 * SystemUI view-layer vertical geometry for the status bar.
 *
 * Does not touch InsetsSource, WindowState, DisplayPolicy or cutout insets.
 * Does not translate [MiuiPhoneStatusBarView] itself (background, touch and
 * animation hosts stay put). Auto-centering expands the inflated view to fill
 * the already-resized window; the user fine-offset is translationY on
 * `status_bar_contents` only.
 */
object StatusBarContentGeometryHooks {

    private const val CONTENTS_ID = "status_bar_contents"
    private const val BIND_FIELD = "customiuizer_sb_content_geometry"
    private const val PACKAGE_SYSTEMUI = "com.android.systemui"

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }
    private val attachedBars = ArrayList<WeakReference<View>>(2)

    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key != null &&
                key != StatusBarContentGeometry.PREF_KEY &&
                key != StatusBarHeightConfig.PREF_KEY
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
            expandToWindowIfNeeded(statusBarView)
            applyContentOffset(statusBarView)
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

    internal fun expandToWindowIfNeeded(statusBarView: View) {
        val window = findStatusBarWindowView(statusBarView)
        val windowHeight = window?.height ?: 0
        val viewHeight = statusBarView.height
        val customHeight = StatusBarHeightConfig.enabled
        if (!customHeight && !StatusBarContentGeometry.shouldExpandToWindow(windowHeight, viewHeight)) {
            return
        }
        setMatchParentHeight(statusBarView)
        val container = statusBarView.parent as? View
        if (container != null && container !== window) {
            setMatchParentHeight(container)
        }
    }

    internal fun applyContentOffset(statusBarView: View) {
        val contents = findContents(statusBarView) ?: return
        val density = contents.resources.displayMetrics.density
        val raw = MainModule.mPrefs.getInt(
            StatusBarContentGeometry.PREF_KEY,
            StatusBarContentGeometry.RAW_DEFAULT,
        )
        val requestedPx = StatusBarContentGeometry.resolveOffsetPx(raw, density)
        val parentHeight = statusBarView.height
        val contentHeight = visualContentHeight(contents, parentHeight)
        val clamped = StatusbarViewMaths.clampVerticalOffsetPx(
            requestedPx,
            parentHeight,
            contentHeight,
        )
        if (contents.translationY != clamped) {
            contents.translationY = clamped
        }
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

    private fun setMatchParentHeight(view: View) {
        val lp = view.layoutParams ?: return
        if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) return
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        view.layoutParams = lp
    }

    private fun visualContentHeight(contents: View, fallback: Int): Int {
        val group = contents as? ViewGroup ?: return if (contents.height > 0) contents.height else fallback
        val bounds = intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE)
        val rect = Rect()
        for (i in 0 until group.childCount) {
            walkVisualBounds(group, group.getChildAt(i), bounds, rect)
        }
        val fallbackHeight = if (group.height > 0) group.height else fallback
        return StatusBarContentGeometry.visualHeightPx(bounds[0], bounds[1], fallbackHeight)
    }

    private fun walkVisualBounds(root: ViewGroup, view: View, bounds: IntArray, rect: Rect) {
        if (view.visibility == View.GONE) return
        val nested = view as? ViewGroup
        if (nested != null && nested.childCount > 0) {
            for (i in 0 until nested.childCount) {
                walkVisualBounds(root, nested.getChildAt(i), bounds, rect)
            }
            return
        }
        if (view.width <= 0 && view.height <= 0) return
        rect.set(0, 0, view.width, view.height)
        root.offsetDescendantRectToMyCoords(view, rect)
        if (rect.top < bounds[0]) bounds[0] = rect.top
        if (rect.bottom > bounds[1]) bounds[1] = rect.bottom
    }
}
