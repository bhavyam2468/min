package com.bhavyam.min.cli

import android.app.role.RoleManager
import com.bhavyam.min.App
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.search.Ranker
import com.bhavyam.min.settings.Setting
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.settings.Slider
import com.bhavyam.min.settings.Toggle
import java.util.Locale

/**
 * The adb command set, as data: a name, an argument hint, one line of help and what it does.
 * Every command answers with one readable line (or a short list) — `ok: …` when something
 * happened, `error: …` when it couldn't — and never throws past [run].
 */
class Commands(private val app: App) {

    private class Command(val name: String, val args: String, val help: String, val run: (String) -> String)

    private val ranker = Ranker()

    private val all: List<Command> = listOf(
        Command("help", "", "this list") { help() },
        Command("status", "", "what the launcher is showing and knows about") { status() },
        Command("search", "<query>", "the category search would show, then results per category, best first") { search(it) },
        Command("open_app", "<name|package>", "launch the best-matching app") { arg -> withApp(arg) { launch(it) } },
        Command("list_favorites", "", "favourites in home-screen order") { listFavorites() },
        Command("add_favorite", "<name|package>", "pin an app to home") { arg ->
            withApp(arg) { if (app.favorites.add(it)) "ok: added ${it.label} to favorites" else "ok: ${it.label} is already a favorite" }
        },
        Command("remove_favorite", "<name|package>", "unpin an app from home") { arg ->
            withApp(arg) { if (app.favorites.remove(it)) "ok: removed ${it.label} from favorites" else "ok: ${it.label} wasn't a favorite" }
        },
        Command("open_settings", "", "show the settings overlay") { withUi { it.openSettings(); "ok: settings open" } },
        Command("close_settings", "", "hide the settings overlay") { withUi { it.closeSettings(); "ok: settings closed" } },
        Command("open_search", "[query]", "open search, optionally typing a query") { q ->
            withUi { it.openSearch(q); if (q.isEmpty()) "ok: search open" else "ok: search open with \"$q\"" }
        },
        Command("close_search", "", "back to home") { withUi { it.closeSearch(); "ok: search closed" } },
        Command("get_setting", "[key]", "one setting's value, or all of them") { getSetting(it) },
        Command("set_setting", "<key> <value>", "change a setting (true/false, or a number)") { setSetting(it) },
    )

