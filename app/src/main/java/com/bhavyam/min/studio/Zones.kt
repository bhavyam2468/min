package com.bhavyam.min.studio

import android.view.View
import com.bhavyam.min.theme.Fonts
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.IconStyle
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Tokens
import com.bhavyam.min.theme.Variant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where a finger landed on the launcher and so what it edits. [sx]/[sy] say which side of
 * [view] it is near (−1 start/top, +1 end/bottom, 0 neither); that picks the corner or edge.
 */
class Hit(val zone: Zone, val view: View, val role: Role? = null, val sx: Int = 0, val sy: Int = 0)

/**
 * The editing vocabulary of the studio, one entry per kind of spot on the real launcher.
 * Each turns a drag — finger travel in dp from where it went down, applied to the tokens as
 * they were at that moment — into new tokens, so a drag can always be walked back exactly.
 * [tap] is what a touch without travel does, or null if it does nothing.
 *
 * [LIST] and [CLOCK_SIZE] move and resize real geometry, so the studio layer drives them
 * itself (magnetic spots, fitting the clock to a box); here they only describe themselves.
 */
enum class Zone(val title: String, val hint: String) {

    CARD_RADIUS("Card corners", "Drag inward to soften, outward to sharpen") {
        override fun value(t: Tokens, hit: Hit) = dp(t.radiusCard)
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(radiusCard = (s.radiusCard + inward(dx, dy, hit) / 2).coerceIn(0f, Globals.RADIUS_CARD_MAX))
    },

    BUTTON_RADIUS("Button corners", "Drag inward — keep going for a pill") {
        override fun value(t: Tokens, hit: Hit) =
            if (t.radiusButton >= Globals.RADIUS_BUTTON_MAX - 0.5f) "pill" else dp(t.radiusButton)
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(radiusButton = (s.radiusButton + inward(dx, dy, hit) / 2).coerceIn(0f, Globals.RADIUS_BUTTON_MAX))
    },

    PAD_X("Horizontal padding", "Drag the edge to make buttons roomier") {
        override fun value(t: Tokens, hit: Hit) = dp(t.padX)
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(padX = (s.padX + hit.sx * dx / 2).coerceIn(Globals.PAD_X_MIN, Globals.PAD_X_MAX))
    },

    PAD_Y("Vertical padding", "Drag the edge — every list gets airier or tighter") {
        override fun value(t: Tokens, hit: Hit) = dp(t.padY)
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(padY = (s.padY + hit.sy * dy / 2).coerceIn(Globals.PAD_Y_MIN, Globals.PAD_Y_MAX))
    },

    ICON("Icon style", "Tap to switch style · drag for stroke weight") {
        override fun value(t: Tokens, hit: Hit) = "${t.iconStyle.label} · ${fmt(t.iconStroke, 2)}"
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(iconStroke = (s.iconStroke + (dx - dy) * 0.008f).coerceIn(Globals.STROKE_MIN, Globals.STROKE_MAX))
        override fun tap(t: Tokens, hit: Hit) = t.copy(iconStyle = next(t.iconStyle, IconStyle.entries))
    },

    FONT("Typeface", "Tap for the next family · drag ↔ for weight") {
        override fun value(t: Tokens, hit: Hit) = "${Fonts.FAMILIES[t.font].name} · ${t.weight.roundToInt()}"
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(weight = (s.weight + dx * 1.6f).coerceIn(Globals.WEIGHT_MIN, Globals.WEIGHT_MAX))
        override fun tap(t: Tokens, hit: Hit) = t.copy(font = (t.font + 1) % Fonts.FAMILIES.size)
    },

