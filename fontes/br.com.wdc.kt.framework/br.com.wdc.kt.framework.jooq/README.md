# framework-jooq

Declarative queries over jOOQ (JVM).

- `JsonQueryBuilder` / `JsonQuery` — declares once how an entity maps to the table; given a projection, builds a single query that returns the requested graph as JSON and reads it back.
- `JsonQuery.insert(bean, projection)` — the `INSERT` by the same fields (`JsonQuery.fields()`), with the inserted row read back through the fetch projection: the bean comes out with the identity, the column defaults and whatever else the database filled in. The derived field (an expression, not a column) and the identity column are left out; a given primary key always goes in. `onConflictDoNothing = true` returns `false` on conflict (on H2, jOOQ emulates it by the primary key).
- `JsonQuery.fields()` for the repository's own writes (an `UPDATE` by the marked columns, for example): `projected` says whether the projection asks for the field, `mark` asks for it, `get` reads from the bean and `toColumn` converts the value to the column type (`Instant` in UTC, `Double` to `BigDecimal`, enum by its `toColumn`, the name by default), without repeating the mapping.
- `CriterionTranslator` — translates a criterion into jOOQ conditions (`translateAny` over several columns, `anyOf` for a criteria's alternative groups); a predicate whose operator and values disagree is refused, not dropped.
- `JsonDialect` — what changes from one database to another. There are dialects for H2 and PostgreSQL.
- `TransactionAwareConnectionProvider` — makes jOOQ use the current transaction's connection.

The scalar field adders have three letters after `add`, so declarations line up:

| Adder | Domain | Column |
|---|---|---|
| `addI64`, `addI32`, `addF64`, `addDec` | `Long`, `Int`, `Double`, `BigDecimal` | numeric |
| `addStr`, `addBit`, `addBin` | `String`, `Boolean`, `ByteArray` | text, boolean, binary |
| `addOdt`, `addLdt` | `OffsetDateTime` | `TIMESTAMP WITH TIME ZONE`, `TIMESTAMP` (in UTC) |
| `addIzt`, `addIlt` | `Instant` | `TIMESTAMP WITH TIME ZONE`, `TIMESTAMP` (in UTC) |
| `addDay` | `LocalDate` | `DATE` |
| `addUid` | `UUID` | `UUID` |

In 0.4.0, `addLocalDate`, `addUuid`, `addInstantFromOdt` and `addInstantFromLdt` became `addDay`, `addUid`, `addIzt` and `addIlt` (the old names are still there, deprecated, and go away in a later version). In `RelationKey`, `addLocalDate` and `addUuid` are gone for good: use `addDay` and `addUid`.

The tests run the same cases on H2 and on embedded PostgreSQL. Published on Maven Central as `io.github.mrcdom.wdc.kt:framework-jooq`.

See the [persistence architecture](../../../docs/architecture-persistence.md).
