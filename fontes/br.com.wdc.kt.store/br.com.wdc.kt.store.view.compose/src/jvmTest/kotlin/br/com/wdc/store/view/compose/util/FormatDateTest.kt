package br.com.wdc.store.view.compose.util

import br.com.wdc.store.i18n.Language
import br.com.wdc.store.i18n.Messages
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDateTest {

    private val usEnglish = Messages(Language.EnUs)
    private val brazilianPortuguese = Messages(Language.PtBr)

    private fun formatDate(epochMs: Long): String = formatDate(epochMs = epochMs, messages = usEnglish)

    @Test
    fun usesTheRealCalendar_inUtc() {
        assertEquals("01/01/1970", formatDate(0))
        // a leap day, and the day after February in a common year: 365-day years and 30-day months got both wrong
        assertEquals("02/29/2024", formatDate(1709208000000))
        assertEquals("03/01/2026", formatDate(1772323200000))
        // the end of the year, where the approximation got even month and year wrong
        assertEquals("12/31/2026", formatDate(1798759800000))
    }

    @Test
    fun followsTheLanguage() {
        assertEquals("02/29/2024", formatDate(epochMs = 1709208000000, messages = usEnglish))
        assertEquals("29/02/2024", formatDate(epochMs = 1709208000000, messages = brazilianPortuguese))
    }
}
