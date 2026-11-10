package dev.bhavyam.min.ui.studio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.IconDrawable
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.motion.SpringSpec
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Fonts
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.ButtonDrawable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Token Studio's specimen card, drawn on one canvas: "Aa", the type meta, and the three buttons.
 * It lays itself out in theme units (so every token shows up exactly as it would anywhere else),
 * scales to fit the stage, answers "what is under this finger?" ([hitAt]) and draws the gliding
 * highlight around the zone being edited.
 */
@SuppressLint("ViewConstructor")
class StudioCard(context: Context, private val settings: SettingsStore) : View(context) {

    private class Button(val tier: Int, val label: String, icon: Icons.Icon, val iconFirst: Boolean) {
        val background = ButtonDrawable(tier)
        val icon = IconDrawable(icon)
        val rect = RectF()
        val iconRect = RectF()
        val labelRect = RectF()
        var labelWidth = 0f
    }

    private val buttons = listOf(
        Button(0, "Get started", Icons.sparkle, iconFirst = true),
        Button(1, "Favourite", Icons.heart, iconFirst = true),
        Button(2, "Settings", Icons.arrow, iconFirst = false),
    )

    private var theme: Theme? = null
    private var wasDark: Boolean? = null
    private var lastFont = -1
    private var lastIconStyle = -1
    private var lastVariants: List<Int> = emptyList()

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val aaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { letterSpacing = AA_TRACKING }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = DashPathEffect(floatArrayOf(DASH, DASH), 0f)

    // Layout, in card units (theme pixels before the fit scale).
    private val card = RectF()
    private val aaRect = RectF()
    private val metaRect = RectF()
    private var aaBaseline = 0f
    private var metaBaseline1 = 0f
    private var metaBaseline2 = 0f
    private var fontName = ""
    private var fontMeta = ""
    private var labelBaseline = 0f

    // Card units → view pixels.
    private var fit = 1f
    private var originX = 0f
    private var originY = 0f

    private var flip = 1f
    private var swap = 0f
    private val iconPop = FloatArray(3) { 1f }
    private val buttonPop = FloatArray(3) { 1f }
    private val flipSpring = Spring(PRECISION) { flip = it; invalidate() }.also { it.snapTo(1f) }
    private val swapSpring = Spring(PRECISION) { swap = it; invalidate() }
    private val iconSprings = List(3) { i -> Spring(PRECISION) { iconPop[i] = it; invalidate() }.also { it.snapTo(1f) } }
    private val buttonSprings = List(3) { i -> Spring(PRECISION) { buttonPop[i] = it; invalidate() }.also { it.snapTo(1f) } }

    /** The highlight glides between targets: rectangle + radius + opacity, all on springs. */
    private enum class Kind { Ring, SolidRing, Dot, Bar }
    private var kind = Kind.Ring
    private val hl = FloatArray(5) // x, y, w, h, r
    private var hlAlpha = 0f
    private val hlSprings = List(5) { i -> Spring(HIGHLIGHT_PRECISION) { hl[i] = it; invalidate() } }
    private val hlAlphaSpring = Spring(PRECISION) { hlAlpha = it; invalidate() }
    private var hlShown = false
    private val target = RectF()

