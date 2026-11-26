# store-tests

Integration tests of the WDC Store application: repositories, REST API, transactions, security, schema and presenters.

```bash
cd fontes
./gradlew test                         # in-memory H2
./gradlew :store-tests:testPostgres    # the same suite on embedded PostgreSQL
./gradlew check                        # both
```

- The repository tests (`repository/Abstract*RepositoryTest`) run in two modes: straight on the repository and through the full REST path.
- `transaction/` covers the atomic checkout and the remote transaction; `repository/SecuredRest*` covers the API's data policies (what each role reads and writes, per-user scope); `security/` the SCRAM login and its throttle of failed sign-ins (shared by instances), the `/api/auth` limit per address, and the sessions — their life on the client and their end across instances; `schema/` covers the migrations and the jOOQ classes; `doc/` checks the OpenAPI against the domain.
- `testPostgres` uses an embedded PostgreSQL, which starts on its own. With `STORE_TEST_PG_URL` (+ `_USER`, `_PASSWORD`) it uses that server **and deletes its data**.
- `live/LiveBackendSmokeTest` only runs with `STORE_LIVE_BACKEND_URL` set: it exercises a running backend and writes a purchase to it.

Details in the [persistence architecture](../../../docs/architecture-persistence.md#tests).
