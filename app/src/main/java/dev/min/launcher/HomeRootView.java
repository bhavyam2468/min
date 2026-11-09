package dev.min.launcher;

import android.content.Context;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home's clock, favorites, and empty-space gesture surface. */
public final class HomeRootView extends FrameLayout {
    public interface Listener {
        void onSwipeStart();
        void onSwipeMove(float deltaY);
        void onSwipeEnd(float velocityY);
        void onEmptyLongPress();
        void onFavoriteTap(AppEntry app);
        void onFavoriteLongPress(AppEntry app);
    }

    private final Listener listener;
    private final LinearLayout clockRow;
    private final TextView dateView;
    private final TextView timeView;
    private final LinearLayout favorites;
    private final Handler handler = new Handler();
    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };
    private final Runnable emptyLongPress = () -> {
        if (!dragging) {
            listener.onEmptyLongPress();
        }
    };
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean trackingEmpty;
    private boolean dragging;
    private VelocityTracker velocityTracker;
    private int insetTop;
    private int insetBottom;
    private int insetLeft;
    private int insetRight;

    public HomeRootView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackgroundColor(UiTokens.COLOR_BLACK);
        setClickable(true);

        clockRow = new LinearLayout(context);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        dateView = clockText(context);
        timeView = clockText(context);
        clockRow.addView(dateView);
        TextView spacer = new TextView(context);
        clockRow.addView(spacer, new LinearLayout.LayoutParams(UiTokens.dp(context, UiTokens.CLOCK_GAP_DP), 1));
        clockRow.addView(timeView);
        addView(clockRow);

        favorites = new LinearLayout(context);
        favorites.setOrientation(LinearLayout.VERTICAL);
        addView(favorites);
    }

    private TextView clockText(Context context) {
        TextView text = new TextView(context);
        text.setTextColor(UiTokens.COLOR_TEXT);
        text.setTextSize(UiTokens.TEXT_CLOCK_SP);
        text.setTypeface(UiTokens.typeface());
        text.setIncludeFontPadding(false);
        return text;
    }

    public void setInsets(int top, int bottom, int left, int right) {
        insetTop = top;
        insetBottom = bottom;
        insetLeft = left;
        insetRight = right;
        requestLayout();
    }

    public void setFavorites(List<AppEntry> entries) {
        favorites.removeAllViews();
        for (AppEntry entry : entries) {
            PressableFrameLayout row = new PressableFrameLayout(getContext());
            TextView label = new TextView(getContext());
            label.setText(entry.label);
            label.setTextColor(UiTokens.COLOR_TEXT);
            label.setTextSize(UiTokens.TEXT_FAVORITE_SP);
            label.setTypeface(UiTokens.typeface());
            label.setIncludeFontPadding(false);
            row.addView(label, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(view -> listener.onFavoriteTap(entry));
            row.setOnLongClickListener(view -> {
                listener.onFavoriteLongPress(entry);
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                    UiTokens.dp(getContext(), UiTokens.RESULT_ROW_MIN_DP));
            params.bottomMargin = UiTokens.dp(getContext(), UiTokens.FAVORITE_LINE_GAP_DP);
            favorites.addView(row, params);
        }
        requestLayout();
    }

    public void updateClock() {
        Date now = new Date();
        dateView.setText(new SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(now));
        String timePattern = DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm a";
        timeView.setText(new SimpleDateFormat(timePattern, Locale.getDefault()).format(now));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateClock();
        handler.removeCallbacks(clockTicker);
        handler.post(clockTicker);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(clockTicker);
        handler.removeCallbacks(emptyLongPress);
        recycleVelocityTracker();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        measureChild(clockRow, MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        int contentWidth = Math.max(0, width - insetLeft - insetRight - 2 * UiTokens.sidePadding(getContext(), width));
        favorites.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - insetTop - insetBottom), MeasureSpec.AT_MOST));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int side = UiTokens.sidePadding(getContext(), width);
        int clockLeft = insetLeft + side;
        int clockTop = insetTop + UiTokens.dp(getContext(), UiTokens.CLOCK_TOP_DP);
        clockRow.layout(clockLeft, clockTop, clockLeft + clockRow.getMeasuredWidth(),
                clockTop + clockRow.getMeasuredHeight());
        int usableHeight = Math.max(0, height - insetTop - insetBottom);
        int favoriteTop = insetTop + usableHeight * UiTokens.HOME_TOP_PERCENT / 100;
        int favoriteLeft = insetLeft + side;
        favorites.layout(favoriteLeft, favoriteTop, favoriteLeft + favorites.getMeasuredWidth(),
                favoriteTop + favorites.getMeasuredHeight());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            dragging = false;
            trackingEmpty = !pointOnFavorite(downX, downY);
            if (trackingEmpty) {
                handler.postDelayed(emptyLongPress, ViewConfiguration.getLongPressTimeout());
            }
            obtainVelocityTracker().addMovement(event);
            return false;
        }
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }
        if (dragging) {
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                handler.removeCallbacks(emptyLongPress);
            }
            if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx)) {
                dragging = true;
                handler.removeCallbacks(emptyLongPress);
                listener.onSwipeStart();
                listener.onSwipeMove(dy);
                return true;
            }
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            handler.removeCallbacks(emptyLongPress);
            recycleVelocityTracker();
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            listener.onSwipeMove(event.getY() - downY);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            velocityTracker.computeCurrentVelocity(1000);
            listener.onSwipeEnd(velocityTracker.getYVelocity());
            handler.removeCallbacks(emptyLongPress);
            dragging = false;
            recycleVelocityTracker();
            return true;
        }
        return true;
    }

    private boolean pointOnFavorite(float x, float y) {
        for (int i = 0; i < favorites.getChildCount(); i++) {
            View row = favorites.getChildAt(i);
            float left = favorites.getLeft() + row.getLeft();
            float top = favorites.getTop() + row.getTop();
            if (x >= left && x <= left + row.getWidth() && y >= top && y <= top + row.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private VelocityTracker obtainVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        return velocityTracker;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}
