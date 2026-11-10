package dev.bhavyam.min.clock

import kotlin.random.Random

/**
 * The clock shapes the home editor steps through as you resize the clock: wider shows more detail,
 * taller splits it over more lines. Each shape is just a list of [ClockFormat] lines, so a typed
 * custom format and a preset are the same kind of thing.
 */
object ClockPresets {

    /** Detail levels, narrowest first: (weekday part, date part). */
    private val DETAIL = listOf(
        "" to "",
        "D" to "d",
        "D" to "d M",
        "D" to "d MM",
        "DD" to "d MM",
        "DD" to "d MM yyyy",
    )
    private const val TIME = "h:ii"
    private const val INLINE_GAP = "  "

    const val LINES_MAX = 3
    val DETAIL_MAX get() = DETAIL.lastIndex

    private const val DEFAULT_DETAIL = 3
    private const val DEFAULT_LINES = 1

    /** The look of a fresh install: "Tue, 10 November  2:25" on one line. */
    val DEFAULT = preset(DEFAULT_DETAIL, DEFAULT_LINES)

    /** Lines for [detail] spread over [lines] (fewer if there isn't enough to split). */
    fun preset(detail: Int, lines: Int): List<String> {
        val (week, date) = DETAIL[detail.coerceIn(0, DETAIL.lastIndex)]
        val day = if (week.isEmpty()) date else "$week, $date"
        return when {
            day.isEmpty() -> listOf(TIME)
            lines <= 1 -> listOf(day + INLINE_GAP + TIME)
            lines == 2 || week.isEmpty() -> listOf(day, TIME)
            else -> listOf(week, date, TIME)
        }
    }

    /** Where [clock] sits in the preset grid as (detail, lines), or null for a hand-written format. */
    fun locate(clock: List<String>): Pair<Int, Int>? {
        for (d in 0..DETAIL.lastIndex) for (l in 1..LINES_MAX) if (preset(d, l) == clock) return d to l
        return null
    }

    /** Where resizing starts from: [clock]'s place in the grid, or the default's for a hand-written one. */
    fun shape(clock: List<String>) = locate(clock) ?: (DEFAULT_DETAIL to DEFAULT_LINES)

    fun random(rng: Random): List<String> = preset(rng.nextInt(DETAIL.size), rng.nextInt(1, LINES_MAX + 1))
}
