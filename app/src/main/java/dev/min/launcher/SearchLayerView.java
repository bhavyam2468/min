package dev.min.launcher;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bottom-stacked search input, fixed category label, and springable result pages. */
public final class SearchLayerView extends ViewGroup {
    public interface Listener {
        void onRunResult(SearchResult result);
        void onAppLongPress(AppEntry app);
        void onQueryChanged(String query);
    }

    private final AppSearchProvider appProvider;
    private final ContactSearchProvider contactProvider;
    private final FallbackSearchProvider fallbackProvider;
    private final Listener listener;
    private final EditText input;
    private final TextView categoryLabel;
    private final CategoryPager pager;
    private final Map<String, List<SearchResult>> currentResults = new LinkedHashMap<>();
    private String manuallyPinnedCategory = "";
    private String focusedCategory = "apps";
    private int insetTop;
    private int insetBottom;
    private int insetLeft;
    private int insetRight;
    private int imeBottom;
    private boolean suppressTextChange;

    public SearchLayerView(Context context, AppSearchProvider appProvider,
            ContactSearchProvider contactProvider, FallbackSearchProvider fallbackProvider, Listener listener) {
        super(context);
        this.appProvider = appProvider;
        this.contactProvider = contactProvider;
        this.fallbackProvider = fallbackProvider;
        this.listener = listener;
        setBackgroundColor(UiTokens.COLOR_BLACK);
        setVisibility(GONE);
        setClipChildren(false);

        pager = new CategoryPager(context, this::onPagerSelected, new ResultPageView.Listener() {
            @Override public void onResultTap(SearchResult result) {
                listener.onRunResult(result);
            }

            @Override public void onAppLongPress(AppEntry app) {
                listener.onAppLongPress(app);
            }
        });
        addView(pager);

        categoryLabel = new TextView(context);
        categoryLabel.setTextColor(UiTokens.COLOR_MUTED);
        categoryLabel.setTextSize(UiTokens.TEXT_CATEGORY_SP);
        categoryLabel.setTypeface(UiTokens.typeface());
        categoryLabel.setGravity(Gravity.CENTER);
        categoryLabel.setIncludeFontPadding(false);
        categoryLabel.setLetterSpacing(UiTokens.CATEGORY_LETTER_SPACING);
        addView(categoryLabel);

        input = new EditText(context);
        input.setSingleLine(true);
        input.setHint("search");
        input.setHintTextColor(UiTokens.COLOR_MUTED);
        input.setTextColor(UiTokens.COLOR_TEXT);
        input.setTextSize(UiTokens.TEXT_SEARCH_SP);
        input.setTypeface(UiTokens.typeface());
        input.setGravity(Gravity.CENTER);
        input.setIncludeFontPadding(false);
        input.setBackgroundColor(UiTokens.COLOR_TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                runFocusedTopResult();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressTextChange) {
                    updateResults();
                    listener.onQueryChanged(s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        addView(input);
        updateResults();
    }

    public void setInsets(int top, int bottom, int left, int right, int imeInsetBottom) {
        insetTop = top;
        insetBottom = bottom;
        insetLeft = left;
        insetRight = right;
        imeBottom = imeInsetBottom;
        requestLayout();
    }

    public void refreshResults() {
        updateResults();
    }

    public void setQuery(String query) {
        suppressTextChange = true;
        input.setText(query == null ? "" : query);
        input.setSelection(input.length());
        suppressTextChange = false;
        updateResults();
        listener.onQueryChanged(input.getText().toString());
    }

    public String query() {
        return input.getText().toString();
    }

    public void clearQuery() {
        manuallyPinnedCategory = "";
        setQuery("");
    }

    public void focusInput() {
        input.requestFocus();
    }

    public void clearFocusFromInput() {
        input.clearFocus();
    }

    public SearchResult focusedTopResult() {
        List<SearchResult> results = currentResults.get(focusedCategory);
        return results == null || results.isEmpty() ? null : results.get(0);
    }

    public void runFocusedTopResult() {
        SearchResult result = focusedTopResult();
        if (result != null) {
            listener.onRunResult(result);
        }
    }

    public String focusedCategory() {
        return focusedCategory;
    }

    public String resultsForCommand() {
        StringBuilder answer = new StringBuilder();
        for (Map.Entry<String, List<SearchResult>> entry : currentResults.entrySet()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(entry.getKey()).append(": ");
            List<SearchResult> results = entry.getValue();
            if (results.isEmpty()) {
                answer.append("(none)");
            } else {
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) answer.append(", ");
                    answer.append(results.get(i).title);
                }
            }
        }
        return answer.toString();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int side = UiTokens.sidePadding(getContext(), width);
        int inputHeight = UiTokens.dp(getContext(), UiTokens.INPUT_HEIGHT_DP);
        int labelHeight = UiTokens.dp(getContext(), UiTokens.CATEGORY_HEIGHT_DP);
        int bottom = Math.max(insetBottom, imeBottom);
        int pagerHeight = Math.max(0, height - insetTop - bottom - inputHeight - labelHeight);
        input.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - insetLeft - insetRight - side * 2), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(inputHeight, MeasureSpec.EXACTLY));
        categoryLabel.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - insetLeft - insetRight), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(labelHeight, MeasureSpec.EXACTLY));
        pager.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(pagerHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int side = UiTokens.sidePadding(getContext(), width);
        int inputHeight = input.getMeasuredHeight();
        int labelHeight = categoryLabel.getMeasuredHeight();
        int lowerInset = Math.max(insetBottom, imeBottom);
        int inputTop = height - lowerInset - inputHeight;
        input.layout(insetLeft + side, inputTop, width - insetRight - side, inputTop + inputHeight);
        int labelTop = inputTop - labelHeight;
        categoryLabel.layout(insetLeft, labelTop, width - insetRight, inputTop);
        pager.layout(0, insetTop, width, Math.max(insetTop, labelTop));
    }

    private void updateResults() {
        String query = input == null ? "" : input.getText().toString();
        LinkedHashMap<String, List<SearchResult>> next = new LinkedHashMap<>();
        List<SearchResult> apps = appProvider.search(query);
        if (!apps.isEmpty()) {
            next.put("apps", apps);
        }
        List<SearchResult> contacts = contactProvider.search(query);
        if (!contacts.isEmpty()) {
            next.put("contacts", contacts);
        }
        List<SearchResult> fallback = fallbackProvider.search(query);
        if (!fallback.isEmpty()) {
            next.put("search", fallback);
        }
        currentResults.clear();
        currentResults.putAll(next);
        ArrayList<CategoryPager.PageData> pages = new ArrayList<>();
        for (Map.Entry<String, List<SearchResult>> entry : currentResults.entrySet()) {
            pages.add(new CategoryPager.PageData(entry.getKey(), entry.getValue()));
        }
        int selected = 0;
        if (!manuallyPinnedCategory.isEmpty()) {
            int pinned = indexOf(manuallyPinnedCategory, pages);
            if (pinned >= 0) {
                selected = pinned;
            } else {
                manuallyPinnedCategory = "";
            }
        }
        if (pages.isEmpty()) {
            focusedCategory = "apps";
        } else {
            focusedCategory = pages.get(selected).id;
        }
        categoryLabel.setText(focusedCategory.toUpperCase());
        pager.setPages(pages, selected);
    }

    private void onPagerSelected(int index, boolean fromUser) {
        String id = pager.pageIdAt(index);
        if (id.isEmpty()) {
            return;
        }
        focusedCategory = id;
        categoryLabel.setText(id.toUpperCase());
        if (fromUser) {
            manuallyPinnedCategory = id;
        }
    }

    private static int indexOf(String id, List<CategoryPager.PageData> pages) {
        for (int i = 0; i < pages.size(); i++) {
            if (id.equals(pages.get(i).id)) {
                return i;
            }
        }
        return -1;
    }
}
