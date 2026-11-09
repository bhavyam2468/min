package dev.min.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;

/** Global visual and motion tokens. Keep presentation constants here. */
public final class UiTokens {
    private UiTokens() { }

    public static final int COLOR_BLACK = Color.BLACK;
    public static final int COLOR_TEXT = Color.WHITE;
    public static final int COLOR_MUTED = Color.rgb(151, 151, 151);
    public static final int COLOR_SURFACE = Color.rgb(22, 22, 22);
    public static final int COLOR_POPUP = Color.rgb(32, 32, 32);
    public static final int COLOR_BORDER = Color.rgb(73, 73, 73);
    public static final int COLOR_DIM = 0x80000000;
    public static final int COLOR_TRANSPARENT = Color.TRANSPARENT;
    public static final int COLOR_ON = Color.rgb(112, 206, 132);
    public static final int COLOR_OFF = Color.rgb(135, 135, 135);

    public static final float TEXT_CLOCK_SP = 13f;
    public static final float TEXT_FAVORITE_SP = 21f;
    public static final float TEXT_SEARCH_SP = 27f;
    public static final float TEXT_RESULT_SP = 22f;
    public static final float TEXT_CATEGORY_SP = 10f;
    public static final float TEXT_TITLE_SP = 22f;
    public static final float TEXT_CARD_TITLE_SP = 16f;
    public static final float TEXT_ITEM_TITLE_SP = 15f;
    public static final float TEXT_ITEM_DESCRIPTION_SP = 12f;
    public static final float TEXT_POPUP_TITLE_SP = 12f;

    public static final int HOME_TOP_PERCENT = 20;
    public static final int FAVORITE_LINE_GAP_DP = 14;
    public static final int SCREEN_SIDE_DP = 24;
    public static final int WIDE_SCREEN_SIDE_DP = 48;
    public static final int CLOCK_TOP_DP = 12;
    public static final int CLOCK_GAP_DP = 14;
    public static final int RESULT_ROW_MIN_DP = 48;
    public static final int INPUT_HEIGHT_DP = 64;
    public static final int CATEGORY_HEIGHT_DP = 25;
    public static final int CARD_RADIUS_DP = 16;
    public static final int SMALL_RADIUS_DP = 9;
    public static final int CARD_BORDER_DP = 1;
    public static final int CARD_GAP_DP = 10;
    public static final int CARD_PADDING_DP = 17;
    public static final int POPUP_WIDTH_DP = 290;
    public static final int POPUP_ROW_HEIGHT_DP = 50;
    public static final int POPUP_TITLE_HEIGHT_DP = 43;
    public static final int POPUP_DIVIDER_DP = 1;
    public static final int SETTINGS_TOP_GAP_DP = 12;
    public static final int SETTINGS_HEADER_HEIGHT_DP = 52;
    public static final int SETTINGS_BACK_WIDTH_DP = 46;
    public static final int SETTINGS_SEARCH_HEIGHT_DP = 47;
    public static final int SETTINGS_CATEGORY_HEIGHT_DP = 73;
    public static final int SETTINGS_ROW_HEIGHT_DP = 70;
    public static final int SETTINGS_EMPTY_HEIGHT_DP = 68;
    public static final int SETTINGS_INDENT_DP = 12;
    public static final int SETTINGS_COPY_RIGHT_DP = 82;
    public static final int SETTINGS_CATEGORY_COPY_RIGHT_DP = 44;
    public static final int SETTINGS_PILL_WIDTH_DP = 58;
    public static final int SETTINGS_PILL_HEIGHT_DP = 27;
    public static final int SETTINGS_PILL_RIGHT_DP = 13;
    public static final int SETTINGS_INPUT_HORIZONTAL_PAD_DP = 4;
    public static final int SETTINGS_CHEVRON_WIDTH_DP = 42;
    public static final int WIDE_LAYOUT_BREAKPOINT_DP = 700;

    public static final float PRESS_SCALE = 0.982f;
    public static final float PRESS_ALPHA = 0.92f;
    public static final float SEARCH_HOME_DRIFT_DP = 28f;
    public static final float SEARCH_LAYER_RISE_DP = 54f;
    public static final float PAGER_NEIGHBOR_SCALE = 0.94f;
    public static final float PAGER_NEIGHBOR_ALPHA = 0.48f;
    public static final float PAGER_SCALE_FALLOFF = 0.06f;
    public static final float PAGER_ALPHA_FALLOFF = 0.52f;
    public static final float CATEGORY_LETTER_SPACING = 0.12f;
    public static final float INTRO_START_SCALE = 1.035f;
    public static final float POPUP_START_SCALE = 0.91f;
    public static final float BLUR_RADIUS_DP = 9f;
    public static final float SETTINGS_SLIDE_DP = 28f;
    public static final float SEARCH_DRAG_FRACTION = 0.34f;
    public static final float SEARCH_OPEN_THRESHOLD = 0.35f;
    public static final float SEARCH_FLING_VELOCITY = 620f;
    public static final float PAGER_OVERSCROLL_PAGES = 0.16f;
    public static final float PAGER_FLING_VELOCITY = 600f;
    public static final float SPRING_REST_DISTANCE = 0.0015f;
    public static final float SPRING_REST_VELOCITY = 0.012f;
    public static final float SPRING_MAX_FRAME_SECONDS = 0.034f;
    public static final float SPRING_MIN_FRAME_SECONDS = 0.001f;

    public static final float SPRING_STIFFNESS = 620f;
    public static final float SPRING_DAMPING = 48f;
    public static final float GENTLE_STIFFNESS = 460f;
    public static final float GENTLE_DAMPING = 40f;
    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static int sidePadding(Context context, int widthPx) {
        int compact = dp(context, SCREEN_SIDE_DP);
        int wide = dp(context, WIDE_SCREEN_SIDE_DP);
        return widthPx >= dp(context, WIDE_LAYOUT_BREAKPOINT_DP) ? wide : compact;
    }

    public static Typeface typeface() {
        return Typeface.create("sans", Typeface.NORMAL);
    }
}
