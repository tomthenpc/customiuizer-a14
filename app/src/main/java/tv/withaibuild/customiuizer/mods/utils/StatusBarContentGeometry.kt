package tv.withaibuild.customiuizer.mods.utils

/**
 * Pure geometry for status-bar content auto-centering and the global vertical offset.
 *
 * Window / insets height is owned by [tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks].
 * This helper only answers view-layer questions: whether the inflated status bar still
 * has the old pixel height, and how far a measured center correction plus user fine-offset
 * may move the content layer without leaving the parent.
 */
object StatusBarContentGeometry {

    const val PREF_KEY = "system_statusbar_content_vertical_offset"
    const val RAW_DEFAULT = 20
    const val RAW_SHIFT = 20
    const val DP_DIVIDER = 2f

    /** Same value as [android.view.ViewGroup.LayoutParams.MATCH_PARENT], kept Android-free. */
    const val MATCH_PARENT = -1

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
     * True when the live window height and the inflated owner height disagree by more
     * than 1 px, in either direction.
     */
    @JvmStatic
    fun shouldResizeOwner(windowHeightPx: Int, viewHeightPx: Int): Boolean {
        if (windowHeightPx <= 0 || viewHeightPx <= 0) return false
        return kotlin.math.abs(windowHeightPx - viewHeightPx) > 1
    }

    /**
     * A MATCH_PARENT leaf that already fills [parentHeightPx] is a container, not optical
     * content. WRAP_CONTENT / shorter leaves are the measured center signal.
     */
    @JvmStatic
    fun isOpticalLeaf(viewHeightPx: Int, parentHeightPx: Int, lpHeight: Int): Boolean {
        if (viewHeightPx <= 0) return false
        if (lpHeight == MATCH_PARENT && parentHeightPx > 0 && viewHeightPx >= parentHeightPx - 1) {
            return false
        }
        return true
    }

    /**
     * Signed correction that moves content so its visual center matches the parent center.
     * Positive means translate content downward.
     */
    @JvmStatic
    fun centerCorrectionPx(
        parentTop: Int,
        parentBottom: Int,
        contentTop: Int,
        contentBottom: Int,
    ): Float {
        val parentCenter = (parentTop + parentBottom) / 2f
        val contentCenter = (contentTop + contentBottom) / 2f
        return parentCenter - contentCenter
    }

    @JvmStatic
    fun isCenteredWithinTolerance(deltaPx: Float, tolerancePx: Float): Boolean =
        kotlin.math.abs(deltaPx) <= tolerancePx

    /**
     * Layout-complete translation for `status_bar_contents`.
     *
     * Unmeasured parent → 0. Unmeasured content → user offset only, clamped to half
     * the parent so a bad walk cannot produce a huge translation. Already-centered
     * content within [tolerancePx] contributes 0 auto-correction. Stock native
     * height ([autoCenter] = false) never adds a measured correction.
     */
    @JvmStatic
    fun resolveContentsTranslationY(
        parentTop: Int,
        parentBottom: Int,
        contentTop: Int,
        contentBottom: Int,
        userOffsetPx: Float,
        tolerancePx: Float,
        autoCenter: Boolean = true,
    ): Float {
        if (parentBottom <= parentTop) return 0f
        val parentHeight = parentBottom - parentTop
        if (contentBottom <= contentTop) {
            val half = parentHeight / 2f
            return userOffsetPx.coerceIn(-half, half)
        }
        var correction = 0f
        if (autoCenter) {
            correction = centerCorrectionPx(parentTop, parentBottom, contentTop, contentBottom)
            if (isCenteredWithinTolerance(correction, tolerancePx)) {
                correction = 0f
            }
        }
        return StatusbarViewMaths.clampVerticalOffsetPx(
            correction + userOffsetPx,
            parentHeight,
            contentBottom - contentTop,
        )
    }

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