    fun applyTheme(theme: Theme) {
        val old = this.theme
        this.theme = theme
        val t = theme.tokens
        if (wasDark != null && wasDark != t.isDark) flipSpring.animateTo(1f, bouncy(), -FLIP_IMPULSE)
        wasDark = t.isDark
        if (old != null && lastFont != t.font) {
            swapSpring.snapTo(1f)
            swapSpring.animateTo(0f, settings.spring(SettingsRegistry.THEME_MOTION))
        }
        lastFont = t.font
        if (old != null && lastIconStyle != t.iconStyle) iconSprings.forEach { pop(it, ICON_POP) }
        lastIconStyle = t.iconStyle
        if (lastVariants.isNotEmpty()) for (i in 0..2) if (lastVariants[i] != t.variants[i]) pop(buttonSprings[i], BUTTON_POP)
        lastVariants = t.variants

        cardPaint.color = theme.surface
        ringPaint.color = Theme.mix(theme.border, 0, 1f - BORDER_OPACITY)
        ringPaint.strokeWidth = theme.dp(1f)
        aaPaint.color = theme.text
        aaPaint.typeface = theme.strong
        metaPaint.typeface = theme.regular
        labelPaint.typeface = theme.regular
        guidePaint.color = Theme.withAlpha(theme.text, GUIDE_ALPHA)
        fontName = Fonts.ALL[t.font].name
        fontMeta = "${t.weight.roundToInt()} · ${"%.1f".format(Locale.ROOT, t.scale).removeSuffix(".0")}px"
        val e = t.elevation
        if (e > 0f) cardPaint.setShadowLayer(theme.em(SHADOW_BLUR * e + SHADOW_BLUR_BASE), 0f, theme.em(SHADOW_Y * e + SHADOW_Y_BASE), theme.shadow)
        else cardPaint.clearShadowLayer()
        for (b in buttons) {
            b.background.style(theme, if (theme.isPill) Float.MAX_VALUE else theme.radiusButton)
            b.icon.style(theme, b.background.foreground)
        }
        layoutCard(theme)
        invalidate()
    }

    private fun bouncy() = settings.spring(SettingsRegistry.CONTROL_MOTION)

    /** 'pop': a spring kicked inwards that overshoots back to rest. */
    private fun pop(spring: Spring, impulse: Float) {
        spring.snapTo(1f)
        spring.animateTo(1f, bouncy(), -impulse)
    }

