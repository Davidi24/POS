package com.saporini.mobile_desktop.core.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * A page with changes not saved asks before the person leaves it (user, 2026-11-23: "everything I do here needs Save changes,
 * and if I want to go somewhere else it asks save or discard"). The page holds a [Guard] while it has changes; the places
 * that lead away (the Admin Hub's bar and back, the page's own tabs) go through [attempt], which asks first.
 */
object LeaveGuard {
    /** What to do with the changes: [save] saves, then calls `leave` once it went well; [discard] drops them. */
    class Guard(val message: String, val save: (leave: () -> Unit) -> Unit, val discard: () -> Unit)

    var active: Guard? by mutableStateOf(null)
        internal set

    /** The move away that waits for an answer. */
    var waiting: (() -> Unit)? by mutableStateOf(null)
        private set

    fun attempt(leave: () -> Unit) {
        if (active == null) leave() else waiting = leave
    }

    internal fun answered() { waiting = null }
}

/** Holds the guard while [unsaved]; let go when the changes are gone or the page closes. */
@Composable
fun GuardUnsaved(unsaved: Boolean, message: String, save: (leave: () -> Unit) -> Unit, discard: () -> Unit) {
    val saveNow by rememberUpdatedState(save)
    val discardNow by rememberUpdatedState(discard)
    DisposableEffect(unsaved, message) {
        val guard = if (unsaved) LeaveGuard.Guard(message, { leave -> saveNow(leave) }, { discardNow() }) else null
        LeaveGuard.active = guard
        onDispose { if (LeaveGuard.active === guard) LeaveGuard.active = null }
    }
}

/** The question, shown while a move away waits: Save (then go), Discard (then go), or stay. */
@Composable
fun LeaveGuardDialog(saving: Boolean = false) {
    val leave = LeaveGuard.waiting ?: return
    val guard = LeaveGuard.active
    if (guard == null) {
        LeaveGuard.answered()
        leave()
        return
    }
    UnsavedLeaveDialog(
        onDiscard = { guard.discard(); LeaveGuard.answered(); leave() },
        onSave = { LeaveGuard.answered(); guard.save(leave) },
        onStay = { LeaveGuard.answered() },
        saving = saving,
        message = guard.message
    )
}
