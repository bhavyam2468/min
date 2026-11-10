package dev.bhavyam.min.theme

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The handful of raw numbers Token Studio edits. [Theme.derive] expands them into every colour,
 * radius, spacing, typeface and icon style the launcher draws with. Lengths are in em
 * (multiples of [scale], which is in sp).
 */
data class Tokens(
    val hue: Float,
    val chroma: Float,
    val accentHue: Float,
    val accentChroma: Float,
    val neutralHue: Float,
    val neutralChroma: Float,
    /** 0 → deepest dark (pure black) · 1 → brightest light, with a jump across the middle. */
    val surface: Float,
    val radiusCard: Float,
    val radiusBtn: Float,
    val padX: Float,
    val padY: Float,
    val font: Int,
    val weight: Float,
    val iconStyle: Int,
    val iconStroke: Float,
    /** Button style index per tier (primary, secondary, tertiary). */
    val variants: List<Int>,
    val elevation: Float,
    val scale: Float,
) {
    val isDark get() = surface < 0.5f

    fun toJson(): JSONObject = JSONObject()
        .put("hue", hue.toDouble()).put("chroma", chroma.toDouble())
        .put("accentHue", accentHue.toDouble()).put("accentChroma", accentChroma.toDouble())
        .put("neutralHue", neutralHue.toDouble()).put("neutralChroma", neutralChroma.toDouble())
        .put("surface", surface.toDouble())
        .put("radiusCard", radiusCard.toDouble()).put("radiusBtn", radiusBtn.toDouble())
        .put("padX", padX.toDouble()).put("padY", padY.toDouble())
        .put("font", font).put("weight", weight.toDouble())
        .put("iconStyle", iconStyle).put("iconStroke", iconStroke.toDouble())
        .put("variants", JSONArray(variants))
        .put("elevation", elevation.toDouble())
        .put("scale", scale.toDouble())

    companion object {
        const val RADIUS_CARD_MAX = 3.5f
        const val RADIUS_BTN_MAX = 2.5f
        const val PILL_THRESHOLD = 2.45f
        const val PAD_X_MIN = 0.3f
        const val PAD_X_MAX = 3f
        const val PAD_Y_MIN = 0.2f
        const val PAD_Y_MAX = 1.5f
        const val WEIGHT_MIN = 300f
        const val WEIGHT_MAX = 800f
        const val STROKE_MIN = 0.75f
        const val STROKE_MAX = 3f
        const val ROLE_CHROMA_MAX = 0.3f
        const val NEUTRAL_CHROMA_MAX = 0.06f
        const val SCALE_MIN = 11f
        const val SCALE_MAX = 24f

        /** Missing keys fall back to [base], so older saved themes keep working as tokens are added. */
        fun fromJson(json: JSONObject, base: Tokens): Tokens {
            fun f(key: String, d: Float) = json.optDouble(key, d.toDouble()).toFloat()
            val v = json.optJSONArray("variants")
            return Tokens(
                hue = f("hue", base.hue), chroma = f("chroma", base.chroma),
                accentHue = f("accentHue", base.accentHue), accentChroma = f("accentChroma", base.accentChroma),
                neutralHue = f("neutralHue", base.neutralHue), neutralChroma = f("neutralChroma", base.neutralChroma),
                surface = f("surface", base.surface),
                radiusCard = f("radiusCard", base.radiusCard), radiusBtn = f("radiusBtn", base.radiusBtn),
                padX = f("padX", base.padX), padY = f("padY", base.padY),
                font = json.optInt("font", base.font).coerceIn(0, Fonts.ALL.lastIndex),
                weight = f("weight", base.weight),
                iconStyle = json.optInt("iconStyle", base.iconStyle).coerceIn(0, IconStyle.entries.lastIndex),
                iconStroke = f("iconStroke", base.iconStroke),
                variants = if (v != null && v.length() == 3) List(3) { v.getInt(it).coerceIn(0, ButtonVariant.entries.lastIndex) } else base.variants,
                elevation = f("elevation", base.elevation),
                scale = f("scale", base.scale),
            )
        }

        /** Continuous tokens blend (hues the short way round); discrete ones take [b]'s value. */
        fun lerp(a: Tokens, b: Tokens, t: Float): Tokens {
            fun mix(x: Float, y: Float) = x + (y - x) * t
            fun hue(x: Float, y: Float): Float {
                val d = ((y - x) % 360f + 540f) % 360f - 180f
                return mod(x + d * t)
            }
            return b.copy(
                hue = hue(a.hue, b.hue), chroma = mix(a.chroma, b.chroma),
                accentHue = hue(a.accentHue, b.accentHue), accentChroma = mix(a.accentChroma, b.accentChroma),
                neutralHue = hue(a.neutralHue, b.neutralHue), neutralChroma = mix(a.neutralChroma, b.neutralChroma),
                surface = mix(a.surface, b.surface),
                radiusCard = mix(a.radiusCard, b.radiusCard), radiusBtn = mix(a.radiusBtn, b.radiusBtn),
                padX = mix(a.padX, b.padX), padY = mix(a.padY, b.padY),
                weight = mix(a.weight, b.weight), iconStroke = mix(a.iconStroke, b.iconStroke),
                elevation = mix(a.elevation, b.elevation), scale = mix(a.scale, b.scale),
            )
        }

        /** Token Studio's "surprise me". */
        fun random(rng: Random = Random.Default): Tokens {
            fun r(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
            val hue = r(0f, 360f).toInt().toFloat()
            val dark = rng.nextFloat() < 0.35f
            return Tokens(
                hue = hue, chroma = r(0.1f, 0.22f),
                accentHue = mod(hue + r(120f, 220f)), accentChroma = r(0.08f, 0.2f),
                neutralHue = if (rng.nextFloat() < 0.6f) hue else r(0f, 360f), neutralChroma = r(0f, 0.03f),
                surface = if (dark) r(0.05f, 0.4f) else r(0.6f, 1f),
                radiusCard = r(0f, 2.4f), radiusBtn = if (rng.nextFloat() < 0.2f) RADIUS_BTN_MAX else r(0f, 1.2f),
                padX = r(0.8f, 1.5f), padY = r(0.45f, 0.9f),
                font = rng.nextInt(Fonts.ALL.size), weight = r(420f, 700f).toInt().toFloat(),
                iconStyle = rng.nextInt(IconStyle.entries.size), iconStroke = r(1.25f, 2.4f),
                variants = listOf(0, rng.nextInt(1, 5), rng.nextInt(2, 5)),
                elevation = r(0f, 0.9f),
                scale = 16f,
            )
        }

        fun mod(h: Float) = ((h % 360f) + 360f) % 360f
    }
}
