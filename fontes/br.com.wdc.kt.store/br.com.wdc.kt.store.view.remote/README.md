# view-remote

The **remote view**: the presenters run on the server, and a client paints the scopes they show and sends back what the user does, over a WebSocket. The server side does not depend on how the client is written.

## Subprojects

- [remote.skeleton/](remote.skeleton/) — the WDC Store as a remote application (`:view-remote-skeleton`): its root presenter over the framework's remote host (`framework-cube-remote-host`), the vid of each scope, the Store's own routes, and the TypeScript client's bundle — [architecture](../../../docs/architecture-react.md)
- [remote.stress/](remote.stress/) — load test (`:view-remote-stress`): virtual clients that speak the protocol, with no browser
- [remote.views/](remote.views/) — the client implementations: React in TypeScript and React in Kotlin
