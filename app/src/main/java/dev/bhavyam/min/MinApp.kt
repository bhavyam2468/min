package dev.bhavyam.min

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.bhavyam.min.apps.AppIndex
import dev.bhavyam.min.apps.Favorites
import dev.bhavyam.min.apps.UsageStore
import dev.bhavyam.min.search.AppsProvider
import dev.bhavyam.min.search.ContactsProvider
import dev.bhavyam.min.search.ContactsSource
import dev.bhavyam.min.search.FallbackProvider
import dev.bhavyam.min.search.SearchEngine
import dev.bhavyam.min.search.SearchTargets
import dev.bhavyam.min.settings.SettingsStore
import dev.bhavyam.min.theme.Fonts
import dev.bhavyam.min.theme.ThemeStore
import java.util.concurrent.Executors

/** Process-wide state: data that outlives the activity and that the adb receiver can reach. */
class MinApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var theme: ThemeStore
        private set
    lateinit var favorites: Favorites
        private set
    lateinit var apps: AppIndex
        private set
    lateinit var contacts: ContactsSource
        private set
    lateinit var targets: SearchTargets
        private set
    lateinit var engine: SearchEngine
        private set

    /** The launcher activity while it exists. */
    var ui: LauncherUi? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val main = Handler(Looper.getMainLooper())
        val background = Executors.newSingleThreadExecutor()
        settings = SettingsStore(prefs)
        theme = ThemeStore(prefs, resources, Fonts(assets))
        favorites = Favorites(prefs)
        apps = AppIndex(this, UsageStore(prefs), background, main)
        contacts = ContactsSource(this, background, main)
        targets = SearchTargets(packageManager, prefs)
        engine = SearchEngine(listOf(AppsProvider(apps), ContactsProvider(contacts), FallbackProvider(targets)))

        apps.addListener {
            favorites.seedOnce(apps.apps)
            targets.refresh()
            engine.refresh()
        }
        contacts.addListener(engine::refresh)
        contacts.refresh()
    }

    companion object {
        private const val PREFS = "min"

        fun of(context: Context) = context.applicationContext as MinApp
    }
}
