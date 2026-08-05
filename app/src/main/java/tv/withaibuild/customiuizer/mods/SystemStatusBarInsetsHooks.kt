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
 * - Type encoding is frozen at install time: either modern public
 *   (`WindowInsets.Type.statusBars()`) or legacy internal (`InsetsState.ITYPE_STATUS_BAR`).
 * - Left, top and right of the original frame are preserved.
 * - The bottom is set to `originalTop + configuredHeightPx`. This makes the configured
 *   value a true height, not an absolute bottom, while still respecting a non-zero top.
 * - The original bottom is no longer used as a floor; the configured height is the
 *   authoritative status bar height. Display cutout safety is a separate `displayCutout`
 *   InsetsSource and is not modified here.
 * - Other Insets source types (navigation, caption, IME, cutout, ...) pass through.
 * - `chain.proceed()` is called exactly once per intercepted call; the decision is
 *   computed in a framework-light, non-throwing prelude and then executed outside any
 *   catch that could fall back to a second proceed.
 */
object SystemStatusBarInsetsHooks {

    private const val INSETS_SOURCE_CLASS = "android.view.InsetsSource"
    private const val INSETS_STATE_CLASS = "android.view.InsetsState"
    private const val SET_FRAME_METHOD = "setFrame"
    private const val GET_TYPE_METHOD = "getType"
    private const val GET_FRAME_METHOD = "getFrame"
    private const val GET_ID_METHOD = "getId"

    internal const val MAX_DIAGNOSTIC_KEYS = 32

    private var typeInfo: InsetsTypeInfo? = null
    private var hookInstalled: Boolean = false

    /** Bounded set of stable diagnostic keys whose first hit has already been logged. */
    private val loggedFirstHit = LinkedHashSet<String>()

    @JvmStatic
    internal fun resetDiagnosticsForTest() {
        synchronized(loggedFirstHit) { loggedFirstHit.clear() }
    }

    @JvmStatic
    internal fun diagnosticKeyCountForTest(): Int {
        synchronized(loggedFirstHit) { return loggedFirstHit.size }
    }

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

