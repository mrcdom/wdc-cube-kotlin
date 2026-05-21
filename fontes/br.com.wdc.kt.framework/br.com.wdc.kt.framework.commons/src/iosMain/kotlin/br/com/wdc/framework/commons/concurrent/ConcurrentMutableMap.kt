package br.com.wdc.framework.commons.concurrent

import platform.Foundation.NSRecursiveLock

actual fun <K, V> concurrentMutableMapOf(): MutableMap<K, V> = SynchronizedMutableMap()

private class SynchronizedMutableMap<K, V>(
    private val delegate: MutableMap<K, V> = LinkedHashMap()
) : MutableMap<K, V> {

    private val lock = NSRecursiveLock()

    private inline fun <T> sync(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    override val size: Int get() = sync { delegate.size }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = sync { LinkedHashMap(delegate).entries.toMutableSet() }
    override val keys: MutableSet<K> get() = sync { delegate.keys.toMutableSet() }
    override val values: MutableCollection<V> get() = sync { delegate.values.toMutableList() }

    override fun containsKey(key: K): Boolean = sync { delegate.containsKey(key) }
    override fun containsValue(value: V): Boolean = sync { delegate.containsValue(value) }
    override fun get(key: K): V? = sync { delegate[key] }
    override fun isEmpty(): Boolean = sync { delegate.isEmpty() }

    override fun clear() = sync { delegate.clear() }
    override fun put(key: K, value: V): V? = sync { delegate.put(key, value) }
    override fun putAll(from: Map<out K, V>) = sync { delegate.putAll(from) }
    override fun remove(key: K): V? = sync { delegate.remove(key) }
}
