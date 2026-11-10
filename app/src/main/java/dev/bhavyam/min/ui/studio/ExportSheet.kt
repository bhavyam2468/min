package dev.bhavyam.min.ui.studio

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Fonts
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.theme.TokenExport
import dev.bhavyam.min.ui.common.MaxWidthLayout
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style

/**
 * "Export tokens": the current look as `globals.css` or `tokens.json`, with a copy button.
 * Rises over a dimmed studio on a spring; tap outside, the close button or back to dismiss.
 */
@SuppressLint("ViewConstructor")
class ExportSheet(context: Context, private val settings: SettingsStore, private val fonts: Fonts) : FrameLayout(context), Themed {

    private var theme: Theme? = null
    private var json = false

    private val title = TextView(context).apply { text = "Export tokens" }
    private val format = Segmented(context, listOf("globals.css", "tokens.json")) { index ->
        json = index == 1
        showCode()
    }
    private val copy = ToolButton(context, Icons.copy, COPY, "Copy", settings).apply { setOnClickListener { copyCode() } }
    private val close = ToolButton(context, Icons.close, null, "Close", settings).apply { setOnClickListener { hide() } }
    private val head = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(copy)
        addView(close)
    }
    private val code = TextView(context).apply { setTextIsSelectable(true) }
    private val codeBackground = GradientDrawable()
    private val codeScroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        background = codeBackground
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(code)
        })
    }
    private val sheetBackground = GradientDrawable()
    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = sheetBackground
        isClickable = true // taps on the sheet don't fall through to the scrim
        addView(head, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(format, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(codeScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Keeps the sheet a readable width on tablets and in landscape. */
    private val frame = MaxWidthLayout(context, Globals.Space.CONTENT_MAX_WIDTH).apply {
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val spring = Spring(PRECISION) { p ->
        val a = p.coerceIn(0f, 1f)
        setBackgroundColor(Theme.withAlpha(SCRIM, Globals.Alpha.SCRIM * a))
        sheet.alpha = a
        sheet.translationY = (1f - p) * RISE * resources.displayMetrics.density
        val s = 1f - (1f - p) * SHRINK
        sheet.scaleX = s
        sheet.scaleY = s
        if (p <= 0f && !isShowing) visibility = GONE
    }

    var isShowing = false
        private set

    init {
        visibility = GONE
        addView(frame)
        setOnClickListener { hide() }
    }

    fun show() {
        isShowing = true
        visibility = VISIBLE
        showCode()
        spring.animateTo(1f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    fun hide() {
        isShowing = false
        spring.animateTo(0f, settings.spring(SettingsRegistry.OVERLAY_MOTION))
    }

    fun reset() {
        isShowing = false
        spring.snapTo(0f)
    }

    private fun showCode() {
        val t = theme ?: return
        code.text = if (json) TokenExport.json(t) else TokenExport.css(t)
    }

    private fun copyCode() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(if (json) "tokens.json" else "globals.css", code.text))
        copy.label = COPIED
        copy.removeCallbacks(restoreCopy)
        copy.postDelayed(restoreCopy, COPIED_MILLIS)
    }

    private val restoreCopy = Runnable { copy.label = COPY }

    override fun applyTheme(theme: Theme) {
        this.theme = theme
        val d = resources.displayMetrics.density
        title.style(theme, Globals.Type.ROW, strong = true)
        format.applyTheme(theme)
        copy.applyTheme(theme)
        close.applyTheme(theme)
        code.style(theme, Globals.Type.CODE)
        code.typeface = fonts.typeface(MONO, MONO_WEIGHT)
        code.setLineSpacing(0f, CODE_LINE)
        val pad = (CODE_PAD * d).toInt()
        code.setPadding(pad, pad, pad, pad)
        codeBackground.setColor(theme.bg)
        codeBackground.cornerRadius = CODE_RADIUS * d
        sheetBackground.setColor(theme.surface)
        sheetBackground.setStroke(d.toInt().coerceAtLeast(1), theme.border)
        sheetBackground.cornerRadius = SHEET_RADIUS * d
        val gap = (GAP * d).toInt()
        head.setPadding((HEAD_PAD_START * d).toInt(), gap * 2, gap * 2, gap * 2)
        for (v in listOf(copy, close)) (v.layoutParams as MarginLayoutParams).marginStart = gap
        (format.layoutParams as MarginLayoutParams).setMargins(gap * 2, 0, gap * 2, gap * 2)
        (codeScroll.layoutParams as MarginLayoutParams).setMargins(gap * 2, 0, gap * 2, gap * 2)
        val edge = (Globals.Space.EDGE * d).toInt()
        frame.setPadding(edge, edge * 3, edge, edge * 3)
        sheet.elevation = theme.elevation
        if (isShowing) showCode()
    }

    /** A two-option pill whose highlight slides between options on a spring. */
    private inner class Segmented(context: Context, private val options: List<String>, private val onSelect: (Int) -> Unit) :
        View(context), Themed {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
        private val on = Paint(Paint.ANTI_ALIAS_FLAG)
        private val off = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var position = 0f
        private var selected = 0
        private val slide = Spring(PRECISION) { position = it; invalidate() }
        private val d = resources.displayMetrics.density

        override fun applyTheme(theme: Theme) {
            track.color = theme.surface2
            thumb.color = theme.surface
            thumb.setShadowLayer(SEG_SHADOW * d, 0f, d, Theme.withAlpha(SCRIM, SEG_SHADOW_ALPHA))
            for (p in listOf(on, off)) {
                p.textSize = theme.dp(SEG_TEXT)
                p.typeface = theme.regular
            }
            on.color = theme.text
            off.color = theme.muted
            requestLayout()
            invalidate()
        }

        private fun segmentWidth() = options.maxOf { on.measureText(it) } + 2 * SEG_PAD_X * d

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension((segmentWidth() * options.size + 2 * SEG_INSET * d).toInt(), (Globals.Space.TOOL * d).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val inset = SEG_INSET * d
            val h = height.toFloat()
            rect.set(0f, 0f, width.toFloat(), h)
            canvas.drawRoundRect(rect, h / 2, h / 2, track)
            val w = segmentWidth()
            val x = inset + w * position
            rect.set(x, inset, x + w, h - inset)
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, thumb)
            options.forEachIndexed { i, text ->
                val p = if (i == selected) on else off
                val cx = inset + w * i + w / 2 - p.measureText(text) / 2
                canvas.drawText(text, cx, h / 2 - (p.ascent() + p.descent()) / 2, p)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                val i = ((e.x - SEG_INSET * d) / segmentWidth()).toInt().coerceIn(0, options.lastIndex)
                if (i != selected) {
                    selected = i
                    slide.animateTo(i.toFloat(), settings.spring(SettingsRegistry.CONTROL_MOTION))
                    onSelect(i)
                }
            }
            return true
        }
    }

    private companion object {
        const val COPY = "Copy"
        const val COPIED = "Copied"
        const val COPIED_MILLIS = 1400L
        const val MONO = 6 // JetBrains Mono in Fonts.ALL
        const val MONO_WEIGHT = 450
        const val CODE_LINE = 1.25f
        const val CODE_PAD = 16f
        const val CODE_RADIUS = 14f
        const val SHEET_RADIUS = 20f
        const val HEAD_PAD_START = 20f
        const val GAP = 7f
        const val RISE = 14f
        const val SHRINK = 0.02f
        const val SCRIM = 0xFF0A0A14.toInt()
        const val SEG_TEXT = 12.5f
        const val SEG_PAD_X = 12f
        const val SEG_INSET = 3f
        const val SEG_SHADOW = 3f
        const val SEG_SHADOW_ALPHA = 0.12f
        const val PRECISION = 0.001f
    }
}
