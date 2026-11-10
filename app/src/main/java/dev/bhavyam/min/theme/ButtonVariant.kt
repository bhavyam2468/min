package dev.bhavyam.min.theme

/**
 * The looks an app row on home can take. [Plain] is the launcher as designed — just the name; the
 * others put a button behind it, coloured by the app colour.
 */
enum class ButtonVariant(val label: String) {
    Plain("Plain"),
    Solid("Solid"),
    Soft("Soft"),
    Outline("Outline"),
    Ghost("Ghost"),
    Gradient("Gradient"),
    ;

    /** Whether anything is drawn behind the text at rest. */
    val hasShape get() = this != Plain && this != Ghost
}
