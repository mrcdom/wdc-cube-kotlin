package br.com.wdc.framework.commons.storage

import android.content.SharedPreferences

class AndroidPersistentSessionStorage(
    private val prefs: SharedPreferences
) : SessionStorage {

    override fun getString(key: String, defaultValue: String?): String? =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? =
        prefs.getString(key, null)?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? =
        prefs.getString(key, null)?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? =
        prefs.getString(key, null)?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        prefs.getString(key, null)?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        if (value != null) prefs.edit().putString(key, value).apply()
        else prefs.edit().remove(key).apply()
    }

    override fun set(key: String, value: Int?) {
        if (value != null) prefs.edit().putString(key, value.toString()).apply()
        else prefs.edit().remove(key).apply()
    }

    override fun set(key: String, value: Long?) {
        if (value != null) prefs.edit().putString(key, value.toString()).apply()
        else prefs.edit().remove(key).apply()
    }

    override fun set(key: String, value: Double?) {
        if (value != null) prefs.edit().putString(key, value.toString()).apply()
        else prefs.edit().remove(key).apply()
    }

    override fun set(key: String, value: Boolean?) {
        if (value != null) prefs.edit().putString(key, value.toString()).apply()
        else prefs.edit().remove(key).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun keys(): Set<String> = prefs.all.keys

    override fun contains(key: String): Boolean = prefs.contains(key)

    override val size: Int get() = prefs.all.size
}
