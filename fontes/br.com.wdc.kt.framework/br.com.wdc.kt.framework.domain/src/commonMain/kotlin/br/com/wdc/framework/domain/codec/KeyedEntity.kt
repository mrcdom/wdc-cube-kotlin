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

/**
 * Uma entidade só com a chave — a referência para gravar uma associação (a chave estrangeira) ou para marcá-la
 * numa projeção. `null` entra, `null` sai.
 *
 * ```
 * item.purchase = ref(::Purchase, purchaseId) { id = it }
 * ```
 *
 * @param factory cria a entidade vazia (em geral, o construtor)
 * @param key a chave, ou `null`
 * @param setKey grava a chave na entidade criada
 */
inline fun <E, K : Any> ref(factory: () -> E, key: K?, setKey: E.(K) -> Unit): E? =
    if (key == null) null else factory().apply { setKey(key) }