        val setFrameOneArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) }
        val setFrameFourArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java, Int::class.java, Int::class.java)) }

        if (!setFrameOneArg && !setFrameFourArg) {
            logInstall("setFrame(Rect) and setFrame(int,int,int,int) both missing")
            return
        }

        val resolvedTypeInfo = resolveInsetsTypeInfo(classLoader)
        if (resolvedTypeInfo.encoding == InsetsTypeEncoding.UNSUPPORTED) {
            logInstall("status bar Insets type encoding not resolvable")
            return
        }
        typeInfo = resolvedTypeInfo

        val hasGetId = hasMethod(insetsSourceClass, GET_ID_METHOD)
        val hasGetFrame = hasMethod(insetsSourceClass, GET_FRAME_METHOD)

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
                "rawDp=${StatusBarHeightConfig.rawPreferenceDp} " +
                "resolvedDp=${StatusBarHeightConfig.configuredDp} " +
                "configuredPx=${StatusBarHeightConfig.configuredPx} " +
                "density=${resources.displayMetrics.density} " +
                "densityDpi=${resources.displayMetrics.densityDpi} " +
                "encoding=${resolvedTypeInfo.encoding} " +
                "statusType=${resolvedTypeInfo.statusBarType} " +
                "navType=${resolvedTypeInfo.navigationType} " +
                "cutoutType=${resolvedTypeInfo.displayCutoutType} " +
                "setFrame1=$setFrameOneArg setFrame4=$setFrameFourArg " +
                "getId=$hasGetId getFrame=$hasGetFrame"
        )

        val callback = SetFrameCallback(resolvedTypeInfo, hasGetId, hasGetFrame)
        ModuleHelper.hookAllMethods(insetsSourceClass, SET_FRAME_METHOD, callback)
        hookInstalled = true
    }

    private fun logInstall(message: String) {
        XposedHelpers.log("[StatusBarInsets] install $message")
    }

    /**
     * Which type encoding the ROM uses for `InsetsSource.getType()`.
     *
     * - `MODERN_PUBLIC`: `getType()` returns public `WindowInsets.Type` masks.
     * - `LEGACY_INTERNAL`: `getType()` returns `InsetsState.ITYPE_*` indices.
     * - `UNSUPPORTED`: neither could be resolved.
     */
    internal enum class InsetsTypeEncoding {
        MODERN_PUBLIC,
        LEGACY_INTERNAL,
        UNSUPPORTED,
    }

    internal data class InsetsTypeInfo(
        val encoding: InsetsTypeEncoding,
        val statusBarType: Int,
        val navigationType: Int,
        val displayCutoutType: Int,
    )

    internal data class RawTypeInfo(
        val statusBarType: Int,
        val navigationType: Int,
        val displayCutoutType: Int,
    )

    private fun resolveInsetsTypeInfo(classLoader: ClassLoader?): InsetsTypeInfo {
        val public = resolvePublicTypes()
        val legacy = resolveLegacyTypes(classLoader)

        // If the legacy internal constants are present, prefer them: AOSP/HyperOS
        // InsetsSource.getType() returns the internal index (e.g. ITYPE_STATUS_BAR=0)
        // and public masks like statusBars()=1 would alias ITYPE_NAVIGATION_BAR=1.
        return if (legacy.statusBarType != -1) {
            InsetsTypeInfo(
                InsetsTypeEncoding.LEGACY_INTERNAL,
                legacy.statusBarType,
                if (legacy.navigationType != -1) legacy.navigationType else public.navigationType,
                if (legacy.displayCutoutType != -1) legacy.displayCutoutType else public.displayCutoutType,
            )
        } else if (public.statusBarType != -1) {
            InsetsTypeInfo(
                InsetsTypeEncoding.MODERN_PUBLIC,
                public.statusBarType,
                public.navigationType,
                public.displayCutoutType,
            )
        } else {
            InsetsTypeInfo(
                InsetsTypeEncoding.UNSUPPORTED,
                -1,
                -1,
                -1,
            )
        }
    }

    private fun resolvePublicTypes(): RawTypeInfo {
        val status = safePublicType { WindowInsets.Type.statusBars() }
        val nav = safePublicType { WindowInsets.Type.navigationBars() }
        val cutout = safePublicType { WindowInsets.Type.displayCutout() }
        return RawTypeInfo(status, nav, cutout)
    }

    private fun safePublicType(block: () -> Int): Int {
        return try {
            block()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            -1
        }
    }

    private fun resolveLegacyTypes(classLoader: ClassLoader?): RawTypeInfo {
        val insetsStateClass = XposedHelpers.findClassIfExists(INSETS_STATE_CLASS, classLoader)
            ?: return RawTypeInfo(-1, -1, -1)
        return RawTypeInfo(
            getStaticInt(insetsStateClass, "ITYPE_STATUS_BAR"),
            getStaticInt(insetsStateClass, "ITYPE_NAVIGATION_BAR"),
            getStaticInt(insetsStateClass, "ITYPE_DISPLAY_CUTOUT"),
        )
    }

    private fun getStaticInt(clazz: Class<*>, fieldName: String): Int {
        return try {
            XposedHelpers.getStaticIntField(clazz, fieldName)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            -1
        }
    }

    private fun hasMethod(clazz: Class<*>, methodName: String): Boolean {
        return try {
            clazz.getDeclaredMethods().any { it.name == methodName }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    internal fun isStatusBarType(type: Int, typeInfo: InsetsTypeInfo): Boolean {
        return type == typeInfo.statusBarType
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

    private fun copyRect(source: Rect): Rect {
        return Rect().apply {
            left = source.left
            top = source.top
            right = source.right
            bottom = source.bottom
        }
    }

    /**
     * Decision produced by the framework-light prelude. The actual `chain.proceed()` call
     * happens exactly once, after this decision has been computed and only outside any
     * catch block that could retry.
     */
    internal sealed interface SetFrameDecision {
        val reason: String
    }

    internal data class ProceedOriginal(override val reason: String) : SetFrameDecision
    internal data class ProceedWithArgs(val args: Array<Any>, override val reason: String) : SetFrameDecision

    /**
     * Compute the decision for a single `InsetsSource.setFrame` interception.
     *
     * This function must never call [XposedInterface.Chain.proceed]. All reflection and
     * logging exceptions are either fatal (rethrown) or non-fatal (produce a safe
     * `ProceedOriginal` decision). The returned decision is then executed exactly once
     * by [SetFrameCallback.intercept].
     */
    internal fun makeSetFrameDecision(
        chain: XposedInterface.Chain,
        typeInfo: InsetsTypeInfo,
        configuredPx: Int,
        enabled: Boolean,
    ): SetFrameDecision {
        val source = chain.thisObject
            ?: return ProceedOriginal("source-null")

        val type = try {
            XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int
                ?: return ProceedOriginal("preprocessing-reflection-failed")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            return ProceedOriginal("preprocessing-reflection-failed")
        }

        if (!isStatusBarType(type, typeInfo)) {
            return ProceedOriginal("non-status-type")
        }

        if (!enabled || configuredPx <= 0) {
            return ProceedOriginal("disabled")
        }

        return try {
            val firstArg = chain.getArg(0)
            when {
                firstArg is Rect -> {
                    val newBottom = computeStatusBarFrameBottom(firstArg.top, firstArg.bottom, configuredPx, enabled)
                    val changed = newBottom != firstArg.bottom
                    if (changed) {
                        val adjusted = copyRect(firstArg)
                        adjusted.bottom = newBottom
                        ProceedWithArgs(arrayOf<Any>(adjusted), "status-source-changed")
                    } else {
                        ProceedOriginal("status-source-no-change")
                    }
                }
                firstArg is Int -> {
                    val top = chain.getArg(1) as? Int
                    val right = chain.getArg(2) as? Int
                    val bottom = chain.getArg(3) as? Int
                    if (top == null || right == null || bottom == null) {
                        return ProceedOriginal("invalid-argument-shape")
                    }
                    val newBottom = computeStatusBarFrameBottom(top, bottom, configuredPx, enabled)
                    val changed = newBottom != bottom
                    if (changed) {
                        ProceedWithArgs(arrayOf<Any>(firstArg, top, right, newBottom), "status-source-changed")
                    } else {
                        ProceedOriginal("status-source-no-change")
                    }
                }
                else -> ProceedOriginal("invalid-argument-shape")
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            ProceedOriginal("preprocessing-reflection-failed")
        }
    }

    internal class SetFrameCallback(
        private val typeInfo: InsetsTypeInfo,
        private val hasGetId: Boolean,
        private val hasGetFrame: Boolean,
    ) : MethodHook() {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val decision = makeSetFrameDecision(
                chain,
                typeInfo,
                StatusBarHeightConfig.configuredPx,
                StatusBarHeightConfig.enabled,
            )

            // Log is best-effort and must never trigger a second proceed or swallow
            // the real decision. It uses a bounded set to avoid unbounded growth.
            try {
                maybeLogFirstHit(chain, decision, typeInfo)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }

            // Exactly one proceed. Exceptions from the original method propagate.
            return when (decision) {
                is ProceedOriginal -> chain.proceed()
                is ProceedWithArgs -> chain.proceed(decision.args)
            }
        }

        private fun maybeLogFirstHit(
            chain: XposedInterface.Chain,
            decision: SetFrameDecision,
            typeInfo: InsetsTypeInfo,
        ) {
            val source = chain.thisObject ?: return

            val type = try {
                XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int ?: return
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return
            }

            val overload = when (chain.getArg(0)) {
                is Rect -> "setFrame(Rect)"
                is Int -> "setFrame(int,int,int,int)"
                else -> "unknown"
            }

            val (oldFrame, newBottom) = readIncomingFrame(chain, decision)

            val sourceId = if (hasGetId) {
                try {
                    XposedHelpers.callMethod(source, GET_ID_METHOD) as? Int
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
            } else null

            val reason = decision.reason

            val key = stableKey(typeInfo.encoding, sourceId, type, overload, reason)
            synchronized(loggedFirstHit) {
                if (loggedFirstHit.size >= MAX_DIAGNOSTIC_KEYS) return
                if (!loggedFirstHit.add(key)) return
            }

            XposedHelpers.log(
                "[StatusBarInsets] source-hit " +
                    "encoding=${typeInfo.encoding} " +
                    "sourceId=${sourceId ?: "n/a"} " +
                    "type=$type " +
                    "overload=$overload " +
                    "oldFrame=${oldFrame?.toShortString()} " +
                    "newBottom=$newBottom " +
                    "rawDp=${StatusBarHeightConfig.rawPreferenceDp} " +
                    "resolvedDp=${StatusBarHeightConfig.configuredDp} " +
                    "configuredPx=${StatusBarHeightConfig.configuredPx} " +
                    "changed=${decision is ProceedWithArgs} " +
                    "reason=$reason"
            )
        }

        private fun readIncomingFrame(
            chain: XposedInterface.Chain,
            decision: SetFrameDecision,
        ): Pair<Rect?, Int> {
            val firstArg = chain.getArg(0)
            return when (firstArg) {
                is Rect -> {
                    val newBottom = if (decision is ProceedWithArgs) {
                        val adjusted = decision.args[0]
                        if (adjusted is Rect) adjusted.bottom else firstArg.bottom
                    } else {
                        firstArg.bottom
                    }
                    copyRect(firstArg) to newBottom
                }
                is Int -> {
                    val top = chain.getArg(1) as? Int ?: 0
                    val right = chain.getArg(2) as? Int ?: 0
                    val bottom = chain.getArg(3) as? Int ?: 0
                    val newBottom = if (decision is ProceedWithArgs) {
                        val args = decision.args
                        if (args.size >= 4) args[3] as? Int ?: bottom else bottom
                    } else {
                        bottom
                    }
                    val rect = Rect()
                    rect.left = firstArg
                    rect.top = top
                    rect.right = right
                    rect.bottom = bottom
                    rect to (newBottom ?: bottom)
                }
                else -> null to -1
            }
        }
    }

    private fun stableKey(
        encoding: InsetsTypeEncoding,
        sourceId: Int?,
        type: Int,
        overload: String,
        reason: String,
    ): String {
        return "${encoding.name}:$sourceId:$type:$overload:$reason"
    }
}
