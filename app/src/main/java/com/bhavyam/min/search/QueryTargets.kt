package com.bhavyam.min.search

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.bhavyam.min.theme.Globals

/**
 * Places a query can be sent, as data. [pkg] is null only for the web, which goes to the
 * default browser. Every other target is offered only if that app is installed *and* declares
 * it handles the intent we build — checked with the real intent, not just the package name.
 * Adding a destination is adding a line here.
 */
class QueryTarget(
    val id: String,
    val label: String,
    val pkg: String?,
    private val build: (String) -> Intent,
) {
    fun intent(query: String): Intent = build(query).also { if (pkg != null) it.setPackage(pkg) }
}

private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
private fun search(query: String) = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, query)
private fun enc(query: String): String = Uri.encode(query)

val QUERY_TARGETS = listOf(
    QueryTarget("web", "the web", null) { view(Globals.WEB_SEARCH + enc(it)) },
    QueryTarget("youtube", "YouTube", "com.google.android.youtube") { search(it) },
    QueryTarget("youtube_music", "YouTube Music", "com.google.android.apps.youtube.music") { search(it) },
    QueryTarget("spotify", "Spotify", "com.spotify.music") { view("spotify:search:" + enc(it)) },
    QueryTarget("maps", "Google Maps", "com.google.android.apps.maps") { view("geo:0,0?q=" + enc(it)) },
    QueryTarget("play", "Play Store", "com.android.vending") { view("market://search?q=" + enc(it)) },
    QueryTarget("amazon", "Amazon", "in.amazon.mShop.android.shopping") { view("https://www.amazon.in/s?k=" + enc(it)) },
    QueryTarget("amazon_global", "Amazon", "com.amazon.mShop.android.shopping") { view("https://www.amazon.com/s?k=" + enc(it)) },
    QueryTarget("flipkart", "Flipkart", "com.flipkart.android") { view("https://www.flipkart.com/search?q=" + enc(it)) },
    QueryTarget("reddit", "Reddit", "com.reddit.frontpage") { view("https://www.reddit.com/search/?q=" + enc(it)) },
    QueryTarget("wikipedia", "Wikipedia", "org.wikipedia") { view("https://en.wikipedia.org/w/index.php?search=" + enc(it)) },
    QueryTarget("x", "X", "com.twitter.android") { view("https://x.com/search?q=" + enc(it)) },
    QueryTarget("pinterest", "Pinterest", "com.pinterest") { view("https://www.pinterest.com/search/pins/?q=" + enc(it)) },
    QueryTarget("github", "GitHub", "com.github.android") { view("https://github.com/search?q=" + enc(it)) },
)
