package dev.bhavyam.min.ui.home

import android.view.View
import dev.bhavyam.min.Globals
import dev.bhavyam.min.motion.Spring
import dev.bhavyam.min.settings.SettingsRegistry
import dev.bhavyam.min.settings.SettingsStore

/**
 * The springs that move one home block off its laid-out place: following a finger while it is
 * lifted, gliding to a new anchor (FLIP: start where it was seen, spring to where it now belongs),
 * and the little pop when its style changes. [onMove] runs on every frame it moves.
 */
class BlockMotion(private val view: View, private val settings: SettingsStore, private val onMove: () -> Unit) {

    private val x = Spring(PRECISION) { view.translationX = it; onMove() }
    private val y = Spring(PRECISION) { view.translationY = it; onMove() }
    private val scale = Spring(SCALE_PRECISION) { view.scaleX = it; view.scaleY = it }.also { it.snapTo(1f) }

    /** Head towards an offset from the laid-out position. */
    fun follow(dx: Float, dy: Float) {
        val spec = settings.spring(SettingsRegistry.MOVE_MOTION)
        x.animateTo(dx, spec)
        y.animateTo(dy, spec)
    }

    /** Raised under the finger (true) or set back down (false). */
    fun lift(up: Boolean) = scale.animateTo(if (up) Globals.Motion.LIFT_SCALE else 1f, settings.spring(SettingsRegistry.MOVE_MOTION))

    /** The layout just moved the view by ([dx], [dy]): keep it where it was seen, then glide home. */
    fun flip(dx: Float, dy: Float) {
        x.snapTo(view.translationX - dx)
        y.snapTo(view.translationY - dy)
        follow(0f, 0f)
    }

    /** A quick dip and bounce back: "this changed". */
    fun pop() {
        scale.snapTo(POP_FROM)
        scale.animateTo(1f, settings.spring(SettingsRegistry.POPUP_MOTION))
    }

    fun reset() {
        x.snapTo(0f)
        y.snapTo(0f)
        scale.snapTo(1f)
    }

    private companion object {
        const val PRECISION = 0.5f
        const val SCALE_PRECISION = 0.0005f
        const val POP_FROM = 0.94f
    }
}
