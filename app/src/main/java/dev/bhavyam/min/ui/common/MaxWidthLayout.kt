package dev.bhavyam.min.ui.common

import android.content.Context
import android.widget.FrameLayout

/** Centres its child and caps its width, so phone layouts don't stretch across tablets and landscape. */
class MaxWidthLayout(context: Context, private val maxWidthDp: Float) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = (maxWidthDp * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val capped = if (width > max) MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY) else widthMeasureSpec
        super.onMeasure(capped, heightMeasureSpec)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val x = (width - child.measuredWidth) / 2
        child.layout(x, paddingTop, x + child.measuredWidth, paddingTop + child.measuredHeight)
    }
}
