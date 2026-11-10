package com.bhavyam.min.theme

import android.content.res.Configuration
import com.bhavyam.min.motion.SpringConfig

/**
 * globals — every UI variable in one place, the way a `:root {}` block works on the web.
 *
 * Two kinds of values live here:
 *  • [TOKENS]: the look a fresh install ships with. The token studio edits a live copy of
 *    these (colour, shape, type, icons, depth, clock); this is the starting point and what
 *    "reset" returns to.
 *  • fixed metrics: layout, type sizes and motion defaults. Views read these instead of
 *    hardcoding numbers, so changing one line here restyles the whole launcher.
 *
 * Units: sizes are dp, text is sp (multiplied by the `scale` token), hue is degrees,
 * chroma/lightness are OKLCH.
 */
object Globals {

    /* ---------------------------------------------------------------- tokens */

    /** Out of the box: pure black, white text, no fills — minimal. Everything here is editable. */
    val TOKENS = Tokens(
        hue = 268f, chroma = 0f,                  // primary  → home favourites
        accentHue = 22f, accentChroma = 0f,       // accent   → search results
        neutralHue = 268f, neutralChroma = 0f,    // neutral  → popup options, surfaces
        surface = 0f,                             // 0 pure black · 0.5 flips to light · 1 brightest
        radiusCard = 22f, radiusButton = 12f,
        padX = 14f, padY = 6f,
        font = 0, weight = 420f,
        iconStyle = IconStyle.OUTLINE, iconStroke = 1.8f,
        home = Variant.GHOST, search = Variant.GHOST, menu = Variant.GHOST,
        elevation = 0.35f,
        scale = 1f,
        clockDate = "D, d MM", clockTime = "h:ii",
        clockDateSize = 14f, clockTimeSize = 14f,
        clockLayout = ClockLayout.DATE_TIME,
        listX = Anchor.START, listY = Anchor.CENTER,   // favourites: left edge, vertically centred
    )

    /* ---------------------------------------------------------------- token ranges */

    const val RADIUS_CARD_MAX = 56f
    const val RADIUS_BUTTON_MAX = 40f          // past half the row height this reads as a pill
    const val PAD_X_MIN = 4f
    const val PAD_X_MAX = 48f
    const val PAD_Y_MIN = 2f
    const val PAD_Y_MAX = 28f
    const val WEIGHT_MIN = 300f
    const val WEIGHT_MAX = 800f
    const val STROKE_MIN = 0.75f
    const val STROKE_MAX = 3f
    const val SCALE_MIN = 0.75f
    const val SCALE_MAX = 1.5f
    const val CHROMA_MAX = 0.3f
    const val NEUTRAL_CHROMA_MAX = 0.06f
    const val CLOCK_SIZE_MIN = 9f
    const val CLOCK_SIZE_MAX = 72f

    /* ---------------------------------------------------------------- layout (dp) */

    const val EDGE = 28f                       // breathing room inside the safe area
    const val CLOCK_GAP = 8f                   // between date and time
    const val LIST_GAP = 2f                    // between rows
    const val ICON = 20f                       // icons inside buttons and rows
    const val ICON_GAP = 10f
    const val ELEVATION_MAX = 18f              // shadow depth at elevation = 1
    const val OUTLINE_WIDTH = 1.5f
    const val SETTINGS_MAX_WIDTH = 560f        // settings column on tablets / landscape
    const val POPUP_GAP = 10f                  // popup ↔ the app it belongs to
    const val SEARCH_DRAG = 220f               // finger travel for a full open/close
    const val SEARCH_TRAVEL = 64f              // how far layers slide while crossfading
    const val EDGE_SHADOW = 56f                // scroll lists: shadow depth where rows slide under an edge
    const val TOUCH_ROW = 56f                  // minimum height of a settings row

    /* ---------------------------------------------------------------- type (sp) */

    const val TEXT_FAVORITE = 26f
    const val TEXT_RESULT = 22f
    const val TEXT_INPUT = 30f
    const val TEXT_LABEL = 11f
    const val TEXT_TITLE = 22f
    const val TEXT_ROW = 17f
    const val TEXT_SUMMARY = 13f
    const val TEXT_BUTTON = 15f
    const val LABEL_TRACKING = 0.18f           // letter spacing of the tiny category label

    /** Tablets get proportionally larger type so the layout isn't a stretched phone. */
    fun screenScale(config: Configuration): Float =
        if (config.smallestScreenWidthDp >= 600) 1.2f else 1f

    /* ---------------------------------------------------------------- motion */

    val SPRING_SEARCH = SpringConfig(stiffness = 420f, damping = 0.68f)
    val SPRING_PAGE = SpringConfig(stiffness = 520f, damping = 0.78f)
    val SPRING_POPUP = SpringConfig(stiffness = 760f, damping = 0.62f)
    val SPRING_OVERLAY = SpringConfig(stiffness = 380f, damping = 0.86f)
    val SPRING_PRESS = SpringConfig(stiffness = 1100f, damping = 0.55f)
    val SPRING_LIST = SpringConfig(stiffness = 460f, damping = 0.74f)
    val SPRING_FOCUS = SpringConfig(stiffness = 140f, damping = 1f)

    const val PRESS_SCALE = 0.95f              // how far a touched row sinks
    const val BLUR_RADIUS = 28f                // dp, blur-to-focus at full strength
    const val FOCUS_SCALE = 1.04f              // the launcher zooms back in as it comes into focus

    /* ---------------------------------------------------------------- behaviour */

    const val WEB_SEARCH = "https://www.google.com/search?q="
}
