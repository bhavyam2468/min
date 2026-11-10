package com.bhavyam.min.studio

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.bhavyam.min.home.ClockFormat
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.ThemedEdit
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dpi
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * Edits one clock pattern (date or time) in place: the pattern itself, and a row of token
 * chips that each show what they print right now — tap one to insert it at the cursor.
 * Every keystroke reaches the live clock immediately through [onChange].
 */
class FormatEditor(context: Context, private val onChange: (String) -> Unit, onDone: () -> Unit) : LinearLayout(context) {

    private val title = ThemedText(context, Globals.TEXT_LABEL, Tone.MUTED)
    private val input = ThemedEdit(context, Globals.TEXT_ROW, "pattern")
    private val chips = LinearLayout(context)
    private val out = StringBuilder()
    private var binding = false

    init {
        orientation = VERTICAL
        visibility = GONE
        val pad = dpi(Globals.EDGE / 2)
        setPadding(pad, pad, pad, pad)
        title.letterSpacing = Globals.LABEL_TRACKING
        input.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        input.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.setOnEditorActionListener { _, _, _ -> onDone(); true }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (!binding) onChange(s.toString())
            }
        })
        val head = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(RoleButton(context, Role.MENU, Globals.TEXT_BUTTON).apply {
                text = "done"
                setOnClickListener { onDone() }
            })
        }
        addView(head)
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })
        for (token in CHIPS) chips.addView(chip(token))
    }

    /** Opens on [pattern]; [label] says which part of the clock it is. */
    fun open(label: String, pattern: String) {
        binding = true
        title.text = label.uppercase(Locale.getDefault())
        input.setText(pattern)
        input.setSelection(pattern.length)
        binding = false
        refreshExamples()
        visibility = VISIBLE
        input.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(input, 0)
    }

    fun close() {
        input.clearFocus()
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
        visibility = GONE
    }

    val isOpen get() = visibility == VISIBLE

    private fun chip(token: String) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = dpi(10f)
        setPadding(pad, pad / 2, pad, pad / 2)
        addView(ThemedText(context, Globals.TEXT_BUTTON, strong = true).apply { text = token })
        addView(ThemedText(context, Globals.TEXT_LABEL, Tone.MUTED))
        tag = token
        setOnClickListener {
            input.text.replace(input.selectionStart.coerceAtLeast(0), input.selectionEnd.coerceAtLeast(0), token)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Each chip shows its own output for this moment, in this locale and 12/24-hour setting. */
    private fun refreshExamples() {
        val cal = Calendar.getInstance()
        val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
        val is24 = DateFormat.is24HourFormat(context)
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as LinearLayout
            out.setLength(0)
            ClockFormat.compile(chip.tag as String).format(cal, is24, symbols, out)
            (chip.getChildAt(1) as ThemedText).text = out.toString().ifEmpty { "—" }
        }
    }

    private companion object {
        /** Ordered like a sentence: day, month, year, then time. */
        val CHIPS = listOf("D", "DD", "d", "dd", "M", "MM", "m", "mm", "yy", "yyyy", "h", "hh", "H", "ii", "ss", "A", "a")
    }
}
