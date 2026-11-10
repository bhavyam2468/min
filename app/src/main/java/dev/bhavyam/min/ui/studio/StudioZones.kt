package dev.bhavyam.min.ui.studio

import android.graphics.RectF
import dev.bhavyam.min.clock.ClockPresets
import dev.bhavyam.min.theme.Anchor
import dev.bhavyam.min.theme.ButtonVariant
import dev.bhavyam.min.theme.Fonts
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.Tokens
import dev.bhavyam.min.ui.home.Block
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** The legend chips at the bottom of the editor: one per family of zones. */
enum class Legend(val label: String, val gesture: String) {
    Background("Background", "glide · pinch"),
    Apps("Apps", "tap · glide · pinch"),
    Edges("Edges", "drag"),
    Clock("Clock", "tap · double-tap"),
    Move("Move", "hold"),
}

/** Where on a block a touch landed; [sx] / [sy] say which side (−1 left/top · 1 right/bottom · 0 neither). */
enum class Part(val sx: Int, val sy: Int) {
    Body(0, 0), Left(-1, 0), Right(1, 0), Top(0, -1), Bottom(0, 1),
    TopLeft(-1, -1), TopRight(1, -1), BottomLeft(-1, 1), BottomRight(1, 1);

    val isCorner get() = sx != 0 && sy != 0

    companion object {
        /**
         * The part of [r] at ([x], [y]), or null if the point is further than [outside] from it.
         * Edge bands reach [outside] beyond the edge and up to [inside] in (less on small blocks,
         * so there is always a body to touch).
         */
        fun at(r: RectF, x: Float, y: Float, outside: Float, inside: Float): Part? {
            if (r.isEmpty || x < r.left - outside || x > r.right + outside || y < r.top - outside || y > r.bottom + outside) return null
            val inX = min(inside, r.width() / BODY_SHARE)
            val inY = min(inside, r.height() / BODY_SHARE)
            val sx = if (x < r.left + inX) -1 else if (x > r.right - inX) 1 else 0
            val sy = if (y < r.top + inY) -1 else if (y > r.bottom - inY) 1 else 0
            return entries.first { it.sx == sx && it.sy == sy }
        }

        private const val BODY_SHARE = 4f
    }
}

/** A drag in progress, in dp from where the finger went down, and the touched block's size then (dp). */
class Drag {
    var dx = 0f
    var dy = 0f
    var width = 0f
    var height = 0f
}

/** What a touch is editing: a zone, the block and part it landed on, and (while moving) the anchor in reach. */
data class Hit(val zone: Zone, val block: Block? = null, val part: Part = Part.Body, val anchor: Anchor? = null)

/**
 * The home editor's zones — Token Studio's idea, mapped onto home itself. Where the finger lands
 * decides what it edits: [drag] maps a displacement onto the tokens as they were when the finger
 * went down, [tap] and [pinch] make their own changes. Labels, values and hints feed the tooltip.
 */
