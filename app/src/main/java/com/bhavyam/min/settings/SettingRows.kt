package com.bhavyam.min.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.motion.SpringConfig
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.ui.Icon
import com.bhavyam.min.ui.Press
import com.bhavyam.min.ui.SpringSlider
import com.bhavyam.min.ui.SpringSwitch
import com.bhavyam.min.ui.ThemedControl
import com.bhavyam.min.ui.ThemedIcon
import com.bhavyam.min.ui.ThemedText
import com.bhavyam.min.ui.Tone
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi
import kotlin.math.abs

/** Below this a stored value and a control's value count as equal (no re-sync needed). */
private const val EPSILON = 1e-3f

/**
 * One settings row: a title over a muted summary, with room on the right ([end]) and below
 * ([below]) for the control. Rows that can be tapped sink like everything else.
 */
@SuppressLint("ViewConstructor")
open class SettingRow(context: Context, title: String, summary: String) : LinearLayout(context) {

    protected val prefs = App.of(context).prefs
    private val press = Press(this)
    private val line = LinearLayout(context)
    private val texts = LinearLayout(context)
    protected val summaryView = ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(Globals.TOUCH_ROW)
        val pad = dpi(Globals.EDGE / 2)
        setPadding(pad, pad, pad, pad)
        texts.orientation = VERTICAL
        texts.addView(ThemedText(context, Globals.TEXT_ROW).apply { text = title })
        summaryView.text = summary
        summaryView.visibility = if (summary.isEmpty()) GONE else VISIBLE
        texts.addView(summaryView)
        line.gravity = Gravity.CENTER_VERTICAL
        line.addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    protected fun start(view: View) =
        line.addView(view, 0, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(Globals.EDGE / 2) })

    protected fun end(view: View) = line.addView(view)

    protected fun below(view: View) =
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(4f) })

    protected fun setSummary(s: String) {
        summaryView.text = s
        summaryView.visibility = if (s.isEmpty()) GONE else VISIBLE
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        if (isClickable) press.set(pressed)
    }

    /** Re-reads the stored value, e.g. after adb changed it or a permission was granted. */
    open fun refresh() = Unit
}

/** A root entry: icon, title, summary and a chevron. */
@SuppressLint("ViewConstructor")
class EntryRow(context: Context, entry: Entry, onTap: () -> Unit) : SettingRow(context, entry.title, entry.summary) {
    init {
        start(ThemedIcon(context, entry.icon))
        end(ThemedIcon(context, Icon.CHEVRON, tone = Tone.MUTED))
        setOnClickListener { onTap() }
    }
}

@SuppressLint("ViewConstructor")
class ToggleRow(context: Context, private val setting: Toggle) : SettingRow(context, setting.title, setting.summary) {
    private val toggle = SpringSwitch(context)

    init {
        end(toggle)
        toggle.jump(prefs[setting])
        setOnClickListener {
            toggle.checked = !toggle.checked
            prefs[setting] = toggle.checked
        }
    }

    override fun refresh() {
        val v = prefs[setting]
        if (v != toggle.checked) toggle.checked = v
    }
}

@SuppressLint("ViewConstructor")
class SliderRow(context: Context, private val setting: Slider) : SettingRow(context, setting.title, setting.summary) {
    private val value = ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED)
    private val slider = SpringSlider(context, setting.min, setting.max) { v ->
        prefs[setting] = v
        value.text = setting.format(v)
    }

    init {
        end(value)
        below(slider)
        slider.jump(prefs[setting])
        value.text = setting.format(prefs[setting])
    }

    override fun refresh() {
        val v = prefs[setting]
        if (abs(v - slider.value) > EPSILON) {
            slider.jump(v)
            value.text = setting.format(v)
        }
    }
}

/**
 * A spring's two dials — stiffness (how quick) and bounce (1 − damping) — and a dot that
 * springs across its track with the new feel every time either dial moves.
 */
@SuppressLint("ViewConstructor")
class SpringRow(context: Context, private val setting: SpringSetting) : SettingRow(context, setting.title, setting.summary) {
    private val preview = Preview(context)
    private val stiffness = SpringSlider(context, STIFFNESS_MIN, STIFFNESS_MAX) { store() }
    private val bounce = SpringSlider(context, 0f, BOUNCE_MAX) { store() }

    init {
        below(preview)
        below(labelled("speed", stiffness))
        below(labelled("bounce", bounce))
        refresh()
    }

    private fun labelled(label: String, slider: SpringSlider) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ThemedText(context, Globals.TEXT_SUMMARY, Tone.MUTED).apply { text = label },
            LayoutParams(dpi(64f), LayoutParams.WRAP_CONTENT))
        addView(slider, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun store() {
        val config = SpringConfig(stiffness.value, 1f - bounce.value)
        prefs[setting] = config
        preview.kick(config)
    }

    override fun refresh() {
        val c = prefs[setting]
        if (abs(c.stiffness - stiffness.value) > EPSILON) stiffness.jump(c.stiffness)
        if (abs(1f - c.damping - bounce.value) > EPSILON) bounce.jump(1f - c.damping)
    }

    /** The feel made visible: a dot crossing its track on exactly this spring. */
    private class Preview(context: Context) : ThemedControl(context) {
        private val spring = Spring(0.001f) { invalidate() }
        private var side = 0f

        fun kick(config: SpringConfig) {
            if (!spring.isRunning || abs(spring.value - spring.target) < 0.5f) side = 1f - side
            spring.animateTo(side, config)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dpi(28f))

        override fun onDraw(canvas: Canvas) {
            val r = dp(7f)
            val left = dp(12f) + r
            val x = left + (width - left * 2) * spring.value
            paint.color = theme.surface2
            rect.set(left - r, height / 2f - dp(1f), width - left + r, height / 2f + dp(1f))
            canvas.drawRect(rect, paint)
            paint.color = theme.colors(Role.HOME).fill
            rect.set(x - r, height / 2f - r, x + r, height / 2f + r)
            val corner = radius(r * 2)
            canvas.drawRoundRect(rect, corner, corner, paint)
        }
    }

    private companion object {
        const val STIFFNESS_MIN = 60f
        const val STIFFNESS_MAX = 1600f
        const val BOUNCE_MAX = 0.7f // damping never below 0.3: beyond that it rings like a bell
    }
}

/** A row whose summary is live state, e.g. whether a permission is granted. */
@SuppressLint("ViewConstructor")
class ActionRow(context: Context, private val setting: Action, private val host: SettingsHost) :
    SettingRow(context, setting.title, "") {
    init {
        setOnClickListener { setting.run(host) }
        refresh()
    }

    override fun refresh() = setSummary(setting.state(host))
}

@SuppressLint("ViewConstructor")
class CustomRow(context: Context, setting: Custom) : SettingRow(context, setting.title, setting.summary) {
    init {
        below(setting.build(context))
    }
}

/** The right row for any [Setting]; the `when` is exhaustive, so a new type can't be forgotten. */
fun rowFor(context: Context, setting: Setting, host: SettingsHost): SettingRow = when (setting) {
    is Toggle -> ToggleRow(context, setting)
    is Slider -> SliderRow(context, setting)
    is SpringSetting -> SpringRow(context, setting)
    is Action -> ActionRow(context, setting, host)
    is Custom -> CustomRow(context, setting)
}
