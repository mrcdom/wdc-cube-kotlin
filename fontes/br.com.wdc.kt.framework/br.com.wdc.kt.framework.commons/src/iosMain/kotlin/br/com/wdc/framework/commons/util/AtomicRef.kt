package br.com.wdc.framework.commons.util

import kotlin.concurrent.AtomicReference

actual class AtomicRef<T : Any> {
    private val ref = AtomicReference<T?>(null)

    actual fun get(): T = ref.value ?: error("BEAN not initialized")

    actual fun getOrNull(): T? = ref.value

    actual fun set(value: T?) {
        ref.value = value
    }
}
