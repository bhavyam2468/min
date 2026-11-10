package dev.bhavyam.min.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.search.SearchTarget
import dev.bhavyam.min.search.SearchTargets
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.IconView
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import kotlin.math.roundToInt

/**
 * The fallback search providers: tap a row to switch it, hold its six-dot handle to drag it.
 * While dragging, the held row lifts and the others spring aside into their new slots; the new
 * order is saved when the row is let go.
 */
@SuppressLint("ViewConstructor")
class ProviderListView(
    context: Context,
    private val targets: SearchTargets,
    private val settings: SettingsStore,
) : LinearLayout(context), Themed {

    private var theme: Theme? = null
    private var held: Row? = null
    private var heldFrom = 0
    private var heldTo = 0
    private var lastRawY = 0f
    private var offset = 0f

    init {
        orientation = VERTICAL
    }

    /** Rebuild from the currently installed targets (call when shown, as apps may have changed). */
    fun rebuild() {
        removeAllViews()
        for (target in targets.installed) addView(Row(target), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        theme?.let(::applyTheme)
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        for (i in 0 until childCount) (getChildAt(i) as Row).applyTheme(theme)
    }

    private fun startDrag(row: Row, rawY: Float) {
        held = row
        heldFrom = indexOfChild(row)
        heldTo = heldFrom
        lastRawY = rawY
        offset = 0f
        row.lift.animateTo(1f, spec())
    }

    private fun moveDrag(rawY: Float) {
        val row = held ?: return
        offset += rawY - lastRawY
        lastRawY = rawY
        row.shift.snapTo(offset)
        val step = row.height.coerceAtLeast(1)
        heldTo = (heldFrom + (offset / step).roundToInt()).coerceIn(0, childCount - 1)
        for (i in 0 until childCount) {
            val other = getChildAt(i) as Row
            if (other === row) continue
            val target = when {
                i in heldFrom + 1..heldTo -> -step
                i in heldTo until heldFrom -> step
                else -> 0
            }
            other.shift.animateTo(target.toFloat(), spec())
        }
    }

    private fun endDrag() {
        val row = held ?: return
        held = null
        row.lift.animateTo(0f, spec())
        if (heldTo == heldFrom) {
            row.shift.animateTo(0f, spec())
            return
        }
        val from = heldFrom
        val to = heldTo
        targets.move(from, to)
        row.shift.onRest = {
            row.shift.onRest = null
            commitOrder(row, to)
        }
        row.shift.animateTo(((to - from) * row.height).toFloat(), spec())
    }

    /** Every row now sits visually in its new slot: make that the real order, in the same frame. */
    private fun commitOrder(row: Row, to: Int) {
        removeView(row)
        addView(row, to)
        for (i in 0 until childCount) (getChildAt(i) as Row).shift.snapTo(0f)
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        layout(left, top, right, bottom)
    }

    private fun spec() = settings.spring(SettingsRegistry.CONTROL_MOTION)

    private inner class Row(val target: SearchTarget) : LinearLayout(context), Themed {
        private val grip = Grip(this)
        private val label = TextView(context).apply { text = target.name.replaceFirstChar { it.uppercase() } }
        private val switch = SwitchView(context, settings)
        private val press = PressDrawable(settings)

        val shift = Spring(SHIFT_PRECISION) { translationY = it }
        val lift = Spring(LIFT_PRECISION) {
            val s = 1f + (Globals.Motion.LIFT_SCALE - 1f) * it
            scaleX = s
            scaleY = s
            translationZ = it * (theme?.elevation ?: 0f)
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = press
            addView(grip)
            addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(switch)
            switch.setChecked(targets.isEnabled(target), animate = false)
            setOnClickListener {
                switch.setChecked(!switch.isChecked, animate = true)
                targets.setEnabled(target, switch.isChecked)
            }
        }

        override fun applyTheme(theme: Theme) {
            label.style(theme, Globals.Type.ROW)
            grip.applyTheme(theme)
            switch.applyTheme(theme)
            press.color = theme.pressed
            press.radius = theme.radiusButton
            val padX = theme.em(theme.tokens.padX * ROW_PAD).toInt()
            minimumHeight = theme.dp(Globals.Space.ROW_MIN).toInt()
            setPadding(0, 0, padX, 0)
        }

        fun touchGrip(e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    startDrag(this, e.rawY)
                }
                MotionEvent.ACTION_MOVE -> moveDrag(e.rawY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
            }
        }
    }

    /** The six-dot handle; touching it starts the drag at once. */
    @SuppressLint("ViewConstructor")
    private class Grip(private val row: Row) : IconView(row.context, Icons.grip, Globals.Space.GRIP, muted = true) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            row.touchGrip(e)
            return true
        }
    }

    private companion object {
        const val SHIFT_PRECISION = 0.5f
        const val LIFT_PRECISION = 0.002f
        const val ROW_PAD = 0.6f
    }
}
