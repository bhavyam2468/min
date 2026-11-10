package dev.bhavyam.min

import dev.bhavyam.min.search.SearchResult

/** What the visible launcher exposes to code outside it (the adb command line). */
interface LauncherUi {
    val isSearchOpen: Boolean
    val isSettingsOpen: Boolean
    val query: String

    fun openSearch(query: String)
    fun closeSearch()
    fun openSettings()
    fun closeSettings()

    /** Run a result the way a tap would, launch animation included. */
    fun run(result: SearchResult)
}
