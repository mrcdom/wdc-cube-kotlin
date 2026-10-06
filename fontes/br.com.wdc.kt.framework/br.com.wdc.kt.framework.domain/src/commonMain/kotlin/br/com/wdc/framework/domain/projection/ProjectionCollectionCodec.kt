package br.com.wdc.framework.domain.projection

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.exception.InvalidRequestException

/**
 * Como uma **coleção projetada** de relação 1:N trafega.
 *
 * Uma coleção de projeção não é uma lista de resultados: é **uma** forma de item mais o que fazer com a
 * coleção — o critério que a filtra ([HasCriteria]) e o recorte a aplicar ([HasSlice]). Esses dados precisam
 * chegar ao outro lado, ou a coleção volta inteira e desordenada.
 *
 * **A forma no fio distingue projeção de resultado por si só.** A projeção é um objeto; o resultado, o
 * array de sempre:
 * ```
 * // projeção (cliente → servidor)
 * "items": { "shape": {…um item…}, "where": {…critério…}, "limit": 5, "offset": 0 }
 *
 * // resultado (servidor → cliente)
 * "items": [ {…}, {…}, {…} ]
 * ```
 * O leitor decide pelo token: objeto é projeção, array é resultado. Assim o mesmo campo serve os dois
 * sentidos sem um segundo nome.
 *
 * O critério embutido usa a mesma estrutura do critério de topo — é o codec da entidade filha quem o
 * escreve e lê, através dos callbacks.
 */
object ProjectionCollectionCodec {

    /**
     * Escreve a coleção projetada como envelope `{ shape, where?, limit?, offset? }`.
     *
     * Só a forma é obrigatória — é o primeiro elemento da coleção. Critério e recorte entram quando a
     * coleção os carrega; ausentes, a chave nem aparece.
     *
     * @param shapeWriter    escreve a forma de um item
     * @param criteriaWriter escreve o critério embutido — objeto completo, `begin/endObject` inclusos
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

    /** Se o valor corrente do campo é uma coleção projetada (objeto), e não uma lista de resultados (array). */
    fun isProjectionEnvelope(input: ExtensibleObjectInput): Boolean = input.peek() == SerializationToken.BEGIN_OBJECT

    /**
     * Lê o envelope e devolve a coleção de projeção pronta — forma, critério e recorte reidratados.
     *
     * @param shapeReader    lê a forma de um item
     * @param criteriaReader lê o critério embutido — objeto completo, `begin/endObject` inclusos
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
                "limit" -> limit = InputCoerceUtils.asInteger(input)
                "offset" -> offset = InputCoerceUtils.asInteger(input)
                else -> input.skipValue()
            }
        }
        input.endObject()
        if (!hasShape) {
            throw InvalidRequestException("coleção projetada sem 'shape'")
        }
        @Suppress("UNCHECKED_CAST")
        return ProjectionList(shape as E, criteria).withLimit(limit).withOffset(offset)
    }
}
