package com.bhavyam.min.apps

import com.bhavyam.min.Signal
import com.bhavyam.min.settings.Prefs

/**
 * The home screen list, in order, saved permanently. A fresh install is seeded once with a
 * few common apps matched by name; anything not on the device is skipped. After that it is
 * whatever the user set.
 */
class Favorites(private val prefs: Prefs, private val index: AppIndex) {

    private var ids: MutableList<String> = prefs.list(KEY)?.toMutableList() ?: mutableListOf()
    val changed = Signal()

    init {
        index.changed += {
            if (prefs.list(KEY) == null && index.apps.isNotEmpty()) seed()
            changed.emit()
        }
    }

    /** Favourites that are currently installed, in the user's order. */
    fun apps(): List<AppEntry> = ids.mapNotNull(index::find)

    operator fun contains(app: AppEntry) = app.id in ids

    fun add(app: AppEntry): Boolean {
        if (app in this) return false
        ids += app.id
        save()
        return true
    }

    fun remove(app: AppEntry): Boolean {
        if (!ids.remove(app.id)) return false
        save()
        return true
    }

    private fun seed() {
        val apps = index.apps
        ids = DEFAULTS.mapNotNull { names ->
            names.firstNotNullOfOrNull { name -> apps.firstOrNull { it.label.equals(name, ignoreCase = true) } }?.id
        }.distinct().toMutableList()
        prefs.putList(KEY, ids)
    }

    private fun save() {
        prefs.putList(KEY, ids)
        changed.emit()
    }

    private companion object {
        const val KEY = "favorites"

        /** Each slot lists the names it may go by on different devices, in preference order. */
        val DEFAULTS = listOf(
            listOf("Phone", "Dialer", "Calls"),
            listOf("Messages", "Messaging", "SMS"),
            listOf("Chrome", "Browser", "Internet", "Samsung Internet", "Firefox", "Brave"),
            listOf("Camera"),
            listOf("Photos", "Gallery"),
        )
    }
}
