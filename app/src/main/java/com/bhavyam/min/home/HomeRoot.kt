package com.bhavyam.min.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import com.bhavyam.min.App
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.search.Launcher
import com.bhavyam.min.settings.SettingsHost
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.settings.SettingsView
import com.bhavyam.min.studio.Scene
import com.bhavyam.min.studio.StudioLayer
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.SearchLayer
import com.bhavyam.min.ui.dp
import kotlin.math.abs

/**
 * The launcher's single window: home and search stacked, settings over them, the long-press
 * popup above, and the studio — a clear editing layer over the real screens — on top. It owns the vertical gesture between home and search — a drag
 * follows the finger exactly, a release hands its speed to the spring, and a moving spring can
 * be caught mid-flight — and the blur-to-focus as the launcher leaves and returns.
 */
@SuppressLint("ViewConstructor")
class HomeRoot(
    context: Context,
    private val launcher: Launcher,
    host: SettingsHost,
    onInfo: (AppEntry) -> Unit,
) : FrameLayout(context), ThemeStore.Listener {

    private val app = App.of(context)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val popup = ContextPopup(context, onInfo)
    private val home = HomeLayer(
        context,
        onLaunch = { entry, v -> launcher.launchApp(entry, v) },
        onHold = popup::show,
        onClockHold = ::openSettings,
    )
    private val search = SearchLayer(context, onRun = { item, v -> item.run(launcher, v) }, onHold = popup::show)
    private val settings = SettingsView(context, host)
    private val studio = StudioLayer(context, home, search, popup, ::showScene)

    /** 0 = home, 1 = search. Overshoot past either end is the bounce. */
    private val progress = Spring(0.001f, ::render)
    private val focus = Spring(0.001f, ::applyFocus)
    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragFromY = 0f
    private var dragFromProgress = 0f
    private var dragging = false

    /** Runs once search has fully slid away (the studio's menu scene needs home in place). */
    private var atHome: (() -> Unit)? = null

    init {
        addView(home, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(settings, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(popup, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(studio, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        search.isClickable = true // empty space in search must not reach home underneath
        progress.onRest = {
            if (progress.value == 0f) {
                search.setQuery("")
                atHome?.invoke()
                atHome = null
            }
        }
        progress.snapTo(0f)
        focus.snapTo(1f)
    }

    val searchOpen get() = progress.target == 1f
    val settingsOpen get() = settings.isOpen

    /* ------------------------------------------------------------ layers */

    private fun render(p: Float) {
        val travel = dp(Globals.SEARCH_TRAVEL)
        val shown = p.coerceIn(0f, 1f)
        home.alpha = 1f - shown
        home.translationY = p * travel
        home.visibility = if (shown >= 1f) INVISIBLE else VISIBLE
        search.alpha = shown
        search.translationY = (p - 1f) * travel
        search.visibility = if (shown <= 0f) INVISIBLE else VISIBLE
    }

    fun openSearch(query: String) {
        closeOverlays()
        setSearch(open = true, velocity = 0f)
        if (query.isNotEmpty()) search.setQuery(query)
    }

    fun closeSearch() = setSearch(open = false, velocity = 0f)

    private fun setSearch(open: Boolean, velocity: Float) {
        val ime = search.windowInsetsController
        if (open) {
            search.visibility = VISIBLE
            search.input.requestFocus()
            ime?.show(WindowInsets.Type.ime())
        } else {
            search.input.clearFocus()
            ime?.hide(WindowInsets.Type.ime())
        }
        progress.animateTo(if (open) 1f else 0f, app.motion[SettingsRegistry.SPRING_SEARCH], velocity)
    }

    fun openSettings() {
        popup.dismiss()
        settings.open()
    }

    fun closeSettings() = settings.close()

    /** Customisation from settings: everything else steps aside and the real home is edited. */
    fun openStudio() {
        closeOverlays()
        if (searchOpen) closeSearch()
        studio.open()
    }

    /**
     * Shows a part of the launcher for the studio to edit, without the keyboard: home, search
     * listing every app, or the long-press menu sprung from the first favourite.
     */
    private fun showScene(scene: Scene) {
        popup.hide()
        atHome = null
        val spring = app.motion[SettingsRegistry.SPRING_SEARCH]
        when (scene) {
            Scene.SEARCH -> {
                search.setQuery("")
                search.visibility = VISIBLE
                progress.animateTo(1f, spring)
            }
            Scene.HOME -> progress.animateTo(0f, spring)
            Scene.MENU -> if (progress.value == 0f && !progress.isRunning) showMenuSpecimen() else {
                atHome = ::showMenuSpecimen
                progress.animateTo(0f, spring)
            }
        }
    }

    private fun showMenuSpecimen() {
        val row = home.rows().firstOrNull()
        if (row != null) popup.show(row.tag as AppEntry, row)
        else app.apps.apps.firstOrNull()?.let { popup.show(it, home.clock) }
    }

    /** Re-reads live settings rows after the launcher regains the screen (permission dialogs). */
    fun refreshSettings() = settings.refresh()

    private fun closeOverlays() {
        popup.dismiss()
        if (studio.isOpen) studio.close()
        if (settings.isOpen) settings.close()
    }

    /** Back steps out of whatever is on top. False when already at a clean home. */
    fun back(): Boolean = when {
        studio.isOpen -> { studio.back(); true }
        popup.isShowing -> { popup.dismiss(); true }
        settings.isOpen -> { if (!settings.back()) settings.close(); true }
        searchOpen -> { closeSearch(); true }
        else -> false
    }

    /** Home pressed while already home: everything slides away, animated. */
    fun goHome() {
        closeOverlays()
        if (searchOpen) closeSearch()
    }

    /** A clean home while nobody is looking: no search, no query, no popup, no overlays. */
    fun reset() {
        popup.hide()
        studio.closeNow()
        settings.closeNow()
        search.input.clearFocus()
        search.windowInsetsController?.hide(WindowInsets.Type.ime())
        search.setQuery("")
        progress.snapTo(0f)
    }

    /* ------------------------------------------------------------ blur to focus */

    /** The launcher sinks out of focus (an app is opening, or the screen went off). */
    fun blur(animate: Boolean) {
        if (animate) focus.animateTo(0f, app.motion[SettingsRegistry.SPRING_FOCUS]) else focus.snapTo(0f)
    }

    /** …and sharpens back as it returns. */
    fun sharpen() = focus.animateTo(1f, app.motion[SettingsRegistry.SPRING_FOCUS])

    private fun applyFocus(p: Float) {
        val off = if (app.prefs[SettingsRegistry.BLUR]) (1f - p).coerceIn(0f, 1f) else 0f
        val radius = dp(app.prefs[SettingsRegistry.BLUR_RADIUS]) * off
        setRenderEffect(if (radius < MIN_BLUR) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        val s = 1f + (Globals.FOCUS_SCALE - 1f) * off
        scaleX = s
        scaleY = s
    }

    /* ------------------------------------------------------------ gesture */

    private val gestureAllowed get() = !popup.isShowing && !settings.isOpen && !studio.isOpen

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (!gestureAllowed) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
                if (progress.isRunning) startDrag(e) // catch it mid-flight
            }
            MotionEvent.ACTION_MOVE -> if (!dragging) {
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dy) > slop && abs(dy) > abs(dx) * AXIS_BIAS) {
                    val opening = !searchOpen && dy > 0 && !home.canScrollUp()
                    val closing = searchOpen && dy < 0 && !search.canScrollDown()
                    if (opening || closing) startDrag(e)
                }
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility") // the gesture is also reachable via back and adb
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(e)
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val raw = dragFromProgress + (e.y - dragFromY) / dp(Globals.SEARCH_DRAG)
                progress.snapTo(rubberBand(raw))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = tracker
                t?.computeCurrentVelocity(1000)
                val v = (t?.yVelocity ?: 0f) / dp(Globals.SEARCH_DRAG)
                t?.recycle()
                tracker = null
                dragging = false
                val open = if (abs(v) > FLING) v > 0 else progress.value > 0.5f
                setSearch(open, v)
            }
        }
        return true
    }

    private fun startDrag(e: MotionEvent) {
        dragging = true
        dragFromY = e.y
        dragFromProgress = progress.value
        progress.stop()
        search.visibility = VISIBLE
        tracker = VelocityTracker.obtain().also { it.addMovement(e) }
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** Past either end the drag meets resistance instead of a wall. */
    private fun rubberBand(p: Float) = when {
        p > 1f -> 1f + (p - 1f) * RESISTANCE
        p < 0f -> p * RESISTANCE
        else -> p
    }

    /* ------------------------------------------------------------ theme */

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        setBackgroundColor(theme.bg)
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        windowInsetsController?.setSystemBarsAppearance(if (theme.dark) 0 else light, light)
    }

    private companion object {
        const val AXIS_BIAS = 1.2f     // vertical must beat horizontal by this much (pager swipes)
        const val FLING = 1.2f         // progress per second that counts as a throw
        const val RESISTANCE = 0.3f
        const val MIN_BLUR = 0.5f      // px; below this the effect is skipped entirely
    }
}
