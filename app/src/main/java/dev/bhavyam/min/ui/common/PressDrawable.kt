package dev.bhavyam.min.ui.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore

/** A rounded press highlight that springs in and out with the pressed state, used on every tappable row. */
class PressDrawable(private val settings: SettingsStore) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var level = 0f
    private val spring = Spring(PRECISION) { level = it; invalidateSelf() }

    var radius = 0f
        set(value) { field = value; invalidateSelf() }

    var color = 0
        set(value) { field = value; invalidateSelf() }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val pressed = state.contains(android.R.attr.state_pressed)
        spring.animateTo(if (pressed) 1f else 0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        return false
    }

    override fun jumpToCurrentState() = spring.snapTo(if (state.contains(android.R.attr.state_pressed)) 1f else 0f)

    override fun draw(canvas: Canvas) {
        if (level <= 0f) return
        paint.color = color
        paint.alpha = (Color.alpha(color) * level.coerceIn(0f, 1f)).toInt()
        rect.set(bounds)
        val r = radius.coerceAtMost(rect.height() / 2f)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val PRECISION = 0.002f
    }
}
