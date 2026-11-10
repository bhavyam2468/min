package com.bhavyam.min.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.search.SearchItem
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role

/**
 * One category page, stacked from the bottom up:
 *
 *     ┌──────────────┐
 *     │   …worse     │  ScrollView (fills the viewport)
 *     │   better     │   └ list, gravity = bottom: rows sit on the floor and grow upward
 *     │   best       │
 *     │    APPS      │  label, always directly on top of the input below the pager
 *     └──────────────┘
 *
 * No offsets are computed: the bottom gravity inside a viewport-filling scroll view is what
 * puts the best match next to the input. Rows are recycled across keystrokes.
 */
class ResultsPage(
    context: Context,
    label: String,
    private val onRun: (SearchItem, View) -> Unit,
    private val onHold: (AppEntry, View) -> Unit,
) : LinearLayout(context) {

    private val scroll = AnchoredScroll(context)
    private val list = LinearLayout(context)
    private val rows = ArrayList<RoleButton>()
    private var items: List<SearchItem> = emptyList()

    init {
        orientation = VERTICAL
        list.orientation = VERTICAL
        list.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        scroll.isFillViewport = true
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(ThemedText(context, Globals.TEXT_LABEL, Tone.MUTED).apply {
            text = label
            gravity = Gravity.CENTER
            letterSpacing = Globals.LABEL_TRACKING
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** The rows currently showing, best last — what the studio lets you restyle. */
    fun rows(): List<RoleButton> = rows.filter { it.visibility == VISIBLE }

    /** True while there is more list below the current scroll position. */
    fun canScrollDown() = scroll.canScrollVertically(1)

    /** Shows [results] best-first from the bottom and snaps the list back to the bottom. */
    fun bind(results: List<SearchItem>) {
        items = results
        val n = results.size
        while (rows.size < n) rows += newRow().also { list.addView(it, 0) }
        // children run top → bottom, so the best item (index 0) is the last child
        val total = rows.size
        for (k in 0 until total) {
            val row = list.getChildAt(k) as RoleButton
            val index = total - 1 - k
            if (index < n) {
                row.text = results[index].title
                row.tag = index
                row.visibility = VISIBLE
            } else {
                row.visibility = GONE
            }
        }
        scroll.toBottom()
    }

    private fun newRow() = RoleButton(context, Role.SEARCH, Globals.TEXT_RESULT).apply {
        gravity = Gravity.CENTER
        setOnClickListener { v -> onRun(items[v.tag as Int], v) }
        setOnLongClickListener { v ->
            val item = items[v.tag as Int]
            if (item is AppEntry) onHold(item, v)
            item is AppEntry
        }
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpi(Globals.LIST_GAP)
        }
    }

    /**
     * A scroll view that keeps its distance from the *bottom* when its size or content changes
     * (the keyboard shrinking the viewport, results changing), because the bottom is where the
     * best match lives.
     */
    private class AnchoredScroll(context: Context) : ShadowScroll(context) {
        private var fromBottom = 0

        fun toBottom() {
            fromBottom = 0
            scrollTo(0, range)
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            fromBottom = range - t
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val keep = fromBottom
            super.onLayout(changed, l, t, r, b)
            fromBottom = keep
            scrollTo(0, (range - keep).coerceAtLeast(0))
        }
    }
}
