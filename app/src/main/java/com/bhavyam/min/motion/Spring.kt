package com.bhavyam.min.motion

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stiffness (k, unit mass) and damping ratio (ζ: < 1 bounces, 1 settles without overshoot).
 * Infinite stiffness means "no animation": the spring jumps straight to its target.
 */
data class SpringConfig(val stiffness: Float, val damping: Float) {
    companion object {
        val INSTANT = SpringConfig(Float.POSITIVE_INFINITY, 1f)
    }
}

/**
 * A damped spring driven by the display's vsync. It is solved analytically every frame, so it
 * stays exact at any frame rate or after a dropped frame, and can be retargeted mid-flight while
 * keeping its velocity — that is what makes a released finger hand its momentum to the motion.
 *
 * [precision] is the distance (in the value's own units) at which it counts as settled.
 */
class Spring(
    private val precision: Float,
    private val onUpdate: (Float) -> Unit,
) : Choreographer.FrameCallback {

    var value = 0f
        private set
    var velocity = 0f
        private set
    var target = 0f
        private set
    var isRunning = false
        private set

    /** Called once each time the spring settles. */
    var onRest: (() -> Unit)? = null

    private var config = SpringConfig.INSTANT
    private var lastFrame = 0L

    /** Animate towards [to], starting with [velocity] (units per second). */
    fun animateTo(to: Float, config: SpringConfig, velocity: Float = this.velocity) {
        target = to
        this.config = config
        this.velocity = velocity
        if (config.stiffness.isInfinite()) {
            snapTo(to)
            onRest?.invoke()
            return
        }
        if (!isRunning) {
            isRunning = true
            lastFrame = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Jump to [to] with no motion and stop. */
    fun snapTo(to: Float) {
        stop()
        value = to
        target = to
        velocity = 0f
        onUpdate(to)
    }

    fun stop() {
        if (isRunning) Choreographer.getInstance().removeFrameCallback(this)
        isRunning = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (lastFrame == 0L) 0f else (frameTimeNanos - lastFrame) / 1e9f
        lastFrame = frameTimeNanos
        step(dt)
        if (abs(value - target) < precision && abs(velocity) < precision * 10f) {
            isRunning = false
            value = target
            velocity = 0f
            onUpdate(value)
            onRest?.invoke()
            return
        }
        onUpdate(value)
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** Closed-form solution of x'' + 2ζω x' + ω² x = 0 over [t] seconds. */
    private fun step(t: Float) {
        if (t <= 0f) return
        val w = sqrt(config.stiffness)
        val z = config.damping
        val x0 = value - target
        val v0 = velocity
        val x: Float
        val v: Float
        when {
            z < 1f -> {
                val a = z * w
                val wd = w * sqrt(1f - z * z)
                val e = exp(-a * t)
                val c = cos(wd * t)
                val s = sin(wd * t)
                val b = (v0 + a * x0) / wd
                x = e * (x0 * c + b * s)
                v = e * ((-a * x0 + b * wd) * c + (-a * b - x0 * wd) * s)
            }
            z == 1f -> {
                val b = v0 + w * x0
                val e = exp(-w * t)
                x = (x0 + b * t) * e
                v = (v0 - w * b * t) * e
            }
            else -> {
                val d = w * sqrt(z * z - 1f)
                val r1 = -z * w + d
                val r2 = -z * w - d
                val c1 = (v0 - r2 * x0) / (r1 - r2)
                val c2 = x0 - c1
                val e1 = exp(r1 * t)
                val e2 = exp(r2 * t)
                x = c1 * e1 + c2 * e2
                v = c1 * r1 * e1 + c2 * r2 * e2
            }
        }
        value = target + x
        velocity = v
    }
}
