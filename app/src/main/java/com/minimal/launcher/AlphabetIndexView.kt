package com.minimal.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Thin vertical strip on the right edge of the screen.
 * Draws A–Z; touch/drag fires [onLetterSelected] so the caller can
 * scroll the RecyclerView to the matching position.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLetterSelected: ((Char) -> Unit)? = null

    private val letters = ('A'..'Z').toList()

    private val sp = resources.displayMetrics.scaledDensity

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D4D4D")
        textAlign = Paint.Align.CENTER
        textSize = 9f * sp
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 10f * sp
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var activeIndex = -1

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val itemH = height.toFloat() / letters.size
        letters.forEachIndexed { i, letter ->
            val paint = if (i == activeIndex) activePaint else idlePaint
            // Centre text vertically within its slot
            val y = itemH * i + itemH / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(letter.toString(), width / 2f, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val index = ((event.y / height) * letters.size)
                    .toInt().coerceIn(0, letters.size - 1)
                if (index != activeIndex) {
                    activeIndex = index
                    invalidate()
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onLetterSelected?.invoke(letters[index])
                }
                true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activeIndex = -1
                invalidate()
                true
            }
            else -> super.onTouchEvent(event)
        }
    }
}
