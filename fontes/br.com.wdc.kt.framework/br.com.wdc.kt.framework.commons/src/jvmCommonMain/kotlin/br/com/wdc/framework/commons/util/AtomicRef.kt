package br.com.wdc.framework.commons.util

import java.util.concurrent.atomic.AtomicReference

actual class AtomicRef<T : Any> {
    private val ref = AtomicReference<T?>(null)

    actual fun get(): T = ref.get() ?: error("BEAN not initialized")

    actual fun getOrNull(): T? = ref.get()

    actual fun set(value: T?) {
        ref.set(value)
    }
}
