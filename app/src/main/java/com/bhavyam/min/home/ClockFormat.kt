package com.bhavyam.min.home

import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * KWGT-style clock patterns. A run of one letter is short, a run of two or more is long:
 *
 *     d  2        dd  02          day of month
 *     D  Fri      DD  Friday      weekday
 *     m  5        mm  05          month number
 *     M  Jan      MM  January     month name
 *     y  8        yy  08   yyyy 2008
 *     h  2        hh  02          hour, following the system 12/24-hour setting
 *     H  14       HH  14          hour, always 24-hour
 *     i  5        ii  05          minutes
 *     s  7        ss  07          seconds
 *     a  pm       A   PM          am/pm (empty when the system uses 24-hour time)
 *
 * Anything else is printed as-is; wrap words in single quotes to keep their letters literal
 * ('at' h:ii). The pattern is compiled once; formatting appends into a caller's builder.
 */
class ClockFormat private constructor(private val parts: List<Part>) {

    private sealed class Part {
        class Literal(val text: String) : Part()
        class Field(val letter: Char, val run: Int) : Part()
    }

    val hasSeconds: Boolean = parts.any { it is Part.Field && it.letter == 's' }

    fun format(cal: Calendar, is24: Boolean, symbols: DateFormatSymbols, out: StringBuilder) {
        val start = out.length
        for (p in parts) {
            when (p) {
                is Part.Literal -> out.append(p.text)
                is Part.Field -> field(p, cal, is24, symbols, out)
            }
        }
        // a dropped am/pm can leave a dangling space
        while (out.length > start && out[out.length - 1] == ' ') out.setLength(out.length - 1)
    }

    private fun field(f: Part.Field, cal: Calendar, is24: Boolean, sym: DateFormatSymbols, out: StringBuilder) {
        val long = f.run >= 2
        when (f.letter) {
            'd' -> num(cal[Calendar.DAY_OF_MONTH], long, out)
            'D' -> out.append(if (long) sym.weekdays[cal[Calendar.DAY_OF_WEEK]] else sym.shortWeekdays[cal[Calendar.DAY_OF_WEEK]])
            'm' -> num(cal[Calendar.MONTH] + 1, long, out)
            'M' -> out.append(if (long) sym.months[cal[Calendar.MONTH]] else sym.shortMonths[cal[Calendar.MONTH]])
            'y' -> {
                val y = cal[Calendar.YEAR]
                when (f.run) {
                    1 -> out.append(y % 10)
                    2 -> num(y % 100, true, out)
                    else -> out.append(y)
                }
            }
            'h' -> {
                val h24 = cal[Calendar.HOUR_OF_DAY]
                num(if (is24) h24 else (h24 + 11) % 12 + 1, long, out)
            }
            'H' -> num(cal[Calendar.HOUR_OF_DAY], long, out)
            'i' -> num(cal[Calendar.MINUTE], long, out)
            's' -> num(cal[Calendar.SECOND], long, out)
            'a', 'A' -> if (!is24) {
                val marker = sym.amPmStrings[cal[Calendar.AM_PM]]
                out.append(if (f.letter == 'a') marker.lowercase() else marker.uppercase())
            }
        }
    }

    private fun num(v: Int, pad: Boolean, out: StringBuilder) {
        if (pad && v < 10) out.append('0')
        out.append(v)
    }

    companion object {
        /** The letters that are fields; every other character prints as itself. */
        const val LETTERS = "dDmMyhHisaA"

        fun compile(pattern: String): ClockFormat {
            val parts = ArrayList<Part>()
            val literal = StringBuilder()
            fun flush() {
                if (literal.isNotEmpty()) parts += Part.Literal(literal.toString())
                literal.setLength(0)
            }
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\'' -> {
                        val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                        literal.append(pattern, i + 1, end)
                        i = end + 1
                    }
                    c in LETTERS -> {
                        var j = i
                        while (j < pattern.length && pattern[j] == c) j++
                        flush()
                        parts += Part.Field(c, j - i)
                        i = j
                    }
                    else -> {
                        literal.append(c)
                        i++
                    }
                }
            }
            flush()
            return ClockFormat(parts)
        }
    }
}
