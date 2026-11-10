package dev.bhavyam.min.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore

/**
 * Blur-to-focus: the launcher comes into focus (blur and zoom easing out) whenever it returns — from
 * an app or from the lock screen — and goes soft again while an app opens over it.
 * Progress 1 = fully blurred, 0 = sharp.
 */
class LaunchBlur(private val context: Context, private val target: View, private val settings: SettingsStore) {

    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var waitingForUnlock = false
    private val spring = Spring(PRECISION, ::apply)

    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (waitingForUnlock) {
                waitingForUnlock = false
                focus()
            }
        }
    }

    init {
        context.registerReceiver(unlock, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED)
        spring.snapTo(1f)
    }

    /** The launcher became visible: focus now, or as soon as the lock screen is gone. */
    fun onStart() {
        waitingForUnlock = keyguard.isKeyguardLocked
        if (!waitingForUnlock) focus()
    }

    /** Hidden: start blurred next time, so returning always plays the focus animation. */
    fun onStop() = spring.snapTo(1f)

    /** An app is opening over the launcher. */
    fun out() = spring.animateTo(1f, settings.spring(SettingsRegistry.BLUR_MOTION))

    fun release() = context.unregisterReceiver(unlock)

    private fun focus() = spring.animateTo(0f, settings.spring(SettingsRegistry.BLUR_MOTION))

    private fun apply(p: Float) {
        val radius = if (settings[SettingsRegistry.BLUR]) p * settings[SettingsRegistry.BLUR_RADIUS] * target.resources.displayMetrics.density else 0f
        target.setRenderEffect(if (radius >= MIN_RADIUS) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
        val scale = 1f + p * settings[SettingsRegistry.LAUNCH_ZOOM]
        target.scaleX = scale
        target.scaleY = scale
    }

    private companion object {
        const val PRECISION = 0.001f
        const val MIN_RADIUS = 0.5f
    }
}
