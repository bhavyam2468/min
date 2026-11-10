package dev.bhavyam.min.settings

/**
 * The vocabulary of the settings registry. A new setting is a new value of one of these types in
 * [SettingsRegistry]; only a brand-new *kind* of control needs a row in the settings view.
 */
sealed class SettingItem(val key: String, val title: String, val summary: String?)

class Toggle(key: String, title: String, summary: String?, val default: Boolean) : SettingItem(key, title, summary)

class Slider(
    key: String,
    title: String,
    summary: String?,
    val min: Float,
    val max: Float,
    val step: Float,
    val default: Float,
    val format: (Float) -> String,
) : SettingItem(key, title, summary)

/** A row that does something when tapped (opens a screen, starts a flow). */
class Action(key: String, title: String, summary: String?, val run: (SettingsHost) -> Unit) :
    SettingItem(key, title, summary)

/** A runtime permission with its granted state and a way to grant it. */
class Permission(key: String, title: String, summary: String, val permission: String) :
    SettingItem(key, title, summary)

/** The reorderable, switchable list of fallback search providers. */
class ProviderOrder(key: String, title: String) : SettingItem(key, title, null)

/** A spring the user can tune: its stiffness and bounce are two ordinary sliders. */
class MotionSetting(id: String, title: String, stiffness: Float, bounce: Float) {
    val stiffness = Slider("${id}_stiffness", "Stiffness", null, 40f, 1600f, 10f, stiffness) { "%.0f".format(it) }
    val bounce = Slider("${id}_bounce", "Bounce", null, 0f, 0.8f, 0.01f, bounce) { "%.2f".format(it) }
    val group = Group(title, listOf(this.stiffness, this.bounce))
}

/** Rows drawn together on one card, with an optional caption. */
class Group(val title: String?, val items: List<SettingItem>)

/** A category of settings (its title is also what the settings search can match). */
class Section(val title: String, val groups: List<Group>)

/** What setting actions may ask of the screen hosting them. */
interface SettingsHost {
    fun openStudio()
    fun requestPermission(permission: String)
}
