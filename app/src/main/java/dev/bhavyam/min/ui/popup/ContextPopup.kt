package dev.bhavyam.min.ui.popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.icons.IconDrawable
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style

/**
 * The small grey app menu that springs out of the long-pressed label: favorite on/off and app info.
 * It floats over everything without moving anything; a tap anywhere else springs it away.
 */
@SuppressLint("ViewConstructor")
class ContextPopup(context: Context, private val settings: SettingsStore, private val host: Host) : FrameLayout(context), Themed {

    interface Host {
        fun isFavorite(app: AppEntry): Boolean
        fun toggleFavorite(app: AppEntry)
        fun openAppInfo(app: AppEntry, source: View)
    }

    private val favorite = Row(context, Icons.star)
    private val info = Row(context, Icons.info).apply { text = "App info" }
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(favorite)
        addView(info)
    }
    private val cardBackground = GradientDrawable()
    private var app: AppEntry? = null
    private var swallowing = false
    private val spring = Spring(PRECISION) { p ->
        val s = Globals.Motion.POPUP_SCALE_FROM + (1f - Globals.Motion.POPUP_SCALE_FROM) * p
        card.scaleX = s
        card.scaleY = s
        card.alpha = p.coerceIn(0f, 1f)
        if (p <= 0f && app == null) visibility = GONE
    }

    val isShowing get() = app != null

    init {
        visibility = GONE
        card.background = cardBackground
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        favorite.setOnClickListener { app?.let(host::toggleFavorite); dismiss() }
        info.setOnClickListener { v -> app?.let { host.openAppInfo(it, v) }; dismiss() }
    }

    /** Spring the menu out above [anchor] (below it if there's no room). [centered] matches centred lists. */
    fun show(app: AppEntry, anchor: View, centered: Boolean) {
        this.app = app
        favorite.text = if (host.isFavorite(app)) "Remove from favorites" else "Set as favorite"
        visibility = VISIBLE
        card.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        place(anchor, centered)
        spring.snapTo(0f)
        spring.animateTo(1f, settings.spring(SettingsRegistry.POPUP_MOTION))
    }

    fun dismiss() {
        if (app == null) return
        app = null
        spring.animateTo(0f, settings.spring(SettingsRegistry.POPUP_MOTION))
    }

    /** Gone at once (the launcher went to the background). */
    fun reset() {
        app = null
        spring.snapTo(0f)
    }

    private fun place(anchor: View, centered: Boolean) {
        val here = IntArray(2).also(::getLocationInWindow)
        val at = IntArray(2).also(anchor::getLocationInWindow)
        val ax = at[0] - here[0]
        val ay = at[1] - here[1]
        val w = card.measuredWidth
        val h = card.measuredHeight
        val gap = (Globals.Space.POPUP_GAP * resources.displayMetrics.density).toInt()
        val edge = (Globals.Space.EDGE * resources.displayMetrics.density).toInt()
        val safe = rootWindowInsets?.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
        val left = (safe?.left ?: 0) + edge
        val right = width - (safe?.right ?: 0) - edge - w
        val x = (if (centered) ax + (anchor.width - w) / 2 else ax).coerceIn(left, right.coerceAtLeast(left))
        val above = ay - h - gap
        val fitsAbove = above >= (safe?.top ?: 0)
        val y = if (fitsAbove) above else ay + anchor.height + gap
        (card.layoutParams as LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = x
            topMargin = y
        }
        card.pivotX = (ax + anchor.width / 2f - x).coerceIn(0f, w.toFloat())
        card.pivotY = if (fitsAbove) h.toFloat() else 0f
        card.requestLayout()
    }

    /** A touch outside the card closes the menu and goes no further. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowing = isShowing
            dismiss()
        }
        return swallowing
    }

    override fun applyTheme(theme: Theme) {
        cardBackground.setColor(theme.surface2)
        cardBackground.cornerRadius = theme.radiusCard
        card.elevation = theme.elevation
        card.outlineAmbientShadowColor = theme.shadow
        card.outlineSpotShadowColor = theme.shadow
        val pad = theme.em(POPUP_PADDING_EM).toInt()
        card.setPadding(pad, pad, pad, pad)
        favorite.applyTheme(theme)
        info.applyTheme(theme)
    }

    /** One option: an icon in the theme's icon style, then its label. */
    private inner class Row(context: Context, icon: Icons.Icon) : TextView(context), Themed {
        private val iconDrawable = IconDrawable(icon)
        private val press = PressDrawable(settings)

        init {
            background = press
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
            minWidth = (Globals.Space.POPUP_MIN_WIDTH * resources.displayMetrics.density).toInt()
        }

        override fun applyTheme(theme: Theme) {
            style(theme, Globals.Type.POPUP)
            iconDrawable.style(theme, theme.text)
            press.color = theme.pressed
            press.radius = theme.radiusButton
            val size = theme.em(Globals.Type.POPUP * ICON_EM).toInt()
            iconDrawable.setBounds(0, 0, size, size)
            setCompoundDrawablesRelative(iconDrawable, null, null, null)
            compoundDrawablePadding = theme.dp(Globals.Space.POPUP_ICON_GAP).toInt()
            setPadding(theme.em(theme.tokens.padX).toInt(), theme.em(theme.tokens.padY).toInt(), theme.em(theme.tokens.padX).toInt(), theme.em(theme.tokens.padY).toInt())
        }
    }

    private companion object {
        const val PRECISION = 0.002f
        const val POPUP_PADDING_EM = 0.3f
        const val ICON_EM = 1.15f
    }
}
