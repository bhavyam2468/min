package com.bhavyam.min.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.observe
import com.bhavyam.min.search.SearchItem
import com.bhavyam.min.search.SearchProvider
import com.bhavyam.min.theme.Globals

/**
 * The search screen: category pages you swipe between, and the input at the very bottom,
 * riding on top of the keyboard. Search is universal: every category is searched on every
 * keystroke and the screen follows the best match. The engine's change signal rebinds the
 * pages, so data changing underneath (apps installed, contacts loaded) shows too.
 */
class SearchLayer(
    context: Context,
    private val onRun: (SearchItem, View) -> Unit,
    onHold: (AppEntry, View) -> Unit,
) : FrameLayout(context) {

    private val engine = App.of(context).search
    private val content = LinearLayout(context)
    private val pager = Pager(context, swipeable = true)
    private val pages: Map<SearchProvider, ResultsPage>

    val input = ThemedEdit(context, Globals.TEXT_INPUT, "search")

    init {
        content.orientation = LinearLayout.VERTICAL
        content.addView(pager, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        pages = engine.providers.associateWith { ResultsPage(context, it.label, onRun, onHold) }

        input.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = engine.search(s.toString())
        })
        input.setOnEditorActionListener { _, action, event ->
            val enter = action == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (enter) runTop()
            enter
        }
        observe(engine.changed, ::bind)

        ImeFollower(content) { insets, bottom ->
            val safe = insets.safe()
            content.setPadding(safe.left, safe.top, safe.right, bottom + bottomInset)
        }.attach()
        engine.search("")
    }

    /** Extra room kept free under the input, e.g. for the studio's toolbar. */
    var bottomInset = 0
        set(v) {
            if (field == v) return
            field = v
            content.requestApplyInsets()
        }

    /** Text currently in the input. */
    val query: String get() = input.text.toString()

    fun setQuery(q: String) {
        input.setText(q)
        input.setSelection(q.length)
    }

    /** Swipe-up to close is only allowed when the list is already at its bottom. */
    fun canScrollDown() = pages[currentProvider()]?.canScrollDown() == true

    /** Runs the best result of the focused category, as if it were tapped. */
    fun runTop() {
        val page = engine.pages.getOrNull(pager.current) ?: return
        val item = page.items.firstOrNull() ?: return
        onRun(item, input)
    }

    private fun currentProvider() = engine.pages.getOrNull(pager.current)?.provider

    /**
     * Rebinds every page, then glides to the category holding the closest match — typing "aa"
     * on APPS moves to CONTACTS when Aarav beats every app. An empty query starts on APPS.
     */
    private fun bind() {
        val visible = engine.pages.map { pages.getValue(it.provider) }
        pager.setPages(visible)
        for (p in engine.pages) pages.getValue(p.provider).bind(p.items)
        val target = if (engine.query.isEmpty) 0 else engine.bestPage(pager.current)
        if (target != pager.current) pager.go(target, animate = true)
    }

    /** The result rows on screen right now — what the studio restyles. */
    fun rows(): List<RoleButton> = currentProvider()?.let { pages.getValue(it).rows() } ?: emptyList()
}
