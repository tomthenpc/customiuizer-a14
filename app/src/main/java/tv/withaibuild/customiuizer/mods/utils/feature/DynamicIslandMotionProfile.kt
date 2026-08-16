package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry and timing for the Dynamic Island transition.
 *
 * Both directions are pure geometry; alpha is never animated. The capsule is a solid object that
 * settles in from a slightly smaller pose and later shrinks away entirely:
 *
 * - entrance: [entranceStartScale] / [entranceStartTranslationY] -> [restingScale] / [restingTranslationY]
 * - exit: resting -> [exitEndScale] / [exitEndTranslationY]
 *
 * The entrance stops short of the edge because a solid capsule that is already fully drawn reads
 * as "settling into place". The exit goes all the way to zero: a partially scaled but fully opaque
 * capsule removed by the Window teardown is what made the disappearance pop, and fading it out
 * instead is not wanted - the island must look like it retracts, not like it dissolves.
 *
 * Scaling is always uniform, so the pill's corners stay circular at every frame.
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
    val restingTranslationY: Float,
    val restingScale: Float,
    val entranceStartTranslationY: Float,
    val entranceStartScale: Float,
    val exitEndTranslationY: Float,
    val exitEndScale: Float,
    val maxDragTranslationY: Float,
    val pivotY: Float,
    val entranceDurationMs: Long,
    val exitDurationMs: Long
) {
    /** Absolute travel between the resting and dragged-out states, used to normalise drag progress. */
    val edgeTravelPx: Int
        get() = kotlin.math.abs(maxDragTranslationY - restingTranslationY).toInt()

    /** Uniform scale for a drag that has moved [progress] of [edgeTravelPx] toward the edge. */
    fun scaleForProgress(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return restingScale + (entranceStartScale - restingScale) * p
    }

    /**
     * Verifies that the full transformed capsule stays within the Window surface at rest and at
     * the far end of every transition.
     */
    fun capsuleFitsWindow(
        capsuleTopAtRest: Int,
        capsuleHeightPx: Int
    ): Boolean = windowEnvelope.fitsAllPhases(capsuleHeightPx, capsuleTopAtRest)

    companion object {

        /** Uniform scale the entrance starts from. */
        internal const val ENTRANCE_START_SCALE = DynamicIslandWindowEnvelope.ENTRANCE_START_SCALE

        /**
         * Uniform scale the exit ends on. The capsule collapses into the pivot point on the near
         * screen edge, so the Window teardown happens after the island has no geometry left.
         */
        internal const val EXIT_END_SCALE = 0f

        internal const val ENTRANCE_DURATION_TOP_MS = 340L
        internal const val ENTRANCE_DURATION_BOTTOM_MS = 380L

        /**
         * The exit is deliberately shorter than the entrance and runs on an accelerating curve:
         * a dismissal should clear the screen decisively instead of lingering at a nearly-resting
         * pose, which is what produced the visible stall before the Window was removed.
         */
        internal const val EXIT_DURATION_MS = 220L

        internal const val DRAG_RELEASE_DURATION_MS = 220L

        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomClearancePx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandMotionProfile = fromEnvelope(
            envelope = DynamicIslandWindowEnvelope.forTop(
                visualHeightPx,
                topMarginPx,
                bottomClearancePx,
                maxEdgeTravelPx
            ),
            entranceDurationMs = ENTRANCE_DURATION_TOP_MS
        )

        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topClearancePx: Int,
            bottomPaddingPx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandMotionProfile = fromEnvelope(
            envelope = DynamicIslandWindowEnvelope.forBottom(
                visualHeightPx,
                topClearancePx,
                bottomPaddingPx,
                maxEdgeTravelPx
            ),
            entranceDurationMs = ENTRANCE_DURATION_BOTTOM_MS
        )

        private fun fromEnvelope(
            envelope: DynamicIslandWindowEnvelope,
            entranceDurationMs: Long
        ): DynamicIslandMotionProfile = DynamicIslandMotionProfile(
            position = envelope.position,
            windowEnvelope = envelope,
            windowHeightPx = envelope.requiredHostHeightPx,
            capsuleTopMarginPx = envelope.shellTopMarginPx,
            capsuleBottomMarginPx = envelope.shellBottomMarginPx,
            restingTranslationY = 0f,
            restingScale = 1f,
            entranceStartTranslationY = envelope.entranceStartTranslationY,
            entranceStartScale = envelope.entranceStartScale,
            // The exit reuses the entrance travel: the capsule retracts toward the same near
            // screen edge it settled in from, and the zero end scale removes it from there.
            exitEndTranslationY = envelope.entranceStartTranslationY,
            exitEndScale = EXIT_END_SCALE,
            maxDragTranslationY = envelope.maxDragTranslationY,
            pivotY = envelope.pivotY,
            entranceDurationMs = entranceDurationMs,
            exitDurationMs = EXIT_DURATION_MS
        )
    }
}
