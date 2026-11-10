package dev.bhavyam.min.motion

import kotlin.math.abs
import kotlin.math.sign

/**
 * Resistance applied when a drag goes past its bounds: the further you pull, the less it follows,
 * approaching [extent] asymptotically (the classic scroll-view rubber band).
 */
fun rubberBand(overshoot: Float, extent: Float, coefficient: Float): Float {
    if (extent <= 0f) return 0f
    val a = abs(overshoot)
    return sign(overshoot) * (1f - 1f / (a * coefficient / extent + 1f)) * extent
}
