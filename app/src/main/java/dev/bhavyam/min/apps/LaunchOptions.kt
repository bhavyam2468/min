package dev.bhavyam.min.apps

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View

/** The window animation for anything started from the launcher: it grows out of the tapped view. */
class LaunchOptions(source: View?) {
    val bounds: Rect? = source?.let {
        val xy = IntArray(2)
        it.getLocationOnScreen(xy)
        Rect(xy[0], xy[1], xy[0] + it.width, xy[1] + it.height)
    }

    val bundle: Bundle = (
        if (source != null) ActivityOptions.makeScaleUpAnimation(source, 0, 0, source.width, source.height)
        else ActivityOptions.makeBasic()
        ).toBundle()
}
