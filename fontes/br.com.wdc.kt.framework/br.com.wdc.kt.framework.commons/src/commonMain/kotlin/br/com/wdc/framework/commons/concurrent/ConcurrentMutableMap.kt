package br.com.wdc.framework.commons.concurrent

/**
 * Creates a thread-safe MutableMap suitable for the current platform.
 *
 * - JVM/Android: backed by ConcurrentHashMap
 * - iOS/Native: backed by a synchronized wrapper using NSRecursiveLock
 * - JS/WasmJs: backed by a plain LinkedHashMap (single-threaded environment)
 */
expect fun <K, V> concurrentMutableMapOf(): MutableMap<K, V>
