package br.com.wdc.store.scripts.codegen

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/**
 * Writes the WDC Store's design tokens, from `tokens.json` (W3C Design Tokens format) in `:store-design`, as:
 *
 * - `StoreTokens.kt`, in `:store-design` itself: what every Kotlin view (Compose, the native views, the remote
 *   Kotlin client) reads;
 * - `tokens.ts`, in the TypeScript remote client;
 * - `tokens.css`, the CSS variables of the backend's landing page.
 *
 * The results are versioned: run `./gradlew :store-scripts:generateDesignTokens` after changing `tokens.json`, and
 * commit what changes. `DesignTokensTest` fails while they are out of sync.
 *
 * Groups are the top-level keys of the file; a token is an object with `$value`. Colors are `#RRGGBB` or `#RRGGBBAA`;
 * dimensions `<n>px`; font weights numbers; font families lists of names.
 */
object GenerateDesignTokens {

    /** One file to write: where, and what. */
    class Output(val path: Path, val text: String)

    /** The source file, under the store's folder (`fontes/br.com.wdc.kt.store`). */
    fun source(storeDir: Path): Path = storeDir.resolve("br.com.wdc.kt.store.design/tokens.json")

    @JvmStatic
    fun main(args: Array<String>) {
        val storeDir = Path.of(args.firstOrNull() ?: error("Give the store's folder (fontes/br.com.wdc.kt.store)"))
        for (output in generate(storeDir)) {
            Files.createDirectories(output.path.parent)
            Files.writeString(output.path, output.text)
            println("Design tokens: wrote ${storeDir.relativize(output.path)}")
        }
    }

    fun generate(storeDir: Path): List<Output> {
        val groups = parse(Files.readString(source(storeDir)))
        return listOf(
            Output(storeDir.resolve("br.com.wdc.kt.store.design/src/commonMain/kotlin/br/com/wdc/store/design/StoreTokens.kt"), kotlin(groups)),
            Output(storeDir.resolve("br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript/src/scripts/tokens.ts"), typescript(groups)),
            Output(storeDir.resolve("br.com.wdc.kt.store.backend/src/main/resources/landing/tokens.css"), css(groups)),
        )
    }

    // :: Reading

    private class Token(val name: String, val type: String, val value: JsonElement)

    private class Group(val name: String, val tokens: List<Token>)

    private fun parse(text: String): List<Group> {
        val root = JsonParser.parseString(text).asJsonObject
        return root.entrySet().filter { !it.key.startsWith("$") }.map { (groupName, element) ->
            val group = element.asJsonObject
            val groupType = group.get("\$type")?.asString
            val tokens = group.entrySet().filter { !it.key.startsWith("$") }.map { (name, tokenElement) ->
                val token = tokenElement as JsonObject
                val type = token.get("\$type")?.asString ?: groupType ?: error("$groupName.$name has no \$type")
                Token(name, type, token.get("\$value") ?: error("$groupName.$name has no \$value"))
            }
            Group(groupName, tokens)
        }
    }

    private const val HEADER = "Generated from br.com.wdc.kt.store.design/tokens.json by ./gradlew :store-scripts:generateDesignTokens: do not edit."

    // :: Values

    /** `#RRGGBB` or `#RRGGBBAA` as red, green, blue, alpha (0–255). */
    private fun rgba(hex: String): IntArray {
        require(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(hex)) { "Not a #RRGGBB(AA) color: $hex" }
        val channel = { i: Int -> hex.substring(1 + 2 * i, 3 + 2 * i).toInt(16) }
        return intArrayOf(channel(0), channel(1), channel(2), if (hex.length == 9) channel(3) else 255)
    }

    private fun argb(hex: String): String {
        val (r, g, b, a) = rgba(hex).toList()
        return "0x%02X%02X%02X%02XL".format(a, r, g, b)
    }

    private fun cssColor(hex: String): String {
        val (r, g, b, a) = rgba(hex).toList()
        if (a == 255) return "#%02X%02X%02X".format(r, g, b)
        val alpha = (a / 255.0 * 100).roundToInt() / 100.0
        return "rgba($r, $g, $b, ${alpha.toString().trimEnd('0').trimEnd('.')})"
    }

    private fun px(value: JsonElement): Int = value.asString.removeSuffix("px").toInt()

    private fun family(value: JsonElement): List<String> = value.asJsonArray.map { it.asString }

    private fun cssFamily(names: List<String>): String =
        names.joinToString(", ") { if (it.contains('-') && it.endsWith("serif")) it else "'$it'" }

    private fun pascal(name: String): String = name.replaceFirstChar { it.uppercase() }

    private fun kebab(name: String): String = name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

    // :: Kotlin

    private fun kotlin(groups: List<Group>): String = buildString {
        appendLine("// $HEADER")
        appendLine("package br.com.wdc.store.design")
        appendLine()
        appendLine("/**")
        appendLine(" * The WDC Store's design tokens, the same for every view technology. Colors are `0xAARRGGBB` (see [TokenColors]);")
        appendLine(" * dimensions are in the platform's unit (dp, pt, px); font weights are CSS weights.")
        appendLine(" */")
        appendLine("object StoreTokens {")
        for ((index, group) in groups.withIndex()) {
            if (index > 0) appendLine()
            appendLine("    object ${pascal(group.name)} {")
            for (token in group.tokens) {
                val declaration = when (token.type) {
                    "color" -> "const val ${pascal(token.name)}: Long = ${argb(token.value.asString)}"
                    "dimension" -> "const val ${pascal(token.name)}: Int = ${px(token.value)}"
                    "fontWeight" -> "const val ${pascal(token.name)}: Int = ${token.value.asInt}"
                    "fontFamily" -> "val ${pascal(token.name)}: List<String> = listOf(${family(token.value).joinToString(", ") { "\"$it\"" }})"
                    else -> error("Unknown token type ${token.type}")
                }
                appendLine("        $declaration")
            }
            appendLine("    }")
        }
        appendLine("}")
    }

    // :: TypeScript

    private fun typescript(groups: List<Group>): String = buildString {
        appendLine("// $HEADER")
        appendLine()
        appendLine("/** The WDC Store's design tokens, the same for every view technology. Dimensions in px. */")
        appendLine("export const StoreTokens = {")
        for (group in groups) {
            appendLine("  ${group.name}: {")
            for (token in group.tokens) {
                val value = when (token.type) {
                    "color" -> "'${cssColor(token.value.asString)}'"
                    "dimension" -> "${px(token.value)}"
                    "fontWeight" -> "${token.value.asInt}"
                    "fontFamily" -> "\"${cssFamily(family(token.value))}\""
                    else -> error("Unknown token type ${token.type}")
                }
                appendLine("    ${token.name}: $value,")
            }
            appendLine("  },")
        }
        appendLine("} as const")
    }

    // :: CSS

    private fun css(groups: List<Group>): String = buildString {
        appendLine("/* $HEADER */")
        appendLine(":root {")
        for (group in groups) {
            for (token in group.tokens) {
                val value = when (token.type) {
                    "color" -> cssColor(token.value.asString)
                    "dimension" -> "${px(token.value)}px"
                    "fontWeight" -> "${token.value.asInt}"
                    "fontFamily" -> cssFamily(family(token.value))
                    else -> error("Unknown token type ${token.type}")
                }
                appendLine("  --store-${kebab(group.name)}-${kebab(token.name)}: $value;")
            }
        }
        appendLine("}")
    }
}
