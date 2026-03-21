package com.minimal.launcher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Full-screen overlay that darkens everything except a rounded-rect "spotlight" cutout.
 * Uses software rendering so PorterDuff.CLEAR works reliably across all devices.
 */
class SpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 0, 0, 0)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var spotRect: RectF? = null
    private val radius = 18f * context.resources.displayMetrics.density

    init {
        // Required for PorterDuff.CLEAR to work correctly
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun highlight(rect: RectF?) {
        spotRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        spotRect?.let { canvas.drawRoundRect(it, radius, radius, clearPaint) }
        canvas.restoreToCount(sc)
    }
}
