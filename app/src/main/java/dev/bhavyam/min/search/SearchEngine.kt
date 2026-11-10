package dev.bhavyam.min.search

/**
 * Runs a query through every provider into reusable per-column result lists, and picks the column
 * with the closest result — search is universal, the columns only group it. Pure and synchronous:
 * the UI and the adb `search` command see exactly the same thing.
 */
class SearchEngine(providers: List<SearchProvider>) {

    class Column(val provider: SearchProvider) {
        val results = ArrayList<SearchResult>()

        /** The best result's score ([FuzzyMatcher.NO_MATCH] if nothing matched). */
        var score = FuzzyMatcher.NO_MATCH
            internal set
    }

    val columns = providers.map(::Column)

    var query = Query("")
        private set

    /**
     * The column holding the closest result: the best score wins (the earlier column on a tie);
     * with no match anywhere, the first column with anything to show (browsing, then fallbacks).
     */
    var best = 0
        private set

    fun search(raw: CharSequence) {
        query = Query(raw)
        var bestScore = FuzzyMatcher.NO_MATCH
        best = -1
        for (i in columns.indices) {
            val column = columns[i]
            column.results.clear()
            column.score = if (column.provider.isAvailable) column.provider.query(query, column.results) else FuzzyMatcher.NO_MATCH
            if (column.score > bestScore) {
                bestScore = column.score
                best = i
            }
        }
        if (best < 0) best = columns.indexOfFirst { it.results.isNotEmpty() }.coerceAtLeast(0)
    }

    /** Re-run the current query (after apps, contacts or targets change underneath it). */
    fun refresh() = search(query.text)
}
