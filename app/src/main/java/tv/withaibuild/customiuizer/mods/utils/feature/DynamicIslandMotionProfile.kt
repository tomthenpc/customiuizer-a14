package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry and timing for the Dynamic Island transition.
 *
 * The transition has exactly two states, `hidden` and `resting`, and every animated property is
 * a function of the same progress between them:
 *
 * - `translationY` moves between [hiddenTranslationY] and [restingTranslationY]
 * - `scaleX` / `scaleY` move between [hiddenScale] and [restingScale] (uniform, pivot on the
 *   near screen edge, so the pill corners stay circular)
 * - `alpha` moves between `0f` and `1f`
 *
 * Entrance runs hidden -> resting, exit runs resting -> hidden. Because both directions share one
 * geometric definition, an interrupted entrance can hand its current values straight to the exit
 * without a jump, and the capsule reads as one continuous object.
 *
 * Exit fades all the way to `0f` before the ROM tears the Window down. The previous exit stopped
 * at a partially scaled, fully opaque capsule and let the Window removal do the rest, which is
 * what made the disappearance pop.
 *
 * The [windowEnvelope] owns the host height and the resting layout; this class only adds the
 * timing and the derived animation constants.
 */
internal data class DynamicIslandMotionProfile(
    val position: StrongToastPosition,
    val windowEnvelope: DynamicIslandWindowEnvelope,
    val windowHeightPx: Int,
    val capsuleTopMarginPx: Int,
    val capsuleBottomMarginPx: Int,
    val hiddenTranslationY: Float,
    val restingTranslationY: Float,
    val maxDragTranslationY: Float,
    val hiddenScale: Float,
    val restingScale: Float,
    val pivotY: Float,
    val entranceDurationMs: Long,
    val exitDurationMs: Long
) {
    /** Absolute travel between the hidden and resting states, used to normalise drag progress. */
    val edgeTravelPx: Int
        get() = kotlin.math.abs(hiddenTranslationY - restingTranslationY).toInt()

    /** Uniform scale for a drag that has moved [progress] of [edgeTravelPx] toward the edge. */
    fun scaleForProgress(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return restingScale + (hiddenScale - restingScale) * p
    }

    /**
     * Verifies that the full transformed capsule stays within the Window surface at rest and at
     * the hidden end of the transition.
     */
    fun capsuleFitsWindow(
        capsuleTopAtRest: Int,
        capsuleHeightPx: Int
    ): Boolean = windowEnvelope.fitsAllPhases(capsuleHeightPx, capsuleTopAtRest)

    companion object {

        /** Uniform scale at the hidden end of the transition. */
        internal const val EDGE_SCALE = DynamicIslandWindowEnvelope.EDGE_SCALE

        internal const val ENTRANCE_DURATION_TOP_MS = 340L
        internal const val ENTRANCE_DURATION_BOTTOM_MS = 380L

        /**
         * The exit is deliberately shorter than the entrance and runs on an accelerating curve:
         * a dismissal should clear the screen decisively instead of lingering at a nearly-resting
         * pose, which is what produced the visible stall before the Window was removed.
         */
        internal const val EXIT_DURATION_MS = 240L

        internal const val DRAG_RELEASE_DURATION_MS = 220L

        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomSafetyMarginPx: Int,
            statusBarInsetPx: Int,
            roundingSafetyPx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandMotionProfile {
            val envelope = DynamicIslandWindowEnvelope.forTop(
                visualHeightPx,
                topMarginPx,
                bottomSafetyMarginPx,
                roundingSafetyPx,
                maxEdgeTravelPx
            )
            return fromEnvelope(
                envelope = envelope,
                windowHeightPx = maxOf(statusBarInsetPx, envelope.requiredHostHeightPx),
                entranceDurationMs = ENTRANCE_DURATION_TOP_MS,
                exitDurationMs = EXIT_DURATION_MS
            )
        }

        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topSafetyMarginPx: Int,
            bottomPaddingPx: Int,
            roundingSafetyPx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandMotionProfile {
            val envelope = DynamicIslandWindowEnvelope.forBottom(
                visualHeightPx,
                topSafetyMarginPx,
                bottomPaddingPx,
                roundingSafetyPx,
                maxEdgeTravelPx
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
        ): DynamicIslandMotionProfile = DynamicIslandMotionProfile(
            position = envelope.position,
            windowEnvelope = envelope,
            windowHeightPx = windowHeightPx,
            capsuleTopMarginPx = envelope.shellTopMarginPx,
            capsuleBottomMarginPx = envelope.shellBottomMarginPx,
            hiddenTranslationY = envelope.hiddenTranslationY,
            restingTranslationY = 0f,
            maxDragTranslationY = envelope.maxDragTranslationY,
            hiddenScale = envelope.edgeScale,
            restingScale = 1f,
            pivotY = envelope.pivotY,
            entranceDurationMs = entranceDurationMs,
            exitDurationMs = exitDurationMs
        )
    }
}
