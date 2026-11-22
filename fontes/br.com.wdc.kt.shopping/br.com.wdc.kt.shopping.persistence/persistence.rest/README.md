# persistence.rest

REST endpoints of the repositories, on top of [`framework-rest`](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.rest/README.md) (Javalin).

| Path | What it is |
|---|---|
| `/api/repo/<entity>/…` | The repository operations (`insert`, `update`, `delete`, `count`, `fetch`, `fetch-page`, `fetch-by-id`) |
| `/api/tx/…` | Client-driven remote transaction (`begin`, `commit`, `rollback`, `status`) |
| `/api/auth/…` | Challenge login and session renewal — only with security on |
| `/openapi.json` | The API's OpenAPI description, derived from the domain |

Each controller configures the `RepositoryRpc` of its entity, which reads and writes with the domain codec — the same one the client uses. **Access is checked** in the controller (`ApiSecurity`): the caller's permission and, for callers who don't reach everyone's data, the restriction to what is theirs. Every write is atomic; errors are mapped to status codes (400, 401, 403, 409, 429).

`RepositoryApiRoutes.create()` builds the `RestApi`; `RepositoryApiRoutes.configure(config)` installs it on a Javalin. Used by the backend and the tests. Security is on if an `AuthenticationService` is registered when the API is built.

See the [persistence architecture](../../../../docs/architecture-persistence.md#rest-api).
