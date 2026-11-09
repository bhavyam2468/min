package dev.min.launcher;

import android.content.ComponentName;

public final class AppEntry {
    public final String label;
    public final String normalizedLabel;
    public final ComponentName component;

    public AppEntry(String label, ComponentName component) {
        this.label = label;
        this.normalizedLabel = FuzzyMatcher.normalize(label);
        this.component = component;
    }

    public String componentKey() {
        return component.flattenToString();
    }

    public String packageName() {
        return component.getPackageName();
    }
}
