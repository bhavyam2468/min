package dev.min.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** One vertically scrollable, bottom-grown category of search results. */
public final class ResultPageView extends FrameLayout {
    public interface Listener {
        void onResultTap(SearchResult result);
        void onAppLongPress(AppEntry app);
    }

    private final ScrollView scroll;
    private final LinearLayout stack;
    private final Listener listener;

    public ResultPageView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        stack = new LinearLayout(context);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.BOTTOM);
        scroll.addView(stack, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setResults(List<SearchResult> results) {
        stack.removeAllViews();
        for (int i = results.size() - 1; i >= 0; i--) {
            SearchResult result = results.get(i);
            PressableFrameLayout row = new PressableFrameLayout(getContext());
            TextView label = new TextView(getContext());
            label.setText(result.title);
            label.setTextColor(UiTokens.COLOR_TEXT);
            label.setTextSize(UiTokens.TEXT_RESULT_SP);
            label.setTypeface(UiTokens.typeface());
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            row.addView(label, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            row.setOnClickListener(view -> listener.onResultTap(result));
            if (result.kind == SearchResult.Kind.APP) {
                row.setOnLongClickListener(view -> {
                    listener.onAppLongPress(result.app);
                    return true;
                });
            }
            stack.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    UiTokens.dp(getContext(), UiTokens.RESULT_ROW_MIN_DP)));
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
