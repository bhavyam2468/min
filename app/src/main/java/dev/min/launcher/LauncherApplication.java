package dev.min.launcher;

import android.app.Application;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/** Process owner for data, commands, and pending UI requests when no activity is visible. */
public final class LauncherApplication extends Application implements CommandProcessor.Target {
    private enum PendingScreen { HOME, SEARCH, SETTINGS }

    private SettingsStore settingsStore;
    private AppIndex appIndex;
    private AppSearchProvider appProvider;
    private ContactSearchProvider contactProvider;
    private FallbackSearchProvider fallbackProvider;
    private LauncherActivity activeActivity;
    private PendingScreen pendingScreen = PendingScreen.HOME;
    private String pendingQuery = "";

    @Override
    public void onCreate() {
        super.onCreate();
        settingsStore = new SettingsStore(this);
        appProvider = new AppSearchProvider();
        contactProvider = new ContactSearchProvider(this);
        fallbackProvider = new FallbackSearchProvider(this, settingsStore);
        fallbackProvider.setListener(() -> {
            LauncherActivity activity = activeActivity;
            if (activity != null) {
                activity.onFallbackEnginesUpdated();
            }
        });
        fallbackProvider.reload();
        appIndex = new AppIndex(this);
        appIndex.addListener(apps -> {
            appProvider.setApps(apps);
            fallbackProvider.reload();
            initializeDefaultFavorites(apps);
            LauncherActivity activity = activeActivity;
            if (activity != null) {
                activity.onAppsUpdated();
            }
        });
        contactProvider.setListener(() -> {
            LauncherActivity activity = activeActivity;
            if (activity != null) {
                activity.onContactsUpdated();
            }
        });
        appIndex.start();
        contactProvider.reload();
    }

    public SettingsStore settings() {
        return settingsStore;
    }

    public AppIndex appIndex() {
        return appIndex;
    }

    public AppSearchProvider appProvider() {
        return appProvider;
    }

    public ContactSearchProvider contactProvider() {
        return contactProvider;
    }

    public FallbackSearchProvider fallbackProvider() {
        return fallbackProvider;
    }

    public void setActiveActivity(LauncherActivity activity) {
        activeActivity = activity;
    }

    public void clearActiveActivity(LauncherActivity activity) {
        if (activeActivity == activity) {
            activeActivity = null;
        }
    }

    public void applyPendingRequest(LauncherActivity activity) {
        if (pendingScreen == PendingScreen.SETTINGS) {
            activity.showSettings();
        } else if (pendingScreen == PendingScreen.SEARCH) {
            activity.openSearch(pendingQuery);
        }
        pendingScreen = PendingScreen.HOME;
        pendingQuery = "";
    }

    public String executeCommand(String command) {
        return CommandProcessor.execute(command, this);
    }

    @Override
    public String status() {
        LauncherActivity activity = activeActivity;
        String query = activity == null ? pendingQuery : activity.currentQuery();
        boolean search = activity != null ? activity.isSearchOpen() : pendingScreen == PendingScreen.SEARCH;
        boolean settings = activity != null ? activity.isSettingsOpen() : pendingScreen == PendingScreen.SETTINGS;
        return "ok: search=" + search + "; settings=" + settings + "; query=" + quoted(query)
                + "; favorites=" + favoriteNames() + "; app_count=" + appIndex.snapshot().size()
                + "; status_bar=" + (settingsStore.hideStatusBar() ? "hidden" : "shown")
                + "; blur=" + (settingsStore.blurEnabled() ? "on" : "off")
                + "; anim_speed=" + settingsStore.animationSpeed();
    }

    @Override
    public String search(String query) {
        LauncherActivity activity = activeActivity;
        String answer;
        if (activity != null) {
            activity.openSearch(query);
            answer = activity.searchResultsForCommand();
        } else {
            pendingScreen = PendingScreen.SEARCH;
            pendingQuery = query;
            answer = resultsFor(query);
            requestActivity();
        }
        return "ok: search " + quoted(query) + "; " + answer;
    }

