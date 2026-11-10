package dev.bhavyam.min.apps

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.view.View
import dev.bhavyam.min.MinApp
import dev.bhavyam.min.search.SearchKey
import dev.bhavyam.min.search.SearchResult

/** A launchable activity. [key] is stable across reloads and is what favorites and usage store. */
class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val key: String,
) : SearchResult {
    val searchKey = SearchKey(label)

    override val title get() = label

    override fun run(context: Context, source: View?) = MinApp.of(context).apps.launch(this, source)
}
