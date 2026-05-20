# react.skeleton

Bridge server-side entre os presenters e o cliente React. Cada `*ReactViewImpl` traduz eventos WebSocket em chamadas ao presenter e serializa o ViewState para JSON.

Componentes principais:
- `ApplicationReactImpl` — gerencia sessão, dirty tracking e server push
- `DispatcherHandler` — handler do WebSocket Javalin
- `GenericViewImpl` — base para todas as view impls
- `*ReactViewImpl` — uma implementação por presenter

Veja a [documentação de arquitetura](../../../../docs/architecture-react.md) para o ciclo completo de comunicação.