    @Override
    public String openApp(String selector) {
        AppEntry entry = resolveApp(selector);
        if (entry == null) {
            return "error: no app matches " + quoted(selector);
        }
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.launchApp(entry);
        } else {
            try {
                Intent intent = new Intent().setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (RuntimeException failure) {
                return "error: could not launch " + entry.label;
            }
        }
        return "ok: opening " + entry.label;
    }

    @Override
    public String listFavorites() {
        return "ok: favorites=" + favoriteNames();
    }

    @Override
    public String addFavorite(String selector) {
        AppEntry entry = resolveApp(selector);
        if (entry == null) {
            return "error: no app matches " + quoted(selector);
        }
        if (!settingsStore.addFavorite(entry.componentKey())) {
            return "ok: " + entry.label + " is already a favorite";
        }
        refreshFavorites();
        return "ok: added " + entry.label + " to favorites";
    }

    @Override
    public String removeFavorite(String selector) {
        AppEntry entry = resolveApp(selector);
        if (entry == null) {
            return "error: no app matches " + quoted(selector);
        }
        if (!settingsStore.removeFavorite(entry.componentKey())) {
            return "error: " + entry.label + " is not a favorite";
        }
        refreshFavorites();
        return "ok: removed " + entry.label + " from favorites";
    }

