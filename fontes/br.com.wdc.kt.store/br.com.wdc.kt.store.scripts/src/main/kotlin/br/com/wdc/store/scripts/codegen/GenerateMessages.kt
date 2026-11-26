package br.com.wdc.store.scripts.codegen

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the WDC Store's UI texts, from `messages.<language>.json` in `:store-i18n` (a flat object: key → text, with
 * `{placeholders}`), as:
 *
 * - `StoreMessages.kt`, in `:store-i18n`: the languages ([Language]), each language's catalog, the keys
 *   (`MessageKeys`) and one typed accessor per key on `Messages`, with a parameter per placeholder;
 * - `messages.ts`, in the TypeScript remote client: the same, for TypeScript.
 *
 * The first language file is `en-US`, the default. Every language must have the same keys, and each key the same
 * placeholders ([check]). The results are versioned: run `./gradlew :store-scripts:generateMessages` after changing a
 * catalog, and commit what changes. `MessagesCatalogTest` fails while they are out of sync.
 */
object GenerateMessages {

    const val DEFAULT = "en-US"

    private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}")

    private const val HEADER = "Generated from br.com.wdc.kt.store.i18n/messages.<language>.json by ./gradlew :store-scripts:generateMessages: do not edit."

    @JvmStatic
    fun main(args: Array<String>) {
        val storeDir = Path.of(args.firstOrNull() ?: error("Give the store's folder (fontes/br.com.wdc.kt.store)"))
        for (output in generate(storeDir)) {
            Files.createDirectories(output.path.parent)
            Files.writeString(output.path, output.text)
            println("Messages: wrote ${storeDir.relativize(output.path)}")
        }
    }

    /** The catalogs, by language tag, the default first; each sorted by key. */
    fun catalogs(storeDir: Path): Map<String, Map<String, String>> {
        val dir = storeDir.resolve("br.com.wdc.kt.store.i18n")
        val files = Files.list(dir).use { list -> list.filter { it.fileName.toString().matches(Regex("messages\\..+\\.json")) }.toList() }
        val byTag = files.associate { file ->
            val tag = file.fileName.toString().removePrefix("messages.").removeSuffix(".json")
            val json = JsonParser.parseString(Files.readString(file)).asJsonObject
            tag to json.entrySet().associate { it.key to it.value.asString }.toSortedMap()
        }
        require(DEFAULT in byTag) { "messages.$DEFAULT.json is missing" }
        return linkedMapOf(DEFAULT to byTag.getValue(DEFAULT)) + byTag.filterKeys { it != DEFAULT }.toSortedMap()
    }

    /** What is wrong with the catalogs: a key missing or extra in a language, or with other placeholders. */
    fun check(catalogs: Map<String, Map<String, String>>): List<String> {
        val reference = catalogs.getValue(DEFAULT)
        val problems = ArrayList<String>()
        for ((tag, catalog) in catalogs) {
            if (tag == DEFAULT) continue
            (reference.keys - catalog.keys).forEach { problems += "$tag: missing $it" }
            (catalog.keys - reference.keys).forEach { problems += "$tag: $it is not in $DEFAULT" }
            for ((key, text) in catalog) {
                val expected = reference[key]?.let(::placeholders) ?: continue
                if (placeholders(text) != expected) problems += "$tag: $key has ${placeholders(text)}, $DEFAULT has $expected"
            }
        }
        return problems
    }

    fun generate(storeDir: Path): List<GenerateDesignTokens.Output> {
        val catalogs = catalogs(storeDir)
        val problems = check(catalogs)
        require(problems.isEmpty()) { "The catalogs disagree:\n" + problems.joinToString("\n") }
        return listOf(
            GenerateDesignTokens.Output(
                storeDir.resolve("br.com.wdc.kt.store.i18n/src/commonMain/kotlin/br/com/wdc/store/i18n/StoreMessages.kt"),
                kotlin(catalogs),
            ),
            GenerateDesignTokens.Output(
                storeDir.resolve("br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript/src/scripts/messages.ts"),
                typescript(catalogs),
            ),
        )
    }

    private fun placeholders(text: String): Set<String> = PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toSet()

    /** `cart.purchaseNumber` → `cartPurchaseNumber`. */
    private fun accessor(key: String): String =
        key.split('.').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar { it.uppercase() } }.joinToString("")

    /** `en-US` → `EnUs`. */
    private fun enumName(tag: String): String =
        tag.split('-', '_').joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

    private fun kotlinString(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$").replace("\n", "\\n") + "\""

    private fun tsString(text: String): String =
        "'" + text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

    // :: Kotlin

    private fun kotlin(catalogs: Map<String, Map<String, String>>): String = buildString {
        val reference = catalogs.getValue(DEFAULT)
        appendLine("// $HEADER")
        appendLine("package br.com.wdc.store.i18n")
        appendLine()
        appendLine("/** The languages of the catalogs. [Default] is ${DEFAULT}. */")
        appendLine("enum class Language(val tag: String) {")
        for (tag in catalogs.keys) appendLine("    ${enumName(tag)}(\"$tag\"),")
        appendLine("    ;")
        appendLine()
        appendLine("    companion object {")
        appendLine("        val Default: Language = ${enumName(DEFAULT)}")
        appendLine()
        appendLine("        /** The language of a platform's tag (`pt-BR`, `pt_BR`, `pt`): the same tag, else the same language, else [Default]. */")
        appendLine("        fun of(tag: String?): Language {")
        appendLine("            val wanted = tag?.replace('_', '-')?.lowercase() ?: return Default")
        appendLine("            return entries.firstOrNull { it.tag.lowercase() == wanted }")
        appendLine("                ?: entries.firstOrNull { it.tag.substringBefore('-').lowercase() == wanted.substringBefore('-') }")
        appendLine("                ?: Default")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("/** The keys of the catalogs. */")
        appendLine("object MessageKeys {")
        for (key in reference.keys) appendLine("    const val ${accessor(key).replaceFirstChar { it.uppercase() }} = \"$key\"")
        appendLine("}")
        for (key in reference.keys) {
            val params = placeholders(reference.getValue(key)).toList()
            appendLine()
            appendLine("/** ${reference.getValue(key).replace("*/", "* /")} */")
            val signature = params.joinToString(", ") { "$it: Any?" }
            val args = params.joinToString(", ") { "\"$it\" to $it" }
            val call = if (params.isEmpty()) "text(MessageKeys.${accessor(key).replaceFirstChar { it.uppercase() }})"
            else "text(MessageKeys.${accessor(key).replaceFirstChar { it.uppercase() }}, $args)"
            appendLine("fun Messages.${accessor(key)}($signature): String = $call")
        }
        appendLine()
        appendLine("internal fun catalogOf(language: Language): Map<String, String> = when (language) {")
        for (tag in catalogs.keys) appendLine("    Language.${enumName(tag)} -> ${enumName(tag)}Catalog")
        appendLine("}")
        for ((tag, catalog) in catalogs) {
            appendLine()
            appendLine("private val ${enumName(tag)}Catalog: Map<String, String> = mapOf(")
            for ((key, text) in catalog) appendLine("    \"$key\" to ${kotlinString(text)},")
            appendLine(")")
        }
    }

    // :: TypeScript

    private fun typescript(catalogs: Map<String, Map<String, String>>): String = buildString {
        val reference = catalogs.getValue(DEFAULT)
        appendLine("// $HEADER")
        appendLine()
        appendLine("/** The languages of the catalogs; the first is the default. */")
        appendLine("export const LANGUAGES = [${catalogs.keys.joinToString(", ") { "'$it'" }}] as const")
        appendLine("export type Language = (typeof LANGUAGES)[number]")
        appendLine("export const DEFAULT_LANGUAGE: Language = '$DEFAULT'")
        appendLine()
        appendLine("export type MessageKey =")
        for (key in reference.keys) appendLine("  | '$key'")
        appendLine()
        appendLine("const CATALOGS: Record<Language, Record<MessageKey, string>> = {")
        for ((tag, catalog) in catalogs) {
            appendLine("  '$tag': {")
            for ((key, text) in catalog) appendLine("    '$key': ${tsString(text)},")
            appendLine("  },")
        }
        appendLine("}")
        appendLine()
        appendLine("/** The language of a browser's tag (`pt-BR`, `pt`): the same tag, else the same language, else the default. */")
        appendLine("export function languageOf(tag: string | null | undefined): Language {")
        appendLine("  if (!tag) return DEFAULT_LANGUAGE")
        appendLine("  const wanted = tag.replace('_', '-').toLowerCase()")
        appendLine("  return (")
        appendLine("    LANGUAGES.find((it) => it.toLowerCase() === wanted) ??")
        appendLine("    LANGUAGES.find((it) => it.split('-')[0].toLowerCase() === wanted.split('-')[0]) ??")
        appendLine("    DEFAULT_LANGUAGE")
        appendLine("  )")
        appendLine("}")
        appendLine()
        appendLine("/** The UI texts in one language: each key's text, with its `{placeholders}` filled. */")
        appendLine("export class Messages {")
        appendLine("  constructor(readonly language: Language) {}")
        appendLine()
        appendLine("  text(key: MessageKey | string, args: Record<string, unknown> = {}): string {")
        appendLine("    const pattern = (CATALOGS[this.language] as Record<string, string>)[key] ?? (CATALOGS[DEFAULT_LANGUAGE] as Record<string, string>)[key] ?? key")
        appendLine("    return pattern.replace(/\\{([A-Za-z0-9_]+)\\}/g, (match, name: string) => (name in args ? String(args[name]) : match))")
        appendLine("  }")
        appendLine()
        appendLine("  /** A civil date in this language's format (`format.date`). */")
        appendLine("  date(year: number, month: number, day: number): string {")
        appendLine("    const two = (n: number) => String(n).padStart(2, '0')")
        appendLine("    return this.text('format.date', { year, month: two(month), day: two(day) })")
        appendLine("  }")
        appendLine("}")
    }
}
