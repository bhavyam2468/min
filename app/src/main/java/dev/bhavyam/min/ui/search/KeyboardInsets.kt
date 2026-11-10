package dev.bhavyam.min.ui.search

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation

/**
 * Keeps [content] glued to the top of the keyboard on every frame of the IME animation, and gives
 * [root] the keyboard as real padding once the animation settles (so the result list's viewport is
 * exactly the space above the keyboard). The pattern from the platform's insets-animation sample:
 * while the IME animates, padding covers only the bars and [content] is translated by the
 * difference; at the end, padding takes over and the translation returns to zero.
 */
class KeyboardInsets(private val root: View, private val content: View) {

    private val bars = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
    private val ime = WindowInsets.Type.ime()
    private var animating = false
    private var lastInsets: WindowInsets? = null

    fun attach() {
        root.setOnApplyWindowInsetsListener { v, insets ->
            lastInsets = insets
            val p = insets.getInsets(if (animating) bars else bars or ime)
            v.setPadding(p.left, p.top, p.right, p.bottom)
            WindowInsets.CONSUMED
        }
        root.setWindowInsetsAnimationCallback(object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onPrepare(animation: WindowInsetsAnimation) {
                if (animation.typeMask and ime != 0) animating = true
            }

            override fun onProgress(insets: WindowInsets, running: MutableList<WindowInsetsAnimation>): WindowInsets {
                if (animating) {
                    val lift = Insets.max(Insets.subtract(insets.getInsets(ime), insets.getInsets(bars)), Insets.NONE)
                    content.translationY = -lift.bottom.toFloat()
                }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimation) {
                if (!animating || animation.typeMask and ime == 0) return
                animating = false
                content.translationY = 0f
                lastInsets?.let(root::dispatchApplyWindowInsets)
            }
        })
    }
}
