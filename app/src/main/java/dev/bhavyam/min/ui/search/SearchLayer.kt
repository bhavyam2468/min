package dev.bhavyam.min.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.bhavyam.min.Globals
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.motion.rubberBand
import dev.bhavyam.min.search.SearchEngine
import dev.bhavyam.min.search.SearchResult
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.KeyboardInsets
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import kotlin.math.abs

/**
 * The search layer: category pages above a bottom-anchored input, pulled down from home on a
 * spring and glued to the keyboard. It arbitrates its own gestures — horizontal drags page between
 * categories, an upward drag at the bottom of a list pushes the layer back up.
 */
@SuppressLint("ViewConstructor")
class SearchLayer(
    context: Context,
    private val settings: SettingsStore,
    private val engine: SearchEngine,
    private val host: Host,
) : FrameLayout(context), Themed {

    interface Host {
        fun run(result: SearchResult, source: View)
        fun showAppMenu(app: AppEntry, anchor: View)

        /** 0 = closed, 1 = open; overshoots while the spring bounces. */
        fun onSearchProgress(progress: Float)
    }

    private val pager = ColumnPager(context, settings)
    private val columns = engine.columns.map { column ->
        ResultColumn(
            context, column.provider.label, settings,
            onRun = { result, view -> host.run(result, view) },
            onLongPress = { result, view -> (result as? AppEntry)?.let { host.showAppMenu(it, view) } != null },
        )
    }
    private val input = EditText(context).apply {
        hint = "search"
        gravity = Gravity.CENTER
        background = null
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_FILTER
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(pager, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private val spring = Spring(PRECISION, ::applyProgress)
    private val travel get() = height * Globals.Motion.SEARCH_TRAVEL

    /** Where the layer is headed (it may still be animating). */
    var isOpen = false
        private set

    val query: String get() = input.text.toString()

    private enum class Drag { UNDECIDED, PAGE, DISMISS, NONE }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var drag = Drag.NONE
    private var downX = 0f
    private var downY = 0f
    private var velocity: VelocityTracker? = null

    init {
        visibility = INVISIBLE
        columns.forEach { pager.addView(it) }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        KeyboardInsets(this, content).attach()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = refresh()
        })
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (enter) currentColumn()?.top?.let { host.run(it, input) }
            enter
        }
    }

    /** Re-run the query and rebind every page (also after apps, contacts or providers change). */
    fun refresh() {
        engine.search(input.text)
        engine.columns.forEachIndexed { i, column ->
            val page = columns[i]
            page.visibility = if (column.provider.isAvailable) VISIBLE else GONE
            page.bind(column.results)
        }
        pager.requestLayout()
    }

    // ---------------------------------------------------------------- open / close

    /** Follow a pull from home: [distance] px dragged down since the gesture began. */
    fun dragOpen(distance: Float) {
        if (visibility != VISIBLE) prepare()
        spring.snapTo(resisted(distance / travel))
    }

    /** End a pull from home with vertical [velocity] (px/s, positive downwards). */
    fun releaseOpen(velocity: Float) {
        val fling = Globals.Motion.FLING_VELOCITY * resources.displayMetrics.density
        val open = velocity > fling || (velocity > -fling && spring.value > Globals.Motion.COMMIT_FRACTION)
        if (open) open(null, velocity / travel) else close(velocity / travel)
    }

    /** Open (optionally replacing the query), focus the input and raise the keyboard. */
    fun open(query: String?, velocity: Float = 0f) {
        if (visibility != VISIBLE) prepare()
        if (query != null) {
            input.setText(query)
            input.setSelection(query.length)
        }
        isOpen = true
        input.requestFocus()
        input.windowInsetsController?.show(WindowInsets.Type.ime())
        spring.animateTo(1f, settings.spring(SettingsRegistry.SEARCH_MOTION), velocity)
    }

    fun close(velocity: Float = 0f) {
        isOpen = false
        input.windowInsetsController?.hide(WindowInsets.Type.ime())
        input.clearFocus()
        spring.animateTo(0f, settings.spring(SettingsRegistry.SEARCH_MOTION), velocity)
    }

    /** Back to a clean, closed state with no animation (the launcher went to the background). */
    fun reset() {
        isOpen = false
        input.windowInsetsController?.hide(WindowInsets.Type.ime())
        input.clearFocus()
        spring.snapTo(0f)
    }

    /** A finger-driven progress: free between closed and open, rubber-banded past open. */
    private fun resisted(p: Float) =
        if (p <= 1f) p.coerceAtLeast(0f) else 1f + rubberBand(p - 1f, 1f, Globals.Motion.RUBBER_BAND)

    private fun prepare() {
        visibility = VISIBLE
        pager.show(0, animate = false)
        refresh()
    }

    private fun applyProgress(p: Float) {
        translationY = (p - 1f) * travel
        content.alpha = p.coerceIn(0f, 1f)
        host.onSearchProgress(p)
        if (p <= 0f && !isOpen) {
            visibility = INVISIBLE
            if (input.length() > 0) input.text.clear()
        }
    }

    private fun currentColumn() = pager.page(pager.current) as? ResultColumn

    // ---------------------------------------------------------------- gestures

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().apply { addMovement(e) }
                drag = Drag.UNDECIDED
                if (pager.isSettling) startDrag(Drag.PAGE) // catch the pages mid-flight
            }
            MotionEvent.ACTION_MOVE -> if (drag == Drag.UNDECIDED) {
                velocity?.addMovement(e)
                decide(e)
            }
        }
        return drag == Drag.PAGE || drag == Drag.DISMISS
    }

    /** Pick the gesture once the finger has clearly moved. */
    private fun decide(e: MotionEvent) {
        val dx = e.x - downX
        val dy = e.y - downY
        when {
            abs(dx) > touchSlop && abs(dx) > abs(dy) -> startDrag(Drag.PAGE)
            dy < -touchSlop && abs(dy) > abs(dx) && currentColumn()?.canScrollDown() != true -> startDrag(Drag.DISMISS)
            abs(dy) > touchSlop -> drag = Drag.NONE
        }
        if (drag == Drag.PAGE || drag == Drag.DISMISS) {
            downX = e.x
            downY = e.y
        }
    }

    private fun startDrag(kind: Drag) {
        drag = kind
        if (kind == Drag.PAGE) pager.beginDrag() else spring.stop()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        velocity?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> when (drag) {
                Drag.UNDECIDED -> decide(e) // touches that no child took arrive here directly
                Drag.PAGE -> pager.dragBy(e.x - downX)
                Drag.DISMISS -> spring.snapTo(resisted(1f + (e.y - downY) / travel))
                else -> Unit
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocity
                tracker?.computeCurrentVelocity(1000)
                val up = e.actionMasked == MotionEvent.ACTION_UP
                when (drag) {
                    Drag.PAGE -> pager.release(if (up && tracker != null) tracker.xVelocity else 0f)
                    Drag.DISMISS -> releaseDismiss(if (up && tracker != null) tracker.yVelocity else 0f)
                    else -> Unit
                }
                drag = Drag.NONE
                tracker?.recycle()
                velocity = null
            }
        }
        return true
    }

    private fun releaseDismiss(velocity: Float) {
        val fling = Globals.Motion.FLING_VELOCITY * resources.displayMetrics.density
        val dismiss = velocity < -fling || (velocity < fling && spring.value < 1f - Globals.Motion.COMMIT_FRACTION)
        if (dismiss) close(velocity / travel) else open(null, velocity / travel)
    }

    // ---------------------------------------------------------------- theme

    override fun applyTheme(theme: Theme) {
        columns.forEach { it.applyTheme(theme) }
        input.style(theme, Globals.Type.INPUT)
        input.setHintTextColor(theme.muted)
        input.highlightColor = Theme.withAlpha(theme.text, Globals.Alpha.SCRIM)
        input.textCursorDrawable = GradientDrawable().apply {
            setColor(theme.text)
            setSize(theme.dp(CURSOR_WIDTH).toInt(), 0)
        }
        val padX = theme.em(theme.tokens.padX).toInt()
        input.setPadding(padX, theme.em(theme.tokens.padY).toInt(), padX, theme.dp(Globals.Space.INPUT_BOTTOM).toInt())
    }

    private companion object {
        const val PRECISION = 0.001f
        const val CURSOR_WIDTH = 2f
    }
}
