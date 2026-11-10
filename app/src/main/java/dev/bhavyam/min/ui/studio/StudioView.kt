package dev.bhavyam.min.ui.studio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
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
import dev.bhavyam.min.theme.Anchor
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.ThemeStore
import dev.bhavyam.min.theme.Tokens
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import dev.bhavyam.min.ui.home.Block
import dev.bhavyam.min.ui.home.HomeView
import kotlin.math.hypot

/**
 * The home editor: Token Studio's idea, played on home itself. This view is a transparent layer
 * of chrome over the real home screen; where a finger lands decides what it edits —
 *
 *  - the background: glide ↕ dark ⇄ light, ↔ tint; pinch to scale everything;
 *  - the apps: glide ↔ hue, ↕ vividness; tap for the next button style; their sides, ends and
 *    corners set padding and radius; pinch to resize;
 *  - the clock: tap for the next typeface, glide for weight, double-tap to write its format; its
 *    sides add or remove detail, its top and bottom split it into lines, its corners resize it;
 *  - hold the clock or the apps to lift it, drag, and it snaps into one of nine spots.
 *
 * Every change restyles home live. Undo, redo, surprise and reset sit at the bottom with the legend.
 */
@SuppressLint("ViewConstructor")
class StudioView(
    context: Context,
    private val themes: ThemeStore,
    private val settings: SettingsStore,
    private val home: HomeView,
    private val onClose: () -> Unit,
) : FrameLayout(context), Themed {

    private val d = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val doubleTapMillis = ViewConfiguration.getDoubleTapTimeout().toLong()

    private val outlines = OutlineView(context, home)
    private val tip = TooltipView(context).apply { alpha = 0f }
    private val whisper = TextView(context).apply {
        text = "Touch anything on home — glide, tap, pinch or hold."
        gravity = Gravity.CENTER
    }

    private val done = ToolButton(context, Icons.check, "Done", "Done", settings).apply { setOnClickListener { onClose() } }
    private val topBar = FrameLayout(context).apply { addView(done, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)) }

    private val undo = ToolButton(context, Icons.undo, null, "Undo", settings).apply { setOnClickListener { step(history?.undo()) } }
    private val redo = ToolButton(context, Icons.redo, null, "Redo", settings).apply { setOnClickListener { step(history?.redo()) } }
    private val shuffle = ToolButton(context, Icons.shuffle, null, "Surprise me", settings).apply { setOnClickListener { jump(Tokens.random(themes.tokens)) } }
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

    private val clockEditor = ClockEditor(context, themes, settings) { commit() }

    private var theme: Theme? = null
    private var history: StudioHistory? = null

    // the edit in progress
    private var hit: Hit? = null
    private var start: Tokens = themes.tokens
    private val drag = Drag()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pinchSpan = 0f
    private var whisperGone = false
    private val rect = RectF()

    // moving a block
    private var lifted: Block? = null
    private val liftHome = RectF()
    private val targets = Array(Anchor.entries.size) { RectF() }
    private var snapped = -1
    private val liftTimer = Runnable { lift() }

    /** A single tap on the clock waits out the double-tap window before changing the typeface. */
    private var fontTapPending = false
    private val fontTap = Runnable {
        fontTapPending = false
        Zone.Typeface.tap(themes.tokens)?.let(themes::update)
        home.motion(Block.Clock).pop()
        post(::commit)
    }

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

    /** Editor in/out: chrome slides in from the edges and home makes room for it. */
    private val presence = Spring(PRECISION, ::applyPresence)

    var isOpen = false
        private set

    init {
        visibility = GONE
        addView(outlines)
        addView(whisper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(tip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(clockEditor)
        home.onBlocksMoved = { if (visibility == VISIBLE) outlines.invalidate() }
        setOnApplyWindowInsetsListener { _, insets ->
            val p = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            (topBar.layoutParams as MarginLayoutParams).setMargins(p.left, p.top, p.right, 0)
            (bottomBar.layoutParams as MarginLayoutParams).setMargins(p.left, 0, p.right, p.bottom)
            requestLayout()
            insets
        }
    }

    /** Home keeps clear of the bars, so nothing being edited hides behind them. */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        home.setEditRoom(topBar.bottom, height - bottomBar.top, presence.value.coerceAtLeast(0f))
    }

    fun open() {
        isOpen = true
        history = StudioHistory(themes.tokens)
        syncHistory()
        visibility = VISIBLE
        whisperGone = false
        whisperFade.snapTo(1f)
        presence.animateTo(1f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    fun close() {
        isOpen = false
        clockEditor.close()
        cancelGesture()
        endFocus()
        presence.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    /** Gone at once (the launcher went to the background). */
    fun reset() {
        isOpen = false
        clockEditor.reset()
        cancelGesture()
        endFocus()
        presence.snapTo(0f)
        home.resetMotion()
    }

    /** Back: the clock sheet first, then the editor itself. */
    fun back(): Boolean = when {
        clockEditor.isOpen -> { clockEditor.close(); true }
        isOpen -> { onClose(); true }
        else -> false
    }

    private fun applyPresence(p: Float) {
        alpha = p.coerceIn(0f, 1f)
        if (p <= 0f && !isOpen) {
            visibility = GONE
            history = null
        }
        val away = 1f - p
        topBar.translationY = -away * CHROME_SLIDE * d
        bottomBar.translationY = away * CHROME_SLIDE * d
        home.setEditRoom(topBar.bottom, height - bottomBar.top, p.coerceAtLeast(0f))
    }

    // ------------------------------------------------------------------ editing

    /** The block and part under ([x], [y]); a block the point is inside wins over one it's merely near. */
    private fun locate(x: Float, y: Float): Pair<Block?, Part> {
        var near: Pair<Block, Part>? = null
        for (block in Block.entries) {
            home.bounds(block, rect)
            val part = Part.at(rect, x, y, Globals.Space.TOUCH_EDGE * d, Globals.Space.TOUCH_EDGE * d / 2) ?: continue
            if (rect.contains(x, y)) return block to part
            if (near == null) near = block to part
        }
        return near ?: (null to Part.Body)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(e.x, e.y)
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2 && lifted == null) startPinch(e)
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_UP -> up()
            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()
                finishEdit()
            }
        }
        return true
    }

    private fun down(x: Float, y: Float) {
        removeCallbacks(linger)
        downX = x
        downY = y
        dragging = false
        pinchSpan = 0f
        start = themes.tokens
        val (block, part) = locate(x, y)
        hit = Hit(Zone.of(block, part), block, part)
        if (block != null) {
            home.bounds(block, rect)
            drag.width = rect.width() / d
            drag.height = rect.height() / d
            if (part == Part.Body) postDelayed(liftTimer, Globals.Motion.LIFT_DELAY)
        }
        dismissWhisper()
        focus(x, y)
    }

    /** A second finger turns any gesture into a pinch on what the first one touched. */
    private fun startPinch(e: MotionEvent) {
        removeCallbacks(liftTimer)
        pinchSpan = span(e)
        start = themes.tokens
        val block = hit?.block
        hit = Hit(Zone.pinchOf(block), block)
        dragging = true
        hintFold.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
    }

    private fun move(e: MotionEvent) {
        val h = hit ?: return
        if (lifted != null) {
            moveLifted(e.x, e.y)
            return
        }
        if (pinchSpan > 0f) {
            if (e.pointerCount >= 2) {
                h.zone.pinch(start, span(e) / pinchSpan)?.let(themes::update)
                focus((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2)
            }
            return // one finger left after a pinch: wait for the lift
        }
        if (!dragging && hypot(e.x - downX, e.y - downY) > touchSlop) {
            dragging = true
            removeCallbacks(liftTimer)
            parent.requestDisallowInterceptTouchEvent(true)
            hintFold.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        }
        if (!dragging) return
        drag.dx = (e.x - downX) / d * TOUCH_GAIN
        drag.dy = (e.y - downY) / d * TOUCH_GAIN
        val next = h.zone.drag(drag, start, h)
        if (next.clock != themes.tokens.clock) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) // a detail or line step
        themes.update(next)
        focus(e.x, e.y)
    }

    private fun up() {
        removeCallbacks(liftTimer)
        val h = hit
        when {
            lifted != null -> drop()
            h == null || dragging -> Unit
            h.zone == Zone.Typeface -> if (fontTapPending) {
                removeCallbacks(fontTap)
                fontTapPending = false
                endFocus()
                clockEditor.open()
            } else {
                fontTapPending = true
                postDelayed(fontTap, doubleTapMillis)
            }
            else -> h.zone.tap(themes.tokens)?.let { next ->
                themes.update(next)
                h.block?.let { home.motion(it).pop() }
            }
        }
        finishEdit()
    }

    private fun span(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    // ------------------------------------------------------------------ moving a block

    /** Held still long enough: the block lifts, and the spots it can go to appear. */
    private fun lift() {
        val h = hit ?: return
        val block = h.block ?: return
        lifted = block
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        home.layoutBounds(block, liftHome)
        for (a in Anchor.entries) home.candidate(block, a, targets[a.ordinal])
        home.motion(block).lift(true)
        val current = anchorOf(block, themes.tokens)
        snapped = current.ordinal
        hit = h.copy(zone = Zone.Move, anchor = current)
        outlines.setTargets(targets, snapped)
        hintFold.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        focus(downX, downY)
    }

    /** The block follows the finger, and is pulled onto any spot it comes close to. */
    private fun moveLifted(x: Float, y: Float) {
        val block = lifted ?: return
        val fx = x - downX
        val fy = y - downY
        val cx = liftHome.centerX() + fx
        val cy = liftHome.centerY() + fy
        var nearest = 0
        var best = Float.MAX_VALUE
        for (i in targets.indices) {
            val dist = hypot(targets[i].centerX() - cx, targets[i].centerY() - cy)
            if (dist < best) {
                best = dist
                nearest = i
            }
        }
        val inReach = best < Globals.Space.MAGNET * d
        val motion = home.motion(block)
        if (inReach) motion.follow(targets[nearest].left - liftHome.left, targets[nearest].top - liftHome.top)
        else motion.follow(fx, fy)
        val reach = if (inReach) nearest else -1
        if (reach != snapped) {
            if (inReach) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            snapped = reach
            outlines.setTargets(targets, snapped)
        }
        val anchor = Anchor.entries[nearest]
        if (hit?.anchor != anchor) hit = hit?.copy(anchor = anchor)
        focus(x, y)
    }

    /** Let go: the block goes to the nearest spot (gliding there from where it was dropped). */
    private fun drop() {
        val block = lifted ?: return
        lifted = null
        outlines.setTargets(null, -1)
        val motion = home.motion(block)
        motion.lift(false)
        motion.follow(0f, 0f)
        val anchor = hit?.anchor ?: return
        val t = themes.tokens
        themes.update(if (block == Block.Clock) t.copy(clockAnchor = anchor) else t.copy(appAnchor = anchor))
    }

    private fun anchorOf(block: Block, t: Tokens) = if (block == Block.Clock) t.clockAnchor else t.appAnchor

    /** Drop whatever the finger was doing, without applying it. */
    private fun cancelGesture() {
        removeCallbacks(liftTimer)
        removeCallbacks(fontTap)
        fontTapPending = false
        lifted?.let {
            lifted = null
            outlines.setTargets(null, -1)
            home.motion(it).lift(false)
            home.motion(it).follow(0f, 0f)
        }
    }

    // ------------------------------------------------------------------ tooltip, history, legend

    private fun finishEdit() {
        if (hit == null) return
        dragging = false
        hintFold.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        post(::commit) // the change lands on the next frame; record it after that
        postDelayed(linger, LINGER_MILLIS)
    }

    /** Record the current tokens as a step in history and keep them. */
    private fun commit() {
        if (history?.commit(themes.tokens) == true) themes.save()
        syncHistory()
    }

    /** Point the highlight and the tip at [hit], with the tip near ([x], [y]). */
    private fun focus(x: Float, y: Float) {
        val h = hit ?: return
        outlines.highlight(h)
        refreshTooltip()
        placeTooltip(x, y)
        tipShow.animateTo(1f, settings.spring(SettingsRegistry.CONTROL_MOTION))
    }

    private fun endFocus() {
        hit = null
        outlines.highlight(null)
        tipShow.animateTo(0f, settings.spring(SettingsRegistry.CONTROL_MOTION))
        chips.forEach { it.setOn(false) }
    }

    private fun refreshTooltip() {
        val h = hit ?: return
        val theme = theme ?: return
        tip.set(h.zone.label(h), h.zone.value(themes.tokens, h), h.zone.hint(h), h.zone.swatch(theme))
        chips.forEach { it.setOn(it.legend == h.zone.legend) }
    }

    /** Above the finger (a finger hides what's under it), centred on it, kept on screen. */
    private fun placeTooltip(x: Float, y: Float) {
        val w = tip.width.takeIf { it > 0 } ?: (TIP_GUESS_W * d).toInt()
        val h = tip.height.takeIf { it > 0 } ?: (TIP_GUESS_H * d).toInt()
        val offset = Globals.Space.TOOLTIP_OFFSET * d
        val margin = Globals.Space.EDGE * d / 2
        val tx = (x - w / 2f).coerceIn(margin, (width - w - margin).coerceAtLeast(margin))
        val above = y - offset - h
        val fitsAbove = above >= topBar.bottom
        val ty = if (fitsAbove) above else y + offset
        tip.pivotX = (x - tx).coerceIn(0f, w.toFloat())
        tip.pivotY = if (fitsAbove) h.toFloat() else 0f
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

    /** Legend chips: tap one to see where that family of controls lives on home. */
    private fun showLegend(legend: Legend) {
        removeCallbacks(linger)
        val t = themes.tokens
        hit = when (legend) {
            Legend.Background -> Hit(Zone.Background)
            Legend.Apps -> Hit(Zone.AppColor, Block.Apps)
            Legend.Edges -> Hit(Zone.ClockDetail, Block.Clock, Part.Right)
            Legend.Clock -> Hit(Zone.Typeface, Block.Clock)
            Legend.Move -> Hit(Zone.Move, Block.Apps, anchor = t.appAnchor)
        }
        dismissWhisper()
        val block = hit?.block
        if (block != null) home.bounds(block, rect) else rect.set(0f, 0f, width.toFloat(), height.toFloat())
        focus(rect.centerX(), rect.centerY())
        postDelayed(linger, LINGER_MILLIS * 2)
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        outlines.applyTheme(theme)
        whisper.style(theme, Globals.Type.WHISPER, theme.muted)
        val gap = (Globals.Space.TOOL_GAP * d).toInt()
        val edge = (Globals.Space.EDGE * d).toInt()
        topBar.setPadding(0, edge * 2 / 3, 0, 0)
        bottomBar.setPadding(0, 0, 0, edge * 2 / 3)
        for (v in listOf(done) + tools) v.applyTheme(theme)
        for (v in tools.drop(1)) (v.layoutParams as MarginLayoutParams).marginStart = gap
        (legendScroll.layoutParams as MarginLayoutParams).topMargin = gap * 2
        legendBackground.setColor(Theme.withAlpha(theme.surface, LEGEND_GLASS))
        legendBackground.setStroke(d.toInt().coerceAtLeast(1), Theme.withAlpha(theme.text, LEGEND_RING))
        legendBackground.cornerRadius = Float.MAX_VALUE
        val inset = (LEGEND_INSET * d).toInt()
        legend.setPadding(inset, inset, inset, inset)
        chips.forEach { it.applyTheme(theme) }
        clockEditor.applyTheme(theme)
        if (hit != null) refreshTooltip()
    }

    /** A legend chip: "Apps  tap · glide · pinch". Filled with the text colour while its zone is in use. */
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
        const val TOUCH_GAIN = 1.4f // fingers travel less than a mouse; Token Studio's gains are per pointer px
        const val LINGER_MILLIS = 1100L
        const val TIP_STIFFNESS = 900f
        const val TIP_SCALE_FROM = 0.94f
        const val TIP_GUESS_W = 160f
        const val TIP_GUESS_H = 52f
        const val TIP_PRECISION = 0.5f
        const val PRECISION = 0.001f
        const val WHISPER_RISE = 8f
        const val CHROME_SLIDE = 24f
        const val LEGEND_GLASS = 0.65f
        const val LEGEND_RING = 0.08f
        const val LEGEND_INSET = 5f
        const val CHIP_PAD_X = 11f
        const val CHIP_PAD_Y = 6f
    }
}