    COLOR("Colour", "Hold and glide ↔ hue · ↕ vividness · tap for the next button type") {
        override fun title(hit: Hit) = when (hit.role) {
            Role.HOME -> "Favourites · primary"
            Role.SEARCH -> "Results · secondary"
            else -> "Menu · neutral"
        }
        override fun value(t: Tokens, hit: Hit): String {
            val role = hit.role ?: Role.MENU
            val (h, _) = hueChroma(t, role)
            return "${h.roundToInt()}° · ${t.variant(role).label}"
        }
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit): Tokens {
            val role = hit.role ?: Role.MENU
            val (h, c) = hueChroma(s, role)
            val max = if (role == Role.MENU) Globals.NEUTRAL_CHROMA_MAX else Globals.CHROMA_MAX
            val hue = ((h + dx * HUE_PER_DP) % 360f + 360f) % 360f
            val chroma = (c - dy * max / CHROMA_TRAVEL).coerceIn(0f, max)
            return when (role) {
                Role.HOME -> s.copy(hue = hue, chroma = chroma)
                Role.SEARCH -> s.copy(accentHue = hue, accentChroma = chroma)
                Role.MENU -> s.copy(neutralHue = hue, neutralChroma = chroma)
            }
        }
        override fun tap(t: Tokens, hit: Hit): Tokens {
            val role = hit.role ?: Role.MENU
            return t.withVariant(role, next(t.variant(role), Variant.entries))
        }
    },

    SURFACE("Background", "Drag ↕ light ⇄ dark · ↔ shift its colour · tap for grey · pinch to scale") {
        override fun value(t: Tokens, hit: Hit): String {
            val tint = if (t.neutralChroma > 0f) "${t.neutralHue.roundToInt()}°" else "grey"
            return "${if (t.dark) "dark" else "light"} ${(t.surface * 100).roundToInt()} · $tint · ${fmt(t.scale, 2)}×"
        }
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit): Tokens {
            val surface = (s.surface - dy * SURFACE_PER_DP).coerceIn(0f, 1f)
            if (abs(dx) < TINT_DEAD_ZONE) return s.copy(surface = surface)
            // a grey background has no hue to shift: sideways travel gives it a gentle tint first
            val chroma = if (s.neutralChroma > 0f) s.neutralChroma else GENTLE_TINT
            val hue = ((s.neutralHue + dx * HUE_PER_DP) % 360f + 360f) % 360f
            return s.copy(surface = surface, neutralHue = hue, neutralChroma = chroma)
        }
        override fun tap(t: Tokens, hit: Hit) = t.copy(neutralChroma = 0f)
    },

    ELEVATION("Elevation", "Drag down to lift cards off the page") {
        override fun value(t: Tokens, hit: Hit) = "${(t.elevation * 100).roundToInt()}%"
        override fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit) =
            s.copy(elevation = (s.elevation + dy * 0.004f).coerceIn(0f, 1f))
    },

    LIST("Favourites", "Drag anywhere — it snaps to the nearest of nine spots") {
        override fun value(t: Tokens, hit: Hit) = "${VERTICAL[t.listY.ordinal]} ${HORIZONTAL[t.listX.ordinal]}"
    },

    CLOCK_SIZE("Clock", "Drag the corner — bigger shows more, smaller shows less") {
        override fun value(t: Tokens, hit: Hit) =
            "${t.clockTimeSize.roundToInt()}sp · ${listOf(t.clockDate, t.clockTime).filter { it.isNotEmpty() }.joinToString("  ")}"
    };

    open fun title(hit: Hit) = title
    abstract fun value(t: Tokens, hit: Hit): String

    /** Tokens after dragging ([dx], [dy]) dp from [s]; geometry zones leave tokens to the layer. */
    open fun drag(s: Tokens, dx: Float, dy: Float, hit: Hit): Tokens = s
    open fun tap(t: Tokens, hit: Hit): Tokens? = null

    protected fun inward(dx: Float, dy: Float, hit: Hit) = -hit.sx * dx - hit.sy * dy

    protected fun dp(v: Float) = "${v.roundToInt()}dp"

    protected fun fmt(v: Float, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

    protected fun <E> next(current: E, all: List<E>) = all[(all.indexOf(current) + 1) % all.size]

    protected fun hueChroma(t: Tokens, role: Role) = when (role) {
        Role.HOME -> t.hue to t.chroma
        Role.SEARCH -> t.accentHue to t.accentChroma
        Role.MENU -> t.neutralHue to t.neutralChroma
    }

    private companion object {
        const val HUE_PER_DP = 0.9f
        const val CHROMA_TRAVEL = 180f   // dp of vertical travel from grey to fully vivid
        const val SURFACE_PER_DP = 0.0028f
        const val TINT_DEAD_ZONE = 12f   // dp sideways before a grey background takes a tint
        const val GENTLE_TINT = 0.03f    // the chroma a grey background starts tinting from
        val VERTICAL = listOf("top", "middle", "bottom")
        val HORIZONTAL = listOf("left", "centre", "right")
    }
}
