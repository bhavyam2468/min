package com.bhavyam.min.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.withAlpha

/**
 * What can be moved and resized on the real home screen, shown only in the studio: a dashed
 * frame with a grip around the favourites (drag to move them) and a dashed frame with a knob
 * on the clock's corner (drag to resize it). The studio layer fills in [list] and [clock] in
 * its own coordinates before each frame; an empty rect draws nothing.
 */
class Guides(context: Context) : View(context), ThemeStore.Listener {

    private val app = App.of(context)
    val list = RectF()
    val clock = RectF()

    /** The frame being dragged right now draws solid and bright. */
    var activeList = false
    var activeClock = false

    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
    }
    private val dashes = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shown = Spring(0.001f) { invalidate() }
    private val tmp = RectF()

    init {
        isClickable = false
    }

    fun show(on: Boolean) = shown.animateTo(if (on) 1f else 0f, app.motion[SettingsRegistry.SPRING_OVERLAY])

    fun hideNow() = shown.snapTo(0f)

    /** The grip's touch target, centred on the top edge of the favourites frame. */
    fun grip(out: RectF) {
        val r = dp(GRIP_REACH)
        out.set(list.centerX() - r * 1.5f, list.top - r, list.centerX() + r * 1.5f, list.top + r)
    }

    /** The resize knob's touch target on the clock frame's bottom-right corner. */
    fun knob(out: RectF) {
        val r = dp(KNOB_REACH)
        out.set(clock.right - r, clock.bottom - r, clock.right + r, clock.bottom + r)
    }

    override fun onDraw(canvas: Canvas) {
        val a = shown.value.coerceIn(0f, 1f)
        if (a <= 0f) return
        val theme = app.themes.theme
        val radius = dp(FRAME_RADIUS)
        if (!list.isEmpty) {
            frame(canvas, list, radius, theme, a, activeList)
            grip(canvas, theme, a)
        }
        if (!clock.isEmpty) {
            frame(canvas, clock, radius / 2, theme, a, activeClock)
            val r = dp(KNOB_RADIUS)
            fill.color = withAlpha(theme.bg, a)
            canvas.drawCircle(clock.right, clock.bottom, r + dp(2f), fill)
            fill.color = withAlpha(theme.text, a * if (activeClock) 1f else 0.8f)
            canvas.drawCircle(clock.right, clock.bottom, r, fill)
        }
    }

    private fun frame(canvas: Canvas, r: RectF, radius: Float, theme: Theme, a: Float, active: Boolean) {
        dash.pathEffect = if (active) null else dashes
        dash.color = withAlpha(theme.text, a * if (active) 0.75f else 0.32f)
        canvas.drawRoundRect(r, radius, radius, dash)
    }

    /** Six dots on a little pill that interrupts the dashed line, like the settings handles. */
    private fun grip(canvas: Canvas, theme: Theme, a: Float) {
        val dot = dp(1.6f)
        val step = dp(5f)
        val cx = list.centerX()
        val cy = list.top
        tmp.set(cx - step * 1.5f - dot * 2, cy - step - dot, cx + step * 1.5f + dot * 2, cy + step + dot)
        fill.color = withAlpha(theme.surface2, a)
        canvas.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill)
        fill.color = withAlpha(theme.text, a * if (activeList) 1f else 0.7f)
        for (col in -1..1) for (row in 0..1) canvas.drawCircle(cx + col * step, cy + (row - 0.5f) * step, dot, fill)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) = invalidate()

    private companion object {
        const val FRAME_RADIUS = 16f
        const val KNOB_RADIUS = 6f
        const val KNOB_REACH = 28f  // dp around the knob that grabs it
        const val GRIP_REACH = 20f
    }
}
