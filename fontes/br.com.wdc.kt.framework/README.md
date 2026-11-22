# Framework

Bibliotecas base reutilizáveis, independentes da aplicação Shopping.

## Módulos

- [br.com.wdc.kt.framework.commons/](br.com.wdc.kt.framework.commons/) — Utilitários e tipos base (KMP)
- [br.com.wdc.kt.framework.cube/](br.com.wdc.kt.framework.cube/) — Engine Cube MVP (KMP)
- [br.com.wdc.kt.framework.domain/](br.com.wdc.kt.framework.domain/) — Contratos de acesso a dados: repositório, critérios, projeção, transação (KMP)
- [br.com.wdc.kt.framework.persistence/](br.com.wdc.kt.framework.persistence/) — Transações sobre JDBC e coordenador de transações remotas (JVM)
- [br.com.wdc.kt.framework.jooq/](br.com.wdc.kt.framework.jooq/) — Consultas declarativas sobre jOOQ, para H2 e PostgreSQL (JVM)
- [br.com.wdc.kt.framework.rest/](br.com.wdc.kt.framework.rest/) — APIs REST sobre Javalin: rotas, segurança, erros, contexto e transação por requisição, OpenAPI (JVM)

## Uso como dependência

Os seis módulos são publicados no Maven Central, sob a licença [MIT](LICENSE), no grupo `io.github.mrcdom.wdc.kt`. Todos saem com a mesma versão.

| Artefato | Plataformas | Traz junto |
|---|---|---|
| `framework-commons` | JVM, Android, JS, WasmJS, iOS | — |
| `framework-cube` | JVM, Android, JS, WasmJS, iOS | `framework-commons` |
| `framework-domain` | JVM, Android, JS, WasmJS, iOS | `framework-commons` |
| `framework-persistence` | JVM | `framework-domain` |
| `framework-jooq` | JVM | `framework-persistence`, jOOQ |
| `framework-rest` | JVM | `framework-persistence`, Javalin 7, Gson |

Alvos: JVM (Java 21), Android (minSdk 26), JS, WasmJS, iosArm64 e iosSimulatorArm64. Compilados com Kotlin 2.3: quem consome nos alvos JS, WasmJS e iOS precisa de Kotlin 2.3 ou mais novo.

```kotlin
// build.gradle.kts de um projeto Kotlin Multiplatform
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mrcdom.wdc.kt:framework-cube:0.4.0")   // navegação e presenters
            implementation("io.github.mrcdom.wdc.kt:framework-domain:0.4.0") // contratos de acesso a dados
        }
    }
}

// build.gradle.kts de um módulo JVM de servidor
dependencies {
    implementation("io.github.mrcdom.wdc.kt:framework-jooq:0.4.0") // já traz persistence e domain
    implementation("io.github.mrcdom.wdc.kt:framework-rest:0.4.0") // API REST sobre Javalin
}
```

