package dev.min.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AppSearchProvider implements SearchProvider {
    private volatile List<AppEntry> apps = Collections.emptyList();

    public void setApps(List<AppEntry> apps) {
        this.apps = apps;
    }

    @Override
    public String id() {
        return "apps";
    }

    @Override
    public String label() {
        return "APPS";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<SearchResult> search(String query) {
        String normalized = FuzzyMatcher.normalize(query);
        ArrayList<SearchResult> results = new ArrayList<>();
        for (AppEntry app : apps) {
            FuzzyMatcher.Match match = normalized.isEmpty() ? FuzzyMatcher.match("", app.label)
                    : FuzzyMatcher.match(normalized, app.label);
            if (match != null) {
                results.add(SearchResult.app(app, match));
            }
        }
        if (!normalized.isEmpty()) {
            Collections.sort(results, RESULT_COMPARATOR);
        }
        return results;
    }

    private static final Comparator<SearchResult> RESULT_COMPARATOR = (left, right) -> {
        FuzzyMatcher.Match a = left.match;
        FuzzyMatcher.Match b = right.match;
        if (a.exact != b.exact) return a.exact ? -1 : 1;
        if (a.startsAtName != b.startsAtName) return a.startsAtName ? -1 : 1;
        if (a.startsAtWord != b.startsAtWord) return a.startsAtWord ? -1 : 1;
        if (a.position != b.position) return Integer.compare(a.position, b.position);
        if (left.title.length() != right.title.length()) return Integer.compare(left.title.length(), right.title.length());
        return left.title.compareToIgnoreCase(right.title);
    };
}
