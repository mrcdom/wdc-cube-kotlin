package br.com.wdc.framework.commons.storage

class WasmSessionStorage : SessionStorage {

    override fun getString(key: String, defaultValue: String?): String? =
        jsSessionGetItem(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? =
        jsSessionGetItem(key)?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? =
        jsSessionGetItem(key)?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? =
        jsSessionGetItem(key)?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        jsSessionGetItem(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        if (value != null) jsSessionSetItem(key, value) else jsSessionRemoveItem(key)
    }

    override fun set(key: String, value: Int?) {
        if (value != null) jsSessionSetItem(key, value.toString()) else jsSessionRemoveItem(key)
    }

    override fun set(key: String, value: Long?) {
        if (value != null) jsSessionSetItem(key, value.toString()) else jsSessionRemoveItem(key)
    }

    override fun set(key: String, value: Double?) {
        if (value != null) jsSessionSetItem(key, value.toString()) else jsSessionRemoveItem(key)
    }

    override fun set(key: String, value: Boolean?) {
        if (value != null) jsSessionSetItem(key, value.toString()) else jsSessionRemoveItem(key)
    }

    override fun remove(key: String) {
        jsSessionRemoveItem(key)
    }

    override fun clear() {
        jsSessionClear()
    }

    override fun keys(): Set<String> {
        val result = mutableSetOf<String>()
        val len = jsSessionLength()
        for (i in 0 until len) {
            jsSessionKey(i)?.let { result.add(it) }
        }
        return result
    }

    override fun contains(key: String): Boolean =
        jsSessionGetItem(key) != null

    override val size: Int get() = jsSessionLength()
}

@JsFun("(key) => { const v = sessionStorage.getItem(key); return v === null ? null : v; }")
private external fun jsSessionGetItem(key: String): String?

@JsFun("(key, value) => { sessionStorage.setItem(key, value); }")
private external fun jsSessionSetItem(key: String, value: String)

@JsFun("(key) => { sessionStorage.removeItem(key); }")
private external fun jsSessionRemoveItem(key: String)

@JsFun("() => { sessionStorage.clear(); }")
private external fun jsSessionClear()

@JsFun("() => { return sessionStorage.length; }")
private external fun jsSessionLength(): Int

@JsFun("(index) => { const k = sessionStorage.key(index); return k === null ? null : k; }")
private external fun jsSessionKey(index: Int): String?
