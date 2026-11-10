package dev.bhavyam.min.theme

import android.content.SharedPreferences
import android.content.res.Resources
import android.view.Choreographer
import dev.bhavyam.min.Globals
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.motion.SpringSpec

/**
 * Owns the live [Tokens] and the [Theme] derived from them. Changes are coalesced to one derive +
 * one listener pass per frame, so the home editor can push tokens on every touch event.
 */
class ThemeStore(
    private val prefs: SharedPreferences,
    private val resources: Resources,
    val fonts: Fonts,
) {
    var tokens: Tokens = load()
        private set
    var theme: Theme = derive()
        private set

    private val listeners = ArrayList<(Theme) -> Unit>()
    private var publishPending = false
    private val publish = Choreographer.FrameCallback {
        publishPending = false
        theme = derive()
        for (i in listeners.indices) listeners[i](theme)
    }

    private var morphFrom = tokens
    private var morphTo = tokens
    private val morph = Spring(MORPH_PRECISION) { p -> update(Tokens.lerp(morphFrom, morphTo, p)) }

    fun addListener(listener: (Theme) -> Unit) {
        listeners += listener
        listener(theme)
    }

    fun removeListener(listener: (Theme) -> Unit) {
        listeners -= listener
    }

    /** Live update (a drag frame): applied on the next frame, not persisted. */
    fun update(next: Tokens) {
        tokens = next
        if (!publishPending) {
            publishPending = true
            Choreographer.getInstance().postFrameCallback(publish)
        }
    }

    /** Glide every continuous token to [target] (reset, shuffle, undo) and keep it; discrete ones switch at once. */
    fun morphTo(target: Tokens, spec: SpringSpec) {
        morphFrom = tokens
        morphTo = target
        morph.snapTo(0f)
        morph.animateTo(1f, spec)
        persist(target)
    }

    /** Keep the current tokens (the end of a drag or tap). */
    fun save() = persist(tokens)

    private fun persist(t: Tokens) {
        prefs.edit().putString(KEY, t.toJson().toString()).apply()
    }

    /** Pixel values depend on display metrics: re-derive after a density or font-scale change. */
    fun refresh() = update(tokens)

    private fun derive() = Theme.derive(tokens, fonts, resources.displayMetrics)

    private fun load(): Tokens = prefs.getString(KEY, null)?.let { Tokens.parse(it, Globals.DEFAULT_TOKENS) } ?: Globals.DEFAULT_TOKENS

    private companion object {
        const val KEY = "tokens"
        const val MORPH_PRECISION = 0.001f
    }
}
