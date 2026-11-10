package com.bhavyam.min.studio

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.ui.ThemedCard
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi

/**
 * The floating label that says what the finger is editing and its current value. It trails
 * the finger on a spring, sitting above it (the finger hides whatever is underneath) and
 * flipping below near the top edge.
 */
class Tip(context: Context) : FrameLayout(context) {

    private val motion = App.of(context).motion
    private val swatchShape = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val swatch = View(context).apply { background = swatchShape }
    private val title = ThemedText(context, Globals.TEXT_SUMMARY, strong = true)
    private val value = ThemedText(context, Globals.TEXT_SUMMARY)
    private val hint = ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED)
    private val x = Spring(0.5f) { translationX = it }
    private val y = Spring(0.5f) { translationY = it }
    private val shown = Spring(0.001f) { p ->
        alpha = p.coerceIn(0f, 1f)
        val s = 0.92f + 0.08f * p
        scaleX = s
        scaleY = s
    }
    private var placed = false

    init {
        visibility = GONE
        shown.onRest = { if (shown.target == 0f) visibility = GONE }
        val pad = dpi(12f)
        val head = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(swatch, LinearLayout.LayoutParams(dpi(10f), dpi(10f)).apply { marginEnd = dpi(8f) })
            addView(title)
            addView(value, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpi(10f)
            })
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3 / 4, pad, pad * 3 / 4)
            addView(head)
            addView(hint)
        }
        hint.maxWidth = dpi(TIP_MAX_WIDTH)
        addView(ThemedCard(context, raised = true).apply { addView(body) })
        isClickable = false
    }

    /** Fills in what's being edited; [swatchColor] shows the colour for colour zones. */
    fun describe(title: String, value: String, hint: String, swatchColor: Int?) {
        this.title.text = title
        this.value.text = value
        this.hint.text = hint
        swatch.visibility = if (swatchColor == null) GONE else VISIBLE
        if (swatchColor != null) swatchShape.setColor(swatchColor)
        // measured now so the first [follow] already knows the size to place
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
    }

    fun show() {
        visibility = VISIBLE
        shown.animateTo(1f, motion[SettingsRegistry.SPRING_POPUP])
    }

    fun hide() {
        placed = false
        shown.animateTo(0f, motion[SettingsRegistry.SPRING_OVERLAY])
    }

    /** Moves towards the finger at ([fx], [fy]) in the parent's coordinates. */
    fun follow(fx: Float, fy: Float) {
        val parent = parent as? View ?: return
        val w = measuredWidth
        val h = measuredHeight
        val margin = dp(Globals.EDGE / 2)
        val lift = dp(FINGER_CLEARANCE)
        val tx = (fx - w / 2f).coerceIn(margin, (parent.width - w - margin).coerceAtLeast(margin))
        val above = fy - lift - h
        val ty = if (above >= margin) above else fy + lift
        if (!placed) {
            placed = true
            x.snapTo(tx)
            y.snapTo(ty)
        } else {
            x.animateTo(tx, motion[SettingsRegistry.SPRING_OVERLAY])
            y.animateTo(ty, motion[SettingsRegistry.SPRING_OVERLAY])
        }
    }

    private companion object {
        const val FINGER_CLEARANCE = 64f
        const val TIP_MAX_WIDTH = 240f
    }
}
