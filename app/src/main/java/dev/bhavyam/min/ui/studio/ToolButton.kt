package dev.bhavyam.min.ui.studio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.IconDrawable
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.IconStyle
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.Themed

/**
 * Token Studio's `.tool`: a frosted pill (icon, optional text) that sinks when pressed and dims
 * when disabled. Chrome, not design — its icon style is fixed so it stays readable on any theme.
 */
@SuppressLint("ViewConstructor")
class ToolButton(
    context: Context,
    icon: Icons.Icon,
    private val text: String?,
    description: String,
    private val settings: SettingsStore,
) : View(context), Themed {

    private val drawable = IconDrawable(icon)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, WEIGHT, false)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, resources.displayMetrics)
    }
    private val rect = RectF()
    private val sink = Spring(PRECISION) { scaleX = it; scaleY = it }.also { it.snapTo(1f) }

    /** Replaceable label (Copy → Copied). */
    var label: String? = text
        set(value) { field = value; requestLayout(); invalidate() }

    init {
        contentDescription = description
        isClickable = true
    }

    override fun applyTheme(theme: Theme) {
        fill.color = Theme.withAlpha(theme.surface, Globals.Alpha.GLASS)
        ring.color = Theme.withAlpha(theme.text, RING_ALPHA)
        ring.strokeWidth = theme.dp(1f)
        textPaint.color = theme.text
        drawable.style(theme.text, IconStyle.Outline, STROKE)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val h = (Globals.Space.TOOL * d).toInt()
        val w = label?.let { (PAD_X * 2 + ICON + GAP) * d + textPaint.measureText(it) }?.toInt() ?: h
        setMeasuredDimension(w, h)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else Globals.Alpha.DISABLED
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        sink.animateTo(if (pressed) PRESSED_SCALE else 1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = height / 2f
        canvas.drawRoundRect(rect, r, r, fill)
        rect.inset(ring.strokeWidth / 2, ring.strokeWidth / 2)
        canvas.drawRoundRect(rect, r, r, ring)
        val s = (ICON * d).toInt()
        val left = if (label == null) (width - s) / 2 else (PAD_X * d).toInt()
        val top = (height - s) / 2
        drawable.setBounds(left, top, left + s, top + s)
        drawable.draw(canvas)
        label?.let {
            val fm = textPaint.fontMetrics
            canvas.drawText(it, left + s + GAP * d, height / 2f - (fm.ascent + fm.descent) / 2, textPaint)
        }
    }

    private companion object {
        const val ICON = 16f
        const val STROKE = 1.8f
        const val PAD_X = 12f
        const val GAP = 7f
        const val TEXT_SP = 14f
        const val WEIGHT = 520
        const val RING_ALPHA = 0.09f
        const val PRESSED_SCALE = 0.94f
        const val PRECISION = 0.0005f
    }
}
