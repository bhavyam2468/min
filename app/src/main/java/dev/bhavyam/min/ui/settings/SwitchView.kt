package dev.bhavyam.min.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.Themed

/** A pill switch whose thumb and colours ride one spring. Its row owns the tap; this only draws. */
@SuppressLint("ViewConstructor")
class SwitchView(context: Context, private val settings: SettingsStore) : View(context), Themed {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var offTrack = 0
    private var onTrack = 0
    private var offThumb = 0
    private var onThumb = 0
    private var position = 0f
    private val spring = Spring(PRECISION) { position = it; invalidate() }

    var isChecked = false
        private set

    fun setChecked(checked: Boolean, animate: Boolean) {
        isChecked = checked
        val target = if (checked) 1f else 0f
        if (animate) spring.animateTo(target, settings.spring(SettingsRegistry.CONTROL_MOTION)) else spring.snapTo(target)
    }

    override fun applyTheme(theme: Theme) {
        offTrack = theme.border
        onTrack = theme.primary.color
        offThumb = theme.text
        onThumb = theme.primary.on
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((Globals.Space.SWITCH_WIDTH * d).toInt(), (Globals.Space.SWITCH_HEIGHT * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val t = position.coerceIn(0f, 1f)
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = Theme.mix(offTrack, onTrack, t)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        val inset = Globals.Space.SWITCH_INSET * resources.displayMetrics.density
        val r = h / 2f - inset
        val cx = inset + r + (width - 2 * (inset + r)) * position // overshoot lets the thumb bounce
        paint.color = Theme.mix(offThumb, onThumb, t)
        canvas.drawCircle(cx, h / 2f, r, paint)
    }

    private companion object {
        const val PRECISION = 0.002f
    }
}