enum class Zone(val legend: Legend) {
    Background(Legend.Background) {
        override fun label(hit: Hit) = "Background"
        override fun hint(hit: Hit) = "Glide ↕ dark ⇄ light · ↔ tint · pinch to scale everything"
        override fun value(t: Tokens, hit: Hit) =
            "${if (t.isDark) "Dark" else "Light"} ${(t.surface * 100).roundToInt()} · tint ${(t.tint / Tokens.TINT_MAX * 100).roundToInt()}"
        override fun drag(g: Drag, start: Tokens, hit: Hit) = start.copy(
            surface = (start.surface - g.dy * SURFACE_PER_DP).coerceIn(0f, 1f),
            tint = (start.tint + g.dx * TINT_PER_DP).coerceIn(0f, Tokens.TINT_MAX),
        )
    },
    Scale(Legend.Background) {
        override fun label(hit: Hit) = "Scale"
        override fun hint(hit: Hit) = "Pinch to scale everything"
        override fun value(t: Tokens, hit: Hit) = "%.1f".format(Locale.ROOT, t.scale).removeSuffix(".0") + " sp"
        override fun drag(g: Drag, start: Tokens, hit: Hit) = start
        override fun pinch(start: Tokens, ratio: Float) = start.copy(scale = (start.scale * ratio).coerceIn(Tokens.SCALE_MIN, Tokens.SCALE_MAX))
    },
    AppColor(Legend.Apps) {
        override fun label(hit: Hit) = "App colour"
        override fun hint(hit: Hit) = "Glide ↔ hue · ↕ vividness · tap for the next style · hold to move"
        override fun value(t: Tokens, hit: Hit) = "${t.hue.roundToInt()}° · ${ButtonVariant.entries[t.appStyle].label}"
        override fun swatch(theme: Theme) = theme.primary.color
        override fun tap(t: Tokens) = t.copy(appStyle = (t.appStyle + 1) % ButtonVariant.entries.size)
        override fun drag(g: Drag, start: Tokens, hit: Hit) = start.copy(
            hue = Tokens.mod(start.hue + g.dx * HUE_PER_DP),
            chroma = (start.chroma - g.dy * Tokens.CHROMA_MAX / CHROMA_SPAN_DP).coerceIn(0f, Tokens.CHROMA_MAX),
        )
    },
    AppSize(Legend.Apps) {
        override fun label(hit: Hit) = "App size"
        override fun hint(hit: Hit) = "Pinch the apps to resize them"
        override fun value(t: Tokens, hit: Hit) = em(t.appSize)
        override fun drag(g: Drag, start: Tokens, hit: Hit) = start
        override fun pinch(start: Tokens, ratio: Float) = start.copy(appSize = size(start.appSize * ratio))
    },
    AppPadX(Legend.Edges) {
        override fun label(hit: Hit) = "Horizontal padding"
        override fun hint(hit: Hit) = "Drag the side to make the apps roomier"
        override fun value(t: Tokens, hit: Hit) = em(t.padX)
        override fun drag(g: Drag, start: Tokens, hit: Hit) =
            start.copy(padX = (start.padX + hit.part.sx * g.dx / 2f / start.scale).coerceIn(Tokens.PAD_X_MIN, Tokens.PAD_X_MAX))
    },
    AppPadY(Legend.Edges) {
        override fun label(hit: Hit) = "Vertical padding"
        override fun hint(hit: Hit) = "Drag the top or bottom to spread the rows out or pack them in"
        override fun value(t: Tokens, hit: Hit) = em(t.padY)
        override fun drag(g: Drag, start: Tokens, hit: Hit) =
            start.copy(padY = (start.padY + hit.part.sy * g.dy / 2f / start.scale).coerceIn(Tokens.PAD_Y_MIN, Tokens.PAD_Y_MAX))
    },
    AppRadius(Legend.Edges) {
        override fun label(hit: Hit) = "Corners"
        override fun hint(hit: Hit) = "Drag inward to soften — keep going for a pill"
        override fun value(t: Tokens, hit: Hit) = if (t.radius >= Tokens.PILL_THRESHOLD) "Pill" else em(t.radius)
        override fun drag(g: Drag, start: Tokens, hit: Hit) =
            start.copy(radius = (start.radius - outward(g, hit.part) / 2f / start.scale).coerceIn(0f, Tokens.RADIUS_MAX))
    },
    Typeface(Legend.Clock) {
        override fun label(hit: Hit) = "Typeface"
        override fun hint(hit: Hit) = "Tap for the next family · glide ↔ for weight · double-tap to write the format · hold to move"
        override fun value(t: Tokens, hit: Hit) = "${Fonts.ALL[t.font].name} · ${t.weight.roundToInt()}"
        override fun tap(t: Tokens) = t.copy(font = (t.font + 1) % Fonts.ALL.size)
        override fun drag(g: Drag, start: Tokens, hit: Hit) =
            start.copy(weight = (start.weight + g.dx * WEIGHT_PER_DP).coerceIn(Tokens.WEIGHT_MIN, Tokens.WEIGHT_MAX))
    },
    ClockDetail(Legend.Edges) {
        override fun label(hit: Hit) = "Clock detail"
        override fun hint(hit: Hit) = "Drag the side out for more, in for less"
        override fun value(t: Tokens, hit: Hit) = format(t)
        override fun drag(g: Drag, start: Tokens, hit: Hit): Tokens {
            val (detail, lines) = ClockPresets.shape(start.clock)
            val steps = (hit.part.sx * g.dx / DETAIL_STEP_DP).roundToInt()
            return start.copy(clock = ClockPresets.preset((detail + steps).coerceIn(0, ClockPresets.DETAIL_MAX), lines))
        }
    },
    ClockLines(Legend.Edges) {
        override fun label(hit: Hit) = "Clock lines"
        override fun hint(hit: Hit) = "Drag the top or bottom to split the clock over more lines"
        override fun value(t: Tokens, hit: Hit) = format(t)
        override fun drag(g: Drag, start: Tokens, hit: Hit): Tokens {
            val (detail, lines) = ClockPresets.shape(start.clock)
            val steps = (hit.part.sy * g.dy / LINE_STEP_DP).roundToInt()
            return start.copy(clock = ClockPresets.preset(detail, (lines + steps).coerceIn(1, ClockPresets.LINES_MAX)))
        }
    },
    ClockSize(Legend.Edges) {
        override fun label(hit: Hit) = "Clock size"
        override fun hint(hit: Hit) = "Drag a corner out to grow, in to shrink · or pinch"
        override fun value(t: Tokens, hit: Hit) = em(t.clockSize)
        override fun drag(g: Drag, start: Tokens, hit: Hit) =
            start.copy(clockSize = size(start.clockSize * (1f + outward(g, hit.part) / (g.width + g.height).coerceAtLeast(1f))))
        override fun pinch(start: Tokens, ratio: Float) = start.copy(clockSize = size(start.clockSize * ratio))
    },
    Move(Legend.Move) {
        override fun label(hit: Hit) = "Move"
        override fun hint(hit: Hit) = "Drag anywhere — it snaps to the nearest spot"
        override fun value(t: Tokens, hit: Hit) = hit.anchor?.label.orEmpty()
        override fun drag(g: Drag, start: Tokens, hit: Hit) = start
    };

