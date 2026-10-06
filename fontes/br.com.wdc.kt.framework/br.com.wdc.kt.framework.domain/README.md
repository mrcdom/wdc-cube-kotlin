# framework-domain

Contratos de acesso a dados, independentes de banco e de transporte (KMP).

- `repository` / `pagination` — `ReadOnlyRepository<E, C, K>` (consultas), `Repository<E, C, K>` (consultas e escrita) e `Page`.
- `criteria` — `Criterion`, `ComparableCriterion`, `TextCriterion`, `Operator` e o `CriterionCodec`, que os leva e traz em JSON.
- `projection` — `ProjectionValues`, `ProjectionList` e o envelope de coleção projetada.
- `codec` — `ModelCodec`, o contrato de leitura e escrita de uma entidade e do seu critério.
- `transaction` — `TransactionService` e `TransactionContext`.
- `exception` — `BusinessException`, `InvalidRequestException`, `AccessDeniedException` e as de transação.

**Plataformas:** JVM, Android, iOS, JS, wasmJs. Publicado no Maven Central como `io.github.mrcdom.wdc.kt:framework-domain`.

Veja a [arquitetura de persistência](../../../docs/architecture-persistence.md).
