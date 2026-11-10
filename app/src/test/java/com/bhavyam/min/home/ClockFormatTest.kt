package com.bhavyam.min.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/** The KWGT-style patterns, checked against Friday 2 May 2008, 14:05:07. */
class ClockFormatTest {

    private val cal = Calendar.getInstance().apply { set(2008, Calendar.MAY, 2, 14, 5, 7) }
    private val symbols = DateFormatSymbols.getInstance(Locale.ENGLISH)

    private fun format(pattern: String, is24: Boolean = false): String {
        val out = StringBuilder()
        ClockFormat.compile(pattern).format(cal, is24, symbols, out)
        return out.toString()
    }

    @Test fun shortAndLongNumbers() {
        assertEquals("2 5 8", format("d m y"))
        assertEquals("02 05 2008", format("dd mm yyyy"))
        assertEquals("May 08", format("M yy"))
    }

    @Test fun names() {
        assertEquals("Fri, 02 05 2008", format("D, dd mm yyyy"))
        assertEquals("Friday, 2 May", format("DD, d MM"))
        assertEquals("Fri, 2 May", format("D, d MM"))
    }

    @Test fun hourFollowsSystemSetting() {
        assertEquals("2:05", format("h:ii", is24 = false))
        assertEquals("14:05", format("h:ii", is24 = true))
        assertEquals("14:05:07", format("HH:ii:ss"))
    }

    @Test fun amPmDisappearsIn24Hour() {
        assertEquals("02:05 pm", format("hh:ii a"))
        assertEquals("14:05", format("h:ii a", is24 = true))
    }

    @Test fun quotedTextIsLiteral() = assertEquals("at 2:05 PM", format("'at' h:ii A"))

    @Test fun secondsAreDetected() {
        assertTrue(ClockFormat.compile("h:ii:ss").hasSeconds)
        assertFalse(ClockFormat.compile("h:ii").hasSeconds)
    }
}
