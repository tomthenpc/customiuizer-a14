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

    fun applyBoldPreservingFamily(textView: TextView, bold: Boolean) {
        if (!bold) return
        textView.typeface = Typeface.create(textView.typeface, Typeface.BOLD)
    }

    /**
     * Default SystemUI size is left untouched. A user-requested size is kept
     * unless it actually overflows the laid-out parent, in which case it is
     * shrunk once. Autosize is not used.
     */
    fun enableShrinkToFit(
        textView: TextView,
        lineCount: Int,
        lineSpacingMultiplier: Float,
        customSizeRequested: Boolean,
    ) {
        if (!customSizeRequested) return
        val requestedPx = textView.textSize
        if (requestedPx <= 0f) return
        val parentHeight = resolvedHeight(textView)
        if (parentHeight <= 0) return
        val minRequested = HookUtils.dp2px(MIN_TEXT_SIZE_DP).coerceAtLeast(1f)
        val fitted = StatusbarViewMaths.shrinkToFitPx(
            requestedPx,
            parentHeight,
            lineCount,
            lineSpacingMultiplier,
            minRequested,
        )
        if (fitted < requestedPx) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted)
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
        if (textView.height > 0) return textView.height
        val parent = textView.parent as? View
        return parent?.height ?: 0
    }
}
