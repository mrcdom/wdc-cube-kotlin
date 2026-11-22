package br.com.wdc.framework.domain.codec

/**
 * Tracks entities already processed in a serialization operation, using **instance identity** (`===`) to
 * detect cyclic references.
 *
 * Two distinct instances with the same key are independent — only the same instance is detected as a
 * repetition. When detected, the codec writes a stub (key only).
 *
 * Scope: one instance per top-level operation (a root `writeEntity`).
 * ```
 * val graph = EntityGraph()
 * graph.track(purchase)        // first time → true
 * graph.track(purchase)        // same instance → false
 *
 * val other = Purchase().apply { id = purchase.id }
 * graph.track(other)           // another instance, same key → true
 * ```
 */
class EntityGraph {

    // There is no IdentityHashMap in commonMain: groups by key (stable during the operation) and compares with ===.
    private val seen = HashMap<Any?, MutableList<KeyedEntity>>()

    /**
     * Tracks the entity. Returns `true` if it is the first occurrence, `false` if this same instance was already
     * tracked (cycle — use a stub with [KeyedEntity.key]). `null` always returns `true`.
     */
    fun track(entity: KeyedEntity?): Boolean {
        if (entity == null) return true
        val bucket = seen.getOrPut(entity.key()) { ArrayList(1) }
        if (bucket.any { it === entity }) return false
        bucket.add(entity)
        return true
    }

    /** Checks whether this same instance was already tracked. */
    fun isSeen(entity: KeyedEntity?): Boolean {
        if (entity == null) return false
        return seen[entity.key()]?.any { it === entity } == true
    }
}
