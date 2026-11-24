# react.skeleton

Server-side bridge between the presenters and the React client. Presenters do not create views: `ReactRootPresenter` fills the application's view catalog (`ScopeViews`) with one `ScopeViewImpl` per scope, carrying the vid of the client component that paints it. A `ScopeViewImpl` sends the client's events to the actions the presenter bound with their codes (`Scope.dispatch`), what the user typed to `Scope.sync`, and writes the scope as JSON (`writeScope`, with the ids of the views of the scopes inside it).

Main components:
- `ReactRootPresenter` — manages the session, dirty tracking and server push
- `DispatcherHandler` — Javalin WebSocket handler
- `GenericViewImpl` — base for all view impls
- `ScopeViewImpl` — the remote view of any scope (dispatch, sync, write)
- `BrowserScope` + `BrowserPresenter` — the browser tab as a scope of the remote layer (the alert, the root view sent as `contentViewId`, start, history, keep-alive); `BrowserPresenter` binds its actions with their event codes like any presenter, and its view is a plain `ScopeViewImpl` (instance 0)
- `LoginReactViewImpl` (deciphers the password) and `ReceiptReactViewImpl` (one-time success notice) — the only scopes with a class of their own

See the [architecture documentation](../../../../docs/architecture-react.md) for the full communication cycle.
