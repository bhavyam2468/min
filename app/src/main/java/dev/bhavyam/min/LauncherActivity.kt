package dev.bhavyam.min

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import dev.bhavyam.min.apps.AppEntry
import dev.bhavyam.min.search.SearchResult
import dev.bhavyam.min.settings.SettingItem
import dev.bhavyam.min.settings.SettingsHost
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.theme.Theme
import dev.bhavyam.min.ui.LaunchBlur
import dev.bhavyam.min.ui.home.HomeView
import dev.bhavyam.min.ui.popup.ContextPopup
import dev.bhavyam.min.ui.search.SearchLayer
import dev.bhavyam.min.ui.settings.SettingsView
import dev.bhavyam.min.ui.studio.StudioView

/**
 * The launcher window. It only assembles the layers (home, search, settings, the home editor,
 * popup — in that z-order), wires them to the app's data, and decides what back, home and leaving do.
 */
class LauncherActivity : Activity(), LauncherUi, SettingsHost, HomeView.Host, SearchLayer.Host, ContextPopup.Host {

    private lateinit var app: MinApp
    private lateinit var root: FrameLayout
    private lateinit var home: HomeView
    private lateinit var search: SearchLayer
    private lateinit var settingsView: SettingsView
    private lateinit var studio: StudioView
    private lateinit var popup: ContextPopup
    private lateinit var blur: LaunchBlur

    private val onTheme: (Theme) -> Unit = ::applyTheme
    private val onApps: () -> Unit = {
        showFavorites()
        search.refresh()
    }
    private val onFavorites: () -> Unit = ::showFavorites
    private val onContacts: () -> Unit = { search.refresh() }
    private val onSetting: (SettingItem) -> Unit = { item ->
        settingsView.settingChanged(item)
        if (item === SettingsRegistry.STATUS_BAR) applyStatusBar()
    }
    private val backCallback = OnBackInvokedCallback { back() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = MinApp.of(this)
        // Draw behind the system bars; every layer pads itself from the insets. (API 35 does this by
        // default for apps targeting it; 31–34 need it asked for.)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        }

        home = HomeView(this, app.settings, this)
        search = SearchLayer(this, app.settings, app.engine, this)
        settingsView = SettingsView(this, app.settings, app.targets, this, ::closeSettings)
        studio = StudioView(this, app.theme, app.settings, home, ::closeStudio)
        popup = ContextPopup(this, app.settings, this)
        root = FrameLayout(this).apply {
            for (layer in listOf(home, search, settingsView, studio, popup)) addView(layer, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)
        blur = LaunchBlur(this, root, app.settings)

        app.theme.addListener(onTheme)
        app.apps.addListener(onApps)
        app.favorites.addListener(onFavorites)
        app.contacts.addListener(onContacts)
        app.settings.addListener(onSetting)
        if (app.apps.isLoaded) onApps()
        applyStatusBar()
        app.ui = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        }
    }

    override fun onDestroy() {
        app.theme.removeListener(onTheme)
        app.apps.removeListener(onApps)
        app.favorites.removeListener(onFavorites)
        app.contacts.removeListener(onContacts)
        app.settings.removeListener(onSetting)
        blur.release()
        if (app.ui === this) app.ui = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        blur.onStart()
    }

    override fun onResume() {
        super.onResume()
        blur.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        blur.onWindowFocusChanged(hasFocus)
    }

    /** Leaving (an app opened, the screen went off): come back to a clean home, without animation. */
    override fun onStop() {
        super.onStop()
        blur.onStop()
        popup.reset()
        search.reset()
        studio.reset()
        settingsView.reset()
    }

