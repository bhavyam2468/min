package dev.min.launcher;

import android.os.SystemClock;
import android.view.View;

/** Small reusable damped spring. Values and velocity are expressed in units per second. */
public final class SpringValue implements Runnable {
    public interface Listener {
        void onUpdate(float value);
        default void onRest(float value) { }
    }

    private static volatile float durationMultiplier = 1f;

    private final View host;
    private final Listener listener;
    private float value;
    private float velocity;
    private float target;
    private float stiffness = UiTokens.SPRING_STIFFNESS;
    private float damping = UiTokens.SPRING_DAMPING;
    private long lastFrame;
    private boolean running;

    public SpringValue(View host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    public static void setDurationMultiplier(float multiplier) {
        durationMultiplier = Math.max(0.1f, multiplier);
    }

    public void setPhysics(float stiffness, float damping) {
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void snapTo(float newValue) {
        cancel();
        value = newValue;
        velocity = 0f;
        listener.onUpdate(value);
    }

    public void start(float newTarget, float initialVelocity) {
        target = newTarget;
        velocity = initialVelocity;
        lastFrame = SystemClock.uptimeMillis();
        if (!running) {
            running = true;
            host.postOnAnimation(this);
        }
    }

    public void cancel() {
        running = false;
        host.removeCallbacks(this);
    }

    public boolean isRunning() {
        return running;
    }

    public float getValue() {
        return value;
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float seconds = Math.min(UiTokens.SPRING_MAX_FRAME_SECONDS,
                Math.max(UiTokens.SPRING_MIN_FRAME_SECONDS, (now - lastFrame) / 1000f)) / durationMultiplier;
        lastFrame = now;
        float acceleration = stiffness * (target - value) - damping * velocity;
        velocity += acceleration * seconds;
        value += velocity * seconds;
        listener.onUpdate(value);
        if (Math.abs(target - value) < UiTokens.SPRING_REST_DISTANCE && Math.abs(velocity) < UiTokens.SPRING_REST_VELOCITY) {
            value = target;
            velocity = 0f;
            running = false;
            listener.onUpdate(value);
            listener.onRest(value);
            return;
        }
        host.postOnAnimation(this);
    }
}
