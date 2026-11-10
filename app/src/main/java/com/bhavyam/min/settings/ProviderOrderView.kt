package com.bhavyam.min.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.observe
import com.bhavyam.min.search.FallbackProvider
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.ui.Icon
import com.bhavyam.min.ui.SpringSwitch
import com.bhavyam.min.ui.ThemedIcon
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi

/**
 * Search destinations in priority order. Hold the six-dot handle and drag to reorder: the row
 * lifts and follows the finger while the others spring out of its way. The switch hides a
 * destination without forgetting its place.
 */
@SuppressLint("ViewConstructor")
class ProviderOrderView(context: Context) : FrameLayout(context) {

    private val fallback = App.of(context).fallback
    private val motion = App.of(context).motion
    private val rowHeight = dpi(Globals.TOUCH_ROW)
    private var rows: List<Row> = emptyList()
    private var dragged: Row? = null
    private var dragStartY = 0f
    private var dragStartTop = 0f

    private inner class Row(val item: FallbackProvider.Item) : LinearLayout(context) {
        var slot = 0
        val spring = Spring(0.5f) { y -> translationY = y }
        val lift = Spring(0.001f) { p ->
            val s = 1f + 0.03f * p
            scaleX = s
            scaleY = s
            translationZ = dp(8f) * p
        }
        val handle = ThemedIcon(context, Icon.GRIP, tone = Tone.MUTED)
        val toggle = SpringSwitch(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dpi(12f)
            handle.setPadding(pad, pad, pad, pad)
            addView(handle)
            addView(ThemedText(context, Globals.TEXT_ROW).apply { text = item.target.label.replaceFirstChar(Char::uppercase) },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle)
            toggle.jump(fallback.isEnabled(item.target))
            setOnClickListener {
                toggle.checked = !toggle.checked
                fallback.setEnabled(item.target, toggle.checked)
            }
            handle.setOnTouchListener { _, e -> onHandle(this, e) }
        }
    }

    init {
        observe(fallback.changed) { if (dragged == null && ids(fallback.ordered()) != ids(rows.sortedBy { it.slot }.map { it.item })) build() }
        build()
    }

    private fun ids(items: List<FallbackProvider.Item>) = items.map { it.target.id }

    private fun build() {
        removeAllViews()
        rows = fallback.ordered().map { Row(it) }
        rows.forEachIndexed { i, r ->
            r.slot = i
            addView(r, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
            r.spring.snapTo((i * rowHeight).toFloat())
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(rows.size * rowHeight, MeasureSpec.EXACTLY))
    }

    private fun onHandle(row: Row, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                dragged = row
                dragStartY = e.rawY
                dragStartTop = row.spring.value
                row.bringToFront()
                row.lift.animateTo(1f, motion[SettingsRegistry.SPRING_PRESS])
                row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            MotionEvent.ACTION_MOVE -> {
                val y = (dragStartTop + e.rawY - dragStartY).coerceIn(0f, ((rows.size - 1) * rowHeight).toFloat())
                row.spring.snapTo(y)
                val slot = ((y + rowHeight / 2f) / rowHeight).toInt().coerceIn(0, rows.lastIndex)
                if (slot != row.slot) reorder(row, slot)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                row.spring.animateTo((row.slot * rowHeight).toFloat(), motion[SettingsRegistry.SPRING_LIST])
                row.lift.animateTo(0f, motion[SettingsRegistry.SPRING_PRESS])
                dragged = null
                fallback.setOrder(ids(rows.sortedBy { it.slot }.map { it.item }))
            }
        }
        return true
    }

    /** Moves [row] to [slot] and springs every row it passed into its new place. */
    private fun reorder(row: Row, slot: Int) {
        val order = rows.sortedBy { it.slot }.toMutableList()
        order.remove(row)
        order.add(slot, row)
        order.forEachIndexed { i, r ->
            if (r.slot != i) {
                r.slot = i
                if (r !== row) r.spring.animateTo((i * rowHeight).toFloat(), motion[SettingsRegistry.SPRING_LIST])
            }
        }
        row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
