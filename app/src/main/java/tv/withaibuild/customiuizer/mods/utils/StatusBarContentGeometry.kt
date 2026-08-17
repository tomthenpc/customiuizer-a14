package tv.withaibuild.customiuizer.mods.utils

/**
 * View-layer status-bar geometry.
 *
 * Window / insets height is owned by [tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks].
 * Auto-center optical scanning is not used: a negative translation on `status_bar_contents`
 * is clipped by `StatusBarWindowView.clipChildren`.
 */
object StatusBarContentGeometry {

    const val PREF_KEY = "system_statusbar_content_vertical_offset"
    const val RAW_DEFAULT = 20
    const val RAW_SHIFT = 20
    const val DP_DIVIDER = 2f
    const val DUAL_ROWS_PREF = "system_statusbar_dualrows"

    /**
     * Preference units are stored unsigned around [RAW_SHIFT]. `20` is 0 dp.
     * Each stored step is 0.5 dp.
     */
    @JvmStatic
    fun resolveOffsetDp(rawValue: Int): Float = (rawValue - RAW_SHIFT) / DP_DIVIDER

    @JvmStatic
    fun resolveOffsetPx(rawValue: Int, density: Float): Float =
        resolveOffsetDp(rawValue) * density

    /**
     * Dual-row is a custom layout and may consume the extra window pixels.
     * Single-row keeps the inflated SystemUI height.
     */
    @JvmStatic
    fun shouldFillWindowForDualRows(dualRows: Boolean, windowHeightPx: Int): Boolean =
        dualRows && windowHeightPx > 0

    @JvmStatic
    fun shouldCenterNativeBlock(
        dualRows: Boolean,
        windowHeightPx: Int,
        viewHeightPx: Int,
    ): Boolean {
        if (dualRows) return false
        if (windowHeightPx <= 0 || viewHeightPx <= 0) return false
        return windowHeightPx > viewHeightPx + 1
    }

    /**
     * User fine-offset only. Unmeasured parent → 0 so a bad layout cannot
     * produce a huge translation into the window clip.
     */
    @JvmStatic
    fun resolveUserTranslationY(
        parentHeightPx: Int,
        contentHeightPx: Int,
        userOffsetPx: Float,
    ): Float {
        if (parentHeightPx <= 0) return 0f
        val content = if (contentHeightPx > 0) contentHeightPx else parentHeightPx
        return StatusbarViewMaths.clampVerticalOffsetPx(userOffsetPx, parentHeightPx, content)
    }
}
