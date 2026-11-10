package com.bhavyam.min.studio

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.home.ContextPopup
import com.bhavyam.min.home.HomeLayer
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Anchor
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Tokens
import com.bhavyam.min.ui.CenteredColumn
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.SearchLayer
import com.bhavyam.min.ui.ThemedCard
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi
import com.bhavyam.min.ui.safe
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The token studio: edit mode on the real launcher. It's a clear layer over home, search and
 * the long-press menu. Nothing here is a mock. Where a finger lands on the actual screen
 * decides what it edits ([Zone]): a favourite's colour or corners, the background, the menu
 * card. Dragging from there changes it live everywhere. The favourites can be picked up and
 * dropped on one of nine spots, and the clock resized by its corner, which also picks its
 * format. A tip trails the finger, a highlight marks the spot, and every finished gesture can
 * be undone.
 */
@SuppressLint("ViewConstructor")
class StudioLayer(
    context: Context,
    private val home: HomeLayer,
    private val search: SearchLayer,
    private val popup: ContextPopup,
    private val showScene: (Scene) -> Unit,
) : FrameLayout(context), Toolbar.Actions {

    private val app = App.of(context)
    private val themes = app.themes
    private val history = History(themes.tokens)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val guides = Guides(context)
    private val highlight = Highlight(context)
    private val tip = Tip(context)
    private val toolbar = Toolbar(context, this)
    private val editor = FormatEditor(context, ::onFormat, ::closeEditor)
    private val editorCard = ThemedCard(context, raised = true).apply {
        addView(editor, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private val editorColumn = CenteredColumn(context, EDITOR_MAX_WIDTH).apply { addView(editorCard) }
    private val measure = ClockMeasure(context)
    private val fit = ClockFit(measure)
    private val chrome = Spring(0.001f) { p ->
        toolbar.alpha = p.coerceIn(0f, 1f)
        toolbar.translationY = (1f - p) * dp(CHROME_RISE)
    }

    var isOpen = false
        private set
    private var scene = Scene.HOME
    private var hit: Hit? = null
    private var start: Tokens = themes.tokens
    private var described: Tokens? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var pinchSpan = 0f
    private var held: Pair<Anchor, Anchor>? = null
    private var editingDate = false
    private val clockFrom = RectF()
    private val clockBox = RectF()
    private val box = RectF()
    private val me = IntArray(2)
    private val loc = IntArray(2)

    private val sync = ViewTreeObserver.OnPreDrawListener {
        syncMarks()
        true
    }

    private val longPress = Runnable {
        val h = hit ?: return@Runnable
        if (moved) return@Runnable
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        drop()
        openEditor(date = h.view === home.clock.date)
    }

    init {
        visibility = GONE
        val edge = dpi(Globals.EDGE / 2)
        editorColumn.visibility = GONE
        addView(guides, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(highlight, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(toolbar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        addView(editorColumn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(tip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        editorColumn.setPadding(edge, 0, edge, 0)
        chrome.onRest = { if (!isOpen) visibility = GONE }
        // the screen under the toolbar keeps clear of it while the studio is up
        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (isOpen) reserve(true) }
    }

    /* ------------------------------------------------------------ open / close */

    fun open() {
        if (isOpen) return
        isOpen = true
        visibility = VISIBLE
        history.commit(themes.tokens)
        syncTools()
        scene(Scene.HOME)
        toolbar.scene(Scene.HOME)
        chrome.animateTo(1f, app.motion[SettingsRegistry.SPRING_OVERLAY])
        reserve(true)
    }

    fun close() {
        if (!isOpen) return
        closeEditor()
        drop()
        isOpen = false
        guides.show(false)
        reserve(false)
        showScene(Scene.HOME)
        chrome.animateTo(0f, app.motion[SettingsRegistry.SPRING_OVERLAY])
    }

    /** Gone at once, no motion: the launcher is resetting while nobody is looking. */
    fun closeNow() {
        close()
        chrome.snapTo(0f)
        guides.hideNow()
        visibility = GONE
    }

    /** Back closes the format editor first, then the studio. */
    fun back() = if (editor.isOpen) closeEditor() else close()

    private fun reserve(on: Boolean) {
        val inset = if (on) toolbar.height + dpi(Globals.EDGE / 2) else 0
        home.bottomInset = inset
        search.bottomInset = inset
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val safe = insets.safe()
        val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
        val edge = dpi(Globals.EDGE / 2)
        (toolbar.layoutParams as LayoutParams).bottomMargin = safe.bottom + edge
        (editorColumn.layoutParams as LayoutParams).apply {
            bottomMargin = max(safe.bottom, ime) + edge
            leftMargin = safe.left
            rightMargin = safe.right
        }
        requestLayout()
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(sync)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(sync)
        super.onDetachedFromWindow()
    }

    /* ------------------------------------------------------------ toolbar */

    override fun done() = close()

    override fun scene(scene: Scene) {
        this.scene = scene
        drop()
        guides.show(scene == Scene.HOME)
        showScene(scene)
    }

    override fun undo() = restore(history.undo())

    override fun redo() = restore(history.redo())

    override fun shuffle() = replace(Tokens.random(themes.tokens))

    override fun reset() = replace(Globals.TOKENS)

    override fun copy() {
        val clip = ClipData.newPlainText("min tokens", themes.tokens.toJson())
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    /* ------------------------------------------------------------ gestures */

    /** The toolbar and the format editor work as normal; everything else is the canvas. */
    private fun onChrome(e: MotionEvent): Boolean {
        for (v in listOf(toolbar, editorColumn)) {
            if (v.visibility != VISIBLE) continue
            rectOf(if (v === editorColumn) editorCard else v, box)
            if (box.contains(e.x, e.y)) return true
        }
        return false
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean =
        if (e.actionMasked == MotionEvent.ACTION_DOWN) !onChrome(e) else hit != null

    @SuppressLint("ClickableViewAccessibility") // the studio is a touch canvas by nature
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (onChrome(e)) return false
                // with the keyboard up, touching the screen just puts the editor away
                if (editor.isOpen) closeEditor() else grab(hitAt(e.x, e.y), e)
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (hit?.zone == Zone.SURFACE && e.pointerCount == 2) {
                removeCallbacks(longPress)
                start = themes.tokens
                pinchSpan = span(e)
            }
            MotionEvent.ACTION_MOVE -> drag(e)
            MotionEvent.ACTION_UP -> release(tapped = !moved && pinchSpan == 0f)
            MotionEvent.ACTION_CANCEL -> release(tapped = false)
        }
        return true
    }

    private fun grab(h: Hit, e: MotionEvent) {
        hit = h
        start = themes.tokens
        downX = e.x
        downY = e.y
        moved = false
        pinchSpan = 0f
        described = null
        when (h.zone) {
            Zone.LIST -> {
                home.beginMove()
                held = start.listX to start.listY
                guides.activeList = true
            }
            Zone.CLOCK_SIZE -> {
                measure.now()
                clockFrom.set(guides.clock)
                clockBox.set(clockFrom)
                guides.activeClock = true
            }
            Zone.FONT -> if (h.view === home.clock.date || h.view === home.clock.time) {
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            else -> Unit
        }
        if (h.zone == Zone.LIST || h.zone == Zone.CLOCK_SIZE) highlight.hide()
        syncMarks()
        tip.show()
        tip.follow(e.x, e.y)
    }

    private fun drag(e: MotionEvent) {
        val h = hit ?: return
        tip.follow(e.x, e.y)
        if (pinchSpan > 0f && e.pointerCount >= 2) {
            val scale = (start.scale * span(e) / pinchSpan).coerceIn(Globals.SCALE_MIN, Globals.SCALE_MAX)
            themes.set(start.copy(scale = scale))
            return
        }
        val dx = e.x - downX
        val dy = e.y - downY
        if (!moved && hypot(dx, dy) > slop) {
            moved = true
            removeCallbacks(longPress)
        }
        if (!moved) return
        when (h.zone) {
            Zone.LIST -> {
                val now = home.moveBy(dx, dy)
                if (now != null && now != held) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                held = now
                described = null
            }
            Zone.CLOCK_SIZE -> resizeClock(dx, dy)
            else -> {
                val density = resources.displayMetrics.density
                themes.set(h.zone.drag(start, dx / density, dy / density, h))
            }
        }
    }

    /** The box follows the corner; the clock takes the biggest size and richest date that fit it. */
    private fun resizeClock(dx: Float, dy: Float) {
        val pad = dp(FRAME_PAD)
        val least = pad * 2 + dp(MIN_CLOCK)
        clockBox.set(
            clockFrom.left,
            clockFrom.top,
            max(clockFrom.left + least, clockFrom.right + dx),
            max(clockFrom.top + least, clockFrom.bottom + dy),
        )
        val now = themes.tokens
        val r = fit.fit(clockBox.width() - pad * 2, clockBox.height() - pad * 2, start.clockLayout)
        val next = now.copy(
            clockDate = r.date,
            clockTime = r.time,
            clockDateSize = r.dateSize,
            clockTimeSize = r.timeSize,
            clockLayout = r.layout,
        )
        if (next == now) return
        if (next.clockDate != now.clockDate || next.clockLayout != now.clockLayout) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        themes.set(next)
    }

    private fun release(tapped: Boolean) {
        val h = hit ?: return
        removeCallbacks(longPress)
        when (h.zone) {
            Zone.LIST -> {
                val (x, y) = home.endMove()
                val t = themes.tokens
                if (x != t.listX || y != t.listY) themes.set(t.copy(listX = x, listY = y))
            }
            else -> if (tapped) h.zone.tap(themes.tokens, h)?.let {
                themes.set(it)
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
        hit = null // already let go of above
        drop()
        commit()
    }

    /** Lets go of whatever is held, without applying a tap. */
    private fun drop() {
        removeCallbacks(longPress)
        if (hit?.zone == Zone.LIST) home.endMove()
        hit = null
        pinchSpan = 0f
        held = null
        guides.activeList = false
        guides.activeClock = false
        highlight.hide()
        tip.hide()
    }

    private fun span(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    /* ------------------------------------------------------------ hit testing */

    /** What the spot at ([x], [y]) edits on the part of the launcher that's showing. */
    private fun hitAt(x: Float, y: Float): Hit = when (scene) {
        Scene.HOME -> homeHit(x, y)
        Scene.SEARCH -> searchHit(x, y)
        Scene.MENU -> menuHit(x, y) ?: homeHit(x, y)
    } ?: Hit(Zone.SURFACE, this)

    /** The guides (move, resize) only exist on the home scene; rows and clock text always. */
    private fun homeHit(x: Float, y: Float): Hit? {
        val guided = scene == Scene.HOME
        if (guided && !guides.clock.isEmpty) {
            guides.knob(box)
            if (box.contains(x, y)) return Hit(Zone.CLOCK_SIZE, home.clock)
        }
        if (guided && !guides.list.isEmpty) {
            guides.grip(box)
            if (box.contains(x, y)) return Hit(Zone.LIST, home.list)
        }
        for (row in home.rows()) buttonHit(row, Role.HOME, x, y)?.let { return it }
        if (guided && !guides.list.isEmpty) {
            box.set(guides.list)
            box.inset(-dp(FRAME_REACH), -dp(FRAME_REACH))
            if (box.contains(x, y)) return Hit(Zone.LIST, home.list)
        }
        for (v in listOf(home.clock.date, home.clock.time)) {
            if (v.visibility != VISIBLE) continue
            rectOf(v, box)
            if (box.contains(x, y)) return Hit(Zone.FONT, v)
        }
        return null
    }

    private fun searchHit(x: Float, y: Float): Hit? {
        for (row in search.rows()) buttonHit(row, Role.SEARCH, x, y)?.let { return it }
        rectOf(search.input, box)
        return if (box.contains(x, y)) Hit(Zone.FONT, search.input) else null
    }

    private fun menuHit(x: Float, y: Float): Hit? {
        if (!popup.isShowing) return null
        for (b in popup.options) buttonHit(b, Role.MENU, x, y)?.let { return it }
        val card = popup.card
        rectOf(card, box)
        if (!box.contains(x, y)) return null
        val corner = max(dp(CORNER_REACH), dp(themes.tokens.radiusCard) * 0.8f)
        val sx = side(x, box.left, box.right, corner)
        val sy = side(y, box.top, box.bottom, corner)
        return if (sx != 0 && sy != 0) Hit(Zone.CARD_RADIUS, card, sx = sx, sy = sy) else Hit(Zone.ELEVATION, card)
    }

    /** Corners → radius, side edges → padding, the icon → icon style, the middle → colour. */
    private fun buttonHit(b: RoleButton, role: Role, x: Float, y: Float): Hit? {
        rectOf(b, box)
        if (!box.contains(x, y)) return null
        val m = max(dp(EDGE_REACH), b.textSize * 0.42f)
        val bx = side(x, box.left, box.right, m)
        val by = side(y, box.top, box.bottom, m)
        return when {
            bx != 0 && by != 0 -> Hit(Zone.BUTTON_RADIUS, b, sx = bx, sy = by)
            bx != 0 -> Hit(Zone.PAD_X, b, sx = bx)
            by != 0 -> Hit(Zone.PAD_Y, b, sy = by)
            b.iconRight > b.paddingLeft && x - box.left < b.iconRight * b.scaleX -> Hit(Zone.ICON, b)
            else -> Hit(Zone.COLOR, b, role = role)
        }
    }

    /** −1 within [reach] of [lo], +1 within reach of [hi], else 0. */
    private fun side(v: Float, lo: Float, hi: Float, reach: Float) = when {
        v < lo + reach -> -1
        v > hi - reach -> 1
        else -> 0
    }

    /** [v]'s on-screen bounds in this view's coordinates, moves and presses included. */
    private fun rectOf(v: View, out: RectF) {
        getLocationInWindow(me)
        v.getLocationInWindow(loc)
        val l = (loc[0] - me[0]).toFloat()
        val t = (loc[1] - me[1]).toFloat()
        out.set(l, t, l + v.width * v.scaleX, t + v.height * v.scaleY)
    }

    /* ------------------------------------------------------------ guides, highlight, tip */

    /** Before each frame: guides and highlight onto the live geometry, tip text refreshed. */
    private fun syncMarks() {
        if (!isOpen) return
        frames()
        val h = hit ?: return
        val t = themes.tokens
        when (h.zone) {
            Zone.SURFACE, Zone.LIST, Zone.CLOCK_SIZE -> Unit
            Zone.COLOR, Zone.BUTTON_RADIUS, Zone.PAD_X, Zone.PAD_Y -> buttonMark(h, t)
            Zone.ICON -> {
                val b = h.view as RoleButton
                rectOf(b, box)
                box.left += b.paddingLeft * b.scaleX
                box.right = box.left + dp(Globals.ICON)
                box.top += b.paddingTop * b.scaleY
                box.bottom -= b.paddingBottom * b.scaleY
                box.inset(-dp(4f), -dp(4f))
                highlight.target(Highlight.Kind.RING, box, dp(6f))
            }
            Zone.FONT -> ring(h.view, dp(4f), dp(6f))
            Zone.ELEVATION -> ring(h.view, dp(6f), dp(t.radiusCard))
            Zone.CARD_RADIUS -> cornerDot(h.view, dp(t.radiusCard), h)
        }
        if (t !== described) {
            described = t
            val shown = if (h.zone == Zone.LIST) held?.let { (x, y) -> t.copy(listX = x, listY = y) } ?: t else t
            val swatch = if (h.zone == Zone.COLOR) h.role?.let { themes.theme.colors(it).fill } else null
            tip.describe(h.zone.title(h), h.zone.value(shown, h), h.zone.hint, swatch)
        }
    }

    /** The dashed frames: around the favourites as drawn now, around the clock (or its box). */
    private fun frames() {
        val pad = dp(FRAME_PAD)
        guides.list.setEmpty()
        for (row in home.rows()) {
            rectOf(row, box)
            guides.list.union(box)
        }
        if (!guides.list.isEmpty) guides.list.inset(-pad, -pad)
        if (hit?.zone == Zone.CLOCK_SIZE) {
            guides.clock.set(clockBox)
        } else {
            rectOf(home.clock, guides.clock)
            guides.clock.inset(-pad, -pad)
        }
        guides.invalidate()
    }

    private fun ring(v: View, pad: Float, radius: Float) {
        rectOf(v, box)
        val r = min(radius, min(box.width(), box.height()) / 2)
        box.inset(-pad, -pad)
        highlight.target(Highlight.Kind.RING, box, r + pad)
    }

    private fun buttonMark(h: Hit, t: Tokens) {
        val v = h.view
        when (h.zone) {
            Zone.PAD_X -> {
                rectOf(v, box)
                val x = if (h.sx < 0) box.left else box.right
                val hgt = box.height()
                box.set(x - dp(1.5f), box.top + hgt * 0.24f, x + dp(1.5f), box.bottom - hgt * 0.24f)
                highlight.target(Highlight.Kind.BAR, box, dp(2f))
            }
            Zone.PAD_Y -> {
                rectOf(v, box)
                val y = if (h.sy < 0) box.top else box.bottom
                val w = box.width()
                box.set(box.left + w * 0.3f, y - dp(1.5f), box.right - w * 0.3f, y + dp(1.5f))
                highlight.target(Highlight.Kind.BAR, box, dp(2f))
            }
            Zone.BUTTON_RADIUS -> cornerDot(v, dp(t.radiusButton), h)
            else -> ring(v, dp(4f), dp(t.radiusButton))
        }
    }

    /** A dot where the corner's curve is, sliding in as the corner rounds. */
    private fun cornerDot(v: View, radius: Float, h: Hit) {
        rectOf(v, box)
        val r = min(radius, min(box.width(), box.height()) / 2)
        val off = r * (1f - HALF_SQRT2)
        val cx = if (h.sx < 0) box.left + off else box.right - off
        val cy = if (h.sy < 0) box.top + off else box.bottom - off
        val d = dp(5f)
        box.set(cx - d, cy - d, cx + d, cy + d)
        highlight.target(Highlight.Kind.DOT, box, d)
    }

    /* ------------------------------------------------------------ editing */

    private fun openEditor(date: Boolean) {
        editingDate = date
        val t = themes.tokens
        editor.open(if (date) "Date" else "Time", if (date) t.clockDate else t.clockTime)
        toolbar.visibility = INVISIBLE
        editorColumn.visibility = VISIBLE
    }

    private fun closeEditor() {
        if (!editor.isOpen) return
        editor.close()
        editorColumn.visibility = GONE
        toolbar.visibility = VISIBLE
        commit()
    }

    private fun onFormat(pattern: String) {
        val t = themes.tokens
        themes.set(if (editingDate) t.copy(clockDate = pattern) else t.copy(clockTime = pattern))
    }

    private fun replace(t: Tokens) {
        themes.set(t)
        commit()
    }

    private fun restore(t: Tokens) {
        themes.set(t)
        themes.save()
        syncTools()
    }

    private fun commit() {
        history.commit(themes.tokens)
        themes.save()
        syncTools()
    }

    private fun syncTools() = toolbar.history(history.canUndo, history.canRedo)

    private companion object {
        const val EDITOR_MAX_WIDTH = 460f
        const val CHROME_RISE = 48f       // dp the toolbar rises as the studio opens
        const val FRAME_PAD = 8f          // dp between a thing and its dashed frame
        const val FRAME_REACH = 14f       // dp around the favourites' frame that grabs the list
        const val MIN_CLOCK = 12f         // dp the clock box can shrink to inside its frame
        const val CORNER_REACH = 28f      // dp around a card corner that grabs the corner
        const val EDGE_REACH = 12f        // dp inside a button edge that grabs the padding
        val HALF_SQRT2 = sqrt(0.5f)
    }
}
