package br.com.wdc.framework.domain.codec

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput

/**
 * Contrato de serialização de entidades e critérios de um repositório.
 *
 * Usa a API de streaming do `framework-commons`, sem reflexão — o mesmo codec serve ao cliente e ao servidor,
 * em todos os alvos.
 *
 * @param E tipo da entidade
 * @param C tipo do critério de pesquisa
 */
interface ModelCodec<E, C> {

    /** Resultado da leitura de uma entidade para update: a entidade e a projeção (campos presentes no JSON). */
    data class UpdateData<T>(val entity: T, val projection: T)

    /** Escreve a entidade como um objeto JSON completo (`beginObject` + campos + `endObject`). */
    fun writeEntity(out: ExtensibleObjectOutput, entity: E) = writeEntity(out, entity, EntityGraph())

    /**
     * Escreve a entidade com rastreamento de grafo — a entidade já vista é escrita só com a chave.
     * Implementações usam [EntityGraph.track] antes de serializar campos aninhados.
     */
    fun writeEntity(out: ExtensibleObjectOutput, entity: E, graph: EntityGraph)

    /**
     * Escreve apenas os campos indicados pela projeção (não-nulos nela). O id é sempre incluído; campo
     * marcado cujo valor é `null` sai como `null` explícito.
     */
    fun writeEntityProjected(out: ExtensibleObjectOutput, entity: E, projection: E)

    /** Projeção com sentinela em cada campo que difere entre [newEntity] e [oldEntity]. */
    fun computeProjection(newEntity: E, oldEntity: E): E

    /** Lê uma entidade a partir da posição corrente (espera `beginObject`). Nome desconhecido é pulado. */
    fun readEntity(input: ExtensibleObjectInput): E

    /**
     * Lê uma entidade para update, devolvendo também a projeção: todo campo presente no JSON — inclusive os
     * `null` — fica marcado nela. É o que permite atualizar um campo para `null` explicitamente.
     */
    fun readEntityForUpdate(input: ExtensibleObjectInput): UpdateData<E>

    /** Lê uma lista de entidades (espera `beginArray`). */
    fun readEntityList(input: ExtensibleObjectInput): List<E> {
        val list = ArrayList<E>()
        input.beginArray()
        while (input.hasNext()) {
            list.add(readEntity(input))
        }
        input.endArray()
        return list
    }

    /** Escreve os campos do critério no objeto corrente (sem `begin/endObject`). */
    fun writeCriteriaFields(out: ExtensibleObjectOutput, criteria: C)

    /**
     * Lê **um** campo do critério, já posicionado após o nome. Devolve `true` se o campo foi consumido;
     * `false` se não é campo de critério reconhecido (quem chama faz `skipValue`).
     */
    fun readCriteriaField(input: ExtensibleObjectInput, fieldName: String, criteria: C): Boolean

    /** A projeção associada ao critério (pode ser `null`). */
    fun getProjection(criteria: C): E?

    /** Define na entidade o id gerado pelo servidor, após o insert. */
    fun setGeneratedId(entity: E, id: Long)
}
