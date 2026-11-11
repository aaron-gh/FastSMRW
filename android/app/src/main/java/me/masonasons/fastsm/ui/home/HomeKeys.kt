package me.masonasons.fastsm.ui.home

import android.view.KeyEvent

/**
 * Hardware-keyboard shortcuts for the home screen. The home screen registers
 * [handler] while it is showing (and clears it when an overlay screen with text
 * fields is up); MainActivity offers every key-down to it before the view
 * hierarchy, so the shortcuts work whether a post, a tab, a toolbar button or
 * nothing has focus. Dialogs are their own windows and never reach it.
 */
object HomeKeys {
    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null
}

/** The modifier keys held with a key press, for matching shortcuts. */
internal data class Mods(val ctrl: Boolean, val shift: Boolean, val alt: Boolean) {
    val none get() = !ctrl && !shift && !alt
    val ctrlOnly get() = ctrl && !shift && !alt
    val ctrlShift get() = ctrl && shift && !alt
    val shiftOnly get() = shift && !ctrl && !alt

    companion object {
        fun of(e: KeyEvent) = Mods(
            ctrl = e.isCtrlPressed || e.isMetaPressed,
            shift = e.isShiftPressed,
            alt = e.isAltPressed,
        )
    }
}
