# 0009. HTTP suspends on every client; the remote transaction goes with the coroutine

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

The repository and transaction contracts were already `suspend`, but the client transports underneath blocked a thread for each call: synchronous `XMLHttpRequest` in the browser (deprecated, and it freezes the page), a spun run loop on iOS, OkHttp's `execute` on Android with `StrictMode.permitNetwork` on the main thread. The apps started with `runBlocking` on the main thread. For a reference architecture, that taught the wrong thing.

Blocking also guaranteed things nobody had written down: two actions never ran at the same time, so the client's current remote transaction could be kept per service instance ([0008](0008-remote-transactions-over-rest.md)), and two calls never met the same expired token at once.

## Decision

`HttpTransport` is `suspend` on every platform, and no thread waits for the network. `BaseHttpTransport` does what is common (headers, renewal of the session on 401, reading each kind of answer); each platform only sends one request: `fetch` in JS and Wasm, an `NSURLSession` data task on iOS, OkHttp's `enqueue` on the JVM and Android. On iOS and on the JVM and Android, cancelling a call cancels the request; in JS and Wasm the `fetch` is not aborted (no `AbortController` yet): the request runs to its end and its answer is dropped. The apps start their first navigation in the presenters' scope instead of `runBlocking`.

What blocking guaranteed by itself is now explicit:

- **The client's remote transaction goes with the coroutine** that opened it, in its context (`RemoteTransactionElement`, told apart by the client's `clientId`). An action running while a transaction waits on the network does not carry its `X-Tx-Id`.
- **Actions run one at a time per application** ([0004](0004-actions-are-guarded-scope-fields.md)).
- **Calls refused together renew the session once.** The server replaces the refresh token on each renewal, so a second renewal with the old one would be refused and lose the session. `RestConfig` renews under a lock; a call that finds the token already replaced just repeats itself.

## Consequences

- The UI thread is never blocked by I/O on any platform; Android no longer needs `permitNetwork`.
- Code that starts a context of its own inside a transaction — a nested `runBlocking`, a coroutine of another scope — leaves the transaction. In tests, `assertFailsWith` (inline) keeps the context where JUnit's `assertThrows` with `runBlocking` inside would not.
- `inRemoteTransaction(txId) { }` runs a block in a transaction opened by hand.
- `AuthenticationService.refresh`, `logout` and `signOut` are `suspend`.

## Alternatives considered

- **Keep the transaction per instance and rely on serialized actions.** Simpler, but its correctness would hang on a property of another component. Turned down by the maintainer in favor of the coroutine context.
