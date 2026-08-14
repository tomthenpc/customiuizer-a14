package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry for the single Dynamic Island vertical soft motion.
 *
 * The capsule enters from the screen edge that matches [position], grows/slides into a
 * resting state, and exits back to that same edge. Scaling only shrinks vertically
 * ([entranceScaleY] < 1f, pivot at the edge); translation only moves the capsule toward the
 * edge, never beyond the host Window surface.
 */
internal data class DynamicIslandMotionProfile(
    val position: StrongToastPosition,
    val windowHeightPx: Int,
    val capsuleTopMarginPx: Int,
    val capsuleBottomMarginPx: Int,
    val entranceTranslationY: Float,
    val exitTranslationY: Float,
    val maxDragTranslationY: Float,
    val entranceScaleY: Float,
    val restingScaleY: Float,
    val exitScaleY: Float,
    val restingTranslationY: Float,
    val pivotY: Float,
    val entranceDurationMs: Long,
    val exitDurationMs: Long
) {
    val entranceTravelPx: Int
        get() = kotlin.math.abs(entranceTranslationY).toInt()

    val exitTravelPx: Int
        get() = kotlin.math.abs(exitTranslationY).toInt()

    companion object {
        /**
         * Vertical scale factor at the entrance/exit edge.
         * Value must be <= 1f and > 0f to keep the full capsule inside the Window envelope.
         */
        internal const val EDGE_SCALE_Y = 0.88f

        internal const val ENTRANCE_DURATION_TOP_MS = 360L
        internal const val ENTRANCE_DURATION_BOTTOM_MS = 420L
        internal const val EXIT_DURATION_MS = 300L

        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomSafetyMarginPx: Int,
            statusBarInsetPx: Int
        ): DynamicIslandMotionProfile {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topMarginPx.coerceAtLeast(0)
            val bottom = bottomSafetyMarginPx.coerceAtLeast(0)

            val windowHeightPx = maxOf(
                statusBarInsetPx,
                visual + top + bottom
            )

            // Travel just far enough to place the capsule's top at the Window top when
            // it is scaled down. The full scaled capsule is inside the surface.
            val travel = top + ((1f - EDGE_SCALE_Y) * visual).toInt()

            return DynamicIslandMotionProfile(
                position = StrongToastPosition.TOP,
                windowHeightPx = windowHeightPx,
                capsuleTopMarginPx = top,
                capsuleBottomMarginPx = bottom,
                entranceTranslationY = -travel.toFloat(),
                exitTranslationY = -travel.toFloat(),
                maxDragTranslationY = -travel.toFloat(),
                entranceScaleY = EDGE_SCALE_Y,
                restingScaleY = 1f,
                exitScaleY = EDGE_SCALE_Y,
                restingTranslationY = 0f,
                pivotY = visual.toFloat(),
                entranceDurationMs = ENTRANCE_DURATION_TOP_MS,
                exitDurationMs = EXIT_DURATION_MS
            )
        }

        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topSafetyMarginPx: Int,
            bottomPaddingPx: Int
        ): DynamicIslandMotionProfile {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topSafetyMarginPx.coerceAtLeast(0)
            val bottom = bottomPaddingPx.coerceAtLeast(0)

            val windowHeightPx = visual + top + bottom

            val travel = bottom + ((1f - EDGE_SCALE_Y) * visual).toInt()

            return DynamicIslandMotionProfile(
                position = StrongToastPosition.BOTTOM,
                windowHeightPx = windowHeightPx,
                capsuleTopMarginPx = top,
                capsuleBottomMarginPx = 0,
                entranceTranslationY = travel.toFloat(),
                exitTranslationY = travel.toFloat(),
                maxDragTranslationY = travel.toFloat(),
                entranceScaleY = EDGE_SCALE_Y,
                restingScaleY = 1f,
                exitScaleY = EDGE_SCALE_Y,
                restingTranslationY = 0f,
                pivotY = 0f,
                entranceDurationMs = ENTRANCE_DURATION_BOTTOM_MS,
                exitDurationMs = EXIT_DURATION_MS
            )
        }
    }

    /**
     * Verifies that the full transformed capsule bounds stay within the Window surface
     * at the animation extremes (entrance start, rest, and exit end).
     */
    fun capsuleFitsWindow(
        capsuleTopAtRest: Int,
        capsuleHeightPx: Int
    ): Boolean {
        val visual = capsuleHeightPx.coerceAtLeast(0)
        val y = capsuleTopAtRest.coerceAtLeast(0)

        val restTop = y
        val restBottom = y + visual

        val entranceTop = transformedTop(y, visual, entranceTranslationY, entranceScaleY)
        val entranceBottom = transformedBottom(y, visual, entranceTranslationY, entranceScaleY)

        val exitTop = transformedTop(y, visual, exitTranslationY, exitScaleY)
        val exitBottom = transformedBottom(y, visual, exitTranslationY, exitScaleY)

        val minTop = minOf(restTop, entranceTop, exitTop)
        val maxBottom = maxOf(restBottom, entranceBottom, exitBottom)
        return minTop >= 0 && maxBottom <= windowHeightPx
    }

    private fun transformedTop(
        topAtRest: Int,
        visualHeight: Int,
        translationY: Float,
        scaleY: Float
    ): Int = (topAtRest + translationY + pivotY * (1f - scaleY)).toInt()

    private fun transformedBottom(
        topAtRest: Int,
        visualHeight: Int,
        translationY: Float,
        scaleY: Float
    ): Int {
        val belowPivot = (visualHeight.toFloat() - pivotY).coerceAtLeast(0f)
        return (topAtRest + translationY + visualHeight - belowPivot * (1f - scaleY)).toInt()
    }
}
