# react.skeleton

The WDC Store as a remote application. The remote host itself is the framework's: sessions, views, protocol, history, security and the WebSocket route are in [`framework-cube-remote`](../../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/README.md). This module is the Store's configuration of it, plus the client bundle.

- `ReactRootPresenter` — the Store's root presenter as a `RemoteApplication`: it holds the session's `RemoteApplicationSupport` and supplies what is the Store's:
  - the vid of each of its scopes (`VIDS`, read from each scope's `VID`);
  - its registry (`REGISTRY`, which the backend gives to `DispatcherController` and starts the cleanup and the flush of);
  - that a signed-in user's session outlives its connection (`keepsSessionWithoutConnection`);
  - the text of an unexpected error, shown in the browser's alert;
  - `b64Decipher`, with the session's key, for what the client ciphered (the login's password, which `LoginPresenter` deciphers on sync).
- `controller/` — the Store's own routes: product images (`ImageController`), `/health` (`StatusController`) and the stress tool's metrics (`StressMetricsController`).
- `src/main/resources/META-INF/resources` — where `npm run build` of the [React client](../remote.react.client/) writes the bundle (not versioned).

`UnexpectedErrorAlertTest` covers the Store's alert text. The host's own tests are in the framework module.

See the [architecture documentation](../../../../../docs/architecture-react.md) for the full communication cycle.
