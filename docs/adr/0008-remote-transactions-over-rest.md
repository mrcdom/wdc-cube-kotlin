# 0008. Remote transactions over REST

- **Date:** 2026-10-05; refined 2026-11-22
- **Status:** Accepted

## Context

The presentation is the same on the server (remote view) and on the client (Compose, native). On the server, a checkout writes a purchase and its items in one local transaction. On the client, each write is a REST request; without something more, a checkout that fails midway leaves a purchase without items. The presentation should not have to know which side it runs on.

## Decision

A client can open a **transaction that lives on the server** and make several REST requests in it. `RestTransactionService` implements the same `TransactionService` as the server's local one: `required` sends `POST /api/tx/begin` and gets a `txId`; while the block runs, every authenticated request carries `X-Tx-Id` and joins the same physical transaction on the server; at the end, `POST /api/tx/commit`, or `rollback` on failure. Reads inside the block join it too, so they see what it already wrote.

The rules, on the server (`RemoteTransactionCoordinatorImpl`) and the client:

- **The owner** of a transaction is `user:<id>/client:<X-Client-Id>`; another owner gets 403. The number of open transactions is capped per user (429).
- **A participant that fails dooms the transaction**, even if its caller catches the exception and carries on. A request that failed on the server dooms it there, and its commit answers 409; a call that failed inside the block on the client makes the client roll back instead of committing. Either way the owner throws instead of returning the block's result. Only an explicit `setRollbackOnly` rolls back without throwing.
- **A write without `X-Tx-Id`** from someone who has a transaction open is refused (409): it would commit alone, outside the atomic unit, which usually means the header was lost.
- **Unknown or expired** transactions answer 410; abandoned ones are rolled back by a periodic reaper.
- **A lost commit answer** (timeout, dropped connection) is resolved by asking `GET /api/tx/status` before deciding.
- `afterCommit` tasks run only after the remote commit returned successfully, with no transaction current.

## Consequences

- Presentation code writes `transactions.required { … }` once and is atomic on both sides.
- The calls of one remote transaction must be sequential: the server refuses concurrent use of the same `txId` (409).
- `notSupported` inside a remote transaction is for reading; a write there is refused like a lost header. To write outside it, `requiresNew`.
- The server holds a database connection for each open remote transaction, which is why they are capped and reaped.

## Alternatives considered

- **A batch endpoint per use case** (`POST /checkout`). Atomic and simpler on the server, but the presentation would differ between server and client, and every new use case would need an endpoint.

See [architecture-persistence.md](../architecture-persistence.md), remote transactions.
