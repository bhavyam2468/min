package com.bhavyam.min.ui

import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals

/**
 * A vertical list whose rows arrive, leave and move on springs. Changes are applied at once,
 * then animated from where things *were* (FLIP): survivors slide from their old position,
 * new rows slide in, removed rows fade out in the container's overlay so nothing jumps.
 */
class SpringList<T>(
    private val container: LinearLayout,
    private val key: (T) -> String,
    private val create: () -> View,
    private val bind: (View, T) -> Unit,
) {
    private val motion = App.of(container.context).motion
    private val views = LinkedHashMap<String, View>()

    fun submit(items: List<T>, animate: Boolean) {
        val before = HashMap<String, Int>(views.size)
        for ((k, v) in views) before[k] = v.top

        val keep = items.mapTo(HashSet(items.size), key)
        val gone = views.entries.filter { it.key !in keep }
        for ((k, v) in gone) {
            views.remove(k)
            container.removeView(v)
            if (animate) leave(v)
        }

        container.removeAllViews()
        val fresh = ArrayList<View>()
        for (item in items) {
            val k = key(item)
            val v = views.getOrPut(k) { create().also(fresh::add) }
            bind(v, item)
            container.addView(v)
        }
        if (!animate) return

        container.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                container.viewTreeObserver.removeOnPreDrawListener(this)
                for ((k, v) in views) {
                    if (v in fresh) arrive(v)
                    else before[k]?.let { old -> move(v, (old - v.top).toFloat()) }
                }
                return true
            }
        })
    }

    private fun spring(onFrame: (Float) -> Unit, done: () -> Unit = {}) {
        val s = Spring(0.002f, onFrame)
        s.onRest = done
        s.snapTo(0f)
        s.animateTo(1f, motion[SettingsRegistry.SPRING_LIST])
    }

    private fun move(v: View, dy: Float) {
        if (dy == 0f) return
        spring({ p -> v.translationY = dy * (1f - p) })
    }

    private fun arrive(v: View) {
        val dx = -v.dp(Globals.SEARCH_TRAVEL) * 0.5f
        spring({ p ->
            v.alpha = p.coerceIn(0f, 1f)
            v.translationX = dx * (1f - p)
        })
    }

    private fun leave(v: View) {
        val overlay = container.overlay
        overlay.add(v)
        val dx = -v.dp(Globals.SEARCH_TRAVEL) * 0.5f
        spring({ p ->
            v.alpha = (1f - p).coerceIn(0f, 1f)
            v.translationX = dx * p
        }, done = { overlay.remove(v) })
    }
}
