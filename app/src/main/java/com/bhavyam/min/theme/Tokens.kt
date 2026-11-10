package com.bhavyam.min.theme

import org.json.JSONObject
import kotlin.random.Random

/** How a role draws itself: the five button types from the token studio. */
enum class Variant(val label: String) {
    SOLID("Solid"), SOFT("Soft"), OUTLINE("Outline"), GHOST("Ghost"), GRADIENT("Gradient")
}

enum class IconStyle(val label: String) {
    OUTLINE("Outline"), DUOTONE("Duotone"), FILLED("Filled"), SHARP("Sharp")
}

/** Where the time sits relative to the date. */
enum class ClockLayout(val label: String, val stacked: Boolean, val timeFirst: Boolean) {
    DATE_TIME("date · time", false, false),
    TIME_DATE("time · date", false, true),
    DATE_OVER_TIME("date over time", true, false),
    TIME_OVER_DATE("time over date", true, true),
}

/** The three colour roles, named after what they paint in the launcher. */
enum class Role {
    HOME,       // primary  → favourites on the home screen
    SEARCH,     // accent   → app results in search
    MENU,       // neutral  → popup options
}

/**
 * Raw design tokens: the handful of numbers the user edits. [Theme.derive] turns them into
 * every colour, radius and typeface the views draw with. Immutable, so undo/redo is a list.
 */
data class Tokens(
    val hue: Float, val chroma: Float,
    val accentHue: Float, val accentChroma: Float,
    val neutralHue: Float, val neutralChroma: Float,
    val surface: Float,
    val radiusCard: Float, val radiusButton: Float,
    val padX: Float, val padY: Float,
    val font: Int, val weight: Float,
    val iconStyle: IconStyle, val iconStroke: Float,
    val home: Variant, val search: Variant, val menu: Variant,
    val elevation: Float,
    val scale: Float,
    val clockDate: String, val clockTime: String,
    val clockDateSize: Float, val clockTimeSize: Float,
    val clockLayout: ClockLayout,
) {
    val dark: Boolean get() = surface < 0.5f

    fun variant(role: Role): Variant = when (role) {
        Role.HOME -> home
        Role.SEARCH -> search
        Role.MENU -> menu
    }

    fun withVariant(role: Role, v: Variant): Tokens = when (role) {
        Role.HOME -> copy(home = v)
        Role.SEARCH -> copy(search = v)
        Role.MENU -> copy(menu = v)
    }

    fun toJson(): String = JSONObject()
        .put("hue", hue).put("chroma", chroma)
        .put("accentHue", accentHue).put("accentChroma", accentChroma)
        .put("neutralHue", neutralHue).put("neutralChroma", neutralChroma)
        .put("surface", surface)
        .put("radiusCard", radiusCard).put("radiusButton", radiusButton)
        .put("padX", padX).put("padY", padY)
        .put("font", font).put("weight", weight)
        .put("iconStyle", iconStyle.name).put("iconStroke", iconStroke)
        .put("home", home.name).put("search", search.name).put("menu", menu.name)
        .put("elevation", elevation)
        .put("scale", scale)
        .put("clockDate", clockDate).put("clockTime", clockTime)
        .put("clockDateSize", clockDateSize).put("clockTimeSize", clockTimeSize)
        .put("clockLayout", clockLayout.name)
        .toString()

    companion object {
        /** Missing or unreadable keys fall back to [base], so old saves survive new tokens. */
        fun fromJson(json: String?, base: Tokens): Tokens {
            if (json == null) return base
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return base
            fun f(k: String, d: Float) = o.optDouble(k, d.toDouble()).toFloat()
            fun s(k: String, d: String) = o.optString(k, d)
            return Tokens(
                hue = f("hue", base.hue), chroma = f("chroma", base.chroma),
                accentHue = f("accentHue", base.accentHue), accentChroma = f("accentChroma", base.accentChroma),
                neutralHue = f("neutralHue", base.neutralHue), neutralChroma = f("neutralChroma", base.neutralChroma),
                surface = f("surface", base.surface),
                radiusCard = f("radiusCard", base.radiusCard), radiusButton = f("radiusButton", base.radiusButton),
                padX = f("padX", base.padX), padY = f("padY", base.padY),
                font = o.optInt("font", base.font).coerceIn(0, Fonts.FAMILIES.lastIndex),
                weight = f("weight", base.weight),
                iconStyle = enumOr(s("iconStyle", ""), base.iconStyle),
                iconStroke = f("iconStroke", base.iconStroke),
                home = enumOr(s("home", ""), base.home),
                search = enumOr(s("search", ""), base.search),
                menu = enumOr(s("menu", ""), base.menu),
                elevation = f("elevation", base.elevation),
                scale = f("scale", base.scale),
                clockDate = s("clockDate", base.clockDate), clockTime = s("clockTime", base.clockTime),
                clockDateSize = f("clockDateSize", base.clockDateSize),
                clockTimeSize = f("clockTimeSize", base.clockTimeSize),
                clockLayout = enumOr(s("clockLayout", ""), base.clockLayout),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback

        /** "Surprise me" — a coherent random theme; the clock is left alone. */
        fun random(base: Tokens, r: Random = Random.Default): Tokens {
            fun R(a: Float, b: Float) = a + r.nextFloat() * (b - a)
            val hue = R(0f, 360f)
            val dark = r.nextFloat() < 0.6f
            return base.copy(
                hue = hue, chroma = R(0.1f, 0.22f),
                accentHue = (hue + R(120f, 220f)) % 360f, accentChroma = R(0.08f, 0.2f),
                neutralHue = if (r.nextFloat() < 0.6f) hue else R(0f, 360f), neutralChroma = R(0f, 0.03f),
                surface = if (dark) R(0f, 0.4f) else R(0.6f, 1f),
                radiusCard = R(0f, 40f), radiusButton = if (r.nextFloat() < 0.2f) Globals.RADIUS_BUTTON_MAX else R(0f, 20f),
                padX = R(10f, 24f), padY = R(4f, 14f),
                font = r.nextInt(Fonts.FAMILIES.size), weight = R(380f, 680f),
                iconStyle = IconStyle.entries.random(r), iconStroke = R(1.25f, 2.4f),
                home = Variant.entries.random(r), search = Variant.entries.random(r), menu = Variant.entries.random(r),
                elevation = R(0f, 0.9f),
            )
        }
    }
}
