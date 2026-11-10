package dev.bhavyam.min.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import java.util.concurrent.Executor

/**
 * An in-memory snapshot of contacts that have a phone number (so matching is a pure CPU loop).
 * Only touches the provider once contacts permission is granted; reloads when contacts change.
 */
class ContactsSource(
    private val context: Context,
    private val background: Executor,
    private val main: Handler,
) {
    var contacts: List<Contact> = emptyList()
        private set

    private val listeners = ArrayList<() -> Unit>()
    private var observing = false
    private var generation = 0
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    val isAllowed get() =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Load (or reload) if permitted; a no-op otherwise. Safe to call whenever permission may have changed. */
    fun refresh() {
        if (!isAllowed) {
            publish(emptyList())
            return
        }
        if (!observing) {
            context.contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
            observing = true
        }
        val ticket = ++generation
        background.execute {
            val loaded = load()
            main.post { if (ticket == generation) publish(loaded) }
        }
    }

    private fun publish(loaded: List<Contact>) {
        contacts = loaded
        for (i in listeners.indices) listeners[i]()
    }

    private fun load(): List<Contact> {
        val byId = LinkedHashMap<Long, Contact>()
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.IS_SUPER_PRIMARY)
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE")
            ?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val number = c.getString(2) ?: continue
                    if (id !in byId || c.getInt(3) != 0) byId[id] = Contact(name, number)
                }
            }
        return byId.values.toList()
    }
}
