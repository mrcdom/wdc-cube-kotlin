package br.com.wdc.shopping.nativeui.web.bridge

/**
 * The emitters of each list item, created once per key and reused between renders — the item-class pattern of the
 * remote React client (`BasePanelClass`), where each item keeps its own handlers.
 *
 * ```kotlin
 * private val itemEmitters = EmitterCache<Long, ItemEmitters> { id -> ItemEmitters(id) }
 * // in the render:
 * itemEmitters.retainOnly(state.items.map { it.id })
 * IconButton { onClick = itemEmitters[item.id].remove }
 * ```
 *
 * An emitter must not capture values of the render: it reads what it needs (the item, its quantity) from the
 * presenter state when it runs, by the key.
 */
class EmitterCache<K, E : Any>(private val create: (K) -> E) {

    private val byKey = LinkedHashMap<K, E>()

    operator fun get(key: K): E = byKey.getOrPut(key) { create(key) }

    /** Drops the emitters of the keys no longer shown. */
    fun retainOnly(keys: Collection<K>) {
        val keep = keys.toSet()
        byKey.keys.retainAll(keep)
    }
}
