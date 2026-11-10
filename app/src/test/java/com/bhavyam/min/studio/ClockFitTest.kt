package com.bhavyam.min.studio

import com.bhavyam.min.theme.ClockLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockFitTest {

    /** Every character half a size wide, lines 1.25 sizes tall. */
    private val fit = ClockFit(object : ClockFit.Measure {
        override fun width(pattern: String, sizeSp: Float) = pattern.length * sizeSp * 0.5f
        override fun lineHeight(sizeSp: Float) = sizeSp * 1.25f
        override val gap = 8f
    })

    @Test
    fun aOneLineBoxKeepsDateBesideTime() {
        val r = fit.fit(width = 400f, height = 18f, layout = ClockLayout.DATE_TIME)
        assertEquals(14f, r.timeSize)
        assertEquals(14f, r.dateSize)
        assertEquals(ClockLayout.DATE_TIME, r.layout)
    }

    @Test
    fun widerBoxesShowRicherDates() {
        val narrow = fit.fit(width = 80f, height = 18f, layout = ClockLayout.DATE_TIME)
        val wide = fit.fit(width = 400f, height = 18f, layout = ClockLayout.DATE_TIME)
        assertTrue(ClockFit.LADDER.indexOf(wide.date) > ClockFit.LADDER.indexOf(narrow.date))
        assertEquals("DD, d MM yyyy", wide.date)
    }

    @Test
    fun noRoomForADateShowsOnlyTheTime() {
        val r = fit.fit(width = 30f, height = 18f, layout = ClockLayout.DATE_TIME)
        assertEquals("", r.date)
        assertEquals(ClockFit.TIME, r.time)
    }

    @Test
    fun aTallBoxStacksTheTimeOverASmallerDate() {
        val r = fit.fit(width = 400f, height = 100f, layout = ClockLayout.DATE_TIME)
        assertTrue(r.timeSize > ClockFit.STACK_FROM)
        assertEquals(ClockLayout.TIME_OVER_DATE, r.layout)
        assertTrue(r.dateSize < r.timeSize)
        assertTrue(r.dateSize >= ClockFit.DATE_MIN)
        assertTrue(r.timeSize * 1.25f + r.dateSize * 1.25f <= 100f)
    }

    @Test
    fun aStackedChoiceIsKeptWhenStillStacked() {
        val r = fit.fit(width = 400f, height = 100f, layout = ClockLayout.DATE_OVER_TIME)
        assertEquals(ClockLayout.DATE_OVER_TIME, r.layout)
    }

    @Test
    fun aTinyBoxBottomsOutAtTheMinimumSize() {
        val r = fit.fit(width = 1f, height = 1f, layout = ClockLayout.TIME_DATE)
        assertEquals(9f, r.timeSize)
        assertEquals(ClockLayout.TIME_DATE, r.layout)
    }
}
