package br.com.wdc.framework.commons.concurrent

/**
 * wasmJs is single-threaded, so ThreadLocalRef is just a simple property holder.
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
