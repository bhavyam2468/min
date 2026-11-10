package dev.bhavyam.min.theme

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Perceptual OKLCH → packed sRGB, ported from Token Studio's colour engine (gamut clipped per channel). */
object Oklch {

    fun toArgb(l: Float, c: Float, h: Float, alpha: Float = 1f): Int {
        val lightness = l.coerceIn(0f, 1f).toDouble()
        val chroma = c.coerceAtLeast(0f).toDouble()
        val rad = Math.toRadians(h.toDouble())
        val a = chroma * cos(rad)
        val b = chroma * sin(rad)
        val lc = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val mc = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val sc = (lightness - 0.0894841775 * a - 1.291485548 * b).pow(3)
        val r = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc
        val g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc
        val bl = -0.0041960863 * lc - 0.7034186147 * mc + 1.707614701 * sc
        return (channel(alpha.toDouble(), linear = false) shl 24) or
            (channel(r) shl 16) or (channel(g) shl 8) or channel(bl)
    }

    private fun channel(v: Double, linear: Boolean = true): Int {
        val x = v.coerceIn(0.0, 1.0)
        val encoded = if (!linear) x else if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
        return (encoded * 255 + 0.5).toInt()
    }
}
