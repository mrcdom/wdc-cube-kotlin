package br.com.wdc.framework.commons.storage

import kotlinx.browser.window

/**
 * SessionStorage implementation for Kotlin/JS using browser sessionStorage API.
 */
class JsSessionStorage : SessionStorage {

    private val storage get() = window.sessionStorage

    override fun getString(key: String, defaultValue: String?): String? =
        storage.getItem(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? =
        storage.getItem(key)?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? =
        storage.getItem(key)?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? =
        storage.getItem(key)?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        storage.getItem(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        if (value != null) storage.setItem(key, value) else storage.removeItem(key)
    }

    override fun set(key: String, value: Int?) {
        if (value != null) storage.setItem(key, value.toString()) else storage.removeItem(key)
    }

    override fun set(key: String, value: Long?) {
        if (value != null) storage.setItem(key, value.toString()) else storage.removeItem(key)
    }

    override fun set(key: String, value: Double?) {
        if (value != null) storage.setItem(key, value.toString()) else storage.removeItem(key)
    }

    override fun set(key: String, value: Boolean?) {
        if (value != null) storage.setItem(key, value.toString()) else storage.removeItem(key)
    }

    override fun remove(key: String) {
        storage.removeItem(key)
    }

    override fun clear() {
        storage.clear()
    }

    override fun keys(): Set<String> {
        val result = mutableSetOf<String>()
        for (i in 0 until storage.length) {
            storage.key(i)?.let { result.add(it) }
        }
        return result
    }

    override fun contains(key: String): Boolean =
        storage.getItem(key) != null

    override val size: Int get() = storage.length
}
