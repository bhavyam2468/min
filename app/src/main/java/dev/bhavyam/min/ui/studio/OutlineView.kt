package dev.bhavyam.min.ui.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.home.Block
import dev.bhavyam.min.ui.home.HomeView

/**
 * What the editor draws over home: a dashed outline round each block, and where the finger is —
 * a solid ring on a touched block, a bar on a touched side, a dot on a touched corner (Token
 * Studio's highlights). While a block is lifted, a dot marks each spot it can snap to.
 */
class OutlineView(context: Context, private val home: HomeView) : View(context) {

    private val d = resources.displayMetrics.density
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        pathEffect = DashPathEffect(floatArrayOf(Globals.Space.OUTLINE_DASH * d, Globals.Space.OUTLINE_DASH * d), 0f)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING * d
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val target = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var hit: Hit? = null
    private var targets: Array<RectF>? = null
    private var activeTarget = -1

    fun applyTheme(theme: Theme) {
        dashed.color = Theme.withAlpha(theme.text, Globals.Alpha.OUTLINE)
        ring.color = theme.text
        mark.color = theme.text
        target.color = theme.text
        invalidate()
    }

    fun highlight(hit: Hit?) {
        this.hit = hit
        invalidate()
    }

    /** Snap spots (one rect per anchor) while a block is lifted, null otherwise. */
    fun setTargets(targets: Array<RectF>?, active: Int) {
        this.targets = targets
        activeTarget = active
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = Globals.Space.OUTLINE_PAD * d
        val r = RADIUS * d
        val h = hit
        for (block in Block.entries) {
            home.bounds(block, rect)
            if (rect.isEmpty) continue
            rect.inset(-pad, -pad)
            val touched = h != null && h.block == block
            canvas.drawRoundRect(rect, r, r, if (touched && h.part == Part.Body) ring else dashed)
            if (touched && h.part != Part.Body) drawPart(canvas, h.part)
        }
        targets?.let { drawTargets(canvas, it) }
    }

    /** A bar along the touched side, or a dot on the touched corner, of [rect]. */
    private fun drawPart(canvas: Canvas, part: Part) {
        val x = when (part.sx) { -1 -> rect.left; 1 -> rect.right; else -> rect.centerX() }
        val y = when (part.sy) { -1 -> rect.top; 1 -> rect.bottom; else -> rect.centerY() }
        if (part.isCorner) {
            canvas.drawCircle(x, y, CORNER_DOT * d / 2, mark)
            return
        }
        val half = BAR * d / 2
        if (part.sx != 0) {
            val len = rect.height() * SIDE_BAR / 2
            canvas.drawRoundRect(x - half, y - len, x + half, y + len, half, half, mark)
        } else {
            val len = rect.width() * END_BAR / 2
            canvas.drawRoundRect(x - len, y - half, x + len, y + half, half, half, mark)
        }
    }

    private fun drawTargets(canvas: Canvas, rects: Array<RectF>) {
        val dot = Globals.Space.ANCHOR_DOT * d / 2
        for (i in rects.indices) {
            val active = i == activeTarget
            target.alpha = if (active) OPAQUE else (OPAQUE * Globals.Alpha.OUTLINE).toInt()
            canvas.drawCircle(rects[i].centerX(), rects[i].centerY(), if (active) dot * ACTIVE_GROWTH else dot, target)
        }
    }

    private companion object {
        const val RING = 1.5f
        const val RADIUS = 10f
        const val BAR = 3f
        const val CORNER_DOT = 10f
        const val SIDE_BAR = 0.52f
        const val END_BAR = 0.4f
        const val ACTIVE_GROWTH = 1.6f
        const val OPAQUE = 255
    }
}
