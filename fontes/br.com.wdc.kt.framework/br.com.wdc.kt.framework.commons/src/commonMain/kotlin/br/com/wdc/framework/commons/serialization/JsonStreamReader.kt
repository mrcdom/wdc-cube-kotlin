package br.com.wdc.framework.commons.serialization

/**
 * Streaming JSON reader — pure Kotlin, sem dependências de plataforma.
 *
 * Implementa [ExtensibleObjectInput] lendo diretamente da string JSON
 * sem construir árvore intermediária (Map/List).
 */
class JsonStreamReader(private val source: String) : ExtensibleObjectInput {

    private companion object {
        // Scope constants
        private const val SCOPE_EMPTY_DOCUMENT = 0
        private const val SCOPE_NONEMPTY_DOCUMENT = 1
        private const val SCOPE_EMPTY_ARRAY = 2
        private const val SCOPE_NONEMPTY_ARRAY = 3
        private const val SCOPE_EMPTY_OBJECT = 4
        private const val SCOPE_NONEMPTY_OBJECT = 5
        private const val SCOPE_DANGLING_NAME = 6

        // Peeked token constants
        private const val PEEKED_NONE = 0
        private const val PEEKED_BEGIN_OBJECT = 1
        private const val PEEKED_END_OBJECT = 2
        private const val PEEKED_BEGIN_ARRAY = 3
        private const val PEEKED_END_ARRAY = 4
        private const val PEEKED_TRUE = 5
        private const val PEEKED_FALSE = 6
        private const val PEEKED_NULL = 7
        private const val PEEKED_STRING = 8
        private const val PEEKED_NAME = 9
        private const val PEEKED_NUMBER = 10
        private const val PEEKED_EOF = 11
    }

    private var pos = 0
    private var scopeStack = IntArray(16)
    private var stackSize = 1
    private var peeked = PEEKED_NONE

    init {
        scopeStack[0] = SCOPE_EMPTY_DOCUMENT
    }

    // ── ExtensibleObjectInput ──

    override fun beginObject() {
        val p = ensurePeeked()
        if (p != PEEKED_BEGIN_OBJECT) throw illegalState("BEGIN_OBJECT", p)
        pos++ // consume '{'
        push(SCOPE_EMPTY_OBJECT)
        peeked = PEEKED_NONE
    }

    override fun endObject() {
        val p = ensurePeeked()
        if (p != PEEKED_END_OBJECT) throw illegalState("END_OBJECT", p)
        stackSize--
        peeked = PEEKED_NONE
    }

    override fun beginArray() {
        val p = ensurePeeked()
        if (p != PEEKED_BEGIN_ARRAY) throw illegalState("BEGIN_ARRAY", p)
        pos++ // consume '['
        push(SCOPE_EMPTY_ARRAY)
        peeked = PEEKED_NONE
    }

    override fun endArray() {
        val p = ensurePeeked()
        if (p != PEEKED_END_ARRAY) throw illegalState("END_ARRAY", p)
        stackSize--
        peeked = PEEKED_NONE
    }

    override fun hasNext(): Boolean {
        val p = ensurePeeked()
        return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY && p != PEEKED_EOF
    }

    override fun peek(): SerializationToken = when (ensurePeeked()) {
        PEEKED_BEGIN_OBJECT -> SerializationToken.BEGIN_OBJECT
        PEEKED_END_OBJECT -> SerializationToken.END_OBJECT
        PEEKED_BEGIN_ARRAY -> SerializationToken.BEGIN_ARRAY
        PEEKED_END_ARRAY -> SerializationToken.END_ARRAY
        PEEKED_TRUE, PEEKED_FALSE -> SerializationToken.BOOLEAN
        PEEKED_NULL -> SerializationToken.NULL
        PEEKED_STRING -> SerializationToken.STRING
        PEEKED_NAME -> SerializationToken.NAME
        PEEKED_NUMBER -> SerializationToken.NUMBER
        PEEKED_EOF -> SerializationToken.END_DOCUMENT
        else -> throw IllegalStateException("Unknown peeked state")
    }

    override fun nextName(): String {
        val p = ensurePeeked()
        if (p != PEEKED_NAME) throw illegalState("NAME", p)
        val name = readQuotedString()
        scopeStack[stackSize - 1] = SCOPE_DANGLING_NAME
        peeked = PEEKED_NONE
        return name
    }

    override fun nextString(): String {
        val p = ensurePeeked()
        val result = when (p) {
            PEEKED_STRING -> readQuotedString()
            PEEKED_NUMBER -> readRawNumber()
            PEEKED_TRUE -> { consumeLiteral("true"); "true" }
            PEEKED_FALSE -> { consumeLiteral("false"); "false" }
            PEEKED_NULL -> { consumeLiteral("null"); "null" }
            else -> throw illegalState("STRING", p)
        }
        peeked = PEEKED_NONE
        return result
    }

