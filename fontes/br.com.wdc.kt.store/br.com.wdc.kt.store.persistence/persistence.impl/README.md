# persistence.impl

JVM implementation of the repositories, over **jOOQ**, for **H2** and **PostgreSQL**.

- `repository/` — one `XxxRepositoryImpl` per entity. Each declares the entity ↔ table mapping (`JsonQueryBuilder`) and translates the criteria into conditions; the query returns the graph the projection asks for in a single database round trip. The item repository's INSERT reads the item's price from its product, on every path.
- `scheme/` — jOOQ classes **generated** from the `DBCreate` DDL. Do not edit by hand: `./gradlew :store-scripts:generateJooqSchema`.
- `security/` — authentication: JWT, sessions (the RSA private key stored encrypted) and intent secrets; the login challenges in flight (`NonceStore`, in `EN_AUTH_NONCE`); the per-user slowdown of failed sign-ins (`LoginThrottle`, in `EN_LOGIN_FAILURE`, shared by every instance); the notice of an ended session to the other instances (`SessionEvents`; `PostgresSessionEvents` with `LISTEN`/`NOTIFY`); the security meters (`SecurityMeters`). `AuthenticationServiceImpl.startCleanup` drops expired sessions, challenges and old failed sign-ins periodically. Access control over the operations is not here: it belongs to the REST API ([persistence.rest](../persistence.rest/)).
- `StoreRepositoryBootstrap` — binds the module to the `DataSource`: registers the `DSLContext`, the transaction service and the repositories; `initializeSecurity` registers the authentication service when there is a JWT secret.

The repositories do not demarcate transactions: they use the current transaction's connection or, outside one, a standalone connection in autocommit.

See the [persistence architecture](../../../../docs/architecture-persistence.md).
