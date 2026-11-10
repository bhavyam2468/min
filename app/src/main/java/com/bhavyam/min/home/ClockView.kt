package com.bhavyam.min.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.theme.Tokens
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.dpi
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Date and time from the clock tokens: two patterns, two sizes and an arrangement. Updates on
 * the minute tick (every second only if a pattern shows seconds), on time/zone/locale changes,
 * and follows the system 12/24-hour setting. Also used as the specimen in the studio.
 */
class ClockView(context: Context) : LinearLayout(context), ThemeStore.Listener {

    private val themes = App.of(context).themes
    val date = ThemedText(context, Globals.TEXT_LABEL)
    val time = ThemedText(context, Globals.TEXT_LABEL)

    private val cal = Calendar.getInstance()
    private val out = StringBuilder()
    private var symbols = DateFormatSymbols.getInstance(Locale.getDefault())
    private var datePattern = ""
    private var timePattern = ""
    private var dateFormat = ClockFormat.compile("")
    private var timeFormat = ClockFormat.compile("")
    private var ticking = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_LOCALE_CHANGED) symbols = DateFormatSymbols.getInstance(Locale.getDefault())
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) cal.timeZone = TimeZone.getDefault()
            update()
        }
    }

    private val everySecond = object : Runnable {
        override fun run() {
            update()
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    init {
        gravity = Gravity.CENTER_VERTICAL
        addView(date)
        addView(time)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        themes.add(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        syncTicking()
        update()
    }

    override fun onDetachedFromWindow() {
        themes.remove(this)
        context.unregisterReceiver(receiver)
        removeCallbacks(everySecond)
        ticking = false
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) = arrange(theme.tokens)

    /** Applies the clock tokens: patterns, sizes, order and orientation. */
    private fun arrange(t: Tokens) {
        if (t.clockDate != datePattern) {
            datePattern = t.clockDate
            dateFormat = ClockFormat.compile(datePattern)
        }
        if (t.clockTime != timePattern) {
            timePattern = t.clockTime
            timeFormat = ClockFormat.compile(timePattern)
        }
        date.sizeSp = t.clockDateSize
        time.sizeSp = t.clockTimeSize

        val layout = t.clockLayout
        orientation = if (layout.stacked) VERTICAL else HORIZONTAL
        gravity = if (layout.stacked) Gravity.START else Gravity.BOTTOM
        val first = if (layout.timeFirst) time else date
        val second = if (layout.timeFirst) date else time
        if (getChildAt(0) !== first) {
            removeAllViews()
            addView(first)
            addView(second)
        }
        val gap = dpi(Globals.CLOCK_GAP)
        (first.layoutParams as LayoutParams).marginStart = 0
        (second.layoutParams as LayoutParams).marginStart = if (layout.stacked) 0 else gap
        second.requestLayout()
        syncTicking()
        update()
    }

    private fun syncTicking() {
        val need = isAttachedToWindow && (dateFormat.hasSeconds || timeFormat.hasSeconds)
        if (need == ticking) return
        ticking = need
        if (need) post(everySecond) else removeCallbacks(everySecond)
    }

    private fun update() {
        cal.timeInMillis = System.currentTimeMillis()
        val is24 = DateFormat.is24HourFormat(context)
        out.setLength(0)
        dateFormat.format(cal, is24, symbols, out)
        date.text = out
        out.setLength(0)
        timeFormat.format(cal, is24, symbols, out)
        time.text = out
        date.visibility = if (date.text.isEmpty()) GONE else VISIBLE
        time.visibility = if (time.text.isEmpty()) GONE else VISIBLE
    }
}
