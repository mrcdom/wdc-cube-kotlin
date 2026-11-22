# framework-rest

APIs REST sobre **Javalin 7** (JVM, Java 21). O módulo foi escrito para reimplementar um backend que já tem contrato fixo, e por isso controla o que sai na resposta: status, corpo de erro, nulos, datas e a resposta a quem chega sem credencial.

Publicado no Maven Central como `io.github.mrcdom.wdc.kt:framework-rest`. Traz junto `framework-persistence` (e, por ele, `framework-domain` e `framework-commons`), Javalin e Gson.

```kotlin
dependencies {
    implementation("io.github.mrcdom.wdc.kt:framework-rest:0.4.0")
}
```

## Exemplo

```kotlin
val roles = RoleRules("ROLE_")

val api = RestApi {
    json = GsonJsonMapper(JsonOptions.springDefaults())        // nulos presentes, datas ISO
    security {
        authenticator = BearerAuthenticator { token -> jwt.resolve(token) }   // a aplicação valida o token
        unauthenticatedStatus = 403                            // o Spring Security sem entry point responde 403
        protectUnmatched = true                                // anônimo leva 403 até em caminho inexistente
        permitAll("POST /auth/login", "GET /catalog/featured", "/swagger-ui/**")
    }
    errors {
        errorBody = { status, _, request -> mapOf("status" to status, "path" to request.path) }   // o corpo de /error
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

api.createServer().start(8080)          // ou api.install(config) dentro de um Javalin.create { … } da aplicação
```

## Pipeline de cada rota

1. **Filtros `before`**, na ordem de registro (todos, ou só os que casam um padrão). Podem guardar atributos no contexto ou recusar lançando exceção.
2. **Autenticação**: o `Authenticator` transforma a credencial em `RestPrincipal`.
3. **Regra de acesso**: a do caminho (pública ou `defaultRule`) somada à da rota.
4. **Handler**, dentro da transação da requisição se a rota for `transactional`.
5. **Resposta**: o valor devolvido, ou o mapa de exceções em caso de falha.
6. **Filtros `after`**: sempre rodam, e recebem a falha se houve uma.

O `RequestContext` é o corrente do primeiro passo ao último, inclusive dentro de corrotinas em outro dispatcher. A resposta, de sucesso ou de erro, é serializada **dentro** desse contexto, e por isso um adaptador JSON que dependa da requisição (o fuso do cliente, por exemplo) enxerga o contexto.

## Rotas

`get`, `post`, `put`, `patch`, `delete` e `route(verbo, …)` para qualquer outro verbo. O caminho usa o formato do Javalin: `{x}` casa um segmento, `<x>` casa vários. A barra final é ignorada. `group(prefixo, rule, tags) { … }` agrupa rotas e soma a regra e as tags do grupo às de cada rota.

Opções de cada rota: `rule` (regra de acesso), `public` (dispensa a credencial), `transactional`, `hidden` (fica fora do OpenAPI) e `doc` (documentação).

O handler recebe uma `RestCall`:

| Leitura | Métodos |
|---|---|
| Caminho | `path`, `pathLong`, `pathInt`, `pathUuid` |
| Query (`null` se ausente) | `query`, `queries`, `queryLong`, `queryInt`, `queryDouble`, `queryBoolean`, `queryEnum<T>`, `queryUuid`, `queryLocalDate`, `queryLocalDateTime` |
| Query exigida | `requiredQuery` e os tipados `requiredQueryLong`, `requiredQueryInt`, `requiredQueryDouble`, `requiredQueryBoolean`, `requiredQueryEnum<T>`, `requiredQueryUuid`, `requiredQueryLocalDate`, `requiredQueryLocalDateTime` |
| Cabeçalho | `header`, `requiredHeader`, `basicCredentials()` (usuário e senha do `Authorization: Basic`, ou `null`) |
| Corpo DTO | `body<T>()` (exige corpo), `bodyOrNull<T>()`; aceita array na raiz (`body<List<Item>>()`); `explicitNulls()` |
| Corpo entidade | `entity(codec)`, `entityForUpdate(codec)`, `bodyInput()` |
| Outros | `bodyText`, `bodyBytes`, `principal`, `requirePrincipal<P>()`, `inTransaction { }`, `http` (o `Context` do Javalin) |

