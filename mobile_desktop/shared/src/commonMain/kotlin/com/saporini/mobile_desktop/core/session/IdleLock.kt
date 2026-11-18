package com.saporini.mobile_desktop.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.time.Clock

/**
 * How long a connected till may sit untouched before it locks (back to the PIN screen; user, 2026-11-18): 3 minutes while
 * someone is in the middle of something important (taking an order, a payment, seating guests, making or changing a
 * booking), 30 seconds anywhere else, and never on the kitchen display. MOCK: fixed here; they become Admin Hub settings.
 */
enum class IdleLevel(val seconds: Int) { NORMAL(30), IMPORTANT(180), NEVER(-1) }

/** The last touch, click or key press, and which screens are open that ask for more time. Only used on connected tills. */
object IdleLock {
    private val lastInput = MutableStateFlow(Clock.System.now().toEpochMilliseconds())
    private val holds = MutableStateFlow<Map<Long, IdleLevel>>(emptyMap())
    private var nextHold = 0L

    /** Someone touched the screen, clicked or typed. */
    fun touch() {
        lastInput.value = Clock.System.now().toEpochMilliseconds()
    }

    /** Seconds since the last touch. */
    fun idleSeconds(): Long = (Clock.System.now().toEpochMilliseconds() - lastInput.value) / 1000

    /** The strongest wish of the screens open now: never lock, 3 minutes, or 30 seconds. */
    val level: IdleLevel
        get() = holds.value.values.let { open ->
            when {
                IdleLevel.NEVER in open -> IdleLevel.NEVER
                IdleLevel.IMPORTANT in open -> IdleLevel.IMPORTANT
                else -> IdleLevel.NORMAL
            }
        }

    internal fun hold(level: IdleLevel): () -> Unit {
        val id = nextHold++
        holds.value = holds.value + (id to level)
        return { holds.value = holds.value - id }
    }
}

/** While this is on screen the till waits [level]'s time before locking ([IdleLevel.NEVER] for the kitchen display). */
@Composable
fun IdleTime(level: IdleLevel) {
    DisposableEffect(level) {
        val release = IdleLock.hold(level)
        onDispose { release() }
    }
}

/** Calls [onInput] for every mouse, touch and key event the platform sees in the app's windows, dialogs too (desktop). */
expect fun watchPlatformInput(onInput: () -> Unit)

/** For a window of its own (a `Dialog` on Android): a touch inside it keeps a connected till awake. */
fun androidx.compose.ui.Modifier.keepsTillAwake(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.ui.Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                IdleLock.touch()
            }
        }
    })
