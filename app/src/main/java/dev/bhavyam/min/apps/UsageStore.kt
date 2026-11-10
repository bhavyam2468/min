package dev.bhavyam.min.apps

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/** When each app was last launched from here — the order of the empty search. */
class UsageStore(private val prefs: SharedPreferences) {

    private val lastUsed: HashMap<String, Long> = load()

    fun lastUsed(key: String): Long = lastUsed[key] ?: 0L

    fun record(key: String) {
        lastUsed[key] = System.currentTimeMillis()
        prefs.edit().putString(KEY, JSONObject(lastUsed as Map<*, *>).toString()).apply()
    }

    private fun load(): HashMap<String, Long> {
        val map = HashMap<String, Long>()
        val raw = prefs.getString(KEY, null) ?: return map
        try {
            val json = JSONObject(raw)
            for (k in json.keys()) map[k] = json.getLong(k)
        } catch (_: JSONException) {
            // A corrupt history only costs the recency order; start fresh.
        }
        return map
    }

    private companion object {
        const val KEY = "last_used"
    }
}
