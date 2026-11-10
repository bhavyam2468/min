package com.bhavyam.min.cli

import com.bhavyam.min.apps.AppEntry

/**
 * What the adb CLI may do to the live launcher. The activity implements it while it exists;
 * commands that need it answer with an error when the launcher isn't running.
 */
interface LauncherUi {
    val searchOpen: Boolean
    val settingsOpen: Boolean
    fun openSearch(query: String)
    fun closeSearch()
    fun openSettings()
    fun closeSettings()
    fun launch(app: AppEntry)
}
