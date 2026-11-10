package dev.bhavyam.min.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import dev.bhavyam.min.apps.LaunchOptions

/** A contact with a phone number. Running it calls them (or opens the dialer without call permission). */
class Contact(val name: String, private val number: String) : SearchResult {
    val searchKey = SearchKey(name)

    override val title get() = name

    override fun run(context: Context, source: View?) {
        val canCall = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, LaunchOptions(source).bundle)
    }
}
