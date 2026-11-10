package com.bhavyam.min.theme

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH → packed sRGB. Perceptual lightness means a hue drag never changes how bright a
 * colour feels, which is what lets one hue token drive a whole palette.
 */
object Oklch {
    fun color(l: Float, c: Float, h: Float, alpha: Float = 1f): Int {
        val lc = l.coerceIn(0f, 1f).toDouble()
        val cc = c.coerceAtLeast(0f).toDouble()
        val hr = Math.toRadians(((h % 360f + 360f) % 360f).toDouble())
        val a = cc * cos(hr)
        val b = cc * sin(hr)
        val l3 = (lc + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m3 = (lc - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s3 = (lc - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = channel(4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3)
        val g = channel(-1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3)
        val bl = channel(-0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3)
        val al = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Linear light → gamma-encoded 0..255. */
    private fun channel(linear: Double): Int {
        val x = linear.coerceIn(0.0, 1.0)
        val v = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
        return (v * 255 + 0.5).toInt().coerceIn(0, 255)
    }
}
