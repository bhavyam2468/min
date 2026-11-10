package dev.bhavyam.min.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.ButtonDrawable
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.style

/**
 * The favorite apps on home: one row each, in the `appStyle` button look, stacked towards the side
 * of home they are anchored to. Row padding and corners are in em of the row text, so they grow
 * with the `appSize` token.
 */
@SuppressLint("ViewConstructor")
class AppsView(
    context: Context,
    private val settings: SettingsStore,
    private val onClick: OnClickListener,
    private val onLongClick: OnLongClickListener,
) : LinearLayout(context), Themed {

    private var theme: Theme? = null

    /**
     * Space between this view's side and the row text. Plain and ghost rows have no visible edge,
     * so home lines their text, not their padding, up with the screen edge; others line up their shape.
     */
    var inset = 0f
        private set

    init {
        orientation = VERTICAL
    }

    fun setApps(apps: List<AppEntry>) {
        while (childCount > apps.size) removeViewAt(childCount - 1)
        while (childCount < apps.size) addView(Row(context, settings).apply {
            setOnClickListener(onClick)
            setOnLongClickListener(onLongClick)
        })
        apps.forEachIndexed { i, app ->
            val row = getChildAt(i) as Row
            row.text = app.label
            row.tag = app
        }
        theme?.let(::applyTheme)
    }

    @SuppressLint("RtlHardcoded") // anchors are places on the screen, not reading directions
    override fun applyTheme(theme: Theme) {
        this.theme = theme
        val t = theme.tokens
        val variant = theme.appStyle
        fun rowEm(v: Float) = theme.em(v * t.appSize * ROW_EM)
        val padX = rowEm(t.padX).toInt()
        val padY = rowEm(t.padY).toInt()
        val radius = if (theme.isPill) Float.MAX_VALUE else rowEm(t.radius)
        val gap = if (variant.hasShape) theme.em(t.appSize * Globals.Space.APP_ROW_GAP).toInt() else 0
        inset = if (variant.hasShape) 0f else padX.toFloat()
        gravity = when (t.appAnchor.column) {
            0 -> Gravity.LEFT
            1 -> Gravity.CENTER_HORIZONTAL
            else -> Gravity.RIGHT
        }
        for (i in 0 until childCount) {
            val row = getChildAt(i) as Row
            row.button.style(theme, variant, radius)
            row.press.color = theme.pressed
            row.press.radius = radius
            row.style(theme, t.appSize, row.button.foreground)
            row.setPadding(padX, padY, padX, padY)
            (row.layoutParams as LayoutParams).topMargin = if (i == 0) 0 else gap
        }
        requestLayout()
    }

    private class Row(context: Context, settings: SettingsStore) : TextView(context) {
        val button = ButtonDrawable()
        val press = PressDrawable(settings)

        init {
            background = LayerDrawable(arrayOf(button, press))
            isSingleLine = true
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }

    private companion object {
        /** Row padding and radius tokens are in em of this fraction of the row text. */
        const val ROW_EM = 0.45f
    }
}
