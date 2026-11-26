# Changelog

The versions of the framework (`io.github.mrcdom.wdc.kt:*`, on Maven Central, and from 0.5.0 the remote bridge's TypeScript packages on npm). Each version's detailed notes — every change, whether it breaks anything, and what to do — are in the [framework's README](fontes/br.com.wdc.kt.framework/README.md#using-as-a-dependency); this file is the summary. The WDC Store demo and the starter follow the framework's `main` and have no versions of their own.

Released versions are immutable.

## [0.5.0] — not yet published

Ten modules. Breaking changes; see the [0.5.0 release notes](fontes/br.com.wdc.kt.framework/README.md#050-release-notes).

- **`framework-cube`:** the framework knows only scopes (`Scope` replaces `ViewState`; `CubeView` is gone, and each rendering technology owns its views); actions are guarded fields of the scope, run one at a time per application; places in a tree declared by keys classes; navigation requested during another one waits for it.
- **New:** `framework-cube-remote-backend` and `-javalin` (presenters on the server, views in a browser), `framework-cube-remote-bridge` and `-react` (its Kotlin/JS client). The remote channel agrees on a key per connection (ECDH P-256, AES-256-GCM) and signs with HMAC-SHA256. The protocol is versioned (1, in the handshake) and specified, with conformance fixtures shared by hosts and clients. For several instances, the registry tells of the sessions it makes and releases one whose tab moved (`onCreated`, `releaseIdle`); for metrics, it tells how long each client message took (`onMessage`). Its TypeScript client goes to npm: `wdc-cube-remote-bridge` and `wdc-cube-remote-bridge-react`.
- **`framework-domain`:** resource calls (`ResourceQuery`, `ResourceCreate`/`Save`/`Update`/`Delete`); a criterion from the wire that does not hold together is refused (400), and so is a slice number (`limit`, `offset`, `page`, `pageSize`) that is not a JSON integer, `null` included.
- **`framework-rest`:** data policies per role (rows, fields, writes); CORS of its own (same-origin requests pass; an origin not allowed gets no CORS headers instead of 400).
- **`framework-persistence`:** remote transactions: a failing participant dooms it, unknown ones answer 410, owner = user + client, periodic cleanup.
- **`framework-commons`:** `RSA` removed (textbook RSA, unsafe).
- **All:** the public API is checked (Kotlin ABI validation) and documented (Dokka in the javadoc jars).

## [0.4.0] — 2026-11-15

`insert` by projection, with the complete bean back; `afterCommit` in transactions; in `framework-rest`, `Server-Timing`, cache and security headers, the Swagger UI page, a per-minute rate limit and new reads in `RestCall`; `isIn(empty)` filters by none. [Notes](fontes/br.com.wdc.kt.framework/README.md#040-release-notes).

## [0.3.0] — 2026-10-28

**`framework-rest`**: REST APIs over Javalin 7 — resource routes and per-entity RPC, pluggable security, exception map, per-request context and transaction, CORS, gzip, OpenAPI generated from the routes, a test server. `framework-domain`: `@ApiDoc`; `containing`/`startingWith` search literally. `framework-jooq`: criteria over several columns, `UUID` and `DATE` columns.

## [0.2.0] — 2026-10-06

`framework-domain`, `framework-persistence` and `framework-jooq`; compiled with Kotlin 2.3.

## [0.1.0] — 2026-10-05

`framework-commons` and `framework-cube`.

[0.5.0]: https://github.com/mrcdom/wdc-cube-kotlin/compare/v0.4.0...main
[0.4.0]: https://github.com/mrcdom/wdc-cube-kotlin/releases/tag/v0.4.0
[0.3.0]: https://github.com/mrcdom/wdc-cube-kotlin/releases/tag/v0.3.0
[0.2.0]: https://github.com/mrcdom/wdc-cube-kotlin/releases/tag/v0.2.0
[0.1.0]: https://github.com/mrcdom/wdc-cube-kotlin/releases/tag/v0.1.0
