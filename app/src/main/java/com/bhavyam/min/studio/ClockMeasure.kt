package com.bhavyam.min.studio

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import android.text.format.DateFormat
import com.bhavyam.min.App
import com.bhavyam.min.home.ClockFormat
import com.bhavyam.min.theme.Globals
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * [ClockFit.Measure] against real text: each pattern formatted for this moment and measured in
 * the theme's typeface at the size the clock would draw it, so the fit is what you'll see.
 */
class ClockMeasure(private val context: Context) : ClockFit.Measure {

    private val themes = App.of(context).themes
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val metrics = Paint.FontMetrics()
    private val formats = HashMap<String, ClockFormat>()
    private val out = StringBuilder()
    private val cal = Calendar.getInstance()
    private var symbols = DateFormatSymbols.getInstance(Locale.getDefault())
    private var is24 = false

    override val gap get() = Globals.CLOCK_GAP * context.resources.displayMetrics.density

    /** Re-reads the time, locale and 12/24-hour setting; call once per gesture. */
    fun now() {
        cal.timeInMillis = System.currentTimeMillis()
        symbols = DateFormatSymbols.getInstance(Locale.getDefault())
        is24 = DateFormat.is24HourFormat(context)
    }

    override fun width(pattern: String, sizeSp: Float): Float {
        if (pattern.isEmpty()) return 0f
        setSize(sizeSp)
        out.setLength(0)
        formats.getOrPut(pattern) { ClockFormat.compile(pattern) }.format(cal, is24, symbols, out)
        return paint.measureText(out, 0, out.length)
    }

    /** A TextView's line with font padding: what each clock line takes vertically. */
    override fun lineHeight(sizeSp: Float): Float {
        setSize(sizeSp)
        paint.getFontMetrics(metrics)
        return metrics.bottom - metrics.top
    }

    private fun setSize(sizeSp: Float) {
        val theme = themes.theme
        paint.typeface = theme.typeface
        paint.textSize = theme.textPx(sizeSp, context.resources)
    }
}
