package dev.min.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Horizontal spring pager that keeps each category a full-width page. */
public final class CategoryPager extends FrameLayout {
    public interface Listener {
        void onPageSelected(int index, boolean fromUser);
    }

    public static final class PageData {
        public final String id;
        public final List<SearchResult> results;

        public PageData(String id, List<SearchResult> results) {
            this.id = id;
            this.results = results;
        }
    }

    private final Listener listener;
    private final ResultPageView.Listener pageListener;
    private final Map<String, ResultPageView> pagesById = new HashMap<>();
    private final ArrayList<PageData> pages = new ArrayList<>();
    private final SpringValue spring;
    private final int touchSlop;
    private float position;
    private float downX;
    private float downY;
    private float startPosition;
    private boolean dragging;
    private VelocityTracker velocityTracker;

    public CategoryPager(Context context, Listener listener, ResultPageView.Listener pageListener) {
        super(context);
        this.listener = listener;
        this.pageListener = pageListener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        spring = new SpringValue(this, this::setPositionInternal);
        spring.setPhysics(UiTokens.GENTLE_STIFFNESS, UiTokens.GENTLE_DAMPING);
    }

    public void setPages(List<PageData> newPages, int selectedIndex) {
        pages.clear();
        pages.addAll(newPages);
        removeAllViews();
        for (PageData data : pages) {
            ResultPageView page = pagesById.get(data.id);
            if (page == null) {
                page = new ResultPageView(getContext(), pageListener);
                pagesById.put(data.id, page);
            }
            page.setResults(data.results);
            addView(page);
        }
        for (String id : new ArrayList<>(pagesById.keySet())) {
            boolean stillPresent = false;
            for (PageData data : pages) {
                if (data.id.equals(id)) {
                    stillPresent = true;
                    break;
                }
            }
            if (!stillPresent) {
                pagesById.remove(id);
            }
        }
        int safeIndex = pages.isEmpty() ? 0 : Math.max(0, Math.min(selectedIndex, pages.size() - 1));
        spring.snapTo(safeIndex);
        requestLayout();
    }

    public String pageIdAt(int index) {
        return index >= 0 && index < pages.size() ? pages.get(index).id : "";
    }

    public int pageCount() {
        return pages.size();
    }

    public int selectedIndex() {
        return pages.isEmpty() ? 0 : Math.max(0, Math.min(Math.round(position), pages.size() - 1));
    }

    public void springTo(int index, float initialVelocity) {
        if (pages.isEmpty()) {
            return;
        }
        int safe = Math.max(0, Math.min(index, pages.size() - 1));
        spring.start(safe, initialVelocity);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(0, 0, width, height);
        }
        applyPageTransforms();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            startPosition = position;
            dragging = false;
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
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                dragging = true;
                spring.cancel();
                return true;
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
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
            float width = Math.max(1f, getWidth());
            float next = startPosition - (event.getX() - downX) / width;
            float last = Math.max(0, pages.size() - 1);
            setPositionInternal(Math.max(-UiTokens.PAGER_OVERSCROLL_PAGES, Math.min(last + UiTokens.PAGER_OVERSCROLL_PAGES, next)));
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            velocityTracker.computeCurrentVelocity(1000);
            float velocity = velocityTracker.getXVelocity();
            int destination = Math.round(position);
            if (Math.abs(velocity) > UiTokens.PAGER_FLING_VELOCITY) {
                destination = velocity < 0f ? (int) Math.ceil(position) : (int) Math.floor(position);
            }
            destination = Math.max(0, Math.min(destination, pages.size() - 1));
            listener.onPageSelected(destination, true);
            springTo(destination, -velocity / Math.max(1f, getWidth()));
            dragging = false;
            recycleVelocityTracker();
            return true;
        }
        return true;
    }

    private void setPositionInternal(float value) {
        position = value;
        applyPageTransforms();
    }

    private void applyPageTransforms() {
        float width = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View page = getChildAt(i);
            float distance = Math.abs(i - position);
            page.setTranslationX((i - position) * width);
            page.setScaleX(Math.max(UiTokens.PAGER_NEIGHBOR_SCALE, 1f - distance * UiTokens.PAGER_SCALE_FALLOFF));
            page.setScaleY(Math.max(UiTokens.PAGER_NEIGHBOR_SCALE, 1f - distance * UiTokens.PAGER_SCALE_FALLOFF));
            page.setAlpha(Math.max(UiTokens.PAGER_NEIGHBOR_ALPHA, 1f - distance * UiTokens.PAGER_ALPHA_FALLOFF));
        }
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
