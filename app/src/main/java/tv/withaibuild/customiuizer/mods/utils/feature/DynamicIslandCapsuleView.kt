package tv.withaibuild.customiuizer.mods.utils.feature

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.FrameLayout

/**
 * Module-owned Dynamic Island capsule.
 *
 * The pill is painted directly on the [Canvas] instead of being delegated to a
 * [android.graphics.drawable.GradientDrawable] background. HyperOS 1 enables
 * `persist.sys.support_view_smoothcorner` and substitutes its own smooth-corner
 * implementation for the platform rounded-rect drawables, so a `GradientDrawable`
 * background does not reproduce the measured View bounds exactly and the resulting
 * pill loses pixels at one edge. Painting here keeps the visible shape identical to
 * [getWidth] x [getHeight] on every ROM and keeps the corner radius a pure function
 * of the capsule height.
 *
 * The capsule never clips: the rounded shape is drawn, not applied as an outline, so
 * ROM content inside the capsule keeps its own drawing bounds.
 */
@SuppressLint("ViewConstructor")
internal class DynamicIslandCapsuleView(context: Context) : FrameLayout(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val shape = RectF()

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        clipToOutline = false
        background = null
    }

    /** Capsule fill colour. Kept settable so a future themed island can reuse this View. */
    var fillColor: Int
        get() = fillPaint.color
        set(value) {
            if (fillPaint.color != value) {
                fillPaint.color = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        shape.set(0f, 0f, w, h)
        val radius = h / 2f
        canvas.drawRoundRect(shape, radius, radius, fillPaint)
    }
}
