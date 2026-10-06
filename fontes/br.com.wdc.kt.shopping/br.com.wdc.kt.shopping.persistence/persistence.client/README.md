# persistence.client

Cliente REST multiplataforma: implementa os repositórios e o serviço de transação do domínio sobre HTTP.

**Plataformas:** JVM, Android, iOS, JS, wasmJs

- `HttpReadOnlyRepository` / `HttpRepository` — as consultas e, no segundo, também a escrita, uma vez para todas as entidades; cada `HttpXxxRepository` só informa o codec e o caminho.
- `HttpTransport` — uma implementação bloqueante por plataforma: `OkHttpTransport` (JVM e Android), `JsHttpTransport`, `WasmHttpTransport`, `IosHttpTransport`. Cuidam do token, da renovação da sessão no 401 e dos cabeçalhos de transação.
- `RestTransactionService` — torna várias escritas atômicas, abrindo uma transação no servidor.
- `RestAuthClient` / `RestAuthenticationService` — login por desafio e renovação de sessão.
- `RestRepositoryBootstrap.initialize(config, cryptoProvider)` — registra tudo; todo entry point de cliente o chama.

Usado pelas views locais (Compose e nativas), em que os presenters rodam no cliente. Veja a [arquitetura de persistência](../../../../docs/architecture-persistence.md#cliente-http).
