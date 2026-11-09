package dev.min.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Persistent user choices. This class is the only owner of launcher preferences. */
public final class SettingsStore {
    public static final String KEY_HIDE_STATUS = "hide_status_bar";
    public static final String KEY_BLUR = "blur";
    public static final String KEY_ANIMATION_SPEED = "animation_speed";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_DEFAULT_FAVORITES_SET = "default_favorites_set";
    private static final String KEY_PROVIDER_ORDER = "provider_order";
    private static final String PROVIDER_ENABLED_PREFIX = "provider_enabled_";
    private static final String SEPARATOR = "|";

    private final SharedPreferences preferences;

    public SettingsStore(Context context) {
        preferences = context.getSharedPreferences("min_launcher", Context.MODE_PRIVATE);
    }

    public boolean getToggle(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public void setToggle(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public String getText(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    public void setText(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public boolean hideStatusBar() {
        return getToggle(KEY_HIDE_STATUS, true);
    }

    public void setHideStatusBar(boolean value) {
        setToggle(KEY_HIDE_STATUS, value);
    }

    public boolean blurEnabled() {
        return getToggle(KEY_BLUR, true);
    }

    public void setBlurEnabled(boolean value) {
        setToggle(KEY_BLUR, value);
    }

    public String animationSpeed() {
        return getText(KEY_ANIMATION_SPEED, "standard");
    }

    public void setAnimationSpeed(String value) {
        setText(KEY_ANIMATION_SPEED, value);
    }

    public float animationMultiplier() {
        String value = animationSpeed();
        if ("snappy".equals(value)) {
            return 0.75f;
        }
        if ("relaxed".equals(value)) {
            return 1.5f;
        }
        return 1f;
    }

    public List<String> favorites() {
        String raw = preferences.getString(KEY_FAVORITES, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split("\\|")));
    }

    public void setFavorites(List<String> values) {
        preferences.edit().putString(KEY_FAVORITES, join(values)).apply();
    }

    public boolean addFavorite(String component) {
        List<String> values = favorites();
        if (values.contains(component)) {
            return false;
        }
        values.add(component);
        setFavorites(values);
        return true;
    }

    public boolean removeFavorite(String component) {
        List<String> values = favorites();
        boolean removed = values.remove(component);
        if (removed) {
            setFavorites(values);
        }
        return removed;
    }

    public boolean isFavorite(String component) {
        return favorites().contains(component);
    }

    public boolean defaultFavoritesSet() {
        return preferences.getBoolean(KEY_DEFAULT_FAVORITES_SET, false);
    }

    public void setDefaultFavoritesSet() {
        preferences.edit().putBoolean(KEY_DEFAULT_FAVORITES_SET, true).apply();
    }

    public boolean providerEnabled(String id) {
        return getToggle(PROVIDER_ENABLED_PREFIX + id, true);
    }

    public void setProviderEnabled(String id, boolean enabled) {
        setToggle(PROVIDER_ENABLED_PREFIX + id, enabled);
    }

    public List<String> providerOrder(List<String> defaults) {
        String raw = preferences.getString(KEY_PROVIDER_ORDER, "");
        ArrayList<String> order = raw.isEmpty() ? new ArrayList<>() :
                new ArrayList<>(Arrays.asList(raw.split("\\|")));
        for (String id : defaults) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        order.retainAll(new HashSet<>(defaults));
        return order;
    }

    public void moveProviderUp(String id, List<String> defaults) {
        List<String> order = providerOrder(defaults);
        int index = order.indexOf(id);
        if (index > 0) {
            Collections.swap(order, index, index - 1);
            preferences.edit().putString(KEY_PROVIDER_ORDER, join(order)).apply();
        }
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.contains(SEPARATOR)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
