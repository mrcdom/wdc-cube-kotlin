package br.com.wdc.framework.commons.concurrent

expect class ThreadLocalRef<T>() {
    fun get(): T?
    fun set(value: T?)
    fun remove()
}
