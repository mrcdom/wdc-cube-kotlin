package br.com.wdc.framework.domain.codec

/**
 * Rastreia entidades já processadas numa operação de serialização, usando **identidade de instância**
 * (`===`) para detectar referências cíclicas.
 *
 * Duas instâncias distintas com a mesma chave são independentes — só a mesma instância é detectada como
 * repetição. Quando detectada, o codec escreve um stub (só a chave).
 *
 * Escopo: uma instância por operação de topo (um `writeEntity` raiz).
 * ```
 * val graph = EntityGraph()
 * graph.track(purchase)        // primeira vez → true
 * graph.track(purchase)        // mesma instância → false
 *
 * val other = Purchase().apply { id = purchase.id }
 * graph.track(other)           // outra instância, mesma chave → true
 * ```
 */
class EntityGraph {

    // Não há IdentityHashMap em commonMain: agrupa pela chave (estável durante a operação) e compara por ===.
    private val seen = HashMap<Any?, MutableList<KeyedEntity>>()

    /**
     * Rastreia a entidade. Devolve `true` se é a primeira ocorrência, `false` se esta mesma instância já foi
     * rastreada (ciclo — use um stub com [KeyedEntity.key]). `null` devolve sempre `true`.
     */
    fun track(entity: KeyedEntity?): Boolean {
        if (entity == null) return true
        val bucket = seen.getOrPut(entity.key()) { ArrayList(1) }
        if (bucket.any { it === entity }) return false
        bucket.add(entity)
        return true
    }

    /** Verifica se esta mesma instância já foi rastreada. */
    fun isSeen(entity: KeyedEntity?): Boolean {
        if (entity == null) return false
        return seen[entity.key()]?.any { it === entity } == true
    }
}
