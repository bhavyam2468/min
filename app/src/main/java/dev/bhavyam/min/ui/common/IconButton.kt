package dev.bhavyam.min.ui.common

import android.annotation.SuppressLint
import android.content.Context
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme

/** A round, icon-only button (back arrows, sheet actions). */
@SuppressLint("ViewConstructor")
class IconButton(context: Context, icon: Icons.Icon, settings: SettingsStore, description: String) :
    IconView(context, icon, Globals.Space.ICON_BUTTON, muted = false) {

    private val press = PressDrawable(settings)

    init {
        background = press
        contentDescription = description
        isClickable = true
    }

    override fun applyTheme(theme: Theme) {
        super.applyTheme(theme)
        press.color = theme.pressed
        press.radius = Float.MAX_VALUE
    }
}
