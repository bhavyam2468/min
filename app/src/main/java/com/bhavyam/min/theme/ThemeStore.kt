package com.bhavyam.min.theme

import android.view.Choreographer
import com.bhavyam.min.settings.Prefs

/**
 * The live theme. Edits land in [set]; listeners are told once per frame no matter how many
 * edits a drag produces (the studio's `requestAnimationFrame` publish), and [save] persists
 * the tokens when a gesture ends.
 */
class ThemeStore(private val prefs: Prefs, private val fonts: Fonts) {

    fun interface Listener {
        fun onTheme(theme: Theme)
    }

    var tokens: Tokens = Tokens.fromJson(prefs.string(KEY), Globals.TOKENS)
        private set
    var theme: Theme = Theme.derive(tokens, fonts)
        private set

    private val listeners = ArrayList<Listener>()
    private var scheduled = false
    private val publish = Choreographer.FrameCallback {
        scheduled = false
        theme = Theme.derive(tokens, fonts)
        var i = 0
        while (i < listeners.size) listeners[i++].onTheme(theme)
    }

    fun set(next: Tokens) {
        if (next == tokens) return
        tokens = next
        if (!scheduled) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(publish)
        }
    }

    fun save() = prefs.putString(KEY, tokens.toJson())

    /** Registers and immediately applies the current theme, so a view is never unstyled. */
    fun add(listener: Listener) {
        listeners += listener
        listener.onTheme(theme)
    }

    fun remove(listener: Listener) {
        listeners -= listener
    }

    private companion object {
        const val KEY = "tokens"
    }
}
