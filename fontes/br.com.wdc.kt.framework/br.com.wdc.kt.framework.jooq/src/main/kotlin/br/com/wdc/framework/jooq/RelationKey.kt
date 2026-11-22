package br.com.wdc.framework.jooq

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Field
import org.jooq.Table

/**
 * The match between the key of an associated entity and the column that holds it on this side.
 *
 * Serves a shortcut: when the association's projection asks for **only the key**, the value is already in the
 * current row — it is the foreign key column. Fetching it from the other side would be a subquery to find out
 * what is already known.
 *
 * **The method names are those of [JsonQueryBuilder] on purpose**: the key is declared from the same column
 * type the scalar field would be declared with, which prevents picking one type here and another there.
 *
 * @param T the table on this side, where the foreign key column lives
 */
class RelationKey<T : Table<*>> internal constructor() {

    private class Part<T>(val name: String, val column: (T) -> Field<*>, val type: JsonFieldType)

    private val parts = LinkedHashMap<String, Part<T>>()

    /** `Long` field of the child's key, stored in this column. */
    fun addI64(fn: String, column: (T) -> Field<out Number?>) = add(fn, column, JsonFieldType.NUMBER)

    /** `Int` field of the child's key, stored in this column. */
    fun addI32(fn: String, column: (T) -> Field<out Number?>) = add(fn, column, JsonFieldType.NUMBER)

    /** `Double` field of the child's key, stored in this column. */
    fun addF64(fn: String, column: (T) -> Field<out Number?>) = add(fn, column, JsonFieldType.NUMBER)

    /** `BigDecimal` field of the child's key, stored in this column. */
    fun addDec(fn: String, column: (T) -> Field<out BigDecimal?>) = add(fn, column, JsonFieldType.NUMBER)

    /** `String` field of the child's key, stored in this column. */
    fun addStr(fn: String, column: (T) -> Field<out String?>) = add(fn, column, JsonFieldType.STRING)

    /** Enum field of the child's key, stored in this column as text. */
    fun addEnm(fn: String, column: (T) -> Field<out String?>) = add(fn, column, JsonFieldType.STRING)

    /** `Boolean` field of the child's key, stored in this column. */
    fun addBit(fn: String, column: (T) -> Field<out Boolean?>) = add(fn, column, JsonFieldType.BOOLEAN)

    /** Date/time with time zone field of the child's key, stored in this column. */
    fun addOdt(fn: String, column: (T) -> Field<out OffsetDateTime?>) = add(fn, column, JsonFieldType.DATETIME)

    /** Date/time without time zone field of the child's key, stored in this column. */
    fun addLdt(fn: String, column: (T) -> Field<out LocalDateTime?>) = add(fn, column, JsonFieldType.DATETIME)

    /** Day (DATE) field of the child's key, stored in this column. */
    fun addDay(fn: String, column: (T) -> Field<out LocalDate?>) = add(fn, column, JsonFieldType.DATETIME)

    /** [UUID] field of the child's key, stored in this column. */
    fun addUid(fn: String, column: (T) -> Field<out UUID?>) = add(fn, column, JsonFieldType.STRING)

    /** `ByteArray` field of the child's key, stored in this column. */
    fun addBin(fn: String, column: (T) -> Field<out ByteArray?>) = add(fn, column, JsonFieldType.BINARY)

    private fun add(fn: String, column: (T) -> Field<*>, type: JsonFieldType): RelationKey<T> {
        parts[fn] = Part(fn, column, type)
        return this
    }

    /** Each part: the name in the child's JSON and this table's column. */
    internal fun columns(): List<Pair<String, (T) -> Field<*>>> = parts.values.map { it.name to it.column }

    /** The fields this key covers — they are what the projection is checked against for asking more. */
    internal fun names(): Set<String> = parts.keys

    internal fun isEmpty(): Boolean = parts.isEmpty()

    /** The key object, built from this row's columns. */
    internal fun entries(table: T): List<JsonFieldEntry> =
        parts.values.map { JsonFieldEntry(it.name, it.column(table), it.type) }
}
