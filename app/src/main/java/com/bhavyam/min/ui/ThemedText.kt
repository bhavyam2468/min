package com.bhavyam.min.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import com.bhavyam.min.App
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore

enum class Tone { TEXT, MUTED }

/** The type tokens on any text view: family, weight (regular or strong) and size × scale. */
fun TextView.applyType(theme: Theme, sizeSp: Float, strong: Boolean) {
    typeface = if (strong) theme.typefaceStrong else theme.typeface
    setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.textPx(sizeSp, resources))
}

/** Text that follows the theme while it is on screen. */
open class ThemedText(
    context: Context,
    sizeSp: Float,
    private val tone: Tone = Tone.TEXT,
    private val strong: Boolean = false,
) : TextView(context), ThemeStore.Listener {

    protected val themes = App.of(context).themes

    var sizeSp: Float = sizeSp
        set(v) {
            field = v
            onTheme(themes.theme)
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        themes.add(this)
    }

    override fun onDetachedFromWindow() {
        themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        applyType(theme, sizeSp, strong)
        setTextColor(if (tone == Tone.MUTED) theme.muted else theme.text)
    }
}

/** A borderless single-line input in the theme: text colour, muted hint, text-coloured caret. */
class ThemedEdit(context: Context, private val sizeSp: Float, hint: String) : EditText(context), ThemeStore.Listener {

    private val themes = App.of(context).themes
    private val caret = GradientDrawable()

    init {
        this.hint = hint
        background = null
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        gravity = Gravity.CENTER
        textCursorDrawable = caret
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        themes.add(this)
    }

    override fun onDetachedFromWindow() {
        themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        applyType(theme, sizeSp, strong = false)
        setTextColor(theme.text)
        setHintTextColor(theme.muted)
        highlightColor = withAlpha(theme.colors(Role.SEARCH).fill, 0.35f)
        caret.setColor(theme.text)
        caret.setSize(dpi(2f), (textSize * 1.2f).toInt())
    }
}
