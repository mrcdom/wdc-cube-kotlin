package br.com.wdc.framework.commons.storage

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The session kept across launches, in [prefs] with every value encrypted: what the app keeps there is a session —
 * tokens, the key that signs its addresses —, so it is not left readable in the app's files or backups.
 *
 * Each value is AES-256-GCM-encrypted with a key that lives in the Android Keystore ([keyAlias]) and never leaves it.
 * The names of the entries stay readable; their values don't. A value that does not decrypt — written in the clear by
 * an earlier version, or under a key that was lost — reads as absent and is dropped: the user signs in again.
 */
class AndroidPersistentSessionStorage(
    private val prefs: SharedPreferences,
    private val keyAlias: String = "wdc_session_key",
) : SessionStorage {

    override fun getString(key: String, defaultValue: String?): String? = read(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int?): Int? = read(key)?.toIntOrNull() ?: defaultValue

    override fun getLong(key: String, defaultValue: Long?): Long? = read(key)?.toLongOrNull() ?: defaultValue

    override fun getDouble(key: String, defaultValue: Double?): Double? = read(key)?.toDoubleOrNull() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean?): Boolean? =
        read(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun set(key: String, value: String?) {
        if (value != null) prefs.edit().putString(key, encrypt(value)).apply()
        else prefs.edit().remove(key).apply()
    }

    override fun set(key: String, value: Int?) = set(key, value?.toString())

    override fun set(key: String, value: Long?) = set(key, value?.toString())

    override fun set(key: String, value: Double?) = set(key, value?.toString())

    override fun set(key: String, value: Boolean?) = set(key, value?.toString())

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun keys(): Set<String> = prefs.all.keys

    override fun contains(key: String): Boolean = read(key) != null

    override val size: Int get() = prefs.all.size

    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            decrypt(stored)
        } catch (_: Exception) {
            remove(key)
            null
        }
    }

    /** `Base64(iv + ciphertext)`: GCM authenticates it, so a changed value does not decrypt. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    /** The Keystore key, created the first time. */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
