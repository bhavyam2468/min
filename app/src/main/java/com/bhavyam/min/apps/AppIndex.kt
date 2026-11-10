package com.bhavyam.min.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.bhavyam.min.Signal
import java.text.Collator
import java.util.concurrent.Executors

/**
 * Every launchable activity on every profile, most recently used first (then alphabetical).
 * Loads on a worker thread, publishes on the main thread, and reloads whenever a package is
 * installed, removed, changed or (un)mounted.
 */
class AppIndex(context: Context, private val history: LaunchHistory) {

    var apps: List<AppEntry> = emptyList()
        private set
    var loaded = false
        private set
    val changed = Signal()

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        }, main)
    }

    fun reload() {
        worker.execute {
            val fresh = query()
            main.post { publish(fresh) }
        }
    }

    /** Synchronous load for the adb CLI when it wakes a cold process and needs answers now. */
    fun loadNow() = publish(query())

    fun find(id: String): AppEntry? = apps.firstOrNull { it.id == id }

    /** Records a launch and moves the app to the front of the recency order. */
    fun touch(app: AppEntry) {
        app.lastUsed = history.record(app.id)
        publish(sorted(apps))
    }

    private fun query(): List<AppEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (user in users.userProfiles) {
            val serial = users.getSerialNumberForUser(user)
            for (info in launcherApps.getActivityList(null, user)) {
                val entry = AppEntry(
                    label = info.label.toString().trim(),
                    component = info.componentName,
                    user = user,
                    userSerial = serial,
                    lastUsed = 0L,
                )
                if (seen.add(entry.id)) {
                    entry.lastUsed = history.lastUsed(entry.id)
                    out += entry
                }
            }
        }
        return sorted(out)
    }

    private fun sorted(list: List<AppEntry>): List<AppEntry> {
        val collator = Collator.getInstance()
        return list.sortedWith { a, b ->
            if (a.lastUsed != b.lastUsed) b.lastUsed.compareTo(a.lastUsed)
            else collator.compare(a.label, b.label)
        }
    }

    private fun publish(list: List<AppEntry>) {
        apps = list
        loaded = true
        changed.emit()
    }
}
