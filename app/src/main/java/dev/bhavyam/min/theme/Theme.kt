package dev.bhavyam.min.theme

import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.TypedValue
import dev.bhavyam.min.Globals

/**
 * Everything the launcher paints with, derived from [Tokens] (Token Studio's `derive`, adapted so
 * surface 0 is true black with white text). Immutable: a token change produces a new Theme.
 */
class Theme private constructor(
    val tokens: Tokens,
    private val metrics: DisplayMetrics,
    val bg: Int,
    /** Grouped settings rows, the specimen card. */
    val surface: Int,
    /** Popups and other things that float. */
    val surface2: Int,
    val border: Int,
    val text: Int,
    val muted: Int,
    val roles: List<Role>,
    val shadow: Int,
    val regular: Typeface,
    val strong: Typeface,
) {
    /** A colour role (primary · accent · neutral) and its derived pairs. */
    class Role(val color: Int, val on: Int, val soft: Int, val ink: Int, val gradientStart: Int, val gradientEnd: Int)

    val dark get() = tokens.isDark
    val primary get() = roles[0]
    val iconStyle get() = IconStyle.entries[tokens.iconStyle]
    val iconStroke get() = tokens.iconStroke
    fun variant(tier: Int) = ButtonVariant.entries[tokens.variants[tier]]

    /** A text size in sp for a [Globals.Type] multiplier. */
    fun sp(multiplier: Float) = tokens.scale * multiplier

    /** Pixels for [em] multiples of the scale token (follows the user's font scale). */
    fun em(em: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tokens.scale * em, metrics)

    fun dp(dp: Float) = dp * metrics.density

    val radiusCard get() = em(tokens.radiusCard)
    val radiusButton get() = em(tokens.radiusBtn)
    val isPill get() = tokens.radiusBtn >= Tokens.PILL_THRESHOLD

    /** Layout density from the vertical padding token (1 at the default). */
    val density get() = (tokens.padY / BASE_PAD_Y).coerceIn(0.55f, 1.7f)

    val elevation get() = tokens.elevation * dp(Globals.Space.ELEVATION_MAX)

    /** Press highlight: the text colour at low opacity, so it works on every surface. */
    val pressed get() = withAlpha(text, Globals.Alpha.PRESSED)

    companion object {
        private const val BASE_PAD_Y = 0.66f

        /** The strong weight sits this far above the body weight, within [STRONG_MIN]..[STRONG_MAX]. */
        const val STRONG_OFFSET = 160
        const val STRONG_MIN = 500
        const val STRONG_MAX = 900

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
            val nc = t.neutralChroma
            val nh = t.neutralHue
            fun ok(l: Float, c: Float, h: Float) = Oklch.toArgb(l, c, h)

            fun role(h: Float, c: Float): Role {
                val rl = if (dark) 0.72f else 0.6f
                return Role(
                    color = ok(rl, c, h),
                    on = if (rl > 0.68f) ok(0.2f, c * 0.4f, h) else ok(0.99f, 0f, 0f),
                    soft = if (dark) ok(0.31f, c * 0.45f, h) else ok(0.935f, c * 0.3f, h),
                    ink = if (dark) ok(0.84f, c * 0.9f, h) else ok(0.47f, c, h),
                    gradientStart = ok(rl + 0.04f, c, h - 14f),
                    gradientEnd = ok(rl - 0.05f, c * 1.05f, h + 40f),
                )
            }

            val surface2 = if (dark) ok(0.27f + l * 0.3f, nc, nh) else ok(l - 0.06f, nc, nh)
            val text = if (dark) ok(1f - l * 0.13f, nc * 0.6f, nh) else ok(0.22f, nc * 1.5f, nh)
            val nl = if (dark) 0.92f else 0.27f
            val neutral = Role(
                color = ok(nl, nc * 2f, nh),
                on = if (dark) ok(0.2f, nc, nh) else ok(0.99f, nc * 0.3f, nh),
                soft = surface2,
                ink = text,
                gradientStart = ok(nl + if (dark) 0.04f else 0.1f, nc * 2f, nh - 14f),
                gradientEnd = ok(nl - if (dark) 0.12f else 0.1f, nc * 3f, nh + 40f),
            )
            val weight = t.weight.toInt()
            return Theme(
                tokens = t,
                metrics = metrics,
                bg = if (dark) ok(l, nc, nh) else ok(l - 0.035f, nc, nh),
                surface = if (dark) ok(0.17f + l * 0.3f, nc, nh) else ok(l + 0.012f, nc * 0.5f, nh),
                surface2 = surface2,
                border = if (dark) ok(0.33f + l * 0.3f, nc, nh) else ok(l - 0.12f, nc * 1.2f, nh),
                text = text,
                muted = if (dark) ok(0.6f + l * 0.4f, nc, nh) else ok(0.5f, nc * 1.5f, nh),
                roles = listOf(role(t.hue, t.chroma), role(t.accentHue, t.accentChroma), neutral),
                shadow = if (dark) Oklch.toArgb(0.04f, 0f, 0f, 0.5f + t.elevation * 0.4f)
                else Oklch.toArgb(0.25f, nc + 0.02f, nh, 0.2f + t.elevation * 0.25f),
                regular = fonts.typeface(t.font, weight),
                strong = fonts.typeface(t.font, (weight + STRONG_OFFSET).coerceIn(STRONG_MIN, STRONG_MAX)),
            )
        }
    }
}
