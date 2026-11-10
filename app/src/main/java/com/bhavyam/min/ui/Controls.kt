package com.bhavyam.min.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import kotlin.math.abs

/** Shared plumbing for the two controls: theme subscription and paints. */
abstract class ThemedControl(context: Context) : View(context), ThemeStore.Listener {
    protected val app = App.of(context)
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val rect = RectF()
    protected var theme: Theme = app.themes.theme

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        this.theme = theme
        invalidate()
    }

    /** Corner radius from the button-radius token, capped at a pill. */
    protected fun radius(h: Float) = dp(theme.tokens.radiusButton).coerceAtMost(h / 2f)
}

/** On/off switch in the home colour; the thumb travels on the press spring. */
class SpringSwitch(context: Context) : ThemedControl(context) {
    private val spring = Spring(0.001f) { invalidate() }

    var checked = false
        set(v) {
            field = v
            spring.animateTo(if (v) 1f else 0f, app.motion[SettingsRegistry.SPRING_PRESS])
        }

    /** Sets the state without animating, e.g. when a row is first bound. */
    fun jump(v: Boolean) {
        checked = v
        spring.snapTo(if (v) 1f else 0f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(dpi(46f), dpi(28f))

    override fun onDraw(canvas: Canvas) {
        val p = spring.value
        val c = theme.colors(Role.HOME)
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = mix(theme.surface2, c.fill, p.coerceIn(0f, 1f))
        canvas.drawRoundRect(rect, radius(h), radius(h), paint)
        val inset = dp(3f)
        val size = h - inset * 2
        val x = inset + (width - h) * p
        rect.set(x, inset, x + size, inset + size)
        paint.color = mix(theme.muted, c.on, p.coerceIn(0f, 1f))
        val r = radius(size)
        canvas.drawRoundRect(rect, r, r, paint)
    }
}

/**
 * Continuous slider. The thumb follows the finger exactly while dragging; a tap on the track
 * springs the thumb there. [onChange] fires on every change.
 */
class SpringSlider(
    context: Context,
    private val min: Float,
    private val max: Float,
    private val onChange: (Float) -> Unit,
) : ThemedControl(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val spring = Spring(0.0005f) { invalidate() }
    private val grip = Spring(0.001f) { invalidate() }
    private var downX = 0f
    private var dragging = false

    var value: Float = min
        private set

    /** Sets the value from outside without notifying. */
    fun jump(v: Float) {
        value = v.coerceIn(min, max)
        spring.snapTo(fraction(value))
    }

    private fun fraction(v: Float) = (v - min) / (max - min)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dpi(36f))

    override fun onDraw(canvas: Canvas) {
        val c = theme.colors(Role.HOME)
        val thumb = dp(10f) + dp(4f) * grip.value
        val left = dp(12f)
        val right = width - dp(12f)
        val cy = height / 2f
        val x = left + (right - left) * spring.value.coerceIn(0f, 1f)
        val track = dp(4f)
        paint.color = theme.surface2
        rect.set(left, cy - track / 2, right, cy + track / 2)
        canvas.drawRoundRect(rect, track, track, paint)
        paint.color = c.fill
        rect.set(left, cy - track / 2, x, cy + track / 2)
        canvas.drawRoundRect(rect, track, track, paint)
        rect.set(x - thumb, cy - thumb, x + thumb, cy + thumb)
        paint.color = c.ink
        val r = radius(thumb * 2)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    @SuppressLint("ClickableViewAccessibility") // value is exposed through the row's text
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val left = dp(12f)
        val f = ((e.x - left) / (width - left * 2)).coerceIn(0f, 1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                dragging = false
                grip.animateTo(1f, app.motion[SettingsRegistry.SPRING_PRESS])
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.x - downX) > slop) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    spring.snapTo(f)
                    set(f)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) spring.animateTo(f, app.motion[SettingsRegistry.SPRING_PRESS])
                set(f)
                grip.animateTo(0f, app.motion[SettingsRegistry.SPRING_PRESS])
            }
            MotionEvent.ACTION_CANCEL -> grip.animateTo(0f, app.motion[SettingsRegistry.SPRING_PRESS])
        }
        return true
    }

    private fun set(f: Float) {
        value = min + (max - min) * f
        onChange(value)
    }
}
