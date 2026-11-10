package com.bhavyam.min.search

import com.bhavyam.min.Signal

/**
 * Runs a query through every provider into reusable per-provider result lists. The UI and the
 * adb CLI both read [pages], so the CLI reports exactly what a user would see.
 */
class SearchEngine(val providers: List<SearchProvider>) {

    class Page(val provider: SearchProvider) {
        val items = ArrayList<SearchItem>()

        /** How well this page's best item matched; pages compete on it. */
        var best = Fuzzy.NONE
    }

    private val all = providers.map(::Page)

    /** Pages that exist for the current query, in order. */
    val pages = ArrayList<Page>(providers.size)

    var query = Query("")
        private set

    /** Fires after every search, including refreshes caused by data changing. */
    val changed = Signal()

    fun search(raw: String) {
        query = Query(raw)
        pages.clear()
        for (page in all) {
            page.items.clear()
            if (page.provider.visible(query)) {
                page.best = page.provider.search(query, page.items)
                pages += page
            }
        }
        changed.emit()
    }

    /**
     * The page that should be on screen: the one holding the best match, and [current] when
     * it ties — the screen only moves when another category is genuinely closer.
     */
    fun bestPage(current: Int): Int {
        var best = current.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        for (i in pages.indices) if (pages[i].best > (pages.getOrNull(best)?.best ?: Fuzzy.NONE)) best = i
        return best
    }

    /** Re-run the current query, e.g. after apps or contacts change underneath it. */
    fun refresh() = search(query.text)
}
