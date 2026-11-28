package com.saporini.mobile_desktop.core.scan

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A scanner types fast and ends with Enter; a person types slowly (2026-11-27). */
class ScanCatcherTest {

    @Test
    fun aQuickBurstEndedByEnterIsAScan() {
        "5901234123457".forEach(ScanCatcher::char)
        assertTrue(ScanCatcher.enter())
        // Too short to be a barcode.
        "12".forEach(ScanCatcher::char)
        assertFalse(ScanCatcher.enter())
    }

    @Test
    fun slowTypingIsNotAScan() {
        "ABCDEF".forEach { c -> ScanCatcher.char(c); Thread.sleep(90) }
        assertFalse(ScanCatcher.enter())
        // Enter alone does nothing.
        assertFalse(ScanCatcher.enter())
    }
}
