package com.bhavyam.min.search

import com.bhavyam.min.apps.AppIndex

/** APPS: everything when the query is empty (most recent first), otherwise fuzzy-ranked. */
class AppsProvider(private val index: AppIndex) : SearchProvider {
    private val ranker = Ranker()

    override val label = "APPS"

    override fun visible(query: Query) = true

    override fun search(query: Query, out: MutableList<SearchItem>): Int {
        if (!query.isEmpty) return ranker.rank(query.norm, index.apps, { it.key }, out)
        out.addAll(index.apps)
        return Fuzzy.ANY
    }
}
