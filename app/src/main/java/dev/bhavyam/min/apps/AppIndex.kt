package dev.bhavyam.min.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import java.util.concurrent.Executor

/**
 * Every launchable activity of every profile, most recently used first. Loaded on [background],
 * published on [main], and reloaded whenever a package is installed, removed or changed.
 */
class AppIndex(
    context: Context,
    private val usage: UsageStore,
    private val background: Executor,
    private val main: Handler,
) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val listeners = ArrayList<() -> Unit>()
    private var generation = 0
    private var byKey: Map<String, AppEntry> = emptyMap()

    var apps: List<AppEntry> = emptyList()
        private set
    var isLoaded = false
        private set

    private val recency = Comparator<AppEntry> { a, b ->
        val byUse = usage.lastUsed(b.key).compareTo(usage.lastUsed(a.key))
        if (byUse != 0) byUse else String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label)
    }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(packageCallback, main)
        reload()
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    operator fun get(key: String): AppEntry? = byKey[key]

    fun launch(app: AppEntry, source: View?) {
        val options = LaunchOptions(source)
        launcherApps.startMainActivity(app.component, app.user, options.bounds, options.bundle)
        usage.record(app.key)
        publish(apps)
    }

    fun openInfo(app: AppEntry, source: View?) {
        val options = LaunchOptions(source)
        launcherApps.startAppDetailsActivity(app.component, app.user, options.bounds, options.bundle)
    }

    private fun reload() {
        val ticket = ++generation
        background.execute {
            val loaded = load()
            main.post { if (ticket == generation) publish(loaded) }
        }
    }

    private fun load(): List<AppEntry> {
        val unique = LinkedHashMap<String, AppEntry>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            for (info in launcherApps.getActivityList(null, user)) {
                val key = "${info.componentName.flattenToShortString()}#$serial"
                unique.getOrPut(key) { AppEntry(info.label.toString(), info.componentName, user, key) }
            }
        }
        return unique.values.toList()
    }

    private fun publish(loaded: List<AppEntry>) {
        apps = loaded.sortedWith(recency)
        byKey = apps.associateBy { it.key }
        isLoaded = true
        for (i in listeners.indices) listeners[i]()
    }
}
