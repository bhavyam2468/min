package dev.bhavyam.min.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme

/** A text button drawn by [ButtonDrawable]; it sinks a little under the finger, on a spring. */
@SuppressLint("ViewConstructor")
class TokenButton(context: Context, tier: Int, private val settings: SettingsStore) : TextView(context), Themed {

    private val drawable = ButtonDrawable(tier)
    private val sink = Spring(PRECISION) { scaleX = it; scaleY = it }

    init {
        background = drawable
        gravity = Gravity.CENTER
        isSingleLine = true
        sink.snapTo(1f)
    }

    override fun applyTheme(theme: Theme) {
        drawable.style(theme, if (theme.isPill) Float.MAX_VALUE else theme.radiusButton)
        style(theme, Globals.Type.CHIP, drawable.foreground)
        val padX = theme.em(theme.tokens.padX * Globals.Type.CHIP).toInt()
        val padY = theme.em(theme.tokens.padY * Globals.Type.CHIP).toInt()
        setPadding(padX, padY, padX, padY)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        sink.animateTo(if (pressed) PRESSED_SCALE else 1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
    }

    private companion object {
        const val PRECISION = 0.0005f
        const val PRESSED_SCALE = 0.95f
    }
}
