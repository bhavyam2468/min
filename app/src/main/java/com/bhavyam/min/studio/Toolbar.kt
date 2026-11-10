package com.bhavyam.min.studio

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import com.bhavyam.min.theme.Globals
import com.bhavyam.min.ui.Icon
import com.bhavyam.min.ui.ThemedCard
import com.bhavyam.min.ui.ThemedIcon
import com.bhavyam.min.ui.dpi

/** Which part of the launcher the studio is showing for editing. */
enum class Scene(val label: String) { HOME("home"), SEARCH("search"), MENU("menu") }

/**
 * The studio's only chrome: a small raised card floating over the bottom of the real screen.
 * Top row: done, and which part of the launcher to edit. Bottom row: undo, redo, shuffle,
 * reset, copy the tokens. Everything else is edited by touching the launcher itself.
 */
class Toolbar(context: Context, private val actions: Actions) : ThemedCard(context, raised = true) {

    interface Actions {
        fun done()
        fun scene(scene: Scene)
        fun undo()
        fun redo()
        fun shuffle()
        fun reset()
        fun copy()
    }

    private val segment = Segment(context, Scene.entries.map { it.label }) { actions.scene(Scene.entries[it]) }
    private val undo = tool(Icon.UNDO, "undo") { actions.undo() }
    private val redo = tool(Icon.REDO, "redo") { actions.redo() }

    init {
        orientation = VERTICAL
        val pad = dpi(6f)
        setPadding(pad, pad, pad, pad)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(tool(Icon.BACK, "done") { actions.done() })
            addView(segment, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = pad })
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(undo)
            addView(redo)
            addView(tool(Icon.SHUFFLE, "shuffle") { actions.shuffle() })
            addView(tool(Icon.RESET, "reset") { actions.reset() })
            addView(tool(Icon.COPY, "copy tokens as JSON") { actions.copy() })
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun scene(scene: Scene) = segment.select(scene.ordinal, animate = true)

    fun history(canUndo: Boolean, canRedo: Boolean) {
        for ((v, on) in listOf(undo to canUndo, redo to canRedo)) {
            v.isEnabled = on
            v.alpha = if (on) 1f else DISABLED_ALPHA
        }
    }

    private fun tool(icon: Icon, label: String, onTap: () -> Unit) = ThemedIcon(context, icon).apply {
        val pad = dpi(Globals.EDGE / 2.5f)
        setPadding(pad, pad, pad, pad)
        contentDescription = label
        setOnClickListener {
            onTap()
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.3f
    }
}
