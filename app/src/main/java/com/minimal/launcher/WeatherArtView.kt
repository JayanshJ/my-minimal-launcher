package com.minimal.launcher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact geometric weather art — 64×32 dp, sits beside the date line without
 * adding noticeable vertical space.
 */
class WeatherArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val SUN   = Color.parseColor("#D4A830")
    private val MOON  = Color.parseColor("#C8BC88")
    private val CLOUD = Color.parseColor("#6A6A6A")
    private val RAIN  = Color.parseColor("#5A9AB8")
    private val SNOW  = Color.parseColor("#8AAEC4")
    private val STORM = Color.parseColor("#4A5E6A")
    private val FOG   = Color.parseColor("#585858")
    private val STAR  = Color.parseColor("#686888")
    private val BOLT  = Color.parseColor("#C09828")

    private val W = 64f
    private val H = 32f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var code  = -1
    private var night = false

    fun setWeather(weatherCode: Int, isNight: Boolean) {
        code  = weatherCode
        night = isNight
        visibility = VISIBLE
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((W * d).toInt(), (H * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        canvas.scale(d, d)
        val sw = 1.3f

        when {
            (code == 0 || code == 1) && night -> drawNight(canvas, sw)
            code == 0  -> drawSun(canvas, 32f, 16f, 8f, sw)
            code == 1  -> { drawSun(canvas, 18f, 13f, 6f, sw); drawCloud(canvas, 42f, 20f, 28f, CLOUD) }
            code == 2  -> { drawSunArc(canvas, 16f, 12f, 6f, sw); drawCloud(canvas, 40f, 19f, 30f, CLOUD) }
            code == 3  -> drawCloud(canvas, 32f, 16f, 44f, CLOUD)
            code in listOf(45, 48)             -> drawFog(canvas, sw)
            code in listOf(51, 53, 55, 56, 57) -> { drawCloud(canvas, 32f, 12f, 36f, CLOUD); drawRain(canvas, 4, RAIN, sw) }
            code in listOf(61, 63, 66, 67, 80, 81, 82) -> { drawCloud(canvas, 32f, 12f, 36f, CLOUD); drawRain(canvas, 6, RAIN, sw) }
            code == 65 -> { drawCloud(canvas, 32f, 12f, 36f, STORM); drawRain(canvas, 7, RAIN, sw + 0.3f) }
            code in listOf(71, 73, 75, 77, 85, 86) -> { drawCloud(canvas, 32f, 12f, 36f, CLOUD); drawSnow(canvas, sw) }
            code in listOf(95, 96, 99) -> { drawCloud(canvas, 32f, 11f, 38f, STORM); drawBolt(canvas, sw) }
            else -> drawSun(canvas, 32f, 16f, 8f, sw)
        }
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw; paint.color = SUN
        canvas.drawCircle(cx, cy, r, paint)
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            canvas.drawLine(
                cx + (r + 2f) * cos(a).toFloat(), cy + (r + 2f) * sin(a).toFloat(),
                cx + (r + 5f) * cos(a).toFloat(), cy + (r + 5f) * sin(a).toFloat(), paint
            )
        }
    }

    private fun drawSunArc(canvas: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw; paint.color = SUN
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -30f, 220f, false, paint)
        listOf(-90f, -45f, 180f, 210f).forEach { deg ->
            val a = Math.toRadians(deg.toDouble())
            canvas.drawLine(
                cx + (r + 1.5f) * cos(a).toFloat(), cy + (r + 1.5f) * sin(a).toFloat(),
                cx + (r + 4f)   * cos(a).toFloat(), cy + (r + 4f)   * sin(a).toFloat(), paint
            )
        }
    }

    private fun drawNight(canvas: Canvas, sw: Float) {
        paint.style = Paint.Style.FILL; paint.color = MOON
        canvas.drawCircle(26f, 16f, 11f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(32f, 12f, 9f, paint)
        paint.color = STAR
        listOf(48f to 10f, 54f to 22f, 50f to 28f).forEach { (x, y) ->
            canvas.drawCircle(x, y, 1.6f, paint)
        }
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color
        val r1 = w * 0.17f; val x1 = cx - w * 0.20f; val y1 = cy
        val r2 = w * 0.23f; val x2 = cx;              val y2 = cy - w * 0.13f
        val r3 = w * 0.19f; val x3 = cx + w * 0.21f; val y3 = cy - w * 0.04f
        canvas.drawCircle(x1, y1, r1, paint)
        canvas.drawCircle(x2, y2, r2, paint)
        canvas.drawCircle(x3, y3, r3, paint)
        val bottom = maxOf(y1 + r1, y2 + r2, y3 + r3)
        canvas.drawRoundRect(x1 - r1, cy - w * 0.05f, x3 + r3, bottom, r1 * 0.5f, r1 * 0.5f, paint)
    }

    private fun drawRain(canvas: Canvas, count: Int, color: Int, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw
        paint.strokeCap = Paint.Cap.ROUND; paint.color = color
        val y1 = 22f; val y2 = 30f
        val step = 44f / (count + 1)
        repeat(count) { i ->
            val x = 10f + step * (i + 1)
            canvas.drawLine(x, y1, x - 3f, y2, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawSnow(canvas: Canvas, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw
        paint.strokeCap = Paint.Cap.ROUND; paint.color = SNOW
        listOf(18f to 27f, 32f to 30f, 46f to 27f).forEach { (cx, cy) ->
            repeat(3) { i ->
                val a = Math.toRadians(i * 60.0)
                canvas.drawLine(
                    cx + 3.5f * cos(a).toFloat(), cy + 3.5f * sin(a).toFloat(),
                    cx - 3.5f * cos(a).toFloat(), cy - 3.5f * sin(a).toFloat(), paint
                )
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBolt(canvas: Canvas, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw + 0.5f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = BOLT
        val path = Path().apply { moveTo(36f, 20f); lineTo(29f, 28f); lineTo(34f, 28f); lineTo(27f, 32f) }
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT; paint.strokeJoin = Paint.Join.MITER
    }

    private fun drawFog(canvas: Canvas, sw: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw + 0.4f
        paint.strokeCap = Paint.Cap.ROUND; paint.color = FOG
        listOf(Triple(10f, 46f, 8f), Triple(8f, 52f, 14f), Triple(10f, 50f, 20f), Triple(8f, 48f, 26f))
            .forEach { (x1, x2, y) -> canvas.drawLine(x1, y, x2, y, paint) }
        paint.strokeCap = Paint.Cap.BUTT
    }
}
