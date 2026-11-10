package dev.bhavyam.min.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import kotlin.math.abs

/**
 * Home: the date and time top-left, favorites bottom-left, black everywhere else.
 * A downward drag anywhere is handed to [Host] to pull the search layer down.
 */
@SuppressLint("ViewConstructor")
class HomeView(context: Context, private val settings: SettingsStore, private val host: Host) : FrameLayout(context), Themed {

    interface Host {
        fun launch(app: AppEntry, source: View)
        fun showAppMenu(app: AppEntry, anchor: View)
        fun openSettings()
        fun dragSearch(distance: Float)
        fun releaseSearch(velocity: Float)
    }

    private val date = TextClock(context).apply {
        format12Hour = DATE_FORMAT
        format24Hour = DATE_FORMAT
    }
    private val time = TextClock(context).apply {
        format12Hour = "h:mm"
        format24Hour = "H:mm"
    }
    private val clock = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(date)
        addView(time)
        setOnLongClickListener { host.openSettings(); true }
    }
    private val favorites = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var theme: Theme? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null

    private val onFavoriteClick = OnClickListener { host.launch(it.tag as AppEntry, it) }
    private val onFavoriteLongClick = OnLongClickListener { host.showAppMenu(it.tag as AppEntry, it); true }

    init {
        addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        addView(favorites, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val edge = (Globals.Space.EDGE * resources.displayMetrics.density).toInt()
            val bottom = (Globals.Space.FAVORITES_BOTTOM * resources.displayMetrics.density).toInt()
            setPadding(bars.left + edge, bars.top + edge, bars.right + edge, bars.bottom + bottom)
            insets
        }
    }

    fun setFavorites(apps: List<AppEntry>) {
        while (favorites.childCount > apps.size) favorites.removeViewAt(favorites.childCount - 1)
        while (favorites.childCount < apps.size) favorites.addView(newFavoriteRow())
        apps.forEachIndexed { i, app ->
            val row = favorites.getChildAt(i) as TextView
            row.text = app.label
            row.tag = app
        }
        theme?.let(::applyTheme)
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        date.style(theme, Globals.Type.CLOCK)
        time.style(theme, Globals.Type.CLOCK)
        (time.layoutParams as LinearLayout.LayoutParams).marginStart = theme.dp(Globals.Space.CLOCK_GAP).toInt()
        time.requestLayout()
        val padX = theme.em(theme.tokens.padX * ROW_PAD_X).toInt()
        val padY = theme.em(theme.tokens.padY * ROW_PAD_Y).toInt()
        for (i in 0 until favorites.childCount) {
            val row = favorites.getChildAt(i) as TextView
            row.style(theme, Globals.Type.FAVORITE)
            row.setPadding(padX, padY, padX, padY)
            (row.layoutParams as MarginLayoutParams).marginStart = -padX // text, not the highlight, aligns with the clock
            (row.background as PressDrawable).apply { color = theme.pressed; radius = theme.radiusButton }
        }
    }

    private fun newFavoriteRow() = TextView(context).apply {
        background = PressDrawable(settings)
        isSingleLine = true
        setOnClickListener(onFavoriteClick)
        setOnLongClickListener(onFavoriteLongClick)
        layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

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
                    tracker.computeCurrentVelocity(1000)
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
        const val DATE_FORMAT = "EEE, d MMMM"
        const val ROW_PAD_X = 0.45f
        const val ROW_PAD_Y = 0.42f
    }
}
