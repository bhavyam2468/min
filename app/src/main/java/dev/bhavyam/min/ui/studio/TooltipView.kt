package dev.bhavyam.min.ui.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.theme.Oklch
import kotlin.math.max
import kotlin.math.min

/**
 * The floating label that tells you what you're touching: swatch, name, live value and a hint.
 * The hint folds away while you drag (the value is what matters then) and opens again after.
 * Dark frosted glass whatever the theme, like Token Studio's.
 */
class TooltipView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Oklch.toArgb(0.2f, 0.01f, 270f, Globals.Alpha.TOOLTIP) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = WHITE_8
    }
    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swatchRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = WHITE_50
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE
        textSize = sp(LABEL_SP)
        typeface = Typeface.create(Typeface.DEFAULT, LABEL_WEIGHT, false)
    }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE_70
        textSize = sp(LABEL_SP)
        typeface = Typeface.create(Typeface.DEFAULT, VALUE_WEIGHT, false)
        fontFeatureSettings = "tnum"
    }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE_55
        textSize = sp(HINT_SP)
        typeface = Typeface.create(Typeface.DEFAULT, VALUE_WEIGHT, false)
    }
    private val rect = RectF()

    private var label = ""
    private var value = ""
    private var hint: StaticLayout? = null
    private var hintText = ""
    private var swatch: Int? = null

    /** 1 = hint open, 0 = folded (driven by the studio's spring). */
    var hintOpen = 1f
        set(v) { field = v; requestLayout(); invalidate() }

    fun set(label: String, value: String, hint: String, swatch: Int?) {
        val sizeChanged = label != this.label || hint != hintText || (swatch == null) != (this.swatch == null)
        this.label = label
        this.value = value
        this.swatch = swatch
        if (hint != hintText) {
            hintText = hint
            this.hint = null
        }
        if (sizeChanged) requestLayout()
        invalidate()
    }

    private val padX get() = PAD_X * density
    private val swatchSpace get() = if (swatch != null) (SWATCH + GAP) * density else 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rowW = swatchSpace + labelPaint.measureText(label) + VALUE_GAP * density + valuePaint.measureText(value)
        val maxW = min(MAX_W * density, MeasureSpec.getSize(widthMeasureSpec).toFloat())
        val w = max(MIN_W * density, min(maxW, rowW + 2 * padX))
        val inner = (w - 2 * padX).toInt().coerceAtLeast(1)
        val layout = hint?.takeIf { it.width == inner }
            ?: StaticLayout.Builder.obtain(hintText, 0, hintText.length, hintPaint, inner).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        hint = layout
        val row = labelPaint.fontSpacing
        val h = (PAD_TOP + PAD_BOTTOM) * density + row + (HINT_GAP * density + layout.height) * hintOpen
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = RADIUS * density
        canvas.drawRoundRect(rect, r, r, glass)
        rect.inset(edge.strokeWidth / 2, edge.strokeWidth / 2)
        canvas.drawRoundRect(rect, r, r, edge)

        val top = PAD_TOP * density
        val row = labelPaint.fontSpacing
        val baseline = top + row / 2 - (labelPaint.ascent() + labelPaint.descent()) / 2
        var x = padX
        swatch?.let {
            val cr = SWATCH * density / 2
            swatchPaint.color = it
            canvas.drawCircle(x + cr, top + row / 2, cr, swatchPaint)
            canvas.drawCircle(x + cr, top + row / 2, cr, swatchRing)
            x += swatchSpace
        }
        canvas.drawText(label, x, baseline, labelPaint)
        canvas.drawText(value, width - padX - valuePaint.measureText(value), baseline, valuePaint)

        val layout = hint ?: return
        if (hintOpen <= 0f) return
        val save = canvas.save()
        canvas.clipRect(0f, top + row, width.toFloat(), height - PAD_BOTTOM * density)
        canvas.translate(padX, top + row + HINT_GAP * density * hintOpen)
        hintPaint.alpha = ((WHITE_55 ushr 24) * hintOpen.coerceIn(0f, 1f)).toInt()
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private companion object {
        const val WHITE = -0x1
        const val WHITE_70 = 0xB3FFFFFF.toInt()
        const val WHITE_55 = 0x8CFFFFFF.toInt()
        const val WHITE_50 = 0x80FFFFFF.toInt()
        const val WHITE_8 = 0x14FFFFFF
        const val LABEL_SP = 12.5f
        const val HINT_SP = 11.5f
        const val LABEL_WEIGHT = 620
        const val VALUE_WEIGHT = 500
        const val PAD_X = 12f
        const val PAD_TOP = 9f
        const val PAD_BOTTOM = 10f
        const val HINT_GAP = 3f
        const val GAP = 8f
        const val VALUE_GAP = 10f
        const val SWATCH = 12f
        const val RADIUS = 13f
        const val MIN_W = 120f
        const val MAX_W = 280f
    }
}
