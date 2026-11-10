package com.bhavyam.min.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import com.bhavyam.min.App
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore

/** A standalone icon in the icon tokens. Sinks when pressed if it is clickable. */
class ThemedIcon(
    context: Context,
    icon: Icon,
    sizeDp: Float = Globals.ICON,
    private val tone: Tone = Tone.TEXT,
) : View(context), ThemeStore.Listener {

    private val themes = App.of(context).themes
    private val glyph = IconDrawable(icon, dpi(sizeDp)).also { it.callback = this }
    private val press = Press(this)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        themes.add(this)
    }

    override fun onDetachedFromWindow() {
        themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        val t = theme.tokens
        glyph.style(if (tone == Tone.MUTED) theme.muted else theme.text, t.iconStyle, t.iconStroke)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        if (isClickable) press.set(pressed)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            glyph.intrinsicWidth + paddingLeft + paddingRight,
            glyph.intrinsicHeight + paddingTop + paddingBottom,
        )
    }

    override fun onDraw(canvas: Canvas) {
        glyph.setBounds(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        glyph.draw(canvas)
    }

    override fun verifyDrawable(who: Drawable) = who === glyph || super.verifyDrawable(who)
}
