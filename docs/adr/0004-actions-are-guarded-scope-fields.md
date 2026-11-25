# 0004. Actions are guarded fields of the scope, run one at a time

- **Date:** 2026-11-24 (actions as fields); 2026-11-25 (one at a time)
- **Status:** Accepted

## Context

Views call presenter logic when the user does something. Each view used to call presenter methods directly, each with its own error handling, and the remote view dispatched numeric event codes through a hand-written `onEvent` per presenter. Errors nobody handled reached the user as stack traces or disappeared, and the address was not always republished after an action changed what a presenter publishes.

With blocking HTTP, two user actions could never interleave: the second waited for the first to give the thread back. Once HTTP became suspending ([0009](0009-suspending-http-on-every-client.md)), two clicks on "Buy" would run while the first waits on the network.

## Decision

An action is a field of the scope (`Action`, `ActionOf<T>`, `ActionOf2<T, U>`, each with `suspend operator fun invoke`), bound once: in the presenter's constructor, or when the presenter creates a segment (a cart row, bound in the method that creates it):

```kotlin
scope.onBuy = action(1) { buy() }
row.onModifyQuantity = actionOf(2, FormParam.int("p.quantity"), target = row) { modifyQuantity(productId, it) }
```

Every bound action runs through one **guard**: what the body did not handle goes to `onActionFailure` (by default `alertUnexpectedError`, "Unexpected error: <cause>"), `CancellationException` passes through, and at the end the application republishes the address. The optional event code and `FormParam`s let a remote view fire the same action by code.

The actions of one application run **one at a time** (`CubeApplication.runAction`, a `Mutex` per application): an action that starts while another waits on I/O waits for it, without blocking a thread. The lock goes with the coroutine context, so an action that runs another runs it at once instead of waiting for itself.

## Consequences

- Views only call `scope.onX(...)`; they never see a presenter.
- No action sees another one's state halfway, and a double click makes two purchases one after the other, never interleaved — the same guarantee blocking I/O gave, without its cost.
- An action that waits on a coroutine of another scope which itself runs an action would wait for itself; such code calls the action directly instead.
- Navigation keeps its own rule for requests that arrive during another navigation (see [architecture-cube.md](../architecture-cube.md)).

## Alternatives considered

- **Let actions interleave** and have each presenter protect itself. More responsive in theory, but every presenter would need its own guards against double submission and torn state. Turned down by the maintainer in favor of serialization per application.
