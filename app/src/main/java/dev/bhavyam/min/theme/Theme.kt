package dev.bhavyam.min.theme

import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.TypedValue
import dev.bhavyam.min.Globals

/**
 * Everything the launcher paints with, derived from [Tokens] (Token Studio's colour engine, adapted
 * so surface 0 is true black with white text, and the greys lean towards the app hue by `tint`).
 * Immutable: a token change produces a new Theme.
 */
class Theme private constructor(
    val tokens: Tokens,
    private val metrics: DisplayMetrics,
    val bg: Int,
    /** Grouped settings rows, sheets. */
    val surface: Int,
    /** Popups and other things that float. */
    val surface2: Int,
    val border: Int,
    val text: Int,
    val muted: Int,
    val primary: Role,
    val shadow: Int,
    val regular: Typeface,
    val strong: Typeface,
) {
    /** The app colour and the pairs derived from it. */
    class Role(val color: Int, val on: Int, val soft: Int, val ink: Int, val gradientStart: Int, val gradientEnd: Int)

    val dark get() = tokens.isDark
    val appStyle get() = ButtonVariant.entries[tokens.appStyle]

    /** A text size in sp for a [Globals.Type] multiplier. */
    fun sp(multiplier: Float) = tokens.scale * multiplier

    /** Pixels for [em] multiples of the scale token (follows the user's font scale). */
    fun em(em: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tokens.scale * em, metrics)

    fun dp(dp: Float) = dp * metrics.density

    val radiusButton get() = em(tokens.radius)
    val isPill get() = tokens.radius >= Tokens.PILL_THRESHOLD

    /** Cards and sheets are rounder than buttons by a fixed ratio, so one radius token shapes everything. */
    val radiusCard get() = em((tokens.radius * CARD_RADIUS_RATIO).coerceAtMost(CARD_RADIUS_MAX))

    /** Layout density from the vertical padding token (1 at the default). */
    val density get() = (tokens.padY / BASE_PAD_Y).coerceIn(DENSITY_MIN, DENSITY_MAX)

    val elevation get() = dp(Globals.Space.ELEVATION)

    /** Press highlight: the text colour at low opacity, so it works on every surface. */
    val pressed get() = withAlpha(text, Globals.Alpha.PRESSED)

    companion object {
        private const val BASE_PAD_Y = 0.66f
        private const val DENSITY_MIN = 0.55f
        private const val DENSITY_MAX = 1.7f
        private const val CARD_RADIUS_RATIO = 2f
        private const val CARD_RADIUS_MAX = 3.5f

        /** The strong weight sits this far above the body weight, within [STRONG_MIN]..[STRONG_MAX]. */
        private const val STRONG_OFFSET = 160
        private const val STRONG_MIN = 500
        private const val STRONG_MAX = 900

        /** Linear blend of two packed colours, alpha included (t = 0 → [a], 1 → [b]). */
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).toInt().coerceIn(0, 255) shl shift
            return ch(24) or ch(16) or ch(8) or ch(0)
        }

        fun withAlpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255 + 0.5f).toInt() shl 24)

        /** Surface token → background lightness, jumping across the middle (dark band | light band). */
        private fun surfaceLightness(s: Float) =
            if (s < 0.5f) s / 0.5f * 0.3f else 0.9f + (s - 0.5f) / 0.5f * 0.095f

        fun derive(t: Tokens, fonts: Fonts, metrics: DisplayMetrics): Theme {
            val dark = t.isDark
            val l = surfaceLightness(t.surface)
            val nc = t.tint
            val h = t.hue
            val c = t.chroma
            fun ok(l: Float, c: Float, h: Float) = Oklch.toArgb(l, c, h)
            val rl = if (dark) 0.72f else 0.6f
            val weight = t.weight.toInt()
            return Theme(
                tokens = t,
                metrics = metrics,
                bg = if (dark) ok(l, nc, h) else ok(l - 0.035f, nc, h),
                surface = if (dark) ok(0.17f + l * 0.3f, nc, h) else ok(l + 0.012f, nc * 0.5f, h),
                surface2 = if (dark) ok(0.27f + l * 0.3f, nc, h) else ok(l - 0.06f, nc, h),
                border = if (dark) ok(0.33f + l * 0.3f, nc, h) else ok(l - 0.12f, nc * 1.2f, h),
                text = if (dark) ok(1f - l * 0.13f, nc * 0.6f, h) else ok(0.22f, nc * 1.5f, h),
                muted = if (dark) ok(0.6f + l * 0.4f, nc, h) else ok(0.5f, nc * 1.5f, h),
                primary = Role(
                    color = ok(rl, c, h),
                    on = if (dark) ok(0.2f, c * 0.4f, h) else ok(0.99f, 0f, 0f),
                    soft = if (dark) ok(0.31f, c * 0.45f, h) else ok(0.935f, c * 0.3f, h),
                    ink = if (dark) ok(0.84f, c * 0.9f, h) else ok(0.47f, c, h),
                    gradientStart = ok(rl + 0.04f, c, h - 14f),
                    gradientEnd = ok(rl - 0.05f, c * 1.05f, h + 40f),
                ),
                shadow = if (dark) Oklch.toArgb(0.04f, 0f, 0f, 0.7f) else Oklch.toArgb(0.25f, nc + 0.02f, h, 0.32f),
                regular = fonts.typeface(t.font, weight),
                strong = fonts.typeface(t.font, (weight + STRONG_OFFSET).coerceIn(STRONG_MIN, STRONG_MAX)),
            )
        }
    }
}
