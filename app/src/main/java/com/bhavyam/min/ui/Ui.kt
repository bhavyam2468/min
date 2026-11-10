package com.bhavyam.min.ui

import android.view.View
import android.view.WindowInsets

/** dp → px. */
fun View.dp(v: Float): Float = v * resources.displayMetrics.density

/** dp → whole px. */
fun View.dpi(v: Float): Int = (dp(v) + 0.5f).toInt()

/** Linear blend of two ARGB colours, channel by channel. */
fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val x = (a ushr shift) and 0xFF
        val y = (b ushr shift) and 0xFF
        return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255) shl shift
    }
    return ch(24) or ch(16) or ch(8) or ch(0)
}

/** [color] with its alpha multiplied by [a]. */
fun withAlpha(color: Int, a: Float): Int =
    (((color ushr 24) * a.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0xFFFFFF)

/** The area no system bar or display cutout covers, honoured by every layer. */
fun WindowInsets.safe() = getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
