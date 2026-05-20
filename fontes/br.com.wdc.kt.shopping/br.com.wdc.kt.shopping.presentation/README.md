# shopping-presentation

Presenters (Cubes) da aplicação Shopping — lógica de apresentação independente de plataforma.

**Plataformas:** JVM, Android, iOS, wasmJs

Os mesmos presenters são reutilizados nas três arquiteturas:
- [Compose Multiplatform (view local)](../../../docs/architecture.md) — presenters no cliente
- [Native (view local)](../../../docs/architecture.md) — presenters no cliente com UI nativa por plataforma
- [React (view remota)](../../../docs/architecture-react.md) — presenters no servidor

Contém: `LoginPresenter`, `HomePresenter`, `ProductPresenter`, `CartPresenter`, `ReceiptPresenter`, entre outros.
