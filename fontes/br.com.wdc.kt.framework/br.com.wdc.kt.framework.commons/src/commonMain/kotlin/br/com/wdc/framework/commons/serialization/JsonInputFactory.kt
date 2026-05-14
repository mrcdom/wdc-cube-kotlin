package br.com.wdc.framework.commons.serialization

/**
 * Result of creating a JSON string reader.
 * Wraps an [ExtensibleObjectInput] backed by a parsed JSON string.
 */
class JsonStringInput(
    val input: ExtensibleObjectInput,
)

/**
 * Factory for creating [ExtensibleObjectInput] instances from a JSON string.
 * Must be initialized per platform before use.
 */
object JsonInputFactory {

    private var factory: ((String) -> JsonStringInput)? = null

    fun install(factory: (String) -> JsonStringInput) {
        this.factory = factory
    }

    fun createStringInput(json: String): JsonStringInput {
        return (factory ?: throw IllegalStateException("JsonInputFactory not installed"))
            .invoke(json)
    }
}

/**
 * Instala a implementação streaming em Kotlin puro — funciona em todas as plataformas.
 */
fun JsonInputFactory.installCommon() {
    install { json -> JsonStringInput(JsonStreamReader(json)) }
}
