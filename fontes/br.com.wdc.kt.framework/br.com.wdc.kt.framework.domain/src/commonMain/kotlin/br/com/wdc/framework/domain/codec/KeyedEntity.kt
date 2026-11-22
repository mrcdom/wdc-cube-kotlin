package br.com.wdc.framework.domain.codec

/**
 * Contract for entities that have an identity key.
 *
 * Lets serialization detect repeated references in the object graph and replace them with stubs containing
 * only the key, avoiding infinite recursion.
 */
interface KeyedEntity {

    /** The identity key (typically the id). May be `null` for entities not yet persisted. */
    fun key(): Any?
}

/**
 * An entity with the key only — the reference for writing an association (the foreign key) or for marking it
 * in a projection. `null` in, `null` out.
 *
 * ```
 * item.purchase = ref(::Purchase, purchaseId) { id = it }
 * ```
 *
 * @param factory creates the empty entity (usually, the constructor)
 * @param key the key, or `null`
 * @param setKey writes the key into the created entity
 */
inline fun <E, K : Any> ref(factory: () -> E, key: K?, setKey: E.(K) -> Unit): E? =
    if (key == null) null else factory().apply { setKey(key) }
