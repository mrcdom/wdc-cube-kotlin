# framework-rest

REST APIs on **Javalin 7** (JVM, Java 21). The module was written to reimplement a backend that already has a fixed contract, so it controls what goes out in the response: status, error body, nulls, dates and the response to callers without credentials.

Published on Maven Central as `io.github.mrcdom.wdc.kt:framework-rest`. Brings along `framework-persistence` (and, through it, `framework-domain` and `framework-commons`), Javalin and Gson.

```kotlin
dependencies {
    implementation("io.github.mrcdom.wdc.kt:framework-rest:0.4.0")
}
```

This README describes the code in this repository, which is ahead of the last published version (0.4.0): the data policy, `NotFoundException` (404), `RemoteTransactionNotFoundException` (410), `inRemoteTransaction` and the query cap of `RepositoryRpc` come with 0.5.0, not yet published. See the [0.5.0 release notes](../README.md#050-release-notes).

## Example

```kotlin
val roles = RoleRules("ROLE_")

val api = RestApi {
    json = GsonJsonMapper(JsonOptions.springDefaults())        // nulls present, ISO dates
    security {
        authenticator = BearerAuthenticator { token -> jwt.resolve(token) }   // the application validates the token
        unauthenticatedStatus = 403                            // Spring Security without an entry point answers 403
        protectUnmatched = true                                // anonymous gets 403 even on a nonexistent path
        permitAll("POST /auth/login", "GET /catalog/featured", "/swagger-ui/**")
    }
    errors {
        errorBody = { status, _, request -> mapOf("status" to status, "path" to request.path) }   // the /error body
        on<AccessDeniedException> { _, _ -> ErrorResponse(403, mapOf("message" to "Access denied.")) }
        on<UniqueException> { e, _ -> ErrorResponse(400, mapOf(e.field to e.message)) }
    }
    transactions { service = TransactionServiceImpl { dataSource } }
    cors { anyOrigin(); maxAge = 3600 }
    compression { mimeTypes += CompressionOptions.SPRING_MIME_TYPES }
    openApi { path = "/v3/api-docs"; title = "Orders" }
    before { request -> request[APP_VERSION] = request.header("X-App-Version") }

    group("/order", tags = listOf("Order")) {
        get("/{id}", rule = roles.hasAnyRole("ADMIN", "SELLER")) { call ->
            orders.find(call.pathLong("id")) ?: RestResponse.noContent()
        }
        post("/search", transactional = true) { call -> orders.search(call.body<SearchRequest>()) }
    }
}

api.createServer().start(8080)          // or api.install(config) inside the application's Javalin.create { … }
```

## Pipeline of each route

1. **`before` filters**, in registration order (all of them, or only those matching a pattern). They can store attributes in the context or refuse by throwing an exception.
2. **Authentication**: the `Authenticator` turns the credential into a `RestPrincipal`.
3. **Access rule**: the path's rule (public or `defaultRule`) combined with the route's.
4. **Handler**, inside the request transaction if the route is `transactional`.
5. **Response**: the returned value, or the exception map on failure.
6. **`after` filters**: always run, and receive the failure if there was one.

The `RequestContext` is current from the first step to the last, even inside coroutines on another dispatcher. The response, success or error, is serialized **inside** that context, so a JSON adapter that depends on the request (the client's time zone, for example) sees the context.

## Routes

`get`, `post`, `put`, `patch`, `delete` and `route(verb, …)` for any other verb. The path uses Javalin's format: `{x}` matches one segment, `<x>` matches several. The trailing slash is ignored. `group(prefix, rule, tags) { … }` groups routes and adds the group's rule and tags to each route's.

Options of each route: `rule` (access rule), `public` (no credential required), `transactional`, `hidden` (left out of OpenAPI) and `doc` (documentation).

The handler receives a `RestCall`:

| Reading | Methods |
|---|---|
| Path | `path`, `pathLong`, `pathInt`, `pathUuid` |
| Query (`null` if absent) | `query`, `queries`, `queryLong`, `queryInt`, `queryDouble`, `queryBoolean`, `queryEnum<T>`, `queryUuid`, `queryLocalDate`, `queryLocalDateTime` |
| Required query | `requiredQuery` and the typed `requiredQueryLong`, `requiredQueryInt`, `requiredQueryDouble`, `requiredQueryBoolean`, `requiredQueryEnum<T>`, `requiredQueryUuid`, `requiredQueryLocalDate`, `requiredQueryLocalDateTime` |
| Header | `header`, `requiredHeader`, `basicCredentials()` (user and password from `Authorization: Basic`, or `null`) |
| DTO body | `body<T>()` (requires a body), `bodyOrNull<T>()`; accepts an array at the root (`body<List<Item>>()`); `explicitNulls()` |
| Entity body | `entity(codec)`, `entityForUpdate(codec)`, `bodyInput()` |
| Other | `bodyText`, `bodyBytes`, `principal`, `requirePrincipal<P>()`, `inTransaction { }`, `http` (Javalin's `Context`) |

A missing or malformed parameter throws `RequestParameterException`; an unreadable body throws `RequestBodyException`. Both answer 400.

`explicitNulls()` returns the top-level body fields that came with an explicit `null`. A DTO reads absent and null the same way; in a partial update, this is what separates "leave alone" (absent) from "clear" (null). It doesn't consume the body: `body<T>()` still works before or after.

`basicCredentials()` decodes the Base64 and then each part as a form (`%3A`, `+`), as OAuth sends `client_id` and `client_secret` (RFC 6749, 2.3.1). Only the first `:` separates, so the password may contain others. Without the header, with another scheme or with an invalid value, it returns `null`.

**The returned value is the response:**

| Value | Response |
|---|---|
| `Unit` or `null` | empty body, 200 (unless `call.status(…)`) |
| `RestResponse` | its status, headers, Content-Type and body: `ok`, `created`, `noContent`, `status` |
| `RawJson` | the JSON as is (this is how entities go out through the `ModelCodec`s) |
| `String` | `text/plain` |
| `ByteArray`, `InputStream` | `application/octet-stream` (other content type: `RestResponse(contentType = …)`) |
| any other value | JSON through the DTO mapper; `Boolean` goes out as `true` |

## JSON

Repository entities travel through their `ModelCodec`, without reflection. The rest (DTOs) goes through the `GsonJsonMapper`, configured by `JsonOptions`:

- **Nulls**: `nulls = INCLUDE` writes `"field": null` (like Jackson and Spring) and `nulls = OMIT` omits the field (like Gson). The `@JsonNulls(…)` annotation on a class or field overrides the global policy, and the field's overrides the class's.
- **Dates**: there is one formatter per type in `dates` (`localDate`, `localDateTime`, `instant`, `offsetDateTime`, `zonedDateTime`, `localTime` and `java.util.Date`). The default is Jackson's without timestamps (`2026-09-27T09:00:00`, with a fraction only when there are nanos). On reading, `LocalDateTime` also accepts `…Z` or an offset, which is converted to UTC. To change the format of a single field, use Gson's `@JsonAdapter`.
- **Free tuning**: `customize { builder -> … }` runs after the library's adapters, and what it registers wins.
- `JsonOptions.springDefaults()` reproduces Spring Boot without configuration.
- **Kotlin classes are read through the primary constructor**, not through Gson's field writing. A field absent from the JSON uses the parameter's default value; a nullable without a default becomes `null`; a non-nullable without a default (absent, or explicit `null`) refuses the body with 400. Validation in `init` applies. `val` fields are not written by reflection, which JDK 26 warns it will block. `@SerializedName` (with alternate names) and `@JsonAdapter` on the field apply (with `TypeAdapter` or `TypeAdapterFactory`; not `JsonSerializer`/`JsonDeserializer`); a `var` declared outside the constructor is written afterwards, and a `val` outside the constructor is ignored. A transient field parameter (`@Transient`) is not read from the JSON, as in Gson: the key, if present, is ignored, and it gets the default or `null`; without a default and not nullable, it is a declaration error (`JsonDeclarationException`, response 500, not 400). Enums, `object`s, records and non-Kotlin classes go through Gson.

## Errors

`errors { … }` configures the exception-to-response map. The lookup climbs from the exception's class to its superclasses and interfaces; a handler that returns `null` passes to the next level. An exception without a handler falls into `fallback` (500).

| Exception | Default status |
|---|---|
| `InvalidRequestException`, including `RequestParameterException` and `RequestBodyException` | 400 |
| `AuthenticationException` (no credential, or credential refused) | `unauthenticatedStatus` |
| `AccessDeniedException` | 403 |
| `NotFoundException` | 404 |
| `TransactionConflictException` | 409 |
| `TooManyRequestsException` (including `TransactionLimitExceededException`) | 429, with `Retry-After` when it knows the wait |
| `RateLimitedException` (per-minute limit) | 429, with `Retry-After` |
| `RemoteTransactionNotFoundException` (unknown or expired remote transaction) | 410 |
| Javalin's `HttpResponseException` (`NotFoundResponse`…) | its status |

The body of all of these comes from `errorBody(status, message, request)`, which by default is `{"error": "<message>"}`. For paths without a route (404/405) to go through the map too, set `handleUnmatched = true`; without it, the body is Javalin's.

## Security

There is no JWT or login in the module. The application provides the `Authenticator` (`BearerAuthenticator { token -> … }` reads `Authorization: Bearer`) and a `RestPrincipal` with `id` and `roles`, which it reads back with `call.requirePrincipal<YourType>()` or `request.principalAs<YourType>()`.

| Spring Security | framework-rest |
|---|---|
| `requestMatchers("/x/**").permitAll()` | `permitAll("/x/**")`, `permitAll("POST /auth/login")` |
| `anyRequest().authenticated()` | `defaultRule = AccessRules.authenticated` (the default) |
| `@PreAuthorize("hasRole('ADMIN')")` | `rule = RoleRules("ROLE_").hasRole("ADMIN")`, which accepts `ADMIN` and `ROLE_ADMIN` |
| `hasAnyRole(…)`, SpEL over the principal or the parameters | `hasAnyRole(…)`, `AccessRules.principalOf<P> { … }`, `AccessRules.rule { request -> … }`, combined with `and` and `or` |
| `Http403ForbiddenEntryPoint` | `unauthenticatedStatus = 403` |
| authorization before routing | `protectUnmatched = true` |

- Without a credential on a route that requires one, the response is `unauthenticatedStatus` (401 by default). With a credential but no permission, the response is that of `AccessDeniedException` (403). The bodies of the two cases may differ.
- A refused credential on a public route is ignored and the request proceeds anonymously. `rejectInvalidCredentialsOnPublicRoutes = true` changes that.
- A rule that depends on the body or the database stays in the handler, which throws `AccessDeniedException`.

**Per-minute limit.** `rateLimit { key; perMinute; clock }` limits the requests of each caller. It is evaluated after authentication and before the access rule, and a request without a principal doesn't count. The window is fixed, per clock minute. Above the limit, `RateLimitedException`, which the default mapping answers with 429 and `Retry-After` (the seconds until the minute turns); an `on<RateLimitedException>` in `errors { }` changes the response. The count is kept in memory, per instance: with N instances, the effective limit is up to N times the configured one.

```kotlin
security {
    authenticator = …
    rateLimit {
        key = { p -> p.id }                                         // the default
        perMinute = { p -> (p as Partner).requestsPerMinute }       // null = no limit (the default)
    }
}
```

## Per-request context

`RequestContext.current()` works from any layer, even inside `withContext(Dispatchers.IO)`. It gives access to `method`, `path`, `header`, `queryParam`, `pathParam`, `principal` and typed attributes:

```kotlin
val TIMEZONE = RequestKey<ZoneId>("timezone")

before { request -> request[TIMEZONE] = request.header("X-Timezone")?.let(ZoneId::of) }

fun clientZone(): ZoneId = RequestContext.currentOrNull()?.get(TIMEZONE) ?: ZoneOffset.UTC
```

To carry the context into a coroutine launched outside the pipeline, use `request.asContextElement()`.

## Transactions

`transactions { service = …; coordinator = … }`. On a `transactional = true` route, everything the handler writes is committed together, or nothing is. `X-Tx-Id` is optional:

1. If `X-Tx-Id` came and there is a coordinator, the write joins the client's remote transaction and is only committed on its commit. A server without a coordinator refuses the header with 400, in reads and writes: the client believes it is in a transaction, and a write made outside it could not be undone.
2. If the header didn't come but the owner has an open remote transaction, the write is refused with 409, so it isn't committed outside the transaction. `refuseWritesOutsideOpenTransaction = false` turns that off.
3. Otherwise, there is one transaction per request (`TransactionService.required`).

The owner of a remote transaction is `user:<principal.id>/client:<X-Client-Id>` (or `user:<principal.id>` without the header), or `anon:<X-Client-Id>` without a principal (configurable in `ownerKey`): the same user on two clients doesn't block itself. The cap of open transactions per owner is counted by `quotaKey` (`user:<principal.id>`), and changing the `X-Client-Id` doesn't get around it. With `X-Tx-Id`, the request runs as a participant: if it fails, the remote transaction is doomed, and the commit answers 409. `RemoteTransactionRoutes.register(this, transactions)` publishes `begin`, `commit`, `rollback` and `status` under `/api/tx`. `serviceSource` and `coordinatorSource` are queried on each request, for those who keep the services in holders.

## CORS, compression and body limit

- `cors { anyOrigin() | allowOrigins(…); allowCredentials; reflectClientOrigin; maxAge; exposedHeaders }` answers the preflight before the routes and security. An origin without a scheme means `https://`; `https://*.example.com` allows the subdomains; the port is part of the origin. A request from the server's own origin (the `Origin` matches the `Host`) is not a CORS request and passes untouched; one from an origin not allowed is answered without the CORS headers (the browser hides the answer from the page), not refused. `anyOrigin()` with `allowCredentials` is a configuration error: use `reflectClientOrigin`.
- `Cors.install(config) { … }` does the same on a Javalin server without a `RestApi` (pages, WebSocket hosts).
- `compression { gzipLevel; minSize; mimeTypes }` uses gzip. The default `minSize` is 2 KB, as in Spring Boot.
- `maxRequestSize = bytes`: a larger body gets 413.

## Server-Timing

`serverTiming()` turns on the `Server-Timing` header, which DevTools shows in the Timing tab; off if not called. Every request that goes through the library carries `total` (authentication, rule, handler, commit and serialization), also on error responses and on the 404/405 it handles. The route adds its own measurements, which go out before `total`, in registration order:

```kotlin
serverTiming { names = false }   // names = true adds desc="…"; in production, don't expose internal names
post("/order") { call -> call.timed("action", "CreateOrderAction") { createOrder(call.body()) } }
// Server-Timing: action;dur=12.3, total;dur=15.8
```

`call.timing(name, nanos, desc)` records an already measured duration; it is a plain function, which the route can pass along (`call::timing`). With `cors { … }`, `Server-Timing` enters the `exposedHeaders` on its own.

## Cache and security headers

`securityHeaders()` puts headers on every response that goes through the library: success, mapped error, refusal by authentication or rule, the 404/405 it handles (`handleUnmatched` or `protectUnmatched`), the OpenAPI document and the CORS preflight. It is off if not called.

- **Cache** (`noCache = true`): `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache` and `Expires: 0`, only when the route didn't set its own `Cache-Control`. A route that wants caching writes its own, and the response goes out without `Pragma` or `Expires`.
- **Security** (`headers`, always, overriding any value the route set): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `X-XSS-Protection: 1; mode=block`. The map accepts replacing, adding and removing.

```kotlin
securityHeaders {
    headers -= "X-XSS-Protection"                              // obsolete in current browsers
    headers["Strict-Transport-Security"] = "max-age=31536000"  // not there by default: behind a TLS proxy the request arrives over HTTP
}
```

## OpenAPI

`openApi { path; title; version; description; tag(…); schemas; parameters; securityScheme; raw }`. The document comes from the registered routes: verb, path, path parameters and the security of each operation (a public route gets `security: []`). DTO schemas are generated by reflection, with the names Gson uses. Each route adds what it has in `doc = { summary = …; query("page", Int::class.java); accepts<Req>(); returns<Resp>(200); response(404); raw(mapOf(…)) }`, and `raw` overrides what was generated. The document is served at `path`, and also available in `api.openApiJson`.

`openApi { ui { } }` serves the Swagger UI page from the official webjar on the classpath, without a CDN. The webjar doesn't come with the library: whoever turns the page on declares it (`runtimeOnly("org.webjars:swagger-ui:5.34.1")`, or another 5.x version). The version is read from the webjar itself, and without it building the `RestApi` fails.

```kotlin
openApi {
    title = "Orders"
    ui {
        path = "/swagger-ui"                               // the default
        document("/partner/openapi.json", "Partner API")   // another document: another RestApi's, a static file
        primary = "Orders"                                 // the one opened when there are several; default, the first
    }
}
```

`GET /swagger-ui.html` redirects (302) to `/swagger-ui/index.html`, which is the page: with one document, just it; with several, the selector at the top. `includeOwn = false` removes the API's own document from the list. Of the webjar's files, only those the page loads are served (`swagger-ui.css`, `swagger-ui-bundle.js`, `swagger-ui-standalone-preset.js`, `favicon-32x32.png`), with `Cache-Control: public, max-age=86400`; the rest is 404. The routes are public and left out of the document.

## Generic RPC per entity

`RepositoryRpc(basePath, codec, repository, …)` publishes a repository's operations: `insert`, `update`, `delete`, `count`, `fetch`, `fetch-page`, `fetch-by-id` and `GET {id}`. It is the protocol the WDC Store `HttpRepository` client speaks. Access stays with the application, in one of two ways: a **data policy** (`policy`, below), or `rule` (per operation), `scope` (restricts the criteria to the caller), `sanitizeProjection` and `sanitizeOutput`. The write hooks, `beforeInsert` and `beforeUpdate`, run inside the transaction either way. For a read-only repository, use `writes = false`. The criteria's alternative groups (`anyOf`) do not travel: the key is refused (400).

**`scope` also guards `update`** (without a policy; a policy has [its own rule](#data-policy)): before `beforeUpdate`, the RPC checks that the target row is within the caller's reach (the key plus `scope`) and answers 404 otherwise — the same as a row that doesn't exist, so the refusal doesn't tell the row is there. What the scope cannot see — moving the row to another owner — stays with `beforeUpdate`.

**`scope` adds the reach to criteria that came from the caller**, with the disjunction the caller chose on each field. Use the conjunctive field, `criteria.ownerId.and().eq(owner)`, and never just `eq`: on a field sent with `"or": true`, `eq` becomes one more alternative (`ownerId IS NOT NULL OR ownerId = :owner`) and the caller reaches everything.

**Reads join the client's remote transaction.** With `X-Tx-Id`, `count`, `fetch`, `fetch-page`, `fetch-by-id` and `GET {id}` run in it, as participants: they see what it already wrote and are not blocked by the rows it locked. Without the header, they run with no transaction. A handler of your own does the same with `call.inRemoteTransaction { … }`.

**Queries have a cap**, `maxLimit` (1000 by default; `null` turns it off). A `limit` or `pageSize` above it is 400, and so is a `fetch` without `limit` that would bring more rows: it is refused, not cut short, so the client never takes a partial list for the whole one. Page with `limit` and `offset`, or with `fetch-page`.

`insert` receives `{"entity", "projection"?}` (or, as before 0.4.0, the bare entity) and answers `{"success", "id", "entity"}`, with the entity as the repository left it — the generated key and the column defaults —, passed through `sanitizeOutput` (with a policy, shaped as a read of what it carries). When nothing was inserted, only `{"success": false}`.

## Data policy

The client of a repository RPC assembles the request: any criteria, any projection. A **data policy** (package `policy`) says, per role, what it may do with an entity's data — the rows it reaches, the fields it reads as they are or masked, the fields it writes, whether it deletes —, and the RPC applies it to every request. Set `rpc.policy` before `register`; it then takes over the authorization of the routes (`rule`, `scope` and the `sanitize*` hooks are not consulted) and the routes only require an authenticated caller. The model is **allow-wins**: a caller gets the grants of all their roles (`RestPrincipal.roles`), and the fields are decided **per row**, by the grants that reach that row.

```kotlin
val ORDER = EntityDescriptor("order", ::Order, ::OrderCriteria) {
    projection(OrderCriteria::projection, OrderCriteria::withProjection)
    anyOf(OrderCriteria::anyOf, OrderCriteria::withAnyOf)
    key(Order::id, ProjectionValues.i64)
    value(Order::status, ProjectionValues.str)
    value(Order::document, ProjectionValues.str)
    relation(Order::customer) { CustomerPolicy.POLICY }                     // read through the customer's policy
    collection(Order::items, inverse = OrderItem::order) { ItemPolicy.POLICY }
    criterion("orderId", Order::id)                                          // criteria field → entity field
    criterion("customerId", Order::customer)
    orderings { c -> if (c.orderBy == OrderBy.BY_STATUS) setOf(Order::status) else emptySet() }
    defaultShape { Order().apply { status = ProjectionValues.str; items = ProjectionValues.singletonList(ItemPolicy.defaultShape(), null) } }
}

object OwnOrders : RowScope<Order, OrderCriteria> {
    override fun restrict(group: OrderCriteria, requester: RestPrincipal) { group.customerId.eq(requester.id.toLong()) }
    override suspend fun covers(row: Order, requester: RestPrincipal) = row.customer?.id == requester.id.toLong()
    override fun projection() = Order().apply { customer = Customer().apply { id = ProjectionValues.i64 } }
}

val POLICY = DataPolicy(ORDER) {
    grant("ADMIN") {
        read(Order::status, Order::document, Order::customer, Order::items)
        update(Order::status)
        delete()
    }
    grant("CUSTOMER") {
        rows(OwnOrders)
        read(Order::status, Order::customer, Order::items)
        mask(Mask.keepLast(2), Order::document)
        create(Order::status, Order::customer, Order::items)
    }
}

rpc.policy = POLICY
```

- **The entity description** names each field by its property reference (the wire name defaults to the property's): a **value**, a **relation** to one row of another entity, or a **collection** of rows of another entity — the latter two read through the other entity's policy, so an entity has one set of rules wherever it appears. `writeOnly(...)` declares a field no one reads through the API (a password): no grant may read it, and asking for it is refused even with security off. Every criteria field must map to an entity field (`criterion`, or the same name), checked when the description is built.
- **Reading** (`fetch`, `fetch-page`, `fetch-by-id`, `GET {id}`, `count`): a projected field no grant reads is refused with 403, **naming the fields**; filtering or sorting by a field counts as reading it, and takes a grant that reads it as it is — only the rows of such grants are returned. The rows of scoped grants are added to the criteria as alternative groups (`Criteria.anyOf`, translated by the repository as an `OR` of the groups, each the `AND` of its fields, joined with the rest of the criteria by `AND`). Each row then shows, per field, what the grants that reach it allow: as it is, masked, or nothing. What is asked inside a relation or a collection goes through the related policy; the rows of a collection are restricted by it too. **Without a projection**, the entity's default shape (`defaultShape`, or every field but collections and write-only ones), cut to what the caller reads — never refused.
- **Creating**: one grant must allow every field sent and reach the new row; a relation is written by its key only; the rows of a collection are created through their entity's policy, as rows of the new one. The answer's `entity` is shaped as a read.
- **Updating**: one grant must reach the stored row, allow every changed field, and still reach the row after the change (an owner cannot hand a row to someone else). A row no updating grant reaches is 404 when the caller cannot read it either, 403 when it can.
- **Deleting**: only the rows of the grants that delete, filtered by fields they read.
- **Security off** (no principal): every row and field is reached and the writes are not checked, but write-only fields are still refused and never returned; a read without a projection keeps the repository's default.

`DataAccess` (from `policy.accessFor(principal)`) also serves a handler of your own: `prepareRead`, `restrictCount`, `checkCreate`, `checkUpdate`, `restrictDelete` and `readBack`.

## Tests

`RestTestServer(api)` starts the API on a free port, with the same pipeline as production:

```kotlin
RestTestServer(api).use { server ->
    val response = server.client().bearer(token).header("X-Timezone", "Asia/Tokyo").get("/order/1")
    assertEquals(200, response.status)
    assertEquals("open", response.json().asJsonObject["status"].asString)
}
```

The `TestClient` sends any verb, with query, fixed or per-call headers and a text or byte body. The `TestResponse` carries `status`, `headers`, `body`, `bytes` and `json()`. Nothing is decompressed: the test sees the `Content-Encoding` as it came.

## What is not covered

- **415 for a wrong Content-Type** (Spring's `consumes`): check it in the handler, if the contract requires it.
- **Bean validation** (`@Valid`): validate in the handler and throw an application exception, with the mapping that reproduces the expected body.
- **A returned `String` goes out as `text/plain`**, as in Spring. For a JSON string, return `RawJson("\"…\"")`.
