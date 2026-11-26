/**
 * Base64 with the standard alphabet and padding (RFC 4648 §4), through the platform's `btoa`/`atob`: input that is
 * not Base64 throws (`InvalidCharacterError`), as the Kotlin bridge's `kotlin.io.encoding.Base64` does, instead of
 * turning into wrong bytes that only fail later, in the cryptography.
 */
const Base64 = {
  encode(binData: Uint8Array): string {
    let binary = ''
    for (let i = 0; i < binData.length; i++) binary += String.fromCharCode(binData[i]!)
    return btoa(binary)
  },

  decode(base64String: string): Uint8Array {
    const binary = atob(base64String)
    const binData = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) binData[i] = binary.charCodeAt(i)
    return binData
  },
}

export default Base64