Parâmetro ausente ou malformado lança `RequestParameterException`; corpo ilegível lança `RequestBodyException`. Os dois respondem 400.

`explicitNulls()` devolve os campos de primeiro nível do corpo que vieram com `null` explícito. Um DTO lê ausente e nulo do mesmo jeito; numa atualização parcial, é isso que separa "não mexer" (ausente) de "apagar" (nulo). Não consome o corpo: o `body<T>()` continua valendo antes ou depois.

`basicCredentials()` decodifica o Base64 e depois cada parte como formulário (`%3A`, `+`), como o OAuth manda `client_id` e `client_secret` (RFC 6749, 2.3.1). Só o primeiro `:` separa, então a senha pode conter outros. Sem o cabeçalho, com outro esquema ou com valor inválido, devolve `null`.

**O valor devolvido é a resposta:**

| Valor | Resposta |
|---|---|
| `Unit` ou `null` | corpo vazio, 200 (salvo `call.status(…)`) |
| `RestResponse` | status, cabeçalhos, Content-Type e corpo dela: `ok`, `created`, `noContent`, `status` |
| `RawJson` | o JSON como está (é como as entidades saem pelos `ModelCodec`) |
| `String` | `text/plain` |
| `ByteArray`, `InputStream` | `application/octet-stream` (outro tipo de conteúdo: `RestResponse(contentType = …)`) |
| qualquer outro valor | JSON pelo mapeador de DTOs; `Boolean` sai como `true` |

## JSON

Entidades de repositório trafegam pelo `ModelCodec` delas, sem reflexão. O resto (DTOs) passa pelo `GsonJsonMapper`, configurado por `JsonOptions`:

- **Nulos**: `nulls = INCLUDE` escreve `"campo": null` (como o Jackson e o Spring) e `nulls = OMIT` omite o campo (como o Gson). A anotação `@JsonNulls(…)` numa classe ou num campo vence a política global, e a do campo vence a da classe.
- **Datas**: há um formatador por tipo em `dates` (`localDate`, `localDateTime`, `instant`, `offsetDateTime`, `zonedDateTime`, `localTime` e `java.util.Date`). O padrão é o do Jackson sem timestamps (`2026-09-27T09:00:00`, com fração só quando há nanos). Na leitura, `LocalDateTime` aceita também `…Z` ou um deslocamento, que é convertido para UTC. Para trocar o formato de um único campo, use o `@JsonAdapter` do Gson.
- **Ajuste livre**: `customize { builder -> … }` roda depois dos adaptadores da biblioteca, e o que ele registra vence.
- `JsonOptions.springDefaults()` reproduz o Spring Boot sem configuração.
- **Classes Kotlin são lidas pelo construtor primário**, e não pela gravação de campos do Gson. Campo ausente no JSON usa o valor padrão do parâmetro; um anulável sem padrão fica `null`; um não anulável sem padrão (ausente, ou `null` explícito) recusa o corpo com 400. A validação no `init` vale. Os campos `val` não são gravados por reflexão, o que o JDK 26 avisa que vai bloquear. `@SerializedName` (com os nomes alternativos) e `@JsonAdapter` no campo valem (com `TypeAdapter` ou `TypeAdapterFactory`; `JsonSerializer`/`JsonDeserializer` não); uma `var` declarada fora do construtor é gravada depois, e uma `val` fora do construtor é ignorada. Parâmetro de campo transiente (`@Transient`) não é lido do JSON, como no Gson: a chave, se vier, é ignorada, e ele recebe o padrão ou `null`; sem padrão e sem ser anulável é erro de declaração (`JsonDeclarationException`, resposta 500 e não 400). Enum, `object`, registro e classes que não são Kotlin seguem pelo Gson.

