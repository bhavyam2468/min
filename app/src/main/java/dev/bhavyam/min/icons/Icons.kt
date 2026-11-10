package dev.bhavyam.min.icons

import android.graphics.Path

/**
 * The icon set: stroked outlines on a 24-unit grid. The studio tools come from Token Studio's own
 * set; the rest are Lucide icons (ISC licence) drawn on the same grid.
 */
object Icons {

    class Icon(vararg val paths: Path)

    private fun d(path: String) = SvgPath.parse(path)
    private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

    // Token Studio
    val star by lazy { Icon(d("m12 3.2 2.7 5.5 6 .9-4.4 4.2 1.1 6-5.4-2.8-5.4 2.8 1-6-4.3-4.2 6-.9z")) }
    val search by lazy { Icon(circle(11f, 11f, 6.5f), d("m20 20-4.2-4.2")) }
    val undo by lazy { Icon(d("M9 7 4.5 11.5 9 16"), d("M5 11.5h9.5a5 5 0 0 1 0 10H12")) }
    val redo by lazy { Icon(d("m15 7 4.5 4.5L15 16"), d("M19 11.5H9.5a5 5 0 0 0 0 10H12")) }
    val shuffle by lazy {
        Icon(d("M3.5 7h3.2c4.6 0 5.6 10 10.6 10h3.2M3.5 17h3.2c1.7 0 2.8-1.3 3.7-3M14 10c.9-1.7 2-3 3.3-3h3.2M18 4.5 20.5 7 18 9.5M18 14.5l2.5 2.5-2.5 2.5"))
    }
    val reset by lazy { Icon(d("M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9M4.5 4.5V9H9")) }
    val close by lazy { Icon(d("M6 6l12 12M18 6 6 18")) }

    // Lucide
    val back by lazy { Icon(d("m12 19-7-7 7-7"), d("M19 12H5")) }
    val check by lazy { Icon(d("M20 6 9 17l-5-5")) }
    val plus by lazy { Icon(d("M5 12h14"), d("M12 5v14")) }
    val grip by lazy {
        Icon(circle(9f, 12f, 1f), circle(9f, 5f, 1f), circle(9f, 19f, 1f), circle(15f, 12f, 1f), circle(15f, 5f, 1f), circle(15f, 19f, 1f))
    }
    val info by lazy { Icon(circle(12f, 12f, 10f), d("M12 16v-4"), d("M12 8h.01")) }
    val chevron by lazy { Icon(d("m9 18 6-6-6-6")) }
}
