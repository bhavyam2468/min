package com.bhavyam.min.apps

import android.content.ComponentName
import android.os.UserHandle
import android.view.View
import com.bhavyam.min.search.FuzzyKey
import com.bhavyam.min.search.Launcher
import com.bhavyam.min.search.SearchItem

/** A launchable activity: label + component (+ profile), prepared for matching. */
class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    userSerial: Long,
    var lastUsed: Long,
) : SearchItem {
    /** Stable identity across restarts — favourites and history are keyed by it. */
    val id: String = component.flattenToShortString() + "#" + userSerial
    val key = FuzzyKey(label)
    val packageName: String get() = component.packageName

    override val title: String get() = label
    override fun run(launcher: Launcher, source: View?) = launcher.launchApp(this, source)
}
