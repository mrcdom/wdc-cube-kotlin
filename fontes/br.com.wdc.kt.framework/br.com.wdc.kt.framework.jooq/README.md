# framework-jooq

Consultas declarativas sobre jOOQ (JVM).

- `JsonQueryBuilder` / `JsonQuery` — declara uma vez como uma entidade se relaciona com a tabela; dada uma projeção, monta uma única consulta que devolve o grafo pedido como JSON e o lê de volta.
- `CriterionTranslator` — traduz um critério em condições jOOQ.
- `JsonDialect` — o que muda de um banco para outro. Há dialetos para H2 e PostgreSQL.
- `TransactionAwareConnectionProvider` — faz o jOOQ usar a conexão da transação corrente.

Os testes rodam os mesmos casos em H2 e em PostgreSQL embutido. Publicado no Maven Central como `io.github.mrcdom.wdc.kt:framework-jooq`.

Veja a [arquitetura de persistência](../../../docs/architecture-persistence.md).
