package com.bhavyam.min.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Full-width pages side by side, moved by one spring. A horizontal drag carries the pages
 * with the finger (resisting past the ends) and the release hands its velocity to the spring,
 * so a flick overshoots and settles. Pages further from the centre dim.
 */
class Pager(context: Context, private val swipeable: Boolean) : FrameLayout(context) {

    private val motion = App.of(context).motion
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val spring = Spring(0.0005f) { layoutPages() }
    private var pages: List<View> = emptyList()
    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var startPos = 0f
    private var dragging = false

    var current = 0
        private set

    /** Shows exactly [views], keeping the current page index if it still exists. */
    fun setPages(views: List<View>) {
        if (views == pages) return
        removeAllViews()
        pages = views
        for (v in views) addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (current > views.lastIndex) go(views.lastIndex.coerceAtLeast(0), animate = true) else layoutPages()
    }

    fun go(page: Int, animate: Boolean) {
        val p = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        current = p
        if (animate) spring.animateTo(p.toFloat(), motion[SettingsRegistry.SPRING_PAGE]) else spring.snapTo(p.toFloat())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutPages()
    }

    private fun layoutPages() {
        val pos = spring.value
        for (i in pages.indices) {
            val v = pages[i]
            val d = i - pos
            v.translationX = d * width
            v.alpha = 1f - (abs(d) * DIM).coerceAtMost(1f)
            v.visibility = if (abs(d) < 1f) VISIBLE else INVISIBLE
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (!swipeable || pages.size < 2) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
                spring.stop()
                startPos = spring.value
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dx = abs(e.x - downX)
                if (dx > slop && dx > abs(e.y - downY)) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) settle(0f)
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility") // pages carry their own accessible content
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return swipeable && pages.size > 1 && e.actionMasked == MotionEvent.ACTION_DOWN
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val last = (pages.size - 1).toFloat()
                var pos = startPos - (e.x - downX) / width
                if (pos < 0f) pos *= EDGE_RESISTANCE
                if (pos > last) pos = last + (pos - last) * EDGE_RESISTANCE
                spring.snapTo(pos)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = tracker
                t?.computeCurrentVelocity(1000)
                settle(-(t?.xVelocity ?: 0f) / width)
                dragging = false
            }
        }
        return true
    }

    /** Pick the page the motion is heading to and spring there with the finger's velocity. */
    private fun settle(velocity: Float) {
        tracker?.recycle()
        tracker = null
        val pos = spring.value
        val target = when {
            velocity > FLING -> floor(pos).toInt() + 1
            velocity < -FLING -> ceil(pos).toInt() - 1
            else -> pos.roundToInt()
        }.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        current = target
        spring.animateTo(target.toFloat(), motion[SettingsRegistry.SPRING_PAGE], velocity)
    }

    private companion object {
        const val DIM = 0.7f
        const val EDGE_RESISTANCE = 0.35f
        const val FLING = 0.6f // pages per second
    }
}
