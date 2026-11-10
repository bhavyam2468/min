package dev.bhavyam.min.icons

import android.graphics.Path
import android.graphics.RectF

/**
 * The icon set, on a 24-unit grid. Most come from Token Studio's own set; back, grip, info, chevron
 * and eye are Lucide icons (ISC licence) drawn on the same grid. Shapes take the icon style's fill;
 * [line]s are only ever stroked.
 */
object Icons {

    class Part(val path: Path, val isLine: Boolean)

    class Icon(vararg parts: Part) {
        val parts = parts.toList()
    }

    private fun shape(d: String) = Part(SvgPath.parse(d), false)
    private fun line(d: String) = Part(SvgPath.parse(d), true)
    private fun circle(cx: Float, cy: Float, r: Float) = Part(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }, false)
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        Part(Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }, false)

    // Token Studio
    val sparkle by lazy {
        Icon(
            shape("M12 3c.6 4.3 2.7 6.4 7 7-4.3.6-6.4 2.7-7 7-.6-4.3-2.7-6.4-7-7 4.3-.6 6.4-2.7 7-7z"),
            shape("M19 15.5c.2 1.3.8 1.9 2 2-1.2.2-1.8.8-2 2-.2-1.2-.8-1.8-2-2 1.2-.1 1.8-.7 2-2z"),
        )
    }
    val heart by lazy {
        Icon(shape("M12 20s-7.5-4.6-9-9.2C2 7.4 4.3 4.5 7.5 4.5c2 0 3.4 1.1 4.5 2.6 1.1-1.5 2.5-2.6 4.5-2.6 3.2 0 5.5 2.9 4.5 6.3C19.5 15.4 12 20 12 20z"))
    }
    val arrow by lazy { Icon(line("M5 12h14M13 6l6 6-6 6")) }
    val star by lazy { Icon(shape("m12 3.2 2.7 5.5 6 .9-4.4 4.2 1.1 6-5.4-2.8-5.4 2.8 1-6-4.3-4.2 6-.9z")) }
    val search by lazy { Icon(circle(11f, 11f, 6.5f), line("m20 20-4.2-4.2")) }
    val undo by lazy { Icon(line("M9 7 4.5 11.5 9 16"), line("M5 11.5h9.5a5 5 0 0 1 0 10H12")) }
    val redo by lazy { Icon(line("m15 7 4.5 4.5L15 16"), line("M19 11.5H9.5a5 5 0 0 0 0 10H12")) }
    val shuffle by lazy {
        Icon(line("M3.5 7h3.2c4.6 0 5.6 10 10.6 10h3.2M3.5 17h3.2c1.7 0 2.8-1.3 3.7-3M14 10c.9-1.7 2-3 3.3-3h3.2M18 4.5 20.5 7 18 9.5M18 14.5l2.5 2.5-2.5 2.5"))
    }
    val reset by lazy { Icon(line("M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9M4.5 4.5V9H9")) }
    val code by lazy { Icon(line("m8.5 7-5 5 5 5M15.5 7l5 5-5 5")) }
    val copy by lazy {
        Icon(rect(8.5f, 8.5f, 12f, 12f, 2f), line("M15.5 5.5v-1A1.5 1.5 0 0 0 14 3H5A1.5 1.5 0 0 0 3.5 4.5V14A1.5 1.5 0 0 0 5 15.5h1"))
    }
    val close by lazy { Icon(line("M6 6l12 12M18 6 6 18")) }

    // Lucide
    val back by lazy { Icon(line("m12 19-7-7 7-7"), line("M19 12H5")) }
    val grip by lazy {
        Icon(
            circle(9f, 12f, 1f), circle(9f, 5f, 1f), circle(9f, 19f, 1f),
            circle(15f, 12f, 1f), circle(15f, 5f, 1f), circle(15f, 19f, 1f),
        )
    }
    val info by lazy { Icon(circle(12f, 12f, 10f), line("M12 16v-4"), line("M12 8h.01")) }
    val chevron by lazy { Icon(line("m9 18 6-6-6-6")) }
    val eye by lazy {
        Icon(
            shape("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"),
            circle(12f, 12f, 3f),
        )
    }
}
