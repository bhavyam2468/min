package com.bhavyam.min.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.View
import com.bhavyam.min.Signal
import java.text.Collator
import java.util.concurrent.Executors

/**
 * CONTACTS: people with a phone number, kept in memory so matching is as instant as apps.
 * The page only exists once the contacts permission is granted. Running a contact calls it —
 * directly when the call permission is granted, otherwise through the pre-filled dialer.
 */
class ContactsProvider(private val context: Context) : SearchProvider {

    class Contact(val name: String, private val number: String, private val canCall: () -> Boolean) : SearchItem {
        val key = FuzzyKey(name)
        override val title get() = name
        override fun run(launcher: Launcher, source: View?) {
            val action = if (canCall()) Intent.ACTION_CALL else Intent.ACTION_DIAL
            launcher.startIntent(Intent(action, Uri.fromParts("tel", number, null)), source)
        }
    }

    private var contacts: List<Contact> = emptyList()
    private val ranker = Ranker()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var observing = false
    val changed = Signal()

    override val label = "CONTACTS"

    val granted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private val canCall = {
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }

    override fun visible(query: Query) = granted

    override fun search(query: Query, out: MutableList<SearchItem>) {
        if (query.isEmpty) out.addAll(contacts)
        else ranker.rank(query.norm, contacts, { it.key }, out)
    }

    /** Starts loading if the permission arrived since (dialog or system settings); cheap otherwise. */
    fun ensureLoaded() {
        if (granted && !observing) reload()
    }

    /** (Re)loads contacts off the main thread; the content observer keeps it current after. */
    fun reload() {
        if (!granted) return
        if (!observing) {
            observing = true
            context.contentResolver.registerContentObserver(Phone.CONTENT_URI, true, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = reload()
            })
        }
        worker.execute {
            val fresh = query()
            main.post {
                contacts = fresh
                changed.emit()
            }
        }
    }

    private fun query(): List<Contact> {
        val byId = LinkedHashMap<Long, Contact>()
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.IS_SUPER_PRIMARY)
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.trim().orEmpty()
                val number = c.getString(2) ?: continue
                // one row per person: their primary number if they have one, else the first
                if (name.isNotEmpty() && (id !in byId || c.getInt(3) != 0)) byId[id] = Contact(name, number, canCall)
            }
        }
        val collator = Collator.getInstance()
        return byId.values.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
}
