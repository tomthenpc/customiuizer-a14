package tv.withaibuild.customiuizer.mods.utils

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import tv.withaibuild.customiuizer.utils.HookUtils
import kotlin.math.roundToInt

/**
 * Shared TextView metrics for status-bar custom text. No lifecycle owner, no
 * preference layer: callers apply this on view create, preference change,
 * configuration change, or layout size change — never on a 2s ticker.
 */
internal object StatusBarTextFit {

    const val MIN_TEXT_SIZE_DP = 6f
    private const val OFFSET_GUARD = "customiuizer_sb_text_offset"
    private const val OFFSET_LISTENER = "customiuizer_sb_text_offset_listener"
    private const val SHRINK_REQUESTED = "customiuizer_sb_text_shrink_requested"
    private const val SHRINK_LINES = "customiuizer_sb_text_shrink_lines"
    private const val SHRINK_SPACING = "customiuizer_sb_text_shrink_spacing"
    private const val SHRINK_LISTENER = "customiuizer_sb_text_shrink_listener"

    fun applyBoldPreservingFamily(textView: TextView, bold: Boolean) {
        if (!bold) return
        textView.typeface = Typeface.create(textView.typeface, Typeface.BOLD)
    }

    /**
     * Default SystemUI size is left untouched. A user-requested or dual-line size
     * is kept unless FontMetrics overflow the laid-out row, in which case it is
     * shrunk once. Autosize is not used. Retry happens on layout, not on text.
     */
    fun enableShrinkToFit(
        textView: TextView,
        lineCount: Int,
        lineSpacingMultiplier: Float,
        customSizeRequested: Boolean,
    ) {
        if (!customSizeRequested) {
            XposedHelpers.removeAdditionalInstanceField(textView, SHRINK_REQUESTED)
            return
        }
        val requestedPx = textView.textSize
        if (requestedPx <= 0f) return
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_REQUESTED, requestedPx)
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_LINES, lineCount)
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_SPACING, lineSpacingMultiplier)
        applyShrinkNow(textView)
        if (XposedHelpers.getAdditionalInstanceField(textView, SHRINK_LISTENER) != null) return
        val listener = View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@OnLayoutChangeListener
            val tv = v as? TextView ?: return@OnLayoutChangeListener
            applyShrinkNow(tv)
        }
        textView.addOnLayoutChangeListener(listener)
        XposedHelpers.setAdditionalInstanceField(textView, SHRINK_LISTENER, true)
    }

    private fun applyShrinkNow(textView: TextView) {
        val requestedPx = XposedHelpers.getAdditionalInstanceField(textView, SHRINK_REQUESTED) as? Float ?: return
        val lineCount = XposedHelpers.getAdditionalInstanceField(textView, SHRINK_LINES) as? Int ?: 1
        val lineSpacingMultiplier = XposedHelpers.getAdditionalInstanceField(textView, SHRINK_SPACING) as? Float
            ?: textView.lineSpacingMultiplier
        val parentHeight = resolvedHeight(textView)
        if (parentHeight <= 0) return
        val minRequested = HookUtils.dp2px(MIN_TEXT_SIZE_DP).coerceAtLeast(1f)
        val fm = textView.paint.fontMetrics
        val fontH = (fm.bottom - fm.top).coerceAtLeast(textView.textSize)
        val fitted = StatusbarViewMaths.shrinkToFitPx(
            requestedPx,
            parentHeight,
            lineCount,
            lineSpacingMultiplier,
            minRequested,
        )
        val metricsFitted = StatusbarViewMaths.shrinkToFitPx(
            fontH,
            parentHeight,
            lineCount,
            lineSpacingMultiplier,
            minRequested,
        )
        val scale = if (fontH > 0f) metricsFitted / fontH else 1f
        val next = (requestedPx * scale).coerceAtMost(fitted).coerceAtMost(requestedPx)
        if (kotlin.math.abs(next - textView.textSize) > 0.5f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, next)
        }
    }

    fun applyVerticalOffset(textView: TextView, requestedOffsetPx: Float) {
        XposedHelpers.setAdditionalInstanceField(textView, OFFSET_GUARD, requestedOffsetPx)
        applyVerticalOffsetNow(textView, requestedOffsetPx)
        if (requestedOffsetPx == 0f) return
        if (XposedHelpers.getAdditionalInstanceField(textView, OFFSET_LISTENER) != null) return
        val listener = View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop && (bottom - top) > 0) return@OnLayoutChangeListener
            val tv = v as? TextView ?: return@OnLayoutChangeListener
            val requested = XposedHelpers.getAdditionalInstanceField(tv, OFFSET_GUARD) as? Float ?: return@OnLayoutChangeListener
            applyVerticalOffsetNow(tv, requested)
        }
        textView.addOnLayoutChangeListener(listener)
        XposedHelpers.setAdditionalInstanceField(textView, OFFSET_LISTENER, true)
    }

    private fun applyVerticalOffsetNow(textView: TextView, requestedOffsetPx: Float) {
        val parentHeight = resolvedHeight(textView)
        val fm = textView.paint.fontMetrics
        val fontH = fm.descent - fm.ascent
        val lineCount = textView.maxLines.coerceAtLeast(1)
        val textH = StatusbarViewMaths.occupiedHeightPx(
            fontH,
            lineCount,
            textView.lineSpacingMultiplier,
        ).roundToInt()
        textView.translationY = StatusbarViewMaths.clampVerticalOffsetPx(
            requestedOffsetPx,
            parentHeight,
            textH,
        )
    }

    private fun resolvedHeight(textView: TextView): Int {
        val parent = textView.parent as? View
        return StatusbarViewMaths.resolvedTextFitHeightPx(textView.height, parent?.height ?: 0)
    }
}
