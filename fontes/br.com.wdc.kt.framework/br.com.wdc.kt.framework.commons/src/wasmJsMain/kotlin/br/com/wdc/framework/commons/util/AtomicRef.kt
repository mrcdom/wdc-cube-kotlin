package br.com.wdc.framework.commons.util

actual class AtomicRef<T : Any> {
    private var _value: T? = null

    actual fun get(): T = _value ?: error("BEAN not initialized")

    actual fun getOrNull(): T? = _value

    actual fun set(value: T?) {
        _value = value
    }
}
