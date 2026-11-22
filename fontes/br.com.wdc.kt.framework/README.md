# Framework

Reusable base libraries, independent of the Storefront application.

## Modules

- [br.com.wdc.kt.framework.commons/](br.com.wdc.kt.framework.commons/) — Utilities and base types (KMP)
- [br.com.wdc.kt.framework.cube/](br.com.wdc.kt.framework.cube/) — Cube MVP engine (KMP)
- [br.com.wdc.kt.framework.domain/](br.com.wdc.kt.framework.domain/) — Data access contracts: repository, criteria, projection, transaction (KMP)
- [br.com.wdc.kt.framework.persistence/](br.com.wdc.kt.framework.persistence/) — Transactions over JDBC and remote transaction coordinator (JVM)
- [br.com.wdc.kt.framework.jooq/](br.com.wdc.kt.framework.jooq/) — Declarative queries over jOOQ, for H2 and PostgreSQL (JVM)
- [br.com.wdc.kt.framework.rest/](br.com.wdc.kt.framework.rest/) — REST APIs over Javalin: routes, security, errors, per-request context and transaction, OpenAPI (JVM)

## Using as a dependency

The six modules are published on Maven Central, under the [MIT](LICENSE) license, in the group `io.github.mrcdom.wdc.kt`. All ship with the same version.

| Artifact | Platforms | Brings along |
|---|---|---|
| `framework-commons` | JVM, Android, JS, WasmJS, iOS | — |
| `framework-cube` | JVM, Android, JS, WasmJS, iOS | `framework-commons` |
| `framework-domain` | JVM, Android, JS, WasmJS, iOS | `framework-commons` |
| `framework-persistence` | JVM | `framework-domain` |
| `framework-jooq` | JVM | `framework-persistence`, jOOQ |
| `framework-rest` | JVM | `framework-persistence`, Javalin 7, Gson |

Targets: JVM (Java 21), Android (minSdk 26), JS, WasmJS, iosArm64 and iosSimulatorArm64. Compiled with Kotlin 2.3: consumers on the JS, WasmJS and iOS targets need Kotlin 2.3 or newer.

```kotlin
// build.gradle.kts of a Kotlin Multiplatform project
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mrcdom.wdc.kt:framework-cube:0.4.0")   // navigation and presenters
            implementation("io.github.mrcdom.wdc.kt:framework-domain:0.4.0") // data access contracts
        }
    }
}

// build.gradle.kts of a JVM server module
dependencies {
    implementation("io.github.mrcdom.wdc.kt:framework-jooq:0.4.0") // already brings persistence and domain
    implementation("io.github.mrcdom.wdc.kt:framework-rest:0.4.0") // REST API over Javalin
}
```

