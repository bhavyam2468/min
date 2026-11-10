package com.bhavyam.min.studio

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
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Tokens
import com.bhavyam.min.ui.CenteredColumn
import com.bhavyam.min.ui.Icon
import com.bhavyam.min.ui.OverlayLayer
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.ThemedIcon
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi
import com.bhavyam.min.ui.safe
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The token studio: the launcher's look edited by touching it. There are no sliders — where
 * the finger lands on the specimen decides what it edits ([Zone]), and dragging from there
 * changes it live across the whole launcher. A tip trails the finger with the name and value,
 * a highlight marks the spot, and every finished gesture can be undone.
 */
class StudioView(context: Context) : OverlayLayer(context) {

    private val themes = app.themes
    private val history = History(themes.tokens)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val content = CenteredColumn(context, STUDIO_MAX_WIDTH)
    private val stage = FrameLayout(context)
    private val specimen = SpecimenCard(context)
    private val highlight = Highlight(context)
    private val tip = Tip(context)
    private val whisper = ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED)
    private val whisperFade = Spring(0.001f) { whisper.alpha = it }
    private val editor = FormatEditor(context, ::onFormat, ::closeEditor)
    private val undo = tool(Icon.UNDO, "undo") { restore(history.undo()) }
    private val redo = tool(Icon.REDO, "redo") { restore(history.redo()) }

    private var hit: Hit? = null
    private var editing: Zone? = null
    private var start: Tokens = themes.tokens
    private var described: Tokens? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var axis = Axis.FREE
    private var pinchSpan = 0f
    private val box = RectF()
    private val me = IntArray(2)
    private val loc = IntArray(2)

    private enum class Axis { FREE, X, Y }

    private val sync = ViewTreeObserver.OnPreDrawListener {
        syncMarks()
        true
    }

    init {
        addView(Ambient(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val toolbar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton())
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(undo)
            addView(redo)
            addView(tool(Icon.SHUFFLE, "shuffle") { replace(Tokens.random(themes.tokens)) })
            addView(tool(Icon.RESET, "reset") { replace(Globals.TOKENS) })
            addView(tool(Icon.COPY, "copy tokens as JSON") { copy() })
        }
        stage.addView(specimen, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        val edge = dpi(Globals.EDGE / 2)
        stage.setPadding(edge, edge, edge, edge)
        whisper.text = "touch anything on the card · drag to change it"
        whisper.gravity = Gravity.CENTER
        whisper.setPadding(edge, edge, edge, edge)

        content.addView(toolbar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(stage, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(whisper, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(editor, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(highlight, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(tip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        syncTools()
    }

    private fun tool(icon: Icon, label: String, onTap: () -> Unit) = ThemedIcon(context, icon).apply {
        val pad = dpi(Globals.EDGE / 2)
        setPadding(pad, pad, pad, pad)
        contentDescription = label
        setOnClickListener {
            onTap()
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val safe = insets.safe()
        val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
        content.setPadding(safe.left, safe.top, safe.right, max(safe.bottom, ime))
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

    override fun open() {
        whisperFade.snapTo(1f)
        history.commit(themes.tokens)
        syncTools()
        super.open()
    }

    override fun close() {
        closeEditor()
        super.close()
    }

    override fun back(): Boolean {
        if (!editor.isOpen) return false
        closeEditor()
        return true
    }

    /* ------------------------------------------------------------ gestures */

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean =
        if (e.actionMasked == MotionEvent.ACTION_DOWN) onStage(e) else hit != null

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (onStage(e)) grab(hitAt(e.x, e.y), e)
            MotionEvent.ACTION_POINTER_DOWN -> if (hit != null && e.pointerCount == 2) {
                pinchSpan = span(e)
                start = themes.tokens
                hit = Hit(Zone.SURFACE, specimen.card)
                described = null
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
        axis = Axis.FREE
        pinchSpan = 0f
        described = null
        whisperFade.animateTo(0f, app.motion[SettingsRegistry.SPRING_OVERLAY])
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
            if (h.zone.locksAxis) axis = if (abs(dx) > abs(dy)) Axis.X else Axis.Y
        }
        if (!moved) return
        val density = resources.displayMetrics.density
        val next = h.zone.drag(
            start,
            if (axis == Axis.Y) 0f else dx / density,
            if (axis == Axis.X) 0f else dy / density,
            h,
        )
        if (next.clockLayout != themes.tokens.clockLayout) performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        themes.set(next)
    }

    private fun release(tapped: Boolean) {
        val h = hit ?: return
        if (tapped) {
            if (h.zone == Zone.DATE || h.zone == Zone.TIME) openEditor(h.zone)
            else h.zone.tap(themes.tokens, h)?.let {
                themes.set(it)
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
        hit = null
        pinchSpan = 0f
        commit()
        highlight.hide()
        tip.hide()
    }

    private fun onStage(e: MotionEvent): Boolean {
        rectOf(stage, box)
        return box.contains(e.x, e.y)
    }

    private fun span(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    /* ------------------------------------------------------------ hit testing */

    /** What the spot at ([x], [y]) edits: content first, then the card's corners, then its surface. */
    private fun hitAt(x: Float, y: Float): Hit {
        val t = themes.tokens
        rectOf(specimen.strip, box)
        box.inset(-dp(STRIP_REACH_X), -dp(STRIP_REACH_Y))
        if (box.contains(x, y)) return Hit(Zone.ELEVATION, specimen.strip)

        for ((role, b) in specimen.buttons) {
            rectOf(b, box)
            if (!box.contains(x, y)) continue
            val m = max(dp(EDGE_REACH), b.textSize * 0.42f)
            val bx = side(x, box.left, box.right, m)
            val by = side(y, box.top, box.bottom, m)
            return when {
                bx != 0 && by != 0 -> Hit(Zone.BUTTON_RADIUS, b, sx = bx, sy = by)
                bx != 0 -> Hit(Zone.PAD_X, b, sx = bx)
                by != 0 -> Hit(Zone.PAD_Y, b, sy = by)
                x - box.left < b.iconRight * b.scaleX -> Hit(Zone.ICON, b)
                else -> Hit(Zone.COLOR, b, role = role)
            }
        }
        for ((zone, v) in listOf(Zone.DATE to specimen.clock.date, Zone.TIME to specimen.clock.time)) {
            if (v.visibility != VISIBLE) continue
            rectOf(v, box)
            if (box.contains(x, y)) return Hit(zone, v)
        }
        for (v in listOf(specimen.aa, specimen.meta)) {
            rectOf(v, box)
            if (box.contains(x, y)) return Hit(Zone.FONT, v)
        }
        rectOf(specimen.card, box)
        val corner = max(dp(CORNER_REACH), dp(t.radiusCard) * 0.8f)
        val sx = side(x, box.left, box.right, corner)
        val sy = side(y, box.top, box.bottom, corner)
        if (sx != 0 && sy != 0 && box.contains(x, y)) return Hit(Zone.CARD_RADIUS, specimen.card, sx = sx, sy = sy)

        return Hit(Zone.SURFACE, specimen.card)
    }

    /** −1 within [reach] of [lo], +1 within reach of [hi], else 0. */
    private fun side(v: Float, lo: Float, hi: Float, reach: Float) = when {
        v < lo + reach -> -1
        v > hi - reach -> 1
        else -> 0
    }

    /** [v]'s on-screen bounds in this view's coordinates, presses and all. */
    private fun rectOf(v: View, out: RectF) {
        getLocationInWindow(me)
        v.getLocationInWindow(loc)
        val l = (loc[0] - me[0]).toFloat()
        val t = (loc[1] - me[1]).toFloat()
        out.set(l, t, l + v.width * v.scaleX, t + v.height * v.scaleY)
    }

    /* ------------------------------------------------------------ highlight + tip */

    /** Before each frame: move the highlight onto the live geometry and refresh the tip text. */
    private fun syncMarks() {
        val h = hit ?: return
        val t = themes.tokens
        when (h.zone) {
            Zone.SURFACE -> ring(specimen.card, dp(7f), dp(t.radiusCard))
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
            Zone.FONT, Zone.DATE, Zone.TIME -> ring(h.view, dp(4f), dp(6f))
            Zone.CARD_RADIUS -> cornerDot(specimen.card, dp(t.radiusCard), h)
            Zone.ELEVATION -> {
                rectOf(specimen.strip, box)
                highlight.target(Highlight.Kind.BAR, box, box.height() / 2)
            }
        }
        if (t !== described) {
            described = t
            val swatch = if (h.zone == Zone.COLOR) h.role?.let { themes.theme.colors(it).fill } else null
            tip.describe(h.zone.title(h), h.zone.value(t, h), h.zone.hint, swatch)
        }
    }

    private fun ring(v: View, pad: Float, radius: Float) {
        rectOf(v, box)
        val r = min(radius, min(box.width(), box.height()) / 2)
        box.inset(-pad, -pad)
        highlight.target(Highlight.Kind.RING, box, r + pad)
    }

    private fun buttonMark(h: Hit, t: Tokens) {
        val v = h.view
        val radius = dp(t.radiusButton)
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
            Zone.BUTTON_RADIUS -> cornerDot(v, radius, h)
            else -> ring(v, dp(4f), radius)
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

    private fun openEditor(zone: Zone) {
        editing = zone
        val t = themes.tokens
        editor.open(zone.title, if (zone == Zone.DATE) t.clockDate else t.clockTime)
        // with the keyboard up the clock (top of the card) must stay in view
        (specimen.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.TOP
        specimen.requestLayout()
    }

    private fun closeEditor() {
        if (!editor.isOpen) return
        editor.close()
        editing = null
        (specimen.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
        specimen.requestLayout()
        commit()
    }

    private fun onFormat(pattern: String) {
        val t = themes.tokens
        themes.set(if (editing == Zone.DATE) t.copy(clockDate = pattern) else t.copy(clockTime = pattern))
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

    private fun copy() {
        val clip = ClipData.newPlainText("min tokens", themes.tokens.toJson())
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    private fun syncTools() {
        for ((v, on) in listOf(undo to history.canUndo, redo to history.canRedo)) {
            v.isEnabled = on
            v.alpha = if (on) 1f else DISABLED_ALPHA
        }
    }

    private companion object {
        const val STUDIO_MAX_WIDTH = 420f
        const val CORNER_REACH = 28f      // dp around a card corner that grabs the corner
        const val EDGE_REACH = 12f        // dp inside a button edge that grabs the padding
        const val STRIP_REACH_X = 24f
        const val STRIP_REACH_Y = 16f
        const val DISABLED_ALPHA = 0.3f
        val HALF_SQRT2 = sqrt(0.5f)
    }
}
