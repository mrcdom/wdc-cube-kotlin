# persistence.client

Multiplatform REST client: implements the domain's repositories and transaction service over HTTP.

**Platforms:** JVM, Android, iOS, JS, wasmJs

- `HttpReadOnlyRepository` / `HttpRepository` — the queries and, in the second, also the writes, once for every entity; each `HttpXxxRepository` only supplies the codec and the path.
- `HttpTransport` — one blocking implementation per platform: `OkHttpTransport` (JVM and Android), `JsHttpTransport`, `WasmHttpTransport`, `IosHttpTransport`. They handle the token, session renewal on 401 and the transaction headers.
- `RestTransactionService` — makes several writes atomic by opening a transaction on the server.
- `RestAuthClient` / `RestAuthenticationService` — challenge login and session renewal.
- `RestRepositoryBootstrap.initialize(config, cryptoProvider)` — registers everything; every client entry point calls it.

Used by the local views (Compose and native), where the presenters run on the client. See the [persistence architecture](../../../../docs/architecture-persistence.md#http-client).
