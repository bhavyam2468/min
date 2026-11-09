package dev.min.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** The framework-only launcher activity and coordinator for its focused views. */
public final class LauncherActivity extends Activity {
    private static final int CONTACTS_REQUEST = 41;

    private LauncherApplication application;
    private SettingsStore store;
    private FrameLayout root;
    private HomeRootView home;
    private SearchLayerView search;
    private SettingsView settings;
    private ContextPopup popup;
    private SpringValue searchSpring;
    private SpringValue settingsSpring;
    private SpringValue introSpring;
    private SpringValue departureSpring;
    private float searchFraction;
    private float searchDragStart;
    private boolean handlingSearchDrag;
    private boolean searchOpenTarget;
    private boolean settingsOpenTarget;
    private Runnable departureAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = (LauncherApplication) getApplication();
        store = application.settings();
        SpringValue.setDurationMultiplier(store.animationMultiplier());
        configureWindow();
        buildViews();
        application.setActiveActivity(this);
        updateSystemBars();
        root.post(() -> {
            application.applyPendingRequest(this);
            playIntro();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        application.setActiveActivity(this);
        updateSystemBars();
        if (root != null) {
            root.post(this::playIntro);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            resetToCleanHome();
            application.clearActiveActivity(this);
        }
    }

    @Override
    protected void onDestroy() {
        application.clearActiveActivity(this);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            resetToCleanHome();
        }
    }

