# shopping-presentation

Presenters (Cubes) of the Shopping application — platform-independent presentation logic.

**Platforms:** JVM, Android, iOS, wasmJs

The same presenters are reused in the three architectures:
- [Compose Multiplatform (view local)](../../../docs/architecture.md) — presenters on the client
- [Native (view local)](../../../docs/architecture.md) — presenters on the client with a native UI per platform
- [React (view remota)](../../../docs/architecture-react.md) — presenters on the server

Contains: `LoginPresenter`, `HomePresenter`, `ProductPresenter`, `CartPresenter`, `ReceiptPresenter`, among others.
