package tv.withaibuild.customiuizer.mods.utils

/**
 * Pure, Android-independent math helpers for status bar view insertion.
 *
 * These functions intentionally do not touch any Android classes so they can be
 * unit-tested on the JVM without a robolectric or device environment.
 */
object StatusbarViewMaths {

    /**
     * Clamp a requested insert index to a safe [0, childCount] range.
     *
     * @param requested the index requested by the caller
     * @param childCount the current child count of the target ViewGroup
     * @return a safe index that is guaranteed not to throw
     *         [IndexOutOfBoundsException] when used with [android.view.ViewGroup.addView]
     */
    @JvmStatic
    fun clampStatusIconInsertIndex(requested: Int, childCount: Int): Int = when {
        requested < 0 -> 0
        requested > childCount -> childCount
        else -> requested
    }

    /**
     * Custom status-bar font size sentinel: `0` keeps the SystemUI TextAppearance size.
     * Positive preference values are stored as twice the requested dip.
     */
    @JvmStatic
    fun resolveCustomTextSizeDp(rawValue: Int, divider: Float = 0.5f): Float? =
        if (rawValue > 0) rawValue * divider else null

    /**
     * Vertical space occupied by [lineCount] lines of [fontHeightPx] with Android
     * `setLineSpacing(0, multiplier)` (extra = 0).
     */
    @JvmStatic
    fun occupiedHeightPx(fontHeightPx: Float, lineCount: Int, lineSpacingMultiplier: Float): Float {
        val lines = lineCount.coerceAtLeast(1)
        val multiplier = if (lineSpacingMultiplier <= 0f) 1f else lineSpacingMultiplier
        return fontHeightPx * (1f + (lines - 1) * multiplier)
    }

    /**
     * Shrink-to-fit only. Never enlarges [requestedPx]. If the parent has not been
     * laid out yet ([parentHeightPx] <= 0), the requested size is kept so text is
     * not collapsed to 0 before the first layout.
     */
    @JvmStatic
    fun shrinkToFitPx(
        requestedPx: Float,
        parentHeightPx: Int,
        lineCount: Int,
        lineSpacingMultiplier: Float,
        minPx: Float,
    ): Float {
        if (requestedPx <= 0f) return requestedPx
        if (parentHeightPx <= 0) return requestedPx
        val occupied = occupiedHeightPx(requestedPx, lineCount, lineSpacingMultiplier)
        if (occupied <= parentHeightPx) return requestedPx
        val lines = lineCount.coerceAtLeast(1)
        val multiplier = if (lineSpacingMultiplier <= 0f) 1f else lineSpacingMultiplier
        val maxFont = parentHeightPx / (1f + (lines - 1) * multiplier)
        val floor = if (minPx > 0f) minPx else 1f
        return maxFont.coerceAtLeast(floor.coerceAtMost(requestedPx)).coerceAtMost(requestedPx)
    }

    /**
     * Clamp a vertical translation so the text block stays inside the parent.
     * Unlaid-out views ([parentHeightPx] or [textHeightPx] <= 0) keep the requested
     * offset instead of collapsing it to 0.
     */
    @JvmStatic
    fun clampVerticalOffsetPx(
        requestedOffsetPx: Float,
        parentHeightPx: Int,
        textHeightPx: Int,
    ): Float {
        if (parentHeightPx <= 0 || textHeightPx <= 0) return requestedOffsetPx
        val slack = ((parentHeightPx - textHeightPx) / 2f).coerceAtLeast(0f)
        return requestedOffsetPx.coerceIn(-slack, slack)
    }
}
