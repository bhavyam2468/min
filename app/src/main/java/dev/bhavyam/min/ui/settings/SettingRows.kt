package dev.bhavyam.min.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.bhavyam.min.Globals
import dev.bhavyam.min.icons.Icons
import dev.bhavyam.min.search.FuzzyMatcher
import dev.bhavyam.min.search.SearchKey
import dev.bhavyam.min.search.SearchTargets
import dev.bhavyam.min.settings.Action
import dev.bhavyam.min.settings.Permission
import dev.bhavyam.min.settings.ProviderOrder
import dev.bhavyam.min.settings.SettingItem
import dev.bhavyam.min.settings.SettingsHost
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.settings.Slider
import dev.bhavyam.min.settings.Toggle
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.common.IconView
import dev.bhavyam.min.ui.common.PressDrawable
import dev.bhavyam.min.ui.common.Themed
import dev.bhavyam.min.ui.common.TokenButton
import dev.bhavyam.min.ui.common.style

/**
 * One row per kind of setting. A row knows how to draw its control, how to re-read its value
 * ([refresh]) and whether it matches a settings search.
 */
@SuppressLint("ViewConstructor")
sealed class SettingRow(
    context: Context,
    val item: SettingItem,
    searchContext: List<String>,
    protected val settings: SettingsStore,
) : LinearLayout(context), Themed {

    protected val title = TextView(context).apply { text = item.title }
    protected val summary = TextView(context).apply {
        text = item.summary
        visibility = if (item.summary == null) GONE else VISIBLE
    }
    protected val texts = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(title)
        addView(summary)
    }
    private val keys = (listOf(item.title) + listOfNotNull(item.summary) + searchContext).map(::SearchKey)
    protected val press = PressDrawable(settings)

    init {
        gravity = Gravity.CENTER_VERTICAL
    }

    fun matches(query: String) = query.isEmpty() || keys.any { FuzzyMatcher.score(query, it) != FuzzyMatcher.NO_MATCH }

    /** Re-read state that may have changed elsewhere (adb, permissions, installs). */
    open fun refresh() = Unit

    override fun applyTheme(theme: Theme) {
        title.style(theme, Globals.Type.ROW)
        summary.style(theme, Globals.Type.ROW_SUMMARY, theme.muted)
        press.color = theme.pressed
        press.radius = theme.radiusButton
        minimumHeight = theme.dp(Globals.Space.ROW_MIN).toInt()
        val padX = paddingX(theme)
        val padY = theme.em(theme.tokens.padY * ROW_PAD_Y).toInt()
        setPadding(padX, padY, padX, padY)
    }

    /** Text on the left, [controls] on the right. */
    protected fun horizontal(vararg controls: View) {
        orientation = HORIZONTAL
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val gap = (Globals.Space.CONTROL_GAP * resources.displayMetrics.density).toInt()
        for (c in controls) addView(c, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
    }

    class ToggleRow(context: Context, private val toggle: Toggle, keys: List<String>, settings: SettingsStore) :
        SettingRow(context, toggle, keys, settings) {
        private val switch = SwitchView(context, settings)

        init {
            horizontal(switch)
            background = press
            setOnClickListener {
                settings[toggle] = !settings[toggle]
                switch.setChecked(settings[toggle], animate = true)
            }
            refresh()
        }

        override fun refresh() {
            if (switch.isChecked != settings[toggle]) switch.setChecked(settings[toggle], animate = isAttachedToWindow)
        }

        override fun applyTheme(theme: Theme) {
            super.applyTheme(theme)
            switch.applyTheme(theme)
        }
    }

    class SliderRow(context: Context, private val setting: Slider, keys: List<String>, settings: SettingsStore) :
        SettingRow(context, setting, keys, settings) {
        private val value = TextView(context)
        private val slider = SliderView(context, setting, settings) { v ->
            settings[setting] = v
            value.text = setting.format(v)
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.NO_GRAVITY
            val top = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(value)
            }
            addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            refresh()
        }

        override fun refresh() {
            val v = settings[setting]
            if (v != slider.value || value.text.isEmpty()) {
                slider.setValue(v)
                value.text = setting.format(v)
            }
        }

        override fun applyTheme(theme: Theme) {
            super.applyTheme(theme)
            value.style(theme, Globals.Type.ROW_SUMMARY, theme.muted)
            slider.applyTheme(theme)
        }
    }

    class ActionRow(context: Context, item: Action, keys: List<String>, settings: SettingsStore, host: SettingsHost) :
        SettingRow(context, item, keys, settings) {
        private val chevron = IconView(context, Icons.chevron, Globals.Space.ICON, muted = true)

        init {
            horizontal(chevron)
            background = press
            setOnClickListener { item.run(host) }
        }

        override fun applyTheme(theme: Theme) {
            super.applyTheme(theme)
            chevron.applyTheme(theme)
        }
    }

    class PermissionRow(context: Context, private val permission: Permission, keys: List<String>, settings: SettingsStore, host: SettingsHost) :
        SettingRow(context, permission, keys, settings) {
        private val grant = TokenButton(context, settings).apply {
            text = "Allow"
            setOnClickListener { host.requestPermission(permission.permission) }
        }
        private val granted = TextView(context).apply { text = "Allowed" }

        init {
            horizontal(grant, granted)
            refresh()
        }

        override fun refresh() {
            val ok = context.checkSelfPermission(permission.permission) == PackageManager.PERMISSION_GRANTED
            grant.visibility = if (ok) GONE else VISIBLE
            granted.visibility = if (ok) VISIBLE else GONE
        }

        override fun applyTheme(theme: Theme) {
            super.applyTheme(theme)
            grant.applyTheme(theme)
            granted.style(theme, Globals.Type.ROW_SUMMARY, theme.muted)
        }
    }

    class ProvidersRow(context: Context, item: ProviderOrder, keys: List<String>, settings: SettingsStore, targets: SearchTargets) :
        SettingRow(context, item, keys + targets.installed.map { it.name }, settings) {
        private val list = ProviderListView(context, targets, settings)

        init {
            orientation = VERTICAL
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            refresh()
        }

        override fun refresh() = list.rebuild()

        override fun applyTheme(theme: Theme) {
            list.applyTheme(theme)
        }
    }

    companion object {
        private const val ROW_PAD_X = 0.7f
        private const val ROW_PAD_Y = 0.6f

        /** Horizontal text inset of a row, following the padX token. */
        fun paddingX(theme: Theme) = theme.em(theme.tokens.padX * ROW_PAD_X).toInt()
    }
}
