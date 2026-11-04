# framework-jooq

Consultas declarativas sobre jOOQ (JVM).

- `JsonQueryBuilder` / `JsonQuery` — declara uma vez como uma entidade se relaciona com a tabela; dada uma projeção, monta uma única consulta que devolve o grafo pedido como JSON e o lê de volta.
- `JsonQuery.insert(bean, projection)` — o `INSERT` pelos mesmos campos (`JsonQuery.fields()`), e a linha inserida lida de volta pela projeção do fetch: o bean sai com a identidade, os padrões das colunas e o que mais o banco preencheu. Ficam fora o campo derivado (expressão, e não coluna) e a coluna de identidade; a chave primária informada entra sempre. `onConflictDoNothing = true` devolve `false` no conflito (no H2, o jOOQ o emula pela chave primária).
- `CriterionTranslator` — traduz um critério em condições jOOQ.
- `JsonDialect` — o que muda de um banco para outro. Há dialetos para H2 e PostgreSQL.
- `TransactionAwareConnectionProvider` — faz o jOOQ usar a conexão da transação corrente.

Os adicionadores de campo escalar têm três letras depois do `add`, para as declarações ficarem alinhadas:

| Adicionador | Domínio | Coluna |
|---|---|---|
| `addI64`, `addI32`, `addF64`, `addDec` | `Long`, `Int`, `Double`, `BigDecimal` | numérica |
| `addStr`, `addBit`, `addBin` | `String`, `Boolean`, `ByteArray` | texto, booleana, binária |
| `addOdt`, `addLdt` | `OffsetDateTime` | `TIMESTAMP WITH TIME ZONE`, `TIMESTAMP` (em UTC) |
| `addIzt`, `addIlt` | `Instant` | `TIMESTAMP WITH TIME ZONE`, `TIMESTAMP` (em UTC) |
| `addDay` | `LocalDate` | `DATE` |
| `addUid` | `UUID` | `UUID` |

Na 0.4.0, `addLocalDate`, `addUuid`, `addInstantFromOdt` e `addInstantFromLdt` passaram a `addDay`, `addUid`, `addIzt` e `addIlt` (os nomes antigos ficam obsoletos por esta versão). No `RelationKey`, `addLocalDate` e `addUuid` saíram de vez: use `addDay` e `addUid`.

Os testes rodam os mesmos casos em H2 e em PostgreSQL embutido. Publicado no Maven Central como `io.github.mrcdom.wdc.kt:framework-jooq`.

Veja a [arquitetura de persistência](../../../docs/architecture-persistence.md).
