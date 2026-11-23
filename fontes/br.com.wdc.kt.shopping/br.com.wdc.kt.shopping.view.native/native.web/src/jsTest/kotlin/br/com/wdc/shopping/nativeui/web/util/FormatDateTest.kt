package br.com.wdc.shopping.nativeui.web.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDateTest {

    @Test
    fun usesTheRealCalendar_inUtc() {
        assertEquals("01/01/1970", formatDate(0))
        // a leap day, and the day after February in a common year: 365-day years and 30-day months got both wrong
        assertEquals("02/29/2024", formatDate(1709208000000))
        assertEquals("03/01/2026", formatDate(1772323200000))
        // the end of the year, where the approximation got even month and year wrong
        assertEquals("12/31/2026", formatDate(1798759800000))
    }
}
