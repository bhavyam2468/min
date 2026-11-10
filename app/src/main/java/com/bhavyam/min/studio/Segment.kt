package com.bhavyam.min.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.dpi

/**
 * A row of choices with one picked; the pill behind the picked one slides (on a spring) and
 * stretches to the next choice's width rather than jumping.
 */
class Segment(context: Context, labels: List<String>, private val onPick: (Int) -> Unit) :
    LinearLayout(context), ThemeStore.Listener {

    private val app = App.of(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = RectF()
    private val position = Spring(0.001f) { invalidate() }

    private var selected = 0

    init {
        setWillNotDraw(false)
        gravity = Gravity.CENTER_VERTICAL
        val padX = dpi(12f)
        val padY = dpi(6f)
        labels.forEachIndexed { i, label ->
            addView(ThemedText(context, Globals.TEXT_BUTTON).apply {
                text = label
                setPadding(padX, padY, padX, padY)
                setOnClickListener {
                    if (i == selected) return@setOnClickListener
                    select(i, animate = true)
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onPick(i)
                }
            })
        }
        select(0, animate = false)
    }

    fun select(i: Int, animate: Boolean) {
        selected = i
        for (c in 0 until childCount) getChildAt(c).alpha = if (c == i) 1f else UNPICKED_ALPHA
        if (animate) position.animateTo(i.toFloat(), app.motion[SettingsRegistry.SPRING_PAGE]) else position.snapTo(i.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        if (childCount == 0) return
        val p = position.value.coerceIn(0f, childCount - 1f)
        val a = getChildAt(p.toInt())
        val b = getChildAt((p.toInt() + 1).coerceAtMost(childCount - 1))
        val f = p - p.toInt()
        pill.set(
            a.left + (b.left - a.left) * f,
            a.top.toFloat(),
            a.right + (b.right - a.right) * f,
            a.bottom.toFloat(),
        )
        canvas.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        paint.color = theme.border
        invalidate()
    }

    private companion object {
        const val UNPICKED_ALPHA = 0.5f
    }
}
