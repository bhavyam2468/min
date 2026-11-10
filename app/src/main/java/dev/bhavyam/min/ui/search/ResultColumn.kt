package dev.bhavyam.min.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.search.SearchResult
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import dev.bhavyam.min.ui.common.styleCaps

/**
 * One category page: results stacked from the bottom up (best match lowest) in a scroll view that
 * fills the space, and the category label beneath them — always directly on top of the input.
 * Rows are pooled; binding a new result set only rewrites text.
 */
@SuppressLint("ViewConstructor")
class ResultColumn(
    context: Context,
    label: String,
    private val settings: SettingsStore,
    private val onRun: (SearchResult, View) -> Unit,
    private val onLongPress: (SearchResult, View) -> Boolean,
) : LinearLayout(context), Themed {

    private val scroll = BottomScrollView(context)
    private val list = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }
    private val title = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
    }
    private var theme: Theme? = null
    private var styled = 0

    private val click = OnClickListener { onRun(it.tag as SearchResult, it) }
    private val longClick = OnLongClickListener { onLongPress(it.tag as SearchResult, it) }

    /** The best (bottom-most) result, what the keyboard's search key runs. */
    var top: SearchResult? = null
        private set

    init {
        orientation = VERTICAL
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(results: List<SearchResult>) {
        val n = results.size
        while (list.childCount < n) list.addView(newRow())
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as TextView
            if (i < n) {
                val result = results[n - 1 - i]
                row.text = result.title
                row.tag = result
                row.visibility = View.VISIBLE
            } else if (row.visibility != View.GONE) {
                row.visibility = View.GONE
                row.tag = null
            }
        }
        if (n > styled) theme?.let { t -> for (i in styled until n) styleRow(list.getChildAt(i) as TextView, t) }
        styled = maxOf(styled, n)
        top = results.firstOrNull()
        scroll.anchorToBottom()
    }

    /** Whether the list can still scroll towards its best matches (if not, an upward drag closes search). */
    fun canScrollDown() = scroll.canScrollVertically(1)

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        title.styleCaps(theme, Globals.Type.CATEGORY)
        (title.layoutParams as MarginLayoutParams).topMargin = theme.dp(Globals.Space.CATEGORY_GAP).toInt()
        title.requestLayout()
        scroll.setFadingEdgeLength(theme.em(FADE_EM).toInt())
        for (i in 0 until styled) styleRow(list.getChildAt(i) as TextView, theme)
    }

    private fun styleRow(row: TextView, theme: Theme) {
        row.style(theme, Globals.Type.RESULT)
        val padY = (theme.em(Globals.Type.RESULT * Globals.Space.RESULT_GAP) * theme.density / 2).toInt()
        val padX = theme.em(theme.tokens.padX).toInt()
        row.setPadding(padX, padY, padX, padY)
        (row.background as PressDrawable).apply { color = theme.pressed; radius = theme.radiusButton }
    }

    private fun newRow() = TextView(context).apply {
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        background = PressDrawable(settings)
        setOnClickListener(click)
        setOnLongClickListener(longClick)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    private companion object {
        const val FADE_EM = 4f
    }
}
