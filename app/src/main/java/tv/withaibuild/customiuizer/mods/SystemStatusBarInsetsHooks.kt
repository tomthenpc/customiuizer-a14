package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Rect
import android.view.WindowInsets
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
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
 * - The bottom is set to `originalTop + configuredHeightPx`. This makes the configured
 *   value a true height, not an absolute bottom, while still respecting a non-zero top.
 * - The original bottom is no longer used as a floor; the configured height is the
 *   authoritative status bar height. Display cutout safety is a separate `displayCutout`
 *   InsetsSource and is not modified here.
 * - Other Insets source types (navigation, caption, IME, cutout, ...) pass through.
 * - Reflection is done once on the cold install path; the hot path reads cached values.
 */
object SystemStatusBarInsetsHooks {

    private const val INSETS_SOURCE_CLASS = "android.view.InsetsSource"
    private const val SET_FRAME_METHOD = "setFrame"
    private const val GET_TYPE_METHOD = "getType"
    private const val GET_FRAME_METHOD = "getFrame"
    private const val GET_ID_METHOD = "getId"

    private var statusBarPublicType: Int = -1
    private var statusBarInternalType: Int = -1
    private var setFrameOneArg: Boolean = false
    private var setFrameFourArg: Boolean = false
    private var hookInstalled: Boolean = false

    /** Bounded set of (source id hash) identities whose first hit has already been logged. */
    private val loggedFirstHit = mutableSetOf<Int>()

