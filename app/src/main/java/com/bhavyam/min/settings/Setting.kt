package com.bhavyam.min.settings

import android.app.Activity
import android.content.Context
import android.view.View
import com.bhavyam.min.motion.SpringConfig
import com.bhavyam.min.ui.Icon
import java.util.Locale

/** What a setting action can ask of the launcher. */
interface SettingsHost {
    val activity: Activity
    fun openStudio()
    fun requestPermission(permission: String)
    fun requestHomeRole()
}

/** One row in settings. The row type decides how [SettingsView] draws and edits it. */
sealed class Setting(val key: String, val title: String, val summary: String)

class Toggle(key: String, title: String, summary: String, val default: Boolean) :
    Setting(key, title, summary)

class Slider(
    key: String, title: String, summary: String,
    val min: Float, val max: Float, val default: Float,
    private val decimals: Int, private val unit: String,
) : Setting(key, title, summary) {
    fun clamp(v: Float) = v.coerceIn(min, max)
    fun format(v: Float): String = String.format(Locale.US, "%.${decimals}f", v) + unit
}

/** A spring's feel: stiffness and bounce, with a live preview. */
class SpringSetting(key: String, title: String, summary: String, val default: SpringConfig) :
    Setting(key, title, summary)

/** A tappable row whose summary reflects live state (e.g. a permission). */
class Action(
    key: String, title: String,
    val state: (SettingsHost) -> String,
    val run: (SettingsHost) -> Unit,
) : Setting(key, title, "")

/** A row that brings its own view. */
class Custom(key: String, title: String, summary: String, val build: (Context) -> View) :
    Setting(key, title, summary)

/** Top-level items on the settings root page. */
sealed class Entry(val title: String, val icon: Icon, val summary: String)

class Category(title: String, icon: Icon, summary: String, val settings: List<Setting>) :
    Entry(title, icon, summary)

/** A root item that goes somewhere instead of opening a page. */
class Link(title: String, icon: Icon, summary: String, val run: (SettingsHost) -> Unit) :
    Entry(title, icon, summary)
