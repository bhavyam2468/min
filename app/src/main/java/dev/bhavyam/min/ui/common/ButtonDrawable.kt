package dev.bhavyam.min.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import dev.bhavyam.min.theme.ButtonVariant
import dev.bhavyam.min.theme.Theme

/**
 * Token Studio's button component as a drawable: one shape, five variants (solid, soft, outline,
 * ghost, gradient), coloured by a role and lifted by the elevation token. [tier] picks the role and
 * the variant the studio assigned to it (0 primary · 1 accent · 2 neutral).
 */
class ButtonDrawable(private val tier: Int) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private var theme: Theme? = null
    private var radius = 0f

    /** Text and icon colour on top of this button. */
    var foreground = 0
        private set

    fun style(theme: Theme, radius: Float) {
        this.theme = theme
        this.radius = radius
        val role = theme.roles[tier]
        val variant = theme.variant(tier)
        foreground = when (variant) {
            ButtonVariant.Solid, ButtonVariant.Gradient -> role.on
            else -> role.ink
        }
        fill.color = when (variant) {
            ButtonVariant.Solid -> role.color
            ButtonVariant.Soft -> role.soft
            ButtonVariant.Gradient -> WHITE // opaque, so the gradient shader shows at full strength
            else -> 0
        }
        val lifted = variant == ButtonVariant.Solid || variant == ButtonVariant.Gradient
        val e = theme.tokens.elevation
        if (lifted && e > 0f) fill.setShadowLayer(theme.em(e * 0.45f), 0f, theme.em(e * 0.12f), theme.shadow)
        else fill.clearShadowLayer()
        outline.color = role.color
        outline.strokeWidth = theme.dp(OUTLINE_DP)
        highlight.color = Theme.withAlpha(WHITE, if (lifted) e * HIGHLIGHT else 0f)
        updateShader()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) = updateShader()

    private fun updateShader() {
        val t = theme ?: return
        val b = bounds
        fill.shader = if (t.variant(tier) == ButtonVariant.Gradient && !b.isEmpty) {
            val role = t.roles[tier]
            LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), role.gradientStart, role.gradientEnd, Shader.TileMode.CLAMP)
        } else null
    }

    override fun draw(canvas: Canvas) {
        val t = theme ?: return
        rect.set(bounds)
        val r = radius.coerceAtMost(rect.height() / 2f)
        val variant = t.variant(tier)
        if (variant == ButtonVariant.Solid || variant == ButtonVariant.Soft || variant == ButtonVariant.Gradient) {
            canvas.drawRoundRect(rect, r, r, fill)
        }
        if (variant == ButtonVariant.Outline) {
            val inset = outline.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, r - inset, r - inset, outline)
            rect.set(bounds)
        }
        if (highlight.alpha > 0) { // the 1px inner top light of lifted buttons
            clip.reset()
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clip)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + t.dp(1f), highlight)
            canvas.restoreToCount(save)
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val WHITE = -0x1
        const val OUTLINE_DP = 1.5f
        const val HIGHLIGHT = 0.22f
    }
}
