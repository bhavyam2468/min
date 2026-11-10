package dev.bhavyam.min.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import dev.bhavyam.min.theme.ButtonVariant
import dev.bhavyam.min.theme.Theme

/**
 * Token Studio's button component as a drawable: one shape, the [ButtonVariant] looks, coloured by
 * the theme's app colour. Plain and ghost draw nothing at rest (the press highlight shows on touch).
 */
class ButtonDrawable : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var theme: Theme? = null
    private var variant = ButtonVariant.Plain
    private var radius = 0f

    /** Text and icon colour on top of this button. */
    var foreground = 0
        private set

    fun style(theme: Theme, variant: ButtonVariant, radius: Float) {
        this.theme = theme
        this.variant = variant
        this.radius = radius
        val role = theme.primary
        foreground = when (variant) {
            ButtonVariant.Plain -> theme.text
            ButtonVariant.Solid, ButtonVariant.Gradient -> role.on
            else -> role.ink
        }
        fill.color = when (variant) {
            ButtonVariant.Solid -> role.color
            ButtonVariant.Soft -> role.soft
            ButtonVariant.Gradient -> WHITE // opaque, so the gradient shader shows at full strength
            else -> 0
        }
        outline.color = role.color
        outline.strokeWidth = theme.dp(OUTLINE_DP)
        updateShader()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) = updateShader()

    private fun updateShader() {
        val t = theme ?: return
        val b = bounds
        fill.shader = if (variant == ButtonVariant.Gradient && !b.isEmpty) {
            LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), t.primary.gradientStart, t.primary.gradientEnd, Shader.TileMode.CLAMP)
        } else null
    }

    override fun draw(canvas: Canvas) {
        if (!variant.hasShape) return
        rect.set(bounds)
        val r = radius.coerceAtMost(rect.height() / 2f)
        if (variant == ButtonVariant.Outline) {
            val inset = outline.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, r - inset, r - inset, outline)
        } else {
            canvas.drawRoundRect(rect, r, r, fill)
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        outline.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val WHITE = -0x1
        const val OUTLINE_DP = 1.5f
    }
}
