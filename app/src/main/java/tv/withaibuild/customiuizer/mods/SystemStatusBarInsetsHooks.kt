package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Rect
import android.view.WindowInsets
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * system_server Insets boundary for status bar height.
 *
 * Hooks `android.view.InsetsSource.setFrame` so the status bar [InsetsSource] frame
 * reflects the same height as the framework resources and the SystemUI view.
 *
 * Implementation rules:
 * - Only `ITYPE_STATUS_BAR` / `WindowInsets.Type.statusBars()` sources are touched.
 * - Left, top and right of the original frame are preserved.
 * - Only the bottom is adjusted to the configured px height.
 * - The original bottom is never reduced, preserving system cutout-safe logic.
 * - Other Insets source types (navigation, caption, IME, cutout, ...) pass through.
 * - Reflection is done once on the cold install path; the hot path reads cached values.
 */
object SystemStatusBarInsetsHooks {

    private const val INSETS_SOURCE_CLASS = "android.view.InsetsSource"
    private const val SET_FRAME_METHOD = "setFrame"
    private const val GET_TYPE_METHOD = "getType"
    private const val GET_FRAME_METHOD = "getFrame"

    /** Cached status bar type masks; detected on the cold path. */
    private var statusBarPublicType: Int = -1
    private var statusBarInternalType: Int = -1
    private var hookInstalled: Boolean = false

    @JvmStatic
    fun StatusBarInsetsHeightHook(lpparam: SystemServerStartingParam) {
        if (hookInstalled) return

        val classLoader = lpparam.classLoader ?: return
        val insetsSourceClass = XposedHelpers.findClassIfExists(INSETS_SOURCE_CLASS, classLoader)
        if (insetsSourceClass == null) {
            logUnsupported("InsetsSource class not found: $INSETS_SOURCE_CLASS")
            return
        }

        val setFrameMethods = try {
            insetsSourceClass.getDeclaredMethods().filter { it.name == SET_FRAME_METHOD }
        } catch (t: Throwable) {
            logUnsupported("InsetsSource.setFrame methods not accessible")
            return
        }

        if (setFrameMethods.none { it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) || it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java, Int::class.java, Int::class.java)) }) {
            logUnsupported("InsetsSource.setFrame(Rect) or setFrame(int,int,int,int) not found")
            return
        }

        statusBarPublicType = resolveStatusBarsPublicType()
        statusBarInternalType = resolveStatusBarsInternalType(classLoader)

        if (statusBarPublicType == -1 && statusBarInternalType == -1) {
            logUnsupported("Cannot resolve status bar Insets type")
            return
        }

        val resources = try {
            Resources.getSystem()
        } catch (t: Throwable) {
            logUnsupported("Resources.getSystem() failed: ${t.javaClass.simpleName}")
            return
        }

        StatusBarHeightConfig.configure(MainModule.mPrefs, resources)

        val callback = SetFrameCallback(
            statusBarPublicType = statusBarPublicType,
            statusBarInternalType = statusBarInternalType,
        )

        ModuleHelper.hookAllMethods(insetsSourceClass, SET_FRAME_METHOD, callback)
        hookInstalled = true
    }

    private fun resolveStatusBarsPublicType(): Int {
        return try {
            WindowInsets.Type.statusBars()
        } catch (t: Throwable) {
            -1
        }
    }

    private fun resolveStatusBarsInternalType(classLoader: ClassLoader?): Int {
        return try {
            val insetsStateClass = XposedHelpers.findClassIfExists("android.view.InsetsState", classLoader)
                ?: return 0 // AOSP ITYPE_STATUS_BAR is 0
            XposedHelpers.getStaticIntField(insetsStateClass, "ITYPE_STATUS_BAR")
        } catch (t: Throwable) {
            0
        }
    }

    private fun logUnsupported(message: String) {
        XposedHelpers.log("[StatusBarInsets] unsupported target: $message")
    }

    /**
     * Pure geometry logic: given the original frame bottom and the configured height,
     * return the new bottom.
     *
     * The original bottom is treated as a floor. This preserves the system's existing
     * cutout-safe logic: if the ROM has already enlarged the source to clear a cutout,
     * we never shrink below that.
     */
    @JvmStatic
    fun computeStatusBarFrameBottom(originalBottom: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalBottom
        return if (originalBottom >= configuredPx) originalBottom else configuredPx
    }

    /**
     * Convenience overload that reads [StatusBarHeightConfig.enabled].
     */
    @JvmStatic
    fun computeStatusBarFrameBottom(originalBottom: Int, configuredPx: Int): Int {
        return computeStatusBarFrameBottom(originalBottom, configuredPx, StatusBarHeightConfig.enabled)
    }

    private class SetFrameCallback(
        private val statusBarPublicType: Int,
        private val statusBarInternalType: Int,
    ) : MethodHook() {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val source = chain.thisObject ?: return chain.proceed()

            val type = try {
                XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int ?: return chain.proceed()
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) throw t
                return chain.proceed()
            }

            if (!isStatusBarSource(type, source)) {
                return chain.proceed()
            }

            val configuredPx = StatusBarHeightConfig.configuredPx
            if (configuredPx <= 0 || !StatusBarHeightConfig.enabled) {
                return chain.proceed()
            }

            return try {
                val firstArg = chain.getArg(0)
                when {
                    firstArg is Rect -> {
                        firstArg.bottom = computeStatusBarFrameBottom(firstArg.bottom, configuredPx)
                        chain.proceed()
                    }
                    firstArg is Int -> {
                        val top = chain.getArg(1) as? Int ?: return chain.proceed()
                        val right = chain.getArg(2) as? Int ?: return chain.proceed()
                        val bottom = chain.getArg(3) as? Int ?: return chain.proceed()
                        chain.proceed(
                            arrayOf(
                                firstArg,
                                top,
                                right,
                                computeStatusBarFrameBottom(bottom, configuredPx),
                            )
                        )
                    }
                    else -> chain.proceed()
                }
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) throw t
                XposedHelpers.log(t)
                chain.proceed()
            }
        }

        private fun isStatusBarSource(type: Int, source: Any?): Boolean {
            return when {
                // Internal InsetsState encoding (ITYPE_STATUS_BAR == 0 on AOSP 14).
                type == statusBarInternalType -> true
                // Public WindowInsets.Type encoding (statusBars() == 1 on AOSP 14).
                // Verify top-anchored to avoid misclassifying navigation when a ROM
                // uses the internal index for navigation (which is also 1).
                statusBarPublicType != -1 && type == statusBarPublicType -> isTopAnchored(source)
                else -> false
            }
        }

        private fun isTopAnchored(source: Any?): Boolean {
            return try {
                val frame = XposedHelpers.callMethod(source, GET_FRAME_METHOD) as? Rect
                frame != null && frame.top == 0
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) throw t
                false
            }
        }
    }
}
