package dev.bhavyam.min.theme

import dev.bhavyam.min.clock.ClockPresets
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

/**
 * Everything the home editor changes, as a handful of raw values. [Theme.derive] expands the look
 * tokens into colours, radii, spacing and typefaces; the layout tokens place the clock and the apps.
 * Lengths are in em (multiples of [scale], which is in sp).
 */
data class Tokens(
    /** App colour: hue (degrees) and vividness (OKLCH chroma). */
    val hue: Float,
    val chroma: Float,
    /** Background: 0 → deepest dark (pure black) · 1 → brightest light, jumping across the middle. */
    val surface: Float,
    /** How far the background and greys lean towards the app hue. */
    val tint: Float,
    val radius: Float,
    val padX: Float,
    val padY: Float,
    val font: Int,
    val weight: Float,
    val scale: Float,
    /** [ButtonVariant] index of the apps on home. */
    val appStyle: Int,
    val appSize: Float,
    val appAnchor: Anchor,
    /** One format per clock line (see [dev.bhavyam.min.clock.ClockFormat]); empty hides the clock. */
    val clock: List<String>,
    val clockSize: Float,
    val clockAnchor: Anchor,
) {
    val isDark get() = surface < 0.5f

    fun toJson(): JSONObject = JSONObject()
        .put("hue", hue.toDouble()).put("chroma", chroma.toDouble())
        .put("surface", surface.toDouble()).put("tint", tint.toDouble())
        .put("radius", radius.toDouble()).put("padX", padX.toDouble()).put("padY", padY.toDouble())
        .put("font", font).put("weight", weight.toDouble()).put("scale", scale.toDouble())
        .put("appStyle", appStyle).put("appSize", appSize.toDouble()).put("appAnchor", appAnchor.name)
        .put("clock", JSONArray(clock)).put("clockSize", clockSize.toDouble()).put("clockAnchor", clockAnchor.name)

    companion object {
        const val RADIUS_MAX = 2.5f
        const val PILL_THRESHOLD = 2.45f
        const val PAD_X_MIN = 0.3f
        const val PAD_X_MAX = 3f
        const val PAD_Y_MIN = 0.2f
        const val PAD_Y_MAX = 1.5f
        const val WEIGHT_MIN = 300f
        const val WEIGHT_MAX = 800f
        const val CHROMA_MAX = 0.3f
        const val TINT_MAX = 0.06f
        const val SCALE_MIN = 11f
        const val SCALE_MAX = 24f
        const val SIZE_MIN = 0.5f
        const val SIZE_MAX = 6f

        /** Missing or broken keys fall back to [base], so older saved looks keep working. */
        fun fromJson(json: JSONObject, base: Tokens): Tokens {
            fun f(key: String, d: Float) = json.optDouble(key, d.toDouble()).toFloat()
            fun anchor(key: String, d: Anchor) = Anchor.entries.firstOrNull { it.name == json.optString(key) } ?: d
            val clock = json.optJSONArray("clock")?.let { a -> List(a.length()) { a.optString(it) } }
            return Tokens(
                hue = f("hue", base.hue), chroma = f("chroma", base.chroma),
                surface = f("surface", base.surface), tint = f("tint", base.tint),
                radius = f("radius", base.radius), padX = f("padX", base.padX), padY = f("padY", base.padY),
                font = json.optInt("font", base.font).coerceIn(0, Fonts.ALL.lastIndex),
                weight = f("weight", base.weight), scale = f("scale", base.scale),
                appStyle = json.optInt("appStyle", base.appStyle).coerceIn(0, ButtonVariant.entries.lastIndex),
                appSize = f("appSize", base.appSize), appAnchor = anchor("appAnchor", base.appAnchor),
                clock = clock ?: base.clock, clockSize = f("clockSize", base.clockSize),
                clockAnchor = anchor("clockAnchor", base.clockAnchor),
            )
        }

        fun parse(raw: String, base: Tokens): Tokens = try {
            fromJson(JSONObject(raw), base)
        } catch (_: JSONException) {
            base
        }

        /** Continuous tokens blend (hue the short way round); discrete ones take [b]'s value. */
        fun lerp(a: Tokens, b: Tokens, t: Float): Tokens {
            fun mix(x: Float, y: Float) = x + (y - x) * t
            val dh = ((b.hue - a.hue) % 360f + 540f) % 360f - 180f
            return b.copy(
                hue = mod(a.hue + dh * t), chroma = mix(a.chroma, b.chroma),
                surface = mix(a.surface, b.surface), tint = mix(a.tint, b.tint),
                radius = mix(a.radius, b.radius), padX = mix(a.padX, b.padX), padY = mix(a.padY, b.padY),
                weight = mix(a.weight, b.weight), scale = mix(a.scale, b.scale),
                appSize = mix(a.appSize, b.appSize), clockSize = mix(a.clockSize, b.clockSize),
            )
        }

        /** "Surprise me": a new look and a new clock; where things sit is left alone. */
        fun random(current: Tokens, rng: Random = Random.Default): Tokens {
            fun r(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
            val dark = rng.nextFloat() < DARK_ODDS
            return current.copy(
                hue = r(0f, 360f).toInt().toFloat(), chroma = r(0.08f, 0.22f),
                surface = if (dark) r(0f, 0.4f) else r(0.6f, 1f), tint = r(0f, 0.03f),
                radius = if (rng.nextFloat() < PILL_ODDS) RADIUS_MAX else r(0f, 1.2f),
                padX = r(0.8f, 1.5f), padY = r(0.45f, 0.9f),
                font = rng.nextInt(Fonts.ALL.size), weight = r(380f, 700f).toInt().toFloat(),
                appStyle = rng.nextInt(ButtonVariant.entries.size),
                clock = ClockPresets.random(rng),
            )
        }

        fun mod(h: Float) = ((h % 360f) + 360f) % 360f

        private const val DARK_ODDS = 0.5f
        private const val PILL_ODDS = 0.2f
    }
}