| Version | What it brought |
|---|---|
| 0.4.1 (to be published) | Security fix: a criterion from the wire whose operator and values disagree is now refused (400), instead of vanishing from the SQL — a `delete` like that wiped the table. A navigation requested from outside waits for the current one, instead of interrupting it. Remote transaction: a failing request condemns it, an unknown one answers 410, a rollback during a request waits for it to finish, owner = user + client. In `framework-cube`, `ObservableViewState`, and places in a tree declared by keys classes (`CubeTreePlace`, `CubeKeys`). Details in [0.4.1 release notes](#041-release-notes). |
| 0.4.0 | `insert` by projection, with the complete bean back; `afterCommit` in transactions; in `framework-rest`, `Server-Timing`, cache and security headers, Swagger UI page, per-minute rate limit and new reads in `RestCall`; `isIn(empty)` now filters by none. Details and changes to make in [0.4.0 release notes](#040-release-notes). |
| 0.3.0 | **`framework-rest`**, REST APIs over Javalin 7: resource routes and per-entity RPC, pluggable security, exception map, per-request context and transaction, CORS, gzip, OpenAPI generated from the routes (also from `@ApiDoc`), test server; Kotlin bodies are read through the primary constructor (defaults apply, no `val` written by reflection, ready for JDK 26). **`framework-domain`**: `@ApiDoc`; `containing` and `startingWith` search the text literally (`%` and `_` are no longer wildcards), and the translator declares `ESCAPE '\'`. **`framework-jooq`**: a criterion over several columns (`translateAny`); `UUID` and `DATE` (as `LocalDate`) columns in the JSON projection; reading `TIMESTAMP` without time zone without throwing an exception per value. |
| 0.2.0 | `framework-domain`, `framework-persistence` and `framework-jooq`; compiled with Kotlin 2.3 |
| 0.1.0 | `framework-commons` and `framework-cube` |

## 0.4.1 release notes

Not yet published. Fixes defects found while reviewing a port of the architecture to another language.

| Module | Change | Breaking? | What to do |
|---|---|---|---|
| `framework-domain` | **Security.** `Criterion.restore` (reading the criterion that comes from the wire) refuses with `InvalidRequestException` (400) a number of values the operator does not accept (`BETWEEN` with one, `EQ` with none, `GT` with two) and `null` in an ordering comparison, `LIKE`, `ILIKE` or `BETWEEN`. Before, the predicate was kept, the field counted as set and the translator dropped it from the SQL: a `delete` "with criterion" became a `DELETE` without `WHERE`, and `count`, `fetch` and the per-user scope lost the filter. `EQ`/`NE` with `null` and empty `IN` are still accepted. | Behavior | None, unless a client sent malformed predicates: it now gets 400. |
| `framework-jooq` | `CriterionTranslator` refuses (`IllegalArgumentException`) a predicate whose operator and values disagree, instead of discarding it. | Behavior | A criterion built in code only gets there through `restore`; nothing to do. |
| `framework-cube` | **Navigation requested during another one.** `execute()` decides, no longer `navigate()`: requested from inside the current one (the presenter, in `applyParameters`) it is a redirect and interrupts it, as before; requested from outside (view action, history) it **waits** for the current one to finish. Only the last request waits; an earlier one, or one that was waiting for a navigation that failed, returns `false`. The module now depends on `kotlinx-coroutines-core`. | Behavior | Nothing, unless a presenter launches another coroutine to navigate and waits for it inside `applyParameters` (it would wait for itself): navigate directly. |
| `framework-cube` | With `false` in `applyParameters`, the current place is that of the presenter that stopped the composition, not the last step of the route (never visited). | Behavior | Nothing. |
| `framework-cube` | **`ObservableViewState`**: a view state whose fields (`var x by field(initial)`) ask for a repaint when assigned a different value; the presenter wires it with `observed(state)` (in `AbstractCubePresenter` and `AbstractChildPresenter`) and no longer calls `update()` after each change. A change inside an object already assigned is not seen: assign a copy or call `changed()`. | No | Optional: extend `ObservableViewState`, declare the fields with `field(...)`, wrap the state in `observed(...)` and drop the `update()` calls that follow a field assignment. |
| `framework-cube` | **`CubeTreePlace` and `CubeKeys`**: a place that knows its parent (`CubeTreePlace.ROOT`, the application itself — no presenter, id -1, not a step of any path —, `ROOT.child(name, factory)` for a top-level place and `place.child(name, factory)` below it; ids grow from the top to the leaves), and a base for the application's keys classes (the application, the intent — a new one from the current state when none is given — and `flip()`). Each keys class declares its place in its companion, from the place of the keys it extends, so the class tree is the place tree. `CubeApplication.flip(place, intent)` navigates through the path of the place. | No | Optional: declare each place in its keys class and navigate with `XxxKeys(app).flip()`, instead of an enum of places and one navigation function per place. |
| `framework-cube` | **`CubeApplication.applyParameters(intent, initialization, deepest)`**: the application, root of the place tree, takes part in every navigation before the first step, in the redirect-marked context (a navigation requested in there is a redirect). `initialization` is `true` only the first time; `deepest` is `true` when there are no steps (`flip(CubeTreePlace.ROOT)`); `false` stops the navigation at the root. On rollback it runs first, with the source intent (`false, false`). The default returns `true`. | No | Optional: move a root presenter's job (page frame, session restore, the first level's slot) into the application and declare the root keys with `CubeTreePlace.ROOT`. |
| `framework-domain` + `framework-persistence` + `framework-rest` | Unknown or expired remote transaction: `RemoteTransactionNotFoundException` (was `TransactionSystemException`), 410 in REST. In use by another request: `TransactionConflictException` (409; was `TransactionSystemException`, 500). | **Yes** (type) | Whoever caught `TransactionSystemException` for these cases catches the new types. |
| `framework-persistence` | A rollback requested while a request is using the transaction takes effect at once (outcome recorded, the `txId` accepts no more requests), but the connection only closes when the request unbinds it; a commit at that moment is `TransactionConflictException`. `suspend` always unbinds the thread. Committing a transaction marked for rollback rolls it back and throws `TransactionConflictException`, instead of answering success. `begin(ownerKey, quotaKey)` counts the cap by `quotaKey` (interface default: falls back to `begin(ownerKey)`). | Behavior | Implementers of `RemoteTransactionCoordinator` may override `begin(ownerKey, quotaKey)`. |
| `framework-rest` | With `X-Tx-Id`, the request runs as a participant (`service.required`): if it fails midway, the remote transaction is condemned and the client's commit answers 409. `ownerKey` becomes `user:<id>/client:<X-Client-Id>`, and the new `quotaKey` (`user:<id>`) keeps the cap per user. | Behavior | Whoever overrode `ownerKey` checks whether they also want `quotaKey`. |
| `framework-domain` + `framework-persistence` | **A participant that fails dooms the transaction and the owner throws.** When a block that joined the current transaction (`required`, `mandatory`, `supports`) throws and its caller catches the exception and carries on, the owner still rolls back, as before, but now throws `TransactionSystemException` ("The transaction was rolled back: a call inside it failed.") instead of returning the block's result. An explicit `setRollbackOnly()` still rolls back without throwing; `isRollbackOnly()` is `true` in both cases. | Behavior | Code that caught a nested failure and expected the outer block to return normally now gets the exception: let it propagate, or use `requiresNew` for a step that may fail on its own. |
| `framework-persistence` | `RemoteTransactionCoordinatorImpl.startReaper(executor)` sweeps expired remote transactions periodically (`RemoteTransactionOptions.reapIntervalMs`, `reapIntervalSeconds` in the configuration, 15 s by default); `reapExpired()` is public. Before, the sweep only ran on `begin`: on a quiet server, an abandoned transaction kept its connection and row locks past the timeouts. | No | Call `startReaper` once at startup with a `ScheduledExecutorService` you already have, and close the handle at shutdown. |
| `framework-rest` | `commit`, `rollback` and `status` of the remote transaction without the `X-Tx-Id` header answer 400 (`InvalidRequestException`), not 403: the request is malformed, access was not refused. A foreign owner is still 403. | Behavior | A client that told the cases apart by the status checks 400. |
| `framework-rest` | **Queries have a cap**: `RepositoryRpc.maxLimit` (1000 by default, `null` turns it off). `fetch` with `limit` above it, `fetch-page` with `pageSize` outside `1..maxLimit`, and a `fetch` without `limit` that would bring more rows answer 400 — refused, not cut short. Before, `limit` had no ceiling and a missing one brought the whole table. | Behavior | A client that read more than 1000 rows at once pages the query, or the application raises `maxLimit`. |
| `framework-rest` | **`scope` also guards `update`.** Before `beforeUpdate`, `RepositoryRpc` counts the target row with the key plus `scope`; outside the caller's reach, `AccessDeniedException` (403); without the key, 400. Before, the reach of an update depended entirely on `beforeUpdate`. Without a `scope` set, nothing changes (no extra query). | Behavior | An application that checked ownership in `beforeUpdate` can drop that check; keep only what the scope cannot see (reassigning the owner). |
| `framework-rest` | **Reads join the client's remote transaction.** With `X-Tx-Id`, the `RepositoryRpc` reads (`count`, `fetch`, `fetch-page`, `fetch-by-id`, `GET {id}`) run in it as participants, through the new `RestCall.inRemoteTransaction` / `RestTransactions.joinRemote`: they see the transaction's own writes and are not blocked by its row locks (on SQL Server without RCSI, a read of a row the transaction locked waited for the lock timeout). Without the header, nothing changes. | Behavior | A client that relied on reads inside a remote transaction seeing only committed data reads with `notSupported` (no header). |
| `framework-rest` | The KDoc of `RepositoryRpc.scope` (and the README) warns: add the scope with `field.and().eq(owner)`, never just `eq`. On a field the client marked with `"or": true`, `eq` becomes one more alternative and the scope is gone. | No | Check your `scope`s: replace `eq(owner)` with `and().eq(owner)`. |

## 0.4.0 release notes

One row per change. **Breaking** means the consumer's code needs adjusting; the next column says which.

| Module | Change | Breaking? | What to do |
|---|---|---|---|
| `framework-rest` | A constructor parameter whose field is `@Transient` (or `transient`) is no longer read from JSON: the key is ignored, and it gets the default or `null`. Without a default and not nullable, the first read of the class fails with `JsonDeclarationException` (500, not 400). | No | Give a default to, or make nullable, any transient parameter that does not have one yet. |
| `framework-domain` | `ProjectionList(bean, criteria, limit, offset)` and `ProjectionValues.singletonList(bean, criteria, limit, offset)`; `limit` and `offset` become `val`. | No | `withLimit`/`withOffset` are deprecated (they return a copy) and go away in the next version: pass the slice to the constructor. |
| `framework-domain` + `framework-jooq` | **Behavior change:** `isIn(empty)` (and `isIn()` with no arguments) filters by none: the search matches no row, `count` gives 0 and `delete` deletes nothing. `isIn(null)` still does not filter. Before, empty did not filter, and returned or deleted everything. | Behavior | Whoever relied on "empty = no filter" passes `null`. |
| `framework-jooq` | `addLocalDate` → `addDay`, `addUuid` → `addUid`, `addInstantFromOdt` → `addIzt`, `addInstantFromLdt` → `addIlt` (also `addDay`/`addUid` in `RelationKey`). | No | The old names are deprecated in this version and go away in the next: switch to the new ones. |
| `framework-jooq` | `addEnm(…, toColumn = { it.code })`: the text the constant writes to the column, the inverse of `parser`; by default, the name. It is what `insert` writes and what `JsonQueryField.toColumn` returns. | No | An enum stored in the database by its own code passes `toColumn`; without it, `insert` would write the constant's name. |
| `framework-jooq` | In `RelationKey`, `addLocalDate` and `addUuid` go away without a transition period: they become `addDay` and `addUid`. | **Yes** | Rename them in the key declarations (`key = { … }`). |
| `framework-domain` | `newProjection()` becomes `ownFieldsProjection()`: the entity's own fields, associations only by key, no collections or derived fields. | **Yes** | Mechanical rename in implementers and callers. |
| `framework-domain` | `ref(::Entity, key) { id = it }` builds an entity with just the key; `null` in, `null` out. | No | Replace each entity's own `ref(id)`, if you like. |
| `framework-domain` + `framework-persistence` | `TransactionService.afterCommit(task)`: runs the task after the commit of the current physical transaction; discarded if it is rolled back (exception or `setRollbackOnly`); without a transaction, runs at once; a task failure goes to the log. `AfterCommitTasks` is the shared queue and runner. | **Yes** | Implementers of `TransactionService` add `afterCommit`; `AfterCommitTasks` does the work. |
| `framework-rest` | `serverTiming { names = false }` turns on the `Server-Timing` header (`total`, plus the measurements from `call.timing`/`call.timed`), also on error responses; with CORS, the header is exposed. Off by default. | No | Replace your own `before`/`after` filters with the option. |
| All | Compiled with Kotlin 2.4.21. Published dependencies: Javalin 7.2.4, Gson 2.15.0, jOOQ 3.21.9 (which requires Java 21), kotlinx-coroutines 1.11.0, kotlinx-datetime 0.8.0, `kotlin-reflect` 2.4.21. | No | Whoever pins versions of these libraries checks compatibility; jOOQ 3.21 removes `DataType.convert` (use `DSL.val(value, column).value`). |
| `framework-rest` | `RestTestServer` listens only on `127.0.0.1`, and `baseUrl` uses that address (before, all interfaces and `localhost`, which on macOS could land on another process's port). | No | Nothing, unless a test builds the URL with `localhost` by hand: use `server.baseUrl`. |
| `framework-rest` | `securityHeaders { noCache; headers }` sets the cache headers (only when the route did not set `Cache-Control`) and the security headers (`nosniff`, `X-Frame-Options`, `X-XSS-Protection`, in an editable map) on every API response, including errors, refusals, 404/405 handled by the library and the CORS preflight. Off by default. | No | Replace the `after` filters (of the API and of Javalin) that did this with the option. |
| `framework-rest` | `openApi { ui { path; title; document(url, name); primary; includeOwn } }` serves the Swagger UI page from the official webjar (no CDN), with the selector when there are several documents; only the files the page loads, cached for a day. The `org.webjars:swagger-ui` webjar is up to whoever turns the page on. | No | Replace your own page with the option and declare the webjar. |
| `framework-rest` | `security { rateLimit { key; perMinute; clock } }` limits each caller's requests per minute (fixed window, in memory, per instance), after authentication and before the rule; above the limit, `RateLimitedException`, 429 with `Retry-After` through the default mapping. | No | Move the counting out of the `Authenticator` into the option; remap `RateLimitedException` if the error format is different. |
| `framework-rest` | In `RestCall`: `requiredQueryLong`, `requiredQueryInt` and siblings (missing and malformed with the same failures as `requiredQuery` and `queryLong`); `explicitNulls()`, the body fields that came with an explicit `null`; `basicCredentials()`, user and password from `Authorization: Basic`. | No | Replace your own reads, if you like. |
| `framework-domain` + `framework-jooq` + `framework-rest` | `Repository.insert(bean, projection = null)`: the projection says what goes into the `INSERT` (left out = column default; without a projection, the fields with a value), and the bean comes out complete. `JsonQuery.fields()` exposes the mapping, with `mark` and `toColumn` for the repository's own writes; `JsonQuery.insert(bean, projection, onConflictDoNothing)` does the `INSERT` and reads the row back through the fetch projection. The `RepositoryRpc` `insert` accepts `{"entity", "projection"?}` (and the bare entity) and answers `{"success", "id", "entity"}`. | **Yes** | Implementers of `Repository` add the `projection` parameter to `insert`; the jOOQ-based repository can delegate to `QUERY.insert`. An RPC client can read `entity` from the response. |

## Publishing

The configuration is in [`fontes/build.gradle.kts`](../build.gradle.kts) (the `publishedModules` block), with the `com.vanniktech.maven.publish` plugin. There is a single version, defined there.

```bash
cd fontes

# Check the artifacts locally (~/.m2), unsigned
./gradlew publishToMavenLocal

# Send to Maven Central (requires credentials and a GPG key in ~/.gradle/gradle.properties)
./gradlew publishToMavenCentral
```

Both tasks exist only in the published modules. Sending creates a *deployment* on the [Central Portal](https://central.sonatype.com/publishing/deployments) that must be released manually. A released version cannot be changed or removed.
