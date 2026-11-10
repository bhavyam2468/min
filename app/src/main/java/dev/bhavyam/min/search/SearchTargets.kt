package dev.bhavyam.min.search

import android.app.SearchManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri

/** Somewhere a query can be sent: the web, or an installed app's own search. */
class SearchTarget(val id: String, val name: String, private val packageName: String?, private val build: (String) -> Intent) {
    fun intent(query: String): Intent = build(query).apply {
        packageName?.let(::setPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * The fallback providers that actually work on this device, in the user's order, each switchable.
 * A target is offered only if its search intent resolves, so nothing here can be a dead end.
 */
class SearchTargets(private val packageManager: PackageManager, private val prefs: SharedPreferences) {

    /** Every target that resolves on this device, in the user's order (enabled or not). */
    var installed: List<SearchTarget> = emptyList()
        private set

    /** The enabled subset of [installed] — what the search column offers. */
    var active: List<SearchTarget> = emptyList()
        private set

    private val disabled: MutableSet<String> = prefs.getString(DISABLED, "")!!.split(',').filter { it.isNotEmpty() }.toMutableSet()

    fun isEnabled(target: SearchTarget) = target.id !in disabled

    fun setEnabled(target: SearchTarget, enabled: Boolean) {
        if (enabled) disabled -= target.id else disabled += target.id
        prefs.edit().putString(DISABLED, disabled.joinToString(",")).apply()
        publish(installed)
    }

    /** Move the target at [from] to [to] (indices into [installed]). */
    fun move(from: Int, to: Int) {
        val list = installed.toMutableList()
        list.add(to, list.removeAt(from))
        val ids = list.map { it.id }
        val rest = order().filter { it !in ids }
        prefs.edit().putString(ORDER, (ids + rest).joinToString(",")).apply()
        publish(list)
    }

    /** Re-detect which targets resolve (call when packages change). */
    fun refresh() {
        val order = order()
        publish(CATALOG.filter { packageManager.resolveActivity(it.intent(PROBE), PackageManager.MATCH_DEFAULT_ONLY) != null }
            .sortedBy { order.indexOf(it.id) })
    }

    private fun publish(list: List<SearchTarget>) {
        installed = list
        active = list.filter(::isEnabled)
    }

    /** Saved order, with targets newer than the saved order appended in catalog order. */
    private fun order(): List<String> {
        val saved = prefs.getString(ORDER, "")!!.split(',').filter { it.isNotEmpty() }
        return saved + CATALOG.map { it.id }.filter { it !in saved }
    }

    private companion object {
        const val ORDER = "target_order"
        const val DISABLED = "target_disabled"
        const val PROBE = "min"

        /** Open `prefix + encoded query`, the cleanest search entry point most apps expose. */
        fun uri(prefix: String): (String) -> Intent = { q -> Intent(Intent.ACTION_VIEW, Uri.parse(prefix + Uri.encode(q))) }

        val CATALOG = listOf(
            SearchTarget("web", "the web", null, uri("https://www.google.com/search?q=")),
            SearchTarget("youtube", "YouTube", "com.google.android.youtube") { q ->
                Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, q)
            },
            SearchTarget("spotify", "Spotify", "com.spotify.music", uri("spotify:search:")),
            SearchTarget("maps", "Google Maps", "com.google.android.apps.maps", uri("geo:0,0?q=")),
            SearchTarget("play", "Play Store", "com.android.vending", uri("market://search?q=")),
            SearchTarget("ytmusic", "YouTube Music", "com.google.android.apps.youtube.music", uri("https://music.youtube.com/search?q=")),
            SearchTarget("amazon", "Amazon", "com.amazon.mShop.android.shopping", uri("https://www.amazon.com/s?k=")),
            SearchTarget("flipkart", "Flipkart", "com.flipkart.android", uri("https://www.flipkart.com/search?q=")),
            SearchTarget("reddit", "Reddit", "com.reddit.frontpage", uri("https://www.reddit.com/search/?q=")),
            SearchTarget("wikipedia", "Wikipedia", "org.wikipedia", uri("https://en.wikipedia.org/wiki/Special:Search?search=")),
            SearchTarget("x", "X", "com.twitter.android", uri("https://x.com/search?q=")),
            SearchTarget("pinterest", "Pinterest", "com.pinterest", uri("https://www.pinterest.com/search/pins/?q=")),
            SearchTarget("github", "GitHub", "com.github.android", uri("https://github.com/search?q=")),
        )
    }
}
