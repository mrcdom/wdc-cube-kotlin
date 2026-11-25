import UTF8 from './utils/UTF8'
import Base64 from './utils/Base64'

/** Bytes of the IV in front of every ciphered value (the size GCM is made for). */
const IV_SIZE = 12

/** The HKDF info: what the derived key is for, and the version of this scheme (the same as the host's). */
const HKDF_INFO = 'wdc-cube-remote/aes-256-gcm/v1'

/** How long a value waits for a handshake to complete before ciphering fails. */
const KEY_TIMEOUT_MS = 15_000

const ECDH_P256 = { name: 'ECDH', namedCurve: 'P-256' } as const

type Deferred = {
  promise: Promise<CryptoKey>
  resolve: (key: CryptoKey) => void
}

const newDeferred = (): Deferred => {
  let resolve!: (key: CryptoKey) => void
  const promise = new Promise<CryptoKey>((r) => (resolve = r))
  return { promise, resolve }
}

/**
 * The session's cipher for sensitive values. Each WebSocket agrees on its own key: the client sends an ephemeral
 * ECDH P-256 public key (`clientKey`), the host answers with its own (`serverKey`), and both derive an AES-256-GCM key
 * from the shared secret with HKDF-SHA256 (salt = the session id, info = {@link HKDF_INFO}). No long-lived key exists,
 * so a recorded session cannot be deciphered later (forward secrecy).
 *
 * Every value gets a random IV of its own, sent in front of it: Base64(IV ‖ ciphertext and tag).
 */
export class DataSecurity {
  private __pair: CryptoKeyPair | null = null
  private __key: CryptoKey | null = null
  private __pending = newDeferred()

  /** Starts a new handshake (a new key pair) and returns the public key to send, as Base64 SPKI. */
  async startHandshake(): Promise<string> {
    // The old key dies with its socket; values ciphered from now on wait for the new one.
    if (this.__key) {
      this.__key = null
      this.__pending = newDeferred()
    }
    const pair = await crypto.subtle.generateKey(ECDH_P256, false, ['deriveBits'])
    this.__pair = pair
    const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))
    return Base64.encode(spki)
  }

  /** Completes the current handshake with the host's public key (Base64 SPKI) and the session id. */
  async completeHandshake(serverKeyB64: string, sessionId: string): Promise<void> {
    const pair = this.__pair
    if (!pair) {
      throw new Error('No handshake in progress')
    }

    const serverKeyBytes = Base64.decode(serverKeyB64) as Uint8Array<ArrayBuffer>
    const serverPublic = await crypto.subtle.importKey('spki', serverKeyBytes, ECDH_P256, false, [])
    const shared = await crypto.subtle.deriveBits({ name: 'ECDH', public: serverPublic }, pair.privateKey, 256)

    const hkdfKey = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey'])
    const key = await crypto.subtle.deriveKey(
      {
        name: 'HKDF',
        hash: 'SHA-256',
        salt: UTF8.encode(sessionId) as Uint8Array<ArrayBuffer>,
        info: UTF8.encode(HKDF_INFO) as Uint8Array<ArrayBuffer>,
      },
      hkdfKey,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt'],
    )

    // A newer handshake started meanwhile: this key belongs to a socket that is gone.
    if (this.__pair !== pair) {
      return
    }
    this.__pair = null
    this.__key = key
    this.__pending.resolve(key)
  }

  /** The current key, waiting for the handshake in progress (or the next one) up to {@link KEY_TIMEOUT_MS}. */
  private async currentKey(): Promise<CryptoKey> {
    if (this.__key) {
      return this.__key
    }
    let timer = 0
    const timeout = new Promise<never>((_, reject) => {
      timer = window.setTimeout(
        () => reject(new Error('No secure session with the server: the key handshake did not complete')),
        KEY_TIMEOUT_MS,
      )
    })
    try {
      return await Promise.race([this.__pending.promise, timeout])
    } finally {
      window.clearTimeout(timer)
    }
  }

  async b64Cipher(text: string) {
    const key = await this.currentKey()
    const textAsUtf8Array = UTF8.encode(text) as Uint8Array<ArrayBuffer>
    const iv = crypto.getRandomValues(new Uint8Array(IV_SIZE))
    const cipheredText = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, textAsUtf8Array))
    const result = new Uint8Array(IV_SIZE + cipheredText.length)
    result.set(iv, 0)
    result.set(cipheredText, IV_SIZE)
    return Base64.encode(result)
  }

  async b64Decipher(b64CipheredText: string) {
    const key = await this.currentKey()
    const data = Base64.decode(b64CipheredText) as Uint8Array<ArrayBuffer>
    const iv = data.slice(0, IV_SIZE)
    const textAsUtf8Array = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, data.slice(IV_SIZE))
    return UTF8.decode(textAsUtf8Array)
  }
}
