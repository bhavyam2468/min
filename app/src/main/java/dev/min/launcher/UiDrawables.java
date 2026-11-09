package dev.min.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

public final class UiDrawables {
    private UiDrawables() { }

    public static GradientDrawable rounded(Context context, int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UiTokens.dp(context, radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(UiTokens.dp(context, UiTokens.CARD_BORDER_DP), strokeColor);
        }
        return drawable;
    }
}
