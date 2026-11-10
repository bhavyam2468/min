package com.bhavyam.min.ui

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
 * Minimal SVG path-data parser (M L H V C S Q T A Z, absolute and relative) into a [Path].
 * Runs once per icon; the result is cached by [Icon].
 */
object SvgPath {

    fun parse(d: String): Path {
        val p = Path()
        val r = Reader(d)
        var x = 0f; var y = 0f            // current point
        var sx = 0f; var sy = 0f          // subpath start
        var cx = 0f; var cy = 0f          // last control point (for S / T reflection)
        var cmd = ' '
        while (r.skipSeparators()) {
            if (r.peekCommand()) cmd = r.next()
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.lowercaseChar()) {
                'm' -> {
                    x = ox + r.num(); y = oy + r.num()
                    p.moveTo(x, y); sx = x; sy = y; cx = x; cy = y
                    cmd = if (rel) 'l' else 'L'   // extra pairs after a move are line-tos
                }
                'l' -> { x = ox + r.num(); y = oy + r.num(); p.lineTo(x, y); cx = x; cy = y }
                'h' -> { x = ox + r.num(); p.lineTo(x, y); cx = x; cy = y }
                'v' -> { y = oy + r.num(); p.lineTo(x, y); cx = x; cy = y }
                'c' -> {
                    val x1 = ox + r.num(); val y1 = oy + r.num()
                    cx = ox + r.num(); cy = oy + r.num()
                    x = ox + r.num(); y = oy + r.num()
                    p.cubicTo(x1, y1, cx, cy, x, y)
                }
                's' -> {
                    val x1 = 2 * x - cx; val y1 = 2 * y - cy
                    cx = ox + r.num(); cy = oy + r.num()
                    x = ox + r.num(); y = oy + r.num()
                    p.cubicTo(x1, y1, cx, cy, x, y)
                }
                'q' -> {
                    cx = ox + r.num(); cy = oy + r.num()
                    x = ox + r.num(); y = oy + r.num()
                    p.quadTo(cx, cy, x, y)
                }
                't' -> {
                    cx = 2 * x - cx; cy = 2 * y - cy
                    x = ox + r.num(); y = oy + r.num()
                    p.quadTo(cx, cy, x, y)
                }
                'a' -> {
                    val rx = r.num(); val ry = r.num(); val rot = r.num()
                    val large = r.flag(); val sweep = r.flag()
                    val ex = ox + r.num(); val ey = oy + r.num()
                    arc(p, x, y, ex, ey, rx, ry, rot, large, sweep)
                    x = ex; y = ey; cx = x; cy = y
                }
                'z' -> { p.close(); x = sx; y = sy; cx = x; cy = y }
                else -> throw IllegalArgumentException("Unsupported path command '$cmd'")
            }
        }
        return p
    }

    /** Endpoint arc → centre parameterisation → cubic segments of at most 90°. */
    private fun arc(
        p: Path, x0: Float, y0: Float, x1: Float, y1: Float,
        rx0: Float, ry0: Float, rotation: Float, large: Boolean, sweep: Boolean,
    ) {
        if (rx0 == 0f || ry0 == 0f) { p.lineTo(x1, y1); return }
        var rx = abs(rx0).toDouble(); var ry = abs(ry0).toDouble()
        val phi = Math.toRadians(rotation.toDouble())
        val cosP = cos(phi); val sinP = sin(phi)
        val dx = (x0 - x1) / 2.0; val dy = (y0 - y1) / 2.0
        val x1p = cosP * dx + sinP * dy
        val y1p = -sinP * dx + cosP * dy
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) { val s = sqrt(lambda); rx *= s; ry *= s }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coef = sqrt((num / den).coerceAtLeast(0.0))
        if (large == sweep) coef = -coef
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val ccx = cosP * cxp - sinP * cyp + (x0 + x1) / 2.0
        val ccy = sinP * cxp + cosP * cyp + (y0 + y1) / 2.0
        val theta1 = atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
        var delta = atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - theta1
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        val segments = ceil(abs(delta) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = delta / segments
        val k = 4.0 / 3.0 * tan(step / 4)
        var t = theta1
        repeat(segments) {
            val c1 = cos(t); val s1 = sin(t)
            val t2 = t + step
            val c2 = cos(t2); val s2 = sin(t2)
            fun px(ex: Double, ey: Double) = (ccx + rx * ex * cosP - ry * ey * sinP).toFloat()
            fun py(ex: Double, ey: Double) = (ccy + rx * ex * sinP + ry * ey * cosP).toFloat()
            p.cubicTo(
                px(c1 - k * s1, s1 + k * c1), py(c1 - k * s1, s1 + k * c1),
                px(c2 + k * s2, s2 - k * c2), py(c2 + k * s2, s2 - k * c2),
                px(c2, s2), py(c2, s2),
            )
            t = t2
        }
    }

    /** Tokenizer for path data: commands, numbers like ".5-.3" and packed arc flags. */
    private class Reader(private val s: String) {
        private var i = 0

        fun skipSeparators(): Boolean {
            while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\t')) i++
            return i < s.length
        }

        fun peekCommand(): Boolean = s[i].isLetter() && s[i] != 'e' && s[i] != 'E'

        fun next(): Char = s[i++]

        fun flag(): Boolean {
            skipSeparators()
            return s[i++] == '1'
        }

        fun num(): Float {
            skipSeparators()
            val start = i
            if (s[i] == '-' || s[i] == '+') i++
            var dot = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c.isDigit() -> i++
                    c == '.' && !dot -> { dot = true; i++ }
                    (c == 'e' || c == 'E') -> {
                        i++
                        if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                    }
                    else -> break
                }
            }
            return s.substring(start, i).toFloat()
        }
    }
}
