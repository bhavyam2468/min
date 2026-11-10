package dev.bhavyam.min.clock

import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The clock's format language (in the spirit of KWGT). A run of one letter is one field; its length
 * picks the form:
 *
 * | letters | gives (Friday 2 May 2008, 9:05 pm) |
 * |---|---|
 * | `d` · `dd` | `2` · `02` (day) |
 * | `D` · `DD` | `Fri` · `Friday` |
 * | `m` · `mm` | `5` · `05` (month) |
 * | `M` · `MM` | `May` · `May` (`Jan` · `January`) |
 * | `y` · `yy` · `yyyy` | `8` · `08` · `2008` |
 * | `h` · `hh` | `9` · `09` (hour; 12 or 24 h as the system is set) |
 * | `i` · `ii` | `5` · `05` (minutes) |
 * | `a` · `A` | `pm` · `PM` (only shown in 12-hour time) |
 *
 * Anything else is printed as is; put letters in single quotes to print them literally
 * (`'week' w` …), and write `''` for a quote.
 */
object ClockFormat {

    /** The field letters, for the editor's cheat sheet. */
    const val FIELDS = "dDmMyhiaA"

    fun format(pattern: String, time: LocalDateTime, is24Hour: Boolean, locale: Locale): String {
        val out = StringBuilder(pattern.length + EXTRA)
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == QUOTE) {
                i = literal(pattern, i + 1, out)
                continue
            }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            if (c in FIELDS) field(c, n, time, is24Hour, locale, out) else repeat(n) { out.append(c) }
            i += n
        }
        return out.toString()
    }

    /** Copies a quoted run starting after its opening quote; returns the index after the closing one. */
    private fun literal(p: String, from: Int, out: StringBuilder): Int {
        if (from < p.length && p[from] == QUOTE) { // '' on its own is a quote character
            out.append(QUOTE)
            return from + 1
        }
        var i = from
        while (i < p.length) {
            if (p[i] == QUOTE) {
                if (i + 1 < p.length && p[i + 1] == QUOTE) { // '' inside quotes, too
                    out.append(QUOTE)
                    i += 2
                    continue
                }
                return i + 1
            }
            out.append(p[i++])
        }
        return i
    }

    private fun field(c: Char, n: Int, t: LocalDateTime, is24Hour: Boolean, locale: Locale, out: StringBuilder) {
        when (c) {
            'd' -> number(t.dayOfMonth, n, out)
            'D' -> out.append(t.dayOfWeek.getDisplayName(if (n == 1) TextStyle.SHORT else TextStyle.FULL, locale))
            'm' -> number(t.monthValue, n, out)
            'M' -> out.append(t.month.getDisplayName(if (n == 1) TextStyle.SHORT else TextStyle.FULL, locale))
            'y' -> if (n >= FULL_YEAR) out.append(t.year) else number(t.year % CENTURY, n, out)
            'h' -> number(if (is24Hour) t.hour else (t.hour + HALF_DAY - 1) % HALF_DAY + 1, n, out)
            'i' -> number(t.minute, n, out)
            'a', 'A' -> if (!is24Hour) {
                val mark = if (t.hour < HALF_DAY) "am" else "pm"
                repeat(n) { out.append(if (c == 'A') mark.uppercase(locale) else mark) }
            }
        }
    }

    /** One letter: as is. Two or more: at least two digits. */
    private fun number(v: Int, n: Int, out: StringBuilder) {
        if (n >= 2 && v < 10) out.append('0')
        out.append(v)
    }

    private const val QUOTE = '\''
    private const val EXTRA = 16
    private const val FULL_YEAR = 3
    private const val CENTURY = 100
    private const val HALF_DAY = 12
}
