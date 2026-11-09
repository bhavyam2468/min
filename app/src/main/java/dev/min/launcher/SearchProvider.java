package dev.min.launcher;

import java.util.List;

public interface SearchProvider {
    String id();
    String label();
    boolean isAvailable();
    List<SearchResult> search(String query);
}
