package dev.bhavyam.min.ui.common

import android.content.Context
import android.graphics.Canvas
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.IconDrawable
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.theme.Theme

/** An icon centred in a square box of [boxDp], in the text colour (or muted). */
open class IconView(
    context: Context,
    icon: Icons.Icon,
    private val boxDp: Float,
    private val muted: Boolean,
) : View(context), Themed {

    private val drawable = IconDrawable(icon)

    override fun applyTheme(theme: Theme) {
        drawable.style(theme, if (muted) theme.muted else theme.text)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (boxDp * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val s = (Globals.Space.ICON * resources.displayMetrics.density).toInt().coerceAtMost(minOf(width, height))
        val l = (width - s) / 2
        val t = (height - s) / 2
        drawable.setBounds(l, t, l + s, t + s)
        drawable.draw(canvas)
    }
}
