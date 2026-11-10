package dev.bhavyam.min.theme

import android.graphics.Paint

/** How every icon is painted: shape fill opacity and the stroke's caps/joins. */
enum class IconStyle(val label: String, val fillAlpha: Float, val cap: Paint.Cap, val join: Paint.Join) {
    Outline("Outline", 0f, Paint.Cap.ROUND, Paint.Join.ROUND),
    Duotone("Duotone", 0.24f, Paint.Cap.ROUND, Paint.Join.ROUND),
    Filled("Filled", 1f, Paint.Cap.ROUND, Paint.Join.ROUND),
    Sharp("Sharp", 0f, Paint.Cap.SQUARE, Paint.Join.MITER),
}
