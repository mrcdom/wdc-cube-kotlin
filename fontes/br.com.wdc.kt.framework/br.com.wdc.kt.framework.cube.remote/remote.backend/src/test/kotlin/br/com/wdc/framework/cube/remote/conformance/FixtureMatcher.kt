package br.com.wdc.framework.cube.remote.conformance

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * The matching rules of the conformance fixtures (`conformance/README.md`): partial objects, `$absent`, `$any`,
 * `$string`, `$number`, `$capture:<name>` and `${name}`, `scopes` by id and `releasedViews` as a set.
 */
class FixtureMatcher {

    /** The values captured so far, by name. */
    val captured = HashMap<String, JsonElement>()

    /** [template] with every `${name}` replaced — in values, keys and inside strings. */
    fun substitute(template: JsonElement): JsonElement = when {
        template.isJsonObject -> JsonObject().also { out ->
            for ((key, value) in template.asJsonObject.entrySet()) out.add(substituteText(key), substitute(value))
        }
        template.isJsonArray -> JsonArray().also { out -> template.asJsonArray.forEach { out.add(substitute(it)) } }
        template.isJsonPrimitive && template.asJsonPrimitive.isString -> {
            val text = template.asString
            val whole = WHOLE.matchEntire(text)
            if (whole != null) captured[whole.groupValues[1]] ?: fail("\${${whole.groupValues[1]}} was never captured")
            else JsonPrimitive(substituteText(text))
        }
        else -> template
    }

    private fun substituteText(text: String): String = PART.replace(text) { m ->
        val value = captured[m.groupValues[1]] ?: fail("\${${m.groupValues[1]}} was never captured")
        if (value.isJsonPrimitive) value.asString else value.toString()
    }

    /** Checks that [actual] matches [expected]; captures along the way. */
    fun match(expected: JsonElement, actual: JsonElement?, path: String = "$") {
        if (expected.isJsonPrimitive && expected.asJsonPrimitive.isString) {
            val text = expected.asString
            when {
                text == "\$absent" -> if (actual != null) fail("$path: expected absent, was $actual")
                text == "\$any" -> if (actual == null) fail("$path: expected a value, was absent")
                text == "\$string" -> if (actual == null || !actual.isJsonPrimitive || !actual.asJsonPrimitive.isString) fail("$path: expected a string, was $actual")
                text == "\$number" -> if (actual == null || !actual.isJsonPrimitive || !actual.asJsonPrimitive.isNumber) fail("$path: expected a number, was $actual")
                text.startsWith("\$capture:") -> {
                    if (actual == null) fail("$path: expected a value to capture, was absent")
                    captured[text.removePrefix("\$capture:")] = actual!!
                }
                else -> matchValue(substitute(expected), actual, path)
            }
            return
        }
        if (actual == null) fail("$path: expected $expected, was absent")
        when {
            expected.isJsonObject -> {
                if (!actual!!.isJsonObject) fail("$path: expected an object, was $actual")
                for ((key, value) in expected.asJsonObject.entrySet()) {
                    val name = substituteText(key)
                    val inner = actual.asJsonObject.get(name)
                    when (name) {
                        "scopes" -> if (value.isJsonArray) matchScopes(value.asJsonArray, inner, "$path.scopes") else match(value, inner, "$path.scopes")
                        "releasedViews" -> if (value.isJsonArray) matchSet(value.asJsonArray, inner, "$path.releasedViews") else match(value, inner, "$path.releasedViews")
                        else -> match(value, inner, "$path.$name")
                    }
                }
            }
            expected.isJsonArray -> {
                if (!actual!!.isJsonArray) fail("$path: expected an array, was $actual")
                val e = expected.asJsonArray
                val a = actual.asJsonArray
                if (e.size() != a.size()) fail("$path: expected ${e.size()} elements, was ${a.size()}: $a")
                for (i in 0 until e.size()) match(e[i], a[i], "$path[$i]")
            }
            else -> matchValue(expected, actual, path)
        }
    }

    private fun matchValue(expected: JsonElement, actual: JsonElement?, path: String) {
        if (actual == null) fail("$path: expected $expected, was absent")
        val same = if (expected.isJsonPrimitive && expected.asJsonPrimitive.isNumber && actual!!.isJsonPrimitive && actual.asJsonPrimitive.isNumber) {
            expected.asDouble == actual.asDouble
        } else {
            expected == actual
        }
        if (!same) fail("$path: expected $expected, was $actual")
    }

    /** `scopes`: each expected scope against the actual one with the same id (after substitution), same count. */
    private fun matchScopes(expected: JsonArray, actual: JsonElement?, path: String) {
        if (actual == null || !actual.isJsonArray) fail("$path: expected scopes, was $actual")
        val byId = actual!!.asJsonArray.associateBy { it.asJsonObject.get("id")?.asString }
        if (expected.size() != byId.size) fail("$path: expected ${expected.size()} scopes, was ${byId.size}: ${byId.keys}")
        for (scope in expected) {
            val id = substitute(scope.asJsonObject.get("id")).asString
            match(scope, byId[id] ?: fail("$path: no scope $id among ${byId.keys}"), "$path[$id]")
        }
    }

    private fun matchSet(expected: JsonArray, actual: JsonElement?, path: String) {
        if (actual == null || !actual.isJsonArray) fail("$path: expected an array, was $actual")
        val want = expected.map { substitute(it) }.toSet()
        val got = actual!!.asJsonArray.toSet()
        if (want != got) fail("$path: expected $want, was $got")
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    private companion object {
        val WHOLE = Regex("""^\$\{([A-Za-z0-9_-]+)}$""")
        val PART = Regex("""\$\{([A-Za-z0-9_-]+)}""")
    }
}

/** `JsonNull` from a parse is the JSON `null`, a value; a missing field is Kotlin's `null`. */
internal val JSON_NULL: JsonElement = JsonNull.INSTANCE