    override fun nextBoolean(): Boolean {
        val p = ensurePeeked()
        return when (p) {
            PEEKED_TRUE -> { consumeLiteral("true"); peeked = PEEKED_NONE; true }
            PEEKED_FALSE -> { consumeLiteral("false"); peeked = PEEKED_NONE; false }
            else -> throw illegalState("BOOLEAN", p)
        }
    }

    override fun <T> nextNull(): T? {
        val p = ensurePeeked()
        if (p != PEEKED_NULL) throw illegalState("NULL", p)
        consumeLiteral("null")
        peeked = PEEKED_NONE
        return null
    }

    override fun nextDouble(): Double {
        val p = ensurePeeked()
        val raw = when (p) {
            PEEKED_NUMBER -> readRawNumber()
            PEEKED_STRING -> readQuotedString()
            else -> throw illegalState("NUMBER", p)
        }
        peeked = PEEKED_NONE
        return raw.toDouble()
    }

    override fun nextLong(): Long {
        val p = ensurePeeked()
        val raw = when (p) {
            PEEKED_NUMBER -> readRawNumber()
            PEEKED_STRING -> readQuotedString()
            else -> throw illegalState("NUMBER", p)
        }
        peeked = PEEKED_NONE
        return raw.toLongOrNull() ?: run {
            val d = raw.toDouble()
            val l = d.toLong()
            if (d == l.toDouble()) l
            else throw NumberFormatException("Expected long but was $raw at position $pos")
        }
    }

    override fun nextInt(): Int {
        val p = ensurePeeked()
        val raw = when (p) {
            PEEKED_NUMBER -> readRawNumber()
            PEEKED_STRING -> readQuotedString()
            else -> throw illegalState("NUMBER", p)
        }
        peeked = PEEKED_NONE
        return raw.toIntOrNull() ?: run {
            val d = raw.toDouble()
            val i = d.toInt()
            if (d == i.toDouble()) i
            else throw NumberFormatException("Expected int but was $raw at position $pos")
        }
    }

    override fun nextNumber(): Number? {
        val p = ensurePeeked()
        if (p == PEEKED_NULL) {
            consumeLiteral("null")
            peeked = PEEKED_NONE
            return null
        }
        val raw = when (p) {
            PEEKED_NUMBER -> readRawNumber()
            PEEKED_STRING -> readQuotedString()
            else -> throw illegalState("NUMBER", p)
        }
        peeked = PEEKED_NONE
        return if ('.' in raw || 'e' in raw || 'E' in raw) {
            raw.toDouble()
        } else {
            val longVal = raw.toLong()
            if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt() else longVal
        }
    }

    override fun skipValue() {
        var depth = 0
        do {
            val p = ensurePeeked()
            when (p) {
                PEEKED_BEGIN_OBJECT -> { pos++; push(SCOPE_EMPTY_OBJECT); depth++ }
                PEEKED_END_OBJECT -> { stackSize--; depth-- }
                PEEKED_BEGIN_ARRAY -> { pos++; push(SCOPE_EMPTY_ARRAY); depth++ }
                PEEKED_END_ARRAY -> { stackSize--; depth-- }
                PEEKED_NAME -> {
                    readQuotedString()
                    scopeStack[stackSize - 1] = SCOPE_DANGLING_NAME
                }
                PEEKED_STRING -> readQuotedString()
                PEEKED_NUMBER -> readRawNumber()
                PEEKED_TRUE -> consumeLiteral("true")
                PEEKED_FALSE -> consumeLiteral("false")
                PEEKED_NULL -> consumeLiteral("null")
                PEEKED_EOF -> return
            }
            peeked = PEEKED_NONE
        } while (depth > 0)
    }

    // ── State machine ──

    private fun ensurePeeked(): Int {
        if (peeked == PEEKED_NONE) {
            peeked = doPeek()
        }
        return peeked
    }