    @Override
    public void onBackPressed() {
        if (popup.isShowing()) {
            popup.dismiss();
        } else if (isSettingsOpen()) {
            closeSettings();
        } else if (isSearchOpen()) {
            closeSearch();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updateSystemBars();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_REQUEST) {
            application.requestContactsReload();
            settings.refresh();
            search.refreshResults();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(UiTokens.COLOR_BLACK);
        window.setNavigationBarColor(UiTokens.COLOR_BLACK);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        window.setDecorFitsSystemWindows(false);
    }

    private void buildViews() {
        root = new FrameLayout(this);
        root.setBackgroundColor(UiTokens.COLOR_BLACK);
        setContentView(root);

        home = new HomeRootView(this, new HomeRootView.Listener() {
            @Override public void onSwipeStart() {
                beginSearchDrag();
            }

            @Override public void onSwipeMove(float deltaY) {
                moveSearchDrag(deltaY);
            }

            @Override public void onSwipeEnd(float velocityY) {
                endSearchDrag(velocityY);
            }

            @Override public void onEmptyLongPress() {
                showSettings();
            }

            @Override public void onFavoriteTap(AppEntry app) {
                launchApp(app);
            }

            @Override public void onFavoriteLongPress(AppEntry app) {
                popup.show(app, store.isFavorite(app.componentKey()));
            }
        });
        root.addView(home, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        search = new SearchLayerView(this, application.appProvider(), application.contactProvider(),
                application.fallbackProvider(), new SearchLayerView.Listener() {
            @Override public void onRunResult(SearchResult result) {
                runResult(result);
            }

            @Override public void onAppLongPress(AppEntry app) {
                popup.show(app, store.isFavorite(app.componentKey()));
            }

            @Override public void onQueryChanged(String query) {
                // Search state is owned directly by SearchLayerView for allocation-free status reads.
            }
        });
        root.addView(search, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        settings = new SettingsView(this, store, application.fallbackProvider(), new SettingsView.Listener() {
            @Override public void onCloseRequested() {
                closeSettings();
            }

            @Override public void onSettingChanged(SettingDefinition definition) {
                onSettingsChanged();
            }

            @Override public void onSettingAction(SettingDefinition definition) {
                runSettingAction(definition);
            }
        });
        root.addView(settings, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        popup = new ContextPopup(this, new ContextPopup.Listener() {
            @Override public void onAppInfo(AppEntry app) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", app.packageName(), null));
                launchExternal(intent);
            }

            @Override public void onFavoriteAction(AppEntry app, boolean currentlyFavorite) {
                if (currentlyFavorite) {
                    application.removeFavorite(app.componentKey());
                } else {
                    application.addFavorite(app.componentKey());
                }
            }
        });
        root.addView(popup, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        searchSpring = new SpringValue(root, new SpringValue.Listener() {
            @Override public void onUpdate(float value) {
                applySearchFraction(value);
            }

            @Override public void onRest(float value) {
                if (value <= 0.001f && !searchOpenTarget) {
                    search.setVisibility(android.view.View.GONE);
                    search.clearQuery();
                    search.clearFocusFromInput();
                    hideKeyboard();
                } else if (value >= 0.999f && searchOpenTarget) {
                    search.focusInput();
                    showKeyboard();
                }
            }
        });
        settingsSpring = new SpringValue(root, new SpringValue.Listener() {
            @Override public void onUpdate(float value) {
                float safe = clamp(value);
                settings.setAlpha(safe);
                settings.setTranslationY(UiTokens.dp(LauncherActivity.this, UiTokens.SETTINGS_SLIDE_DP) * (1f - safe));
            }

            @Override public void onRest(float value) {
                if (value <= 0.001f && !settingsOpenTarget) {
                    settings.setVisibility(android.view.View.GONE);
                }
            }
        });
        introSpring = new SpringValue(root, new SpringValue.Listener() {
            @Override public void onUpdate(float value) {
                float safe = clamp(value);
                root.setAlpha(safe);
                float scale = UiTokens.INTRO_START_SCALE - (UiTokens.INTRO_START_SCALE - 1f) * safe;
                root.setScaleX(scale);
                root.setScaleY(scale);
                setRootBlur(safe < 0.92f);
            }

            @Override public void onRest(float value) {
                setRootBlur(false);
            }
        });
        departureSpring = new SpringValue(root, new SpringValue.Listener() {
            @Override public void onUpdate(float value) {
                float safe = clamp(value);
                root.setAlpha(1f - safe);
                float scale = 1f + (UiTokens.INTRO_START_SCALE - 1f) * safe;
                root.setScaleX(scale);
                root.setScaleY(scale);
                setRootBlur(safe > 0.04f);
            }

            @Override public void onRest(float value) {
                if (value >= 0.999f) {
                    Runnable action = departureAction;
                    departureAction = null;
                    if (action != null) {
                        action.run();
                    }
                }
            }
        });

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            applyInsets(insets);
            return insets;
        });
        root.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(
                WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @Override
            public WindowInsets onProgress(WindowInsets insets, List<WindowInsetsAnimation> runningAnimations) {
                applyInsets(insets);
                return insets;
            }
        });
        refreshHomeFavorites();
    }

    private void applyInsets(WindowInsets insets) {
        android.graphics.Insets system = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
        home.setInsets(system.top, system.bottom, system.left, system.right);
        search.setInsets(system.top, system.bottom, system.left, system.right, ime.bottom);
        settings.setInsets(system.top, Math.max(system.bottom, ime.bottom), system.left, system.right);
    }

    public void onAppsUpdated() {
        refreshHomeFavorites();
        search.refreshResults();
        if (isSettingsOpen()) {
            settings.refresh();
        }
    }

    public void onContactsUpdated() {
        search.refreshResults();
    }

    public void onFallbackEnginesUpdated() {
        search.refreshResults();
        if (isSettingsOpen()) {
            settings.refresh();
        }
    }

    public void onSettingsChanged() {
        SpringValue.setDurationMultiplier(store.animationMultiplier());
        updateSystemBars();
        settings.refresh();
        search.refreshResults();
    }

    public void openSearch(String query) {
        if (isSettingsOpen()) {
            closeSettings();
        }
        if (popup.isShowing()) {
            popup.dismiss();
        }
        searchSpring.cancel();
        search.setVisibility(android.view.View.VISIBLE);
        search.setQuery(query == null ? "" : query);
        searchOpenTarget = true;
        configureSpring(searchSpring, UiTokens.GENTLE_STIFFNESS, UiTokens.GENTLE_DAMPING);
        searchSpring.start(1f, 0f);
    }

    public void closeSearch() {
        if (search.getVisibility() != android.view.View.VISIBLE) {
            return;
        }
        searchOpenTarget = false;
        configureSpring(searchSpring, UiTokens.GENTLE_STIFFNESS, UiTokens.GENTLE_DAMPING);
        searchSpring.start(0f, 0f);
        hideKeyboard();
    }

    public void showSettings() {
        if (popup.isShowing()) {
            popup.dismiss();
        }
        if (isSearchOpen()) {
            resetSearchImmediately();
        }
        settingsSpring.cancel();
        settings.setVisibility(android.view.View.VISIBLE);
        settingsOpenTarget = true;
        configureSpring(settingsSpring, UiTokens.GENTLE_STIFFNESS, UiTokens.GENTLE_DAMPING);
        settingsSpring.start(1f, 0f);
    }

    public void closeSettings() {
        if (settings.getVisibility() != android.view.View.VISIBLE) {
            return;
        }
        settingsOpenTarget = false;
        configureSpring(settingsSpring, UiTokens.GENTLE_STIFFNESS, UiTokens.GENTLE_DAMPING);
        settingsSpring.start(0f, 0f);
        hideKeyboard();
    }

    public boolean isSearchOpen() {
        return searchOpenTarget || (search.getVisibility() == android.view.View.VISIBLE && searchFraction > 0.5f);
    }

    public boolean isSettingsOpen() {
        return settingsOpenTarget;
    }

    public String currentQuery() {
        return search.query();
    }

    public String searchResultsForCommand() {
        return search.resultsForCommand();
    }

    public void launchApp(AppEntry app) {
        hideKeyboard();
        animateAway(() -> {
            try {
                startActivity(new Intent().setComponent(app.component));
            } catch (RuntimeException failed) {
                recoverFromFailedLaunch();
            }
        });
    }

    private void runResult(SearchResult result) {
        if (result.kind == SearchResult.Kind.APP) {
            launchApp(result.app);
        } else if (result.kind == SearchResult.Kind.CONTACT) {
            launchExternal(new Intent(Intent.ACTION_VIEW, result.contactUri));
        } else {
            launchExternal(result.fallbackEngine.intentFor(search.query()));
        }
    }

    private void runSettingAction(SettingDefinition definition) {
        if ("default_home_settings".equals(definition.key)) {
            launchExternal(new Intent(Settings.ACTION_HOME_SETTINGS));
        } else if ("contacts_permission".equals(definition.key)) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_REQUEST);
            } else {
                application.requestContactsReload();
            }
        } else if ("app_info".equals(definition.key)) {
            launchExternal(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } else if (definition.key.startsWith("move_provider_up:")) {
            application.moveProviderUp(definition.key.substring("move_provider_up:".length()));
        }
    }

    private void launchExternal(Intent intent) {
        hideKeyboard();
        animateAway(() -> {
            try {
                startActivity(intent);
            } catch (RuntimeException failed) {
                recoverFromFailedLaunch();
            }
        });
    }

    private void animateAway(Runnable action) {
        departureAction = action;
        introSpring.cancel();
        configureSpring(departureSpring, UiTokens.SPRING_STIFFNESS, UiTokens.SPRING_DAMPING);
        departureSpring.snapTo(0f);
        departureSpring.start(1f, 0f);
    }

    private void recoverFromFailedLaunch() {
        root.setAlpha(1f);
        root.setScaleX(1f);
        root.setScaleY(1f);
        setRootBlur(false);
        playIntro();
    }

    private void playIntro() {
        if (isFinishing() || root == null) {
            return;
        }
        departureSpring.cancel();
        configureSpring(introSpring, UiTokens.SPRING_STIFFNESS, UiTokens.SPRING_DAMPING);
        introSpring.snapTo(0f);
        introSpring.start(1f, 0f);
    }

    private void beginSearchDrag() {
        if (isSettingsOpen() || popup.isShowing()) {
            handlingSearchDrag = false;
            return;
        }
        handlingSearchDrag = true;
        searchSpring.cancel();
        search.setVisibility(android.view.View.VISIBLE);
        searchDragStart = searchFraction;
    }

    private void moveSearchDrag(float deltaY) {
        if (!handlingSearchDrag) {
            return;
        }
        float trigger = Math.max(1f, root.getHeight() * UiTokens.SEARCH_DRAG_FRACTION);
        applySearchFraction(clamp(searchDragStart + (-deltaY / trigger)));
    }

    private void endSearchDrag(float velocityY) {
        if (!handlingSearchDrag) {
            return;
        }
        handlingSearchDrag = false;
        float trigger = Math.max(1f, root.getHeight() * UiTokens.SEARCH_DRAG_FRACTION);
        boolean open = searchFraction >= UiTokens.SEARCH_OPEN_THRESHOLD || velocityY < -UiTokens.SEARCH_FLING_VELOCITY;
        searchOpenTarget = open;
        configureSpring(searchSpring, UiTokens.GENTLE_STIFFNESS, UiTokens.GENTLE_DAMPING);
        searchSpring.start(open ? 1f : 0f, -velocityY / trigger);
    }

    private void applySearchFraction(float value) {
        searchFraction = clamp(value);
        home.setAlpha(1f - searchFraction);
        home.setTranslationY(UiTokens.dp(this, UiTokens.SEARCH_HOME_DRIFT_DP) * searchFraction);
        search.setAlpha(searchFraction);
        search.setTranslationY(UiTokens.dp(this, UiTokens.SEARCH_LAYER_RISE_DP) * (1f - searchFraction));
    }

    private void resetSearchImmediately() {
        searchSpring.cancel();
        searchOpenTarget = false;
        searchFraction = 0f;
        applySearchFraction(0f);
        search.setVisibility(android.view.View.GONE);
        search.clearQuery();
        search.clearFocusFromInput();
        hideKeyboard();
    }

    private void resetToCleanHome() {
        if (root == null) {
            return;
        }
        popup.dismissImmediately();
        resetSearchImmediately();
        settingsSpring.cancel();
        settingsOpenTarget = false;
        settings.setAlpha(0f);
        settings.setTranslationY(UiTokens.dp(this, UiTokens.SETTINGS_SLIDE_DP));
        settings.setVisibility(android.view.View.GONE);
        home.setAlpha(1f);
        home.setTranslationY(0f);
        setRootBlur(false);
    }

    private void refreshHomeFavorites() {
        List<String> keys = store.favorites();
        ArrayList<AppEntry> favorites = new ArrayList<>();
        for (String key : keys) {
            for (AppEntry app : application.appIndex().snapshot()) {
                if (key.equals(app.componentKey())) {
                    favorites.add(app);
                    break;
                }
            }
        }
        home.setFavorites(favorites);
    }

    private void updateSystemBars() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) {
            return;
        }
        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (store.hideStatusBar()) {
            controller.hide(WindowInsets.Type.statusBars());
        } else {
            controller.show(WindowInsets.Type.statusBars());
        }
    }

    private void showKeyboard() {
        search.post(() -> {
            android.view.inputmethod.InputMethodManager manager =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.showSoftInput(search.findFocus(), android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager manager =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private void setRootBlur(boolean enabled) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && store.blurEnabled()) {
            root.setRenderEffect(enabled ? RenderEffect.createBlurEffect(UiTokens.dp(this, UiTokens.BLUR_RADIUS_DP),
                    UiTokens.dp(this, UiTokens.BLUR_RADIUS_DP), Shader.TileMode.CLAMP) : null);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            root.setRenderEffect(null);
        }
    }

    private static void configureSpring(SpringValue spring, float stiffness, float damping) {
        spring.setPhysics(stiffness, damping);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
