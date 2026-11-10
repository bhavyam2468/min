package dev.bhavyam.min.ui.home

import android.graphics.RectF
import dev.bhavyam.min.theme.Anchor

/**
 * Where the clock and the apps sit inside home's box. Pure, so the editor can ask "what if the clock
 * went there?" for every anchor without moving anything.
 *
 * Each block takes its column (left · centre · right) and row (top · middle · bottom). Two blocks
 * in the same row stack, clock first. A block in the middle row gives way to one above or below it
 * rather than overlap. [inset] lets the apps' text, not their padding, meet the box's side edges.
 */
object Placement {

    class Size(var width: Float = 0f, var height: Float = 0f, var inset: Float = 0f)

    fun place(box: RectF, gap: Float, clock: Size, clockAt: Anchor, apps: Size, appsAt: Anchor, clockOut: RectF, appsOut: RectF) {
        val gapBetween = if (clock.height > 0f && apps.height > 0f) gap else 0f
        clockOut.set(0f, 0f, clock.width, clock.height)
        appsOut.set(0f, 0f, apps.width, apps.height)
        clockOut.offsetTo(x(box, clockAt.column, clock), 0f)
        appsOut.offsetTo(x(box, appsAt.column, apps), 0f)
        if (clockAt.row == appsAt.row) {
            val top = y(box, clockAt.row, clock.height + gapBetween + apps.height)
            clockOut.offsetTo(clockOut.left, top)
            appsOut.offsetTo(appsOut.left, top + clock.height + gapBetween)
            return
        }
        clockOut.offsetTo(clockOut.left, y(box, clockAt.row, clock.height))
        appsOut.offsetTo(appsOut.left, y(box, appsAt.row, apps.height))
        val clockAbove = clockAt.row < appsAt.row
        val upper = if (clockAbove) clockOut else appsOut
        val lower = if (clockAbove) appsOut else clockOut
        val overlap = upper.bottom + gapBetween - lower.top
        val sideBySide = upper.right <= lower.left || lower.right <= upper.left
        if (overlap <= 0f || sideBySide) return
        when (MIDDLE) {
            maxOf(clockAt.row, appsAt.row) -> lower.offset(0f, overlap)
            minOf(clockAt.row, appsAt.row) -> upper.offset(0f, -overlap)
        }
    }

    private fun x(box: RectF, column: Int, s: Size) = when (column) {
        0 -> box.left - s.inset
        1 -> box.centerX() - s.width / 2f
        else -> box.right - s.width + s.inset
    }

    private fun y(box: RectF, row: Int, height: Float) = when (row) {
        0 -> box.top
        MIDDLE -> box.centerY() - height / 2f
        else -> box.bottom - height
    }

    private const val MIDDLE = 1
}
