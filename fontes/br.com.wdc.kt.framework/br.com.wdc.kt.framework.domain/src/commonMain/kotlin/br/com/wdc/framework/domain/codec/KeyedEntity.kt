package br.com.wdc.framework.domain.codec

/**
 * Contrato para entidades que possuem uma chave de identidade.
 *
 * Permite que a serialização detecte referências repetidas no grafo de objetos e as substitua por stubs
 * contendo apenas a chave, evitando recursão infinita.
 */
interface KeyedEntity {

    /** A chave de identidade (tipicamente o id). Pode ser `null` para entidades ainda não persistidas. */
    fun key(): Any?
}
