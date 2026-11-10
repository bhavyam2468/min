package dev.bhavyam.min.icons

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Strokes an [Icons.Icon] with round caps, scaled from the 24-unit grid to its bounds. */
class IconDrawable(private val icon: Icons.Icon) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun style(color: Int) {
        stroke.color = color
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / GRID, b.height() / GRID)
        for (path in icon.paths) canvas.drawPath(path, stroke)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val GRID = 24f
        /** In grid units: 1.8 of 24, Token Studio's default stroke. */
        const val STROKE = 1.8f
    }
}
