package br.com.wdc.framework.commons.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The session kept across launches, in the iOS Keychain: what the app keeps there is a session — tokens, the key that
 * signs its addresses —, so it is not left in plain files. Each value is a generic-password item of the service
 * [service], readable only on this device after its first unlock, and left out of backups.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPersistentSessionStorage(
    private val service: String = "wdc_session"
) : SessionStorage {

    override fun getString(key: String, defaultValue: String?): String? = read(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? = read(key)?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? = read(key)?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? = read(key)?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        read(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        remove(key)
        if (value == null) return
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        val status = withQuery(key) { query, retain ->
            CFDictionaryAddValue(query, kSecValueData, retain(data))
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            SecItemAdd(query, null)
        }
        // a value that could not be kept is an error, not a silent loss of the session
        check(status == errSecSuccess) { "The Keychain refused to keep '$key' (OSStatus $status)" }
    }

    override fun set(key: String, value: Int?) = set(key, value?.toString())

    override fun set(key: String, value: Long?) = set(key, value?.toString())

    override fun set(key: String, value: Double?) = set(key, value?.toString())

    override fun set(key: String, value: Boolean?) = set(key, value?.toString())

    override fun remove(key: String) {
        withQuery(key) { query, _ -> SecItemDelete(query) }
    }

    override fun clear() {
        withQuery(null) { query, _ -> SecItemDelete(query) }
    }

    override fun keys(): Set<String> = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(null) { query, _ ->
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            SecItemCopyMatching(query, result.ptr)
        }
        if (status != errSecSuccess) return emptySet()
        val items = CFBridgingRelease(result.value) as? List<*> ?: return emptySet()
        val account = CFBridgingRelease(CFRetain(kSecAttrAccount))
        items.mapNotNull { (it as? Map<*, *>)?.get(account)?.toString() }.toSet()
    }

    override fun contains(key: String): Boolean = read(key) != null

    override val size: Int get() = keys().size

    private fun read(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(key) { query, _ ->
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            SecItemCopyMatching(query, result.ptr)
        }
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    /**
     * Runs [block] with a query for the items of [service] — one of them when [key] is given. [block] adds what it
     * needs; what it bridges with the given `retain` is released afterwards.
     */
    private fun <T> withQuery(key: String?, block: (CFMutableDictionaryRef?, retain: (Any?) -> CFTypeRef?) -> T): T = memScoped {
        val retained = mutableListOf<CFTypeRef?>()
        val retain: (Any?) -> CFTypeRef? = { value -> CFBridgingRetain(value).also { retained += it } }
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, retain(service))
            if (key != null) CFDictionaryAddValue(query, kSecAttrAccount, retain(key))
            block(query, retain)
        } finally {
            retained.forEach { if (it != null) CFRelease(it) }
            CFRelease(query)
        }
    }
}
