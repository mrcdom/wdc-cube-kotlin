# shopping-presentation

Presenters (Cubes) of the Storefront application — platform-independent presentation logic.

**Platforms:** JVM, Android, iOS, wasmJs

The same presenters are reused in the three architectures:
- [Compose Multiplatform (view local)](../../../docs/architecture.md) — presenters on the client
- [Native (view local)](../../../docs/architecture.md) — presenters on the client with a native UI per platform
- [React (view remota)](../../../docs/architecture-react.md) — presenters on the server

Contains: `RootPresenter` (the application), `LoginPresenter`, `HomePresenter`, `ProductPresenter`, `CartPresenter`, `ReceiptPresenter`, each with its scope (`XxxScope`) and keys class (`XxxKeys`). A presenter shows its scope and binds the scope's actions in its constructor (`scope.onBuy = action(1) { buy() }`); it never creates a view. The Home's products and purchases panels and the cart rows (`CartItemScope`) are segments: scopes of their own, without presenters.
