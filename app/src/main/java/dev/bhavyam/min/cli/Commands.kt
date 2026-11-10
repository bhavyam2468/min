package dev.bhavyam.min.cli

import dev.bhavyam.min.LauncherUi
import dev.bhavyam.min.MinApp
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.settings.SettingItem
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.Slider
import dev.bhavyam.min.settings.Toggle

/** The adb command table: each command is a name, its arguments, a line of help and what it does. */
class Commands(private val app: MinApp) {

    class Reply private constructor(val ok: Boolean, val text: String) {
        companion object {
            fun ok(text: String) = Reply(true, text)
            fun error(text: String) = Reply(false, "error: $text")
        }
    }

    private class Command(val name: String, val args: String, val help: String, val run: (String) -> Reply)

    private val commands: List<Command> = listOf(
        Command("help", "", "this list") { Reply.ok(help()) },
        Command("status", "", "what the launcher is showing, and counts") { Reply.ok(status()) },
        Command("search", "<query>", "results per category, best first, as the search screen ranks them") { q ->
            if (q.isEmpty()) Reply.error("usage: search <query>") else Reply.ok(search(q))
        },
        Command("open_app", "<name>", "launch the best matching app") { q ->
            withApp(q) { found ->
                val ui = app.ui
                if (ui != null) ui.run(found) else app.apps.launch(found, null)
                Reply.ok("ok: opened ${found.label}")
            }
        },
        Command("list_favorites", "", "favorites, top to bottom") {
            Reply.ok(app.favorites.all.mapNotNull { app.apps[it]?.label }.joinToString("\n").ifEmpty { "(no favorites)" })
        },
        Command("add_favorite", "<name>", "add the best matching app to favorites") { q ->
            withApp(q) { found ->
                if (found.key in app.favorites) Reply.ok("ok: ${found.label} is already a favorite")
                else {
                    app.favorites.add(found.key)
                    Reply.ok("ok: added ${found.label} to favorites")
                }
            }
        },
        Command("remove_favorite", "<name>", "remove the best matching favorite") { q ->
            val favorite = app.favorites.all.mapNotNull { app.apps[it] }
            withApp(q, favorite) { found ->
                app.favorites.remove(found.key)
                Reply.ok("ok: removed ${found.label} from favorites")
            }
        },
        Command("open_settings", "", "show settings") { withUi { it.openSettings(); Reply.ok("ok: settings open") } },
        Command("close_settings", "", "hide settings") { withUi { it.closeSettings(); Reply.ok("ok: settings closed") } },
        Command("open_search", "[query]", "pull down search, optionally typing a query") { q ->
            withUi { it.openSearch(q); Reply.ok(if (q.isEmpty()) "ok: search open" else "ok: search open for \"$q\"") }
        },
        Command("close_search", "", "push search back up") { withUi { it.closeSearch(); Reply.ok("ok: search closed") } },
        Command("get_setting", "[key]", "one setting, or all of them") { key ->
            if (key.isEmpty()) Reply.ok(SettingsRegistry.values.joinToString("\n") { "${it.key} = ${valueOf(it)}" })
            else setting(key)?.let { Reply.ok("${it.key} = ${valueOf(it)}") } ?: unknownSetting(key)
        },
        Command("set_setting", "<key> <value>", "change a setting (get_setting lists the keys)") { args ->
            val parts = args.split(WHITESPACE, limit = 2)
            if (parts.size < 2) Reply.error("usage: set_setting <key> <value>") else setSetting(parts[0], parts[1])
        },
    )

    fun run(line: String): Reply {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return Reply.error("no command given (try: help)")
        val parts = trimmed.split(WHITESPACE, limit = 2)
        val command = commands.firstOrNull { it.name == parts[0].lowercase() }
            ?: return Reply.error("unknown command \"${parts[0]}\" (try: help)")
        return command.run(parts.getOrElse(1) { "" }.trim())
    }

    private fun help(): String = commands.joinToString("\n") { c ->
        "${"${c.name} ${c.args}".trim().padEnd(HELP_COLUMN)} ${c.help}"
    }

    private fun status(): String {
        val ui = app.ui
        return listOf(
            "launcher: " + if (ui == null) "not running" else "running",
            "search: " + when {
                ui == null -> "-"
                ui.isSearchOpen -> "open \"${ui.query}\""
                else -> "closed"
            },
            "settings: " + if (ui?.isSettingsOpen == true) "open" else "closed",
            "apps: ${app.apps.apps.size}" + if (app.apps.isLoaded) "" else " (loading)",
            "favorites: ${app.favorites.all.size}",
            "contacts: " + if (app.contacts.isAllowed) "${app.contacts.contacts.size}" else "no permission",
        ).joinToString("\n")
    }

    /** Runs [query] through the shared engine, then puts the engine back on the screen's query. */
    private fun search(query: String): String {
        val engine = app.engine
        val previous = engine.query.text
        engine.search(query)
        val best = "best: " + engine.columns[engine.best].provider.label.lowercase() + "\n"
        val text = best + engine.columns.filter { it.provider.isAvailable }.joinToString("\n") { column ->
            val lines = column.results.take(RESULT_LIMIT).joinToString("") { "\n  ${it.title}" }
            column.provider.label.uppercase() + lines.ifEmpty { "\n  (none)" }
        }
        engine.search(previous)
        return text
    }

    /** The best match for [query] among [among], ranked exactly as the apps column ranks. */
    private fun withApp(query: String, among: List<AppEntry>? = null, action: (AppEntry) -> Reply): Reply {
        if (query.isEmpty()) return Reply.error("which app? give a name")
        if (!app.apps.isLoaded) return Reply.error("apps are still loading, try again")
        val engine = app.engine
        val previous = engine.query.text
        engine.search(query)
        val found = engine.columns.first().results.filterIsInstance<AppEntry>().firstOrNull { among == null || it in among }
        engine.search(previous)
        return found?.let(action) ?: Reply.error("no app matches \"$query\"")
    }

    private fun withUi(action: (LauncherUi) -> Reply): Reply =
        app.ui?.let(action) ?: Reply.error("the launcher isn't running (press home once)")

    private fun setting(key: String) = SettingsRegistry.values.firstOrNull { it.key == key.lowercase() }

    private fun unknownSetting(key: String) =
        Reply.error("unknown setting \"$key\" (keys: ${SettingsRegistry.values.joinToString { it.key }})")

    private fun valueOf(item: SettingItem): String = when (item) {
        is Toggle -> if (app.settings[item]) "on" else "off"
        is Slider -> app.settings[item].toString()
        else -> "-"
    }

    private fun setSetting(key: String, raw: String): Reply {
        val item = setting(key) ?: return unknownSetting(key)
        val value = raw.trim().lowercase()
        when (item) {
            is Toggle -> app.settings[item] = when (value) {
                in TRUE -> true
                in FALSE -> false
                else -> return Reply.error("${item.key} is on/off, got \"$raw\"")
            }
            is Slider -> {
                val v = value.toFloatOrNull() ?: return Reply.error("${item.key} needs a number, got \"$raw\"")
                if (v < item.min || v > item.max) return Reply.error("${item.key} must be between ${item.min} and ${item.max}")
                app.settings[item] = v
            }
            else -> return unknownSetting(key)
        }
        return Reply.ok("ok: ${item.key} = ${valueOf(item)}")
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val TRUE = setOf("on", "true", "1", "yes")
        val FALSE = setOf("off", "false", "0", "no")
        const val RESULT_LIMIT = 8
        const val HELP_COLUMN = 30
    }
}
