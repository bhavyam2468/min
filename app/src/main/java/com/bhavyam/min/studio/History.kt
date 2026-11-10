package com.bhavyam.min.studio

import com.bhavyam.min.theme.Tokens

/** Undo/redo over immutable tokens: one entry per finished gesture or button press. */
class History(initial: Tokens) {
    private val states = arrayListOf(initial)
    private var index = 0

    val canUndo get() = index > 0
    val canRedo get() = index < states.lastIndex

    /** Records [t] as the newest state, dropping anything that had been undone. */
    fun commit(t: Tokens) {
        if (t == states[index]) return
        while (states.lastIndex > index) states.removeAt(states.lastIndex)
        states += t
        if (states.size > LIMIT) states.removeAt(0)
        index = states.lastIndex
    }

    fun undo(): Tokens {
        if (canUndo) index--
        return states[index]
    }

    fun redo(): Tokens {
        if (canRedo) index++
        return states[index]
    }

    private companion object {
        const val LIMIT = 300
    }
}
