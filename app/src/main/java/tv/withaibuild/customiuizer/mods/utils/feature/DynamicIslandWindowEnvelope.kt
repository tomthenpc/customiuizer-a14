package tv.withaibuild.customiuizer.mods.utils.feature

import android.view.Gravity
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry for the Dynamic Island host Window and the capsule's resting position.
 *
 * The ROM-owned Window surface is the only hard boundary: SurfaceFlinger clips anything the
 * module draws outside it. The capsule therefore only ever travels and shrinks *toward* the
 * near screen edge (the status bar edge for [StrongToastPosition.TOP], the navigation edge for
 * [StrongToastPosition.BOTTOM]), with the scale pivot placed on that same edge. That makes the
 * transformed bounds a strict subset of the resting bounds plus the bounded travel, so no
 * animation phase can leave the surface.
 *
 * Scaling is uniform. A vertical-only scale turns the pill's circular corners into ellipses,
 * which reads as a clipped or squashed capsule; a uniform scale keeps the corner radius
 * proportional to the capsule height at every frame.
 *
 * The host height is `margin + capsule + clearance`. The corner radius lives *inside* the capsule
 * rectangle, so it needs no extra Window height; the shape correctness is owned entirely by
 * [DynamicIslandCapsuleView].
 *
 * @property requiredHostHeightPx total Window height needed to contain the capsule at rest
 * @property shellTopMarginPx top margin on the capsule [android.view.ViewGroup.MarginLayoutParams]
 * @property shellBottomMarginPx bottom margin on the capsule [android.view.ViewGroup.MarginLayoutParams]
 * @property parentPaddingTopPx top padding applied to the ROM row that hosts the capsule
 * @property parentPaddingBottomPx bottom padding applied to the ROM row that hosts the capsule
 * @property parentGravity [android.widget.LinearLayout] gravity for that ROM row
 * @property restingShellTopPx capsule top at rest, relative to the Window
 * @property restingShellBottomPx capsule bottom at rest, relative to the Window
 * @property entranceStartTranslationY signed translation the entrance starts from
 * @property maxDragTranslationY signed maximum translation the user can drag toward the edge
 * @property pivotY scale pivot, always on the near screen edge of the capsule
 * @property entranceStartScale uniform scale the entrance starts from
 */
internal data class DynamicIslandWindowEnvelope(
    val position: StrongToastPosition,
    val requiredHostHeightPx: Int,
    val shellTopMarginPx: Int,
    val shellBottomMarginPx: Int,
    val parentPaddingTopPx: Int,
    val parentPaddingBottomPx: Int,
    val parentGravity: Int,
    val restingShellTopPx: Int,
    val restingShellBottomPx: Int,
    val entranceStartTranslationY: Float,
    val maxDragTranslationY: Float,
    val pivotY: Float,
    val entranceStartScale: Float
) {

    /**
     * Verifies that a capsule of [visualHeightPx] placed with its top at [shellTopAtRestPx] stays
     * inside the host Window at rest and at the far end of every transition.
     *
     * Because the pivot sits on the near edge and the travel points at that same edge, the near
     * edge is the only one that moves outward and the far edge only ever moves inward. The exit
     * shrinks to zero, so the entrance start is the widest transformed pose.
     */
    fun fitsAllPhases(visualHeightPx: Int, shellTopAtRestPx: Int): Boolean {
        val visual = visualHeightPx.coerceAtLeast(0)
        val restTop = shellTopAtRestPx.coerceAtLeast(0)
        val restBottom = restTop + visual
        return when (position) {
            StrongToastPosition.TOP ->
                restTop + entranceStartTranslationY >= 0f && restBottom <= requiredHostHeightPx
            StrongToastPosition.BOTTOM ->
                restTop >= 0 && restBottom + entranceStartTranslationY <= requiredHostHeightPx
        }
    }

    companion object {

        /**
         * Uniform scale the entrance starts from. Must be `> 0f` and `<= 1f`; the capsule never
         * grows past its resting bounds, so the resting bounds are the widest it ever gets.
         */
        internal const val ENTRANCE_START_SCALE = 0.90f

        /**
         * Upper bound on the travel toward the near edge. The transition should read as a short
         * settle, not a long slide, and a bounded travel keeps the capsule inside the surface even
         * when the resting offset from the edge is large (bottom position with a tall gesture inset).
         */
        internal const val MAX_EDGE_TRAVEL_DP = 20f

        /**
         * Build an envelope for the top (status-bar-side) island.
         *
         * The capsule rests [topMarginPx] below the Window top and keeps [bottomClearancePx] of
         * clearance underneath so the ROM content and the expanded touch region stay inside the
         * surface.
         */
        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomClearancePx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topMarginPx.coerceAtLeast(0)
            val bottom = bottomClearancePx.coerceAtLeast(0)

            // The capsule slides up to at most the Window top edge, which coincides with the
            // screen top edge, so the travel can never expose a clipped capsule.
            val travel = top.coerceAtMost(maxEdgeTravelPx.coerceAtLeast(0))

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.TOP,
                requiredHostHeightPx = visual + top + bottom,
                shellTopMarginPx = top,
                shellBottomMarginPx = 0,
                parentPaddingTopPx = 0,
                parentPaddingBottomPx = bottom,
                parentGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top,
                restingShellBottomPx = top + visual,
                entranceStartTranslationY = -travel.toFloat(),
                maxDragTranslationY = -travel.toFloat(),
                pivotY = 0f,
                entranceStartScale = ENTRANCE_START_SCALE
            )
        }

        /**
         * Build an envelope for the bottom island.
         *
         * The capsule rests [bottomPaddingPx] above the Window bottom and keeps [topClearancePx]
         * of clearance above it.
         */
        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topClearancePx: Int,
            bottomPaddingPx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topClearancePx.coerceAtLeast(0)
            val bottom = bottomPaddingPx.coerceAtLeast(0)

            val travel = bottom.coerceAtMost(maxEdgeTravelPx.coerceAtLeast(0))

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.BOTTOM,
                requiredHostHeightPx = visual + top + bottom,
                shellTopMarginPx = 0,
                shellBottomMarginPx = bottom,
                parentPaddingTopPx = top,
                parentPaddingBottomPx = 0,
                parentGravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top,
                restingShellBottomPx = top + visual,
                entranceStartTranslationY = travel.toFloat(),
                maxDragTranslationY = travel.toFloat(),
                pivotY = visual.toFloat(),
                entranceStartScale = ENTRANCE_START_SCALE
            )
        }
    }
}
