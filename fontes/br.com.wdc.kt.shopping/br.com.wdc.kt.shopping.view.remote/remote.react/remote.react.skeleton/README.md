# react.skeleton

Server-side bridge between the presenters and the React client. Each `*ReactViewImpl` turns WebSocket events into presenter calls and serializes the ViewState to JSON.

Main components:
- `ApplicationReactImpl` — manages the session, dirty tracking and server push
- `DispatcherHandler` — Javalin WebSocket handler
- `GenericViewImpl` — base for all view impls
- `*ReactViewImpl` — one implementation per presenter

See the [architecture documentation](../../../../docs/architecture-react.md) for the full communication cycle.
