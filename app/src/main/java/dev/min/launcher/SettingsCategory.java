package dev.min.launcher;

import java.util.List;

public final class SettingsCategory {
    public final String id;
    public final String title;
    public final String description;
    public final List<SettingDefinition> items;

    public SettingsCategory(String id, String title, String description, List<SettingDefinition> items) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.items = items;
    }
}
