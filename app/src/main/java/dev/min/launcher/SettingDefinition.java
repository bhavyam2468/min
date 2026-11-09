package dev.min.launcher;

import java.util.Collections;
import java.util.List;

public final class SettingDefinition {
    public enum Type { TOGGLE, CHOICE, ACTION }

    public final Type type;
    public final String key;
    public final String title;
    public final String description;
    public final String tags;
    public final boolean defaultToggle;
    public final List<String> choices;

    private SettingDefinition(Type type, String key, String title, String description, String tags,
            boolean defaultToggle, List<String> choices) {
        this.type = type;
        this.key = key;
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.defaultToggle = defaultToggle;
        this.choices = choices;
    }

    public static SettingDefinition toggle(String key, String title, String description, String tags,
            boolean defaultValue) {
        return new SettingDefinition(Type.TOGGLE, key, title, description, tags, defaultValue,
                Collections.emptyList());
    }

    public static SettingDefinition choice(String key, String title, String description, String tags,
            List<String> choices) {
        return new SettingDefinition(Type.CHOICE, key, title, description, tags, false, choices);
    }

    public static SettingDefinition action(String key, String title, String description, String tags) {
        return new SettingDefinition(Type.ACTION, key, title, description, tags, false,
                Collections.emptyList());
    }

    public String searchText() {
        return title + " " + description + " " + tags;
    }
}
