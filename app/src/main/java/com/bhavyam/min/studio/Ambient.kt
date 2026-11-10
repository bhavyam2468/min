package com.bhavyam.min.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.bhavyam.min.App
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.withAlpha
import kotlin.math.max

/**
 * The studio's backdrop: the page colour with a soft glow of the primary colour in one corner
 * and the accent in the other. Glow strength follows vividness, so a grey theme gets a plain
 * page and a colourful one fills the room with its colours.
 */
class Ambient(context: Context) : View(context), ThemeStore.Listener {

    private val app = App.of(context)
    private val primary = Paint()
    private val accent = Paint()
    private var bg = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = onTheme(app.themes.theme)

    override fun onTheme(theme: Theme) {
        bg = theme.bg
        val t = theme.tokens
        val reach = max(width, height) * 0.75f
        if (reach <= 0f) return
        primary.shader = glow(0f, 0f, reach, theme.colors(Role.HOME).fill, t.chroma)
        accent.shader = glow(width.toFloat(), height.toFloat(), reach, theme.colors(Role.SEARCH).fill, t.accentChroma)
        invalidate()
    }

    private fun glow(x: Float, y: Float, r: Float, color: Int, chroma: Float): Shader {
        val strength = (chroma / Globals.CHROMA_MAX).coerceIn(0f, 1f) * MAX_ALPHA
        return RadialGradient(x, y, r, withAlpha(color, strength), withAlpha(color, 0f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        canvas.drawPaint(primary)
        canvas.drawPaint(accent)
    }

    private companion object {
        const val MAX_ALPHA = 0.35f
    }
}
