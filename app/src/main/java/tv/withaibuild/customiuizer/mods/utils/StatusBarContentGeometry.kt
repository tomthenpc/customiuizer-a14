package tv.withaibuild.customiuizer.mods.utils

/**
 * Pure geometry for status-bar content auto-centering and the global vertical offset.
 *
 * Window / insets height is owned by [tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks].
 * This helper only answers view-layer questions: whether the inflated status bar still
 * has the old pixel height, and how far a user fine-offset may move the content layer
 * without leaving the parent.
 */
object StatusBarContentGeometry {

    const val PREF_KEY = "system_statusbar_content_vertical_offset"
    const val RAW_DEFAULT = 20
    const val RAW_SHIFT = 20
    const val DP_DIVIDER = 2f

    /**
     * Preference units are stored unsigned around [RAW_SHIFT]. `20` is 0 dp (auto-center).
     * Each stored step is 0.5 dp.
     */
    @JvmStatic
    fun resolveOffsetDp(rawValue: Int): Float = (rawValue - RAW_SHIFT) / DP_DIVIDER

    @JvmStatic
    fun resolveOffsetPx(rawValue: Int, density: Float): Float =
        resolveOffsetDp(rawValue) * density

    /**
     * True when the status-bar window has grown past the inflated view height, so the
     * view (and its WRAP_CONTENT container) must be expanded to MATCH_PARENT before
     * gravity can center the content layer.
     */
    @JvmStatic
    fun shouldExpandToWindow(windowHeightPx: Int, viewHeightPx: Int): Boolean {
        if (windowHeightPx <= 0 || viewHeightPx <= 0) return false
        return windowHeightPx > viewHeightPx + 1
    }

    /**
     * Signed delta from parent visual center to content visual center. Positive means
     * the content is below the parent center.
     */
    @JvmStatic
    fun centerDeltaPx(
        parentTop: Int,
        parentBottom: Int,
        contentTop: Int,
        contentBottom: Int,
    ): Float {
        val parentCenter = (parentTop + parentBottom) / 2f
        val contentCenter = (contentTop + contentBottom) / 2f
        return contentCenter - parentCenter
    }

    @JvmStatic
    fun isCenteredWithinTolerance(deltaPx: Float, tolerancePx: Float): Boolean =
        kotlin.math.abs(deltaPx) <= tolerancePx

    /**
     * Visual height of laid-out leaves. Empty or inverted ranges fall back to [fallbackHeightPx]
     * so an unmeasured tree does not clamp a live offset to 0.
     */
    @JvmStatic
    fun visualHeightPx(minTop: Int, maxBottom: Int, fallbackHeightPx: Int): Int {
        if (minTop == Int.MAX_VALUE || maxBottom <= minTop) return fallbackHeightPx
        return maxBottom - minTop
    }

    /**
     * Pixel tolerance for the auto-center gate: 1 px plus rounding at the current density.
     */
    @JvmStatic
    fun centerTolerancePx(density: Float): Float = 1f + density.coerceAtLeast(1f)
}
