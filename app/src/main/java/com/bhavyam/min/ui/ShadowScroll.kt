package com.bhavyam.min.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.widget.ScrollView
import com.bhavyam.min.App
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore

/**
 * A vertical scroll view with a soft shadow at each edge that has more content beyond it, so
 * rows look like they slide under the edge and keep going. Each shadow grows with how far
 * there is to scroll that way and disappears at the end of the list. Painted from the page
 * colour plus the theme's shadow tint: on black it reads as depth, on light themes as a cast.
 */
open class ShadowScroll(context: Context) : ScrollView(context), ThemeStore.Listener {

    private val themes = App.of(context).themes
    private val top = Paint()
    private val bottom = Paint()
    private val depth = dp(Globals.EDGE_SHADOW)
    private var bg = 0
    private var cast = 0

    init {
        isVerticalScrollBarEnabled = false
        isVerticalFadingEdgeEnabled = false
        overScrollMode = OVER_SCROLL_NEVER // the shadows already say "there's an end here"
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
        bg = theme.bg
        cast = mix(theme.bg, theme.shadow, if (theme.dark) 0.6f else 0.18f)
        shaders()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shaders()
    }

    /** Built only when size or theme change; scrolling just changes their alpha. */
    private fun shaders() {
        val stops = floatArrayOf(0f, 0.45f, 1f)
        val colors = intArrayOf(cast, withAlpha(bg, 0.7f), withAlpha(bg, 0f))
        top.shader = LinearGradient(0f, 0f, 0f, depth, colors, stops, Shader.TileMode.CLAMP)
        bottom.shader = LinearGradient(0f, height.toFloat(), 0f, height - depth, colors, stops, Shader.TileMode.CLAMP)
        invalidate()
    }

    /** How far the content can scroll in total. */
    protected val range get() = ((getChildAt(0)?.height ?: 0) - height + paddingTop + paddingBottom).coerceAtLeast(0)

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val above = (scrollY / depth).coerceIn(0f, 1f)
        val below = ((range - scrollY) / depth).coerceIn(0f, 1f)
        if (above <= 0f && below <= 0f) return
        canvas.save()
        canvas.translate(scrollX.toFloat(), scrollY.toFloat())
        if (above > 0f) {
            top.alpha = (above * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), depth, top)
        }
        if (below > 0f) {
            bottom.alpha = (below * 255).toInt()
            canvas.drawRect(0f, height - depth, width.toFloat(), height.toFloat(), bottom)
        }
        canvas.restore()
    }
}
