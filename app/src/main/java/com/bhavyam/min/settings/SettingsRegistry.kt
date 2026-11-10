package com.bhavyam.min.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.ui.Icon

/**
 * Every setting the launcher has, as data. Adding a setting is adding a `val` and listing it in
 * a category; adding a category is adding an [Entry]. The settings screen, its search and the
 * adb CLI all read from here — nothing else needs to change.
 */
object SettingsRegistry {

    /* ---------------------------------------------------------------- display */

    val STATUS_BAR = Toggle("status_bar", "Status bar", "Show the system bar with time and notifications", false)
    val BLUR = Toggle("blur", "Blur to focus", "Soften the launcher as apps open, sharpen it as they close", true)
    val BLUR_RADIUS = Slider(
        "blur_radius", "Blur strength", "How far out of focus home goes",
        min = 4f, max = 60f, default = Globals.BLUR_RADIUS, decimals = 0, unit = "dp",
    )

    /* ---------------------------------------------------------------- animations */

    val ANIM_SPEED = Slider(
        "anim_speed", "Speed", "Plays every spring faster or slower",
        min = 0.25f, max = 3f, default = 1f, decimals = 2, unit = "×",
    )
    val SPRING_SEARCH = SpringSetting("spring_search", "Search", "Swiping into search and back", Globals.SPRING_SEARCH)
    val SPRING_PAGE = SpringSetting("spring_page", "Categories", "Paging between apps, contacts and search", Globals.SPRING_PAGE)
    val SPRING_POPUP = SpringSetting("spring_popup", "Popup", "The long-press menu", Globals.SPRING_POPUP)
    val SPRING_OVERLAY = SpringSetting("spring_overlay", "Overlays", "Settings and the studio arriving", Globals.SPRING_OVERLAY)
    val SPRING_PRESS = SpringSetting("spring_press", "Press", "How a row sinks under your finger", Globals.SPRING_PRESS)
    val SPRING_LIST = SpringSetting("spring_list", "Lists", "Favourites arriving, leaving and moving", Globals.SPRING_LIST)
    val SPRING_FOCUS = SpringSetting("spring_focus", "Focus", "Blur to focus when coming home", Globals.SPRING_FOCUS)

    /* ---------------------------------------------------------------- search providers */

    val PROVIDERS = Custom("providers", "Providers", "Hold the handle to reorder · switch to hide", ::ProviderOrderView)

    /* ---------------------------------------------------------------- permissions */

    private fun granted(host: SettingsHost, permission: String) =
        if (host.activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "granted"
        else "tap to grant"

    val CONTACTS = Action(
        "perm_contacts", "Contacts",
        state = { "${granted(it, Manifest.permission.READ_CONTACTS)} · shows the contacts category" },
        run = { it.requestPermission(Manifest.permission.READ_CONTACTS) },
    )
    val CALLS = Action(
        "perm_calls", "Direct calls",
        state = { "${granted(it, Manifest.permission.CALL_PHONE)} · otherwise the dialer opens first" },
        run = { it.requestPermission(Manifest.permission.CALL_PHONE) },
    )
    val HOME_ROLE = Action(
        "default_home", "Default launcher",
        state = {
            val held = it.activity.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
            if (held) "min is your home screen" else "tap to make min your home screen"
        },
        run = { it.requestHomeRole() },
    )

    /* ---------------------------------------------------------------- the list */

    val ENTRIES: List<Entry> = listOf(
        Category("display", Icon.DISPLAY, "status bar, blur", listOf(STATUS_BAR, BLUR, BLUR_RADIUS)),
        Link("customisation", Icon.PALETTE, "colour, shape, type, icons, depth, clock") { it.openStudio() },
        Category("search providers", Icon.GLOBE, "where a search can go, in order", listOf(PROVIDERS)),
        Category(
            "animations", Icon.ZAP, "speed and the feel of every spring",
            listOf(ANIM_SPEED, SPRING_SEARCH, SPRING_PAGE, SPRING_POPUP, SPRING_OVERLAY, SPRING_PRESS, SPRING_LIST, SPRING_FOCUS),
        ),
        Category("permissions", Icon.SHIELD, "contacts, calls, default launcher", listOf(CONTACTS, CALLS, HOME_ROLE)),
    )

    /** Settings that are a single value — what the adb CLI can read and write. */
    val SIMPLE: List<Setting> = ENTRIES.filterIsInstance<Category>()
        .flatMap { it.settings }
        .filter { it is Toggle || it is Slider }
}
