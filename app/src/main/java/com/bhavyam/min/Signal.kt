package com.bhavyam.min

import android.view.View

/** "Something changed" — the one observer primitive every store uses. Main thread only. */
class Signal {
    private val listeners = ArrayList<() -> Unit>()

    operator fun plusAssign(listener: () -> Unit) {
        listeners += listener
    }

    operator fun minusAssign(listener: () -> Unit) {
        listeners -= listener
    }

    fun emit() {
        for (l in listeners.toTypedArray()) l()
    }
}

/**
 * Listens to [signal] only while this view is attached, so a view never outlives its window
 * through a store that lives for the whole process.
 */
fun View.observe(signal: Signal, listener: () -> Unit) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            signal += listener
        }

        override fun onViewDetachedFromWindow(v: View) {
            signal -= listener
        }
    })
    if (isAttachedToWindow) signal += listener
}
