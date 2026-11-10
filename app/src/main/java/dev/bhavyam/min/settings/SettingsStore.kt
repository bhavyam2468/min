package dev.bhavyam.min.settings

import android.content.SharedPreferences
import dev.bhavyam.min.motion.SpringSpec

/** Typed, persistent values for the registry's settings, with change listeners. */
class SettingsStore(private val prefs: SharedPreferences) {

    private val listeners = ArrayList<(SettingItem) -> Unit>()
    private val specs = HashMap<MotionSetting, SpringSpec>()

    operator fun get(setting: Toggle): Boolean = prefs.getBoolean(setting.key, setting.default)

    operator fun get(setting: Slider): Float = prefs.getFloat(setting.key, setting.default)

    operator fun set(setting: Toggle, value: Boolean) {
        prefs.edit().putBoolean(setting.key, value).apply()
        changed(setting)
    }

    operator fun set(setting: Slider, value: Float) {
        prefs.edit().putFloat(setting.key, value.coerceIn(setting.min, setting.max)).apply()
        changed(setting)
    }

    /** The spring for [motion], with the global speed applied (time scales as √stiffness). */
    fun spring(motion: MotionSetting): SpringSpec = specs.getOrPut(motion) {
        val speed = get(SettingsRegistry.ANIM_SPEED)
        SpringSpec(get(motion.stiffness) * speed * speed, 1f - get(motion.bounce))
    }

    fun addListener(listener: (SettingItem) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (SettingItem) -> Unit) {
        listeners -= listener
    }

    private fun changed(setting: SettingItem) {
        specs.clear()
        for (i in listeners.indices) listeners[i](setting)
    }
}