    @JvmStatic
    fun StatusBarInsetsHeightHook(lpparam: SystemServerStartingParam) {
        if (hookInstalled) return

        val classLoader = lpparam.classLoader ?: run {
            logInstall("no classLoader")
            return
        }

        val insetsSourceClass = XposedHelpers.findClassIfExists(INSETS_SOURCE_CLASS, classLoader)
        if (insetsSourceClass == null) {
            logInstall("InsetsSource class not found")
            return
        }

        val setFrameMethods = try {
            insetsSourceClass.getDeclaredMethods().filter { it.name == SET_FRAME_METHOD }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            logInstall("setFrame methods not accessible: ${t.javaClass.simpleName}")
            return
        }

        setFrameOneArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) }
        setFrameFourArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java, Int::class.java, Int::class.java)) }

        if (!setFrameOneArg && !setFrameFourArg) {
            logInstall("setFrame(Rect) and setFrame(int,int,int,int) both missing")
            return
        }

        statusBarPublicType = resolveStatusBarsPublicType()
        statusBarInternalType = resolveStatusBarsInternalType(classLoader)

        if (statusBarPublicType == -1 && statusBarInternalType == -1) {
            logInstall("status bar Insets type not resolvable")
            return
        }

        val resources = try {
            Resources.getSystem()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            logInstall("Resources.getSystem() failed: ${t.javaClass.simpleName}")
            return
        }

        StatusBarHeightConfig.configure(MainModule.mPrefs, resources)

        logInstall(
            "enabled=${StatusBarHeightConfig.enabled} " +
                "rawDp=${StatusBarHeightConfig.configuredDp} " +
                "configuredPx=${StatusBarHeightConfig.configuredPx} " +
                "density=${resources.displayMetrics.density} " +
                "densityDpi=${resources.displayMetrics.densityDpi} " +
                "publicType=${statusBarPublicType} " +
                "internalType=${statusBarInternalType} " +
                "setFrame1=$setFrameOneArg setFrame4=$setFrameFourArg"
        )

        val callback = SetFrameCallback()
        ModuleHelper.hookAllMethods(insetsSourceClass, SET_FRAME_METHOD, callback)
        hookInstalled = true
    }

    private fun logInstall(message: String) {
        XposedHelpers.log("[StatusBarInsets] install $message")
    }

    private fun resolveStatusBarsPublicType(): Int {
        return try {
            WindowInsets.Type.statusBars()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            -1
        }
    }

    private fun resolveStatusBarsInternalType(classLoader: ClassLoader?): Int {
        return try {
            val insetsStateClass = XposedHelpers.findClassIfExists("android.view.InsetsState", classLoader)
                ?: return 0 // AOSP ITYPE_STATUS_BAR is 0
            XposedHelpers.getStaticIntField(insetsStateClass, "ITYPE_STATUS_BAR")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            0
        }
    }

    /**
     * Pure geometry logic: given the original frame and the configured height in pixels,
     * return the new bottom.
     *
     * The configured value is a height, so the new bottom is `originalTop + configuredPx`.
     * This allows the user to shrink the status bar below the original bottom, which is
     * required on devices (e.g. fuxi) where the original bottom already includes the
     * display cutout safe top and is therefore too large a floor.
     *
     * A non-zero `originalTop` is respected (e.g. secondary displays or rotation).
     */
    @JvmStatic
    fun computeStatusBarFrameBottom(originalTop: Int, originalBottom: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalBottom
        return originalTop + configuredPx
    }

    /**
     * Convenience overload that reads [StatusBarHeightConfig.enabled] and [StatusBarHeightConfig.configuredPx].
     */
    @JvmStatic
    fun computeStatusBarFrameBottom(originalTop: Int, originalBottom: Int): Int {
        return computeStatusBarFrameBottom(originalTop, originalBottom, StatusBarHeightConfig.configuredPx, StatusBarHeightConfig.enabled)
    }

    private class SetFrameCallback : MethodHook() {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val source = chain.thisObject ?: return chain.proceed()

            val type = try {
                XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int ?: return chain.proceed()
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return chain.proceed()
            }

            if (!isStatusBarSource(type, source)) {
                return chain.proceed()
            }

            val configuredPx = StatusBarHeightConfig.configuredPx
            if (configuredPx <= 0 || !StatusBarHeightConfig.enabled) {
                maybeLogFirstHit(source, type, configuredPx, false, "disabled")
                return chain.proceed()
            }

            return try {
                val firstArg = chain.getArg(0)
                when {
                    firstArg is Rect -> {
                        val newBottom = computeStatusBarFrameBottom(firstArg.top, firstArg.bottom)
                        val changed = newBottom != firstArg.bottom
                        firstArg.bottom = newBottom
                        maybeLogFirstHit(source, type, configuredPx, changed, "setFrame(Rect)")
                        chain.proceed()
                    }
                    firstArg is Int -> {
                        val top = chain.getArg(1) as? Int ?: return chain.proceed()
                        val right = chain.getArg(2) as? Int ?: return chain.proceed()
                        val bottom = chain.getArg(3) as? Int ?: return chain.proceed()
                        val newBottom = computeStatusBarFrameBottom(top, bottom)
                        val changed = newBottom != bottom
                        maybeLogFirstHit(source, type, configuredPx, changed, "setFrame(int,int,int,int)")
                        if (changed) {
                            chain.proceed(
                                arrayOf(
                                    firstArg,
                                    top,
                                    right,
                                    newBottom,
                                )
                            )
                        } else {
                            chain.proceed()
                        }
                    }
                    else -> chain.proceed()
                }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(t)
                chain.proceed()
            }
        }

        private fun isStatusBarSource(type: Int, source: Any?): Boolean {
            return when {
                type == statusBarInternalType -> true
                statusBarPublicType != -1 && type == statusBarPublicType -> isTopAnchored(source)
                else -> false
            }
        }

        private fun isTopAnchored(source: Any?): Boolean {
            return try {
                val frame = XposedHelpers.callMethod(source, GET_FRAME_METHOD) as? Rect
                frame != null && frame.top == 0
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                false
            }
        }

        private fun maybeLogFirstHit(source: Any, type: Int, configuredPx: Int, changed: Boolean, overload: String) {
            val identity = try {
                val id = XposedHelpers.callMethod(source, GET_ID_METHOD) as? Int ?: 0
                java.lang.System.identityHashCode(source) xor id
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                java.lang.System.identityHashCode(source)
            }

            synchronized(loggedFirstHit) {
                if (!loggedFirstHit.add(identity)) return
            }

            val frame = try {
                XposedHelpers.callMethod(source, GET_FRAME_METHOD) as? Rect
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                null
            }

            val oldBottom = frame?.bottom ?: -1
            val newBottom = if (StatusBarHeightConfig.enabled && configuredPx > 0 && frame != null) {
                computeStatusBarFrameBottom(frame.top, frame.bottom)
            } else {
                oldBottom
            }

            XposedHelpers.log(
                "[StatusBarInsets] source-hit " +
                    "type=$type " +
                    "overload=$overload " +
                    "oldFrame=$frame " +
                    "newBottom=$newBottom " +
                    "configuredDp=${StatusBarHeightConfig.configuredDp} " +
                    "configuredPx=$configuredPx " +
                    "changed=$changed" +
                    if (changed) "" else " reason=${if (StatusBarHeightConfig.enabled) "no-change" else "disabled"}"
            )
        }
    }
}
