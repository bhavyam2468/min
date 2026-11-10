package dev.bhavyam.min.ui.studio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.clock.ClockFormat
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.ThemeStore
import dev.bhavyam.min.ui.common.IconButton
import dev.bhavyam.min.ui.common.KeyboardInsets
import dev.bhavyam.min.ui.common.MaxWidthLayout
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style
import java.time.LocalDateTime
import java.util.Locale

/**
 * The clock's format, written by hand: a sheet above the keyboard with one field per clock line,
 * what each line reads right now, and chips that insert a field at the cursor. Home's clock
 * follows every keystroke; [onCommit] runs once, when the sheet closes. Tapping outside closes it.
 */
@SuppressLint("ViewConstructor")
class ClockEditor(
    context: Context,
    private val themes: ThemeStore,
    private val settings: SettingsStore,
    private val onCommit: () -> Unit,
) : FrameLayout(context), Themed {

    private val d = resources.displayMetrics.density

    private val title = TextView(context).apply { text = "Clock format" }
    private val done = IconButton(context, Icons.check, settings, "Done").apply { setOnClickListener { close() } }
    private val header = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(done)
    }
    private val lines = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val addPress = PressDrawable(settings)
    private val add = TextView(context).apply {
        text = "+  Add line"
        background = addPress
        setOnClickListener { addLine("", focus = true) }
    }
    private val chips = LinearLayout(context)
    private val chipScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(chips)
    }
    private val caption = TextView(context).apply {
        text = "Letters are fields, anything else shows as typed. Quote words to keep their letters: 'at' h:ii"
    }
    private val sheetBackground = GradientDrawable()
    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = sheetBackground
        isClickable = true // taps on the sheet don't close it
        addView(header)
        addView(lines)
        addView(add, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(chipScroll)
        addView(caption)
    }
    private val holder = MaxWidthLayout(context, Globals.Space.CONTENT_MAX_WIDTH).apply { addView(sheet) }

    private var theme: Theme? = null
    private val slide = Spring(PRECISION) { p ->
        sheet.translationY = (1f - p) * sheet.height // the holder itself rides the keyboard
        alpha = p.coerceIn(0f, 1f)
        if (p <= 0f && !isOpen) visibility = GONE
    }

    var isOpen = false
        private set

    init {
        visibility = GONE
        isClickable = true
        setOnClickListener { close() }
        addView(holder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        KeyboardInsets(this, holder).attach()
        for (field in CHIPS) chips.addView(Chip(context, field))
        slide.snapTo(0f)
    }

    fun open() {
        isOpen = true
        lines.removeAllViews()
        themes.tokens.clock.ifEmpty { listOf("") }.forEach { addLine(it, focus = false) }
        refreshPreviews()
        visibility = VISIBLE
        post { slide.animateTo(1f, settings.spring(SettingsRegistry.OVERLAY_MOTION)) }
        (lines.getChildAt(lines.childCount - 1) as Line).field.let { f ->
            f.requestFocus()
            f.setSelection(f.length())
            f.windowInsetsController?.show(WindowInsets.Type.ime())
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        windowInsetsController?.hide(WindowInsets.Type.ime())
        findFocus()?.clearFocus()
        slide.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
        onCommit()
    }

    /** Gone at once (the launcher went to the background); what was typed is kept. */
    fun reset() {
        if (isOpen) onCommit()
        isOpen = false
        findFocus()?.clearFocus()
        slide.snapTo(0f)
    }

    private fun addLine(text: String, focus: Boolean) {
        if (lines.childCount >= MAX_LINES) return
        val line = Line(context, text)
        lines.addView(line)
        theme?.let(line::applyTheme)
        syncLineControls()
        if (focus) line.field.requestFocus()
    }

    private fun removeLine(line: Line) {
        lines.removeView(line)
        syncLineControls()
        publish()
    }

    /** One line can't be removed (clear it to hide the clock); there's a cap on how many to add. */
    private fun syncLineControls() {
        for (i in 0 until lines.childCount) (lines.getChildAt(i) as Line).remove.visibility = if (lines.childCount > 1) VISIBLE else INVISIBLE
        add.visibility = if (lines.childCount < MAX_LINES) VISIBLE else GONE
    }

    /** Push the typed lines to home's clock (blank lines dropped). */
    private fun publish() {
        val formats = (0 until lines.childCount).map { (lines.getChildAt(it) as Line).field.text.toString() }.filter { it.isNotBlank() }
        themes.update(themes.tokens.copy(clock = formats))
        refreshPreviews()
    }

    private fun refreshPreviews() {
        val now = LocalDateTime.now()
        val is24 = DateFormat.is24HourFormat(context)
        val locale = resources.configuration.locales[0]
        for (i in 0 until lines.childCount) {
            val line = lines.getChildAt(i) as Line
            line.preview.text = ClockFormat.format(line.field.text.toString(), now, is24, locale)
        }
        for (i in 0 until chips.childCount) (chips.getChildAt(i) as Chip).refresh(now, is24, locale)
    }

    /** Puts [field] at the cursor of the focused line (or the last line). */
    private fun insert(field: String) {
        val target = (0 until lines.childCount).map { lines.getChildAt(it) as Line }.let { all -> all.firstOrNull { it.field.hasFocus() } ?: all.lastOrNull() }
            ?: return
        val f = target.field
        val start = f.selectionStart.coerceAtLeast(0)
        val end = f.selectionEnd.coerceAtLeast(0)
        f.text.replace(minOf(start, end), maxOf(start, end), field)
        f.requestFocus()
    }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        val pad = (Globals.Space.SHEET_PAD * d).toInt()
        sheetBackground.setColor(theme.surface)
        val r = theme.radiusCard
        sheetBackground.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.elevation = theme.elevation
        sheet.setPadding(pad, pad / 2, pad / 2, pad)
        title.style(theme, Globals.Type.ROW, strong = true)
        done.applyTheme(theme)
        add.style(theme, Globals.Type.ROW_SUMMARY, theme.primary.ink)
        addPress.color = theme.pressed
        addPress.radius = Float.MAX_VALUE
        val chipGap = (CHIP_GAP * d).toInt()
        add.setPadding(chipGap, chipGap, chipGap, chipGap)
        (add.layoutParams as MarginLayoutParams).marginStart = -chipGap // its text lines up with the fields
        (chipScroll.layoutParams as MarginLayoutParams).setMargins(0, chipGap, pad / 2, chipGap)
        caption.style(theme, Globals.Type.ROW_SUMMARY, theme.muted)
        (caption.layoutParams as MarginLayoutParams).marginEnd = pad / 2
        for (i in 0 until lines.childCount) (lines.getChildAt(i) as Line).applyTheme(theme)
        for (i in 0 until chips.childCount) (chips.getChildAt(i) as Chip).applyTheme(theme)
    }

    /** One clock line: its format, what it reads now, and a way to remove it. */
    @SuppressLint("ViewConstructor")
    private inner class Line(context: Context, text: String) : LinearLayout(context), Themed {
        val field = EditText(context).apply {
            setText(text)
            hint = "e.g. DD, d MM"
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable) = publish()
            })
        }
        val preview = TextView(context).apply {
            isSingleLine = true
            gravity = Gravity.END
        }
        val remove = IconButton(context, Icons.close, settings, "Remove line").apply { setOnClickListener { removeLine(this@Line) } }

        init {
            gravity = Gravity.CENTER_VERTICAL
            addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(preview, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(remove)
        }

        override fun applyTheme(theme: Theme) {
            field.style(theme, Globals.Type.EDITOR)
            field.setHintTextColor(theme.muted)
            field.setPadding(0, (FIELD_PAD * d).toInt(), 0, (FIELD_PAD * d).toInt())
            preview.style(theme, Globals.Type.ROW_SUMMARY, theme.muted)
            preview.setPadding((FIELD_PAD * d).toInt(), 0, 0, 0)
            remove.applyTheme(theme)
        }
    }

    /** "dd · 02": inserts its field; the example is the field formatted for right now. */
    @SuppressLint("ViewConstructor")
    private inner class Chip(context: Context, private val field: String) : TextView(context), Themed {
        private val fill = GradientDrawable().apply { cornerRadius = Float.MAX_VALUE }

        init {
            background = fill
            isSingleLine = true
            setOnClickListener { insert(field) }
        }

        fun refresh(now: LocalDateTime, is24: Boolean, locale: Locale) {
            text = "$field  ${ClockFormat.format(field, now, is24, locale).ifEmpty { "—" }}"
        }

        override fun applyTheme(theme: Theme) {
            style(theme, Globals.Type.CHIP)
            fill.setColor(theme.surface2)
            val px = (CHIP_PAD_X * d).toInt()
            val py = (CHIP_PAD_Y * d).toInt()
            setPadding(px, py, px, py)
            (layoutParams as MarginLayoutParams).marginEnd = (CHIP_GAP * d).toInt()
        }
    }

    private companion object {
        /** The fields offered as chips (the example is filled in live). */
        val CHIPS = listOf("D", "DD", "d", "dd", "M", "MM", "m", "mm", "yy", "yyyy", "h", "hh", "ii", "a")
        const val MAX_LINES = 4
        const val PRECISION = 0.001f
        const val FIELD_PAD = 8f
        const val CHIP_PAD_X = 11f
        const val CHIP_PAD_Y = 6f
        const val CHIP_GAP = 6f
    }
}
