package br.com.wdc.framework.jooq

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.jooq.Field
import org.jooq.Table

/**
 * A correspondência entre a chave de uma entidade associada e a coluna que a guarda deste lado.
 *
 * Serve a um atalho: quando a projeção da associação pede **apenas a chave**, o valor já está na linha
 * corrente — é a coluna da chave estrangeira. Ir buscá-lo do outro lado seria uma subconsulta para descobrir
 * o que já se sabe.
 *
 * **Os nomes dos métodos são os do [JsonQueryBuilder] de propósito**: a chave é declarada a partir do mesmo
 * tipo de coluna com que se declararia o campo escalar, o que impede escolher um tipo aqui e outro lá.
 *
 * @param T a tabela deste lado, onde mora a coluna da chave estrangeira
 */
class RelationKey<T : Table<*>> internal constructor() {

    private class Part<T>(val name: String, val column: (T) -> Field<*>, val type: JsonFieldType)

    private val parts = LinkedHashMap<String, Part<T>>()

    /** Campo `Long` da chave do filho, guardado nesta coluna. */
    fun addI64(fn: String, column: (T) -> Field<out Number?>) = add(fn, column, JsonFieldType.NUMBER)

    /** Campo `Int` da chave do filho, guardado nesta coluna. */
    fun addI32(fn: String, column: (T) -> Field<out Number?>) = add(fn, column, JsonFieldType.NUMBER)

    /** Campo `Double` da chave do filho, guardado nesta coluna. */
    fun addF64(fn: String, column: (T) -> Field<out Number?>) = add(fn, column, JsonFieldType.NUMBER)

    /** Campo `BigDecimal` da chave do filho, guardado nesta coluna. */
    fun addDec(fn: String, column: (T) -> Field<out BigDecimal?>) = add(fn, column, JsonFieldType.NUMBER)

    /** Campo `String` da chave do filho, guardado nesta coluna. */
    fun addStr(fn: String, column: (T) -> Field<out String?>) = add(fn, column, JsonFieldType.STRING)

    /** Campo enum da chave do filho, guardado nesta coluna como texto. */
    fun addEnm(fn: String, column: (T) -> Field<out String?>) = add(fn, column, JsonFieldType.STRING)

    /** Campo `Boolean` da chave do filho, guardado nesta coluna. */
    fun addBit(fn: String, column: (T) -> Field<out Boolean?>) = add(fn, column, JsonFieldType.BOOLEAN)

    /** Campo de data/hora com fuso da chave do filho, guardado nesta coluna. */
    fun addOdt(fn: String, column: (T) -> Field<out OffsetDateTime?>) = add(fn, column, JsonFieldType.DATETIME)

    /** Campo de data/hora sem fuso da chave do filho, guardado nesta coluna. */
    fun addLdt(fn: String, column: (T) -> Field<out LocalDateTime?>) = add(fn, column, JsonFieldType.DATETIME)

    /** Campo `ByteArray` da chave do filho, guardado nesta coluna. */
    fun addBin(fn: String, column: (T) -> Field<out ByteArray?>) = add(fn, column, JsonFieldType.BINARY)

    private fun add(fn: String, column: (T) -> Field<*>, type: JsonFieldType): RelationKey<T> {
        parts[fn] = Part(fn, column, type)
        return this
    }

    /** Os campos que esta chave cobre — é contra eles que se pergunta se a projeção pediu mais. */
    internal fun names(): Set<String> = parts.keys

    internal fun isEmpty(): Boolean = parts.isEmpty()

    /** O objeto da chave, montado com as colunas desta linha. */
    internal fun entries(table: T): List<JsonFieldEntry> =
        parts.values.map { JsonFieldEntry(it.name, it.column(table), it.type) }
}
