# remote.host

The WDC Store as a remote application. The remote backend itself is the framework's: sessions, views, protocol, history and security are in [`framework-cube-remote-backend`](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.backend/), and the WebSocket route the backend installs is in [`framework-cube-remote-backend-javalin`](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.backend.javalin/). This module is the Store's configuration of it, plus the client bundle.

- `RemoteRootPresenter` — the Store's root presenter as a `RemoteApplication`: it holds the session's `RemoteApplicationSupport` and supplies what is the Store's:
  - the vid of each of its scopes (`VIDS`, read from each scope's `VID`);
  - its registry (`REGISTRY`, which the backend gives to `DispatcherController` and starts the cleanup and the flush of);
  - that a signed-in user's session outlives its connection (`keepsSessionWithoutConnection`);
  - an unexpected error, as a catalog message (`:store-i18n`) sent to the browser's alert with the code `RemoteRootPresenter.ALERT_MESSAGE` (-3): `args` are the key, then each argument's name and value; both clients translate it (the cause and the stack trace go only to the server log);
  - `b64Decipher`, with the session's key, for what the client ciphered (the login's password, which `LoginPresenter` deciphers on sync).
- `controller/` — the Store's own route for product images (`ImageController`). The backend serves the health checks and the metrics.
- `src/main/resources/META-INF/resources` — where `npm run build` of the [TypeScript client](../remote.views/remote.react.typescript/) writes the bundle (not versioned).

`UnexpectedErrorAlertTest` covers the Store's alert text. The host's own tests are in the framework module.

See the [architecture documentation](../../../../docs/architecture-react.md) for the full communication cycle.
