package br.com.wdc.framework.domain

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/** Critério de exemplo: um campo de cada família. */
class SampleCriteria : Criteria {
    val id = ComparableCriterion<SampleCriteria, Long>(this, "id")
    val price = ComparableCriterion<SampleCriteria, Double>(this, "price")
    val name = TextCriterion(this, "name")
    val active = Criterion<SampleCriteria, Boolean>(this, "active")

    var orderBy: OrderBy? = null

    override fun criterions(): List<Criterion<*, *>> = listOf(id, price, name, active)

    enum class OrderBy { OLDEST_FIRST, NAME_A_TO_Z }
}

/** Escreve um objeto JSON com o que [block] puser dentro. */
fun jsonObject(block: (ExtensibleObjectOutput) -> Unit): String {
    val writer = JsonStreamWriter()
    writer.beginObject()
    block(writer)
    writer.endObject()
    return writer.result()
}

/** Posiciona um leitor no valor do único campo de `{"<field>": <value>}`. */
fun readerAtField(json: String): ExtensibleObjectInput {
    val reader = JsonStreamReader(json)
    reader.beginObject()
    reader.nextName()
    return reader
}