## Erros

`errors { … }` configura o mapa de exceção para resposta. A busca sobe da classe da exceção para as superclasses e interfaces; um handler que devolve `null` passa a vez para o próximo nível. Exceção sem handler cai no `fallback` (500).

| Exceção | Status padrão |
|---|---|
| `InvalidRequestException`, inclusive `RequestParameterException` e `RequestBodyException` | 400 |
| `AuthenticationException` (sem credencial, ou credencial recusada) | `unauthenticatedStatus` |
| `AccessDeniedException` | 403 |
| `TransactionConflictException` | 409 |
| `TransactionLimitExceededException` | 429 |
| `RateLimitedException` (limite por minuto) | 429, com `Retry-After` |
| `RemoteTransactionNotFoundException` (transação remota desconhecida ou expirada) | 410 |
| `HttpResponseException` do Javalin (`NotFoundResponse`…) | o status dela |

O corpo de todos esses vem de `errorBody(status, mensagem, request)`, que por padrão é `{"error": "<mensagem>"}`. Para que caminhos sem rota (404/405) também passem pelo mapa, ligue `handleUnmatched = true`; sem isso, o corpo é o do Javalin.

## Segurança

Não há JWT nem login no módulo. A aplicação fornece o `Authenticator` (`BearerAuthenticator { token -> … }` lê `Authorization: Bearer`) e um `RestPrincipal` com `id` e `roles`, que lê de volta com `call.requirePrincipal<SeuTipo>()` ou com `request.principalAs<SeuTipo>()`.

| Spring Security | framework-rest |
|---|---|
| `requestMatchers("/x/**").permitAll()` | `permitAll("/x/**")`, `permitAll("POST /auth/login")` |
| `anyRequest().authenticated()` | `defaultRule = AccessRules.authenticated` (é o padrão) |
| `@PreAuthorize("hasRole('ADMIN')")` | `rule = RoleRules("ROLE_").hasRole("ADMIN")`, que aceita `ADMIN` e `ROLE_ADMIN` |
| `hasAnyRole(…)`, SpEL sobre o principal ou os parâmetros | `hasAnyRole(…)`, `AccessRules.principalOf<P> { … }`, `AccessRules.rule { request -> … }`, combinados com `and` e `or` |
| `Http403ForbiddenEntryPoint` | `unauthenticatedStatus = 403` |
| autorização antes do roteamento | `protectUnmatched = true` |

- Sem credencial numa rota que a exige, a resposta é `unauthenticatedStatus` (401 por padrão). Com credencial mas sem permissão, a resposta é a de `AccessDeniedException` (403). Os corpos dos dois casos podem ser diferentes.
- Uma credencial recusada numa rota pública é ignorada e a requisição segue anônima. `rejectInvalidCredentialsOnPublicRoutes = true` muda isso.
- Uma regra que dependa do corpo ou do banco fica no handler, que lança `AccessDeniedException`.

**Limite por minuto.** `rateLimit { key; perMinute; clock }` limita as requisições de cada quem chama. É avaliado depois da autenticação e antes da regra de acesso, e pedido sem principal não conta. A janela é fixa, por minuto do relógio. Acima do limite, `RateLimitedException`, que o mapeamento padrão responde com 429 e `Retry-After` (os segundos até a virada do minuto); um `on<RateLimitedException>` em `errors { }` troca a resposta. A contagem fica em memória, por instância: com N instâncias, o limite efetivo é até N vezes o configurado.

```kotlin
security {
    authenticator = …
    rateLimit {
        key = { p -> p.id }                                         // o padrão
        perMinute = { p -> (p as Partner).requestsPerMinute }       // null = sem limite (o padrão)
    }
}
```

## Contexto por requisição

`RequestContext.current()` funciona de qualquer camada, inclusive dentro de `withContext(Dispatchers.IO)`. Ele dá acesso a `method`, `path`, `header`, `queryParam`, `pathParam`, `principal` e a atributos tipados:

