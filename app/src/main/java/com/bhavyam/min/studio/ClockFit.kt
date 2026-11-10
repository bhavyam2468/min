package com.bhavyam.min.studio

import com.bhavyam.min.theme.ClockLayout
import com.bhavyam.min.theme.Globals
import kotlin.math.max

/**
 * Turns a box the user drew around the clock into clock tokens, so resizing the clock is the
 * way you pick its format:
 *  • size: the largest text whose height fits the box — past [STACK_FROM] the date drops
 *    under the time instead of sitting beside it;
 *  • detail: the richest date pattern from [LADDER] that still fits the box's width.
 * Pure arithmetic over a [Measure], so the decisions are testable without a screen.
 */
class ClockFit(private val measure: Measure) {

    /** Text metrics in px for a clock pattern at a size in sp (the pattern formatted for now). */
    interface Measure {
        fun width(pattern: String, sizeSp: Float): Float
        fun lineHeight(sizeSp: Float): Float
        val gap: Float
    }

    data class Result(val date: String, val time: String, val dateSize: Float, val timeSize: Float, val layout: ClockLayout)

    /** Clock tokens filling a [width] × [height] px box; [layout] is the current arrangement. */
    fun fit(width: Float, height: Float, layout: ClockLayout): Result {
        var size = Globals.CLOCK_SIZE_MIN
        var s = Globals.CLOCK_SIZE_MAX
        while (s >= Globals.CLOCK_SIZE_MIN) {
            val tall = if (s > STACK_FROM) measure.lineHeight(s) + measure.lineHeight(dateSize(s)) else measure.lineHeight(s)
            if (tall <= height && measure.width(TIME, s) <= width) {
                size = s
                break
            }
            s -= 1f
        }
        val stacked = size > STACK_FROM
        val dateSp = if (stacked) dateSize(size) else size
        val room = if (stacked) width else width - measure.width(TIME, size) - measure.gap
        val date = LADDER.lastOrNull { it.isEmpty() || measure.width(it, dateSp) <= room } ?: ""
        val arranged = when {
            stacked == layout.stacked -> layout
            stacked -> ClockLayout.TIME_OVER_DATE
            else -> ClockLayout.DATE_TIME
        }
        return Result(date, TIME, dateSp, size, arranged)
    }

    private fun dateSize(timeSp: Float) = max(DATE_MIN, (timeSp * DATE_RATIO).toInt().toFloat())

    companion object {
        /** From bare to everything, each step a little longer than the one before. */
        val LADDER = listOf("", "D", "D d", "D, d M", "D, d MM", "DD, d MM", "DD, d MM yyyy")
        const val TIME = "h:ii"
        const val STACK_FROM = 24f   // sp; bigger than this the time gets a line of its own
        const val DATE_RATIO = 0.37f // stacked date size relative to the time
        const val DATE_MIN = 12f
    }
}
