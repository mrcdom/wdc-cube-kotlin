# shopping-domain

The Shopping application domain: entities, criteria, repository contracts and the codecs all of it travels with.

**Platforms:** JVM, Android, iOS, JS, wasmJs

Each entity has its own package — `product`, `user`, `purchase`, `purchaseitem` — with four classes:

| Class | Role |
|---|---|
| `Xxx` | The entity. Also serves as a projection: field set = "fetch this field" |
| `XxxCriteria` | The filters and ordering of a query |
| `XxxRepository` | The contract, implemented on the server (jOOQ) and on the client (HTTP) |
| `XxxCodec` | JSON reading and writing — the same on client and server |

Also here: `ShoppingTransactions` (the application's transaction service), security (`SecurityContext`, `Role`, `AuthenticationService`) and configuration.

See the [persistence architecture](../../../docs/architecture-persistence.md).