```kotlin
val TIMEZONE = RequestKey<ZoneId>("timezone")

before { request -> request[TIMEZONE] = request.header("X-Timezone")?.let(ZoneId::of) }

fun clientZone(): ZoneId = RequestContext.currentOrNull()?.get(TIMEZONE) ?: ZoneOffset.UTC
```

Para levar o contexto a uma corrotina lançada fora do pipeline, use `request.asContextElement()`.

## Transações

`transactions { service = …; coordinator = … }`. Numa rota `transactional = true`, tudo o que o handler grava é confirmado junto, ou nada é. O `X-Tx-Id` é opcional:

1. Se veio `X-Tx-Id` e há coordenador, a escrita se junta à transação remota do cliente e só é confirmada no commit dele.
2. Se não veio o cabeçalho mas o dono tem transação remota aberta, a escrita é recusada com 409, para não ser confirmada fora da transação. `refuseWritesOutsideOpenTransaction = false` desliga isso.
3. Nos outros casos, há uma transação por requisição (`TransactionService.required`).

O dono de uma transação remota é `user:<principal.id>`, ou `anon:<X-Client-Id>` sem principal (configurável em `ownerKey`). `RemoteTransactionRoutes.register(this, transactions)` publica `begin`, `commit`, `rollback` e `status` em `/api/tx`. `serviceSource` e `coordinatorSource` são consultados a cada requisição, para quem guarda os serviços em holders.

## CORS, compressão e limite de corpo

- `cors { anyOrigin() | allowOrigins(…); allowCredentials; reflectClientOrigin; maxAge; exposedHeaders }` usa o plugin do Javalin, que responde a pré-requisição antes das rotas.
- `compression { gzipLevel; minSize; mimeTypes }` usa gzip. O padrão de `minSize` é 2 KB, como no Spring Boot.
- `maxRequestSize = bytes`: um corpo maior recebe 413.

## Server-Timing

`serverTiming()` liga o cabeçalho `Server-Timing`, que o DevTools mostra na aba Timing; desligado se não chamado. Todo pedido que passa pela biblioteca leva `total` (autenticação, regra, handler, commit e serialização), também nas respostas de erro e nos 404/405 tratados por ela. A rota acrescenta medidas próprias, que saem antes do `total`, na ordem do registro:

```kotlin
serverTiming { names = false }   // names = true acrescenta desc="…"; em produção, não exponha nomes internos
post("/order") { call -> call.timed("action", "CreateOrderAction") { createOrder(call.body()) } }
// Server-Timing: action;dur=12.3, total;dur=15.8
```

`call.timing(name, nanos, desc)` registra uma duração já medida; é uma função simples, que a rota pode passar adiante (`call::timing`). Com `cors { … }`, `Server-Timing` entra sozinho nos `exposedHeaders`.

## Cabeçalhos de cache e de segurança

`securityHeaders()` põe cabeçalhos em toda resposta que passa pela biblioteca: sucesso, erro mapeado, recusa da autenticação ou da regra, 404/405 tratados por ela (`handleUnmatched` ou `protectUnmatched`), o documento OpenAPI e a pré-requisição do CORS. Fica desligado se não for chamado.

