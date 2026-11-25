package br.com.wdc.framework.cube.remote.bridge

/**
 * The matching rules of the conformance fixtures (`conformance/README.md`), over parsed JSON: partial objects,
 * `$absent`, `$any`, `$string`, `$number`, `$capture:<name>` and `${name}`, `scopes` by id, `releasedViews` as a set.
 */
internal class FixtureMatcher {

    private val captured = HashMap<String, dynamic>()

    /** [template] with every `${name}` replaced — in values, keys and inside strings. */
    fun substitute(template: dynamic): dynamic {
        if (isArray(template)) return template.unsafeCast<Array<dynamic>>().map { substitute(it) }.toTypedArray()
        if (isObject(template)) {
            val out: dynamic = js("({})")
            for (key in keys(template)) out[substituteText(key)] = substitute(template[key])
            return out
        }
        if (jsTypeOf(template) == "string") {
            val text = template as String
            WHOLE.matchEntire(text)?.let { return captured(it.groupValues[1]) }
            return substituteText(text)
        }
        return template
    }

    private fun captured(name: String): dynamic {
        if (!captured.containsKey(name)) fail("\${$name} was never captured")
        return captured[name]
    }

    private fun substituteText(text: String): String = PART.replace(text) { m ->
        val value = captured(m.groupValues[1])
        if (jsTypeOf(value) == "string") value as String else JSON.stringify(value)
    }

    fun match(expected: dynamic, actual: dynamic, path: String = "$") {
        val present = actual !== undefined
        if (jsTypeOf(expected) == "string") {
            val text = expected as String
            when {
                text == "\$absent" -> if (present) fail("$path: expected absent, was ${JSON.stringify(actual)}")
                text == "\$any" -> if (!present) fail("$path: expected a value, was absent")
                text == "\$string" -> if (jsTypeOf(actual) != "string") fail("$path: expected a string, was ${JSON.stringify(actual)}")
                text == "\$number" -> if (jsTypeOf(actual) != "number") fail("$path: expected a number, was ${JSON.stringify(actual)}")
                text.startsWith("\$capture:") -> {
                    if (!present) fail("$path: expected a value to capture, was absent")
                    captured[text.removePrefix("\$capture:")] = actual
                }
                else -> matchValue(substitute(expected), actual, path)
            }
            return
        }
        if (!present) fail("$path: expected ${JSON.stringify(expected)}, was absent")
        when {
            isArray(expected) -> {
                if (!isArray(actual)) fail("$path: expected an array, was ${JSON.stringify(actual)}")
                val e = expected.unsafeCast<Array<dynamic>>()
                val a = actual.unsafeCast<Array<dynamic>>()
                if (e.size != a.size) fail("$path: expected ${e.size} elements, was ${a.size}: ${JSON.stringify(actual)}")
                for (i in e.indices) match(e[i], a[i], "$path[$i]")
            }
            isObject(expected) -> {
                if (!isObject(actual)) fail("$path: expected an object, was ${JSON.stringify(actual)}")
                for (key in keys(expected)) {
                    val name = substituteText(key)
                    val value = expected[key]
                    when {
                        name == "scopes" && isArray(value) -> matchScopes(value, actual[name], "$path.scopes")
                        name == "releasedViews" && isArray(value) -> matchSet(value, actual[name], "$path.releasedViews")
                        else -> match(value, actual[name], "$path.$name")
                    }
                }
            }
            else -> matchValue(expected, actual, path)
        }
    }

    private fun matchValue(expected: dynamic, actual: dynamic, path: String) {
        if (JSON.stringify(expected) != JSON.stringify(actual)) {
            fail("$path: expected ${JSON.stringify(expected)}, was ${JSON.stringify(actual)}")
        }
    }

    private fun matchScopes(expected: dynamic, actual: dynamic, path: String) {
        if (!isArray(actual)) fail("$path: expected scopes, was ${JSON.stringify(actual)}")
        val byId = actual.unsafeCast<Array<dynamic>>().associateBy { it.id as String }
        val e = expected.unsafeCast<Array<dynamic>>()
        if (e.size != byId.size) fail("$path: expected ${e.size} scopes, was ${byId.keys}")
        for (scope in e) {
            val id = substitute(scope.id) as String
            match(scope, byId[id] ?: fail("$path: no scope $id among ${byId.keys}"), "$path[$id]")
        }
    }

    private fun matchSet(expected: dynamic, actual: dynamic, path: String) {
        if (!isArray(actual)) fail("$path: expected an array, was ${JSON.stringify(actual)}")
        val want = expected.unsafeCast<Array<dynamic>>().map { JSON.stringify(substitute(it)) }.toSet()
        val got = actual.unsafeCast<Array<dynamic>>().map { JSON.stringify(it) }.toSet()
        if (want != got) fail("$path: expected $want, was $got")
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    private companion object {
        val WHOLE = Regex("""^\$\{([A-Za-z0-9_-]+)\}$""")
        val PART = Regex("""\$\{([A-Za-z0-9_-]+)\}""")

        fun isArray(v: dynamic): Boolean = js("Array.isArray")(v) == true
        fun isObject(v: dynamic): Boolean = v != null && jsTypeOf(v) == "object" && !isArray(v)
        fun keys(v: dynamic): Array<String> = js("Object.keys")(v).unsafeCast<Array<String>>()
    }
}
