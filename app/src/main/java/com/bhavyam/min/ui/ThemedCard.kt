package com.bhavyam.min.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore

/**
 * A surface: card radius, elevation and a hairline border, in the surface colour —
 * or the raised surface colour for things that float (the popup).
 */
class ThemedCard(context: Context, private val raised: Boolean = false) :
    LinearLayout(context), ThemeStore.Listener {

    private val themes = App.of(context).themes
    private val shape = GradientDrawable()

    init {
        background = shape
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        themes.add(this)
    }

    override fun onDetachedFromWindow() {
        themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        val t = theme.tokens
        shape.setColor(if (raised) theme.surface2 else theme.surface)
        shape.cornerRadius = dp(t.radiusCard)
        shape.setStroke(dpi(1f), withAlpha(theme.border, if (theme.dark) 0.7f else 0.5f))
        elevation = dp(Globals.ELEVATION_MAX) * t.elevation * if (raised) 1f else 0.5f
        outlineSpotShadowColor = theme.shadow
        outlineAmbientShadowColor = theme.shadow
    }
}
