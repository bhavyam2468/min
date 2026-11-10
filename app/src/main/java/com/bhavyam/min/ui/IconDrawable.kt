package com.bhavyam.min.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.bhavyam.min.theme.IconStyle

/**
 * Draws an [Icon] in the icon-style and stroke tokens. Colour comes from whoever owns it
 * (usually the text colour of the row it sits in), so icons always match their label.
 */
class IconDrawable(icon: Icon, private val sizePx: Int) : Drawable() {

    var icon: Icon = icon
        set(v) { field = v; invalidateSelf() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var fillAlpha = 0f

    fun style(color: Int, style: IconStyle, strokeWidth: Float) {
        stroke.color = color
        fill.color = color
        stroke.strokeWidth = strokeWidth
        val sharp = style == IconStyle.SHARP
        stroke.strokeCap = if (sharp) Paint.Cap.SQUARE else Paint.Cap.ROUND
        stroke.strokeJoin = if (sharp) Paint.Join.MITER else Paint.Join.ROUND
        fillAlpha = when (style) {
            IconStyle.DUOTONE -> 0.24f
            IconStyle.FILLED -> 1f
            else -> 0f
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = b.width() / GRID
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(s, s)
        if (fillAlpha > 0f) {
            fill.alpha = (fillAlpha * (stroke.color ushr 24)).toInt()
            canvas.drawPath(icon.shapes, fill)
        }
        canvas.drawPath(icon.shapes, stroke)
        canvas.drawPath(icon.strokes, stroke)
        canvas.restore()
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val GRID = 24f
    }
}
