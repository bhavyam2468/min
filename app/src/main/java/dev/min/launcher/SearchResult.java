package dev.min.launcher;

import android.net.Uri;

public final class SearchResult {
    public enum Kind { APP, CONTACT, FALLBACK }

    public final String categoryId;
    public final String title;
    public final Kind kind;
    public final AppEntry app;
    public final Uri contactUri;
    public final FallbackEngine fallbackEngine;
    public final FuzzyMatcher.Match match;

    private SearchResult(String categoryId, String title, Kind kind, AppEntry app, Uri contactUri,
            FallbackEngine fallbackEngine, FuzzyMatcher.Match match) {
        this.categoryId = categoryId;
        this.title = title;
        this.kind = kind;
        this.app = app;
        this.contactUri = contactUri;
        this.fallbackEngine = fallbackEngine;
        this.match = match;
    }

    public static SearchResult app(AppEntry entry, FuzzyMatcher.Match match) {
        return new SearchResult("apps", entry.label, Kind.APP, entry, null, null, match);
    }

    public static SearchResult contact(String name, Uri uri, FuzzyMatcher.Match match) {
        return new SearchResult("contacts", name, Kind.CONTACT, null, uri, null, match);
    }

    public static SearchResult fallback(String query, FallbackEngine engine) {
        return new SearchResult("search", "search " + query + " on " + engine.label,
                Kind.FALLBACK, null, null, engine, null);
    }
}
