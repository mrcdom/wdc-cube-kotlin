import BigIntUtils from './utils/BigIntUtils'
import RSA from './utils/RSA'
import UTF8 from './utils/UTF8'
import Base64 from './utils/Base64'

class RsaHelper {
  private __rsa: RSA

  constructor(skey: string) {
    const [exponent, key] = skey.split(/:/)

    const publicExponent = BigIntUtils.parse(exponent, 36)
    const privateKey = 0n
    const publicKey = BigIntUtils.parse(key, 36)
    this.__rsa = new RSA(publicExponent, privateKey, publicKey)
  }

  encryptToBase36(message: Uint8Array) {
    const messageAsSafeBytes = UTF8.encode(Base64.encode(message))
    const messageAsBigint = BigIntUtils.fromBuffer(messageAsSafeBytes)

    const messageEncryptedAsBigInt = this.__rsa.encrypt(messageAsBigint)
    return messageEncryptedAsBigInt.toString(36)
  }
}

/** Bytes of the IV in front of every ciphered value (the size GCM is made for). */
const IV_SIZE = 12

/**
 * The session's cipher: AES-256-GCM with a key derived by PBKDF2 from a random password, which goes to the host
 * ciphered with its RSA key (the signature). Every value gets an IV of its own, random, sent in front of it:
 * Base64(IV ‖ ciphertext and tag) — one IV for the whole session would let whoever sees two values learn their XOR.
 */
export class DataSecurity {
  private __key!: CryptoKey
  private __signature!: string
  private __rsa!: RsaHelper

  updateSecurityKey(appSKey: string) {
    this.__rsa = new RsaHelper(appSKey)
  }

  async updateSecretWithRandomPassword() {
    const pwd = Base64.encodeUrlSafe(window.crypto.getRandomValues(new Uint8Array(12)))
    const pwdBuf = UTF8.encode(pwd)
    await this.updateSecret(pwdBuf)
  }

  async updateSecret(password: Uint8Array) {
    const salt = crypto.getRandomValues(new Uint8Array(16))

    // Derive key from password
    const key = await crypto.subtle.importKey('raw', password as unknown as any, { name: 'PBKDF2' }, false, [
      'deriveKey',
    ])

    this.__key = await crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: salt,
        iterations: 250000,
        hash: 'SHA-256',
      },
      key,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt'],
    )

    const cryptedPwd = this.__rsa.encryptToBase36(password)

    this.__signature = `${cryptedPwd}.${Base64.encodeUrlSafe(salt)}`
  }

  getSignature() {
    return this.__signature
  }

  async b64Cipher(text: string) {
    const textAsUtf8Array = UTF8.encode(text) as ArrayBufferView<ArrayBuffer>
    const iv = crypto.getRandomValues(new Uint8Array(IV_SIZE))
    const cipheredText = new Uint8Array(
      await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, this.__key, textAsUtf8Array),
    )
    const result = new Uint8Array(IV_SIZE + cipheredText.length)
    result.set(iv, 0)
    result.set(cipheredText, IV_SIZE)
    return Base64.encode(result)
  }

  async b64Decipher(b64CipheredText: string) {
    const data = Base64.decode(b64CipheredText) as Uint8Array<ArrayBuffer>
    const iv = data.slice(0, IV_SIZE)
    const textAsUtf8Array = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, this.__key, data.slice(IV_SIZE))
    return UTF8.decode(textAsUtf8Array)
  }
}
