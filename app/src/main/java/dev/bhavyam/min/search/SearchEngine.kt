package dev.bhavyam.min.search

/**
 * Runs a query through every provider into reusable per-column result lists. Pure and synchronous:
 * the UI and the adb `search` command see exactly the same thing.
 */
class SearchEngine(providers: List<SearchProvider>) {

    class Column(val provider: SearchProvider) {
        val results = ArrayList<SearchResult>()
    }

    val columns = providers.map(::Column)

    var query = Query("")
        private set

    fun search(raw: CharSequence) {
        query = Query(raw)
        for (column in columns) {
            column.results.clear()
            if (column.provider.isAvailable) column.provider.query(query, column.results)
        }
    }

    /** Re-run the current query (after apps, contacts or targets change underneath it). */
    fun refresh() = search(query.text)
}