    private fun layoutCard(theme: Theme) {
        val t = theme.tokens
        val fs = theme.em(1f)
        val density = theme.density
        val padV = CARD_PAD_V * fs * density
        val padH = CARD_PAD_H * fs * density
        val gap = CARD_GAP * fs * density

        aaPaint.textSize = AA_SIZE * fs
        val aaW = aaPaint.measureText(AA)
        val aaH = AA_SIZE * fs * AA_LINE
        metaPaint.textSize = META_SIZE * fs
        val metaW = max(metaPaint.measureText(fontName), metaPaint.measureText(fontMeta))
        val metaLine = META_SIZE * fs * META_LINE
        val headH = max(aaH, metaLine * 2)

        labelPaint.textSize = fs
        val iconSize = ICON_SIZE * fs
        val lineH = max(iconSize, LABEL_LINE * fs)
        val border = theme.dp(BUTTON_BORDER)
        val btnH = 2 * t.padY * fs + lineH + 2 * border
        var rowW = 0f
        for (b in buttons) {
            b.labelWidth = labelPaint.measureText(b.label)
            rowW += 2 * t.padX * fs + 2 * border + iconSize + ICON_GAP * fs + b.labelWidth
        }
        rowW += BUTTON_GAP * fs * (buttons.size - 1)

        val innerW = max(aaW + HEAD_GAP * fs + metaW, rowW)
        card.set(0f, 0f, innerW + 2 * padH, padV * 2 + headH + gap + btnH)

        aaRect.set(padH, padV + headH - aaH, padH + aaW, padV + headH)
        val fm = aaPaint.fontMetrics
        aaBaseline = aaRect.bottom - (aaH - (fm.descent - fm.ascent)) / 2f - fm.descent
        metaRect.set(card.right - padH - metaW, padV + headH - metaLine * 2, card.right - padH, padV + headH)
        val mm = metaPaint.fontMetrics
        val metaInset = (metaLine - (mm.descent - mm.ascent)) / 2f - mm.ascent
        metaBaseline1 = metaRect.top + metaInset
        metaBaseline2 = metaRect.top + metaLine + metaInset

        var x = padH
        val top = padV + headH + gap
        val lm = labelPaint.fontMetrics
        labelBaseline = (lineH - (lm.descent - lm.ascent)) / 2f - lm.ascent
        for (b in buttons) {
            val w = 2 * t.padX * fs + 2 * border + iconSize + ICON_GAP * fs + b.labelWidth
            b.rect.set(x, top, x + w, top + btnH)
            val cy = top + btnH / 2f
            val contentX = x + border + t.padX * fs
            if (b.iconFirst) {
                b.iconRect.set(contentX, cy - iconSize / 2f, contentX + iconSize, cy + iconSize / 2f)
                b.labelRect.set(b.iconRect.right + ICON_GAP * fs, cy - lineH / 2f, b.iconRect.right + ICON_GAP * fs + b.labelWidth, cy + lineH / 2f)
            } else {
                b.labelRect.set(contentX, cy - lineH / 2f, contentX + b.labelWidth, cy + lineH / 2f)
                b.iconRect.set(b.labelRect.right + ICON_GAP * fs, cy - iconSize / 2f, b.labelRect.right + ICON_GAP * fs + iconSize, cy + iconSize / 2f)
            }
            x += w + BUTTON_GAP * fs
        }
        place()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = place()

    /** Centre the card and shrink it to fit (it never grows past 1:1 — tokens decide its size). */
    private fun place() {
        val theme = theme ?: return
        if (width == 0 || card.width() <= 0f) return
        val edge = theme.dp(Globals.Space.EDGE)
        val guide = guideSpace(theme)
        fit = min(1f, min((width - 2 * edge) / card.width(), (height - 2 * guide) / card.height()))
        originX = (width - card.width() * fit) / 2f
        originY = (height - card.height() * fit) / 2f - theme.tokens.elevation * LIFT * theme.em(1f) * fit
    }

    private fun guideSpace(theme: Theme) = max(theme.dp(Globals.Space.ICON_BUTTON), GUIDE_HEIGHT * theme.em(1f))

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val theme = theme ?: return
        val fs = theme.em(1f)
        val save = canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(fit, fit)

        // elevation guide: a short bar below the card that sinks as the card lifts
        val gw = GUIDE_WIDTH * fs
        val gy = card.bottom + GUIDE_TOP * fs + theme.tokens.elevation * GUIDE_TRAVEL * fs
        val gh = theme.dp(GUIDE_THICKNESS)
        canvas.drawRoundRect(card.centerX() - gw / 2, gy, card.centerX() + gw / 2, gy + gh, gh, gh, guidePaint)

        val cardSave = canvas.save()
        canvas.scale(flip, flip, card.centerX(), card.centerY())
        val r = theme.radiusCard.coerceAtMost(card.height() / 2f)
        canvas.drawRoundRect(card, r, r, cardPaint)
        canvas.drawRoundRect(card, r, r, ringPaint)

        // 'swap': the type fades up into place after a family change
        val textAlpha = ((1f - SWAP_FADE * swap) * 255).toInt().coerceIn(0, 255)
        val shift = SWAP_SHIFT * fs * swap
        aaPaint.alpha = textAlpha
        canvas.drawText(AA, aaRect.left, aaBaseline + shift, aaPaint)
        metaPaint.textAlign = Paint.Align.RIGHT
        metaPaint.color = theme.text
        metaPaint.alpha = textAlpha
        canvas.drawText(fontName, metaRect.right, metaBaseline1 + shift, metaPaint)
        metaPaint.color = theme.muted
        canvas.drawText(fontMeta, metaRect.right, metaBaseline2, metaPaint)

        for (b in buttons) drawButton(canvas, b, textAlpha, shift)
        canvas.restoreToCount(cardSave)
        canvas.restoreToCount(save)
        drawHighlight(canvas, theme)
    }