    private fun doPeek(): Int {
        val scope = scopeStack[stackSize - 1]

        when (scope) {
            SCOPE_EMPTY_DOCUMENT -> {
                scopeStack[stackSize - 1] = SCOPE_NONEMPTY_DOCUMENT
            }

            SCOPE_NONEMPTY_DOCUMENT -> {
                skipWhitespace()
                return if (pos >= source.length) PEEKED_EOF
                else throw syntaxError("Expected end of document")
            }

            SCOPE_EMPTY_ARRAY -> {
                scopeStack[stackSize - 1] = SCOPE_NONEMPTY_ARRAY
                skipWhitespace()
                if (pos < source.length && source[pos] == ']') {
                    pos++
                    return PEEKED_END_ARRAY
                }
            }

            SCOPE_NONEMPTY_ARRAY -> {
                skipWhitespace()
                if (pos >= source.length) throw syntaxError("Unterminated array")
                when (source[pos]) {
                    ']' -> { pos++; return PEEKED_END_ARRAY }
                    ',' -> pos++
                    else -> throw syntaxError("Expected ',' or ']'")
                }
            }

            SCOPE_EMPTY_OBJECT -> {
                scopeStack[stackSize - 1] = SCOPE_NONEMPTY_OBJECT
                skipWhitespace()
                if (pos < source.length) {
                    when (source[pos]) {
                        '}' -> { pos++; return PEEKED_END_OBJECT }
                        '"' -> return PEEKED_NAME
                    }
                }
                throw syntaxError("Expected '\"' or '}'")
            }

            SCOPE_NONEMPTY_OBJECT -> {
                skipWhitespace()
                if (pos >= source.length) throw syntaxError("Unterminated object")
                when (source[pos]) {
                    '}' -> { pos++; return PEEKED_END_OBJECT }
                    ',' -> {
                        pos++
                        skipWhitespace()
                        if (pos < source.length && source[pos] == '"') {
                            return PEEKED_NAME
                        }
                        throw syntaxError("Expected name after ','")
                    }
                    else -> throw syntaxError("Expected ',' or '}'")
                }
            }

            SCOPE_DANGLING_NAME -> {
                scopeStack[stackSize - 1] = SCOPE_NONEMPTY_OBJECT
                skipWhitespace()
                if (pos >= source.length || source[pos] != ':') {
                    throw syntaxError("Expected ':'")
                }
                pos++
            }
        }

        // Read a value token
        skipWhitespace()
        if (pos >= source.length) throw syntaxError("Unexpected end of input")

        return when (source[pos]) {
            '{' -> PEEKED_BEGIN_OBJECT
            '[' -> PEEKED_BEGIN_ARRAY
            '"' -> PEEKED_STRING
            't' -> PEEKED_TRUE
            'f' -> PEEKED_FALSE
            'n' -> PEEKED_NULL
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> PEEKED_NUMBER
            else -> throw syntaxError("Unexpected character '${source[pos]}'")
        }
    }

    // ── Low-level parsing ──

    private fun skipWhitespace() {
        while (pos < source.length) {
            when (source[pos]) {
                ' ', '\t', '\n', '\r' -> pos++
                else -> return
            }
        }
    }

    private fun readQuotedString(): String {
        if (pos >= source.length || source[pos] != '"') {
            throw syntaxError("Expected '\"'")
        }
        pos++ // consume opening quote

        // Fast path: scan for closing quote without escapes
        val start = pos
        while (pos < source.length) {
            val c = source[pos]
            if (c == '"') {
                val result = source.substring(start, pos)
                pos++ // consume closing quote
                return result
            }
            if (c == '\\') break // has escapes, use slow path
            pos++
        }

        // Slow path: string has escape sequences
        val sb = StringBuilder(source.substring(start, pos))
        while (pos < source.length) {
            val c = source[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= source.length) throw syntaxError("Unterminated escape sequence")
                    when (val escaped = source[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > source.length) throw syntaxError("Unterminated unicode escape")
                            val hex = source.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> throw syntaxError("Invalid escape: '\\$escaped'")
                    }
                }
                else -> sb.append(c)
            }
        }
        throw syntaxError("Unterminated string")
    }

    private fun readRawNumber(): String {
        val start = pos
        if (pos < source.length && source[pos] == '-') pos++
        if (pos < source.length && source[pos] == '0') {
            pos++
        } else {
            readDigits()
        }
        if (pos < source.length && source[pos] == '.') {
            pos++
            readDigits()
        }
        if (pos < source.length && (source[pos] == 'e' || source[pos] == 'E')) {
            pos++
            if (pos < source.length && (source[pos] == '+' || source[pos] == '-')) pos++
            readDigits()
        }
        return source.substring(start, pos)
    }

    private fun readDigits() {
        if (pos >= source.length || source[pos] !in '0'..'9') {
            throw syntaxError("Expected digit")
        }
        while (pos < source.length && source[pos] in '0'..'9') {
            pos++
        }
    }

    private fun consumeLiteral(expected: String) {
        for (i in expected.indices) {
            if (pos + i >= source.length || source[pos + i] != expected[i]) {
                throw syntaxError("Expected '$expected'")
            }
        }
        pos += expected.length
    }

    private fun push(scope: Int) {
        if (stackSize >= scopeStack.size) {
            scopeStack = scopeStack.copyOf(scopeStack.size * 2)
        }
        scopeStack[stackSize++] = scope
    }

    private fun syntaxError(message: String): IllegalStateException {
        return IllegalStateException("$message at position $pos")
    }

    private fun illegalState(expected: String, actual: Int): IllegalStateException {
        val actualName = when (actual) {
            PEEKED_BEGIN_OBJECT -> "BEGIN_OBJECT"
            PEEKED_END_OBJECT -> "END_OBJECT"
            PEEKED_BEGIN_ARRAY -> "BEGIN_ARRAY"
            PEEKED_END_ARRAY -> "END_ARRAY"
            PEEKED_TRUE, PEEKED_FALSE -> "BOOLEAN"
            PEEKED_NULL -> "NULL"
            PEEKED_STRING -> "STRING"
            PEEKED_NAME -> "NAME"
            PEEKED_NUMBER -> "NUMBER"
            PEEKED_EOF -> "END_DOCUMENT"
            else -> "UNKNOWN"
        }
        return IllegalStateException("Expected $expected but was $actualName at position $pos")
    }
}
