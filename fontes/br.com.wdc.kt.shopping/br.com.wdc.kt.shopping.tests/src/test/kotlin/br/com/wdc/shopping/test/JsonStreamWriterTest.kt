package br.com.wdc.shopping.test

import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonStreamWriterTest {

    // ── Empty structures ──

    @Test
    fun emptyObject() {
        val w = JsonStreamWriter()
        w.beginObject().endObject()
        assertEquals("{}", w.result())
    }

    @Test
    fun emptyArray() {
        val w = JsonStreamWriter()
        w.beginArray().endArray()
        assertEquals("[]", w.result())
    }

    // ── Primitive values at root ──

    @Test
    fun stringValueAtRoot() {
        val w = JsonStreamWriter()
        w.value("hello")
        assertEquals("\"hello\"", w.result())
    }

    @Test
    fun booleanTrueAtRoot() {
        val w = JsonStreamWriter()
        w.value(true)
        assertEquals("true", w.result())
    }

    @Test
    fun booleanFalseAtRoot() {
        val w = JsonStreamWriter()
        w.value(false)
        assertEquals("false", w.result())
    }

    @Test
    fun nullValueAtRoot() {
        val w = JsonStreamWriter()
        w.nullValue()
        assertEquals("null", w.result())
    }

    @Test
    fun longValueAtRoot() {
        val w = JsonStreamWriter()
        w.value(42L)
        assertEquals("42", w.result())
    }

    @Test
    fun doubleValueAtRoot() {
        val w = JsonStreamWriter()
        w.value(3.14)
        assertEquals("3.14", w.result())
    }

    @Test
    fun numberValueAtRoot() {
        val w = JsonStreamWriter()
        w.value(99 as Number)
        assertEquals("99", w.result())
    }

    @Test
    fun nullNumberAtRoot() {
        val w = JsonStreamWriter()
        w.value(null as Number?)
        assertEquals("null", w.result())
    }

    @Test
    fun nullStringAtRoot() {
        val w = JsonStreamWriter()
        w.value(null as String?)
        assertEquals("null", w.result())
    }

    @Test
    fun nullByteArrayAtRoot() {
        val w = JsonStreamWriter()
        w.value(null as ByteArray?)
        assertEquals("null", w.result())
    }

    // ── Object with properties ──

    @Test
    fun objectWithSingleProperty() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("key").value("value")
            .endObject()
        assertEquals("""{"key":"value"}""", w.result())
    }

    @Test
    fun objectWithMultipleProperties() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("a").value(1L)
            .name("b").value("two")
            .name("c").value(true)
            .endObject()
        assertEquals("""{"a":1,"b":"two","c":true}""", w.result())
    }

    @Test
    fun objectWithNullProperties() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("x").nullValue()
            .name("y").value(null as String?)
            .endObject()
        assertEquals("""{"x":null,"y":null}""", w.result())
    }

    // ── name(id, name) ──

    @Test
    fun nameWithIdAndNonBlankName() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name(1, "field").value(10L)
            .endObject()
        assertEquals("""{"field":10}""", w.result())
    }

    @Test
    fun nameWithIdAndBlankNameUsesId() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name(42, "").value("val")
            .endObject()
        assertEquals("""{"42":"val"}""", w.result())
    }

    @Test
    fun nameWithIdAndWhitespaceOnlyUsesId() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name(7, "   ").value(true)
            .endObject()
        assertEquals("""{"7":true}""", w.result())
    }

    // ── Array with elements ──

    @Test
    fun arrayWithPrimitives() {
        val w = JsonStreamWriter()
        w.beginArray()
            .value(1L)
            .value(2L)
            .value(3L)
            .endArray()
        assertEquals("[1,2,3]", w.result())
    }

    @Test
    fun arrayWithMixedTypes() {
        val w = JsonStreamWriter()
        w.beginArray()
            .value("hello")
            .value(42L)
            .value(true)
            .nullValue()
            .value(3.14)
            .endArray()
        assertEquals("""["hello",42,true,null,3.14]""", w.result())
    }

    // ── Nested structures ──

    @Test
    fun nestedObjectInObject() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("outer").beginObject()
                .name("inner").value("deep")
            .endObject()
        .endObject()
        assertEquals("""{"outer":{"inner":"deep"}}""", w.result())
    }

    @Test
    fun arrayInObject() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("items").beginArray()
                .value(1L).value(2L).value(3L)
            .endArray()
        .endObject()
        assertEquals("""{"items":[1,2,3]}""", w.result())
    }

    @Test
    fun objectInArray() {
        val w = JsonStreamWriter()
        w.beginArray()
            .beginObject().name("id").value(1L).endObject()
            .beginObject().name("id").value(2L).endObject()
        .endArray()
        assertEquals("""[{"id":1},{"id":2}]""", w.result())
    }

    @Test
    fun deeplyNested() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("a").beginObject()
                .name("b").beginArray()
                    .beginObject()
                        .name("c").beginArray()
                            .value(true)
                        .endArray()
                    .endObject()
                .endArray()
            .endObject()
        .endObject()
        assertEquals("""{"a":{"b":[{"c":[true]}]}}""", w.result())
    }

    @Test
    fun nestedEmptyStructures() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("emptyObj").beginObject().endObject()
            .name("emptyArr").beginArray().endArray()
        .endObject()
        assertEquals("""{"emptyObj":{},"emptyArr":[]}""", w.result())
    }

    // ── String escaping ──

    @Test
    fun stringWithQuotes() {
        val w = JsonStreamWriter()
        w.value("say \"hello\"")
        assertEquals(""""say \"hello\""""", w.result())
    }

    @Test
    fun stringWithBackslash() {
        val w = JsonStreamWriter()
        w.value("path\\to\\file")
        assertEquals(""""path\\to\\file"""", w.result())
    }

    @Test
    fun stringWithNewlineAndTab() {
        val w = JsonStreamWriter()
        w.value("line1\nline2\ttab")
        assertEquals(""""line1\nline2\ttab"""", w.result())
    }

    @Test
    fun stringWithCarriageReturn() {
        val w = JsonStreamWriter()
        w.value("cr\r")
        assertEquals(""""cr\r"""", w.result())
    }

    @Test
    fun stringWithBackspace() {
        val w = JsonStreamWriter()
        w.value("bs\b")
        assertEquals(""""bs\b"""", w.result())
    }

    @Test
    fun stringWithFormFeed() {
        val w = JsonStreamWriter()
        w.value("ff\u000C")
        assertEquals(""""ff\f"""", w.result())
    }

    @Test
    fun stringWithControlCharacters() {
        val w = JsonStreamWriter()
        w.value("ctrl\u0001\u001F")
        assertEquals(""""ctrl\u0001\u001f"""", w.result())
    }

    @Test
    fun emptyString() {
        val w = JsonStreamWriter()
        w.value("")
        assertEquals("\"\"", w.result())
    }

    @Test
    fun stringWithUnicode() {
        val w = JsonStreamWriter()
        w.value("café ☕ 日本語")
        assertEquals("\"café ☕ 日本語\"", w.result())
    }

    // ── ByteArray / Base64 ──

    @Test
    fun byteArrayBase64() {
        val w = JsonStreamWriter()
        w.value("Hello".encodeToByteArray())
        assertEquals("\"SGVsbG8=\"", w.result())
    }

    @Test
    fun emptyByteArrayBase64() {
        val w = JsonStreamWriter()
        w.value(ByteArray(0))
        assertEquals("\"\"", w.result())
    }

    @Test
    fun singleByteBase64() {
        val w = JsonStreamWriter()
        w.value(byteArrayOf(0x41)) // 'A'
        assertEquals("\"QQ==\"", w.result())
    }

    @Test
    fun twoBytesBase64() {
        val w = JsonStreamWriter()
        w.value(byteArrayOf(0x41, 0x42)) // 'AB'
        assertEquals("\"QUI=\"", w.result())
    }

    @Test
    fun threeBytesBase64NoPadding() {
        val w = JsonStreamWriter()
        w.value(byteArrayOf(0x41, 0x42, 0x43)) // 'ABC'
        assertEquals("\"QUJD\"", w.result())
    }

    // ── Double edge cases ──

    @Test
    fun doubleZero() {
        val w = JsonStreamWriter()
        w.value(0.0)
        assertEquals("0.0", w.result())
    }

    @Test
    fun negativeDouble() {
        val w = JsonStreamWriter()
        w.value(-1.5)
        assertEquals("-1.5", w.result())
    }

    @Test
    fun longMaxValue() {
        val w = JsonStreamWriter()
        w.value(Long.MAX_VALUE)
        assertEquals("9223372036854775807", w.result())
    }

    @Test
    fun longMinValue() {
        val w = JsonStreamWriter()
        w.value(Long.MIN_VALUE)
        assertEquals("-9223372036854775808", w.result())
    }

    // ── Error: value without name in object ──

    @Test
    fun valueWithoutNameInObjectThrows() {
        val w = JsonStreamWriter()
        w.beginObject()
        assertThrows<IllegalStateException> {
            w.value("oops")
        }
    }

    // ── Scope stack growth ──

    @Test
    fun deepNestingExceedsInitialStackSize() {
        val w = JsonStreamWriter()
        val depth = 20 // > initial 16
        repeat(depth) { w.beginArray() }
        w.value(1L)
        repeat(depth) { w.endArray() }
        val result = w.result()
        assertTrue(result.startsWith("[".repeat(depth)))
        assertTrue(result.endsWith("]".repeat(depth)))
        assertTrue(result.contains("1"))
    }

    // ── Complex realistic structure ──

    @Test
    fun realisticJsonDocument() {
        val w = JsonStreamWriter()
        w.beginObject()
            .name("id").value(123L)
            .name("name").value("Test Product")
            .name("price").value(29.99)
            .name("active").value(true)
            .name("description").value(null as String?)
            .name("tags").beginArray()
                .value("electronics")
                .value("sale")
            .endArray()
            .name("metadata").beginObject()
                .name("created").value("2026-01-01")
                .name("views").value(1000L)
            .endObject()
        .endObject()

        val expected = """{"id":123,"name":"Test Product","price":29.99,"active":true,"description":null,"tags":["electronics","sale"],"metadata":{"created":"2026-01-01","views":1000}}"""
        assertEquals(expected, w.result())
    }
}
