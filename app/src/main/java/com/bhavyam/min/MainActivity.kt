package com.bhavyam.min

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.cli.LauncherUi
import com.bhavyam.min.home.HomeRoot
import com.bhavyam.min.search.Launcher
import com.bhavyam.min.settings.SettingsHost
import com.bhavyam.min.settings.SettingsRegistry

/**
 * The home activity. It owns the window and the system-facing jobs — launching, permissions,
 * the home role, back, the status bar — and hands everything visual to [HomeRoot]. It is
 * also the [LauncherUi] the adb CLI drives while it exists.
 */
class MainActivity : Activity(), Launcher, SettingsHost, LauncherUi {

    private val app get() = App.of(this)
    private lateinit var root: HomeRoot
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val back = OnBackInvokedCallback { root.back() }
    private val onSetting: (String) -> Unit = { key -> if (key == SettingsRegistry.STATUS_BAR.key) applyStatusBar() }
    private var visible = false

    override val activity: Activity get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawEdgeToEdge()
        root = HomeRoot(this, launcher = this, host = this, onInfo = ::openAppInfo)
        setContentView(root)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back)
        app.prefs.observe(onSetting)
        app.ui = this
        applyStatusBar()
    }

    override fun onDestroy() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(back)
        app.prefs.unobserve(onSetting)
        if (app.ui === this) app.ui = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        app.contacts.ensureLoaded()
        root.sharpen()
        root.refreshSettings()
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    /** Back on the home screen later — from an app, the lock screen or the screen turning on. */
    override fun onStop() {
        super.onStop()
        root.reset()
        root.blur(animate = false)
    }

    /** The home button while the launcher is already in front: back to a clean home. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (visible) root.goHome()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        app.contacts.ensureLoaded()
        root.refreshSettings()
    }

    /** Android 15 draws every app edge to edge; on 13–14 the launcher has to ask for it. */
    @Suppress("DEPRECATION") // deprecated only because 15 made it the default
    private fun drawEdgeToEdge() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) window.setDecorFitsSystemWindows(false)
    }

    private fun applyStatusBar() {
        val controller = window.insetsController ?: return
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (app.prefs[SettingsRegistry.STATUS_BAR]) controller.show(WindowInsets.Type.statusBars())
        else controller.hide(WindowInsets.Type.statusBars())
    }

    /* ------------------------------------------------------------ Launcher */

    override fun launchApp(app: AppEntry, source: View?) {
        try {
            launcherApps.startMainActivity(app.component, app.user, source?.let(::boundsOf), options(source))
            this.app.apps.touch(app)
            root.blur(animate = true)
        } catch (e: ActivityNotFoundException) {
            cannotOpen(app.label)
        } catch (e: SecurityException) {
            cannotOpen(app.label)
        }
    }

    override fun startIntent(intent: Intent, source: View?) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options(source))
            root.blur(animate = true)
        } catch (e: ActivityNotFoundException) {
            cannotOpen(intent.`package` ?: intent.data?.scheme ?: "that")
        }
    }

    /** The app grows out of the row that was tapped. */
    private fun options(source: View?): Bundle? =
        source?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }

    private fun boundsOf(v: View): Rect {
        val at = IntArray(2)
        v.getLocationOnScreen(at)
        return Rect(at[0], at[1], at[0] + v.width, at[1] + v.height)
    }

    private fun cannotOpen(what: String) = Toast.makeText(this, "can't open $what", Toast.LENGTH_SHORT).show()

    private fun openAppInfo(entry: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null)
        } catch (e: SecurityException) {
            cannotOpen("app info")
        }
    }

    /* ------------------------------------------------------------ SettingsHost */

    override fun openStudio() = root.openStudio()

    /** Asks for [permission]; once granted (or refused for good) the system page is the place to change it. */
    override fun requestPermission(permission: String) {
        val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val refusedForGood = app.prefs.string(ASKED + permission) != null && !shouldShowRequestPermissionRationale(permission)
        if (granted || refusedForGood) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            return
        }
        app.prefs.putString(ASKED + permission, "1")
        requestPermissions(arrayOf(permission), 0)
    }

    override fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 0)
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    /* ------------------------------------------------------------ LauncherUi */

    override val searchOpen get() = root.searchOpen
    override val settingsOpen get() = root.settingsOpen
    override fun openSearch(query: String) = root.openSearch(query)
    override fun closeSearch() = root.closeSearch()
    override fun openSettings() = root.openSettings()
    override fun closeSettings() = root.closeSettings()
    override fun launch(app: AppEntry) = launchApp(app, null)

    private companion object {
        const val ASKED = "asked."
    }
}
