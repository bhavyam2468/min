package dev.bhavyam.min.ui.studio

import dev.bhavyam.min.theme.ButtonVariant
import dev.bhavyam.min.theme.Fonts
import dev.bhavyam.min.theme.IconStyle
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.Tokens
import java.util.Locale
import kotlin.math.roundToInt

/** The legend chips at the bottom of the studio: one per family of zones. */
enum class Legend(val label: String, val gesture: String) {
    Corners("Corners", "drag"),
    Padding("Button edges", "drag"),
    Fill("Fill", "drag · tap"),
    Text("Text", "tap · drag"),
    Icons("Icons", "tap · drag"),
    Surface("Surface", "drag · pinch"),
    Shadow("Shadow", "drag below"),
}

enum class Corner(val top: Boolean, val left: Boolean) { TL(true, true), TR(true, false), BL(false, true), BR(false, false) }

enum class Edge { Left, Right, Top, Bottom }

/** What a touch landed on: a zone, plus which corner, edge or button tier when that matters. */
data class Hit(val zone: Zone, val corner: Corner? = null, val edge: Edge? = null, val tier: Int = 0)

/**
 * Token Studio's editing zones. Where the finger lands decides what it edits: [drag] maps a
 * displacement (dp, from where the finger went down) onto the tokens as they were at that moment,
 * [tap] makes a discrete change. Labels, values and hints feed the tooltip.
 */
