package com.bhavyam.min.search

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.View
import com.bhavyam.min.Signal
import com.bhavyam.min.settings.Prefs
import java.util.concurrent.Executors

/**
 * SEARCH: "search X on …" for every enabled destination, in the user's order. The web is
 * first by default; the rest are only the [QUERY_TARGETS] that exist on this device.
 */
class FallbackProvider(private val context: Context, private val prefs: Prefs) : SearchProvider {

    /** One reusable row per destination; only the query changes per keystroke. */
    class Item(val target: QueryTarget) : SearchItem {
        var query = ""
        override val title get() = "search $query on ${target.label}"
        override fun run(launcher: Launcher, source: View?) = launcher.startIntent(target.intent(query), source)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var items: List<Item> = listOf(Item(QUERY_TARGETS.first()))
    val changed = Signal()

    override val label = "SEARCH"

    override fun visible(query: Query) = !query.isEmpty

    override fun search(query: Query, out: MutableList<SearchItem>) {
        val off = disabled()
        for (item in ordered()) {
            if (item.target.id in off) continue
            item.query = query.text
            out += item
        }
    }

    /** Installed destinations in the user's order, enabled or not (for settings). */
    fun ordered(): List<Item> {
        val order = prefs.list(ORDER) ?: return items
        return items.sortedBy { item -> order.indexOf(item.target.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    fun isEnabled(target: QueryTarget) = target.id !in disabled()

    fun setEnabled(target: QueryTarget, on: Boolean) {
        val off = disabled().toMutableSet()
        if (on) off -= target.id else off += target.id
        prefs.putList(DISABLED, off.toList())
        changed.emit()
    }

    fun setOrder(ids: List<String>) {
        prefs.putList(ORDER, ids)
        changed.emit()
    }

    /** Re-checks which destinations can take a query; run when apps change. */
    fun detect() {
        worker.execute {
            val pm = context.packageManager
            val found = QUERY_TARGETS.filter { t ->
                pm.resolveActivity(t.intent("min"), PackageManager.ResolveInfoFlags.of(0)) != null
            }
            main.post {
                items = found.map(::Item)
                changed.emit()
            }
        }
    }

    private fun disabled(): Set<String> = prefs.list(DISABLED)?.toSet() ?: emptySet()

    private companion object {
        const val ORDER = "providers.order"
        const val DISABLED = "providers.disabled"
    }
}
