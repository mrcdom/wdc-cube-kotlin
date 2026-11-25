# 0007. Read-only and read-write repositories; the presentation uses resource calls

- **Date:** 2026-10-06 (two levels); 2026-11-24 (resource calls)
- **Status:** Accepted

## Context

Every entity has a repository with the same shape: count, fetch, fetch a page, fetch by key, and the writes. Dashboards and similar screens only read, yet a single contract forced their repositories to implement writes that throw. On the other side, presenters called repositories and services directly, with transaction handling spread over them, and services in the presentation layer existed only to group a few repository calls.

## Decision

Two contract levels, in `framework-domain`: `ReadOnlyRepository<E, C, K>` (`count`, `fetch`, `fetchPage`, `fetchById`, `ownFieldsProjection`) and `Repository<E, C, K>`, which extends it with `insert`, `update`, `insertOrUpdate` and `delete`. The client side mirrors them (`HttpReadOnlyRepository`, `HttpRepository`). A repository that only reads extends the read-only one; nobody writes `insert` that throws.

The presentation reaches data through **resource calls** (`framework-domain`, package `resource`): `ResourceQuery` for reads, and `ResourceCreate`, `ResourceSave`, `ResourceUpdate`, `ResourceDelete` for writes. A write call runs in a `required` transaction of the `TransactionService` read at that moment, or directly when there is none. Each call lives in a `calls/` package next to its presenter (`ProductQuery`, `PurchaseCreate`, `CredentialsCheck`). A call reads its repositories through the repository's `BEAN`, in a private property read at the moment of use:

```kotlin
private val productRepo: ProductRepository get() = ProductRepository.BEAN.get()
```

## Consequences

- The same presenter runs against the in-process repositories (remote view, tests) or the HTTP ones (Compose, native), depending only on what was registered in the `BEAN`s.
- A write is atomic by construction: its transaction is opened by the call, not by the presenter.
- Calls with two or more arguments pass them by name, one per line, since arguments of the same type swapped by mistake would compile (page and page size, purchase and owner).
- Tests exercise calls in process; the REST path is covered by the repository tests and by the checkouts over `RestTransactionService`.

## Alternatives considered

- **One repository contract.** Simpler, but read-only repositories would implement writes that throw.
- **Services in the presentation.** Removed: they only grouped calls, and their transaction handling was easy to get wrong.
