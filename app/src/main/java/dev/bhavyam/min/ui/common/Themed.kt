package dev.bhavyam.min.ui.common

import dev.bhavyam.min.theme.Theme

/** A view that restyles itself from the theme. Called once at start and on every token change. */
interface Themed {
    fun applyTheme(theme: Theme)
}
