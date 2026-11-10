package com.bhavyam.min.search

import com.bhavyam.min.apps.AppIndex

/** APPS: everything when the query is empty (most recent first), otherwise fuzzy-ranked. */
class AppsProvider(private val index: AppIndex) : SearchProvider {
    private val ranker = Ranker()

    override val label = "APPS"

    override fun visible(query: Query) = true

    override fun search(query: Query, out: MutableList<SearchItem>) {
        if (query.isEmpty) out.addAll(index.apps)
        else ranker.rank(query.norm, index.apps, { it.key }, out)
    }
}
