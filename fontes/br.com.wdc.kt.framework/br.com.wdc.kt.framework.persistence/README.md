# framework-persistence

Transactions over JDBC (JVM).

- `TransactionServiceImpl` — the six propagations of `TransactionService`, on a connection from the `DataSource`. Works with coroutines: the transaction follows the block across suspensions.
- `TransactionScope` — the thread's current transaction; data access finds the connection through it.
- `RemoteTransactionCoordinatorImpl` — keeps transactions opened by remote clients, with owner, caps, idle expiration and idempotent outcome.

Published on Maven Central as `io.github.mrcdom.wdc.kt:framework-persistence`.

See the [persistence architecture](../../../docs/architecture-persistence.md).
