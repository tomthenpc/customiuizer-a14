package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry and timing for the single Dynamic Island vertical soft motion.
 *
 * The capsule enters from the screen edge that matches [position], grows/slides into a
 * resting state, and exits back to that same edge. Scaling only shrinks vertically
 * ([entranceScaleY] < 1f, pivot at the edge); translation only moves the capsule toward the
 * edge, never beyond the host Window surface.
 *
 * The [windowEnvelope] owns the host height and margin layout; this class adds the
 * animation constants (scale, duration) that live on the shell [ViewPropertyAnimator].
 */
internal data class DynamicIslandMotionProfile(
    val position: StrongToastPosition,
    val windowEnvelope: DynamicIslandWindowEnvelope,
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

    /**
     * Verifies that the full transformed capsule bounds stay within the Window surface
     * at the animation extremes (entrance start, rest, and exit end).
     */
    fun capsuleFitsWindow(
        capsuleTopAtRest: Int,
        capsuleHeightPx: Int
    ): Boolean = windowEnvelope.fitsAllPhases(capsuleHeightPx, capsuleTopAtRest)

    companion object {

        /**
         * Vertical scale factor at the entrance/exit edge.
         * Value must be <= 1f and > 0f to keep the full capsule inside the Window envelope.
         */
        internal const val EDGE_SCALE_Y = DynamicIslandWindowEnvelope.EDGE_SCALE_Y

        internal const val ENTRANCE_DURATION_TOP_MS = 360L
        internal const val ENTRANCE_DURATION_BOTTOM_MS = 420L
        internal const val EXIT_DURATION_MS = 300L

        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomSafetyMarginPx: Int,
            statusBarInsetPx: Int,
            roundingSafetyPx: Int = 0
        ): DynamicIslandMotionProfile {
            val envelope = DynamicIslandWindowEnvelope.forTop(
                visualHeightPx,
                topMarginPx,
                bottomSafetyMarginPx,
                roundingSafetyPx
            )
            val windowHeightPx = maxOf(statusBarInsetPx, envelope.requiredHostHeightPx)
            return fromEnvelope(
                envelope = envelope,
                windowHeightPx = windowHeightPx,
                entranceDurationMs = ENTRANCE_DURATION_TOP_MS,
                exitDurationMs = EXIT_DURATION_MS
            )
        }

        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topSafetyMarginPx: Int,
            bottomPaddingPx: Int,
            roundingSafetyPx: Int = 0
        ): DynamicIslandMotionProfile {
            val envelope = DynamicIslandWindowEnvelope.forBottom(
                visualHeightPx,
                topSafetyMarginPx,
                bottomPaddingPx,
                roundingSafetyPx
            )
            return fromEnvelope(
                envelope = envelope,
                windowHeightPx = envelope.requiredHostHeightPx,
                entranceDurationMs = ENTRANCE_DURATION_BOTTOM_MS,
                exitDurationMs = EXIT_DURATION_MS
            )
        }

        private fun fromEnvelope(
            envelope: DynamicIslandWindowEnvelope,
            windowHeightPx: Int,
            entranceDurationMs: Long,
            exitDurationMs: Long
        ): DynamicIslandMotionProfile {
            return DynamicIslandMotionProfile(
                position = envelope.position,
                windowEnvelope = envelope,
                windowHeightPx = windowHeightPx,
                capsuleTopMarginPx = envelope.shellTopMarginPx,
                capsuleBottomMarginPx = envelope.shellBottomMarginPx,
                entranceTranslationY = envelope.entranceTranslationY,
                exitTranslationY = envelope.exitTranslationY,
                maxDragTranslationY = envelope.maxDragTranslationY,
                entranceScaleY = envelope.edgeScaleY,
                restingScaleY = 1f,
                exitScaleY = envelope.edgeScaleY,
                restingTranslationY = 0f,
                pivotY = envelope.pivotY,
                entranceDurationMs = entranceDurationMs,
                exitDurationMs = exitDurationMs
            )
        }
    }
}
