package dev.bhavyam.min.apps

import android.content.SharedPreferences
import java.util.Locale

/** The ordered favorites on home, persisted by app key. */
class Favorites(private val prefs: SharedPreferences) {

    private val keys: MutableList<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
    private val listeners = ArrayList<() -> Unit>()

    val all: List<String> get() = keys

    operator fun contains(key: String) = key in keys

    fun add(key: String) {
        if (key in keys) return
        keys += key
        save()
    }

    fun remove(key: String) {
        if (keys.remove(key)) save()
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /**
     * On a fresh install, pick a few common apps by name: the first label found from each group,
     * anything missing skipped. Runs once; after that favorites are only what the user chose.
     */
    fun seedOnce(apps: List<AppEntry>) {
        if (prefs.getBoolean(SEEDED, false) || apps.isEmpty()) return
        for (names in DEFAULTS) {
            val app = names.firstNotNullOfOrNull { name -> apps.firstOrNull { it.label.lowercase(Locale.ROOT) == name } }
            if (app != null && app.key !in keys) keys += app.key
        }
        prefs.edit().putBoolean(SEEDED, true).apply()
        save()
    }

    private fun save() {
        prefs.edit().putString(KEY, keys.joinToString(SEPARATOR)).apply()
        for (i in listeners.indices) listeners[i]()
    }

    private companion object {
        const val KEY = "favorites"
        const val SEEDED = "favorites_seeded"
        const val SEPARATOR = "\n"

        val DEFAULTS = listOf(
            listOf("phone", "dialer"),
            listOf("messages", "messaging", "sms"),
            listOf("chrome", "browser", "firefox", "internet", "brave"),
            listOf("camera"),
            listOf("photos", "gallery"),
        )
    }
}
