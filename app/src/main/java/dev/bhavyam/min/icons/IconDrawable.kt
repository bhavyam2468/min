package dev.bhavyam.min.icons

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import dev.bhavyam.min.theme.IconStyle
import dev.bhavyam.min.theme.Theme

/**
 * Paints an [Icons.Icon] in the theme's icon style (outline · duotone · filled · sharp) and stroke
 * weight, scaled from the 24-unit grid to its bounds — Token Studio's CSS icon rules, on a canvas.
 */
class IconDrawable(private val icon: Icons.Icon) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var fillAlpha = 0f

    /** In the theme's icon style — every icon that belongs to the design. */
    fun style(theme: Theme, color: Int) = style(color, theme.iconStyle, theme.iconStroke)

    /** In a fixed style — studio chrome, which must stay legible whatever the tokens say. */
    fun style(color: Int, s: IconStyle, strokeWidth: Float) {
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        stroke.strokeCap = s.cap
        stroke.strokeJoin = s.join
        fill.color = color
        fillAlpha = s.fillAlpha
        fill.alpha = (fillAlpha * 255).toInt()
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / GRID, b.height() / GRID)
        for (part in icon.parts) {
            if (!part.isLine && fillAlpha > 0f) canvas.drawPath(part.path, fill)
            canvas.drawPath(part.path, stroke)
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = (alpha * fillAlpha).toInt()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val GRID = 24f
    }
}