    private fun drawButton(canvas: Canvas, b: Button, textAlpha: Int, shift: Float) {
        val s = canvas.save()
        val pop = buttonPop[b.tier]
        canvas.scale(pop, pop, b.rect.centerX(), b.rect.centerY())
        b.background.setBounds(b.rect.left.toInt(), b.rect.top.toInt(), b.rect.right.roundToInt(), b.rect.bottom.roundToInt())
        b.background.draw(canvas)
        val i = canvas.save()
        val ip = iconPop[b.tier]
        canvas.scale(ip, ip, b.iconRect.centerX(), b.iconRect.centerY())
        b.icon.setBounds(b.iconRect.left.toInt(), b.iconRect.top.toInt(), b.iconRect.right.roundToInt(), b.iconRect.bottom.roundToInt())
        b.icon.draw(canvas)
        canvas.restoreToCount(i)
        labelPaint.color = b.background.foreground
        labelPaint.alpha = textAlpha
        canvas.drawText(b.label, b.labelRect.left, b.labelRect.top + labelBaseline + shift, labelPaint)
        canvas.restoreToCount(s)
    }

    private fun drawHighlight(canvas: Canvas, theme: Theme) {
        if (hlAlpha <= 0f) return
        val a = hlAlpha.coerceIn(0f, 1f)
        val (x, y, w, h) = hl
        val r = hl[4]
        when (kind) {
            Kind.Ring, Kind.SolidRing -> {
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = theme.dp(RING_WIDTH)
                highlightPaint.pathEffect = if (kind == Kind.Ring) dash else null
                highlightPaint.color = Theme.withAlpha(theme.text, RING_ALPHA * a)
                canvas.drawRoundRect(x, y, x + w, y + h, r, r, highlightPaint)
                highlightPaint.pathEffect = null
            }
            Kind.Dot, Kind.Bar -> {
                highlightPaint.style = Paint.Style.FILL
                val halo = theme.dp(HALO)
                highlightPaint.color = Theme.withAlpha(theme.text, HALO_ALPHA * a)
                canvas.drawRoundRect(x - 2 * halo, y - 2 * halo, x + w + 2 * halo, y + h + 2 * halo, r + 2 * halo, r + 2 * halo, highlightPaint)
                highlightPaint.color = Theme.withAlpha(theme.surface, a)
                canvas.drawRoundRect(x - halo, y - halo, x + w + halo, y + h + halo, r + halo, r + halo, highlightPaint)
                highlightPaint.color = Theme.withAlpha(theme.text, a)
                canvas.drawRoundRect(x, y, x + w, y + h, r, r, highlightPaint)
            }
        }
    }

    // ------------------------------------------------------------------ hit testing

    /** The zone under a point in view pixels, or null off the card. */
    fun hitAt(px: Float, py: Float): Hit? {
        val theme = theme ?: return null
        val u = (px - originX) / fit
        val v = (py - originY) / fit
        val fs = theme.em(1f)

        // corners first: they reach a little outside the card
        val handle = theme.dp(Globals.Space.CORNER_HANDLE) / fit
        for (c in Corner.entries) {
            val cx = if (c.left) card.left + handle * CORNER_INSET else card.right - handle * CORNER_INSET
            val cy = if (c.top) card.top + handle * CORNER_INSET else card.bottom - handle * CORNER_INSET
            if (abs(u - cx) < handle / 2 && abs(v - cy) < handle / 2) return Hit(Zone.CardRadius, corner = c)
        }
        val guide = guideSpace(theme) / fit
        if (u in card.width() * GUIDE_ZONE..card.width() * (1 - GUIDE_ZONE) && v in card.bottom..card.bottom + guide) return Hit(Zone.Elevation)
        if (!card.contains(u, v)) return null

        for (b in buttons) {
            if (!b.rect.contains(u, v)) continue
            val m = max(theme.dp(Globals.Space.TOUCH_EDGE) / fit, BUTTON_EDGE * fs)
            val l = u < b.rect.left + m
            val r = u > b.rect.right - m
            val t = v < b.rect.top + m
            val bm = v > b.rect.bottom - m
            if ((l || r) && (t || bm)) return Hit(Zone.ButtonRadius, corner = cornerOf(t, l), tier = b.tier)
            if (l || r) return Hit(Zone.PadX, edge = if (l) Edge.Left else Edge.Right, tier = b.tier)
            if (t || bm) return Hit(Zone.PadY, edge = if (t) Edge.Top else Edge.Bottom, tier = b.tier)
            if (b.iconRect.contains(u, v) || near(b.iconRect, u, v, ICON_SLOP * fs)) return Hit(Zone.Icon, tier = b.tier)
            if (b.labelRect.contains(u, v)) return Hit(Zone.Font, tier = b.tier)
            return Hit(Zone.Color, tier = b.tier)
        }
        if (aaRect.contains(u, v) || metaRect.contains(u, v)) return Hit(Zone.Font, tier = -1)
        return Hit(Zone.Surface)
    }

