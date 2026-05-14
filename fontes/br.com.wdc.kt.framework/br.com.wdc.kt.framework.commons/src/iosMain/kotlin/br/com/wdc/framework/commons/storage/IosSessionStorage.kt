package br.com.wdc.framework.commons.storage

class IosSessionStorage : SessionStorage {

    private val map = mutableMapOf<String, String>()

    override fun getString(key: String, defaultValue: String?): String? =
        map[key] ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? =
        map[key]?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? =
        map[key]?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? =
        map[key]?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        map[key]?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        if (value != null) map[key] = value else map.remove(key)
    }

    override fun set(key: String, value: Int?) {
        if (value != null) map[key] = value.toString() else map.remove(key)
    }

    override fun set(key: String, value: Long?) {
        if (value != null) map[key] = value.toString() else map.remove(key)
    }

    override fun set(key: String, value: Double?) {
        if (value != null) map[key] = value.toString() else map.remove(key)
    }

    override fun set(key: String, value: Boolean?) {
        if (value != null) map[key] = value.toString() else map.remove(key)
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }

    override fun keys(): Set<String> = map.keys.toSet()

    override fun contains(key: String): Boolean = map.containsKey(key)

    override val size: Int get() = map.size
}
