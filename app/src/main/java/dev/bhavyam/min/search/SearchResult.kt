package dev.bhavyam.min.search

import android.content.Context
import android.view.View

/** One line in a result column: what it says and what tapping it does. */
interface SearchResult {
    val title: String

    /** Start what this result stands for. [source] is the tapped view (it anchors the launch animation). */
    fun run(context: Context, source: View?)
}
