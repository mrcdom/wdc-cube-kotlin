package br.com.wdc.framework.domain

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/** Sample criteria: one field of each family. */
class SampleCriteria : Criteria {
    val id = ComparableCriterion<SampleCriteria, Long>(this, "id")
    val price = ComparableCriterion<SampleCriteria, Double>(this, "price")
    val name = TextCriterion(this, "name")
    val active = Criterion<SampleCriteria, Boolean>(this, "active")

    var orderBy: OrderBy? = null

    override fun criterions(): List<Criterion<*, *>> = listOf(id, price, name, active)

    /** The alternative groups, typed: a criteria that supports them narrows the interface's list to its own type. */
    override var anyOf: List<SampleCriteria> = emptyList()
        private set

    fun withAnyOf(vararg groups: SampleCriteria) = apply { anyOf = groups.toList() }

    enum class OrderBy { OLDEST_FIRST, NAME_A_TO_Z }
}

/** Writes a JSON object with whatever [block] puts inside. */
fun jsonObject(block: (ExtensibleObjectOutput) -> Unit): String {
    val writer = JsonStreamWriter()
    writer.beginObject()
    block(writer)
    writer.endObject()
    return writer.result()
}

/** Positions a reader on the value of the single field of `{"<field>": <value>}`. */
fun readerAtField(json: String): ExtensibleObjectInput {
    val reader = JsonStreamReader(json)
    reader.beginObject()
    reader.nextName()
    return reader
}
