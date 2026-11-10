package com.bhavyam.min.search

import com.bhavyam.min.Signal

/**
 * Runs a query through every provider into reusable per-provider result lists. The UI and the
 * adb CLI both read [pages], so the CLI reports exactly what a user would see.
 */
class SearchEngine(val providers: List<SearchProvider>) {

    class Page(val provider: SearchProvider) {
        val items = ArrayList<SearchItem>()
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
                page.provider.search(query, page.items)
                pages += page
            }
        }
        changed.emit()
    }

    /** Re-run the current query, e.g. after apps or contacts change underneath it. */
    fun refresh() = search(query.text)
}