    @Override
    public String openSettings() {
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.showSettings();
        } else {
            pendingScreen = PendingScreen.SETTINGS;
            requestActivity();
        }
        return "ok: opened settings";
    }

    @Override
    public String closeSettings() {
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.closeSettings();
        }
        if (pendingScreen == PendingScreen.SETTINGS) {
            pendingScreen = PendingScreen.HOME;
        }
        return "ok: closed settings";
    }

    @Override
    public String openSearch(String query) {
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.openSearch(query);
        } else {
            pendingScreen = PendingScreen.SEARCH;
            pendingQuery = query;
            requestActivity();
        }
        return "ok: opened search" + (query.isEmpty() ? "" : " with " + quoted(query));
    }

    @Override
    public String closeSearch() {
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.closeSearch();
        }
        if (pendingScreen == PendingScreen.SEARCH) {
            pendingScreen = PendingScreen.HOME;
            pendingQuery = "";
        }
        return "ok: closed search";
    }

    @Override
    public String getSetting(String key) {
        String normalized = key.toLowerCase();
        if ("status_bar".equals(normalized)) {
            return "ok: status_bar=" + (settingsStore.hideStatusBar() ? "hidden" : "shown");
        }
        if ("blur".equals(normalized)) {
            return "ok: blur=" + (settingsStore.blurEnabled() ? "on" : "off");
        }
        if ("anim_speed".equals(normalized) || "animation_speed".equals(normalized)) {
            return "ok: anim_speed=" + settingsStore.animationSpeed();
        }
        return "error: unknown setting " + quoted(key);
    }

    @Override
    public String setSetting(String key, String value) {
        String normalized = key.toLowerCase();
        String settingValue = value.toLowerCase();
        if ("status_bar".equals(normalized)) {
            if ("hidden".equals(settingValue) || "hide".equals(settingValue) || "off".equals(settingValue)) {
                settingsStore.setHideStatusBar(true);
            } else if ("shown".equals(settingValue) || "show".equals(settingValue) || "on".equals(settingValue)) {
                settingsStore.setHideStatusBar(false);
            } else {
                return "error: status_bar value must be hidden or shown";
            }
            refreshSettings();
            return "ok: status_bar=" + (settingsStore.hideStatusBar() ? "hidden" : "shown");
        }
        if ("blur".equals(normalized)) {
            Boolean enabled = parseOnOff(settingValue);
            if (enabled == null) return "error: blur value must be on or off";
            settingsStore.setBlurEnabled(enabled);
            refreshSettings();
            return "ok: blur=" + (enabled ? "on" : "off");
        }
        if ("anim_speed".equals(normalized) || "animation_speed".equals(normalized)) {
            if (!"snappy".equals(settingValue) && !"standard".equals(settingValue) && !"relaxed".equals(settingValue)) {
                return "error: anim_speed must be snappy, standard, or relaxed";
            }
            settingsStore.setAnimationSpeed(settingValue);
            refreshSettings();
            return "ok: anim_speed=" + settingValue;
        }
        return "error: unknown setting " + quoted(key);
    }

    public void requestContactsReload() {
        contactProvider.reload();
    }

    public void moveProviderUp(String id) {
        settingsStore.moveProviderUp(id, fallbackProvider.defaultIds());
        refreshSettings();
    }

    private void initializeDefaultFavorites(List<AppEntry> apps) {
        if (settingsStore.defaultFavoritesSet() || apps.isEmpty()) {
            return;
        }
        String[] desired = {"phone", "messages", "chrome", "browser", "camera", "gmail", "maps"};
        ArrayList<String> favorites = new ArrayList<>();
        for (String wanted : desired) {
            for (AppEntry app : apps) {
                if (matchesDefaultName(app.normalizedLabel, wanted) && !favorites.contains(app.componentKey())) {
                    favorites.add(app.componentKey());
                    break;
                }
            }
        }
        settingsStore.setFavorites(favorites);
        settingsStore.setDefaultFavoritesSet();
    }

    private static boolean matchesDefaultName(String label, String wanted) {
        return label.equals(wanted) || label.startsWith(wanted + " ") || label.endsWith(" " + wanted)
                || label.contains(wanted);
    }

    private AppEntry resolveApp(String selector) {
        String normalized = FuzzyMatcher.normalize(selector);
        for (AppEntry app : appIndex.snapshot()) {
            if (app.packageName().equalsIgnoreCase(selector) || app.componentKey().equalsIgnoreCase(selector)) {
                return app;
            }
        }
        for (AppEntry app : appIndex.snapshot()) {
            if (app.normalizedLabel.equals(normalized)) {
                return app;
            }
        }
        AppEntry best = null;
        for (AppEntry app : appIndex.snapshot()) {
            if (FuzzyMatcher.match(normalized, app.label) != null) {
                best = app;
                break;
            }
        }
        return best;
    }

    private String resultsFor(String query) {
        StringBuilder answer = new StringBuilder();
        appendResults(answer, "apps", appProvider.search(query));
        appendResults(answer, "contacts", contactProvider.search(query));
        appendResults(answer, "search", fallbackProvider.search(query));
        return answer.toString();
    }

    private static void appendResults(StringBuilder output, String category, List<SearchResult> results) {
        if (output.length() > 0) output.append(" | ");
        output.append(category).append(": ");
        if (results.isEmpty()) {
            output.append("(none)");
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) output.append(", ");
            output.append(results.get(i).title);
        }
    }

    private String favoriteNames() {
        ArrayList<String> names = new ArrayList<>();
        for (String component : settingsStore.favorites()) {
            for (AppEntry app : appIndex.snapshot()) {
                if (component.equals(app.componentKey())) {
                    names.add(app.label);
                    break;
                }
            }
        }
        return names.toString();
    }

    private void refreshFavorites() {
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.onAppsUpdated();
        }
    }

    private void refreshSettings() {
        LauncherActivity activity = activeActivity;
        if (activity != null) {
            activity.onSettingsChanged();
        }
    }

    private void requestActivity() {
        try {
            Intent intent = new Intent(this, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // Android may reject a background activity launch; the pending request is retained for HOME.
        }
    }

    private static Boolean parseOnOff(String value) {
        if ("on".equals(value) || "true".equals(value)) return true;
        if ("off".equals(value) || "false".equals(value)) return false;
        return null;
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}
