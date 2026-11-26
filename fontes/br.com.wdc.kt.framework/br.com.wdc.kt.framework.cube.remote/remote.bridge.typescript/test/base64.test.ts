import { describe, expect, it } from 'vitest'
import { DataSecurity } from '../src/DataSecurity.js'
import Base64 from '../src/utils/Base64.js'

describe('Base64', () => {
  it('encodes and decodes every byte value', () => {
    const all = Uint8Array.from({ length: 256 }, (_, i) => i)
    expect(Array.from(Base64.decode(Base64.encode(all)))).toEqual(Array.from(all))
  })

  it('follows RFC 4648', () => {
    expect(Base64.encode(new TextEncoder().encode('foobar'))).toBe('Zm9vYmFy')
    expect(Base64.encode(new TextEncoder().encode('fo'))).toBe('Zm8=')
    expect(new TextDecoder().decode(Base64.decode('Zm9vYg=='))).toBe('foob')
  })

  it('refuses what is not Base64, instead of decoding it to wrong bytes', () => {
    expect(() => Base64.decode('Zm9v*mFy')).toThrow()
    expect(() => Base64.decode('Zm9vY')).toThrow()
  })
})

describe('DataSecurity', () => {
  it('refuses a garbled server key at once', async () => {
    const security = new DataSecurity()
    await security.startHandshake()
    await expect(security.completeHandshake('not*base64', 'session')).rejects.toThrow()
  })
})
