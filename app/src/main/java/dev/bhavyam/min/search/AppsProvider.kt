package dev.bhavyam.min.search

import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.apps.AppIndex

/** Installed apps. Browsing shows every app, most recently used first. */
class AppsProvider(private val index: AppIndex) : SearchProvider {
    private val ranker = Ranker<AppEntry> { it.searchKey }

    override val label = "Apps"
    override val isAvailable get() = true

    override fun query(query: Query, out: MutableList<SearchResult>) {
        if (query.isEmpty) out.addAll(index.apps) else ranker.rank(query.normalized, index.apps, out)
    }
}
