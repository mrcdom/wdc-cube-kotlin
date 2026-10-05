package br.com.wdc.framework.cube.util

import br.com.wdc.framework.cube.CubeIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryStringParserTest {

    private fun parseQuery(query: String): CubeIntent {
        val intent = CubeIntent()
        QueryStringParser.parse(intent, query)
        return intent
    }

    // -- Basic parsing --

    @Test
    fun `parse single key-value pair`() {
        val intent = parseQuery("name=John")
        assertEquals("John", intent.getParameterValue("name"))
    }

    @Test
    fun `parse multiple key-value pairs`() {
        val intent = parseQuery("name=John&age=30&city=NYC")
        assertEquals("John", intent.getParameterValue("name"))
        assertEquals("30", intent.getParameterValue("age"))
        assertEquals("NYC", intent.getParameterValue("city"))
    }

    @Test
    fun `parse empty value`() {
        val intent = parseQuery("key=")
        assertEquals("", intent.getParameterValue("key"))
    }

    @Test
    fun `parse value with equals sign`() {
        val intent = parseQuery("expr=a=b")
        assertEquals("a=b", intent.getParameterValue("expr"))
    }

    // -- URL encoding --

    @Test
    fun `decode plus sign as space`() {
        val intent = parseQuery("name=John+Doe")
        assertEquals("John Doe", intent.getParameterValue("name"))
    }

    @Test
    fun `decode percent-encoded characters`() {
        val intent = parseQuery("msg=hello%20world")
        assertEquals("hello world", intent.getParameterValue("msg"))
    }

    @Test
    fun `decode percent-encoded special characters`() {
        val intent = parseQuery("q=%26%3D%25")
        assertEquals("&=%", intent.getParameterValue("q"))
    }

    @Test
    fun `decode percent-encoded UTF-8 multibyte`() {
        // "ã" in UTF-8 is C3 A3
        val intent = parseQuery("text=%C3%A3")
        assertEquals("ã", intent.getParameterValue("text"))
    }

    // -- Duplicate keys (multi-value / list behavior) --

    @Test
    fun `duplicate keys create list`() {
        val intent = parseQuery("id=1&id=2")
        val value = intent.getParameterValue("id")
        assertTrue(value is List<*>, "Expected List for duplicate keys, got ${value?.let { it::class.simpleName }}")
        val list = value as List<*>
        assertEquals(2, list.size)
        assertEquals("1", list[0])
        assertEquals("2", list[1])
    }

    @Test
    fun `triple duplicate keys create list of three`() {
        val intent = parseQuery("id=a&id=b&id=c")
        val value = intent.getParameterValue("id")
        assertTrue(value is List<*>)
        val list = value as List<*>
        assertEquals(3, list.size)
        assertEquals("a", list[0])
        assertEquals("b", list[1])
        assertEquals("c", list[2])
    }

    @Test
    fun `mixed single and duplicate keys`() {
        val intent = parseQuery("name=John&tag=a&tag=b&age=30")
        assertEquals("John", intent.getParameterValue("name"))
        assertEquals("30", intent.getParameterValue("age"))
        val tags = intent.getParameterValue("tag")
        assertTrue(tags is List<*>)
        assertEquals(2, (tags as List<*>).size)
        assertEquals("a", tags[0])
        assertEquals("b", tags[1])
    }

    // -- Edge cases --

    @Test
    fun `null data does nothing`() {
        val intent = CubeIntent()
        QueryStringParser.parse(intent, null)
        assertTrue(intent.parameters.isEmpty())
    }

    @Test
    fun `empty string does nothing`() {
        val intent = CubeIntent()
        QueryStringParser.parse(intent, "")
        assertTrue(intent.parameters.isEmpty())
    }

    @Test
    fun `blank string does nothing`() {
        val intent = CubeIntent()
        QueryStringParser.parse(intent, "   ")
        assertTrue(intent.parameters.isEmpty())
    }

    @Test
    fun `key without value is ignored`() {
        val intent = parseQuery("justkey")
        assertNull(intent.getParameterValue("justkey"))
    }

    @Test
    fun `key with value followed by key without value`() {
        val intent = parseQuery("a=1&novalue")
        assertEquals("1", intent.getParameterValue("a"))
        assertNull(intent.getParameterValue("novalue"))
    }

    // -- parseParameters with String directly --

    @Test
    fun `parseParameters with string`() {
        val intent = CubeIntent()
        QueryStringParser.parseParameters(intent, "x=10&y=20")
        assertEquals("10", intent.getParameterValue("x"))
        assertEquals("20", intent.getParameterValue("y"))
    }

    @Test
    fun `parseParameters with empty string does nothing`() {
        val intent = CubeIntent()
        QueryStringParser.parseParameters(intent, "")
        assertTrue(intent.parameters.isEmpty())
    }

    // -- Complex scenarios --

    @Test
    fun `encoded key and value`() {
        val intent = parseQuery("my%20key=my%20value")
        assertEquals("my value", intent.getParameterValue("my key"))
    }

    @Test
    fun `multiple encoded pairs with plus and percent`() {
        val intent = parseQuery("first=Hello+World&second=%41%42%43")
        assertEquals("Hello World", intent.getParameterValue("first"))
        assertEquals("ABC", intent.getParameterValue("second"))
    }

    @Test
    fun `decode percent-encoded UTF-8 three-byte character`() {
        // "€" (Euro sign) in UTF-8 is E2 82 AC
        val intent = parseQuery("price=%E2%82%AC100")
        assertEquals("€100", intent.getParameterValue("price"))
    }

    @Test
    fun `decode percent-encoded UTF-8 four-byte character`() {
        // "😀" in UTF-8 is F0 9F 98 80
        val intent = parseQuery("emoji=%F0%9F%98%80")
        assertEquals("😀", intent.getParameterValue("emoji"))
    }
}
