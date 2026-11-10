package dev.bhavyam.min.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import dev.bhavyam.min.Globals
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.motion.rubberBand
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Full-width pages side by side, moved by a spring. It doesn't read touches itself: the search
 * layer decides which gesture owns a drag and feeds horizontal ones here.
 */
@SuppressLint("ViewConstructor")
class ColumnPager(context: Context, private val settings: SettingsStore) : ViewGroup(context) {

    var current = 0
        private set

    /** Called when the settled-on page changes. */
    var onPageChanged: ((Int) -> Unit)? = null

    private var dragStart = 0f
    private val spring = Spring(PRECISION) { scrollTo(it.roundToInt(), 0) }

    private val maxScroll get() = (visibleCount() - 1).coerceAtLeast(0) * width

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val childW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(childW, childH)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.layout(x, 0, x + width, height)
            x += width
        }
        if (current > visibleCount() - 1) current = (visibleCount() - 1).coerceAtLeast(0)
        if (!spring.isRunning) spring.snapTo((current * width).toFloat())
    }

    /** Index among visible pages → child view. */
    fun page(index: Int): View? {
        var n = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (n++ == index) return child
        }
        return null
    }

    fun visibleCount(): Int {
        var n = 0
        for (i in 0 until childCount) if (getChildAt(i).visibility != View.GONE) n++
        return n
    }

    fun beginDrag() {
        spring.stop()
        dragStart = scrollX.toFloat()
    }

    /** Follow the finger ([dx] since [beginDrag], positive to the right), rubber-banding at the ends. */
    fun dragBy(dx: Float) {
        val raw = dragStart - dx
        val max = maxScroll.toFloat()
        val shown = when {
            raw < 0f -> rubberBand(raw, width.toFloat(), Globals.Motion.RUBBER_BAND)
            raw > max -> max + rubberBand(raw - max, width.toFloat(), Globals.Motion.RUBBER_BAND)
            else -> raw
        }
        spring.snapTo(shown)
    }

    /** Let go with horizontal [velocity] (px/s): fling to the neighbour or settle on the nearest page. */
    fun release(velocity: Float) {
        val fling = Globals.Motion.FLING_VELOCITY * resources.displayMetrics.density
        val position = scrollX.toFloat() / width.coerceAtLeast(1)
        val target = when {
            velocity < -fling -> floor(position).toInt() + 1
            velocity > fling -> ceil(position).toInt() - 1
            else -> position.roundToInt()
        }
        settle(target, -velocity)
    }

    fun show(index: Int, animate: Boolean) {
        if (animate) settle(index, 0f) else {
            setCurrent(index.coerceIn(0, (visibleCount() - 1).coerceAtLeast(0)))
            spring.snapTo((current * width).toFloat())
        }
    }

    private fun settle(index: Int, velocity: Float) {
        setCurrent(index.coerceIn(0, (visibleCount() - 1).coerceAtLeast(0)))
        spring.animateTo((current * width).toFloat(), settings.spring(SettingsRegistry.COLUMNS_MOTION), velocity)
    }

    private fun setCurrent(index: Int) {
        if (index == current) return
        current = index
        onPageChanged?.invoke(index)
    }

    /** True while the finger may grab the pages mid-flight. */
    val isSettling get() = spring.isRunning && abs(spring.target - scrollX) > 1f

    private companion object {
        const val PRECISION = 0.5f
    }
}