    /** Home pressed while already home: everything animates shut. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blur.onNewIntent()
        if (hasWindowFocus() || popup.isShowing) {
            popup.dismiss()
            if (studio.isOpen) closeStudio()
            if (settingsView.isOpen) settingsView.close()
            if (search.isOpen) search.close()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        app.theme.refresh() // sizes are in sp/dp: re-derive for the new density or font scale
    }

    @Deprecated("Back before API 33; newer versions use the OnBackInvokedCallback registered in onCreate")
    override fun onBackPressed() {
        back()
    }

    /** Back closes the topmost thing; on bare home it does nothing (a launcher has nowhere to go back to). */
    private fun back() {
        when {
            popup.isShowing -> popup.dismiss()
            studio.back() -> Unit
            settingsView.isOpen -> closeSettings()
            search.isOpen -> search.close()
        }
    }

    private fun applyTheme(theme: Theme) {
        root.setBackgroundColor(theme.bg)
        home.applyTheme(theme)
        search.applyTheme(theme)
        settingsView.applyTheme(theme)
        studio.applyTheme(theme)
        popup.applyTheme(theme)
        val light = if (theme.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(
            light, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    private fun applyStatusBar() {
        val controller = window.insetsController ?: return
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (app.settings[SettingsRegistry.STATUS_BAR]) controller.show(WindowInsets.Type.statusBars())
        else controller.hide(WindowInsets.Type.statusBars())
    }

    private fun showFavorites() = home.setFavorites(app.favorites.all.mapNotNull { app.apps[it] })

    // ------------------------------------------------------------------ HomeView.Host

    override fun launch(app: AppEntry, source: View) = run(app, source)

    override fun showAppMenu(app: AppEntry, anchor: View) = popup.show(app, anchor, centered = search.isOpen)

    override fun openSettings() {
        if (search.isOpen) search.close()
        if (studio.isOpen) closeStudio()
        settingsView.open()
    }

    override fun dragSearch(distance: Float) = search.dragOpen(distance)

    override fun releaseSearch(velocity: Float) = search.releaseOpen(velocity)

    // ------------------------------------------------------------------ SearchLayer.Host

    override fun run(result: SearchResult, source: View) = start(result, source)

    /** Home dims and sinks a little as search comes down over it. */
    override fun onSearchProgress(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        home.alpha = 1f - p
        home.translationY = p * root.height * Globals.Motion.SEARCH_TRAVEL * Globals.Motion.HOME_PARALLAX
    }

    // ------------------------------------------------------------------ ContextPopup.Host

    override fun isFavorite(app: AppEntry) = app.key in this.app.favorites

    override fun toggleFavorite(app: AppEntry) {
        if (isFavorite(app)) this.app.favorites.remove(app.key) else this.app.favorites.add(app.key)
    }

    override fun openAppInfo(app: AppEntry, source: View) {
        blur.out()
        this.app.apps.openInfo(app, source)
    }

    // ------------------------------------------------------------------ SettingsHost

    /** The editor works on home itself, so settings steps aside first. */
    override fun openStudio() {
        settingsView.close()
        studio.open()
    }

    override fun requestPermission(permission: String) {
        requestPermissions(arrayOf(permission), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        val denied = permissions.indices.firstOrNull { grantResults.getOrNull(it) != PackageManager.PERMISSION_GRANTED }
        // Denied for good: the system won't ask again, so the only way left is the app's settings page.
        if (denied != null && !shouldShowRequestPermissionRationale(permissions[denied])) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        app.contacts.refresh()
        settingsView.refresh()
    }

    // ------------------------------------------------------------------ LauncherUi (adb)

    override val isSearchOpen get() = search.isOpen
    override val isSettingsOpen get() = settingsView.isOpen
    override val query get() = search.query

    override fun openSearch(query: String) {
        popup.dismiss()
        if (studio.isOpen) closeStudio()
        if (settingsView.isOpen) settingsView.close()
        search.open(query.ifEmpty { null })
    }

    override fun closeSearch() = search.close()

    override fun closeSettings() = settingsView.close()

    override fun run(result: SearchResult) = start(result, null)

    private fun closeStudio() = studio.close()

    /** Everything leaves the launcher through here, so the blur-out always plays. */
    private fun start(result: SearchResult, source: View?) {
        blur.out()
        result.run(this, source)
    }

    private companion object {
        const val PERMISSION_REQUEST = 1
    }
}
