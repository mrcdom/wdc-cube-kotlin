# framework-cube-remote-backend

The remote backend of Cube MVP: the presenters run on the server, and a client paints the views and talks to them over the remote protocol. The host keeps each view's scope and sends what changed; the client paints it and sends back what the user typed and the events. JVM only, and free of any web server or client technology: the routes on Javalin are [`framework-cube-remote-backend-javalin`](../remote.backend.javalin/), and the clients are the [Kotlin/JS](../remote.bridge.kotlin/) and [TypeScript](../remote.bridge.typescript/) bridges.

It is the Kotlin counterpart of the Java version's `remote.backend`. The WDC Store's remote view is built on it: its `RemoteRootPresenter` is a complete example.

Gradle: `implementation("io.github.mrcdom.wdc.kt:framework-cube-remote-backend:<version>")` (from 0.5.0). It brings `framework-cube` and Gson.

## How an application uses it

```kotlin
class ShopRemoteApplication(id: String) : ShopRootPresenter(), RemoteApplication {

    companion object {
        /** The client component that paints each scope: its id in the protocol (each scope declares its own). */
        val VIDS: Map<KClass<out Scope>, String> = mapOf(
            RootScope::class to RootScope.VID,
            OrderScope::class to OrderScope.VID,
        )

        val REGISTRY = RemoteApplicationRegistry(::ShopRemoteApplication)
    }

    override val remote = RemoteApplicationSupport(id, this, VIDS, content = scope)

    // a signed-in user's session waits for the client to reconnect
    override fun keepsSessionWithoutConnection() = user != null

    override fun updateHistory() = remote.updateHistory()

    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        logger.error(message, e)
        remote.browser.alertUnexpectedError(message) // the text only, never the stack trace
    }

    override fun release() {
        remote.release()
        super.release()
    }
}

// in the server (the routes: framework-cube-remote-backend-javalin, or your own over another web server)
ShopRemoteApplication.REGISTRY.startFlushing()           // pushes changes made outside a client message
ShopRemoteApplication.REGISTRY.startCleanup(executor)    // releases the sessions whose client went away
```

## Main classes

| Class | Responsibility |
|-------|----------------|
| `RemoteApplication` | What the application implements: it holds its `remote` support, says whether its session outlives a closed socket, shows unexpected errors and releases itself. |
| `RemoteApplicationSupport` | The work of one session. **Views:** one `RemoteViewImpl` per scope drawn, made the first time a scope that holds it is written, by the vid of the scope's class. **Protocol:** each client message runs the secret, the sync (`Scope.sync`) and the events (`Scope.dispatch`), and is answered with the scopes of the dirty views, the released views and the signed address. **History:** `safeGo` does not follow an address whose signature is not the host's. **Life:** expiry and `extendLife`. It also holds the browser tab and the session's cipher. |
| `RemoteApplicationRegistry` | The live sessions, by id: makes one with the factory on the client's first message, releases the expired ones (`startCleanup`, `removeExpireds`), and flushes every 50 ms what changed outside a message (`startFlushing`). |
| `RemoteViewImpl` | The remote view of any scope, known by `"<vid>:<n>"`. Writing it is drawing it: it releases the views it no longer refers to (in cascade, once no view holds them), and their ids go to the client in `releasedViews`. |
| `RemoteViewHost` | What a view needs from its session (`viewOf`, `markDirty`, …); `RemoteApplicationSupport` is the real one. |
| `BrowserScope`, `RemoteBrowserPresenter` | The client's browser tab, its top view (`7b32e816a191:0`): the alert, the application's root scope (`contentViewId`), start (`-1`), history (`-2`), the alert's OK (`1`), keep-alive (`2`). |
| `RemoteAppSecurity` | The host's signatures: HMAC-SHA256 of the session ids (`newSessionId`, `isSessionId`) and of the navigation addresses (`sign`, `verify`). The key is random per process; several instances serving the same clients share one with `useKey`. Only the server signs and checks: there is no public key. |
| `RemoteDataSecurity` | A session's cipher for sensitive values (a typed password), on top of TLS: each connection agrees on an AES-256-GCM key by ECDH P-256 (`handshake(clientKey)` returns the `serverKey`) and HKDF-SHA256 (salt = the session id, info `wdc-cube-remote/aes-256-gcm/v1`); every value carries a random IV of its own, `Base64(IV ‖ ciphertext and tag)`. No long-lived key: forward secrecy. |
| `WebSocketConnection` | Where a session sends its messages: the web server's socket, given by the routes (`remote.wsSession`). |

## The protocol, in short

- **Client → server**: `{requestId, clientKey?, ping?, event: ["<vsid>:<code>", …], "<vsid>": {form data}}`. The form data of a view is synced into its scope before the events run.
- **Server → client**: `{requestId, ping?, serverKey?, uri, scopes: [{id, …fields, "<name>ViewId", "<name>ViewIds"}], releasedViews?: [vsid, …]}`. A scope inside a scope goes as the id of its view; a view created while writing goes in the same message.

The WDC Store's [remote view documentation](../../../../docs/architecture-react.md) describes the whole cycle, with diagrams.

## Tests

`RemoteViewReleaseTest` (views released in cascade), `ExpiredApplicationsTest` (registry and life), `RemoteProtocolTest` (a message and its answer, the alert, a forged address) and `RemoteDataSecurityTest` (the ECDH handshake with a JCA client, a new key per handshake, a new IV per value, a changed byte or another curve refused, the HMAC signatures).
