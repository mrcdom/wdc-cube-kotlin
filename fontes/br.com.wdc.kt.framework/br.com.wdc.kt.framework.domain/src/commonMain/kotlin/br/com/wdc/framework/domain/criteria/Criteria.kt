package br.com.wdc.framework.domain.criteria

/**
 * Contrato comum a todo `XxxCriteria`: expõe os campos filtráveis como uma lista.
 *
 * Existe para que a tradução do critério em condição seja escrita **uma vez**: o núcleo percorre os
 * campos informados e o que resta a cada entidade é dizer qual coluna corresponde a cada nome.
 */
interface Criteria {

    /**
     * Os campos do critério, na ordem de declaração — que é a ordem em que as condições entram no `AND`.
     * Traz todos, informados ou não; distinguir é com [Criterion.isSet].
     */
    fun criterions(): List<Criterion<*, *>>
}
