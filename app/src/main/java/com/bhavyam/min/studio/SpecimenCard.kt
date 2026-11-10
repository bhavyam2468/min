package com.bhavyam.min.studio

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.home.ClockView
import com.bhavyam.min.theme.Fonts
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.theme.Theme
import com.bhavyam.min.theme.ThemeStore
import com.bhavyam.min.ui.Icon
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.ThemedCard
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi
import kotlin.math.roundToInt

/**
 * The launcher in miniature, drawn by the real views: the live clock, a type specimen and one
 * button per colour role — Home (favourites), Search (results), Info (the popup). Below it, the
 * elevation strip: the shadow's resting line, which you pull down to lift everything.
 */
class SpecimenCard(context: Context) : LinearLayout(context), ThemeStore.Listener {

    private val app = App.of(context)
    val card = ThemedCard(context)
    val clock = ClockView(context)
    val aa = ThemedText(context, Globals.TEXT_SPECIMEN)
    val meta = ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED)
    val buttons = linkedMapOf(
        Role.HOME to RoleButton(context, Role.HOME, Globals.TEXT_RESULT, Icon.HOME).apply { text = "Home" },
        Role.SEARCH to RoleButton(context, Role.SEARCH, Globals.TEXT_RESULT, Icon.SEARCH).apply { text = "Search" },
        Role.MENU to RoleButton(context, Role.MENU, Globals.TEXT_BUTTON, Icon.INFO).apply { text = "App info" },
    )
    private val stripShape = GradientDrawable()
    val strip = View(context).apply { background = stripShape }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = dpi(Globals.EDGE)
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(clock)
            val type = LinearLayout(context).apply {
                gravity = Gravity.BOTTOM
                addView(aa.apply { text = "Aa" })
                addView(meta, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dpi(12f)
                    bottomMargin = dpi(8f)
                })
            }
            addView(type, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(Globals.EDGE / 2)
                bottomMargin = dpi(Globals.EDGE / 2)
            })
            for (b in buttons.values) {
                addView(b, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpi(Globals.LIST_GAP * 3)
                })
            }
        }
        card.addView(body)
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(strip, LayoutParams(dpi(STRIP_WIDTH), dpi(4f)).apply { topMargin = dpi(STRIP_GAP) })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.themes.add(this)
    }

    override fun onDetachedFromWindow() {
        app.themes.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onTheme(theme: Theme) {
        val t = theme.tokens
        meta.text = "${Fonts.FAMILIES[t.font].name} · ${t.weight.roundToInt()}"
        stripShape.cornerRadius = dp(2f)
        stripShape.setColor(theme.border)
        strip.translationY = dp(STRIP_GAP) * t.elevation
    }

    private companion object {
        const val STRIP_WIDTH = 72f
        const val STRIP_GAP = 14f
    }
}