    private fun near(r: RectF, u: Float, v: Float, slop: Float) =
        u in r.left - slop..r.right + slop && v in r.top - slop..r.bottom + slop

    private fun cornerOf(top: Boolean, left: Boolean) = Corner.entries.first { it.top == top && it.left == left }

    // ------------------------------------------------------------------ highlight

    /** Glide the highlight onto [hit] (or fade it out for null). [dragging] makes it follow tightly. */
    fun highlight(hit: Hit?, dragging: Boolean) {
        val theme = theme ?: return
        if (hit == null) {
            hlShown = false
            hlAlphaSpring.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
            return
        }
        val r = geometry(hit, theme)
        val spec = if (dragging) SpringSpec(TRACK_STIFFNESS, 1f) else settings.spring(SettingsRegistry.CONTROL_MOTION)
        val values = floatArrayOf(target.left, target.top, target.width(), target.height(), r)
        for (i in 0..4) if (hlShown) hlSprings[i].animateTo(values[i], spec) else hlSprings[i].snapTo(values[i])
        hlShown = true
        hlAlphaSpring.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
    }

    /** Fills [target] (view pixels) for [hit], sets [kind], returns the corner radius. */
    private fun geometry(hit: Hit, theme: Theme): Float {
        val fs = theme.em(1f)
        val button = buttons.getOrNull(hit.tier)
        fun box(r: RectF, pad: Float, radius: Float): Float {
            target.set(sx(r.left) - pad, sy(r.top) - pad, sx(r.right) + pad, sy(r.bottom) + pad)
            return radius * fit + pad
        }
        fun bar(cx: Float, cy: Float, w: Float, h: Float): Float {
            target.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            kind = Kind.Bar
            return min(w, h) / 2
        }
        val btnRadius = if (button == null) 0f else min(theme.radiusButton, button.rect.height() / 2)
        return when (hit.zone) {
            Zone.Surface -> box(card, theme.dp(SURFACE_PAD), min(theme.radiusCard, card.height() / 2)).also { kind = Kind.Ring }
            Zone.Color -> box(button!!.rect, theme.dp(ELEMENT_PAD), btnRadius).also { kind = Kind.SolidRing }
            Zone.Icon -> box(button!!.iconRect, theme.dp(ELEMENT_PAD), theme.dp(ELEMENT_RADIUS) / fit).also { kind = Kind.Ring }
            Zone.Font -> box(button?.labelRect ?: aaRect, theme.dp(ELEMENT_PAD), theme.dp(ELEMENT_RADIUS) / fit).also { kind = Kind.Ring }
            Zone.PadX -> {
                val b = button!!.rect
                bar(sx(if (hit.edge == Edge.Left) b.left else b.right), sy(b.centerY()), theme.dp(BAR), b.height() * fit * BAR_SPAN_Y)
            }
            Zone.PadY -> {
                val b = button!!.rect
                bar(sx(b.centerX()), sy(if (hit.edge == Edge.Top) b.top else b.bottom), b.width() * fit * BAR_SPAN_X, theme.dp(BAR))
            }
            Zone.CardRadius, Zone.ButtonRadius -> {
                val rect = if (hit.zone == Zone.CardRadius) card else button!!.rect
                val radius = if (hit.zone == Zone.CardRadius) min(theme.radiusCard, card.height() / 2) else btnRadius
                val off = radius * (1 - INV_SQRT2)
                val c = hit.corner!!
                val d = theme.dp(DOT)
                target.set(0f, 0f, d, d)
                target.offsetTo(sx(if (c.left) rect.left + off else rect.right - off) - d / 2, sy(if (c.top) rect.top + off else rect.bottom - off) - d / 2)
                kind = Kind.Dot
                d / 2
            }
            Zone.Elevation -> bar(
                sx(card.centerX()),
                sy(card.bottom + GUIDE_TOP * fs + theme.tokens.elevation * GUIDE_TRAVEL * fs) + theme.dp(GUIDE_THICKNESS) / 2,
                card.width() * fit * GUIDE_ZONE, theme.dp(GUIDE_THICKNESS),
            )
        }
    }

