package br.com.wdc.framework.domain.criteria

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.exception.InvalidRequestException
import kotlin.time.Instant

/** How to write a value of the field's type. */
typealias CriterionValueWriter<T> = (out: ExtensibleObjectOutput, value: T) -> Unit

/** How to read a value of the field's type. */
typealias CriterionValueReader<T> = (input: ExtensibleObjectInput) -> T?

/**
 * How a criterion field travels.
 *
 * Written once, here, instead of repeated in each entity codec: what changes from field to field is the
 * value type, not the shape. Each codec only says which value reader/writer to use.
 *
 * The shape is an object with the requests — **it is a contract with the other clients; don't change it**:
 * ```
 * "price": { "or": true, "p": [ { "o": "GE", "v": [10.0] }, { "o": "IS_NULL" } ] }
 * ```
 *
 * **The bare value wouldn't be enough.** A field carries several requests, each with its operator and its
 * arity, and the disjunction belongs to the field, not to the request. Reducing that to `"price": 10.0` would
 * discard everything but equality, and silently: the other side would receive a looser filter than requested.
 */
object CriterionCodec {

    val LONG_OUT: CriterionValueWriter<Long> = { out, v -> out.value(v) }
    val LONG_IN: CriterionValueReader<Long> = { input -> InputCoerceUtils.asLong(input) }

    val INT_OUT: CriterionValueWriter<Int> = { out, v -> out.value(v.toLong()) }
    val INT_IN: CriterionValueReader<Int> = { input -> InputCoerceUtils.asInteger(input) }

    val DOUBLE_OUT: CriterionValueWriter<Double> = { out, v -> out.value(v) }
    val DOUBLE_IN: CriterionValueReader<Double> = { input -> InputCoerceUtils.asDouble(input) }

    val STRING_OUT: CriterionValueWriter<String> = { out, v -> out.value(v) }
    val STRING_IN: CriterionValueReader<String> = { input -> InputCoerceUtils.asString(input) }

    val BOOL_OUT: CriterionValueWriter<Boolean> = { out, v -> out.value(v) }
    val BOOL_IN: CriterionValueReader<Boolean> = { input -> InputCoerceUtils.asBoolean(input) }

    /**
     * Instant as ISO-8601 text. Writes in UTC (`…Z`); also reads with offset (`…+03:00`), which is how Java
     * clients send it.
     */
    val INSTANT_OUT: CriterionValueWriter<Instant> = { out, v -> out.value(v.toString()) }
    val INSTANT_IN: CriterionValueReader<Instant> = { input -> InputCoerceUtils.asString(input)?.let(Instant::parse) }

    /**
     * Reads the ordering name, rejecting what it doesn't recognize.
     *
     * **Unlike an unknown operator, which is discarded**, here reading fails. The difference is what each
     * discard would produce: without a request, the filter gets looser and the field is still listed — the
     * mismatch shows; without the ordering, the list comes back in some arbitrary order, and whoever displays
     * it presents it as if it were the requested order.
     *
     * Takes the accepted values (`XxxCriteria.OrderBy.entries`) instead of the class: no reflection.
     *
     * @throws InvalidRequestException if the name is not among the accepted ones — the REST layer returns 400,
     *         naming the received value and the accepted ones
     */
    fun <E : Enum<E>> readOrderBy(input: ExtensibleObjectInput, accepted: List<E>): E? {
        val name = InputCoerceUtils.asString(input) ?: return null
        return accepted.firstOrNull { it.name == name }
            ?: throw InvalidRequestException(
                "unknown ordering: '$name' — accepted: ${accepted.joinToString(", ") { it.name }}"
            )
    }

    /** The name the alternative groups ([Criteria.anyOf]) would have on the wire, where they are refused. */
    const val ANY_OF = "anyOf"

    /**
     * Refuses to write a criteria that has alternative groups ([Criteria.anyOf]): they are for code on the server, and
     * do not travel.
     *
     * A disjunction across fields chosen by the client would let it send any tree of `OR`s over any columns — the
     * queries the provisioned orderings exist to avoid. Leaving the groups out instead would send a looser filter than
     * the one built, with nothing pointing out the loss; hence the failure, on the side that built it. A search of one
     * term over several columns is a criteria field of its own, which the repository translates with
     * `CriterionTranslator.translateAny`.
     *
     * @throws InvalidRequestException if the criteria has groups
     */
    fun refuseAnyOf(criteria: Criteria?) {
        if (criteria != null && criteria.anyOf.isNotEmpty()) {
            throw anyOfDoesNotTravel()
        }
    }

    /**
     * The failure for alternative groups on the wire — thrown by the reader that meets an [ANY_OF] key, which the REST
     * layer returns as 400 instead of skipping it and answering with a looser filter.
     */
    fun anyOfDoesNotTravel(): InvalidRequestException = InvalidRequestException(
        "alternative groups (anyOf) are server-side only and do not travel; search several columns with a " +
            "criteria field of its own",
    )

