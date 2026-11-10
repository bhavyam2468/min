package com.bhavyam.min.home

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.bhavyam.min.App
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.observe
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.SpringList
import com.bhavyam.min.ui.dpi
import com.bhavyam.min.ui.safe

/**
 * The home screen: the clock in the top-left corner and the favourites, vertically centred
 * on the left. Inside the safe area on every edge, in any orientation.
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
    private val scroll = ScrollView(context)
    private val list = LinearLayout(context)
    private val favorites = SpringList(
        container = list,
        key = AppEntry::id,
        create = ::newRow,
        bind = { v, entry -> (v as RoleButton).text = entry.label; v.tag = entry },
    )
    private var bound = false

    init {
        clock.setOnLongClickListener { onClockHold(); true }
        list.orientation = LinearLayout.VERTICAL
        list.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.orientation = LinearLayout.VERTICAL
        column.addView(clock, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dpi(Globals.EDGE)
        })
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        observe(app.favorites.changed, ::bindFavorites)
        bindFavorites()
    }

    /** Swipe-down to search only starts when the favourites aren't scrolled. */
    fun canScrollUp() = scroll.canScrollVertically(-1)

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
        val s = insets.safe()
        val edge = dpi(Globals.EDGE)
        column.setPadding(s.left, s.top + edge, s.right + edge, s.bottom + edge)
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) = alignList(theme)

    /** Pills hang into the margin so the favourites' text lines up with the clock. */
    private fun alignList(theme: Theme) {
        list.setPaddingRelative((dpi(Globals.EDGE) - dpi(theme.tokens.padX)).coerceAtLeast(0), 0, 0, 0)
    }
}
