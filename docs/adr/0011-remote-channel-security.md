# 0011. The remote channel: a key agreed per connection, signatures by HMAC

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

The remote view sends some values ciphered on top of TLS, such as the password typed in the login form, and the server signs session ids and the addresses it returns for the history. The original scheme, inherited from the Java version, was weak on every count:

- the key exchange was textbook RSA (no padding) with a key of about 250 bits from a cookie;
- signatures used RSA-512 and MD5;
- the servers fell back to fixed development keys;
- AES-GCM used one IV for the whole session, which lets whoever sees two values learn their XOR and forge tags.

## Decision

- **The key is agreed per connection by ECDH P-256.** The client sends its ephemeral public key (`clientKey`, Base64 SubjectPublicKeyInfo) in its first message; the server answers with its own (`serverKey`). Both derive an AES-256-GCM key with HKDF-SHA256: the session id as salt, `wdc-cube-remote/aes-256-gcm/v1` as info.
- **Every ciphered value has its own random 12-byte IV**, sent in front of it: `Base64(IV ‖ ciphertext and tag)`.
- **The server signs with HMAC-SHA256** (`RemoteAppSecurity`): session ids (`newSessionId`, `isSessionId`) and addresses (`sign`, `verify`). The key is random per process, or given with `useKey` when several instances must accept each other's signatures.
- The only cookie is `app_id`, the signed session id.

The three ends change together: the backend, both bridges (Web Crypto in the browser) and the stress client.

## Consequences

- Forward secrecy: a recorded session cannot be opened later, even with the server's secrets.
- Requests queued before the handshake answer wait for it.
- A breaking change of the wire for any other client of the protocol.
- The Java version still has the old scheme.

## Alternatives considered

- **RSA-OAEP with a server key.** Rejected: no forward secrecy, and a long-lived key to manage.
- **Rely on TLS alone.** TLS is assumed; the extra layer keeps sensitive form values out of logs and proxies that terminate TLS.