    private fun sx(u: Float) = originX + u * fit
    private fun sy(v: Float) = originY + v * fit

    /** Where a tooltip should sit for the current target: just above the card, in view pixels. */
    val cardTop get() = originY

    private companion object {
        const val AA = "Aa"
        const val AA_SIZE = 2.6f
        const val AA_LINE = 0.95f
        const val AA_TRACKING = -0.03f
        const val META_SIZE = 0.78f
        const val META_LINE = 1.45f
        const val HEAD_GAP = 2f
        const val CARD_PAD_V = 1.5f
        const val CARD_PAD_H = 1.6f
        const val CARD_GAP = 1.2f
        const val ICON_SIZE = 1.2f
        const val ICON_GAP = 0.5f
        const val LABEL_LINE = 1.2f
        const val BUTTON_GAP = 0.6f
        const val BUTTON_BORDER = 1.5f
        const val BUTTON_EDGE = 0.42f
        const val ICON_SLOP = 0.2f
        const val BORDER_OPACITY = 0.7f
        const val LIFT = 0.35f

        const val SHADOW_BLUR = 3.2f / 2 // a canvas shadow radius is about half a CSS blur
        const val SHADOW_BLUR_BASE = 0.1f
        const val SHADOW_Y = 1.3f
        const val SHADOW_Y_BASE = 0.04f

        const val GUIDE_WIDTH = 2.6f
        const val GUIDE_HEIGHT = 2.6f
        const val GUIDE_TOP = 1.2f
        const val GUIDE_TRAVEL = 1.2f
        const val GUIDE_THICKNESS = 4f
        const val GUIDE_ALPHA = 0.22f
        const val GUIDE_ZONE = 0.2f

        const val CORNER_INSET = 0.15f
        const val SURFACE_PAD = 7f
        const val ELEMENT_PAD = 4f
        const val ELEMENT_RADIUS = 6f
        const val BAR = 3f
        const val BAR_SPAN_X = 0.4f
        const val BAR_SPAN_Y = 0.52f
        const val DOT = 10f
        const val HALO = 3f
        const val HALO_ALPHA = 0.12f
        const val RING_WIDTH = 1.5f
        const val RING_ALPHA = 0.26f
        const val DASH = 6f
        val INV_SQRT2 = 1f / sqrt(2f)

        const val SWAP_FADE = 0.8f
        const val SWAP_SHIFT = 0.25f
        const val FLIP_IMPULSE = 0.6f
        const val ICON_POP = 4f
        const val BUTTON_POP = 1.6f
        const val TRACK_STIFFNESS = 3000f
        const val PRECISION = 0.001f
        const val HIGHLIGHT_PRECISION = 0.3f
    }
}
