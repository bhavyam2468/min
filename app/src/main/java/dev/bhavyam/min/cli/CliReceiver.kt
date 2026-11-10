package dev.bhavyam.min.cli

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bhavyam.min.MinApp

/**
 * The adb entry point. Runs one command synchronously on the main thread and replies through the
 * broadcast's result data, so `am broadcast` prints it. Never throws: a failure is a readable
 * `error: …` line and result code 1.
 *
 *     adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'add_favorite chat gpt'"
 *
 * The outer double quotes are for your shell, the inner single quotes for the device shell — without
 * them a command with spaces arrives cut at the first space.
 */
class CliReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reply = try {
            Commands(MinApp.of(context)).run(intent.getStringExtra(EXTRA).orEmpty())
        } catch (e: RuntimeException) {
            Commands.Reply.error("${e.javaClass.simpleName}: ${e.message}")
        }
        resultCode = if (reply.ok) Activity.RESULT_OK else RESULT_ERROR
        resultData = reply.text
    }

    private companion object {
        const val EXTRA = "cmd"
        const val RESULT_ERROR = 1
    }
}
