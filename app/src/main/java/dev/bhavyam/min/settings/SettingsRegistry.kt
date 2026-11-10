package dev.bhavyam.min.settings

import android.Manifest
import dev.bhavyam.min.Globals.Motion

/**
 * Every setting, as data. To add a setting, declare it here and put it in a [Section]; to add a
 * category, add a [Section]. Storage, the settings screen, settings search and the adb
 * `get_setting` / `set_setting` commands all read this list — nothing else needs touching.
 */
object SettingsRegistry {

    val STATUS_BAR = Toggle("status_bar", "Status bar", "Show the system status bar on top", true)
    val BLUR = Toggle("blur", "Blur", "Blur to focus as apps open and close", true)

    val STUDIO = Action("studio", "Customise home", "Colour, type, shape, the clock and where things sit — edited right on home") { it.openStudio() }

    val PROVIDERS = ProviderOrder("providers", "Search providers")

    val ANIM_SPEED = Slider("anim_speed", "Speed", "Scales every animation", 0.25f, 3f, 0.05f, Motion.SPEED) { "%.2f×".format(it) }
    val BLUR_RADIUS = Slider("blur_radius", "Blur strength", null, 0f, 80f, 1f, Motion.BLUR_RADIUS) { "%.0f dp".format(it) }
    val LAUNCH_ZOOM = Slider("launch_zoom", "Launch zoom", null, 0f, 0.2f, 0.005f, Motion.LAUNCH_ZOOM) { "%.0f%%".format(it * 100) }
    val SEARCH_MOTION = MotionSetting("search", "Search", Motion.SEARCH_STIFFNESS, Motion.SEARCH_BOUNCE)
    val COLUMNS_MOTION = MotionSetting("columns", "Columns", Motion.COLUMNS_STIFFNESS, Motion.COLUMNS_BOUNCE)
    val POPUP_MOTION = MotionSetting("popup", "Popup", Motion.POPUP_STIFFNESS, Motion.POPUP_BOUNCE)
    val OVERLAY_MOTION = MotionSetting("overlay", "Settings & editor", Motion.OVERLAY_STIFFNESS, Motion.OVERLAY_BOUNCE)
    val BLUR_MOTION = MotionSetting("blur", "Blur to focus", Motion.BLUR_STIFFNESS, Motion.BLUR_BOUNCE)
    val CONTROL_MOTION = MotionSetting("controls", "Controls", Motion.CONTROL_STIFFNESS, Motion.CONTROL_BOUNCE)
    val THEME_MOTION = MotionSetting("theme", "Theme changes", Motion.THEME_STIFFNESS, Motion.THEME_BOUNCE)
    val MOVE_MOTION = MotionSetting("move", "Moving things on home", Motion.MOVE_STIFFNESS, Motion.MOVE_BOUNCE)

    val CONTACTS = Permission("contacts", "Contacts", "Search and call your contacts", Manifest.permission.READ_CONTACTS)
    val CALLS = Permission("calls", "Phone calls", "Call a contact directly instead of opening the dialer", Manifest.permission.CALL_PHONE)

    val SECTIONS = listOf(
        Section("Display", listOf(Group(null, listOf(STATUS_BAR, BLUR)))),
        Section("Customisation", listOf(Group(null, listOf(STUDIO)))),
        Section("Search providers", listOf(Group(null, listOf(PROVIDERS)))),
        Section(
            "Animations",
            listOf(
                Group(null, listOf(ANIM_SPEED, BLUR_RADIUS, LAUNCH_ZOOM)),
                SEARCH_MOTION.group, COLUMNS_MOTION.group, POPUP_MOTION.group, OVERLAY_MOTION.group,
                BLUR_MOTION.group, CONTROL_MOTION.group, THEME_MOTION.group, MOVE_MOTION.group,
            ),
        ),
        Section("Permissions", listOf(Group(null, listOf(CONTACTS, CALLS)))),
    )

    /** Settings with a plain value (toggles and sliders) — the ones adb can read and write. */
    val values: List<SettingItem> = SECTIONS.flatMap { s -> s.groups.flatMap { it.items } }
        .filter { it is Toggle || it is Slider }
}
