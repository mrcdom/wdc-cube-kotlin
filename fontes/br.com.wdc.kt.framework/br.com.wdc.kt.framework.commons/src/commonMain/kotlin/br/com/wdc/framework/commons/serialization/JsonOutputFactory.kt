package br.com.wdc.framework.commons.serialization

/**
 * Result of creating a JSON string writer.
 * Use [output] to write JSON structure, then call [resultString] to get the JSON string.
 */
class JsonStringOutput(
    val output: ExtensibleObjectOutput,
    val resultString: () -> String,
)

/**
 * Factory for creating [ExtensibleObjectOutput] instances that write to a String.
 * Must be initialized per platform before use.
 */
object JsonOutputFactory {

    private var factory: (() -> JsonStringOutput)? = null

    fun install(factory: () -> JsonStringOutput) {
        this.factory = factory
    }

    fun createStringOutput(): JsonStringOutput {
        return (factory ?: throw IllegalStateException("JsonOutputFactory not installed"))()
    }
}
