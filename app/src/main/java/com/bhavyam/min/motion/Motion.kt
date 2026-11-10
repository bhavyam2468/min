package com.bhavyam.min.motion

import android.animation.ValueAnimator
import com.bhavyam.min.settings.Prefs
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.settings.SpringSetting

/**
 * The feel of every spring, read live from settings → animations. Global speed scales time,
 * which for a spring means stiffness × speed² with the same bounce. When the system has
 * animations turned off (accessibility), every spring jumps.
 */
class Motion(private val prefs: Prefs) {
    operator fun get(s: SpringSetting): SpringConfig {
        if (!ValueAnimator.areAnimatorsEnabled()) return SpringConfig.INSTANT
        val base = prefs[s]
        val speed = prefs[SettingsRegistry.ANIM_SPEED]
        return SpringConfig(base.stiffness * speed * speed, base.damping)
    }
}
