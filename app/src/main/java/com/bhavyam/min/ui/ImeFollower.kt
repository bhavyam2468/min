package com.bhavyam.min.ui

import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation

/**
 * Keeps [target]'s bottom edge glued to the keyboard, frame by frame.
 *
 * Laying out on every frame of the keyboard animation would re-measure every result row, so
 * instead the layout is held at the inset it had when the animation started, [target] is
 * translated by the live difference each frame, and the final inset is laid out once at the
 * end — at which point the translation is exactly zero, so nothing jumps.
 *
 * [apply] receives the bottom inset (keyboard or navigation bar, whichever is taller) to lay out with.
 */
class ImeFollower(
    private val target: View,
    private val apply: (WindowInsets, Int) -> Unit,
) : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP), View.OnApplyWindowInsetsListener {

    private var animating = false
    private var laidOut = 0
    private var latest: WindowInsets? = null

    fun attach() {
        target.setOnApplyWindowInsetsListener(this)
        target.setWindowInsetsAnimationCallback(this)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        latest = insets
        if (!animating) {
            laidOut = bottom(insets)
            apply(insets, laidOut)
        }
        return insets
    }

    override fun onPrepare(animation: WindowInsetsAnimation) {
        if (animation.typeMask and WindowInsets.Type.ime() != 0) animating = true
    }

    override fun onProgress(insets: WindowInsets, running: List<WindowInsetsAnimation>): WindowInsets {
        if (animating) target.translationY = (laidOut - bottom(insets)).toFloat()
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimation) {
        if (!animating || animation.typeMask and WindowInsets.Type.ime() == 0) return
        animating = false
        target.translationY = 0f
        latest?.let { onApplyWindowInsets(target, it) }
    }

    private fun bottom(insets: WindowInsets) = maxOf(
        insets.getInsets(WindowInsets.Type.ime()).bottom,
        insets.safe().bottom,
    )
}
