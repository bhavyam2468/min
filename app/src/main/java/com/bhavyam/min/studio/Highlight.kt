package com.bhavyam.min.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.withAlpha

/**
 * Marks the spot being edited: a ring around a thing, a bar on an edge, a dot on a corner.
 * Moving to a new spot glides the shape from where it was instead of jumping.
 */
class Highlight(context: Context) : View(context), ThemeStore.Listener {

    /** A ring is outlined; bars and dots are filled. */
    enum class Kind { RING, BAR, DOT }

    private val app = App.of(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val from = RectF()
    private val to = RectF()
    private val now = RectF()
    private var fromRadius = 0f
    private var toRadius = 0f
    private var kind = Kind.RING
    private val glide = Spring(0.001f) { invalidate() }
    private val shown = Spring(0.001f) { invalidate() }

    init {
        isClickable = false
        paint.strokeWidth = dp(2f)
    }

    fun target(kind: Kind, rect: RectF, radius: Float) {
        if (rect == to && kind == this.kind && shown.target == 1f) return
        val visible = shown.value > 0.01f
        current(now)
        from.set(if (visible) now else rect)
        fromRadius = if (visible) currentRadius() else radius
        to.set(rect)
        toRadius = radius
        this.kind = kind
        glide.snapTo(0f)
        glide.animateTo(1f, app.motion[SettingsRegistry.SPRING_POPUP])
        shown.animateTo(1f, app.motion[SettingsRegistry.SPRING_PRESS])
    }

    fun hide() = shown.animateTo(0f, app.motion[SettingsRegistry.SPRING_OVERLAY])

    private fun current(out: RectF) {
        val p = glide.value
        out.set(
            from.left + (to.left - from.left) * p,
            from.top + (to.top - from.top) * p,
            from.right + (to.right - from.right) * p,
            from.bottom + (to.bottom - from.bottom) * p,
        )
    }

    private fun currentRadius() = fromRadius + (toRadius - fromRadius) * glide.value

    override fun onDraw(canvas: Canvas) {
        val a = shown.value.coerceIn(0f, 1f)
        if (a <= 0f) return
        current(now)
        val theme = app.themes.theme
        paint.color = withAlpha(theme.text, a * 0.9f)
        paint.style = if (kind == Kind.RING) Paint.Style.STROKE else Paint.Style.FILL
        val r = currentRadius()
        canvas.drawRoundRect(now, r, r, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) = invalidate()
}
