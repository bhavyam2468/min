package dev.min.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fallback engines are cached off the UI thread, then ordered/enabled by SettingsStore. */
public final class FallbackSearchProvider implements SearchProvider {
    public interface Listener {
        void onFallbackEnginesChanged();
    }

    private static final List<FallbackEngine> ALL_ENGINES = Arrays.asList(
            new FallbackEngine("web", "web", null, FallbackEngine.Kind.WEB, null),
            new FallbackEngine("youtube", "YouTube", "com.google.android.youtube", FallbackEngine.Kind.ACTION_SEARCH, null),
            new FallbackEngine("spotify", "Spotify", "com.spotify.music", FallbackEngine.Kind.VIEW_URI, "spotify:search:"),
            new FallbackEngine("maps", "Google Maps", "com.google.android.apps.maps", FallbackEngine.Kind.VIEW_URI, "geo:0,0?q="),
            new FallbackEngine("play_store", "Play Store", "com.android.vending", FallbackEngine.Kind.VIEW_URI, "market://search?q="),
            new FallbackEngine("amazon", "Amazon", "com.amazon.mShop.android.shopping", FallbackEngine.Kind.VIEW_URI, "amazon://www.amazon.com/s?k="),
            new FallbackEngine("reddit", "Reddit", "com.reddit.frontpage", FallbackEngine.Kind.VIEW_URI, "https://www.reddit.com/search/?q="),
            new FallbackEngine("wikipedia", "Wikipedia", "org.wikipedia", FallbackEngine.Kind.ACTION_SEARCH, null),
            new FallbackEngine("x", "X", "com.twitter.android", FallbackEngine.Kind.ACTION_SEARCH, null)
    );

    private final Context context;
    private final SettingsStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile List<FallbackEngine> installed = Collections.singletonList(ALL_ENGINES.get(0));
    private Listener listener;

    public FallbackSearchProvider(Context context, SettingsStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reload() {
        executor.execute(() -> {
            ArrayList<FallbackEngine> detected = new ArrayList<>();
            for (FallbackEngine engine : ALL_ENGINES) {
                if (engine.kind == FallbackEngine.Kind.WEB || packageInstalled(engine.packageName)) {
                    detected.add(engine);
                }
            }
            List<FallbackEngine> immutable = Collections.unmodifiableList(detected);
            mainHandler.post(() -> {
                installed = immutable;
                Listener current = listener;
                if (current != null) {
                    current.onFallbackEnginesChanged();
                }
            });
        });
    }

    @Override
    public String id() {
        return "search";
    }

    @Override
    public String label() {
        return "SEARCH";
    }

    @Override
    public boolean isAvailable() {
        return !installedAndEnabled().isEmpty();
    }

    @Override
    public List<SearchResult> search(String query) {
        if (FuzzyMatcher.normalize(query).isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<SearchResult> results = new ArrayList<>();
        for (FallbackEngine engine : installedAndEnabled()) {
            results.add(SearchResult.fallback(query, engine));
        }
        return results;
    }

    public List<FallbackEngine> installedEngines() {
        return ordered(installed, false);
    }

    public List<String> defaultIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (FallbackEngine engine : ALL_ENGINES) {
            ids.add(engine.id);
        }
        return ids;
    }

    private List<FallbackEngine> installedAndEnabled() {
        return ordered(installed, true);
    }

    private List<FallbackEngine> ordered(List<FallbackEngine> engines, boolean onlyEnabled) {
        ArrayList<String> allowedIds = new ArrayList<>();
        for (FallbackEngine engine : ALL_ENGINES) {
            allowedIds.add(engine.id);
        }
        List<String> order = store.providerOrder(allowedIds);
        ArrayList<FallbackEngine> ordered = new ArrayList<>();
        for (String id : order) {
            for (FallbackEngine engine : engines) {
                if (engine.id.equals(id) && (!onlyEnabled || store.providerEnabled(id))) {
                    ordered.add(engine);
                    break;
                }
            }
        }
        return ordered;
    }

    private boolean packageInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
