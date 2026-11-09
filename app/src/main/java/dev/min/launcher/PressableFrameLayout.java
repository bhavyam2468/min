package dev.min.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/** A framework-only row/card with quiet physical press feedback. */
public class PressableFrameLayout extends FrameLayout {
    private final SpringValue pressSpring;

    public PressableFrameLayout(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        pressSpring = new SpringValue(this, value -> {
            setScaleX(value);
            setScaleY(value);
        });
        pressSpring.setPhysics(UiTokens.SPRING_STIFFNESS, UiTokens.SPRING_DAMPING);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressSpring.cancel();
                setScaleX(UiTokens.PRESS_SCALE);
                setScaleY(UiTokens.PRESS_SCALE);
                setAlpha(UiTokens.PRESS_ALPHA);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setAlpha(1f);
                pressSpring.snapTo(getScaleX());
                pressSpring.start(1f, 0f);
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }
}
