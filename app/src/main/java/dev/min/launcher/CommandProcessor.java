package dev.min.launcher;

/** Parser for the synchronous ADB broadcast protocol. */
public final class CommandProcessor {
    public interface Target {
        String status();
        String search(String query);
        String openApp(String selector);
        String listFavorites();
        String addFavorite(String selector);
        String removeFavorite(String selector);
        String openSettings();
        String closeSettings();
        String openSearch(String query);
        String closeSearch();
        String getSetting(String key);
        String setSetting(String key, String value);
    }

    private CommandProcessor() { }

    public static String execute(String command, Target target) {
        if (command == null || command.trim().isEmpty()) {
            return "error: cmd is required; use help";
        }
        String trimmed = command.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String verb = parts[0].toLowerCase();
        String rest = parts.length == 2 ? parts[1].trim() : "";
        switch (verb) {
            case "help":
                return "help: status; search <query>; open_app <name-or-package>; list_favorites; "
                        + "add_favorite <name-or-package>; remove_favorite <name-or-package>; open_settings; "
                        + "close_settings; open_search [query]; close_search; get_setting <status_bar|blur|anim_speed>; "
                        + "set_setting <status_bar|blur|anim_speed> <value>. Quote cmd twice in adb shell when it has spaces.";
            case "status":
                return target.status();
            case "search":
                return rest.isEmpty() ? "error: search requires a query" : target.search(rest);
            case "open_app":
                return requires(rest, "open_app", target::openApp);
            case "list_favorites":
                return target.listFavorites();
            case "add_favorite":
                return requires(rest, "add_favorite", target::addFavorite);
            case "remove_favorite":
                return requires(rest, "remove_favorite", target::removeFavorite);
            case "open_settings":
                return target.openSettings();
            case "close_settings":
                return target.closeSettings();
            case "open_search":
                return target.openSearch(rest);
            case "close_search":
                return target.closeSearch();
            case "get_setting":
                return requires(rest, "get_setting", target::getSetting);
            case "set_setting": {
                String[] setting = rest.split("\\s+", 2);
                if (setting.length != 2 || setting[0].isEmpty() || setting[1].isEmpty()) {
                    return "error: set_setting requires a key and value";
                }
                return target.setSetting(setting[0], setting[1]);
            }
            default:
                return "error: unknown command '" + verb + "'; use help";
        }
    }

    private static String requires(String value, String verb, Command handler) {
        return value.isEmpty() ? "error: " + verb + " requires an argument" : handler.run(value);
    }

    private interface Command {
        String run(String value);
    }
}
