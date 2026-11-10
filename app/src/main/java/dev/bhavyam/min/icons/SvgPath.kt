package dev.bhavyam.min.icons

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Parses SVG path data (the `d` attribute) into a [Path], so icons can be kept as the same strings
 * the web icon sets publish. Supports M L H V C S A Z in absolute and relative form — everything the
 * bundled icons use. Runs once per icon.
 */
object SvgPath {

    fun parse(d: String): Path {
        val p = Path()
        val t = Tokenizer(d)
        var cmd = ' '
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var ctrlX = 0f
        var ctrlY = 0f
        while (t.hasMore()) {
            if (t.atCommand()) cmd = t.command()
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + t.number(); y = oy + t.number()
                    p.moveTo(x, y)
                    startX = x; startY = y
                    cmd = if (rel) 'l' else 'L' // further pairs are implicit line-tos
                }
                'L' -> { x = ox + t.number(); y = oy + t.number(); p.lineTo(x, y) }
                'H' -> { x = ox + t.number(); p.lineTo(x, y) }
                'V' -> { y = oy + t.number(); p.lineTo(x, y) }
                'C' -> {
                    val x1 = ox + t.number(); val y1 = oy + t.number()
                    ctrlX = ox + t.number(); ctrlY = oy + t.number()
                    x = ox + t.number(); y = oy + t.number()
                    p.cubicTo(x1, y1, ctrlX, ctrlY, x, y)
                }
                'S' -> {
                    val x1 = 2 * x - ctrlX; val y1 = 2 * y - ctrlY
                    ctrlX = ox + t.number(); ctrlY = oy + t.number()
                    x = ox + t.number(); y = oy + t.number()
                    p.cubicTo(x1, y1, ctrlX, ctrlY, x, y)
                }
                'A' -> {
                    val rx = t.number(); val ry = t.number(); val rotation = t.number()
                    val large = t.flag(); val sweep = t.flag()
                    val ex = ox + t.number(); val ey = oy + t.number()
                    arc(p, x, y, ex, ey, rx, ry, rotation, large, sweep)
                    x = ex; y = ey
                }
                'Z' -> { p.close(); x = startX; y = startY }
                else -> throw IllegalArgumentException("Unsupported path command '$cmd' in \"$d\"")
            }
            if (cmd.uppercaseChar() != 'C' && cmd.uppercaseChar() != 'S') { ctrlX = x; ctrlY = y }
        }
        return p
    }

    /** SVG endpoint arc → centre parameterisation → cubic segments of at most 90°. */
    private fun arc(
        p: Path, x0: Float, y0: Float, x1: Float, y1: Float,
        rxIn: Float, ryIn: Float, angle: Float, large: Boolean, sweep: Boolean,
    ) {
        if (x0 == x1 && y0 == y1) return
        var rx = abs(rxIn).toDouble()
        var ry = abs(ryIn).toDouble()
        if (rx == 0.0 || ry == 0.0) { p.lineTo(x1, y1); return }
        val phi = Math.toRadians(angle.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (x0 - x1) / 2.0
        val dy = (y0 - y1) / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = (if (large == sweep) -1 else 1) * sqrt((num / den).coerceAtLeast(0.0))
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2.0
        val start = atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
        var delta = atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - start
        if (sweep && delta < 0) delta += 2 * PI
        if (!sweep && delta > 0) delta -= 2 * PI

        val segments = ceil(abs(delta) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = delta / segments
        val k = 4.0 / 3.0 * tan(step / 4)
        var a = start
        for (i in 0 until segments) {
            val b = a + step
            val cosA = cos(a); val sinA = sin(a); val cosB = cos(b); val sinB = sin(b)
            fun px(ex: Double, ey: Double) = (cx + rx * ex * cosPhi - ry * ey * sinPhi).toFloat()
            fun py(ex: Double, ey: Double) = (cy + rx * ex * sinPhi + ry * ey * cosPhi).toFloat()
            p.cubicTo(
                px(cosA - k * sinA, sinA + k * cosA), py(cosA - k * sinA, sinA + k * cosA),
                px(cosB + k * sinB, sinB - k * cosB), py(cosB + k * sinB, sinB - k * cosB),
                px(cosB, sinB), py(cosB, sinB),
            )
            a = b
        }
    }

    private class Tokenizer(private val s: String) {
        private var i = 0

        private fun skip() {
            while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\t')) i++
        }

        fun hasMore(): Boolean { skip(); return i < s.length }

        fun atCommand(): Boolean { skip(); return s[i].isLetter() }

        fun command(): Char = s[i++]

        /** Arc flags are single digits and may be written without separators ("a1 1 0 01 2 2"). */
        fun flag(): Boolean { skip(); return s[i++] == '1' }

        fun number(): Float {
            skip()
            val start = i
            if (s[i] == '-' || s[i] == '+') i++
            var dot = false
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) i++
                else if (c == '.' && !dot) { dot = true; i++ }
                else if ((c == 'e' || c == 'E') && i + 1 < s.length) { i++; if (s[i] == '-' || s[i] == '+') i++ }
                else break
            }
            return s.substring(start, i).toFloat()
        }
    }
}
