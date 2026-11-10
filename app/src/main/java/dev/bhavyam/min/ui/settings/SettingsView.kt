package dev.bhavyam.min.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.search.FuzzyMatcher
import dev.bhavyam.min.search.SearchTargets
import dev.bhavyam.min.settings.Action
import dev.bhavyam.min.settings.Permission
import dev.bhavyam.min.settings.ProviderOrder
import dev.bhavyam.min.settings.SettingItem
import dev.bhavyam.min.settings.SettingsHost
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.settings.Slider
import dev.bhavyam.min.settings.Toggle
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.IconButton
import dev.bhavyam.min.ui.common.IconView
import dev.bhavyam.min.ui.common.MaxWidthLayout
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import dev.bhavyam.min.ui.common.styleCaps

/**
 * The settings overlay, built entirely from [SettingsRegistry.SECTIONS]: a header with a back
 * arrow, a search field (same matcher as everywhere), then each section's groups as cards.
 * It fades and slides in over home on a spring, and reverses on the way out.
 */
@SuppressLint("ViewConstructor")
class SettingsView(
    context: Context,
    private val settings: SettingsStore,
    targets: SearchTargets,
    private val host: SettingsHost,
    private val onClose: () -> Unit,
) : FrameLayout(context), Themed {

    private val back = IconButton(context, Icons.back, settings, "Back").apply { setOnClickListener { onClose() } }
    private val title = TextView(context).apply { text = "settings" }
    private val header = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(back)
        addView(title)
    }
    private val searchIcon = IconView(context, Icons.search, Globals.Space.ICON_BUTTON, muted = true)
    private val searchInput = EditText(context).apply {
        hint = "search settings"
        background = null
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
    }
    private val searchBackground = GradientDrawable()
    private val search = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = searchBackground
        addView(searchIcon)
        addView(searchInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** One rendered section: its label, and its groups (caption, card, rows). */
    private class SectionViews(val label: TextView, val groups: List<GroupViews>)
    private class GroupViews(val caption: TextView?, val card: LinearLayout, val rows: List<SettingRow>)

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val sections: List<SectionViews>
    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        addView(list)
    }

    private val spring = Spring(PRECISION) { p ->
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * Globals.Motion.OVERLAY_SLIDE * resources.displayMetrics.density
        if (p <= 0f && !isOpen) {
            visibility = GONE
            if (searchInput.length() > 0) searchInput.text.clear() // next time starts fresh
            scroll.scrollTo(0, 0)
        }
    }

    var isOpen = false
        private set

    /**
     * Fully behind the studio. Hidden then, so holding the studio's preview shows home, and not
     * restyled: ~40 rows on every drag frame would be wasted work, so the latest theme is kept and
     * applied once, as the studio starts to leave.
     */
    var isCovered = false
        set(value) {
            field = value
            if (isOpen) visibility = if (value) INVISIBLE else VISIBLE
            if (!value) pendingTheme?.let(::applyTheme)
        }
    private var pendingTheme: Theme? = null

    init {
        visibility = GONE
        isClickable = true // nothing underneath reacts while settings is up
        sections = SettingsRegistry.SECTIONS.map { section ->
            val label = TextView(context).apply { text = section.title }
            list.addView(label)
            val groups = section.groups.map { group ->
                val caption = group.title?.let { TextView(context).apply { text = it }.also(list::addView) }
                val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val keys = listOfNotNull(section.title, group.title)
                val rows = group.items.map { item ->
                    when (item) {
                        is Toggle -> SettingRow.ToggleRow(context, item, keys, settings)
                        is Slider -> SettingRow.SliderRow(context, item, keys, settings)
                        is Action -> SettingRow.ActionRow(context, item, keys, settings, host)
                        is Permission -> SettingRow.PermissionRow(context, item, keys, settings, host)
                        is ProviderOrder -> SettingRow.ProvidersRow(context, item, keys, settings, targets)
                    }.also { card.addView(it, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
                }
                list.addView(card, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                GroupViews(caption, card, rows)
            }
            SectionViews(label, groups)
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(search, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(MaxWidthLayout(context, Globals.Space.CONTENT_MAX_WIDTH).apply { addView(column) })

        setOnApplyWindowInsetsListener { _, insets ->
            val p = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            setPadding(p.left, p.top, p.right, p.bottom)
            insets
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = filter(FuzzyMatcher.normalize(s))
        })
    }

    fun open() {
        refresh()
        isOpen = true
        visibility = VISIBLE
        spring.animateTo(1f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    fun close() {
        isOpen = false
        searchInput.windowInsetsController?.hide(WindowInsets.Type.ime())
        searchInput.clearFocus()
        spring.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    /** Closed at once (the launcher went to the background). */
    fun reset() {
        isOpen = false
        searchInput.clearFocus()
        spring.snapTo(0f)
    }

    /** A setting changed (here, over adb, anywhere): re-read just its row. */
    fun settingChanged(item: SettingItem) = forEachRow { if (it.item === item) it.refresh() }

    /** Re-read every value (changed by adb, a permission dialog, an install…). */
    fun refresh() = forEachRow(SettingRow::refresh)

    private inline fun forEachRow(action: (SettingRow) -> Unit) {
        for (s in sections) for (g in s.groups) for (r in g.rows) action(r)
    }

    private fun filter(query: String) {
        for (s in sections) {
            var sectionVisible = false
            for (g in s.groups) {
                var groupVisible = false
                for (r in g.rows) {
                    val match = r.matches(query)
                    r.visibility = if (match) View.VISIBLE else View.GONE
                    groupVisible = groupVisible || match
                }
                g.card.visibility = if (groupVisible) View.VISIBLE else View.GONE
                g.caption?.visibility = g.card.visibility
                sectionVisible = sectionVisible || groupVisible
            }
            s.label.visibility = if (sectionVisible) View.VISIBLE else View.GONE
        }
    }

    override fun applyTheme(theme: Theme) {
        if (isCovered) {
            pendingTheme = theme
            return
        }
        pendingTheme = null
        setBackgroundColor(theme.bg)
        back.applyTheme(theme)
        title.style(theme, Globals.Type.TITLE, strong = true)
        searchIcon.applyTheme(theme)
        searchInput.style(theme, Globals.Type.ROW)
        searchInput.setHintTextColor(theme.muted)
        searchBackground.setColor(theme.surface)
        searchBackground.cornerRadius = if (theme.isPill) Float.MAX_VALUE else theme.radiusButton
        val edge = theme.dp(Globals.Space.EDGE).toInt()
        header.minimumHeight = theme.dp(Globals.Space.HEADER).toInt()
        // Pull the back button out by its touch padding so the arrow itself lines up with the edge.
        header.setPadding(edge - theme.dp((Globals.Space.ICON_BUTTON - Globals.Space.ICON) / 2).toInt(), 0, edge, 0)
        val searchGap = theme.dp(Globals.Space.SETTINGS_SEARCH_GAP).toInt()
        (search.layoutParams as MarginLayoutParams).setMargins(edge, searchGap, edge, searchGap)
        list.setPadding(edge, 0, edge, edge * 2)
        val cardPad = theme.em(CARD_PADDING_EM).toInt()
        val textInset = cardPad + SettingRow.paddingX(theme) // labels line up with the row text
        for (s in sections) {
            s.label.styleCaps(theme, Globals.Type.SECTION)
            s.label.setPadding(textInset, theme.dp(Globals.Space.SECTION_TOP).toInt(), 0, theme.dp(Globals.Space.SECTION_BOTTOM).toInt())
            for (g in s.groups) {
                g.caption?.let {
                    it.style(theme, Globals.Type.ROW_SUMMARY, theme.muted)
                    it.setPadding(textInset, theme.dp(Globals.Space.CAPTION_TOP).toInt(), 0, theme.dp(Globals.Space.CAPTION_BOTTOM).toInt())
                }
                g.card.background = GradientDrawable().apply {
                    setColor(theme.surface)
                    cornerRadius = theme.radiusCard
                }
                g.card.setPadding(cardPad, cardPad, cardPad, cardPad)
                g.rows.forEach { it.applyTheme(theme) }
            }
        }
    }

    private companion object {
        const val PRECISION = 0.001f
        const val CARD_PADDING_EM = 0.3f
    }
}
