package dev.bhavyam.min.ui.common

import android.util.TypedValue
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.theme.Theme

/** Size (a [Globals.Type] multiplier), colour and weight from the theme, in one call. */
fun TextView.style(theme: Theme, size: Float, color: Int = theme.text, strong: Boolean = false) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(size))
    setTextColor(color)
    typeface = if (strong) theme.strong else theme.regular
}

/** The tiny dim uppercase label style (search categories, settings sections). */
fun TextView.styleCaps(theme: Theme, size: Float) {
    style(theme, size, theme.muted)
    isAllCaps = true
    letterSpacing = Globals.Type.CAPS_TRACKING
}