    /** Parses and runs one command line. */
    fun run(line: String?): String {
        val text = line?.trim().orEmpty()
        if (text.isEmpty()) return "error: no command — pass one with --es cmd \"'<command> [args]'\"; try 'help'"
        val name = text.substringBefore(' ').lowercase(Locale.ROOT)
        val arg = text.substringAfter(' ', "").trim()
        val command = all.firstOrNull { it.name == name } ?: return "error: unknown command '$name'; try 'help'"
        return try {
            command.run(arg)
        } catch (e: RuntimeException) {
            "error: $name failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun help(): String = all.joinToString("\n") { c ->
        val usage = if (c.args.isEmpty()) c.name else "${c.name} ${c.args}"
        "${usage.padEnd(USAGE_WIDTH)} ${c.help}"
    }

    private fun status(): String {
        val ui = app.ui
        val roles = app.getSystemService(RoleManager::class.java)
        return listOf(
            "launcher: " + if (ui == null) "not running" else "running",
            "default home: " + if (roles.isRoleHeld(RoleManager.ROLE_HOME)) "yes" else "no",
            "search: " + if (ui?.searchOpen == true) "open" else "closed",
            "settings: " + if (ui?.settingsOpen == true) "open" else "closed",
            "apps: ${apps().size}",
            "favorites: ${app.favorites.apps().size}",
            "contacts: " + if (app.contacts.granted) "granted" else "not granted",
        ).joinToString("\n")
    }

    /** Runs the query through the shared engine, then puts the screen's own query back. */
    private fun search(query: String): String {
        if (query.isEmpty()) return "error: search needs a query, e.g. search chat"
        apps()
        val engine = app.search
        val previous = engine.query.text
        engine.search(query)
        val best = engine.pages.getOrNull(engine.bestPage(0))?.provider?.label ?: "(none)"
        val out = "best: $best\n" + engine.pages.joinToString("\n") { page ->
            val items = page.items.take(RESULTS_PER_PAGE).joinToString(", ") { it.title }
            "${page.provider.label}: ${items.ifEmpty { "(no matches)" }}"
        }
        engine.search(previous)
        return out
    }

    private fun launch(entry: AppEntry): String {
        val ui = app.ui ?: return "error: the launcher isn't running, so it can't start ${entry.label}"
        ui.launch(entry)
        return "ok: opened ${entry.label}"
    }

    private fun listFavorites(): String {
        apps()
        val favorites = app.favorites.apps()
        if (favorites.isEmpty()) return "(no favorites)"
        return favorites.mapIndexed { i, a -> "${i + 1}. ${a.label} (${a.packageName})" }.joinToString("\n")
    }

    private fun getSetting(key: String): String {
        if (key.isEmpty()) return SettingsRegistry.SIMPLE.joinToString("\n") { "${it.key} = ${valueOf(it)}" }
        val s = setting(key) ?: return unknownSetting(key)
        return "${s.key} = ${valueOf(s)}"
    }

    private fun setSetting(arg: String): String {
        val key = arg.substringBefore(' ')
        val raw = arg.substringAfter(' ', "").trim()
        if (key.isEmpty() || raw.isEmpty()) return "error: usage: set_setting <key> <value>"
        val s = setting(key) ?: return unknownSetting(key)
        when (s) {
            is Toggle -> app.prefs[s] = when (raw.lowercase(Locale.ROOT)) {
                "true", "on", "1", "yes" -> true
                "false", "off", "0", "no" -> false
                else -> return "error: ${s.key} is on/off — use true or false"
            }
            is Slider -> {
                // a unit typed after the number ("1.5x", "28dp") is fine
                val v = raw.trimEnd { !it.isDigit() }.toFloatOrNull()
                    ?: return "error: ${s.key} takes a number from ${s.format(s.min)} to ${s.format(s.max)}"
                app.prefs[s] = v
            }
            else -> return "error: ${s.key} can't be set from adb"
        }
        return "ok: ${s.key} = ${valueOf(s)}"
    }

    private fun setting(key: String): Setting? = SettingsRegistry.SIMPLE.firstOrNull { it.key.equals(key, ignoreCase = true) }

    private fun unknownSetting(key: String) =
        "error: unknown setting '$key'; one of: ${SettingsRegistry.SIMPLE.joinToString(", ") { it.key }}"

    private fun valueOf(s: Setting): String = when (s) {
        is Toggle -> if (app.prefs[s]) "true" else "false"
        is Slider -> s.format(app.prefs[s])
        else -> "—"
    }

    /* ------------------------------------------------------------ helpers */

    /** The app index, loaded right now if the process just started for this command. */
    private fun apps(): List<AppEntry> {
        if (!app.apps.loaded) app.apps.loadNow()
        return app.apps.apps
    }

    /**
     * An app by package name, exact label, or else the best fuzzy match — the same matcher as
     * search, so `open_app chrme` finds Chrome and `open_app xyz` finds nothing.
     */
    private fun withApp(query: String, action: (AppEntry) -> String): String {
        if (query.isEmpty()) return "error: name an app, e.g. Chrome or com.android.chrome"
        val all = apps()
        val exact = all.firstOrNull { it.packageName.equals(query, ignoreCase = true) || it.label.equals(query, ignoreCase = true) }
        val entry = exact ?: run {
            val ranked = ArrayList<AppEntry>()
            ranker.rank(query.lowercase(), all, { it.key }, ranked)
            ranked.firstOrNull()
        }
        return if (entry == null) "error: no app matches '$query'" else action(entry)
    }

    private fun withUi(action: (LauncherUi) -> String): String {
        val ui = app.ui ?: return "error: the launcher isn't running — press home first"
        return action(ui)
    }

    private companion object {
        const val RESULTS_PER_PAGE = 10
        const val USAGE_WIDTH = 32
    }
}
