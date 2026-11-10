package com.bhavyam.min.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Insets
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.bhavyam.min.App
import com.bhavyam.min.apps.AppEntry
import com.bhavyam.min.motion.Spring
import com.bhavyam.min.settings.SettingsRegistry
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.theme.Role
import com.bhavyam.min.ui.Icon
import com.bhavyam.min.ui.RoleButton
import com.bhavyam.min.ui.ThemedCard
import com.bhavyam.min.ui.dp
import com.bhavyam.min.ui.dpi
import com.bhavyam.min.ui.safe

/**
 * The long-press menu: a small raised card that springs out of the app it belongs to, just
 * above it (below if there's no room), leaving everything else where it is. Two options:
 * app info, and add/remove from favourites depending on the current state.
 */
class ContextPopup(context: Context, private val onInfo: (AppEntry) -> Unit) : FrameLayout(context) {

    private val app = App.of(context)
    val card = ThemedCard(context, raised = true)
    private val info = RoleButton(context, Role.MENU, Globals.TEXT_BUTTON, Icon.INFO)
    private val favorite = RoleButton(context, Role.MENU, Globals.TEXT_BUTTON, Icon.STAR)

    /** The menu's buttons, for the studio to restyle in place. */
    val options = listOf(info, favorite)
    private val spring = Spring(0.001f) { p ->
        card.alpha = p.coerceIn(0f, 1f)
        val s = POP_FROM + (1f - POP_FROM) * p
        card.scaleX = s
        card.scaleY = s
    }
    private var entry: AppEntry? = null
    private var safe = Insets.NONE
    private val anchorAt = IntArray(2)
    private val selfAt = IntArray(2)

    val isShowing: Boolean get() = entry != null

    init {
        visibility = GONE
        card.orientation = LinearLayout.HORIZONTAL
        val pad = dpi(4f)
        card.setPadding(pad, pad, pad, pad)
        info.text = "App info"
        card.addView(info)
        card.addView(favorite)
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        info.setOnClickListener { entry?.let(onInfo); dismiss() }
        favorite.setOnClickListener {
            entry?.let { e -> if (e in app.favorites) app.favorites.remove(e) else app.favorites.add(e) }
            dismiss()
        }
        spring.onRest = { if (spring.target == 0f) visibility = GONE }
    }

    fun show(target: AppEntry, anchor: View) {
        entry = target
        val fav = target in app.favorites
        favorite.text = if (fav) "Remove from favorites" else "Set as favorite"
        favorite.icon = if (fav) Icon.STAR_OFF else Icon.STAR
        visibility = VISIBLE
        // measured against the parent: until its first show this layer was never laid out
        val root = parent as View
        card.measure(
            MeasureSpec.makeMeasureSpec(root.width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(root.height, MeasureSpec.AT_MOST),
        )
        val w = card.measuredWidth
        val h = card.measuredHeight
        anchor.getLocationInWindow(anchorAt)
        getLocationInWindow(selfAt)
        val ax = anchorAt[0] - selfAt[0] + anchor.width * anchor.scaleX / 2f
        val top = anchorAt[1] - selfAt[1]
        val gap = dp(Globals.POPUP_GAP)
        val margin = dp(Globals.EDGE) / 2f
        val x = (ax - w / 2f).coerceIn(safe.left + margin, (root.width - safe.right - margin - w).coerceAtLeast(safe.left + margin))
        val above = top - gap - h >= safe.top + margin
        val y = if (above) top - gap - h else top + anchor.height + gap
        card.translationX = x
        card.translationY = y
        card.pivotX = (ax - x).coerceIn(0f, w.toFloat())
        card.pivotY = if (above) h.toFloat() else 0f
        spring.snapTo(0f)
        spring.animateTo(1f, app.motion[SettingsRegistry.SPRING_POPUP])
    }

    fun dismiss() {
        if (entry == null) return
        entry = null
        spring.animateTo(0f, app.motion[SettingsRegistry.SPRING_POPUP])
    }

    /** Gone instantly, e.g. when the launcher goes to the background. */
    fun hide() {
        entry = null
        spring.snapTo(0f)
        visibility = GONE
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safe = insets.safe()
        return insets
    }

    /** A touch outside the card only closes the menu; it never reaches what's underneath. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) dismiss()
        return true
    }

    private companion object {
        const val POP_FROM = 0.82f
    }
}
