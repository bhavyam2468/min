package dev.bhavyam.min.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.bhavyam.min.Globals
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.Slider
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.Themed
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A thin track with a thumb that follows the finger exactly while dragged, springs to a tapped
 * spot, and swells while held. Vertical drags are left to the scrolling list.
 */
@SuppressLint("ViewConstructor")
class SliderView(
    context: Context,
    private val setting: Slider,
    private val settings: SettingsStore,
    private val onChange: (Float) -> Unit,
) : View(context), Themed {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var trackColor = 0
    private var accent = 0
    private var fraction = 0f
    private var grow = 0f
    private val position = Spring(PRECISION) { fraction = it; invalidate() }
    private val press = Spring(PRECISION) { grow = it; invalidate() }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    var value = setting.default
        private set

    fun setValue(v: Float) {
        value = v
        position.snapTo(toFraction(v))
    }

    override fun applyTheme(theme: Theme) {
        trackColor = theme.border
        accent = theme.primary.color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (Globals.Space.SLIDER_HEIGHT * resources.displayMetrics.density).toInt())
    }

    private val thumbRadius get() = Globals.Space.SLIDER_THUMB * resources.displayMetrics.density / 2f
    private val trackStart get() = paddingLeft + thumbRadius
    private val trackLength get() = (width - paddingLeft - paddingRight - 2 * thumbRadius).coerceAtLeast(1f)

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cy = height / 2f
        val half = Globals.Space.SLIDER_TRACK * d / 2f
        val x = trackStart + trackLength * fraction
        paint.color = trackColor
        canvas.drawRoundRect(trackStart, cy - half, trackStart + trackLength, cy + half, half, half, paint)
        paint.color = accent
        canvas.drawRoundRect(trackStart, cy - half, x.coerceAtLeast(trackStart), cy + half, half, half, paint)
        canvas.drawCircle(x, cy, thumbRadius * (1f + THUMB_GROW * grow), paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.x - downX) > touchSlop && abs(e.x - downX) > abs(e.y - downY)) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    press.animateTo(1f, spec())
                }
                if (dragging) {
                    commit(fromX(e.x))
                    position.snapTo(fraction(e.x))
                }
            }
            MotionEvent.ACTION_UP -> {
                commit(fromX(e.x))
                position.animateTo(toFraction(value), spec()) // settle onto the step
                release()
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    private fun release() {
        dragging = false
        press.animateTo(0f, spec())
    }

    private fun commit(v: Float) {
        if (v == value) return
        value = v
        onChange(v)
    }

    private fun spec() = settings.spring(SettingsRegistry.CONTROL_MOTION)

    private fun fraction(x: Float) = ((x - trackStart) / trackLength).coerceIn(0f, 1f)

    private fun fromX(x: Float): Float {
        val raw = setting.min + fraction(x) * (setting.max - setting.min)
        return (setting.min + ((raw - setting.min) / setting.step).roundToInt() * setting.step).coerceIn(setting.min, setting.max)
    }

    private fun toFraction(v: Float) = (v - setting.min) / (setting.max - setting.min)

    private companion object {
        const val PRECISION = 0.001f
        const val THUMB_GROW = 0.25f
    }
}
