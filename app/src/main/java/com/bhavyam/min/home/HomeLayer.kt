package com.bhavyam.min.home

import android.content.Context
import android.graphics.Insets
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.observe
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Anchor
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.ShadowScroll
import com.bhavyam.min.ui.SpringList
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi
import com.bhavyam.min.ui.safe
import kotlin.math.hypot

/**
 * The home screen: the clock in the top-left corner and the favourites, placed at one of nine
 * spots (left/centre/right × top/centre/bottom) by the list tokens. Inside the safe area on
 * every edge, in any orientation.
 *
 * The studio can pick the list up and drag it: it is pulled towards each spot as it passes
 * (magnetic), and wherever it lands — or whenever the spot changes, by undo or reset too —
 * it glides from where it was to where it now belongs.
 */
class HomeLayer(
    context: Context,
    private val onLaunch: (AppEntry, View) -> Unit,
    private val onHold: (AppEntry, View) -> Unit,
    onClockHold: () -> Unit,
) : FrameLayout(context), ThemeStore.Listener {

    private val app = App.of(context)
    val clock = ClockView(context)
    private val column = LinearLayout(context)
    private val scroll = ShadowScroll(context)

    /** The favourites column; its padding box is the area the nine spots divide. */
    val list = LinearLayout(context)

    private val favorites = SpringList(
        container = list,
        key = AppEntry::id,
        create = ::newRow,
        bind = { v, entry -> (v as RoleButton).text = entry.label; v.tag = entry },
    )
    private var bound = false
    private var anchorX = Globals.TOKENS.listX
    private var anchorY = Globals.TOKENS.listY
    private val tx = Spring(0.5f) { list.translationX = it }
    private val ty = Spring(0.5f) { list.translationY = it }
    private val block = RectF()
    private val spot = RectF()
    private var moveFromX = 0f
    private var moveFromY = 0f
    private var safe = Insets.NONE

    /** Extra room kept free at the bottom, e.g. under the studio's toolbar. */
    var bottomInset = 0
        set(v) {
            if (field == v) return
            field = v
            glide(animate = true, ::pad)
        }

    init {
        clock.setOnLongClickListener { onClockHold(); true }
        list.orientation = LinearLayout.VERTICAL
        scroll.isFillViewport = true
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.orientation = LinearLayout.VERTICAL
        column.addView(clock, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dpi(Globals.EDGE)
        })
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        observe(app.favorites.changed, ::bindFavorites)
        bindFavorites()
        applyAnchors(animate = false)
    }

    /** Swipe-down to search only starts when the favourites aren't scrolled. */
    fun canScrollUp() = scroll.canScrollVertically(-1)

    /** The favourite rows, top to bottom. */
    fun rows(): List<RoleButton> = (0 until list.childCount).map { list.getChildAt(it) as RoleButton }

    private fun bindFavorites() {
        favorites.submit(app.favorites.apps(), animate = bound)
        bound = true
    }

    private fun newRow() = RoleButton(context, Role.HOME, Globals.TEXT_FAVORITE).apply {
        setOnClickListener { v -> onLaunch(v.tag as AppEntry, v) }
        setOnLongClickListener { v -> onHold(v.tag as AppEntry, v); true }
        layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpi(Globals.LIST_GAP)
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safe = insets.safe()
        pad()
        return insets
    }

    private fun pad() {
        val edge = dpi(Globals.EDGE)
        column.setPadding(safe.left, safe.top + edge, safe.right, safe.bottom + edge + bottomInset)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        // pills hang into the margin so the favourites' text lines up with the clock's edge
        val hang = (dpi(Globals.EDGE) - dpi(theme.tokens.padX)).coerceAtLeast(0)
        list.setPadding(hang, 0, hang, 0)
        val t = theme.tokens
        if (t.listX != anchorX || t.listY != anchorY) {
            anchorX = t.listX
            anchorY = t.listY
            applyAnchors(animate = true)
        }
    }

    /* ------------------------------------------------------------ the nine spots */

    private fun gravity(a: Anchor, start: Int, center: Int, end: Int) = when (a) {
        Anchor.START -> start
        Anchor.CENTER -> center
        Anchor.END -> end
    }

    private fun applyAnchors(animate: Boolean) = glide(animate) {
        list.gravity = gravity(anchorX, Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END) or
            gravity(anchorY, Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM)
    }

    /**
     * Applies a [change] that moves the rows; when [animate], they glide from where they were
     * drawn to where the change puts them (FLIP: first, last, invert, play).
     */
    private fun glide(animate: Boolean, change: () -> Unit) {
        val glide = animate && list.isLaidOut && measureBlock()
        val fromX = block.left + list.translationX
        val fromY = block.top + list.translationY
        change()
        if (!glide) return
        list.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                list.viewTreeObserver.removeOnPreDrawListener(this)
                if (!measureBlock()) return true
                tx.snapTo(fromX - block.left)
                ty.snapTo(fromY - block.top)
                settle()
                return true
            }
        })
    }

    private fun settle() {
        tx.animateTo(0f, app.motion[SettingsRegistry.SPRING_LIST])
        ty.animateTo(0f, app.motion[SettingsRegistry.SPRING_LIST])
    }

    /** The rows' bounds in the list's own coordinates (translation not included). */
    private fun measureBlock(): Boolean {
        if (list.childCount == 0) return false
        block.setEmpty()
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            block.union(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
        }
        return true
    }

    /** Where the rows' bounds would be at spot ([x], [y]), in list coordinates. */
    fun spotRect(x: Anchor, y: Anchor, out: RectF) {
        measureBlock()
        val w = block.width()
        val h = block.height()
        val ltrX = if (layoutDirection == LAYOUT_DIRECTION_RTL) Anchor.entries[2 - x.ordinal] else x
        val left = when (ltrX) {
            Anchor.START -> list.paddingLeft.toFloat()
            Anchor.CENTER -> list.paddingLeft + (list.width - list.paddingLeft - list.paddingRight - w) / 2f
            Anchor.END -> list.width - list.paddingRight - w
        }
        val top = when (y) {
            Anchor.START -> list.paddingTop.toFloat()
            Anchor.CENTER -> (list.height - h) / 2f
            Anchor.END -> list.height - list.paddingBottom - h
        }
        out.set(left, top, left + w, top + h)
    }

    /** Picks the list up where it is drawn now. */
    fun beginMove() {
        tx.stop()
        ty.stop()
        moveFromX = list.translationX
        moveFromY = list.translationY
    }

    /**
     * Follows the finger by ([dx], [dy]); near a spot the list is pulled onto it. Returns the
     * spot it is held by, or null while it floats freely.
     */
    fun moveBy(dx: Float, dy: Float): Pair<Anchor, Anchor>? {
        measureBlock()
        val x = block.left + moveFromX + dx
        val y = block.top + moveFromY + dy
        val near = nearest(x, y)
        spotRect(near.first, near.second, spot)
        val held = hypot(spot.left - x, spot.top - y) < dp(MAGNET_RADIUS)
        val pull = if (held) MAGNET_PULL else 0f
        list.translationX = x + (spot.left - x) * pull - block.left
        list.translationY = y + (spot.top - y) * pull - block.top
        return if (held) near else null
    }

    /** Lets go: returns the spot nearest to where the list is. Same spot → it glides home. */
    fun endMove(): Pair<Anchor, Anchor> {
        measureBlock()
        val near = nearest(block.left + list.translationX, block.top + list.translationY)
        tx.snapTo(list.translationX)
        ty.snapTo(list.translationY)
        if (near.first == anchorX && near.second == anchorY) settle()
        return near
    }

    private fun nearest(x: Float, y: Float): Pair<Anchor, Anchor> {
        var best = anchorX to anchorY
        var bestDistance = Float.MAX_VALUE
        for (ax in Anchor.entries) for (ay in Anchor.entries) {
            spotRect(ax, ay, spot)
            val d = hypot(spot.left - x, spot.top - y)
            if (d < bestDistance) {
                bestDistance = d
                best = ax to ay
            }
        }
        return best
    }

    private companion object {
        const val MAGNET_RADIUS = 56f   // dp from a spot at which it starts pulling
        const val MAGNET_PULL = 0.7f    // how much of the remaining distance the pull closes
    }
}
