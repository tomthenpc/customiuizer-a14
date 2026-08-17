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
     * Caps auto-size at the current (system or requested) size so the framework
     * may only shrink. If the view has no size yet, auto-size still defers fit
     * to the first layout instead of collapsing to 0sp.
     */
    fun enableShrinkToFit(textView: TextView, lineCount: Int, lineSpacingMultiplier: Float) {
        val requestedPx = textView.textSize
        if (requestedPx <= 0f) return
        val minRequested = HookUtils.dp2px(MIN_TEXT_SIZE_DP).coerceAtLeast(1f)
        val parentHeight = resolvedHeight(textView)
        val fitted = StatusbarViewMaths.shrinkToFitPx(
            requestedPx,
            parentHeight,
            lineCount,
            lineSpacingMultiplier,
            minRequested,
        )
        if (parentHeight > 0 && fitted < requestedPx) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted)
        }
        val maxPx = (if (parentHeight > 0) fitted else requestedPx).roundToInt().coerceAtLeast(1)
        val minPx = minRequested.roundToInt().coerceAtMost(maxPx).coerceAtLeast(1)
        try {
            textView.setAutoSizeTextTypeUniformWithConfiguration(
                minPx,
                maxPx,
                1,
                TypedValue.COMPLEX_UNIT_PX,
            )
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (td: ThreadDeath) {
            throw td
        } catch (vme: VirtualMachineError) {
            throw vme
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    fun applyVerticalOffset(textView: TextView, requestedOffsetPx: Float) {
        XposedHelpers.setAdditionalInstanceField(textView, OFFSET_GUARD, requestedOffsetPx)
        applyVerticalOffsetNow(textView, requestedOffsetPx)
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
