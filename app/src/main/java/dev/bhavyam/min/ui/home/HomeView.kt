package dev.bhavyam.min.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import dev.bhavyam.min.Globals
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Anchor
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.Tokens
import dev.bhavyam.min.ui.common.Themed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Home: the clock and the favorite apps, each at its anchor, on the background colour.
 * A downward drag anywhere is handed to [Host] to pull the search layer down.
 *
 * The home editor works on this view directly: it reads where the blocks are ([bounds],
 * [candidate]), moves them ([motion]) and makes room for its own bars ([setEditRoom]).
 */
@SuppressLint("ViewConstructor")
class HomeView(context: Context, settings: SettingsStore, private val host: Host) : ViewGroup(context), Themed {

    interface Host {
        fun launch(app: AppEntry, source: View)
        fun showAppMenu(app: AppEntry, anchor: View)
        fun openSettings()
        fun dragSearch(distance: Float)
        fun releaseSearch(velocity: Float)
    }

    private val clock = ClockView(context).apply {
        setOnLongClickListener { host.openSettings(); true }
    }
    private val apps = AppsView(
        context, settings,
        onClick = { host.launch(it.tag as AppEntry, it) },
        onLongClick = { host.showAppMenu(it.tag as AppEntry, it); true },
    )

    /** Called on every frame a block moves or the layout changes (the editor redraws its outlines). */
    var onBlocksMoved: (() -> Unit)? = null

    private val clockMotion = BlockMotion(clock, settings) { onBlocksMoved?.invoke() }
    private val appsMotion = BlockMotion(apps, settings) { onBlocksMoved?.invoke() }

    private var tokens: Tokens? = null
    private var placedClock: Anchor? = null
    private var placedApps: Anchor? = null

    private val box = RectF()
    private val clockSize = Placement.Size()
    private val appsSize = Placement.Size()
    private val clockRect = RectF()
    private val appsRect = RectF()
    private val scratch = RectF()

    private var roomTop = 0
    private var roomBottom = 0
    private var room = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null

    init {
        addView(clock)
        addView(apps)
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val edge = (Globals.Space.EDGE * resources.displayMetrics.density).toInt()
            val bottom = (Globals.Space.FAVORITES_BOTTOM * resources.displayMetrics.density).toInt()
            setPadding(bars.left + edge, bars.top + edge, bars.right + edge, bars.bottom + bottom)
            insets
        }
    }

    fun setFavorites(list: List<AppEntry>) = apps.setApps(list)

    override fun applyTheme(theme: Theme) {
        tokens = theme.tokens
        clock.applyTheme(theme)
        apps.applyTheme(theme)
        requestLayout()
    }

    fun motion(block: Block) = if (block == Block.Clock) clockMotion else appsMotion

    private fun view(block: Block): View = if (block == Block.Clock) clock else apps

    /** Where [block] is drawn right now (moves and lifts included), in this view's coordinates. */
    fun bounds(block: Block, out: RectF) {
        val v = view(block)
        out.set(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
        out.offset(v.translationX, v.translationY)
    }

    /** Where [block] is laid out, ignoring any motion. */
    fun layoutBounds(block: Block, out: RectF) {
        val v = view(block)
        out.set(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
    }

    /** Where [block] would be laid out if it were anchored at [anchor]. */
    fun candidate(block: Block, anchor: Anchor, out: RectF) {
        val t = tokens ?: return
        if (block == Block.Clock) place(anchor, t.appAnchor, out, scratch)
        else place(t.clockAnchor, anchor, scratch, out)
    }

    /**
     * The editor's bars take [top] px from the top of the screen and [bottom] px from the bottom;
     * [progress] (0..1) is how far home has made room for them.
     */
    fun setEditRoom(top: Int, bottom: Int, progress: Float) {
        if (top == roomTop && bottom == roomBottom && progress == room) return
        roomTop = top
        roomBottom = bottom
        room = progress
        requestLayout()
    }

    /** Back to rest: no moves, no lifts (the launcher went to the background). */
    fun resetMotion() {
        clockMotion.reset()
        appsMotion.reset()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val innerW = w - paddingLeft - paddingRight
        val innerH = h - paddingTop - paddingBottom
        clock.measure(atMost(innerW), atMost(innerH))
        apps.measure(atMost(innerW + (apps.inset * 2).toInt()), atMost(innerH))
    }

    private fun atMost(size: Int) = MeasureSpec.makeMeasureSpec(max(0, size), MeasureSpec.AT_MOST)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val tokens = tokens ?: return
        place(tokens.clockAnchor, tokens.appAnchor, clockRect, appsRect)
        // A new anchor glides there from where the block was seen; other changes (size, style) just apply.
        val moveClock = placedClock != null && placedClock != tokens.clockAnchor
        val moveApps = placedApps != null && placedApps != tokens.appAnchor
        lay(clock, clockRect, if (moveClock) clockMotion else null)
        lay(apps, appsRect, if (moveApps) appsMotion else null)
        placedClock = tokens.clockAnchor
        placedApps = tokens.appAnchor
        onBlocksMoved?.invoke()
    }

    private fun lay(v: View, r: RectF, flip: BlockMotion?) {
        val left = r.left.roundToInt()
        val top = r.top.roundToInt()
        flip?.flip((left - v.left).toFloat(), (top - v.top).toFloat())
        v.layout(left, top, left + v.measuredWidth, top + v.measuredHeight)
    }

    private fun place(clockAt: Anchor, appsAt: Anchor, clockOut: RectF, appsOut: RectF) {
        val edge = Globals.Space.EDGE * resources.displayMetrics.density
        val top = paddingTop + max(0f, roomTop + edge - paddingTop) * room
        val bottom = paddingBottom + max(0f, roomBottom + edge - paddingBottom) * room
        box.set(paddingLeft.toFloat(), top, (width - paddingRight).toFloat(), height - bottom)
        clockSize.width = clock.measuredWidth.toFloat()
        clockSize.height = clock.measuredHeight.toFloat()
        appsSize.width = apps.measuredWidth.toFloat()
        appsSize.height = apps.measuredHeight.toFloat()
        appsSize.inset = apps.inset
        val gap = Globals.Space.CLOCK_GAP * resources.displayMetrics.density
        Placement.place(box, gap, clockSize, clockAt, appsSize, appsAt, clockOut, appsOut)
    }

    // ------------------------------------------------------------------ swipe down for search

    /** Shared by intercept and touch: decides when a press becomes a downward drag. */
    private fun track(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain().apply { addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val dy = e.y - downY
                if (!dragging && dy > touchSlop && dy > abs(e.x - downX)) {
                    dragging = true
                    downY = e.y
                }
            }
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                track(e)
                if (dragging) host.dragSearch(e.y - downY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocity
                if (dragging && tracker != null) {
                    tracker.computeCurrentVelocity(MILLIS_PER_SECOND)
                    host.releaseSearch(if (e.actionMasked == MotionEvent.ACTION_UP) tracker.yVelocity else 0f)
                }
                dragging = false
                tracker?.recycle()
                velocity = null
            }
        }
        return true
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000
    }
}
