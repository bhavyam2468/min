package com.bhavyam.min.theme

import android.content.res.Resources
import android.graphics.Typeface
import android.util.TypedValue
import kotlin.math.min

/** Everything one colour role needs to paint any of the five button types. */
class RoleColors(
    val fill: Int, val on: Int,
    val soft: Int, val ink: Int,
    val gradStart: Int, val gradEnd: Int,
)

/**
 * The resolved theme: [Tokens] derived into concrete colours and typefaces. A port of the
 * token studio's `derive()` — same OKLCH maths, same roles — tuned so surface 0 is true
 * black (AMOLED) and greys stay visible on it.
 */
class Theme private constructor(
    val tokens: Tokens,
    val bg: Int, val surface: Int, val surface2: Int, val border: Int,
    val text: Int, val muted: Int, val shadow: Int,
    private val roles: Array<RoleColors>,
    val typeface: Typeface, val typefaceStrong: Typeface,
) {
    val dark: Boolean get() = tokens.dark

    fun colors(role: Role): RoleColors = roles[role.ordinal]

    fun textPx(sp: Float, res: Resources): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, res.displayMetrics) *
            tokens.scale * Globals.screenScale(res.configuration)

    companion object {
        /** Surface token → background lightness, jumping across the middle so a drag "flips". */
        private fun surfaceL(s: Float) =
            if (s < 0.5f) s / 0.5f * 0.30f else 0.9f + (s - 0.5f) / 0.5f * 0.095f

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        fun derive(t: Tokens, fonts: Fonts): Theme {
            val dark = t.dark
            val l = surfaceL(t.surface)
            val nc = t.neutralChroma
            val nh = t.neutralHue
            val ok = Oklch::color

            val bg: Int; val surface: Int; val surface2: Int; val border: Int; val text: Int; val muted: Int
            if (dark) {
                // offsets shrink as the base lifts, so greys stay distinct on black and on charcoal
                val k = 1f - l * 1.5f
                bg = ok(l, nc, nh, 1f)
                surface = ok(l + 0.17f * k, nc, nh, 1f)
                surface2 = ok(l + 0.25f * k, nc, nh, 1f)
                border = ok(l + 0.32f * k, nc, nh, 1f)
                text = ok(0.985f, nc * 0.6f, nh, 1f)
                muted = ok(0.66f, nc, nh, 1f)
            } else {
                bg = ok(l - 0.035f, nc, nh, 1f)
                surface = ok(l + 0.012f, nc * 0.5f, nh, 1f)
                surface2 = ok(l - 0.06f, nc, nh, 1f)
                border = ok(l - 0.12f, nc * 1.2f, nh, 1f)
                text = ok(0.22f, nc * 1.5f, nh, 1f)
                muted = ok(0.5f, nc * 1.5f, nh, 1f)
            }

            fun role(h: Float, c: Float): RoleColors {
                val rl = if (dark) 0.72f else 0.6f
                // with no chroma the ink is the text colour, so the default look is plain white
                val vivid = min(1f, c / 0.08f)
                return RoleColors(
                    fill = ok(rl, c, h, 1f),
                    on = if (rl > 0.68f) ok(0.2f, c * 0.4f, h, 1f) else ok(0.99f, 0f, 0f, 1f),
                    soft = if (dark) ok(0.31f, c * 0.45f, h, 1f) else ok(0.935f, c * 0.3f, h, 1f),
                    ink = if (dark) ok(lerp(0.985f, 0.84f, vivid), c * 0.9f, h, 1f)
                    else ok(lerp(0.22f, 0.47f, vivid), c, h, 1f),
                    gradStart = ok(rl + 0.04f, c, h - 14f, 1f),
                    gradEnd = ok(rl - 0.05f, c * 1.05f, h + 40f, 1f),
                )
            }

            val nl = if (dark) 0.92f else 0.27f
            val neutral = RoleColors(
                fill = ok(nl, nc * 2f, nh, 1f),
                on = if (dark) ok(0.2f, nc, nh, 1f) else ok(0.99f, nc * 0.3f, nh, 1f),
                soft = surface2,
                ink = text,
                gradStart = ok(nl + if (dark) 0.04f else 0.1f, nc * 2f, nh - 14f, 1f),
                gradEnd = ok(nl - if (dark) 0.12f else 0.1f, nc * 3f, nh + 40f, 1f),
            )

            val strong = (t.weight + 160f).coerceIn(500f, 900f)
            return Theme(
                tokens = t,
                bg = bg, surface = surface, surface2 = surface2, border = border,
                text = text, muted = muted,
                shadow = if (dark) ok(0.04f, 0f, 0f, 1f) else ok(0.25f, nc + 0.02f, nh, 1f),
                roles = arrayOf(role(t.hue, t.chroma), role(t.accentHue, t.accentChroma), neutral),
                typeface = fonts.get(t.font, t.weight),
                typefaceStrong = fonts.get(t.font, strong),
            )
        }
    }
}
