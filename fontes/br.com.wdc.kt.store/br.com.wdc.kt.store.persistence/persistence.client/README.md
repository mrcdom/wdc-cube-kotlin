# persistence.client

Multiplatform REST client: implements the domain's repositories and transaction service over HTTP.

**Platforms:** JVM, Android, iOS, JS, wasmJs

- `HttpReadOnlyRepository` / `HttpRepository` — the queries and, in the second, also the writes, once for every entity; each `HttpXxxRepository` only supplies the codec and the path.
- `HttpTransport` — `suspend` on every platform (no thread waits for the network). `BaseHttpTransport` handles the token, session renewal on 401 (once for calls refused together) and the transaction header; each platform only sends a request: `OkHttpTransport` (JVM and Android, OkHttp's asynchronous call), `JsHttpTransport` and `WasmHttpTransport` (`fetch`), `IosHttpTransport` (`NSURLSession` data task). The remote transaction goes with the coroutine that opened it (`RestTransactionService`; `inRemoteTransaction` for one opened by hand).
- `RestTransactionService` — makes several writes atomic by opening a transaction on the server.
- `RestAuthClient` / `RestAuthenticationService` — the SCRAM-SHA-256 login (the client's proof, and the check of the server's signature) and session renewal.
- `RestRepositoryBootstrap.initialize(config, cryptoProvider)` — registers everything; every client entry point calls it.

Used by the local views (Compose and native), where the presenters run on the client. See the [persistence architecture](../../../../docs/architecture-persistence.md#http-client).
