package dev.bhavyam.min.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.format.DateFormat
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.clock.ClockFormat
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.Themed
import java.time.LocalDateTime
import kotlin.math.ceil

/**
 * The clock: one line per format in the `clock` token, all in one size, aligned to the side of
 * home it is anchored to. It re-formats once a minute (and when the time, zone or 12/24 h setting
 * changes) while it is on screen, and draws straight to the canvas.
 */
class ClockView(context: Context) : View(context), Themed {

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var formats: List<String> = emptyList()
    private var lines: Array<String> = emptyArray()
    private var widths = FloatArray(0)
    private var column = 0
    private var lineGap = 0f
    private var ascent = 0f

    private val ticker = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reformat()
    }

    override fun applyTheme(theme: Theme) {
        val t = theme.tokens
        paint.textSize = theme.em(t.clockSize)
        paint.typeface = theme.regular
        paint.color = theme.text
        lineGap = paint.textSize * Globals.Space.CLOCK_LINE_GAP
        ascent = -paint.ascent()
        column = t.clockAnchor.column
        formats = t.clock
        reformat()
    }

    private fun reformat() {
        val now = LocalDateTime.now()
        val is24 = DateFormat.is24HourFormat(context)
        val locale = resources.configuration.locales[0]
        if (lines.size != formats.size) {
            lines = Array(formats.size) { "" }
            widths = FloatArray(formats.size)
        }
        for (i in formats.indices) {
            lines[i] = ClockFormat.format(formats[i], now, is24, locale)
            widths[i] = paint.measureText(lines[i])
        }
        requestLayout()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(ticker, filter, Context.RECEIVER_NOT_EXPORTED) // system broadcasts still arrive
        reformat() // minutes may have passed while detached
    }

    override fun onDetachedFromWindow() {
        context.unregisterReceiver(ticker)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = 0f
        for (x in widths) if (x > w) w = x
        val h = if (lines.isEmpty()) 0f else lines.size * paint.fontSpacing + (lines.size - 1) * lineGap
        setMeasuredDimension(resolveSize(ceil(w).toInt(), widthMeasureSpec), resolveSize(ceil(h).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        var baseline = ascent
        for (i in lines.indices) {
            val x = when (column) {
                0 -> 0f
                1 -> (width - widths[i]) / 2f
                else -> width - widths[i]
            }
            canvas.drawText(lines[i], x, baseline, paint)
            baseline += paint.fontSpacing + lineGap
        }
    }
}
