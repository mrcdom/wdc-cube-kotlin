package br.com.wdc.store.nativeui.web.util

import br.com.wdc.store.i18n.Language
import br.com.wdc.store.i18n.Messages
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDateTest {

    private val enUs = Messages(Language.EnUs)
    private val ptBr = Messages(Language.PtBr)

    @Test
    fun usesTheRealCalendar_inUtc() {
        assertEquals("01/01/1970", formatDate(0, enUs))
        // a leap day, and the day after February in a common year: 365-day years and 30-day months got both wrong
        assertEquals("02/29/2024", formatDate(1709208000000, enUs))
        assertEquals("03/01/2026", formatDate(1772323200000, enUs))
        // the end of the year, where the approximation got even month and year wrong
        assertEquals("12/31/2026", formatDate(1798759800000, enUs))
    }

    @Test
    fun followsTheLanguage() {
        assertEquals("01/01/1970", formatDate(0, ptBr))
        assertEquals("29/02/2024", formatDate(1709208000000, ptBr))
        assertEquals("01/03/2026", formatDate(1772323200000, ptBr))
        assertEquals("31/12/2026", formatDate(1798759800000, ptBr))
    }
}
