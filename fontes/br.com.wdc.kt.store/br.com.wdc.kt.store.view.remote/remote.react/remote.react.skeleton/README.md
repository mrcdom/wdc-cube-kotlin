# react.skeleton

Server-side bridge between the presenters and the React client. Presenters do not create views, and the framework knows only scopes: this module is the rendering technology of the remote client. Each session (`ReactRootPresenter`) keeps its own catalog — one `ScopeViewImpl` per scope, carrying the vid of the client component that paints it, which each scope declares (`LoginScope.VID`) — and the live views. A `ScopeViewImpl` sends the client's events to the actions the presenter bound with their codes (`Scope.dispatch`), what the user typed to `Scope.sync`, and writes the scope as JSON (`writeScope`, with the ids of the views of the scopes inside it, made by `viewOf` the first time). Writing a scope is drawing it: a view no longer referred to by any view is released (in cascade), and its id goes to the client in the next message's `releasedViews`.

Main components:
- `ReactRootPresenter` — manages the session, the view catalog and live views (`viewOf`, `forgetView`, `removeView`, `liveViews`), dirty tracking, `releasedViews` and server push
- `DispatcherHandler` — Javalin WebSocket handler
- `RemoteApplication` — what a view needs from its session (`nextInstanceId`, `viewOf`, `forgetView`, `removeView`, `markDirty`)
- `ScopeViewImpl` — the remote view of any scope (dispatch, sync, write; children and holders, `release`). `RemoteViewReleaseTest` covers the releases
- `BrowserScope` + `BrowserPresenter` — the browser tab as a scope of the remote layer (the alert, the root view sent as `contentViewId`, start, history, keep-alive); `BrowserPresenter` binds its actions with their event codes like any presenter, and its view is a plain `ScopeViewImpl` (instance 0)
- `ReactRootPresenter.b64Decipher` — deciphers, for the presentation, what the client ciphered with the session's key (the login's password, which `LoginPresenter` deciphers on sync)

See the [architecture documentation](../../../../../docs/architecture-react.md) for the full communication cycle.
