package com.saporini.mobile_desktop.shifts

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.sales.salesPay
import com.saporini.mobile_desktop.pos.shifts.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayTest {
    @Test fun moneyTextIsReadAsCents() {
        assertEquals(1250, "12.5".toCentsOrNull()); assertEquals(1250, "12.50".toCentsOrNull()); assertEquals(1251, "12.505".toCentsOrNull())
        assertEquals(-50, "-0.50".toCentsOrNull()); assertNull("abc".toCentsOrNull())
    }
    @Test fun moneyAndHoursAreWrittenForPeople() {
        assertEquals("€1,234.50", centsText(123450, "EUR")); assertEquals("€0.05", centsText(5, "EUR"))
        assertEquals("12.05", centsToDecimal(1205).value); assertEquals("-0.50", centsToDecimal(-50).value)
        assertEquals("45m", hoursText(45)); assertEquals("8h", hoursText(480)); assertEquals("7h 30m", hoursText(450))
    }
    @Test fun myPayAddsTheDayTheWeekAndTheMonth() {
        fun line(id: String, date: String, minutes: Long, wages: String) = ShiftPayLine(id, date, "CLOSED", workedMinutes = minutes, hourlyRate = OrderDecimal("12.00"), wages = OrderDecimal(wages))
        val person = StaffPay("u", "Test", hourlyRate = OrderDecimal("12.00"),
            shifts = listOf(line("a", "2026-09-01", 480, "96.00"), line("b", "2026-09-28", 240, "48.00"), line("c", "2026-09-29", 300, "60.00")),
            tipsByDay = listOf(DayTips("2026-09-29", OrderDecimal("7.50")), DayTips("2026-09-01", OrderDecimal("2.00"))))
        val pay = person.salesPay("EUR", LocalDate(2026, 9, 29), null)
        assertEquals(PayBitCheck(300, 6000, 750), pay.day.check()); assertEquals(PayBitCheck(540, 10800, 750), pay.week.check())
        assertEquals(PayBitCheck(1020, 20400, 950), pay.month.check())
        assertEquals(PayBitCheck(240, 4800, 0), person.salesPay("EUR", LocalDate(2026, 9, 29), "b").day.check())
    }
    private data class PayBitCheck(val minutes: Long, val wages: Long, val tips: Long)
    private fun com.saporini.mobile_desktop.pos.sales.PayBit.check() = PayBitCheck(minutes, wages, tips)
}
