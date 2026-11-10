package com.bhavyam.min.ui

import android.view.View
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals

/**
 * The physical "sink" of anything you touch: scales [view] down on a spring while pressed and
 * reports the press depth (0..1) so the owner can deepen its fill with it.
 */
class Press(private val view: View, private val onDepth: (Float) -> Unit = {}) {
    private val motion = App.of(view.context).motion
    private val spring = Spring(0.001f) { p ->
        val s = 1f - (1f - Globals.PRESS_SCALE) * p
        view.scaleX = s
        view.scaleY = s
        onDepth(p)
    }

    val depth: Float get() = spring.value

    fun set(pressed: Boolean) {
        val to = if (pressed) 1f else 0f
        if (spring.target != to) spring.animateTo(to, motion[SettingsRegistry.SPRING_PRESS])
    }
}
