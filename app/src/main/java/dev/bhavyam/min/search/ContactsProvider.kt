package dev.bhavyam.min.search

/** Contacts with a phone number; the column only exists once contacts permission is granted. */
class ContactsProvider(private val source: ContactsSource) : SearchProvider {
    private val ranker = Ranker<Contact> { it.searchKey }

    override val label = "Contacts"
    override val isAvailable get() = source.isAllowed

    override fun query(query: Query, out: MutableList<SearchResult>): Int {
        if (!query.isEmpty) return ranker.rank(query.normalized, source.contacts, out)
        out.addAll(source.contacts)
        return FuzzyMatcher.NO_MATCH
    }
}
