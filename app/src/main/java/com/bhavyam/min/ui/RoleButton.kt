package com.bhavyam.min.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.Variant

/**
 * A tappable label painted in a colour [role] and that role's button type — solid, soft,
 * outline, ghost or gradient — with the radius, padding, depth, type and icon tokens.
 * Favourites are [Role.HOME], search results [Role.SEARCH], popup options [Role.MENU].
 */
class RoleButton(
    context: Context,
    private val role: Role,
    sizeSp: Float,
    icon: Icon? = null,
) : ThemedText(context, sizeSp) {

    private val shape = GradientDrawable()
    private val glyph = icon?.let { IconDrawable(it, dpi(Globals.ICON)) }
    private val press = Press(this) { paint(themes.theme, it) }

    var icon: Icon?
        get() = glyph?.icon
        set(v) { if (v != null) glyph?.icon = v }

    init {
        background = shape
        gravity = Gravity.CENTER_VERTICAL
        glyph?.let {
            setCompoundDrawablesRelativeWithIntrinsicBounds(it, null, null, null)
            compoundDrawablePadding = dpi(Globals.ICON_GAP)
        }
    }

    /** Where the icon sits, for the studio's hit-testing. */
    val iconRight: Int get() = paddingLeft + (glyph?.intrinsicWidth ?: 0)

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        press.set(pressed)
    }

    override fun onTheme(theme: Theme) {
        super.onTheme(theme)
        val t = theme.tokens
        val c = theme.colors(role)
        val v = t.variant(role)
        val filled = v == Variant.SOLID || v == Variant.GRADIENT
        val fg = if (filled) c.on else c.ink
        setTextColor(fg)
        glyph?.style(fg, t.iconStyle, t.iconStroke)
        setPadding(dpi(t.padX), dpi(t.padY), dpi(t.padX), dpi(t.padY))
        shape.cornerRadius = dp(t.radiusButton)
        if (v == Variant.OUTLINE) shape.setStroke(dpi(Globals.OUTLINE_WIDTH), c.fill) else shape.setStroke(0, 0)
        elevation = if (filled) dp(Globals.ELEVATION_MAX) * t.elevation * 0.4f else 0f
        outlineSpotShadowColor = theme.shadow
        outlineAmbientShadowColor = theme.shadow
        paint(theme, press.depth)
    }

    /** Fill for the current variant, deepened by how far the finger has pressed it. */
    private fun paint(theme: Theme, depth: Float) {
        val c = theme.colors(role)
        when (theme.tokens.variant(role)) {
            Variant.SOLID -> shape.setColor(mix(c.fill, c.ink, depth * 0.15f))
            Variant.GRADIENT -> {
                shape.orientation = GradientDrawable.Orientation.TL_BR
                shape.colors = intArrayOf(c.gradStart, c.gradEnd)
            }
            Variant.SOFT -> shape.setColor(mix(c.soft, c.ink, depth * 0.12f))
            Variant.OUTLINE, Variant.GHOST -> shape.setColor(withAlpha(c.soft, depth))
        }
    }
}
