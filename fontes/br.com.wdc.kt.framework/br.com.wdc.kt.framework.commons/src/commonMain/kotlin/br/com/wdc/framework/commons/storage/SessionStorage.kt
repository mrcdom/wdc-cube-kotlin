package br.com.wdc.framework.commons.storage

/**
 * A session-scoped key-value storage, similar to the browser's sessionStorage API.
 * Data is kept in memory and lost when the application closes.
 *
 * Platform implementations:
 * - JVM/Android: ConcurrentHashMap-based
 * - iOS: NSMutableDictionary-based
 * - WasmJs: Delegates to window.sessionStorage
 */
interface SessionStorage {

    fun getString(key: String, defaultValue: String? = null): String?

    fun getInt(key: String, defaultValue: Int? = null): Int?

    fun getLong(key: String, defaultValue: Long? = null): Long?

    fun getDouble(key: String, defaultValue: Double? = null): Double?

    fun getBoolean(key: String, defaultValue: Boolean? = null): Boolean?

    fun set(key: String, value: String?)

    fun set(key: String, value: Int?)

    fun set(key: String, value: Long?)

    fun set(key: String, value: Double?)

    fun set(key: String, value: Boolean?)

    fun remove(key: String)

    fun clear()

    fun keys(): Set<String>

    fun contains(key: String): Boolean

    val size: Int
}
