package com.bhavyam.min.apps

import android.content.Context

/** When each app was last opened from min. This is what "recently used" sorts by. */
class LaunchHistory(context: Context) {
    private val sp = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun lastUsed(id: String): Long = sp.getLong(id, 0L)

    fun record(id: String): Long {
        val now = System.currentTimeMillis()
        sp.edit().putLong(id, now).apply()
        return now
    }
}
