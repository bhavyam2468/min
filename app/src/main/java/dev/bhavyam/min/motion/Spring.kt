package dev.bhavyam.min.motion

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A damped harmonic oscillator (unit mass) stepped by its exact closed-form solution on every
 * vsync, so it stays stable under any frame time and keeps velocity across retargets.
 *
 * Every motion in the launcher is one of these. [onUpdate] is called with the current value each
 * frame (it must not allocate); [onRest] when the spring settles.
 *
 * @param precision distance from the target (in value units) below which the spring may settle.
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

    /** Called once each time the spring comes to rest at [target]. */
    var onRest: (() -> Unit)? = null

    private var stiffness = 400f
    private var dampingRatio = 1f
    private var lastFrameNanos = -1L

    /** Animate from the current value/velocity towards [to]. */
    fun animateTo(to: Float, spec: SpringSpec, startVelocity: Float = velocity) {
        if (!isRunning && to == value && startVelocity == 0f) return
        target = to
        velocity = startVelocity
        stiffness = spec.stiffness
        dampingRatio = spec.dampingRatio
        if (!isRunning) {
            isRunning = true
            lastFrameNanos = -1L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Jump to [to] with no motion (also the way to follow a finger). Cancels a running animation. */
    fun snapTo(to: Float) {
        if (isRunning) {
            Choreographer.getInstance().removeFrameCallback(this)
            isRunning = false
        }
        value = to
        target = to
        velocity = 0f
        onUpdate(to)
    }

    /** Freeze where it is, keeping the value but dropping momentum (a finger catching it mid-flight). */
    fun stop() = snapTo(value)

    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos < 0) FIRST_FRAME_SECONDS
        else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(MAX_FRAME_SECONDS)
        lastFrameNanos = frameTimeNanos
        step(dt)
        if (abs(value - target) < precision && abs(velocity) < precision * REST_VELOCITY_FACTOR) {
            value = target
            velocity = 0f
            isRunning = false
            onUpdate(value)
            onRest?.invoke()
        } else {
            onUpdate(value)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun step(t: Float) {
        val w0 = sqrt(stiffness)
        val z = dampingRatio
        val x0 = value - target
        val v0 = velocity
        val x: Float
        val v: Float
        if (z < 1f) {
            val wd = w0 * sqrt(1f - z * z)
            val decay = exp(-z * w0 * t)
            val a = x0
            val b = (z * w0 * x0 + v0) / wd
            val c = cos(wd * t)
            val s = sin(wd * t)
            x = decay * (a * c + b * s)
            v = -z * w0 * x + decay * (-a * wd * s + b * wd * c)
        } else if (z == 1f) {
            val decay = exp(-w0 * t)
            val b = v0 + w0 * x0
            x = (x0 + b * t) * decay
            v = (b - w0 * (x0 + b * t)) * decay
        } else {
            val r = w0 * sqrt(z * z - 1f)
            val gPlus = -z * w0 + r
            val gMinus = -z * w0 - r
            val b = (gMinus * x0 - v0) / (gMinus - gPlus)
            val a = x0 - b
            val eMinus = exp(gMinus * t)
            val ePlus = exp(gPlus * t)
            x = a * eMinus + b * ePlus
            v = a * gMinus * eMinus + b * gPlus * ePlus
        }
        value = target + x
        velocity = v
    }

    private companion object {
        const val FIRST_FRAME_SECONDS = 1f / 120f
        const val MAX_FRAME_SECONDS = 1f / 20f
        const val REST_VELOCITY_FACTOR = 12f
    }
}

/** Stiffness (k, unit mass) and damping ratio (1 = critical, < 1 bounces). */
class SpringSpec(val stiffness: Float, val dampingRatio: Float)