    /**
     * Writes the field, if there is something to write. A field not provided produces no key at all — this
     * keeps short the criteria that filters by a single field, the common case.
     */
    fun <T : Any> write(
        out: ExtensibleObjectOutput,
        name: String,
        criterion: Criterion<*, T>?,
        valueWriter: CriterionValueWriter<T>,
    ) {
        if (criterion == null || !criterion.isSet()) {
            return
        }
        out.name(name).beginObject()
        if (criterion.disjunctive) {
            out.name("or").value(true)
        }
        out.name("p").beginArray()
        for (predicate in criterion.predicates) {
            out.beginObject()
            out.name("o").value(predicate.operator.name)
            if (predicate.values.isNotEmpty()) {
                out.name("v").beginArray()
                for (value in predicate.values) {
                    if (value == null) out.nullValue() else valueWriter(out, value)
                }
                out.endArray()
            }
            out.endObject()
        }
        out.endArray()
        out.endObject()
    }

    /**
     * Reads the field in the shape above, restoring the requests into [criterion].
     *
     * Restores through [Criterion.restore], not through the fluent methods: `eq(null)` adds no request — right
     * for whoever builds a screen filter — and would silently erase what the other side sent.
     *
     * **Also accepts the bare value** (`"price": 10.0`), reading it as equality — the minimal shape of an
     * equality filter, and the one clients older than this format send. A bare `null` is discarded.
     *
     * **A value that doesn't read is refused** ([InvalidRequestException], 400 in the API): only the JSON `null` is a
     * null operand. A token the reader turns into `null` — `""` for a number — would otherwise become `IS NULL`/
     * `IS NOT NULL`, and `NE [""]` in a `delete` would wipe the table.
     */
    fun <T : Any> read(input: ExtensibleObjectInput, criterion: Criterion<*, T>, valueReader: CriterionValueReader<T>) {
        if (input.peek() != SerializationToken.BEGIN_OBJECT) {
            if (input.peek() == SerializationToken.NULL) {
                input.nextNull<Any>()
                return
            }
            criterion.restore(Operator.EQ, listOf(readValue(input, criterion, valueReader)))
            return
        }
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "or" -> criterion.restoreDisjunctive(InputCoerceUtils.asBoolean(input) == true)
                "p" -> readPredicates(input, criterion, valueReader)
                else -> input.skipValue()
            }
        }
        input.endObject()
    }

    private fun <T : Any> readPredicates(
        input: ExtensibleObjectInput,
        criterion: Criterion<*, T>,
        valueReader: CriterionValueReader<T>,
    ) {
        input.beginArray()
        while (input.hasNext()) {
            var operator: Operator? = null
            val values = ArrayList<T?>(2)
            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "o" -> operator = parseOperator(InputCoerceUtils.asString(input))
                    "v" -> {
                        input.beginArray()
                        while (input.hasNext()) {
                            values.add(
                                if (input.peek() == SerializationToken.NULL) input.nextNull()
                                else readValue(input, criterion, valueReader)
                            )
                        }
                        input.endArray()
                    }
                    else -> input.skipValue()
                }
            }
            input.endObject()
            if (operator != null) {
                criterion.restore(operator, values)
            }
        }
        input.endArray()
    }

    /**
     * Reads a slice number of a request (`limit`, `offset`, `page`, `pageSize`): a JSON integer. Anything else —
     * `null`, text (`"10"`), a boolean, a fraction — is refused ([InvalidRequestException], 400 in the API): a value
     * of the wrong type is refused, not interpreted, as the C# version does. A number not given is left out.
     */
    fun readSliceNumber(input: ExtensibleObjectInput, name: String): Int = when (input.peek()) {
        SerializationToken.NUMBER -> try {
            input.nextInt()
        } catch (_: NumberFormatException) {
            throw InvalidRequestException("'$name' must be an integer")
        }
        else -> throw InvalidRequestException("'$name' must be an integer")
    }

    /** Reads a value that is not the JSON `null`; a reader that makes nothing of it means a malformed request. */
    private fun <T : Any> readValue(
        input: ExtensibleObjectInput,
        criterion: Criterion<*, T>,
        valueReader: CriterionValueReader<T>,
    ): T = valueReader(input)
        ?: throw InvalidRequestException("criterion '${criterion.name}': a value that is not null doesn't read as one")

    /**
     * An unknown operator is discarded, not translated by approximation.
     *
     * It happens when one side is newer than the other and sends an operator this one doesn't know yet.
     * Picking the "closest" one would produce a filter different from the requested one, flagging nothing;
     * leaving the request out produces a looser filter — but the field is still listed, and the mismatch
     * shows.
     */
    private fun parseOperator(name: String?): Operator? =
        if (name == null) null else Operator.entries.firstOrNull { it.name == name }
}
