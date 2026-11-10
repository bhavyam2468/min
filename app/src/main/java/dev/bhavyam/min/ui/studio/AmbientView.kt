package dev.bhavyam.min.ui.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.Themed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * Token Studio's ambient light: two soft glows in the primary and accent tints, drifting slowly
 * behind the card. Runs only while visible; a frame costs two gradient circles.
 */
class AmbientView(context: Context) : View(context), Themed, Choreographer.FrameCallback {

    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var primary = 0
    private var accent = 0
    private var radius = 0f
    private var bg = 0
    private var startNanos = -1L
    private var phase = 0f
    private var running = false

    override fun applyTheme(theme: Theme) {
        val p = Theme.withAlpha(theme.primary.soft, Globals.Alpha.AMBIENT)
        val a = Theme.withAlpha(theme.roles[1].soft, Globals.Alpha.AMBIENT)
        if (theme.bg == bg && p == primary && a == accent) return
        bg = theme.bg
        primary = p
        accent = a
        buildShaders()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        radius = max(w, h) * BLOB / 2f
        buildShaders()
    }

    /** Gradients are centred on the origin and moved with the canvas, so frames allocate nothing. */
    private fun buildShaders() {
        if (radius > 0f) {
            primaryPaint.shader = RadialGradient(0f, 0f, radius, primary, primary and 0x00FFFFFF, Shader.TileMode.CLAMP)
            accentPaint.shader = RadialGradient(0f, 0f, radius, accent, accent and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && !running) {
            running = true
            Choreographer.getInstance().postFrameCallback(this)
        } else if (!isVisible && running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (startNanos < 0) startNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startNanos) / 1e9f
        // ease-in-out, alternate: 0 → 1 → 0 over two periods
        phase = (1f - cos(seconds / Globals.Motion.AMBIENT_PERIOD * PI.toFloat())) / 2f
        invalidate()
        if (running) Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        val vmax = max(width, height).toFloat()
        val r = radius
        glow(canvas, width * 0.18f + r + vmax * DRIFT_X * phase, height * 0.14f + r - vmax * DRIFT_Y * phase, 1 + GROW * phase, primaryPaint)
        // the second glow runs half a cycle behind
        val other = 1f - phase
        glow(canvas, width * 0.84f - r + vmax * DRIFT_X * other, height * 0.9f - r - vmax * DRIFT_Y * other, 1 + GROW * other, accentPaint)
    }

    private fun glow(canvas: Canvas, cx: Float, cy: Float, scale: Float, paint: Paint) {
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.drawCircle(0f, 0f, radius, paint)
        canvas.restoreToCount(save)
    }

    private companion object {
        const val BLOB = 0.46f * 1.6f // 46vmax circles, widened to stand in for the CSS blur
        const val DRIFT_X = 0.06f
        const val DRIFT_Y = 0.04f
        const val GROW = 0.12f
    }
}
