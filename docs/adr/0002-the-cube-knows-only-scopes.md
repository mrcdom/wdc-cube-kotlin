# 0002. The Cube knows only scopes; each rendering technology owns its views

- **Date:** 2026-11-24
- **Status:** Accepted

## Context

The same presenters drive six renderings: Compose (desktop, Android, iOS, web), native web (React), native Android, native iOS, and a remote view whose components live in a browser while the presenters run on the server. Earlier versions had a `CubeView` type in the framework, and presenters created, held and released views. Every technology had to fit that lifecycle, even the ones that already manage component lifetimes themselves (Compose, React), and presenters carried code that had nothing to do with presentation logic.

## Decision

The framework knows only **scopes**. A `Scope` is the state of one piece of a screen plus its actions, with fields that ask for a repaint when they change (`var title by field("")`). A presenter makes scopes and puts them in fields of the scope it is bound to; it never creates, holds or releases a view.

Each rendering technology owns its views: it keeps a catalog from scope class to view, asks for the view of a scope while it draws (`viewOf(scope)`), installs and removes the scope's updater, and lets a view go when no drawing asks for it anymore. Compose and React do this with their own lifecycles; the native iOS and Android layers and the remote host keep a count of holders and release in cascade.

A smaller part of a screen (a panel, a row of a list) is a **segment**: a scope the presenter creates and puts in a field. It needs no presenter of its own.

## Consequences

- A presenter is plain logic over scopes, testable without any view; the mocks only record repaints.
- Adding a scope means registering its view in each technology's catalog; a missing one fails at once, naming the scope.
- Every drawing must ask for all the child views it holds, not only the ones that changed, or the children not asked for are released.
- A view must not change the scope while it draws it: with the updater installed, that schedules another drawing.
- The Java version took the same decision (its commit "the cube knows only scopes; each rendering technology releases its views").

## Alternatives considered

- **Keep `CubeView` and release views from the presenter.** A first version did this (`Scope.release`, a view registry per layer). It worked, but put view lifetimes in the presentation and duplicated what Compose and React already do. Replaced at the maintainer's request.

See [architecture-cube.md](../architecture-cube.md) for scopes, slots and the view lifecycle.
