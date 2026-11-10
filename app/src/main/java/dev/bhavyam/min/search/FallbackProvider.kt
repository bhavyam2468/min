package dev.bhavyam.min.search

import android.content.Context
import android.view.View
import dev.bhavyam.min.apps.LaunchOptions

/** "Search X on …" for every enabled target, in the user's order. Empty until something is typed. */
class FallbackProvider(private val targets: SearchTargets) : SearchProvider {

    private class TargetResult(val target: SearchTarget) : SearchResult {
        var query = ""
        override var title = ""

        override fun run(context: Context, source: View?) =
            context.startActivity(target.intent(query), LaunchOptions(source).bundle)
    }

    private val results = HashMap<String, TargetResult>()

    override val label = "Search"
    override val isAvailable get() = true

    /** Offers, not matches: always [FuzzyMatcher.NO_MATCH], so any real match elsewhere wins. */
    override fun query(query: Query, out: MutableList<SearchResult>): Int {
        if (query.isEmpty) return FuzzyMatcher.NO_MATCH
        for (target in targets.active) {
            val result = results.getOrPut(target.id) { TargetResult(target) }
            result.query = query.text
            result.title = "Search ${query.text} on ${target.name}"
            out += result
        }
        return FuzzyMatcher.NO_MATCH
    }
}
