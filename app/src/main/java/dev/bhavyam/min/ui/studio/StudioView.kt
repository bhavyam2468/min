package dev.bhavyam.min.ui.studio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.motion.SpringSpec
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.ThemeStore
import dev.bhavyam.min.theme.Tokens
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import kotlin.math.hypot

/**
 * Token Studio, full screen. One card; where your finger lands decides what you edit — corners
 * for radius, button edges for padding, fills for colour, text for type, icons for icon style,
 * the card itself for surface, the space below it for elevation; pinch anywhere to scale.
 * Every change restyles the whole launcher live. Undo, redo, surprise and reset sit at the bottom
 * with the legend; hold the eye to see home in the new look, and export as CSS or JSON.
 */
@SuppressLint("ViewConstructor")
class StudioView(
    context: Context,
    private val themes: ThemeStore,
    private val settings: SettingsStore,
    private val onClose: () -> Unit,
    /** True once the studio fully covers what's beneath it, false as soon as it starts to leave. */
    private val onCovering: (Boolean) -> Unit,
) : FrameLayout(context), Themed {

    private val d = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val ambient = AmbientView(context)
    private val card = StudioCard(context, settings)
    private val tip = TooltipView(context).apply { alpha = 0f }
    private val whisper = TextView(context).apply {
        text = "Touch anything on the card — then drag or tap."
        gravity = Gravity.CENTER
    }

    // top bar
    private val back = ToolButton(context, Icons.back, null, "Back", settings).apply { setOnClickListener { onClose() } }
    private val mark = View(context)
    private val markBackground = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0, 0))
    private val title = TextView(context).apply { text = "Token Studio" }
    private val preview = ToolButton(context, Icons.eye, null, "Hold to preview", settings)
    private val export = ToolButton(context, Icons.code, "Export", "Export tokens", settings)
    private val topBar = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(back)
        addView(mark)
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(preview)
        addView(export)
    }

    // bottom: history tools + legend
    private val undo = ToolButton(context, Icons.undo, null, "Undo", settings).apply { setOnClickListener { step(history?.undo()) } }
    private val redo = ToolButton(context, Icons.redo, null, "Redo", settings).apply { setOnClickListener { step(history?.redo()) } }
    private val shuffle = ToolButton(context, Icons.shuffle, null, "Surprise me", settings).apply { setOnClickListener { jump(Tokens.random()) } }
    private val reset = ToolButton(context, Icons.reset, null, "Reset", settings).apply { setOnClickListener { jump(Globals.DEFAULT_TOKENS) } }
    private val tools = listOf(undo, redo, shuffle, reset)
    private val toolRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        tools.forEach { addView(it) }
    }
    private val chips = Legend.entries.map { LegendChip(context, it) }
    private val legendBackground = GradientDrawable()
    private val legend = LinearLayout(context).apply {
        background = legendBackground
        chips.forEach { addView(it) }
    }
    private val legendScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(legend)
    }
    private val bottomBar = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(toolRow)
        addView(legendScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private val sheet = ExportSheet(context, settings, themes.fonts)

    private var theme: Theme? = null
    private var history: StudioHistory? = null

    // the edit in progress
    private var hit: Hit? = null
    private var start: Tokens = themes.tokens
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pinchSpan = 0f
    private var whisperGone = false

    private val tipX = Spring(TIP_PRECISION) { tip.translationX = it }
    private val tipY = Spring(TIP_PRECISION) { tip.translationY = it }
    private val tipShow = Spring(PRECISION) {
        tip.alpha = it.coerceIn(0f, 1f)
        val s = TIP_SCALE_FROM + (1 - TIP_SCALE_FROM) * it
        tip.scaleX = s
        tip.scaleY = s
    }
    private val hintFold = Spring(PRECISION) { tip.hintOpen = it }.also { it.snapTo(1f) }
    private val whisperFade = Spring(PRECISION) {
        whisper.alpha = it.coerceIn(0f, 1f)
        whisper.translationY = (it - 1f) * WHISPER_RISE * d
    }
    private val linger = Runnable { endFocus() }

    /** Studio in/out: content rises into focus out of a blur, chrome slides in from the edges. */
    private val presence = Spring(PRECISION, ::applyPresence)

    /** Hold-to-preview: fades the studio away to show home beneath. */
    private val peek = Spring(PRECISION) { stage.alpha = 1f - it.coerceIn(0f, 1f) }

    private val stage = FrameLayout(context).apply {
        addView(ambient)
        addView(card)
        addView(whisper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(tip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    var isOpen = false
        private set

    init {
        visibility = GONE
        isClickable = true
        addView(stage)
        addView(sheet)
        export.setOnClickListener { sheet.show() }
        preview.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> peek.animateTo(1f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> peek.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
            }
            v.isPressed = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE
            true
        }
        // The ambient light runs under the system bars; everything else stays inside the safe area.
        setOnApplyWindowInsetsListener { _, insets ->
            val p = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            (topBar.layoutParams as MarginLayoutParams).setMargins(p.left, p.top, p.right, 0)
            (bottomBar.layoutParams as MarginLayoutParams).setMargins(p.left, 0, p.right, p.bottom)
            (sheet.layoutParams as MarginLayoutParams).setMargins(p.left, p.top, p.right, p.bottom)
            // the card centres in the space the bars leave (the bottom one holds two rows)
            val bar = ((Globals.Space.TOOL + Globals.Space.EDGE) * d).toInt()
            (card.layoutParams as MarginLayoutParams).setMargins(p.left, p.top + bar, p.right, p.bottom + bar * 2)
            requestLayout()
            insets
        }
    }

    /** The whisper floats just above the card, wherever the tokens have put it. */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val y = (card.top + card.cardTop - whisper.measuredHeight - Globals.Space.EDGE * d).toInt()
        whisper.layout(0, y, stage.width, y + whisper.measuredHeight)
    }

    fun open() {
        isOpen = true
        history = StudioHistory(themes.tokens)
        syncHistory()
        visibility = VISIBLE
        whisperGone = false
        whisperFade.snapTo(1f)
        presence.onRest = { if (isOpen) onCovering(true) }
        presence.animateTo(1f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    fun close() {
        isOpen = false
        onCovering(false)
        endFocus()
        sheet.hide()
        presence.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    /** Gone at once (the launcher went to the background). */
    fun reset() {
        isOpen = false
        onCovering(false)
        endFocus()
        sheet.reset()
        peek.snapTo(0f)
        presence.snapTo(0f)
    }

    /** Back: the export sheet first, then the studio itself. */
    fun back(): Boolean = when {
        sheet.isShowing -> { sheet.hide(); true }
        isOpen -> { onClose(); true }
        else -> false
    }

    private fun applyPresence(p: Float) {
        val a = p.coerceIn(0f, 1f)
        alpha = a
        if (p <= 0f && !isOpen) {
            visibility = GONE
            history = null
        }
        val away = 1f - p
        card.translationY = away * ARRIVE_RISE * d
        val s = 1f - away * ARRIVE_SHRINK
        card.scaleX = s
        card.scaleY = s
        val blur = (1f - a) * ARRIVE_BLUR * d
        card.setRenderEffect(if (blur >= MIN_BLUR) RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL) else null)
        topBar.translationY = -away * CHROME_SLIDE * d
        bottomBar.translationY = away * CHROME_SLIDE * d
    }

    // ------------------------------------------------------------------ editing

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x - card.left
        val y = e.y - card.top
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(linger)
                downX = x
                downY = y
                dragging = false
                pinchSpan = 0f
                start = themes.tokens
                hit = card.hitAt(x, y)
                if (hit != null) {
                    dismissWhisper()
                    focus(x, y)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                // a second finger turns any gesture into a pinch on the scale token
                pinchSpan = span(e)
                start = themes.tokens
                hit = Hit(Zone.Surface)
                dragging = true
                dismissWhisper()
                hintFold.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
            }
            MotionEvent.ACTION_MOVE -> {
                val h = hit ?: return true
                if (pinchSpan > 0f && e.pointerCount >= 2) {
                    val scale = (start.scale * span(e) / pinchSpan).coerceIn(Tokens.SCALE_MIN, Tokens.SCALE_MAX)
                    themes.update(themes.tokens.copy(scale = scale))
                    focus((e.getX(0) + e.getX(1)) / 2 - card.left, (e.getY(0) + e.getY(1)) / 2 - card.top)
                    return true
                }
                if (pinchSpan > 0f) return true // one finger left after a pinch: wait for the lift
                if (!dragging && hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    hintFold.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
                }
                if (dragging) {
                    themes.update(h.zone.drag((x - downX) / d * TOUCH_GAIN, (y - downY) / d * TOUCH_GAIN, start, h))
                    focus(x, y)
                }
            }
            MotionEvent.ACTION_UP -> {
                val h = hit
                if (h != null && !dragging) h.zone.tap(themes.tokens, h)?.let(themes::update)
                finishEdit()
            }
            MotionEvent.ACTION_CANCEL -> finishEdit()
        }
        return true
    }

    private fun span(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun finishEdit() {
        if (hit == null) return
        dragging = false
        hintFold.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        // the tap's change lands on the next frame; record it after that
        post {
            if (history?.commit(themes.tokens) == true) themes.save()
            syncHistory()
        }
        postDelayed(linger, LINGER_MILLIS)
    }

    /** Point the highlight and the tip at [hit], with the tip near ([x], [y]) in card coordinates. */
    private fun focus(x: Float, y: Float) {
        val h = hit ?: return
        card.highlight(h, dragging)
        refreshTooltip()
        placeTooltip(x, y)
        tipShow.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
    }

    private fun endFocus() {
        hit = null
        card.highlight(null, false)
        tipShow.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        chips.forEach { it.setOn(false) }
    }

    private fun refreshTooltip() {
        val h = hit ?: return
        val theme = theme ?: return
        tip.set(h.zone.label(h), h.zone.value(themes.tokens, h), h.zone.hint(h), h.zone.swatch(theme, h))
        chips.forEach { it.setOn(it.legend == h.zone.legend) }
    }

    /** Above the finger (a finger hides what's under it), centred, kept on screen. */
    private fun placeTooltip(x: Float, y: Float) {
        val w = tip.width.takeIf { it > 0 } ?: (TIP_GUESS_W * d).toInt()
        val h = tip.height.takeIf { it > 0 } ?: (TIP_GUESS_H * d).toInt()
        val offset = Globals.Space.TOOLTIP_OFFSET * d
        val margin = Globals.Space.EDGE * d / 2
        val px = card.left + x
        val py = card.top + y
        val tx = (px - w / 2f).coerceIn(margin, (stage.width - w - margin).coerceAtLeast(margin))
        val above = py - offset - h
        val ty = if (above >= card.top) above else py + offset
        tip.pivotX = (px - tx).coerceIn(0f, w.toFloat())
        tip.pivotY = if (above >= card.top) h.toFloat() else 0f
        val spec = SpringSpec(TIP_STIFFNESS, 1f)
        if (tip.alpha <= 0f) {
            tipX.snapTo(tx)
            tipY.snapTo(ty)
        } else {
            tipX.animateTo(tx, spec)
            tipY.animateTo(ty, spec)
        }
    }

    private fun dismissWhisper() {
        if (whisperGone) return
        whisperGone = true
        whisperFade.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    /** Undo / redo: glide to a recorded state. */
    private fun step(target: Tokens?) {
        target ?: return
        themes.morphTo(target, settings.spring(SettingsRegistry.THEME_MOTION))
        syncHistory()
    }

    /** Reset / surprise: glide to a new state and record it. */
    private fun jump(target: Tokens) {
        themes.morphTo(target, settings.spring(SettingsRegistry.THEME_MOTION))
        history?.commit(target)
        syncHistory()
    }

    private fun syncHistory() {
        undo.isEnabled = history?.canUndo == true
        redo.isEnabled = history?.canRedo == true
    }

    /** Legend chips: tap one to see where that family of controls lives on the card. */
    private fun showLegend(legend: Legend) {
        removeCallbacks(linger)
        hit = when (legend) {
            Legend.Corners -> Hit(Zone.CardRadius, corner = Corner.TR)
            Legend.Padding -> Hit(Zone.PadX, edge = Edge.Right, tier = 0)
            Legend.Fill -> Hit(Zone.Color, tier = 0)
            Legend.Text -> Hit(Zone.Font, tier = -1)
            Legend.Icons -> Hit(Zone.Icon, tier = 1)
            Legend.Surface -> Hit(Zone.Surface)
            Legend.Shadow -> Hit(Zone.Elevation)
        }
        dismissWhisper()
        card.highlight(hit, false)
        refreshTooltip()
        placeTooltip(card.width / 2f, card.cardTop)
        tipShow.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        postDelayed(linger, LINGER_MILLIS * 2)
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        ambient.applyTheme(theme)
        card.applyTheme(theme)
        whisper.style(theme, Globals.Type.WHISPER, theme.muted)
        title.style(theme, Globals.Type.ROW, strong = true)
        markBackground.colors = intArrayOf(theme.primary.gradientStart, theme.primary.gradientEnd)
        markBackground.cornerRadius = (theme.radiusButton * MARK_SHAPE).coerceAtMost(MARK_SIZE * d / 2) // a button in miniature
        mark.background = markBackground
        val gap = (Globals.Space.TOOL_GAP * d).toInt()
        val edge = (Globals.Space.EDGE * d).toInt()
        (mark.layoutParams as MarginLayoutParams).apply {
            width = (MARK_SIZE * d).toInt()
            height = width
            marginStart = edge / 2
            marginEnd = gap * 2
        }
        topBar.setPadding(edge * 2 / 3, edge * 2 / 3, edge * 2 / 3, 0)
        bottomBar.setPadding(0, 0, 0, edge * 2 / 3)
        for (v in listOf(back, preview, export) + tools) v.applyTheme(theme)
        for (v in listOf(export) + tools.drop(1)) (v.layoutParams as MarginLayoutParams).marginStart = gap
        (legendScroll.layoutParams as MarginLayoutParams).topMargin = gap * 2
        legendBackground.setColor(Theme.withAlpha(theme.surface, LEGEND_GLASS))
        legendBackground.setStroke(d.toInt().coerceAtLeast(1), Theme.withAlpha(theme.text, LEGEND_RING))
        legendBackground.cornerRadius = Float.MAX_VALUE
        val inset = (LEGEND_INSET * d).toInt()
        legend.setPadding(inset, inset, inset, inset)
        chips.forEach { it.applyTheme(theme) }
        sheet.applyTheme(theme)
        if (hit != null) {
            refreshTooltip()
            card.highlight(hit, dragging)
        }
    }

    /** A legend chip: "Corners drag". Filled with the text colour while its zone is in use. */
    @SuppressLint("ViewConstructor")
    private inner class LegendChip(context: Context, val legend: Legend) : TextView(context), Themed {
        private val fill = GradientDrawable().apply { cornerRadius = Float.MAX_VALUE }
        private var on = 0f
        private var themeRef: Theme? = null
        private val spring = Spring(PRECISION) { on = it; paintState() }

        init {
            text = "${legend.label}  ${legend.gesture}"
            background = fill
            isSingleLine = true
            setOnClickListener { showLegend(legend) }
        }

        fun setOn(active: Boolean) = spring.animateTo(if (active) 1f else 0f, settings.spring(SettingsRegistry.CONTROL_MOTION))

        override fun applyTheme(theme: Theme) {
            themeRef = theme
            style(theme, Globals.Type.CHIP)
            val px = (CHIP_PAD_X * d).toInt()
            val py = (CHIP_PAD_Y * d).toInt()
            setPadding(px, py, px, py)
            paintState()
        }

        private fun paintState() {
            val t = themeRef ?: return
            val a = on.coerceIn(0f, 1f)
            fill.setColor(Theme.withAlpha(t.text, a))
            setTextColor(Theme.mix(t.muted, t.bg, a))
        }
    }

    private companion object {
        const val TOUCH_GAIN = 1.4f // fingers travel less than a mouse; the studio's gains are per pointer px
        const val LINGER_MILLIS = 1100L
        const val TIP_STIFFNESS = 900f
        const val TIP_SCALE_FROM = 0.94f
        const val TIP_GUESS_W = 160f
        const val TIP_GUESS_H = 52f
        const val TIP_PRECISION = 0.5f
        const val PRECISION = 0.001f
        const val WHISPER_RISE = 8f
        const val ARRIVE_RISE = 18f
        const val ARRIVE_SHRINK = 0.03f
        const val ARRIVE_BLUR = 6f
        const val MIN_BLUR = 0.5f
        const val CHROME_SLIDE = 24f
        const val MARK_SIZE = 22f
        const val MARK_SHAPE = 0.625f
        const val LEGEND_GLASS = 0.65f
        const val LEGEND_RING = 0.08f
        const val LEGEND_INSET = 5f
        const val CHIP_PAD_X = 11f
        const val CHIP_PAD_Y = 6f
    }
}
