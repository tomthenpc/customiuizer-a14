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
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/**
 * SystemUI view-layer vertical geometry for the status bar.
 *
 * Does not touch InsetsSource, WindowState, DisplayPolicy or cutout insets.
 * Does not translate [MiuiPhoneStatusBarView] itself (background, touch and
 * animation hosts stay put). Does not mutate `status_bar_container`.
 *
 * Owner: `MiuiPhoneStatusBarView` height follows the already-resized window in px.
 * Correction: one `translationY` on `status_bar_contents` from measured optical
 * center plus the user fine-offset. Alpha on that same view stays with
 * [DynamicIslandStatusBarFade].
 */
object StatusBarContentGeometryHooks {

    private const val CONTENTS_ID = "status_bar_contents"
    private const val BIND_FIELD = "customiuizer_sb_content_geometry"
    private const val PACKAGE_SYSTEMUI = "com.android.systemui"

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }
    private val attachedBars = ArrayList<WeakReference<View>>(2)
    private val visualBounds = intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE)
    private val visualRect = Rect()

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
            val resized = resizeOwnerToWindowIfNeeded(statusBarView)
            if (!resized) {
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
     * One owner: `MiuiPhoneStatusBarView`. The ROM `status_bar_container` stays
     * WRAP_CONTENT and wraps this explicit height. Parent MATCH_PARENT is not used.
     *
     * @return true when layout params changed and a later layout pass must apply offset.
     */
    internal fun resizeOwnerToWindowIfNeeded(statusBarView: View): Boolean {
        val window = findStatusBarWindowView(statusBarView)
        val windowHeight = window?.height ?: 0
        if (windowHeight <= 0) return false
        val lp = statusBarView.layoutParams ?: return false
        if (lp.height == windowHeight) return false
        val viewHeight = statusBarView.height
        val customHeight = StatusBarHeightConfig.enabled
        if (!customHeight) {
            if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT &&
                viewHeight > 0 &&
                kotlin.math.abs(viewHeight - windowHeight) <= 1
            ) {
                return false
            }
            if (!StatusBarContentGeometry.shouldResizeOwner(windowHeight, viewHeight)) {
                return false
            }
        }
        lp.height = windowHeight
        statusBarView.layoutParams = lp
        return true
    }

    internal fun applyContentOffset(statusBarView: View) {
        val contents = findContents(statusBarView) ?: return
        val density = contents.resources.displayMetrics.density
        val raw = MainModule.mPrefs.getInt(
            StatusBarContentGeometry.PREF_KEY,
            StatusBarContentGeometry.RAW_DEFAULT,
        )
        val userOffsetPx = StatusBarContentGeometry.resolveOffsetPx(raw, density)
        val windowHeight = findStatusBarWindowView(statusBarView)?.height ?: 0
        val autoCenter = StatusBarHeightConfig.enabled ||
            StatusBarContentGeometry.shouldResizeOwner(windowHeight, statusBarView.height)
        if (!autoCenter && userOffsetPx == 0f) {
            if (contents.translationY != 0f) {
                contents.translationY = 0f
            }
            return
        }
        val parentBottom = statusBarView.height
        collectOpticalLeafBounds(contents)
        val contentTop: Int
        val contentBottom: Int
        if (visualBounds[1] <= visualBounds[0]) {
            contentTop = Int.MAX_VALUE
            contentBottom = Int.MIN_VALUE
        } else {
            contentTop = contents.top + visualBounds[0]
            contentBottom = contents.top + visualBounds[1]
        }
        val translation = StatusBarContentGeometry.resolveContentsTranslationY(
            0,
            parentBottom,
            contentTop,
            contentBottom,
            userOffsetPx,
            StatusBarContentGeometry.centerTolerancePx(density),
            autoCenter,
        )
        if (contents.translationY != translation) {
            contents.translationY = translation
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

    /**
     * Leaf bounds in `status_bar_contents` layout coordinates. Existing
     * `translationY` is not part of those coordinates, so each apply starts
     * from the raw layout rather than accumulating.
     */
    private fun collectOpticalLeafBounds(contents: View) {
        visualBounds[0] = Int.MAX_VALUE
        visualBounds[1] = Int.MIN_VALUE
        val group = contents as? ViewGroup ?: return
        val parentHeight = if (group.height > 0) group.height else 0
        for (i in 0 until group.childCount) {
            walkOpticalBounds(group, group.getChildAt(i), parentHeight)
        }
    }

    private fun walkOpticalBounds(root: ViewGroup, view: View, parentHeight: Int) {
        if (view.visibility == View.GONE) return
        val nested = view as? ViewGroup
        if (nested != null && nested.childCount > 0) {
            for (i in 0 until nested.childCount) {
                walkOpticalBounds(root, nested.getChildAt(i), parentHeight)
            }
            return
        }
        val lpHeight = view.layoutParams?.height ?: 0
        if (!StatusBarContentGeometry.isOpticalLeaf(view.height, parentHeight, lpHeight)) return
        visualRect.set(0, 0, view.width.coerceAtLeast(0), view.height)
        root.offsetDescendantRectToMyCoords(view, visualRect)
        if (visualRect.top < visualBounds[0]) visualBounds[0] = visualRect.top
        if (visualRect.bottom > visualBounds[1]) visualBounds[1] = visualRect.bottom
    }
}
