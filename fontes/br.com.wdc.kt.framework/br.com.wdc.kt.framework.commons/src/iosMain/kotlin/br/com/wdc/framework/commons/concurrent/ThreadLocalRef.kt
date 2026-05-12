package br.com.wdc.framework.commons.concurrent

/**
 * iOS (Kotlin/Native) implementation of ThreadLocalRef.
 * Kotlin/Native has its own threading model; for the main thread this is a simple holder.
 */
actual class ThreadLocalRef<T> actual constructor() {

    private var value: T? = null

    actual fun get(): T? = value

    actual fun set(value: T?) {
        this.value = value
    }

    actual fun remove() {
        value = null
    }
}
