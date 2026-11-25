# 0012. Sign-in with SCRAM-SHA-256

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

The sign-in was a challenge–response with HMAC, keyed by the MD5 of the password, which was also what the database stored. Anyone who read the database could sign in as any user without knowing a password, and MD5 offers no resistance to guessing. A reference architecture has to be a good example here.

## Decision

Sign-in follows **SCRAM-SHA-256** (RFC 5802 and 7677):

1. `POST /api/auth/challenge {userName}` returns a nonce bound to that user, the user's salt and iteration count.
2. The client proves it knows the password without sending it, and `POST /api/auth/login {userName, proof, nonce}` returns the tokens and the **server's signature**.
3. The client checks that signature before keeping the session; a server that does not hold the user's verifier is not the real one.

The database stores a verifier in PostgreSQL's format (`EN_USER.PASSWORD`), with a random salt and 4096 iterations kept per verifier; `PasswordVerifiers` is the only place that makes one. `Scram` and `ScramVerifier` are common code, used by the server and by every client (JVM, JS, Wasm, iOS), tested against the RFC 7677 vector. An unknown user gets a made-up salt that is stable for that name, and the server does the same work, so the answer does not tell whether the user exists.

Failed sign-ins of one user make it wait (`LoginThrottle`: five free failures, then 30 seconds doubling up to 15 minutes; a success resets it). It is a wait, not a lock. `/api/auth` is also limited per client address and minute.

## Consequences

- Reading the database no longer lets anyone sign in; guessing a password costs the full PBKDF2 work.
- The migration converted existing passwords without knowing them: the old MD5 became only the input of the key derivation (migration 0009).
- Someone who knows a login can make its owner wait, never lock them out.
- The limits are in memory, per instance.

See [security-architecture.md](../security-architecture.md).
