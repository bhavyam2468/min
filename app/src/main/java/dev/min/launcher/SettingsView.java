package dev.min.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Animated overlay content for the data-driven settings catalog. */
public final class SettingsView extends FrameLayout {
    public interface Listener {
        void onCloseRequested();
        void onSettingChanged(SettingDefinition definition);
        void onSettingAction(SettingDefinition definition);
    }

    private final SettingsStore store;
    private final FallbackSearchProvider fallbackProvider;
    private final Listener listener;
    private final LinearLayout content;
    private final LinearLayout results;
    private final EditText searchInput;
    private final Set<String> expandedCategories = new HashSet<>();
    private int insetTop;
    private int insetBottom;
    private int insetLeft;
    private int insetRight;

    public SettingsView(Context context, SettingsStore store, FallbackSearchProvider fallbackProvider,
            Listener listener) {
        super(context);
        this.store = store;
        this.fallbackProvider = fallbackProvider;
        this.listener = listener;
        setBackgroundColor(UiTokens.COLOR_BLACK);
        setVisibility(GONE);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        PressableFrameLayout back = new PressableFrameLayout(context);
        TextView arrow = text(context, "←", UiTokens.TEXT_TITLE_SP, UiTokens.COLOR_TEXT);
        arrow.setGravity(Gravity.CENTER);
        back.addView(arrow, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        back.setOnClickListener(view -> listener.onCloseRequested());
        header.addView(back, new LinearLayout.LayoutParams(UiTokens.dp(context, UiTokens.SETTINGS_BACK_WIDTH_DP), UiTokens.dp(context, UiTokens.SETTINGS_HEADER_HEIGHT_DP)));
        TextView title = text(context, "settings", UiTokens.TEXT_TITLE_SP, UiTokens.COLOR_TEXT);
        title.setTypeface(UiTokens.typeface(), Typeface.NORMAL);
        header.addView(title, new LinearLayout.LayoutParams(0, UiTokens.dp(context, UiTokens.SETTINGS_HEADER_HEIGHT_DP), 1f));
        content.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, UiTokens.dp(context, UiTokens.SETTINGS_HEADER_HEIGHT_DP)));