| Versão | O que trouxe |
|---|---|
| 0.4.1 (a publicar) | Correção de segurança: critério do transporte com operador e valores em desacordo passa a ser recusado (400), em vez de sumir do SQL — um `delete` assim apagava a tabela. Navegação pedida de fora espera a corrente, em vez de interrompê-la. Transação remota: requisição que falha a condena, desconhecida responde 410, rollback durante uma requisição espera ela terminar, dono = usuário + cliente. Detalhes em [Notas da 0.4.1](#notas-da-041). |
| 0.4.0 | `insert` por projeção, com o bean completo de volta; `afterCommit` nas transações; no `framework-rest`, `Server-Timing`, cabeçalhos de cache e de segurança, página do Swagger UI, limite por minuto e novas leituras na `RestCall`; `isIn(vazia)` passa a filtrar por nenhum. Detalhes e trocas a fazer em [Notas da 0.4.0](#notas-da-040). |
| 0.3.0 | **`framework-rest`**, APIs REST sobre Javalin 7: rotas de recurso e RPC por entidade, segurança plugável, mapa de exceções, contexto e transação por requisição, CORS, gzip, OpenAPI gerado das rotas (também de `@ApiDoc`), servidor de teste; os corpos Kotlin são lidos pelo construtor primário (padrões valem, sem gravar `val` por reflexão, pronto para o JDK 26). **`framework-domain`**: `@ApiDoc`; `containing` e `startingWith` procuram o texto literalmente (`%` e `_` deixam de ser curingas), e o tradutor declara `ESCAPE '\'`. **`framework-jooq`**: um critério sobre várias colunas (`translateAny`); colunas `UUID` e `DATE` (como `LocalDate`) na projeção JSON; leitura de `TIMESTAMP` sem fuso sem lançar exceção por valor. |
| 0.2.0 | `framework-domain`, `framework-persistence` e `framework-jooq`; compilação com Kotlin 2.3 |
| 0.1.0 | `framework-commons` e `framework-cube` |

## Notas da 0.4.1

Ainda não publicada. Corrige defeitos achados na revisão de um port da arquitetura para outra linguagem.

| Módulo | Mudança | Quebra? | O que fazer |
|---|---|---|---|
| `framework-domain` | **Segurança.** `Criterion.restore` (a leitura do critério vindo do transporte) recusa com `InvalidRequestException` (400) a quantidade de valores que o operador não aceita (`BETWEEN` com um, `EQ` sem nenhum, `GT` com dois) e `null` em comparação de ordem, `LIKE`, `ILIKE` ou `BETWEEN`. Antes, o pedido era guardado, o campo contava como informado e o tradutor o tirava do SQL: um `delete` "com critério" virava `DELETE` sem `WHERE`, e `count`, `fetch` e o alcance por usuário perdiam o filtro. `EQ`/`NE` com `null` e `IN` vazio continuam aceitos. | Comportamento | Nenhuma, salvo cliente que enviava pedido malformado: ele passa a receber 400. |
| `framework-jooq` | O `CriterionTranslator` recusa (`IllegalArgumentException`) o pedido com operador e valores em desacordo, em vez de descartá-lo. | Comportamento | Critério montado em código só chega aí por `restore`; nada a fazer. |
| `framework-cube` | **Navegação pedida durante outra.** Quem decide é o `execute()`, não mais o `navigate()`: pedida de dentro da corrente (o presenter, no `applyParameters`) é redirect e a interrompe, como antes; pedida de fora (ação da view, histórico) **espera** a corrente terminar. Só o último pedido espera; um anterior, ou o que esperava uma navegação que falhou, devolve `false`. O módulo passa a depender de `kotlinx-coroutines-core`. | Comportamento | Nada, salvo presenter que lance outra corrotina para navegar e espere por ela dentro do `applyParameters` (esperaria a si mesmo): navegue direto. |
| `framework-cube` | Com `false` no `applyParameters`, o lugar corrente é o do presenter que parou a composição, e não o último passo da rota (nunca visitado). | Comportamento | Nada. |
| `framework-domain` + `framework-persistence` + `framework-rest` | Transação remota desconhecida ou expirada: `RemoteTransactionNotFoundException` (era `TransactionSystemException`), 410 no REST. Em uso por outra requisição: `TransactionConflictException` (409; era `TransactionSystemException`, 500). | **Sim** (tipo) | Quem capturava `TransactionSystemException` para esses casos captura os novos tipos. |
| `framework-persistence` | Rollback pedido enquanto uma requisição usa a transação vale na hora (desfecho gravado, o `txId` não aceita mais requisições), mas a conexão só fecha quando ela desliga; commit nessa hora é `TransactionConflictException`. `suspend` sempre desliga a thread. Commit de transação marcada para rollback desfaz e lança `TransactionConflictException`, em vez de responder sucesso. `begin(ownerKey, quotaKey)` conta o teto por `quotaKey` (padrão na interface: cai em `begin(ownerKey)`). | Comportamento | Quem implementa `RemoteTransactionCoordinator` pode sobrescrever `begin(ownerKey, quotaKey)`. |
| `framework-rest` | Com `X-Tx-Id`, a requisição roda como participante (`service.required`): se falhar no meio, a transação remota fica condenada e o commit do cliente responde 409. `ownerKey` passa a `user:<id>/client:<X-Client-Id>`, e o novo `quotaKey` (`user:<id>`) mantém o teto por usuário. | Comportamento | Quem sobrescreveu `ownerKey` confere se quer também `quotaKey`. |
| `framework-rest` | A KDoc de `RepositoryRpc.scope` (e o README) avisa: some o alcance com `campo.and().eq(dono)`, nunca só `eq`. Num campo que o cliente marcou com `"or": true`, `eq` vira mais uma alternativa e o alcance some. | Não | Confira os seus `scope`: troque `eq(dono)` por `and().eq(dono)`. |

## Notas da 0.4.0

Uma linha por mudança. **Quebra** quer dizer que o código de quem usa precisa de ajuste; a coluna ao lado diz qual.

| Módulo | Mudança | Quebra? | O que fazer |
|---|---|---|---|
| `framework-rest` | Parâmetro de construtor cujo campo é `@Transient` (ou `transient`) não é mais lido do JSON: a chave é ignorada, e ele recebe o padrão ou `null`. Sem padrão e sem ser anulável, a primeira leitura da classe falha com `JsonDeclarationException` (500, não 400). | Não | Dê padrão ou torne anulável o parâmetro transiente que ainda não tiver. |
| `framework-domain` | `ProjectionList(bean, criteria, limit, offset)` e `ProjectionValues.singletonList(bean, criteria, limit, offset)`; `limit` e `offset` passam a `val`. | Não | `withLimit`/`withOffset` ficam obsoletos (devolvem uma cópia) e saem na próxima versão: passe o recorte ao construtor. |
| `framework-domain` + `framework-jooq` | **Mudança de comportamento:** `isIn(vazia)` (e `isIn()` sem argumentos) filtra por nenhum: a busca não casa linha, o `count` dá 0 e o `delete` não apaga nada. `isIn(null)` continua sem filtrar. Antes, vazio não filtrava, e devolvia ou apagava tudo. | Comportamento | Quem contava com "vazio = sem filtro" passa `null`. |
| `framework-jooq` | `addLocalDate` → `addDay`, `addUuid` → `addUid`, `addInstantFromOdt` → `addIzt`, `addInstantFromLdt` → `addIlt` (também `addDay`/`addUid` no `RelationKey`). | Não | Os nomes antigos ficam obsoletos nesta versão e saem na próxima: troque pelos novos. |
| `framework-jooq` | `addEnm(…, toColumn = { it.codigo })`: o texto que a constante grava na coluna, o inverso do `parser`; por padrão, o nome. É o que o `insert` grava e o que `JsonQueryField.toColumn` devolve. | Não | Enum guardado no banco por um código próprio passa o `toColumn`; sem ele, o `insert` gravaria o nome da constante. |
| `framework-jooq` | No `RelationKey`, `addLocalDate` e `addUuid` saem sem período de transição: passam a `addDay` e `addUid`. | **Sim** | Troque os nomes nas declarações de chave (`key = { … }`). |
| `framework-domain` | `newProjection()` passa a `ownFieldsProjection()`: os campos próprios da entidade, associação só pela chave, sem coleção nem campo derivado. | **Sim** | Troca mecânica de nome em quem implementa e em quem chama. |
| `framework-domain` | `ref(::Entidade, chave) { id = it }` monta uma entidade só com a chave; `null` entra, `null` sai. | Não | Troque os `ref(id)` próprios de cada entidade, se quiser. |
| `framework-domain` + `framework-persistence` | `TransactionService.afterCommit(task)`: roda a tarefa depois do commit da transação física corrente; descartada se ela for desfeita (exceção ou `setRollbackOnly`); sem transação, roda na hora; falha da tarefa vai ao log. `AfterCommitTasks` é a fila e o executor comuns. | **Sim** | Quem implementa `TransactionService` acrescenta `afterCommit`; `AfterCommitTasks` faz o trabalho. |
| `framework-rest` | `serverTiming { names = false }` liga o cabeçalho `Server-Timing` (`total`, mais as medidas de `call.timing`/`call.timed`), também nas respostas de erro; com CORS, o cabeçalho é exposto. Desligado por padrão. | Não | Troque filtros `before`/`after` próprios pela opção. |
| Todos | Compilados com Kotlin 2.4.21. Dependências publicadas: Javalin 7.2.4, Gson 2.15.0, jOOQ 3.21.9 (que exige Java 21), kotlinx-coroutines 1.11.0, kotlinx-datetime 0.8.0, `kotlin-reflect` 2.4.21. | Não | Quem fixa versões dessas bibliotecas confere a compatibilidade; o jOOQ 3.21 tira `DataType.convert` (use `DSL.val(valor, coluna).value`). |
| `framework-rest` | O `RestTestServer` escuta só em `127.0.0.1`, e `baseUrl` usa esse endereço (antes, todas as interfaces e `localhost`, o que no macOS podia cair na porta de outro processo). | Não | Nada, salvo teste que monte a URL com `localhost` à mão: use `server.baseUrl`. |
| `framework-rest` | `securityHeaders { noCache; headers }` põe os cabeçalhos de cache (só quando a rota não pôs `Cache-Control`) e de segurança (`nosniff`, `X-Frame-Options`, `X-XSS-Protection`, num mapa editável) em toda resposta da API, inclusive erros, recusas, 404/405 tratados pela biblioteca e a pré-requisição do CORS. Desligado por padrão. | Não | Troque os filtros `after` (da API e do Javalin) que faziam isso pela opção. |
| `framework-rest` | `openApi { ui { path; title; document(url, nome); primary; includeOwn } }` serve a página do Swagger UI do webjar oficial (sem CDN), com o seletor quando há vários documentos; só os arquivos que a página carrega, com cache de um dia. O webjar `org.webjars:swagger-ui` fica por conta de quem liga a página. | Não | Troque a página própria pela opção e declare o webjar. |
| `framework-rest` | `security { rateLimit { key; perMinute; clock } }` limita as requisições por minuto de cada quem chama (janela fixa, em memória, por instância), depois da autenticação e antes da regra; acima do limite, `RateLimitedException`, 429 com `Retry-After` pelo mapeamento padrão. | Não | Tire a contagem do `Authenticator` e passe à opção; remapeie `RateLimitedException` se o formato de erro for outro. |
| `framework-rest` | Na `RestCall`: `requiredQueryLong`, `requiredQueryInt` e irmãos (ausente e malformado com as mesmas falhas de `requiredQuery` e `queryLong`); `explicitNulls()`, os campos do corpo que vieram com `null` explícito; `basicCredentials()`, usuário e senha do `Authorization: Basic`. | Não | Troque as leituras próprias, se quiser. |
| `framework-domain` + `framework-jooq` + `framework-rest` | `Repository.insert(bean, projection = null)`: a projeção diz o que entra no `INSERT` (fora = padrão da coluna; sem projeção, os campos com valor), e o bean sai completo. `JsonQuery.fields()` expõe o mapeamento, com `mark` e `toColumn` para a escrita própria do repositório; `JsonQuery.insert(bean, projection, onConflictDoNothing)` faz o `INSERT` e lê a linha de volta pela projeção do fetch. O `insert` do `RepositoryRpc` aceita `{"entity", "projection"?}` (e a entidade solta) e responde `{"success", "id", "entity"}`. | **Sim** | Quem implementa `Repository` acrescenta o parâmetro `projection` ao `insert`; o repositório sobre jOOQ pode delegar a `QUERY.insert`. Cliente do RPC pode ler `entity` da resposta. |

## Publicação

A configuração fica em [`fontes/build.gradle.kts`](../build.gradle.kts) (bloco `publishedModules`), com o plugin `com.vanniktech.maven.publish`. A versão é uma só, definida ali.

```bash
cd fontes

# Conferir os artefatos localmente (~/.m2), sem assinatura
./gradlew publishToMavenLocal

# Enviar ao Maven Central (exige credenciais e chave GPG em ~/.gradle/gradle.properties)
./gradlew publishToMavenCentral
```

As duas tarefas existem só nos módulos publicados. O envio cria um *deployment* no [Central Portal](https://central.sonatype.com/publishing/deployments) que precisa ser liberado manualmente. Uma versão liberada não pode ser alterada nem removida.
