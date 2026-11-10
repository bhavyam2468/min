package com.bhavyam.min

import android.app.Application
import android.content.Context
import com.bhavyam.min.apps.AppIndex
import com.bhavyam.min.apps.Favorites
import com.bhavyam.min.apps.LaunchHistory
import com.bhavyam.min.cli.LauncherUi
import com.bhavyam.min.motion.Motion
import com.bhavyam.min.search.AppsProvider
import com.bhavyam.min.search.ContactsProvider
import com.bhavyam.min.search.FallbackProvider
import com.bhavyam.min.search.SearchEngine
import com.bhavyam.min.settings.Prefs
import com.bhavyam.min.theme.Fonts
import com.bhavyam.min.theme.ThemeStore

/**
 * Process-wide state, shared by the activity and the adb receiver. Data loads start here so
 * the first frame of the launcher already has apps to show.
 */
class App : Application() {

    val prefs by lazy { Prefs(this) }
    val motion by lazy { Motion(prefs) }
    val themes by lazy { ThemeStore(prefs, Fonts(assets)) }
    val apps by lazy { AppIndex(this, LaunchHistory(this)) }
    val favorites by lazy { Favorites(prefs, apps) }
    val contacts by lazy { ContactsProvider(this) }
    val fallback by lazy { FallbackProvider(this, prefs) }
    val search by lazy { SearchEngine(listOf(AppsProvider(apps), contacts, fallback)) }

    /** The live launcher UI while the activity exists; the CLI drives the app through it. */
    var ui: LauncherUi? = null

    override fun onCreate() {
        super.onCreate()
        favorites // seeds itself from the first app load
        apps.changed += {
            fallback.detect()
            search.refresh()
        }
        contacts.changed += search::refresh
        fallback.changed += search::refresh
        apps.reload()
        contacts.reload()
    }

    companion object {
        fun of(context: Context) = context.applicationContext as App
    }
}