        searchInput = new EditText(context);
        searchInput.setSingleLine(true);
        searchInput.setHint("search settings");
        searchInput.setHintTextColor(UiTokens.COLOR_MUTED);
        searchInput.setTextColor(UiTokens.COLOR_TEXT);
        searchInput.setTextSize(UiTokens.TEXT_ITEM_TITLE_SP);
        searchInput.setTypeface(UiTokens.typeface());
        searchInput.setIncludeFontPadding(false);
        searchInput.setBackgroundColor(UiTokens.COLOR_TRANSPARENT);
        searchInput.setPadding(UiTokens.dp(context, UiTokens.SETTINGS_INPUT_HORIZONTAL_PAD_DP), 0, UiTokens.dp(context, UiTokens.SETTINGS_INPUT_HORIZONTAL_PAD_DP), 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        content.addView(searchInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, UiTokens.dp(context, UiTokens.SETTINGS_SEARCH_HEIGHT_DP)));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, UiTokens.dp(context, UiTokens.SETTINGS_TOP_GAP_DP), 0,
                UiTokens.dp(context, UiTokens.SETTINGS_TOP_GAP_DP));
        scroll.addView(results, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        render();
    }

    public void setInsets(int top, int bottom, int left, int right) {
        insetTop = top;
        insetBottom = bottom;
        insetLeft = left;
        insetRight = right;
        updatePadding();
    }

    public void refresh() {
        render();
    }

    private void updatePadding() {
        int side = UiTokens.dp(getContext(), UiTokens.SCREEN_SIDE_DP);
        content.setPadding(insetLeft + side, insetTop, insetRight + side, insetBottom);
    }

    private void render() {
        if (results == null) {
            return;
        }
        results.removeAllViews();
        List<SettingsCategory> categories = SettingsRegistry.create(fallbackProvider);
        String query = FuzzyMatcher.normalize(searchInput.getText().toString());
        if (query.isEmpty()) {
            for (SettingsCategory category : categories) {
                addCategory(category);
            }
        } else {
            int count = 0;
            for (SettingsCategory category : categories) {
                for (SettingDefinition setting : category.items) {
                    if (FuzzyMatcher.match(query, setting.searchText()) != null) {
                        addSettingCard(setting, false);
                        count++;
                    }
                }
            }
            if (count == 0) {
                TextView empty = text(getContext(), "no settings found", UiTokens.TEXT_ITEM_TITLE_SP, UiTokens.COLOR_MUTED);
                empty.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        UiTokens.dp(getContext(), UiTokens.SETTINGS_EMPTY_HEIGHT_DP));
                results.addView(empty, params);
            }
        }
    }

    private void addCategory(SettingsCategory category) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UiDrawables.rounded(getContext(), UiTokens.COLOR_SURFACE, UiTokens.CARD_RADIUS_DP,
                UiTokens.COLOR_BORDER));
        PressableFrameLayout header = new PressableFrameLayout(getContext());
        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getContext(), category.title, UiTokens.TEXT_CARD_TITLE_SP, UiTokens.COLOR_TEXT);
        TextView description = text(getContext(), category.description, UiTokens.TEXT_ITEM_DESCRIPTION_SP, UiTokens.COLOR_MUTED);
        copy.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        copy.addView(description, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL);
        copyParams.leftMargin = UiTokens.dp(getContext(), UiTokens.CARD_PADDING_DP);
        copyParams.rightMargin = UiTokens.dp(getContext(), UiTokens.SETTINGS_CATEGORY_COPY_RIGHT_DP);
        header.addView(copy, copyParams);
        TextView chevron = text(getContext(), expandedCategories.contains(category.id) ? "⌃" : "⌄",
                UiTokens.TEXT_CARD_TITLE_SP, UiTokens.COLOR_MUTED);
        chevron.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams chevronParams = new FrameLayout.LayoutParams(UiTokens.dp(getContext(), UiTokens.SETTINGS_CHEVRON_WIDTH_DP),
                LayoutParams.MATCH_PARENT, Gravity.END);
        header.addView(chevron, chevronParams);
        header.setOnClickListener(view -> {
            if (expandedCategories.contains(category.id)) {
                expandedCategories.remove(category.id);
            } else {
                expandedCategories.add(category.id);
            }
            render();
        });
        card.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, UiTokens.dp(getContext(), UiTokens.SETTINGS_CATEGORY_HEIGHT_DP)));
        if (expandedCategories.contains(category.id)) {
            for (SettingDefinition definition : category.items) {
                addSettingCard(card, definition, true);
            }
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = UiTokens.dp(getContext(), UiTokens.CARD_GAP_DP);
        results.addView(card, params);
    }

    private void addSettingCard(SettingDefinition definition, boolean indented) {
        addSettingCard(results, definition, indented);
    }

    private void addSettingCard(LinearLayout destination, SettingDefinition definition, boolean indented) {
        PressableFrameLayout card = new PressableFrameLayout(getContext());
        card.setBackground(UiDrawables.rounded(getContext(), indented ? UiTokens.COLOR_POPUP : UiTokens.COLOR_SURFACE,
                UiTokens.SMALL_RADIUS_DP, UiTokens.COLOR_BORDER));
        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getContext(), definition.title, UiTokens.TEXT_ITEM_TITLE_SP, UiTokens.COLOR_TEXT);
        TextView description = text(getContext(), definition.description, UiTokens.TEXT_ITEM_DESCRIPTION_SP, UiTokens.COLOR_MUTED);
        copy.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        copy.addView(description, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL);
        copyParams.leftMargin = UiTokens.dp(getContext(), UiTokens.CARD_PADDING_DP);
        copyParams.rightMargin = UiTokens.dp(getContext(), UiTokens.SETTINGS_COPY_RIGHT_DP);
        card.addView(copy, copyParams);
        TextView affordance = text(getContext(), affordance(definition), UiTokens.TEXT_CATEGORY_SP,
                definition.type == SettingDefinition.Type.TOGGLE && store.getToggle(definition.key, definition.defaultToggle)
                        ? UiTokens.COLOR_ON : UiTokens.COLOR_OFF);
        affordance.setGravity(Gravity.CENTER);
        affordance.setBackground(UiDrawables.rounded(getContext(), UiTokens.COLOR_TRANSPARENT, UiTokens.SMALL_RADIUS_DP,
                UiTokens.COLOR_BORDER));
        FrameLayout.LayoutParams affordanceParams = new FrameLayout.LayoutParams(UiTokens.dp(getContext(), UiTokens.SETTINGS_PILL_WIDTH_DP),
                UiTokens.dp(getContext(), UiTokens.SETTINGS_PILL_HEIGHT_DP), Gravity.END | Gravity.CENTER_VERTICAL);
        affordanceParams.rightMargin = UiTokens.dp(getContext(), UiTokens.SETTINGS_PILL_RIGHT_DP);
        card.addView(affordance, affordanceParams);
        card.setOnClickListener(view -> activate(definition));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                UiTokens.dp(getContext(), UiTokens.SETTINGS_ROW_HEIGHT_DP));
        params.leftMargin = indented ? UiTokens.dp(getContext(), UiTokens.SETTINGS_INDENT_DP) : 0;
        params.rightMargin = indented ? UiTokens.dp(getContext(), UiTokens.SETTINGS_INDENT_DP) : 0;
        params.bottomMargin = UiTokens.dp(getContext(), UiTokens.CARD_GAP_DP);
        destination.addView(card, params);
    }

    private String affordance(SettingDefinition definition) {
        if (definition.type == SettingDefinition.Type.TOGGLE) {
            return store.getToggle(definition.key, definition.defaultToggle) ? "ON" : "OFF";
        }
        if (definition.type == SettingDefinition.Type.CHOICE) {
            return store.getText(definition.key, definition.choices.get(0)).toUpperCase();
        }
        return "→";
    }

    private void activate(SettingDefinition definition) {
        if (definition.type == SettingDefinition.Type.TOGGLE) {
            store.setToggle(definition.key, !store.getToggle(definition.key, definition.defaultToggle));
            listener.onSettingChanged(definition);
            render();
            return;
        }
        if (definition.type == SettingDefinition.Type.CHOICE) {
            String current = store.getText(definition.key, definition.choices.get(0));
            int index = definition.choices.indexOf(current);
            String next = definition.choices.get((index + 1 + definition.choices.size()) % definition.choices.size());
            store.setText(definition.key, next);
            listener.onSettingChanged(definition);
            render();
            return;
        }
        listener.onSettingAction(definition);
    }

    private static TextView text(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(UiTokens.typeface());
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
}
