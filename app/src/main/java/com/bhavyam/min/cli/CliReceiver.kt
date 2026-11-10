package com.bhavyam.min.cli

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bhavyam.min.App

/**
 * adb control. One string extra, `cmd`, holds the whole command line; the answer comes back
 * as the broadcast's result data, which `am broadcast` prints. Runs synchronously on the main
 * thread, so commands touch the live UI directly, and a failure is an answer, never a crash.
 *
 *     adb shell am broadcast -n com.bhavyam.min/.cli.CliReceiver -a com.bhavyam.min.CLI \
 *         --es cmd "'add_favorite Google Maps'"
 *
 * The outer quotes are for your shell, the inner ones for the device shell: without them a
 * command with spaces arrives cut off at the first space.
 */
class CliReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val answer = try {
            Commands(App.of(context)).run(intent.getStringExtra(EXTRA))
        } catch (e: Exception) {
            "error: ${e.message ?: e.javaClass.simpleName}"
        }
        resultCode = if (answer.startsWith("error:")) Activity.RESULT_CANCELED else Activity.RESULT_OK
        resultData = answer
    }

    private companion object {
        const val EXTRA = "cmd"
    }
}
