package com.bhavyam.min.settings

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import com.bhavyam.min.search.FuzzyKey
import com.bhavyam.min.search.Query
import com.bhavyam.min.search.Ranker
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.ui.CenteredColumn
import com.bhavyam.min.ui.OverlayLayer
import com.bhavyam.min.ui.Pager
import com.bhavyam.min.ui.ThemedEdit
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dpi
import com.bhavyam.min.ui.safe

/**
 * The settings overlay, drawn entirely from [SettingsRegistry]: a root list of categories with
 * a search over every setting (same matcher as app search), and one page per category. The
 * category page slides in from the right; back slides it out, then closes the overlay.
 */
@SuppressLint("ViewConstructor")
class SettingsView(context: Context, private val host: SettingsHost) : OverlayLayer(context) {

    /** A searchable thing on the root page: a setting (edited in place) or a root link. */
    private class Match(title: String, val build: () -> SettingRow) {
        val key = FuzzyKey(title)
    }

    private val title = ThemedText(context, Globals.TEXT_TITLE, strong = true)
    private val pager = Pager(context, swipeable = false)
    private val search = ThemedEdit(context, Globals.TEXT_ROW, "search settings")
    private val entries = LinearLayout(context)
    private val results = LinearLayout(context)
    private val empty = ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED)
    private val detail = LinearLayout(context)
    private val scrolls = listOf(ScrollView(context), ScrollView(context))
    private val ranker = Ranker()
    private val matched = ArrayList<Match>()
    private val matches: List<Match>
    private val refreshOnChange: (String) -> Unit = { refresh() }

    init {
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton())
            addView(title)
        }
        val column = CenteredColumn(context, Globals.SETTINGS_MAX_WIDTH)
        column.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(pager, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        listOf(entries, results, detail).forEach { it.orientation = LinearLayout.VERTICAL }
        search.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val edge = dpi(Globals.EDGE / 2)
        search.setPadding(edge, edge, edge, edge)
        empty.text = "no settings match"
        empty.gravity = Gravity.CENTER
        empty.setPadding(edge, edge * 2, edge, edge)
        results.addView(empty)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(search)
            addView(entries)
            addView(results)
        }
        scrolls[0].addView(root)
        scrolls[1].addView(detail)
        scrolls.forEach { it.clipToPadding = false }
        pager.setPages(scrolls)

        for (entry in SettingsRegistry.ENTRIES) entries.addView(EntryRow(context, entry) { enter(entry) })
        matches = SettingsRegistry.ENTRIES.flatMap { entry ->
            when (entry) {
                is Category -> entry.settings.map { s -> Match(s.title) { rowFor(context, s, host) } }
                is Link -> listOf(Match(entry.title) { EntryRow(context, entry) { entry.run(host) } })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = filter(s.toString())
        })
        filter("")
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val safe = insets.safe()
        val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
        setPadding(safe.left, safe.top, safe.right, 0)
        scrolls.forEach { it.setPadding(0, 0, 0, maxOf(safe.bottom, ime)) }
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.prefs.observe(refreshOnChange)
    }

    override fun onDetachedFromWindow() {
        app.prefs.unobserve(refreshOnChange)
        super.onDetachedFromWindow()
    }

    override fun open() {
        search.text.clear()
        showRoot(animate = false)
        refresh()
        super.open()
    }

    override fun close() {
        search.clearFocus()
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
        super.close()
    }

    override fun back(): Boolean = when {
        pager.current == DETAIL -> {
            showRoot(animate = true)
            true
        }
        search.text.isNotEmpty() -> {
            search.text.clear()
            true
        }
        else -> false
    }

    /** Re-reads every visible row: values changed over adb, permissions granted, and so on. */
    fun refresh() {
        for (list in listOf(results, detail)) {
            for (i in 0 until list.childCount) (list.getChildAt(i) as? SettingRow)?.refresh()
        }
    }

    private fun enter(entry: Entry) {
        when (entry) {
            is Link -> entry.run(host)
            is Category -> {
                detail.removeAllViews()
                for (s in entry.settings) detail.addView(rowFor(context, s, host))
                scrolls[DETAIL].scrollTo(0, 0)
                title.text = entry.title
                pager.go(DETAIL, animate = true)
            }
        }
    }

    private fun showRoot(animate: Boolean) {
        title.text = TITLE
        pager.go(ROOT, animate)
    }

    private fun filter(raw: String) {
        val query = Query(raw)
        entries.visibility = if (query.isEmpty) View.VISIBLE else View.GONE
        results.visibility = if (query.isEmpty) View.GONE else View.VISIBLE
        results.removeViews(1, results.childCount - 1)
        if (query.isEmpty) return
        matched.clear()
        ranker.rank(query.norm, matches, { it.key }, matched)
        for (m in matched) results.addView(m.build())
        empty.visibility = if (matched.isEmpty()) View.VISIBLE else View.GONE
    }

    private companion object {
        const val TITLE = "settings"
        const val ROOT = 0
        const val DETAIL = 1
    }
}
