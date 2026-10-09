package com.saporini.mobile_desktop.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationKindTest {

    private fun notification(referenceType: String?, eventCode: String = "") =
        StaffNotification("n-1", eventCode, "Title", "Message", referenceType, null, null, read = false)

    @Test fun picksTheKindFromWhatTheNotificationIsAbout() {
        assertEquals(NotificationKind.RESERVATION, notification("RESERVATION", "RESERVATION_CREATED").kind)
        assertEquals(NotificationKind.ORDER, notification("ORDER").kind)
        assertEquals(NotificationKind.ORDER, notification("KDS").kind)
        assertEquals(NotificationKind.TABLE, notification("table").kind)
        assertEquals(NotificationKind.PAYMENT, notification("PAYMENT").kind)
    }

    @Test fun fallsBackToTheEventCodeThenToOther() {
        assertEquals(NotificationKind.RESERVATION, notification(null, "RESERVATION_NO_SHOW").kind)
        assertEquals(NotificationKind.OTHER, notification(null).kind)
        assertEquals(NotificationKind.OTHER, notification("INVENTORY").kind)
    }

    @Test fun queuedNotificationsCannotBeMarkedReadUntilDelivery() {
        val queued = notification("ORDER").copy(markReadAllowed = false)
        val delivered = notification("ORDER").copy(markReadAllowed = true)
        val alreadyRead = delivered.copy(read = true)

        assertEquals(false, queued.canMarkRead)
        assertEquals(true, delivered.canMarkRead)
        assertEquals(false, alreadyRead.canMarkRead)
    }
}
