package tv.withaibuild.customiuizer.mods.utils.feature

import android.view.Gravity
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

/**
 * Pure, testable geometry for the Dynamic Island host Window and the shell's resting position.
 *
 * The ROM-owned [Window surface] is the only hard boundary. The module shell must stay fully
 * inside it at every animation phase: entrance start, rest, and exit end. A small
 * [roundingSafetyPx] margin is reserved because `Math.round()` in the RenderNode can push a
 * transformed View half a pixel outside its enclosing surface.
 *
 * @property requiredHostHeightPx total Window height needed to contain the shell through all phases
 * @property shellTopMarginPx top margin on the shell [ViewGroup.MarginLayoutParams]
 * @property shellBottomMarginPx bottom margin on the shell [ViewGroup.MarginLayoutParams]
 * @property parentPaddingTopPx top padding to give the parent so the shell far edge clears the Window
 * @property parentPaddingBottomPx bottom padding to give the parent so the shell far edge clears the Window
 * @property parentGravity [android.widget.LinearLayout] gravity for the parent
 * @property restingShellTopPx top coordinate of the shell at rest, relative to its parent
 * @property restingShellBottomPx bottom coordinate of the shell at rest, relative to its parent
 * @property entranceTranslationY signed translation applied to the shell at the start of entrance
 * @property exitTranslationY signed translation applied to the shell at the end of the exit
 * @property maxDragTranslationY signed maximum translation the user can drag toward the edge
 * @property pivotY pivot used for the vertical scale transform
 * @property edgeScaleY scale at the entrance/exit edge
 * @property roundingSafetyPx the rounding/pixel-safety margin included in the host height
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
    val entranceTranslationY: Float,
    val exitTranslationY: Float,
    val maxDragTranslationY: Float,
    val pivotY: Float,
    val edgeScaleY: Float
) {

    /**
     * Verifies that the shell, at [visualHeightPx] and placed with its top at [shellTopAtRestPx],
     * is fully inside the host Window at the entrance start, at rest, and at the exit end.
     */
    fun fitsAllPhases(visualHeightPx: Int, shellTopAtRestPx: Int): Boolean {
        val visual = visualHeightPx.coerceAtLeast(0)
        val restTop = shellTopAtRestPx.coerceAtLeast(0)

        val restBottom = restTop + visual
        val entranceTop = transformedTop(restTop, visual, entranceTranslationY, edgeScaleY)
        val entranceBottom = transformedBottom(restTop, visual, entranceTranslationY, edgeScaleY)
        val exitTop = transformedTop(restTop, visual, exitTranslationY, edgeScaleY)
        val exitBottom = transformedBottom(restTop, visual, exitTranslationY, edgeScaleY)

        val minTop = minOf(restTop, entranceTop, exitTop)
        val maxBottom = maxOf(restBottom, entranceBottom, exitBottom)
        return minTop >= 0 && maxBottom <= requiredHostHeightPx
    }

    private fun transformedTop(
        restTop: Int,
        visualHeight: Int,
        translationY: Float,
        scaleY: Float
    ): Int = (restTop + translationY + pivotY * (1f - scaleY)).toInt()

    private fun transformedBottom(
        restTop: Int,
        visualHeight: Int,
        translationY: Float,
        scaleY: Float
    ): Int {
        val belowPivot = (visualHeight.toFloat() - pivotY).coerceAtLeast(0f)
        return (restTop + translationY + visualHeight - belowPivot * (1f - scaleY)).toInt()
    }

    companion object {

        /**
         * Vertical scale factor at the entrance/exit edge.
         * Must be <= 1f and > 0f to keep the full capsule inside the Window envelope.
         */
        internal const val EDGE_SCALE_Y = 0.88f

        /**
         * Build an envelope for the top (status-bar-side) island.
         *
         * The shell rests [topMarginPx] from the top and [bottomSafetyPx] from the bottom.
         * The Window height is [visualHeightPx] + [topMarginPx] + [bottomSafetyPx] +
         * [roundingSafetyPx] so the scaled entrance/exit can start [roundingSafetyPx] inside
         * the top edge without clipping.
         */
        @JvmStatic
        internal fun forTop(
            visualHeightPx: Int,
            topMarginPx: Int,
            bottomSafetyPx: Int,
            roundingSafetyPx: Int
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topMarginPx.coerceAtLeast(0)
            val bottom = bottomSafetyPx.coerceAtLeast(0)
            val rounding = roundingSafetyPx.coerceAtLeast(0)

            val requiredHostHeightPx = visual + top + bottom + rounding

            // Shell top margin places it [top] px from the parent top.
            // Parent bottom padding = [bottom] + [rounding] makes the parent height H.
            // Travel = [top] - rounding + (1 - EDGE_SCALE_Y) * visual so the scaled shell
            // top starts at [rounding] px, i.e. just inside the Window top edge.
            val travel = top - rounding + ((1f - EDGE_SCALE_Y) * visual).toInt()

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.TOP,
                roundingSafetyPx = rounding,
                requiredHostHeightPx = requiredHostHeightPx,
                shellTopMarginPx = top,
                shellBottomMarginPx = 0,
                parentPaddingTopPx = 0,
                parentPaddingBottomPx = bottom + rounding,
                parentGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top,
                restingShellBottomPx = top + visual,
                entranceTranslationY = -travel.toFloat(),
                exitTranslationY = -travel.toFloat(),
                maxDragTranslationY = -travel.toFloat(),
                pivotY = visual.toFloat(),
                edgeScaleY = EDGE_SCALE_Y
            )
        }

        /**
         * Build an envelope for the bottom island.
         *
         * The shell rests [bottomPaddingPx] above the bottom and has [topSafetyPx] clearance
         * at the top. The Window height is [visualHeightPx] + [topSafetyPx] + [bottomPaddingPx]
         * + [roundingSafetyPx].
         */
        @JvmStatic
        internal fun forBottom(
            visualHeightPx: Int,
            topSafetyPx: Int,
            bottomPaddingPx: Int,
            roundingSafetyPx: Int
        ): DynamicIslandWindowEnvelope {
            val visual = visualHeightPx.coerceAtLeast(0)
            val top = topSafetyPx.coerceAtLeast(0)
            val bottom = bottomPaddingPx.coerceAtLeast(0)
            val rounding = roundingSafetyPx.coerceAtLeast(0)

            val requiredHostHeightPx = visual + top + bottom + rounding

            // Parent top padding = [top] + [rounding] and shell bottom margin = [bottom] make
            // the parent height H and place the shell's bottom H - [bottom] px from the top.
            // Travel = [bottom] - rounding + (1 - EDGE_SCALE_Y) * visual so the scaled shell
            // bottom starts at H - [rounding] px, i.e. just inside the Window bottom edge.
            val travel = bottom - rounding + ((1f - EDGE_SCALE_Y) * visual).toInt()

            return DynamicIslandWindowEnvelope(
                position = StrongToastPosition.BOTTOM,
                roundingSafetyPx = rounding,
                requiredHostHeightPx = requiredHostHeightPx,
                shellTopMarginPx = 0,
                shellBottomMarginPx = bottom,
                parentPaddingTopPx = top + rounding,
                parentPaddingBottomPx = 0,
                parentGravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                restingShellTopPx = top + rounding,
                restingShellBottomPx = top + rounding + visual,
                entranceTranslationY = travel.toFloat(),
                exitTranslationY = travel.toFloat(),
                maxDragTranslationY = travel.toFloat(),
                pivotY = 0f,
                edgeScaleY = EDGE_SCALE_Y
            )
        }
    }
}
