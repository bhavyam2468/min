package dev.min.launcher;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Declarative installed search target with the cleanest known query intent for that target. */
public final class FallbackEngine {
    public enum Kind { WEB, ACTION_SEARCH, VIEW_URI, MEDIA_SEARCH }

    public final String id;
    public final String label;
    public final String packageName;
    public final Kind kind;
    public final String uriPrefix;

    public FallbackEngine(String id, String label, String packageName, Kind kind, String uriPrefix) {
        this.id = id;
        this.label = label;
        this.packageName = packageName;
        this.kind = kind;
        this.uriPrefix = uriPrefix;
    }

    public Intent intentFor(String query) {
        switch (kind) {
            case ACTION_SEARCH: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.setPackage(packageName);
                intent.putExtra(SearchManager.QUERY, query);
                return intent;
            }
            case MEDIA_SEARCH: {
                Intent intent = new Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH");
                intent.setPackage(packageName);
                intent.putExtra(SearchManager.QUERY, query);
                return intent;
            }
            case VIEW_URI:
                return new Intent(Intent.ACTION_VIEW, Uri.parse(uriPrefix + encode(query)))
                        .setPackage(packageName);
            case WEB:
            default:
                return new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + encode(query)));
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            return value;
        }
    }
}
