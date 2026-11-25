# framework-cube-remote

The remote host of Cube MVP, on Javalin: the presenters run on the server, and the views run in a browser, which talks to them over a WebSocket. The server keeps each view's scope and sends what changed; the client paints it and sends back what the user typed and the events. JVM only. Its client for React shells is [`@wdc/remote-bridge-react`](../br.com.wdc.kt.framework.cube.remote.react/).

It is the Kotlin counterpart of the Java version's `remote.backend`. The WDC Store's remote React view is built on it: its `ReactRootPresenter` is a complete example.

Gradle: `implementation("io.github.mrcdom.wdc.kt:framework-cube-remote:<version>")` (from 0.5.0). It brings `framework-cube`, Javalin 7 and Gson.

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

// in the server
Javalin.create { config ->
    DispatcherController.configure(config, ShopRemoteApplication.REGISTRY) // ws://…/dispatcher/{id}
    IndexHtmlController.configure(config)                                  // the session cookies on /index.html
}
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
| `RemoteAppSecurity` | The host's keys: textbook RSA for the handshake (the client ciphers its session password with it) and `SHA256withRSA` to sign session ids and addresses. Read from the system properties `wdc.web.public_key`/`wdc.web.private_key` and `wdc.sign.wdc.web.public_key`/`wdc.sign.wdc.web.private_key`; without them, fixed development keys — **set your own in production** (`RemoteAppSecurity.main` prints a new set). |
| `RemoteDataSecurity` | A session's cipher for sensitive values (a typed password): AES-256-GCM, key derived by PBKDF2 (250,000 iterations) from the password of the handshake (`<password ciphered with RSA>.<salt>`); every value carries a random IV of its own in front of it, `Base64(IV ‖ ciphertext and tag)`. |
| `javalin.DispatcherController` | `ws://…/dispatcher/{id}`: checks the session id's signature and the `app_signature` cookie on connect, hands each message to the session, and releases a session that does not outlive its socket when it closes. |
| `javalin.IndexHtmlController` | Before `/index.html`: plants `app_id` (a new signed session id) and `app_skey` (the host's public key), and keeps the page out of caches. |

## The protocol, in short

- **Client → server**: `{requestId, secret?, ping?, event: ["<vsid>:<code>", …], "<vsid>": {form data}}`. The form data of a view is synced into its scope before the events run.
- **Server → client**: `{requestId, ping?, uri, scopes: [{id, …fields, "<name>ViewId", "<name>ViewIds"}], releasedViews?: [vsid, …]}`. A scope inside a scope goes as the id of its view; a view created while writing goes in the same message.

The WDC Store's [remote React documentation](../../../../docs/architecture-react.md) describes the whole cycle, with diagrams.

## Tests

`RemoteViewReleaseTest` (views released in cascade), `ExpiredApplicationsTest` (registry and life), `RemoteProtocolTest` (a message and its answer, the alert, a forged address) and `RemoteDataSecurityTest` (the client's handshake, a new IV per value, a changed byte refused).
