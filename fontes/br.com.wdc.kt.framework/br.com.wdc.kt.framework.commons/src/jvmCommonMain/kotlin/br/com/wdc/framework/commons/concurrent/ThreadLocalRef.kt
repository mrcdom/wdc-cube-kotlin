package br.com.wdc.framework.commons.concurrent

actual class ThreadLocalRef<T> actual constructor() {

    private val holder = ThreadLocal<T>()

    actual fun get(): T? = holder.get()

    actual fun set(value: T?) = holder.set(value)

    actual fun remove() = holder.remove()
}
