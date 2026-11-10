package dev.bhavyam.min.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ClockFormatTest {

    /** Friday 2 May 2008, 9:05 pm. */
    private val time = LocalDateTime.of(2008, 5, 2, 21, 5)

    private fun f(pattern: String, is24Hour: Boolean = false) = ClockFormat.format(pattern, time, is24Hour, Locale.ENGLISH)

    @Test fun numbers() {
        assertEquals("2 5 8", f("d m y"))
        assertEquals("02 05 08", f("dd mm yy"))
        assertEquals("02 05 2008", f("dd mm yyyy"))
    }

    @Test fun names() {
        assertEquals("Fri, 02 05 2008", f("D, dd mm yyyy"))
        assertEquals("Friday", f("DD"))
        assertEquals("May May", f("M MM"))
        assertEquals("Jan January", ClockFormat.format("M MM", LocalDateTime.of(2008, 1, 2, 0, 0), false, Locale.ENGLISH))
    }

    @Test fun time() {
        assertEquals("9:05 pm", f("h:ii a"))
        assertEquals("09:05 PM", f("hh:ii A"))
        assertEquals("21:05", f("h:ii", is24Hour = true))
        assertEquals("21:05 ", f("hh:ii a", is24Hour = true))
        assertEquals("12:00", ClockFormat.format("h:ii", LocalDateTime.of(2008, 5, 2, 0, 0), false, Locale.ENGLISH))
    }

    @Test fun literals() {
        assertEquals("day 2", f("'day' d"))
        assertEquals("it's 9", f("'it''s' h"))
        assertEquals("— 2 · 5 —", f("— d · m —"))
    }

    @Test fun presetsRoundTrip() {
        for (d in 0..ClockPresets.DETAIL_MAX) for (l in 1..ClockPresets.LINES_MAX) {
            val p = ClockPresets.preset(d, l)
            val (ld, ll) = ClockPresets.locate(p)!!
            assertEquals(p, ClockPresets.preset(ld, ll))
        }
        assertEquals(listOf("D, d MM  h:ii"), ClockPresets.DEFAULT)
    }
}
