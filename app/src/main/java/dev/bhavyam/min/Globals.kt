package dev.bhavyam.min

import dev.bhavyam.min.clock.ClockPresets
import dev.bhavyam.min.theme.Anchor
import dev.bhavyam.min.theme.Tokens

/**
 * Globals — every UI variable of the launcher in one file (the Kotlin twin of a `globals.css`).
 *
 * Two kinds of values live here:
 *  - [DEFAULT_TOKENS]: the look and layout a fresh install starts with. The home editor changes
 *    them live; `Theme.derive` turns them into colours, radii, spacing and type.
 *  - fixed variables (type ramp, spacing, alphas, motion defaults) that the tokens scale.
 *
 * Views never hardcode a size, colour, alpha or timing: they read from here or from the Theme.
 */
object Globals {

    /** Pure black, white text, monochrome — the launcher as designed. */
    val DEFAULT_TOKENS = Tokens(
        hue = 268f, chroma = 0f,
        surface = 0f, tint = 0f,
        radius = 0.7f, padX = 1.1f, padY = 0.66f,
        font = 0, weight = 400f,
        scale = 16f,
        appStyle = 0, appSize = Type.FAVORITE,
        appAnchor = Anchor.BottomLeft,
        clock = ClockPresets.DEFAULT, clockSize = Type.CLOCK,
        clockAnchor = Anchor.TopLeft,
    )

    /** Text sizes, as multiples of the `scale` token (sp). */
    object Type {
        const val CLOCK = 0.875f
        const val FAVORITE = 1.375f
        const val RESULT = 1.25f
        const val INPUT = 1.625f
        const val CATEGORY = 0.625f
        const val TITLE = 1.375f
        const val SECTION = 0.6875f
        const val ROW = 1f
        const val ROW_SUMMARY = 0.8125f
        const val POPUP = 0.9375f
        const val EDITOR = 1f
        const val CHIP = 0.75f
        const val WHISPER = 0.8125f

        /** Letter spacing (em) of the tiny uppercase labels. */
        const val CAPS_TRACKING = 0.16f
    }

    /** Fixed spacing and sizes, in dp. Rows also scale with the padX / padY tokens. */
    object Space {
        const val EDGE = 24f
        /** Between the clock and the apps when they share a corner. */
        const val CLOCK_GAP = 28f
        /** Between clock lines, in em of the clock text. */
        const val CLOCK_LINE_GAP = 0.12f
        /** Between app rows that have a background, in em of the app text. */
        const val APP_ROW_GAP = 0.35f
        const val FAVORITES_BOTTOM = 40f
        const val RESULT_GAP = 0.45f // em of the result text, scaled by the density token
        const val CATEGORY_GAP = 10f
        const val INPUT_BOTTOM = 12f

        const val HEADER = 56f
        const val ICON = 22f
        const val ICON_BUTTON = 48f
        const val CONTENT_MAX_WIDTH = 640f
        const val SECTION_TOP = 28f
        const val SECTION_BOTTOM = 8f
        const val CAPTION_TOP = 12f
        const val CAPTION_BOTTOM = 6f
        const val SETTINGS_SEARCH_GAP = 8f
        const val ROW_MIN = 56f
        const val CONTROL_GAP = 16f

        const val SWITCH_WIDTH = 40f
        const val SWITCH_HEIGHT = 24f
        const val SWITCH_INSET = 3f
        const val SLIDER_TRACK = 4f
        const val SLIDER_THUMB = 18f
        const val SLIDER_HEIGHT = 36f
        const val GRIP = 40f

        const val POPUP_GAP = 6f
        const val POPUP_MIN_WIDTH = 208f
        const val POPUP_ICON_GAP = 14f

        /** Popups, sheets and the dragged-up providers. */
        const val ELEVATION = 12f

        const val TOOL = 40f
        const val TOOL_GAP = 6f
        const val TOOLTIP_OFFSET = 56f
        /** Touch band at a block's edges (inside and out) that resizes rather than recolours. */
        const val TOUCH_EDGE = 22f
        const val OUTLINE_DASH = 6f
        const val OUTLINE_PAD = 8f
        const val ANCHOR_DOT = 7f
        /** How close (dp) a lifted block must come to an anchor to snap to it. */
        const val MAGNET = 72f
        const val SHEET_PAD = 20f
    }

    /** Opacity of overlays and states. */
    object Alpha {
        const val PRESSED = 0.10f
        const val GLASS = 0.72f
        const val OUTLINE = 0.45f
        const val TOOLTIP = 0.88f
        const val SCRIM = 0.4f
        const val DISABLED = 0.38f
    }

    /** Motion defaults. Springs are (stiffness, bounce) with bounce = 1 − damping ratio. */
    object Motion {
        const val SEARCH_STIFFNESS = 420f
        const val SEARCH_BOUNCE = 0.28f
        const val COLUMNS_STIFFNESS = 520f
        const val COLUMNS_BOUNCE = 0.22f
        const val POPUP_STIFFNESS = 900f
        const val POPUP_BOUNCE = 0.35f
        const val OVERLAY_STIFFNESS = 500f
        const val OVERLAY_BOUNCE = 0.1f
        const val BLUR_STIFFNESS = 160f
        const val BLUR_BOUNCE = 0f
        const val CONTROL_STIFFNESS = 700f
        const val CONTROL_BOUNCE = 0.3f
        const val THEME_STIFFNESS = 220f
        const val THEME_BOUNCE = 0f

        const val SPEED = 1f
        const val BLUR_RADIUS = 36f // dp at full blur
        const val LAUNCH_ZOOM = 0.06f

        /** How far (fraction of the screen height) the search layer travels while opening. */
        const val SEARCH_TRAVEL = 0.38f
        /** How far (fraction of the travel) home sinks as search opens. */
        const val HOME_PARALLAX = 0.18f
        const val OVERLAY_SLIDE = 28f // dp
        const val POPUP_SCALE_FROM = 0.86f
        const val LIFT_SCALE = 1.03f
        const val COMMIT_FRACTION = 0.3f
        const val FLING_VELOCITY = 500f // dp/s that commits a swipe regardless of distance
        const val RUBBER_BAND = 0.55f
        /** How long (ms) a finger must rest on a block before it lifts to move. */
        const val LIFT_DELAY = 420L
        const val MOVE_STIFFNESS = 600f
        const val MOVE_BOUNCE = 0.25f
    }
}
