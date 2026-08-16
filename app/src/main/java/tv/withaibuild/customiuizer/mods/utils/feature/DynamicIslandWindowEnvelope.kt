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
 * Scaling is uniform ([edgeScale] applies to both axes). A vertical-only scale turns the pill's
 * circular corners into ellipses, which reads as a clipped or squashed capsule; a uniform scale
 * keeps the corner radius proportional to the capsule height at every frame.
 *
 * @property requiredHostHeightPx total Window height needed to contain the capsule at rest
 * @property shellTopMarginPx top margin on the capsule [android.view.ViewGroup.MarginLayoutParams]
 * @property shellBottomMarginPx bottom margin on the capsule [android.view.ViewGroup.MarginLayoutParams]
 * @property parentPaddingTopPx top padding applied to the ROM row that hosts the capsule
 * @property parentPaddingBottomPx bottom padding applied to the ROM row that hosts the capsule
 * @property parentGravity [android.widget.LinearLayout] gravity for that ROM row
 * @property restingShellTopPx capsule top at rest, relative to the Window
 * @property restingShellBottomPx capsule bottom at rest, relative to the Window
 * @property hiddenTranslationY signed translation at the hidden end of the transition
 * @property maxDragTranslationY signed maximum translation the user can drag toward the edge
 * @property pivotY scale pivot, always on the near screen edge of the capsule
 * @property edgeScale uniform scale at the hidden end of the transition
 * @property roundingSafetyPx rounding/pixel-safety margin included in the host height
 */
internal data class DynamicIslandWindowEnvelope(
    val position: StrongToastPosition,
    val roundingSafetyPx: Int,
    val requiredHostHeightPx: Int,
    val shellTopMarginPx: Int,
    val shellBottomMarginPx: Int,
    val parentPaddingTopPx: Int,
    val parentPaddingBottomPx: Int,
    val parentGravity: Int,
    val restingShellTopPx: Int,
    val restingShellBottomPx: Int,
    val hiddenTranslationY: Float,
    val maxDragTranslationY: Float,
    val pivotY: Float,
    val edgeScale: Float
) {

    /**
     * Verifies that a capsule of [visualHeightPx] placed with its top at [shellTopAtRestPx] stays
     * inside the host Window at rest and at the hidden end of the transition.
     *
     * Because the pivot sits on the near edge and [hiddenTranslationY] points at that same edge,
     * the near edge is the only one that moves outward and the far edge only ever moves inward.
     */
    fun fitsAllPhases(visualHeightPx: Int, shellTopAtRestPx: Int): Boolean {
        val visual = visualHeightPx.coerceAtLeast(0)
        val restTop = shellTopAtRestPx.coerceAtLeast(0)
        val restBottom = restTop + visual
        return when (position) {
            StrongToastPosition.TOP ->
                restTop + hiddenTranslationY >= 0f && restBottom <= requiredHostHeightPx
            StrongToastPosition.BOTTOM ->
                restTop >= 0 && restBottom + hiddenTranslationY <= requiredHostHeightPx
        }
    }

    companion object {

        /**
         * Uniform scale at the hidden end of the transition. Must be `> 0f` and `<= 1f`; the
         * capsule only ever shrinks, so the resting bounds are the widest it ever gets.
         */
        internal const val EDGE_SCALE = 0.90f

        /**
         * Upper bound on the travel toward the near edge. The transition should read as a short
         * settle, not a long slide, and a bounded travel keeps the capsule inside the surface even
         * when the resting offset from the edge is large (bottom position with a tall gesture inset).
         */
        internal const val MAX_EDGE_TRAVEL_DP = 20f

        /**
         * Build an envelope for the top (status-bar-side) island.
         *
         * The capsule rests [topMarginPx] below the Window top and keeps [bottomSafetyPx] +
         * [roundingSafetyPx] of clearance underneath so the ROM content, the expanded touch region
         * and pixel rounding all stay inside the surface.
         */
        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomSafetyPx: Int,
            roundingSafetyPx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topMarginPx.coerceAtLeast(0)
            val bottom = bottomSafetyPx.coerceAtLeast(0)
            val rounding = roundingSafetyPx.coerceAtLeast(0)

            // The capsule slides up to at most the Window top edge, which coincides with the
            // screen top edge, so the travel can never expose a clipped capsule.
            val travel = top.coerceAtMost(maxEdgeTravelPx.coerceAtLeast(0))

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.TOP,
                roundingSafetyPx = rounding,
                requiredHostHeightPx = visual + top + bottom + rounding,
                shellTopMarginPx = top,
                shellBottomMarginPx = 0,
                parentPaddingTopPx = 0,
                parentPaddingBottomPx = bottom + rounding,
                parentGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top,
                restingShellBottomPx = top + visual,
                hiddenTranslationY = -travel.toFloat(),
                maxDragTranslationY = -travel.toFloat(),
                pivotY = 0f,
                edgeScale = EDGE_SCALE
            )
        }

        /**
         * Build an envelope for the bottom island.
         *
         * The capsule rests [bottomPaddingPx] above the Window bottom and keeps [topSafetyPx] +
         * [roundingSafetyPx] of clearance above it.
         */
        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topSafetyPx: Int,
            bottomPaddingPx: Int,
            roundingSafetyPx: Int,
            maxEdgeTravelPx: Int
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topSafetyPx.coerceAtLeast(0)
            val bottom = bottomPaddingPx.coerceAtLeast(0)
            val rounding = roundingSafetyPx.coerceAtLeast(0)

            val travel = bottom.coerceAtMost(maxEdgeTravelPx.coerceAtLeast(0))

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.BOTTOM,
                roundingSafetyPx = rounding,
                requiredHostHeightPx = visual + top + bottom + rounding,
                shellTopMarginPx = 0,
                shellBottomMarginPx = bottom,
                parentPaddingTopPx = top + rounding,
                parentPaddingBottomPx = 0,
                parentGravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top + rounding,
                restingShellBottomPx = top + rounding + visual,
                hiddenTranslationY = travel.toFloat(),
                maxDragTranslationY = travel.toFloat(),
                pivotY = visual.toFloat(),
                edgeScale = EDGE_SCALE
            )
        }
    }
}
