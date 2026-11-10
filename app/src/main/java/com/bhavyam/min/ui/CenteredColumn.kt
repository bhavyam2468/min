package com.bhavyam.min.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

/**
 * A vertical column whose children are never wider than [maxWidthDp] and stay centred. On a
 * phone it is simply full width; on a tablet or in landscape it keeps lines a readable length
 * instead of stretching a phone layout edge to edge.
 */
class CenteredColumn(context: Context, private val maxWidthDp: Float) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val column = minOf(available, dpi(maxWidthDp) + paddingLeft + paddingRight)
        // children measure against the column; the view itself still fills what it was given,
        // and CENTER_HORIZONTAL places the column in the middle of it
        super.onMeasure(MeasureSpec.makeMeasureSpec(column, MeasureSpec.EXACTLY), heightMeasureSpec)
        setMeasuredDimension(available, measuredHeight)
    }
}