    abstract fun label(hit: Hit): String
    abstract fun hint(hit: Hit): String
    abstract fun value(t: Tokens, hit: Hit): String
    abstract fun drag(g: Drag, start: Tokens, hit: Hit): Tokens

    /** A tap's discrete change, or null when tapping does nothing here. */
    open fun tap(t: Tokens): Tokens? = null

    /** Two fingers spreading by [ratio] since they landed, or null when pinching does nothing here. */
    open fun pinch(start: Tokens, ratio: Float): Tokens? = null

    /** A colour dot shown in the tooltip. */
    open fun swatch(theme: Theme): Int? = null

    companion object {
        /** What two fingers resize: the clock, the apps, or (on the background) everything. */
        fun pinchOf(block: Block?) = when (block) {
            null -> Scale
            Block.Apps -> AppSize
            Block.Clock -> ClockSize
        }

        /** The zone for a touch on [part] of [block] (or on the background, when [block] is null). */
        fun of(block: Block?, part: Part) = when (block) {
            null -> Background
            Block.Apps -> when {
                part == Part.Body -> AppColor
                part.isCorner -> AppRadius
                part.sx != 0 -> AppPadX
                else -> AppPadY
            }
            Block.Clock -> when {
                part == Part.Body -> Typeface
                part.isCorner -> ClockSize
                part.sx != 0 -> ClockDetail
                else -> ClockLines
            }
        }

        // Token Studio's pointer gains, per dp of finger travel.
        private const val WEIGHT_PER_DP = 1.6f
        private const val HUE_PER_DP = 0.5f
        private const val CHROMA_SPAN_DP = 260f
        private const val SURFACE_PER_DP = 0.0028f
        private const val TINT_PER_DP = 0.00015f

        /** Finger travel per step of clock detail / per extra line. */
        private const val DETAIL_STEP_DP = 32f
        private const val LINE_STEP_DP = 40f

        /** How far a drag moves away from the block's centre, through the touched side or corner. */
        private fun outward(g: Drag, part: Part) = part.sx * g.dx + part.sy * g.dy

        private fun size(v: Float) = v.coerceIn(Tokens.SIZE_MIN, Tokens.SIZE_MAX)

        private fun format(t: Tokens) = t.clock.joinToString(" / ").ifEmpty { "—" }

        private fun em(v: Float) = "%.2fem".format(Locale.ROOT, v)
    }
}
