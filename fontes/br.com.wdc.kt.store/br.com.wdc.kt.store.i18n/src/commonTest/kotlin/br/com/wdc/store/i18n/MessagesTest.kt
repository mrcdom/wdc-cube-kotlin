package br.com.wdc.store.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesTest {

    @Test
    fun aLanguageTag_findsItsLanguage_orTheDefault() {
        assertEquals(Language.PtBr, Language.of("pt-BR"))
        assertEquals(Language.PtBr, Language.of("pt_BR"))
        assertEquals(Language.PtBr, Language.of("pt"))
        assertEquals(Language.EnUs, Language.of("en-GB"))
        assertEquals(Language.EnUs, Language.of("fr-FR"))
        assertEquals(Language.EnUs, Language.of(null))
    }

    @Test
    fun placeholders_areFilled_andAnUnknownKeyIsItself() {
        val messages = Messages(Language.EnUs)
        assertEquals("01/02/2003", messages.text("format.date", "month" to "01", "day" to "02", "year" to 2003))
        assertEquals("{month}/02/2003", messages.text("format.date", "day" to "02", "year" to 2003))
        assertEquals("no.such.key", messages.text("no.such.key"))
    }

    @Test
    fun dates_followTheLanguage() {
        assertEquals("03/07/2026", Messages(Language.EnUs).date(2026, 3, 7))
        assertEquals("07/03/2026", Messages(Language.PtBr).date(2026, 3, 7))
    }
}
