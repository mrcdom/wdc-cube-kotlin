# 0005. Computed state is committed once per paint cycle

- **Date:** 2026-10-05 (the concept); 2026-11-24 (run by the application)
- **Status:** Accepted

## Context

Some of what a view shows is derived from other state: a cart's total, a count in a header, whether a button is enabled. Computing it in every setter repeats work when several fields change in one action and spreads the derivation over the presenter; computing it in the view puts logic where it does not belong, and every rendering technology would repeat it.

## Decision

A presenter computes derived state in `commitComputedState()`. It is presentation logic (a method of `PresenterBase`), not view logic, and it **runs once per paint cycle**, right before the scopes are read to be painted. Because of that, it can be deferred to the last moment: however many fields an action changed, the derivation runs once.

The application runs it for every live presenter (`CubeApplication.commitComputedState()`); each technology's paint scheduler calls it once per cycle before choosing the dirty views, and the remote host before writing the scopes it sends. What it changes is painted in the same cycle.

## Consequences

- Nothing calls `commitComputedState()` outside a paint cycle, and every path that reads scopes to paint goes through it first.
- A value that needs the network is not derived state: an action loads it, since `commitComputedState()` is synchronous and does no I/O.
- Earlier per-view hooks (`prepareForPaint`, `updateMuted`) were removed in favor of the application-level call.
