package dev.bhavyam.min.ui.studio

import dev.bhavyam.min.theme.Tokens

/** Undo / redo over committed token states (Token Studio keeps the last 300). */
class StudioHistory(initial: Tokens) {

    private val states = ArrayList<Tokens>().apply { add(initial) }
    private var index = 0

    val canUndo get() = index > 0
    val canRedo get() = index < states.lastIndex

    /** Record [tokens] as the newest state; a no-op if nothing changed. */
    fun commit(tokens: Tokens): Boolean {
        if (tokens == states[index]) return false
        while (states.lastIndex > index) states.removeAt(states.lastIndex)
        states += tokens
        if (states.size > LIMIT) states.removeAt(0)
        index = states.lastIndex
        return true
    }

    fun undo(): Tokens? = if (canUndo) states[--index] else null

    fun redo(): Tokens? = if (canRedo) states[++index] else null

    private companion object {
        const val LIMIT = 300
    }
}
