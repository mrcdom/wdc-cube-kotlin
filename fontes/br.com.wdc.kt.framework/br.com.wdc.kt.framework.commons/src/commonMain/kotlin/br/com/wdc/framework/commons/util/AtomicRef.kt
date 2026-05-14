package br.com.wdc.framework.commons.util

expect class AtomicRef<T : Any>() {
    fun get(): T
    fun getOrNull(): T?
    fun set(value: T?)
}
