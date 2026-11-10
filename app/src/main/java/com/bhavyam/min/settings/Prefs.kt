package com.bhavyam.min.settings

import android.content.Context
import android.content.SharedPreferences
import com.bhavyam.min.motion.SpringConfig

/**
 * Settings storage. Typed accessors take the registry entry itself, so a setting's key,
 * default and range are defined exactly once (in [SettingsRegistry]).
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("min", Context.MODE_PRIVATE)
    private val observers = ArrayList<(String) -> Unit>()
    private val relay = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) for (o in observers.toTypedArray()) o(key)
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(relay)
    }

    operator fun get(s: Toggle): Boolean = sp.getBoolean(s.key, s.default)
    operator fun set(s: Toggle, v: Boolean) = sp.edit().putBoolean(s.key, v).apply()

    operator fun get(s: Slider): Float = sp.getFloat(s.key, s.default)
    operator fun set(s: Slider, v: Float) = sp.edit().putFloat(s.key, s.clamp(v)).apply()

    operator fun get(s: SpringSetting) = SpringConfig(
        stiffness = sp.getFloat(s.key + STIFFNESS, s.default.stiffness),
        damping = sp.getFloat(s.key + DAMPING, s.default.damping),
    )

    operator fun set(s: SpringSetting, c: SpringConfig) =
        sp.edit().putFloat(s.key + STIFFNESS, c.stiffness).putFloat(s.key + DAMPING, c.damping).apply()

    fun string(key: String): String? = sp.getString(key, null)
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()

    /** Ordered string lists (favourites, provider order). Null means "never saved". */
    fun list(key: String): List<String>? =
        sp.getString(key, null)?.let { s -> if (s.isEmpty()) emptyList() else s.split(SEPARATOR) }

    fun putList(key: String, values: List<String>) = putString(key, values.joinToString(SEPARATOR))

    /** [observer] gets the key of every setting that changes, from anywhere (UI or adb). */
    fun observe(observer: (String) -> Unit) {
        observers += observer
    }

    fun unobserve(observer: (String) -> Unit) {
        observers -= observer
    }

    private companion object {
        const val STIFFNESS = ".stiffness"
        const val DAMPING = ".damping"
        const val SEPARATOR = "\n"
    }
}
