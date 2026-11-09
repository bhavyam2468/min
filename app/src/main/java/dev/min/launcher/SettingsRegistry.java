package dev.min.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Declarative settings catalog. New settings are added here, not by editing the renderer. */
public final class SettingsRegistry {
    private SettingsRegistry() { }

    public static List<SettingsCategory> create(FallbackSearchProvider fallbackProvider) {
        ArrayList<SettingsCategory> categories = new ArrayList<>();
        categories.add(new SettingsCategory("display", "display", "screen and home behavior", Arrays.asList(
                SettingDefinition.toggle(SettingsStore.KEY_HIDE_STATUS, "hide status bar",
                        "Keep the home screen immersive.", "display immersive status bar fullscreen", true),
                SettingDefinition.action("default_home_settings", "default home app",
                        "Choose the Android app used for Home.", "home launcher system picker")
        )));

        ArrayList<SettingDefinition> providerItems = new ArrayList<>();
        for (FallbackEngine engine : fallbackProvider.installedEngines()) {
            providerItems.add(SettingDefinition.toggle("provider_enabled_" + engine.id, engine.label,
                    "Show search queries in " + engine.label + ".", "search provider fallback " + engine.id, true));
            providerItems.add(SettingDefinition.action("move_provider_up:" + engine.id,
                    "move " + engine.label + " up", "Raise its search priority.",
                    "search provider priority reorder " + engine.id));
        }
        categories.add(new SettingsCategory("search_providers", "search providers",
                "installed places that can receive a query", providerItems));

        categories.add(new SettingsCategory("animations", "animations", "motion and visual transitions", Arrays.asList(
                SettingDefinition.toggle(SettingsStore.KEY_BLUR, "blur", "Use blur where Android supports it.",
                        "animation render effect", true),
                SettingDefinition.choice(SettingsStore.KEY_ANIMATION_SPEED, "animation speed",
                        "Scale every launcher animation.", "motion duration snappy standard relaxed",
                        Arrays.asList("snappy", "standard", "relaxed"))
        )));

        categories.add(new SettingsCategory("permissions", "permissions", "optional system access", Arrays.asList(
                SettingDefinition.action("contacts_permission", "contacts access",
                        "Allow contact results in search.", "contacts permission search people"),
                SettingDefinition.action("app_info", "app info", "Open this launcher's Android settings.",
                        "system app details permissions")
        )));
        return categories;
    }
}
