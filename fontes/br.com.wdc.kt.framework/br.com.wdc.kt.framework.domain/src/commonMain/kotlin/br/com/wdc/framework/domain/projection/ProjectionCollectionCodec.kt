package br.com.wdc.framework.domain.projection

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.exception.InvalidRequestException

/**
 * How a **projected collection** of a 1:N relation travels.
 *
 * A projection collection is not a list of results: it is **one** item shape plus what to do with the
 * collection — the criteria that filters it ([HasCriteria]) and the slice to apply ([HasSlice]). This data must
 * reach the other side, or the collection comes back whole and unordered.
 *
 * **The shape on the wire tells projection from result by itself.** The projection is an object; the result, the
 * usual array:
 * ```
 * // projection (client → server)
 * "items": { "shape": {…one item…}, "where": {…criteria…}, "limit": 5, "offset": 0 }
 *
 * // result (server → client)
 * "items": [ {…}, {…}, {…} ]
 * ```
 * The reader decides by the token: object is projection, array is result. So the same field serves both
 * directions without a second name.
 *
 * The embedded criteria uses the same structure as the top-level criteria — the child entity's codec writes
 * and reads it, through the callbacks.
 */
object ProjectionCollectionCodec {

    /**
     * Writes the projected collection as an envelope `{ shape, where?, limit?, offset? }`.
     *
     * Only the shape is mandatory — it is the first element of the collection. Criteria and slice go in when the
     * collection carries them; when absent, the key doesn't even appear.
     *
     * @param shapeWriter    writes the shape of one item
     * @param criteriaWriter writes the embedded criteria — whole object, `begin/endObject` included
     */
    fun <E> write(
        out: ExtensibleObjectOutput,
        name: String,
        collection: Collection<E>,
        shapeWriter: (out: ExtensibleObjectOutput, item: E) -> Unit,
        criteriaWriter: ((out: ExtensibleObjectOutput, criteria: Any) -> Unit)?,
    ) {
        val shape = collection.first()
        out.name(name).beginObject()
        out.name("shape")
        shapeWriter(out, shape)
        val criteria = (collection as? HasCriteria)?.criteria
        if (criteria != null && criteriaWriter != null) {
            out.name("where")
            criteriaWriter(out, criteria)
        }
        if (collection is HasSlice) {
            collection.limit?.let { out.name("limit").value(it.toLong()) }
            collection.offset?.let { out.name("offset").value(it.toLong()) }
        }
        out.endObject()
    }

    /** Whether the field's current value is a projected collection (object), not a list of results (array). */
    fun isProjectionEnvelope(input: ExtensibleObjectInput): Boolean = input.peek() == SerializationToken.BEGIN_OBJECT

    /**
     * Reads the envelope and returns the ready projection collection — shape, criteria and slice rehydrated.
     * A negative `limit` or `offset` is refused ([InvalidRequestException], 400 in the API): the database would refuse
     * the query and answer 500.
     *
     * @param shapeReader    reads the shape of one item
     * @param criteriaReader reads the embedded criteria — whole object, `begin/endObject` included
     */
    fun <E> read(
        input: ExtensibleObjectInput,
        shapeReader: (input: ExtensibleObjectInput) -> E,
        criteriaReader: (input: ExtensibleObjectInput) -> Any?,
    ): ProjectionList<E> {
        var shape: E? = null
        var hasShape = false
        var criteria: Any? = null
        var limit: Int? = null
        var offset: Int? = null
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "shape" -> {
                    shape = shapeReader(input)
                    hasShape = true
                }
                "where" -> criteria = criteriaReader(input)
                "limit" -> limit = notNegative("limit", InputCoerceUtils.asInteger(input))
                "offset" -> offset = notNegative("offset", InputCoerceUtils.asInteger(input))
                else -> input.skipValue()
            }
        }
        input.endObject()
        if (!hasShape) {
            throw InvalidRequestException("projected collection without 'shape'")
        }
        @Suppress("UNCHECKED_CAST")
        return ProjectionList(shape as E, criteria, limit, offset)
    }

    private fun notNegative(name: String, value: Int?): Int? {
        if (value != null && value < 0) {
            throw InvalidRequestException("projected collection: '$name' can't be negative ($value)")
        }
        return value
    }
}
