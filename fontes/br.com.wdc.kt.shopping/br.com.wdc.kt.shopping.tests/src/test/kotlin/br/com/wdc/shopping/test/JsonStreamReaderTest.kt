package br.com.wdc.shopping.test

import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.SerializationToken
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonStreamReaderTest {

    // ── Empty structures ──

    @Test
    fun emptyObject() {
        val r = JsonStreamReader("{}")
        r.beginObject()
        assertFalse(r.hasNext())
        r.endObject()
    }

    @Test
    fun emptyArray() {
        val r = JsonStreamReader("[]")
        r.beginArray()
        assertFalse(r.hasNext())
        r.endArray()
    }

    // ── Peek tokens ──

    @Test
    fun peekBeginObject() {
        val r = JsonStreamReader("{}")
        assertEquals(SerializationToken.BEGIN_OBJECT, r.peek())
    }

    @Test
    fun peekBeginArray() {
        val r = JsonStreamReader("[]")
        assertEquals(SerializationToken.BEGIN_ARRAY, r.peek())
    }

    @Test
    fun peekStringToken() {
        val r = JsonStreamReader("\"hello\"")
        assertEquals(SerializationToken.STRING, r.peek())
    }

    @Test
    fun peekNumberToken() {
        val r = JsonStreamReader("42")
        assertEquals(SerializationToken.NUMBER, r.peek())
    }

    @Test
    fun peekBooleanToken() {
        val r = JsonStreamReader("true")
        assertEquals(SerializationToken.BOOLEAN, r.peek())
    }

    @Test
    fun peekNullToken() {
        val r = JsonStreamReader("null")
        assertEquals(SerializationToken.NULL, r.peek())
    }

    @Test
    fun peekNameInObject() {
        val r = JsonStreamReader("""{"a":1}""")
        r.beginObject()
        assertEquals(SerializationToken.NAME, r.peek())
    }

    @Test
    fun peekEndObjectAfterAllNames() {
        val r = JsonStreamReader("""{"a":1}""")
        r.beginObject()
        r.nextName()
        r.nextInt()
        assertEquals(SerializationToken.END_OBJECT, r.peek())
    }

    @Test
    fun peekEndArrayAfterAllElements() {
        val r = JsonStreamReader("[1]")
        r.beginArray()
        r.nextInt()
        assertEquals(SerializationToken.END_ARRAY, r.peek())
    }

    // ── Primitive values at root ──

    @Test
    fun stringAtRoot() {
        val r = JsonStreamReader("\"hello world\"")
        assertEquals("hello world", r.nextString())
    }

    @Test
    fun integerAtRoot() {
        val r = JsonStreamReader("42")
        assertEquals(42, r.nextInt())
    }

    @Test
    fun longAtRoot() {
        val r = JsonStreamReader("9223372036854775807")
        assertEquals(Long.MAX_VALUE, r.nextLong())
    }

    @Test
    fun doubleAtRoot() {
        val r = JsonStreamReader("3.14")
        assertEquals(3.14, r.nextDouble(), 0.001)
    }

    @Test
    fun booleanTrueAtRoot() {
        val r = JsonStreamReader("true")
        assertTrue(r.nextBoolean())
    }

    @Test
    fun booleanFalseAtRoot() {
        val r = JsonStreamReader("false")
        assertFalse(r.nextBoolean())
    }

    @Test
    fun nullAtRoot() {
        val r = JsonStreamReader("null")
        assertNull(r.nextNull<Any>())
    }

    @Test
    fun negativeIntAtRoot() {
        val r = JsonStreamReader("-10")
        assertEquals(-10, r.nextInt())
    }

    @Test
    fun negativeDoubleAtRoot() {
        val r = JsonStreamReader("-3.14")
        assertEquals(-3.14, r.nextDouble(), 0.001)
    }

    @Test
    fun zeroAtRoot() {
        val r = JsonStreamReader("0")
        assertEquals(0, r.nextInt())
    }

    // ── nextNumber ──

    @Test
    fun nextNumberReturnsIntForSmallIntegers() {
        val r = JsonStreamReader("42")
        val n = r.nextNumber()
        assertNotNull(n)
        assertTrue(n is Int)
        assertEquals(42, n)
    }

    @Test
    fun nextNumberReturnsLongForLargeIntegers() {
        val r = JsonStreamReader("9223372036854775807")
        val n = r.nextNumber()
        assertNotNull(n)
        assertTrue(n is Long)
        assertEquals(Long.MAX_VALUE, n)
    }

    @Test
    fun nextNumberReturnsDoubleForDecimal() {
        val r = JsonStreamReader("3.14")
        val n = r.nextNumber()
        assertNotNull(n)
        assertTrue(n is Double)
        assertEquals(3.14, (n as Double), 0.001)
    }

    @Test
    fun nextNumberReturnsDoubleForExponent() {
        val r = JsonStreamReader("1e10")
        val n = r.nextNumber()
        assertNotNull(n)
        assertTrue(n is Double)
        assertEquals(1e10, (n as Double), 1.0)
    }

    @Test
    fun nextNumberReturnsNullForNull() {
        val r = JsonStreamReader("null")
        assertNull(r.nextNumber())
    }

    // ── nextString coercion from other types ──

    @Test
    fun nextStringFromNumber() {
        val r = JsonStreamReader("42")
        assertEquals("42", r.nextString())
    }

    @Test
    fun nextStringFromBoolean() {
        val r = JsonStreamReader("true")
        assertEquals("true", r.nextString())
    }

    @Test
    fun nextStringFromNull() {
        val r = JsonStreamReader("null")
        assertEquals("null", r.nextString())
    }

    // ── nextInt / nextLong from decimal numbers ──

    @Test
    fun nextIntFromDecimalWithZeroFraction() {
        val r = JsonStreamReader("5.0")
        assertEquals(5, r.nextInt())
    }

    @Test
    fun nextLongFromDecimalWithZeroFraction() {
        val r = JsonStreamReader("100.0")
        assertEquals(100L, r.nextLong())
    }

    @Test
    fun nextIntFromNonZeroFractionThrows() {
        val r = JsonStreamReader("5.5")
        assertThrows<NumberFormatException> { r.nextInt() }
    }

    @Test
    fun nextLongFromNonZeroFractionThrows() {
        val r = JsonStreamReader("5.5")
        assertThrows<NumberFormatException> { r.nextLong() }
    }

    // ── nextInt / nextLong from quoted strings ──

    @Test
    fun nextIntFromQuotedString() {
        val r = JsonStreamReader("\"99\"")
        assertEquals(99, r.nextInt())
    }

    @Test
    fun nextLongFromQuotedString() {
        val r = JsonStreamReader("\"12345678901\"")
        assertEquals(12345678901L, r.nextLong())
    }

    @Test
    fun nextDoubleFromQuotedString() {
        val r = JsonStreamReader("\"2.718\"")
        assertEquals(2.718, r.nextDouble(), 0.001)
    }

    // ── Object reading ──

    @Test
    fun objectWithSingleProperty() {
        val r = JsonStreamReader("""{"name":"Alice"}""")
        r.beginObject()
        assertTrue(r.hasNext())
        assertEquals("name", r.nextName())
        assertEquals("Alice", r.nextString())
        assertFalse(r.hasNext())
        r.endObject()
    }

    @Test
    fun objectWithMultipleProperties() {
        val r = JsonStreamReader("""{"a":1,"b":"two","c":true}""")
        r.beginObject()

        assertEquals("a", r.nextName())
        assertEquals(1, r.nextInt())

        assertEquals("b", r.nextName())
        assertEquals("two", r.nextString())

        assertEquals("c", r.nextName())
        assertTrue(r.nextBoolean())

        assertFalse(r.hasNext())
        r.endObject()
    }

    @Test
    fun objectWithNullValues() {
        val r = JsonStreamReader("""{"x":null,"y":null}""")
        r.beginObject()

        assertEquals("x", r.nextName())
        assertEquals(SerializationToken.NULL, r.peek())
        assertNull(r.nextNull<Any>())

        assertEquals("y", r.nextName())
        assertNull(r.nextNull<Any>())

        r.endObject()
    }

    // ── Array reading ──

    @Test
    fun arrayWithPrimitives() {
        val r = JsonStreamReader("[1,2,3]")
        r.beginArray()
        val values = mutableListOf<Int>()
        while (r.hasNext()) {
            values.add(r.nextInt())
        }
        r.endArray()
        assertEquals(listOf(1, 2, 3), values)
    }

    @Test
    fun arrayWithMixedTypes() {
        val r = JsonStreamReader("""["hello",42,true,null,3.14]""")
        r.beginArray()

        assertEquals("hello", r.nextString())
        assertEquals(42, r.nextInt())
        assertTrue(r.nextBoolean())
        assertNull(r.nextNull<Any>())
        assertEquals(3.14, r.nextDouble(), 0.001)

        assertFalse(r.hasNext())
        r.endArray()
    }

    @Test
    fun arrayOfStrings() {
        val r = JsonStreamReader("""["a","b","c"]""")
        r.beginArray()
        val values = mutableListOf<String>()
        while (r.hasNext()) {
            values.add(r.nextString())
        }
        r.endArray()
        assertEquals(listOf("a", "b", "c"), values)
    }

    // ── Nested structures ──

    @Test
    fun nestedObjectInObject() {
        val r = JsonStreamReader("""{"outer":{"inner":"deep"}}""")
        r.beginObject()
        assertEquals("outer", r.nextName())
        r.beginObject()
        assertEquals("inner", r.nextName())
        assertEquals("deep", r.nextString())
        r.endObject()
        r.endObject()
    }

    @Test
    fun arrayInObject() {
        val r = JsonStreamReader("""{"items":[1,2,3]}""")
        r.beginObject()
        assertEquals("items", r.nextName())
        r.beginArray()
        assertEquals(1, r.nextInt())
        assertEquals(2, r.nextInt())
        assertEquals(3, r.nextInt())
        r.endArray()
        r.endObject()
    }

    @Test
    fun objectsInArray() {
        val r = JsonStreamReader("""[{"id":1},{"id":2}]""")
        r.beginArray()

        r.beginObject()
        assertEquals("id", r.nextName())
        assertEquals(1, r.nextInt())
        r.endObject()

        r.beginObject()
        assertEquals("id", r.nextName())
        assertEquals(2, r.nextInt())
        r.endObject()

        r.endArray()
    }

    @Test
    fun deeplyNested() {
        val r = JsonStreamReader("""{"a":{"b":[{"c":[true]}]}}""")
        r.beginObject()
        assertEquals("a", r.nextName())
        r.beginObject()
        assertEquals("b", r.nextName())
        r.beginArray()
        r.beginObject()
        assertEquals("c", r.nextName())
        r.beginArray()
        assertTrue(r.nextBoolean())
        r.endArray()
        r.endObject()
        r.endArray()
        r.endObject()
        r.endObject()
    }

    @Test
    fun nestedEmptyStructures() {
        val r = JsonStreamReader("""{"emptyObj":{},"emptyArr":[]}""")
        r.beginObject()

        assertEquals("emptyObj", r.nextName())
        r.beginObject()
        assertFalse(r.hasNext())
        r.endObject()

        assertEquals("emptyArr", r.nextName())
        r.beginArray()
        assertFalse(r.hasNext())
        r.endArray()

        r.endObject()
    }

    // ── String escape sequences ──

    @Test
    fun stringWithEscapedQuotes() {
        val r = JsonStreamReader(""""say \"hello\""""")
        assertEquals("say \"hello\"", r.nextString())
    }

    @Test
    fun stringWithEscapedBackslash() {
        val r = JsonStreamReader(""""path\\to\\file"""")
        assertEquals("path\\to\\file", r.nextString())
    }

    @Test
    fun stringWithEscapedSlash() {
        val r = JsonStreamReader(""""a\/b"""")
        assertEquals("a/b", r.nextString())
    }

    @Test
    fun stringWithNewlineAndTab() {
        val r = JsonStreamReader(""""line1\nline2\ttab"""")
        assertEquals("line1\nline2\ttab", r.nextString())
    }

    @Test
    fun stringWithCarriageReturn() {
        val r = JsonStreamReader(""""cr\r"""")
        assertEquals("cr\r", r.nextString())
    }

    @Test
    fun stringWithBackspace() {
        val r = JsonStreamReader(""""bs\b"""")
        assertEquals("bs\b", r.nextString())
    }

    @Test
    fun stringWithFormFeed() {
        val r = JsonStreamReader(""""ff\f"""")
        assertEquals("ff\u000C", r.nextString())
    }

    @Test
    fun stringWithUnicodeEscape() {
        val r = JsonStreamReader(""""caf\u00E9"""")
        assertEquals("café", r.nextString())
    }

    @Test
    fun stringWithMultipleUnicodeEscapes() {
        val r = JsonStreamReader(""""\u0048\u0065\u006C\u006C\u006F"""")
        assertEquals("Hello", r.nextString())
    }

    @Test
    fun emptyString() {
        val r = JsonStreamReader("\"\"")
        assertEquals("", r.nextString())
    }

    @Test
    fun stringWithNonAsciiUnicode() {
        val r = JsonStreamReader("\"café ☕ 日本語\"")
        assertEquals("café ☕ 日本語", r.nextString())
    }

    @Test
    fun stringWithMixedEscapesAndUnicode() {
        val r = JsonStreamReader(""""line1\nline2\t\u0041"""")
        assertEquals("line1\nline2\tA", r.nextString())
    }

    // ── Whitespace handling ──

    @Test
    fun objectWithWhitespace() {
        val r = JsonStreamReader("""  {  "key"  :  "value"  }  """)
        r.beginObject()
        assertEquals("key", r.nextName())
        assertEquals("value", r.nextString())
        r.endObject()
    }

    @Test
    fun arrayWithWhitespace() {
        val r = JsonStreamReader("  [  1  ,  2  ,  3  ]  ")
        r.beginArray()
        assertEquals(1, r.nextInt())
        assertEquals(2, r.nextInt())
        assertEquals(3, r.nextInt())
        r.endArray()
    }

    @Test
    fun objectWithNewlinesAndTabs() {
        val json = "{\n\t\"a\": 1,\n\t\"b\": 2\n}"
        val r = JsonStreamReader(json)
        r.beginObject()
        assertEquals("a", r.nextName())
        assertEquals(1, r.nextInt())
        assertEquals("b", r.nextName())
        assertEquals(2, r.nextInt())
        r.endObject()
    }

    // ── Number formats ──

    @Test
    fun numberWithExponent() {
        val r = JsonStreamReader("1.5e10")
        assertEquals(1.5e10, r.nextDouble(), 1.0)
    }

    @Test
    fun numberWithPositiveExponent() {
        val r = JsonStreamReader("1E+2")
        assertEquals(100.0, r.nextDouble(), 0.001)
    }

    @Test
    fun numberWithNegativeExponent() {
        val r = JsonStreamReader("1e-3")
        assertEquals(0.001, r.nextDouble(), 0.0001)
    }

    @Test
    fun numberWithUpperCaseExponent() {
        val r = JsonStreamReader("2.5E3")
        assertEquals(2500.0, r.nextDouble(), 0.001)
    }

    @Test
    fun zeroPointZero() {
        val r = JsonStreamReader("0.0")
        assertEquals(0.0, r.nextDouble(), 0.001)
    }

    @Test
    fun negativeZero() {
        val r = JsonStreamReader("-0")
        assertEquals(0, r.nextInt())
    }

    // ── skipValue ──

    @Test
    fun skipString() {
        val r = JsonStreamReader("""{"skip":"ignored","keep":"found"}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals("found", r.nextString())
        r.endObject()
    }

    @Test
    fun skipNumber() {
        val r = JsonStreamReader("""{"skip":42,"keep":"yes"}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals("yes", r.nextString())
        r.endObject()
    }

    @Test
    fun skipBoolean() {
        val r = JsonStreamReader("""{"skip":true,"keep":1}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals(1, r.nextInt())
        r.endObject()
    }

    @Test
    fun skipNull() {
        val r = JsonStreamReader("""{"skip":null,"keep":"ok"}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals("ok", r.nextString())
        r.endObject()
    }

    @Test
    fun skipNestedObject() {
        val r = JsonStreamReader("""{"skip":{"a":1,"b":{"c":2}},"keep":"yes"}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals("yes", r.nextString())
        r.endObject()
    }

    @Test
    fun skipNestedArray() {
        val r = JsonStreamReader("""{"skip":[1,[2,3],4],"keep":"yes"}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals("yes", r.nextString())
        r.endObject()
    }

    @Test
    fun skipComplexNestedStructure() {
        val r = JsonStreamReader("""{"skip":{"arr":[{"nested":true},null,[]],"val":"x"},"keep":42}""")
        r.beginObject()
        assertEquals("skip", r.nextName())
        r.skipValue()
        assertEquals("keep", r.nextName())
        assertEquals(42, r.nextInt())
        r.endObject()
    }

    @Test
    fun skipNameInObject() {
        val r = JsonStreamReader("""{"a":1,"b":2}""")
        r.beginObject()
        r.skipValue() // skips name "a"
        // After skipping a name, we should be at the value
        r.skipValue() // skips value 1
        assertEquals("b", r.nextName())
        assertEquals(2, r.nextInt())
        r.endObject()
    }

    // ── Error cases ──

    @Test
    fun beginObjectOnArrayThrows() {
        val r = JsonStreamReader("[]")
        assertThrows<IllegalStateException> { r.beginObject() }
    }

    @Test
    fun beginArrayOnObjectThrows() {
        val r = JsonStreamReader("{}")
        assertThrows<IllegalStateException> { r.beginArray() }
    }

    @Test
    fun nextBooleanOnStringThrows() {
        val r = JsonStreamReader("\"hello\"")
        assertThrows<IllegalStateException> { r.nextBoolean() }
    }

    @Test
    fun nextNullOnStringThrows() {
        val r = JsonStreamReader("\"hello\"")
        assertThrows<IllegalStateException> { r.nextNull<Any>() }
    }

    @Test
    fun nextIntOnBooleanThrows() {
        val r = JsonStreamReader("true")
        assertThrows<IllegalStateException> { r.nextInt() }
    }

    @Test
    fun nextNameOutsideObjectThrows() {
        val r = JsonStreamReader("[1]")
        r.beginArray()
        assertThrows<IllegalStateException> { r.nextName() }
    }

    @Test
    fun endObjectOnArrayThrows() {
        val r = JsonStreamReader("[1]")
        r.beginArray()
        assertThrows<IllegalStateException> { r.endObject() }
    }

    @Test
    fun endArrayOnObjectThrows() {
        val r = JsonStreamReader("""{"a":1}""")
        r.beginObject()
        assertThrows<IllegalStateException> { r.endArray() }
    }

    @Test
    fun invalidJsonThrows() {
        val r = JsonStreamReader("invalid")
        assertThrows<IllegalStateException> { r.nextString() }
    }

    @Test
    fun unterminatedObjectThrows() {
        val r = JsonStreamReader("""{"a":1""")
        r.beginObject()
        r.nextName()
        r.nextInt()
        assertThrows<IllegalStateException> { r.hasNext() }
    }

    @Test
    fun unterminatedArrayThrows() {
        val r = JsonStreamReader("[1,2")
        r.beginArray()
        r.nextInt()
        r.nextInt()
        assertThrows<IllegalStateException> { r.hasNext() }
    }

    // ── hasNext on various positions ──

    @Test
    fun hasNextReturnsFalseOnEmptyObject() {
        val r = JsonStreamReader("{}")
        r.beginObject()
        assertFalse(r.hasNext())
    }

    @Test
    fun hasNextReturnsTrueOnNonEmptyObject() {
        val r = JsonStreamReader("""{"a":1}""")
        r.beginObject()
        assertTrue(r.hasNext())
    }

    @Test
    fun hasNextReturnsFalseAfterLastProperty() {
        val r = JsonStreamReader("""{"a":1}""")
        r.beginObject()
        r.nextName()
        r.nextInt()
        assertFalse(r.hasNext())
    }

    @Test
    fun hasNextReturnsFalseOnEmptyArray() {
        val r = JsonStreamReader("[]")
        r.beginArray()
        assertFalse(r.hasNext())
    }

    @Test
    fun hasNextReturnsTrueOnNonEmptyArray() {
        val r = JsonStreamReader("[1]")
        r.beginArray()
        assertTrue(r.hasNext())
    }

    @Test
    fun hasNextReturnsFalseAfterLastElement() {
        val r = JsonStreamReader("[1]")
        r.beginArray()
        r.nextInt()
        assertFalse(r.hasNext())
    }

    // ── Round-trip: Writer → Reader ──

    @Test
    fun roundTripSimpleObject() {
        val w = br.com.wdc.framework.commons.serialization.JsonStreamWriter()
        w.beginObject()
            .name("id").value(42L)
            .name("name").value("Test")
            .name("active").value(true)
            .name("score").value(9.5)
            .name("data").nullValue()
        .endObject()

        val r = JsonStreamReader(w.result())
        r.beginObject()
        assertEquals("id", r.nextName())
        assertEquals(42L, r.nextLong())
        assertEquals("name", r.nextName())
        assertEquals("Test", r.nextString())
        assertEquals("active", r.nextName())
        assertTrue(r.nextBoolean())
        assertEquals("score", r.nextName())
        assertEquals(9.5, r.nextDouble(), 0.001)
        assertEquals("data", r.nextName())
        assertNull(r.nextNull<Any>())
        r.endObject()
    }

    @Test
    fun roundTripNestedStructure() {
        val w = br.com.wdc.framework.commons.serialization.JsonStreamWriter()
        w.beginObject()
            .name("users").beginArray()
                .beginObject()
                    .name("name").value("Alice")
                    .name("age").value(30L)
                .endObject()
                .beginObject()
                    .name("name").value("Bob")
                    .name("age").value(25L)
                .endObject()
            .endArray()
        .endObject()

        val r = JsonStreamReader(w.result())
        r.beginObject()
        assertEquals("users", r.nextName())
        r.beginArray()

        r.beginObject()
        assertEquals("name", r.nextName())
        assertEquals("Alice", r.nextString())
        assertEquals("age", r.nextName())
        assertEquals(30L, r.nextLong())
        r.endObject()

        r.beginObject()
        assertEquals("name", r.nextName())
        assertEquals("Bob", r.nextString())
        assertEquals("age", r.nextName())
        assertEquals(25L, r.nextLong())
        r.endObject()

        r.endArray()
        r.endObject()
    }

    @Test
    fun roundTripEscapedStrings() {
        val original = "line1\nline2\t\"quoted\"\u0001"
        val w = br.com.wdc.framework.commons.serialization.JsonStreamWriter()
        w.value(original)

        val r = JsonStreamReader(w.result())
        assertEquals(original, r.nextString())
    }

    // ── Realistic complex document ──

    @Test
    fun realisticJsonDocument() {
        val json = """
        {
            "id": 123,
            "name": "Test Product",
            "price": 29.99,
            "active": true,
            "description": null,
            "tags": ["electronics", "sale"],
            "metadata": {
                "created": "2026-01-01",
                "views": 1000
            }
        }
        """.trimIndent()

        val r = JsonStreamReader(json)
        r.beginObject()

        assertEquals("id", r.nextName())
        assertEquals(123, r.nextInt())

        assertEquals("name", r.nextName())
        assertEquals("Test Product", r.nextString())

        assertEquals("price", r.nextName())
        assertEquals(29.99, r.nextDouble(), 0.001)

        assertEquals("active", r.nextName())
        assertTrue(r.nextBoolean())

        assertEquals("description", r.nextName())
        assertNull(r.nextNull<Any>())

        assertEquals("tags", r.nextName())
        r.beginArray()
        assertEquals("electronics", r.nextString())
        assertEquals("sale", r.nextString())
        r.endArray()

        assertEquals("metadata", r.nextName())
        r.beginObject()
        assertEquals("created", r.nextName())
        assertEquals("2026-01-01", r.nextString())
        assertEquals("views", r.nextName())
        assertEquals(1000, r.nextInt())
        r.endObject()

        r.endObject()
    }
}
