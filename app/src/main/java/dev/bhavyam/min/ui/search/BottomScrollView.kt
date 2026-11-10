package dev.bhavyam.min.ui.search

import android.content.Context
import android.widget.ScrollView

/**
 * A viewport-filling scroll view whose content is bottom-gravity: short lists sit on the bottom
 * edge by layout alone. When content is taller than the viewport, it stays anchored to the bottom
 * (the best matches) until the user scrolls away, and re-anchors on every new result set.
 */
class BottomScrollView(context: Context) : ScrollView(context) {

    private var anchored = true

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        isVerticalFadingEdgeEnabled = true
    }

    private val maxScroll get() = ((getChildAt(0)?.height ?: 0) - (height - paddingTop - paddingBottom)).coerceAtLeast(0)

    /** New content: show its bottom. */
    fun anchorToBottom() {
        anchored = true
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (anchored) scrollTo(0, maxScroll)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        anchored = t >= maxScroll
    }

    /** No fade at the bottom edge: the best match sits right on the category label. */
    override fun getBottomFadingEdgeStrength() = 0f
}
