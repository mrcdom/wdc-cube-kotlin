package br.com.wdc.framework.commons.storage

import platform.Foundation.NSUserDefaults

class IosPersistentSessionStorage(
    private val suiteName: String = "wdc_session"
) : SessionStorage {

    private val defaults: NSUserDefaults = NSUserDefaults(suiteName = suiteName)

    private val keysKey = "__wdc_stored_keys__"

    private fun loadStoredKeys(): MutableSet<String> {
        val array = defaults.stringArrayForKey(keysKey)
        return array?.mapNotNull { it as? String }?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveStoredKeys(keys: Set<String>) {
        defaults.setObject(keys.toList(), keysKey)
    }

    override fun getString(key: String, defaultValue: String?): String? =
        defaults.stringForKey(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? =
        defaults.stringForKey(key)?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? =
        defaults.stringForKey(key)?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? =
        defaults.stringForKey(key)?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        defaults.stringForKey(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        if (value != null) {
            defaults.setObject(value, key)
            val keys = loadStoredKeys()
            keys.add(key)
            saveStoredKeys(keys)
        } else {
            remove(key)
        }
    }

    override fun set(key: String, value: Int?) {
        set(key, value?.toString())
    }

    override fun set(key: String, value: Long?) {
        set(key, value?.toString())
    }

    override fun set(key: String, value: Double?) {
        set(key, value?.toString())
    }

    override fun set(key: String, value: Boolean?) {
        set(key, value?.toString())
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        val keys = loadStoredKeys()
        keys.remove(key)
        saveStoredKeys(keys)
    }

    override fun clear() {
        val keys = loadStoredKeys()
        for (key in keys) {
            defaults.removeObjectForKey(key)
        }
        defaults.removeObjectForKey(keysKey)
    }

    override fun keys(): Set<String> = loadStoredKeys()

    override fun contains(key: String): Boolean =
        defaults.stringForKey(key) != null

    override val size: Int get() = loadStoredKeys().size
}
