package com.bhavyam.min.ui

import android.content.Context
import android.widget.FrameLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore

/**
 * A full-screen layer over home that arrives with a fade and a short rise on a spring and
 * leaves the same way in reverse. Settings and the studio are overlays, not pages.
 */
abstract class OverlayLayer(context: Context) : FrameLayout(context), ThemeStore.Listener {

    protected val app = App.of(context)
    private val spring = Spring(0.001f) { p ->
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * dp(Globals.SEARCH_TRAVEL) * 0.5f
    }

    var isOpen = false
        private set

    init {
        visibility = GONE
        isClickable = true // touches stop here instead of reaching home underneath
        spring.onRest = { if (!isOpen) visibility = GONE }
    }

    open fun open() {
        isOpen = true
        visibility = VISIBLE
        spring.animateTo(1f, app.motion[SettingsRegistry.SPRING_OVERLAY])
    }

    open fun close() {
        isOpen = false
        spring.animateTo(0f, app.motion[SettingsRegistry.SPRING_OVERLAY])
    }

    /** Gone at once, no motion: the launcher is resetting while nobody is looking. */
    fun closeNow() {
        if (isOpen) close()
        spring.snapTo(0f)
        visibility = GONE
    }

    /** Handles back inside the overlay; false means "nothing left to go back from". */
    abstract fun back(): Boolean

    /** The arrow in the corner: steps back inside the overlay, or closes it. */
    protected fun backButton() = ThemedIcon(context, Icon.BACK).apply {
        val pad = dpi(Globals.EDGE / 2)
        setPadding(pad, pad, pad, pad)
        contentDescription = "back"
        setOnClickListener { if (!back()) close() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) = setBackgroundColor(theme.bg)
}
