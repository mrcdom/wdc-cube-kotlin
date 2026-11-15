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
| 0.4.0 | `insert` por projeção, com o bean completo de volta; `afterCommit` nas transações; `Server-Timing` no `framework-rest`; `isIn(vazia)` passa a filtrar por nenhum. Detalhes e trocas a fazer em [Notas da 0.4.0](#notas-da-040). |
| 0.3.0 | **`framework-rest`**, APIs REST sobre Javalin 7: rotas de recurso e RPC por entidade, segurança plugável, mapa de exceções, contexto e transação por requisição, CORS, gzip, OpenAPI gerado das rotas (também de `@ApiDoc`), servidor de teste; os corpos Kotlin são lidos pelo construtor primário (padrões valem, sem gravar `val` por reflexão, pronto para o JDK 26). **`framework-domain`**: `@ApiDoc`; `containing` e `startingWith` procuram o texto literalmente (`%` e `_` deixam de ser curingas), e o tradutor declara `ESCAPE '\'`. **`framework-jooq`**: um critério sobre várias colunas (`translateAny`); colunas `UUID` e `DATE` (como `LocalDate`) na projeção JSON; leitura de `TIMESTAMP` sem fuso sem lançar exceção por valor. |
| 0.2.0 | `framework-domain`, `framework-persistence` e `framework-jooq`; compilação com Kotlin 2.3 |
| 0.1.0 | `framework-commons` e `framework-cube` |

## Notas da 0.4.0

Uma linha por mudança. **Quebra** quer dizer que o código de quem usa precisa de ajuste; a coluna ao lado diz qual.

| Módulo | Mudança | Quebra? | O que fazer |
|---|---|---|---|
| `framework-rest` | Parâmetro de construtor cujo campo é `@Transient` (ou `transient`) não é mais lido do JSON: a chave é ignorada, e ele recebe o padrão ou `null`. Sem padrão e sem ser anulável, a primeira leitura da classe falha com `JsonDeclarationException` (500, não 400). | Não | Dê padrão ou torne anulável o parâmetro transiente que ainda não tiver. |
| `framework-domain` | `ProjectionList(bean, criteria, limit, offset)` e `ProjectionValues.singletonList(bean, criteria, limit, offset)`; `limit` e `offset` passam a `val`. | Não | `withLimit`/`withOffset` ficam obsoletos (devolvem uma cópia) e saem na próxima versão: passe o recorte ao construtor. |
| `framework-domain` + `framework-jooq` | **Mudança de comportamento:** `isIn(vazia)` (e `isIn()` sem argumentos) filtra por nenhum: a busca não casa linha, o `count` dá 0 e o `delete` não apaga nada. `isIn(null)` continua sem filtrar. Antes, vazio não filtrava, e devolvia ou apagava tudo. | Comportamento | Quem contava com "vazio = sem filtro" passa `null`. |
| `framework-jooq` | `addLocalDate` → `addDay`, `addUuid` → `addUid`, `addInstantFromOdt` → `addIzt`, `addInstantFromLdt` → `addIlt` (também `addDay`/`addUid` no `RelationKey`). | Não | Os nomes antigos ficam obsoletos nesta versão e saem na próxima: troque pelos novos. |
| `framework-jooq` | No `RelationKey`, `addLocalDate` e `addUuid` saem sem período de transição: passam a `addDay` e `addUid`. | **Sim** | Troque os nomes nas declarações de chave (`key = { … }`). |
| `framework-domain` | `newProjection()` passa a `ownFieldsProjection()`: os campos próprios da entidade, associação só pela chave, sem coleção nem campo derivado. | **Sim** | Troca mecânica de nome em quem implementa e em quem chama. |
| `framework-domain` | `ref(::Entidade, chave) { id = it }` monta uma entidade só com a chave; `null` entra, `null` sai. | Não | Troque os `ref(id)` próprios de cada entidade, se quiser. |
| `framework-domain` + `framework-persistence` | `TransactionService.afterCommit(task)`: roda a tarefa depois do commit da transação física corrente; descartada se ela for desfeita (exceção ou `setRollbackOnly`); sem transação, roda na hora; falha da tarefa vai ao log. `AfterCommitTasks` é a fila e o executor comuns. | **Sim** | Quem implementa `TransactionService` acrescenta `afterCommit`; `AfterCommitTasks` faz o trabalho. |
| `framework-rest` | `serverTiming { names = false }` liga o cabeçalho `Server-Timing` (`total`, mais as medidas de `call.timing`/`call.timed`), também nas respostas de erro; com CORS, o cabeçalho é exposto. Desligado por padrão. | Não | Troque filtros `before`/`after` próprios pela opção. |
| `framework-rest` | `securityHeaders { noCache; headers }` põe os cabeçalhos de cache (só quando a rota não pôs `Cache-Control`) e de segurança (`nosniff`, `X-Frame-Options`, `X-XSS-Protection`, num mapa editável) em toda resposta da API, inclusive erros, recusas, 404/405 tratados pela biblioteca e a pré-requisição do CORS. Desligado por padrão. | Não | Troque os filtros `after` (da API e do Javalin) que faziam isso pela opção. |
| `framework-rest` | `openApi { ui { path; title; document(url, nome); primary; includeOwn } }` serve a página do Swagger UI do webjar oficial (sem CDN), com o seletor quando há vários documentos; só os arquivos que a página carrega, com cache de um dia. O webjar `org.webjars:swagger-ui` fica por conta de quem liga a página. | Não | Troque a página própria pela opção e declare o webjar. |
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
