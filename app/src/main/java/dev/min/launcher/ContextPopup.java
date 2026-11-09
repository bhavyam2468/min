package dev.min.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Context menu shared by favorite and search app long-presses. */
public final class ContextPopup extends FrameLayout {
    public interface Listener {
        void onAppInfo(AppEntry app);
        void onFavoriteAction(AppEntry app, boolean currentlyFavorite);
    }

    private final Listener listener;
    private final FrameLayout card;
    private final TextView appName;
    private final PressableFrameLayout favoriteOption;
    private final SpringValue spring;
    private AppEntry app;
    private boolean favorite;
    private Runnable afterDismiss;

    public ContextPopup(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(UiTokens.COLOR_DIM);
        setVisibility(GONE);
        setClickable(true);

        card = new FrameLayout(context);
        card.setBackground(UiDrawables.rounded(context, UiTokens.COLOR_POPUP, UiTokens.CARD_RADIUS_DP,
                UiTokens.COLOR_BORDER));
        addView(card, new FrameLayout.LayoutParams(UiTokens.dp(context, UiTokens.POPUP_WIDTH_DP),
                LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        appName = new TextView(context);
        appName.setTextColor(UiTokens.COLOR_MUTED);
        appName.setTextSize(UiTokens.TEXT_POPUP_TITLE_SP);
        appName.setGravity(Gravity.CENTER_VERTICAL);
        appName.setTypeface(UiTokens.typeface());
        appName.setIncludeFontPadding(false);
        appName.setPadding(UiTokens.dp(context, UiTokens.CARD_PADDING_DP), 0,
                UiTokens.dp(context, UiTokens.CARD_PADDING_DP), 0);
        content.addView(appName, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                UiTokens.dp(context, UiTokens.POPUP_TITLE_HEIGHT_DP)));
        View divider = new View(context);
        divider.setBackgroundColor(UiTokens.COLOR_BORDER);
        content.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                UiTokens.dp(context, UiTokens.POPUP_DIVIDER_DP)));
        PressableFrameLayout info = option(context, "App info");
        info.setOnClickListener(view -> dismiss(() -> listener.onAppInfo(app)));
        content.addView(info, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                UiTokens.dp(context, UiTokens.POPUP_ROW_HEIGHT_DP)));
        favoriteOption = option(context, "Set as favorite");
        favoriteOption.setOnClickListener(view -> dismiss(() -> listener.onFavoriteAction(app, favorite)));
        content.addView(favoriteOption, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                UiTokens.dp(context, UiTokens.POPUP_ROW_HEIGHT_DP)));

        spring = new SpringValue(this, new SpringValue.Listener() {
            @Override public void onUpdate(float value) {
                applyFraction(value);
            }

            @Override public void onRest(float value) {
                if (value <= 0.001f) {
                    setVisibility(GONE);
                    Runnable callback = afterDismiss;
                    afterDismiss = null;
                    if (callback != null) {
                        callback.run();
                    }
                }
            }
        });
        spring.setPhysics(UiTokens.SPRING_STIFFNESS, UiTokens.SPRING_DAMPING);
    }

    private PressableFrameLayout option(Context context, String title) {
        PressableFrameLayout row = new PressableFrameLayout(context);
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(UiTokens.COLOR_TEXT);
        label.setTextSize(UiTokens.TEXT_ITEM_TITLE_SP);
        label.setTypeface(UiTokens.typeface());
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(UiTokens.dp(context, UiTokens.CARD_PADDING_DP), 0,
                UiTokens.dp(context, UiTokens.CARD_PADDING_DP), 0);
        row.addView(label, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return row;
    }

    public void show(AppEntry selected, boolean isFavorite) {
        app = selected;
        favorite = isFavorite;
        appName.setText(selected.label);
        updateOptionText();
        afterDismiss = null;
        setVisibility(VISIBLE);
        spring.snapTo(0f);
        spring.start(1f, 0f);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void dismissImmediately() {
        spring.cancel();
        afterDismiss = null;
        setAlpha(0f);
        setVisibility(GONE);
    }

    public void dismiss() {
        dismiss(null);
    }

    public void dismiss(Runnable after) {
        if (!isShowing()) {
            if (after != null) {
                after.run();
            }
            return;
        }
        afterDismiss = after;
        spring.start(0f, 0f);
    }

    private void updateOptionText() {
        View child = favoriteOption.getChildAt(0);
        if (child instanceof TextView) {
            ((TextView) child).setText(favorite ? "Remove from favorites" : "Set as favorite");
        }
    }

    private void applyFraction(float fraction) {
        float safe = Math.max(0f, Math.min(1f, fraction));
        setAlpha(safe);
        float scale = UiTokens.POPUP_START_SCALE + (1f - UiTokens.POPUP_START_SCALE) * safe;
        card.setScaleX(scale);
        card.setScaleY(scale);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !insideCard(event.getX(), event.getY())) {
            dismiss();
        }
        return true;
    }

    private boolean insideCard(float x, float y) {
        return x >= card.getLeft() && x <= card.getRight() && y >= card.getTop() && y <= card.getBottom();
    }
}
