package com.bhavyam.min.search

import android.content.Intent
import android.view.View
import com.bhavyam.min.apps.AppEntry

/** How a result gets acted on. Implemented by the activity, which owns launch animations. */
interface Launcher {
    fun launchApp(app: AppEntry, source: View?)
    fun startIntent(intent: Intent, source: View?)
}

/** One line in a results list. */
interface SearchItem {
    val title: String
    fun run(launcher: Launcher, source: View?)
}

/** The query as typed (trimmed) and normalised for matching, computed once per keystroke. */
class Query(raw: String) {
    val text: String = raw.trim()
    val norm: String = text.lowercase()
    val isEmpty: Boolean get() = text.isEmpty()
}

/**
 * A search category — one horizontal page. Adding a category is one class implementing this
 * and one line in [SearchEngine]'s provider list.
 */
interface SearchProvider {
    /** The tiny label above the input, e.g. "APPS". */
    val label: String

    /** Whether the page exists right now (e.g. contacts need a permission). */
    fun visible(query: Query): Boolean

    /** Append results to [out], best first. */
    fun search(query: Query, out: MutableList<SearchItem>)
}