enum class Zone(val legend: Legend) {
    CardRadius(Legend.Corners) {
        override fun label(hit: Hit) = "Card radius"
        override fun hint(hit: Hit) = "Drag inward to soften, outward to sharpen"
        override fun value(t: Tokens, hit: Hit) = em(t.radiusCard)
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(radiusCard = (start.radiusCard + inward(dx, dy, hit.corner!!) / 2f / start.scale).coerceIn(0f, Tokens.RADIUS_CARD_MAX))
    },
    ButtonRadius(Legend.Corners) {
        override fun label(hit: Hit) = "Button radius"
        override fun hint(hit: Hit) = "Drag inward — keep going for a pill"
        override fun value(t: Tokens, hit: Hit) = if (t.radiusBtn >= Tokens.PILL_THRESHOLD) "Pill" else em(t.radiusBtn)
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(radiusBtn = (start.radiusBtn + inward(dx, dy, hit.corner!!) / 2f / start.scale).coerceIn(0f, Tokens.RADIUS_BTN_MAX))
    },
    PadX(Legend.Padding) {
        override fun label(hit: Hit) = "Horizontal padding"
        override fun hint(hit: Hit) = "Drag the edge to make buttons roomier"
        override fun value(t: Tokens, hit: Hit) = em(t.padX)
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(padX = (start.padX + (if (hit.edge == Edge.Right) dx else -dx) / 2f / start.scale).coerceIn(Tokens.PAD_X_MIN, Tokens.PAD_X_MAX))
    },
    PadY(Legend.Padding) {
        override fun label(hit: Hit) = "Vertical padding · density"
        override fun hint(hit: Hit) = "Drag the edge — spacing everywhere follows"
        override fun value(t: Tokens, hit: Hit) = em(t.padY)
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(padY = (start.padY + (if (hit.edge == Edge.Bottom) dy else -dy) / 2f / start.scale).coerceIn(Tokens.PAD_Y_MIN, Tokens.PAD_Y_MAX))
    },
    Icon(Legend.Icons) {
        override fun label(hit: Hit) = "Icon style"
        override fun hint(hit: Hit) = "Tap to switch style · drag for stroke weight"
        override fun value(t: Tokens, hit: Hit) = "${IconStyle.entries[t.iconStyle].label} · ${"%.2f".format(Locale.ROOT, t.iconStroke)}"
        override fun tap(t: Tokens, hit: Hit) = t.copy(iconStyle = (t.iconStyle + 1) % IconStyle.entries.size)
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(iconStroke = (start.iconStroke + (dx - dy) * STROKE_PER_DP).coerceIn(Tokens.STROKE_MIN, Tokens.STROKE_MAX))
    },
    Font(Legend.Text) {
        override fun label(hit: Hit) = "Typeface"
        override fun hint(hit: Hit) = "Tap for the next family · drag for weight"
        override fun value(t: Tokens, hit: Hit) = "${Fonts.ALL[t.font].name} · ${t.weight.roundToInt()}"
        override fun tap(t: Tokens, hit: Hit) = t.copy(font = (t.font + 1) % Fonts.ALL.size)
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(weight = (start.weight + dx * WEIGHT_PER_DP).coerceIn(Tokens.WEIGHT_MIN, Tokens.WEIGHT_MAX))
    },
    Color(Legend.Fill) {
        override fun label(hit: Hit) = "${ROLE_NAMES[hit.tier]} colour"
        override fun hint(hit: Hit) = "Drag ↔ hue · ↕ vividness · tap to change ${TIER_NAMES[hit.tier]} button type"
        override fun value(t: Tokens, hit: Hit) = "${hue(t, hit.tier).roundToInt()}° · ${ButtonVariant.entries[t.variants[hit.tier]].label}"
        override fun swatch(theme: Theme, hit: Hit) = theme.roles[hit.tier].color
        override fun tap(t: Tokens, hit: Hit) =
            t.copy(variants = t.variants.mapIndexed { i, v -> if (i == hit.tier) (v + 1) % ButtonVariant.entries.size else v })
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit): Tokens {
            val h = Tokens.mod(hue(start, hit.tier) + dx * HUE_PER_DP)
            return when (hit.tier) {
                0 -> start.copy(hue = h, chroma = vivid(start.chroma, dy, Tokens.ROLE_CHROMA_MAX))
                1 -> start.copy(accentHue = h, accentChroma = vivid(start.accentChroma, dy, Tokens.ROLE_CHROMA_MAX))
                else -> start.copy(neutralHue = h, neutralChroma = vivid(start.neutralChroma, dy, Tokens.NEUTRAL_CHROMA_MAX))
            }
        }
    },
    Surface(Legend.Surface) {
        override fun label(hit: Hit) = "Surface"
        override fun hint(hit: Hit) = "Drag ↕ light ⇄ dark · ↔ tint the greys · pinch to scale everything"
        override fun value(t: Tokens, hit: Hit) =
            "${if (t.isDark) "Dark" else "Light"} ${(t.surface * 100).roundToInt()} · ${px(t.scale)}"
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) = start.copy(
            surface = (start.surface - dy * SURFACE_PER_DP).coerceIn(0f, 1f),
            neutralChroma = (start.neutralChroma + dx * TINT_PER_DP).coerceIn(0f, Tokens.NEUTRAL_CHROMA_MAX),
        )
    },
    Elevation(Legend.Shadow) {
        override fun label(hit: Hit) = "Elevation"
        override fun hint(hit: Hit) = "Drag down to lift everything off the page"
        override fun value(t: Tokens, hit: Hit) = "${(t.elevation * 100).roundToInt()}%"
        override fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit) =
            start.copy(elevation = (start.elevation + dy * ELEVATION_PER_DP).coerceIn(0f, 1f))
    };

    abstract fun label(hit: Hit): String
    abstract fun hint(hit: Hit): String
    abstract fun value(t: Tokens, hit: Hit): String
    abstract fun drag(dx: Float, dy: Float, start: Tokens, hit: Hit): Tokens

    /** A tap's discrete change, or null when tapping does nothing here. */
    open fun tap(t: Tokens, hit: Hit): Tokens? = null

    /** A colour dot shown in the tooltip (colour zones only). */
    open fun swatch(theme: Theme, hit: Hit): Int? = null

    private companion object {
        // Token Studio's pointer gains, per dp of finger travel.
        const val STROKE_PER_DP = 0.008f
        const val WEIGHT_PER_DP = 1.6f
        const val HUE_PER_DP = 0.5f
        const val CHROMA_SPAN_DP = 260f
        const val SURFACE_PER_DP = 0.0028f
        const val TINT_PER_DP = 0.00015f
        const val ELEVATION_PER_DP = 0.004f

        val ROLE_NAMES = listOf("Primary", "Accent", "Neutral")
        val TIER_NAMES = listOf("primary", "secondary", "tertiary")

        /** How far a drag moves towards a corner's centre, i.e. "rounder". */
        fun inward(dx: Float, dy: Float, c: Corner) = (if (c.left) dx else -dx) + (if (c.top) dy else -dy)

        fun vivid(start: Float, dy: Float, max: Float) = (start - dy * max / CHROMA_SPAN_DP).coerceIn(0f, max)

        fun hue(t: Tokens, tier: Int) = when (tier) {
            0 -> t.hue
            1 -> t.accentHue
            else -> t.neutralHue
        }

        fun em(v: Float) = "%.2fem".format(Locale.ROOT, v)

        fun px(v: Float) = "%.1f".format(Locale.ROOT, v).removeSuffix(".0") + "px"
    }
}