- **Cache** (`noCache = true`): `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache` e `Expires: 0`, só quando a rota não pôs o próprio `Cache-Control`. A rota que quer cache escreve o seu, e a resposta sai sem `Pragma` nem `Expires`.
- **Segurança** (`headers`, sempre, vencendo o valor que a rota tiver posto): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` e `X-XSS-Protection: 1; mode=block`. O mapa aceita trocar, acrescentar e retirar.

```kotlin
securityHeaders {
    headers -= "X-XSS-Protection"                              // obsoleto nos navegadores atuais
    headers["Strict-Transport-Security"] = "max-age=31536000"  // não vem por padrão: atrás de proxy TLS o pedido chega em HTTP
}
```

## OpenAPI

`openApi { path; title; version; description; tag(…); schemas; parameters; securityScheme; raw }`. O documento sai das rotas registradas: verbo, caminho, parâmetros de caminho e a segurança de cada operação (uma rota pública leva `security: []`). Os esquemas dos DTOs são gerados por reflexão, com os nomes que o Gson usa. Cada rota acrescenta o que tiver em `doc = { summary = …; query("page", Int::class.java); accepts<Req>(); returns<Resp>(200); response(404); raw(mapOf(…)) }`, e `raw` vence o que foi gerado. O documento fica servido em `path`, e também em `api.openApiJson`.

`openApi { ui { } }` serve a página do Swagger UI a partir do webjar oficial no classpath, sem CDN. O webjar não vem com a biblioteca: quem liga a página o declara (`runtimeOnly("org.webjars:swagger-ui:5.34.1")`, ou outra versão 5.x). A versão é lida do próprio webjar, e sem ele a montagem da `RestApi` falha.

```kotlin
openApi {
    title = "Orders"
    ui {
        path = "/swagger-ui"                               // o padrão
        document("/partner/openapi.json", "Partner API")   // outro documento: o de outra RestApi, um arquivo estático
        primary = "Orders"                                 // o aberto quando há vários; padrão, o primeiro
    }
}
```

`GET /swagger-ui.html` redireciona (302) para `/swagger-ui/index.html`, que é a página: com um documento, só ele; com vários, o seletor do topo. `includeOwn = false` tira o documento da própria API da lista. Dos arquivos do webjar, só os que a página carrega são servidos (`swagger-ui.css`, `swagger-ui-bundle.js`, `swagger-ui-standalone-preset.js`, `favicon-32x32.png`), com `Cache-Control: public, max-age=86400`; o resto é 404. As rotas são públicas e ficam fora do documento.

## RPC genérico por entidade

`RepositoryRpc(basePath, codec, repository, …)` publica as operações de um repositório: `insert`, `update`, `delete`, `count`, `fetch`, `fetch-page`, `fetch-by-id` e `GET {id}`. É o protocolo que o cliente `HttpRepository` do Shopping fala. O acesso fica com a aplicação, por meio de `rule` (por operação), `scope` (restringe o critério a quem chama), `sanitizeProjection`, `sanitizeOutput`, `beforeInsert` e `beforeUpdate`. Os dois ganchos de escrita rodam dentro da transação. Para um repositório só de leitura, use `writes = false`.

**O `scope` soma o alcance a um critério que veio de quem chama**, com a disjunção que ele escolheu em cada campo. Use o campo conjuntivo, `criteria.ownerId.and().eq(owner)`, e nunca só `eq`: num campo enviado com `"or": true`, `eq` vira mais uma alternativa (`ownerId IS NOT NULL OR ownerId = :owner`) e quem chama alcança tudo.

O `insert` recebe `{"entity", "projection"?}` (ou, como antes da 0.4.0, a entidade solta) e responde `{"success", "id", "entity"}`, com a entidade como o repositório a deixou — a chave gerada e os padrões das colunas —, passada por `sanitizeOutput`.

## Testes

`RestTestServer(api)` sobe a API numa porta livre, com o mesmo pipeline da produção:

```kotlin
RestTestServer(api).use { server ->
    val response = server.client().bearer(token).header("X-Timezone", "Asia/Tokyo").get("/order/1")
    assertEquals(200, response.status)
    assertEquals("open", response.json().asJsonObject["status"].asString)
}
```

O `TestClient` envia qualquer verbo, com query, cabeçalhos fixos ou por chamada e corpo em texto ou bytes. O `TestResponse` traz `status`, `headers`, `body`, `bytes` e `json()`. Nada é descomprimido: o teste vê o `Content-Encoding` como veio.

## O que não está coberto

- **415 por Content-Type errado** (o `consumes` do Spring): confira no handler, se o contrato exigir.
- **Validação de bean** (`@Valid`): valide no handler e lance uma exceção da aplicação, com o mapeamento que reproduz o corpo esperado.
- **`String` devolvida sai como `text/plain`**, como no Spring. Para uma string JSON, devolva `RawJson("\"…\"")`.
