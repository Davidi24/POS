package com.saporini.mobile_desktop.core.scan

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Clock

/**
 * A barcode scanner is a keyboard that types very fast and presses Enter (user, 2026-11-27: "you just connect it"). Keys no text
 * field took (desktop: the window's last look at a key; Android: the activity's) come here; a quick burst of at least 4
 * characters ended by Enter is a scan. In a search box the box takes the keys, so the code just types in.
 */
object ScanCatcher {
    // A scanner types a character every few milliseconds (Bluetooth ones a little slower); people far slower.
    private const val GAP_MS = 60L
    private const val MIN_LENGTH = 4

    private val buffer = StringBuilder()
    private var last = 0L
    private val found = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Each barcode scanned outside a text field. */
    val scans: SharedFlow<String> = found

    private fun now() = Clock.System.now().toEpochMilliseconds()

    /** A character typed outside any text field. */
    fun char(c: Char) {
        if (c.code < 32 || c.code == 127) return
        val at = now()
        if (at - last > GAP_MS || buffer.length >= 80) buffer.clear()
        buffer.append(c)
        last = at
    }

    /** Enter outside any text field: answers true when it ended a scan (and takes the key). */
    fun enter(): Boolean {
        val code = buffer.toString().trim()
        val quick = now() - last <= GAP_MS * 2
        buffer.clear()
        if (!quick || code.length < MIN_LENGTH) return false
        found.tryEmit(code)
        return true
    }
}
