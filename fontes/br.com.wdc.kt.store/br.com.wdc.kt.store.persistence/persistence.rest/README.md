# persistence.rest

REST endpoints of the repositories, on top of [`framework-rest`](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.rest/README.md) (Javalin).

| Path | What it is |
|---|---|
| `/api/repo/<entity>/…` | The repository operations (`insert`, `update`, `delete`, `count`, `fetch`, `fetch-page`, `fetch-by-id`) |
| `/api/tx/…` | Client-driven remote transaction (`begin`, `commit`, `rollback`, `status`) |
| `/api/auth/…` | Challenge login and session renewal — only with security on |
| `/openapi.json` | The API's OpenAPI description, derived from the domain |

Each controller configures the `RepositoryRpc` of its entity, which reads and writes with the domain codec — the same one the client uses. **Access is decided by the entity's data policy** (package `security`, on `framework-rest`'s [data policy](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.rest/README.md#data-policy)) — the only authorization of the repository API; the routes only require an authenticated caller. Every write is atomic; errors are mapped to status codes (400, 401, 403, 404, 409, 429).

| Entity | ADMIN | CUSTOMER | MANAGER |
|---|---|---|---|
| `user` | reads every user (not the password), creates, updates, deletes | reads their own user | reads their own user in full, the others by name with the login masked (`f*****`) |
| `product` | reads, creates, updates, deletes (image included) | reads the catalog | as ADMIN |
| `purchase` | reads, creates, updates (date, buyer), deletes | reads and creates their own purchases — for themselves only | reads their own purchases |
| `purchase-item` | reads, creates, updates, deletes | reads the items of their own purchases, adds items to them without a price | reads the items of their own purchases |

- **A purchase is a receipt**: once written, only an ADMIN changes it or its items.
- **The password is write-only**: in no read grant; projecting it is refused (403, naming `password`), with security on or off. On insert and update it is stored as its digest (`beforeInsert`/`beforeUpdate`, after the policy's check): encrypted with the session's RSA key when there is a session (otherwise 400), as typed without security.
- **The price of an item is the server's**: the repository reads the product's in the INSERT; an update that touches the price or the product gets the current one (`ItemPrices`, in `beforeUpdate`).
- **Default shapes**, for a read without a projection (security on): a purchase is its receipt (date, buyer's name, items with quantity, price and the product's name and price); an item is its quantity, price and product (name and price); a user or a product, every field the role reads. Without security, the repository's own fields.
- Projecting, filtering or sorting by a field the caller's roles do not read is 403, naming it; the alternative groups of a criteria (`anyOf`) are refused (400).
- The security context carries the user's **role names** (`Role.ADMIN`, `CUSTOMER`, `MANAGER`); roles carry no permissions of their own.

`RepositoryApiRoutes.create()` builds the `RestApi`; `RepositoryApiRoutes.configure(config)` installs it on a Javalin. Used by the backend and the tests. Security is on if an `AuthenticationService` is registered when the API is built.

See the [persistence architecture](../../../../docs/architecture-persistence.md#rest-api) and the [security architecture](../../../../docs/security-architecture.md).
