# framework-domain

Data access contracts, independent of database and transport (KMP).

- `repository` / `pagination` — `ReadOnlyRepository<E, C, K>` (queries), `Repository<E, C, K>` (queries and writes) and `Page`.
- `criteria` — `Criterion`, `ComparableCriterion`, `TextCriterion`, `Operator` and the `CriterionCodec`, which carries them to and from JSON.
- `projection` — `ProjectionValues`, `ProjectionList` and the projected collection envelope.
- `codec` — `ModelCodec`, the contract for reading and writing an entity and its criteria.
- `transaction` — `TransactionService` and `TransactionContext`.
- `exception` — `BusinessException`, `InvalidRequestException`, `AccessDeniedException` and the transaction ones.

**Platforms:** JVM, Android, iOS, JS, wasmJs. Published on Maven Central as `io.github.mrcdom.wdc.kt:framework-domain`.

See the [persistence architecture](../../../docs/architecture-persistence.md).
