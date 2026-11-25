# 0015. The method an action runs carries the action's name

- **Date:** 2026-11-25
- **Status:** Accepted

## Context

A presenter binds each action of its scope in its constructor ([0004](0004-actions-are-guarded-scope-fields.md)), and the body of the binding calls a method of the presenter. Those methods were named freely: `scope.onBuy` called `buy()`, `products.onOpenProduct` called `openProduct()`, `purchases.onPageChange` called `changePage()`, and some actions had no method at all, only a navigation written inline. Reading a presenter, nothing told which methods are entry points from the view and which are internals that several places call (`changePage` was both).

## Decision

**The method an action runs has the action's name**: `scope.onBuy = action(1) { onBuy() }`, `row.onModifyQuantity = actionOf(2, …) { onModifyQuantity(productId, it) }`. Every bound action calls its own method, even when the method is a single navigation (`onOpenProducts() { HomeKeys(app).flip() }`). A method with the `on` prefix in a presenter is an entry point from the view, and nothing else calls it; what other code also needs stays in an internal method that the `on…` method calls (`onPageChange(page)` calls `changePage(page)`, which the cart's listener calls too).

Listeners of the presenter's own sources follow the same reading (`onCartChanged`, `onCartCommited`): they are entry points too, from something other than the view.

## Consequences

- The constructor is a table of the screen's entry points, each with its method one search away.
- Views, tests and the remote protocol do not change: they call the scope's actions, not the methods.
- The Java version names them without the prefix; the two differ here on purpose.
- The starter project follows it from the beginning.
