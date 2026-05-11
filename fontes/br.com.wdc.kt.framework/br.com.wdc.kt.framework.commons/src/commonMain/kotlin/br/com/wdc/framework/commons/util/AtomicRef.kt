package br.com.wdc.framework.commons.util

class AtomicRef<T : Any> {
    @Volatile
    private var _value: T? = null

    fun get(): T = _value ?: error("BEAN not initialized")

    fun getOrNull(): T? = _value

    fun set(value: T?) {
        _value = value
    }
}
