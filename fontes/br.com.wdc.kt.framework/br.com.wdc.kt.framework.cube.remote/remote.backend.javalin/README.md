# framework-cube-remote-backend-javalin

The remote Cube backend ([`framework-cube-remote-backend`](../remote.backend/)) on [Javalin](https://javalin.io): the two routes a remote client needs. Another web server gets the same by writing these two pieces over its own API.

Gradle: `implementation("io.github.mrcdom.wdc.kt:framework-cube-remote-backend-javalin:<version>")` (from 0.5.0). It brings the host and Javalin 7.

```kotlin
Javalin.create { config ->
    DispatcherController.configure(config, ShopRemoteApplication.REGISTRY) // ws://…/dispatcher/{id}
    IndexHtmlController.configure(config, "kotlin-client")                 // the session cookies on /index.html and /kotlin-client/index.html
}
```

| Class | Responsibility |
|-------|----------------|
| `DispatcherController` | `ws://…/dispatcher/{id}`: checks the session id's HMAC on connect, hands each message to the session of the registry (made on its first message), and releases a session that does not outlive its socket when it closes. |
| `IndexHtmlController` | Before `/index.html`, and `/<context>/index.html` of the contexts it is given (clients served under one): plants `app_id` (a new session id signed by the host) and keeps the page out of caches. |
| `JavalinWebSocketConnection` (internal) | The session's `WebSocketConnection` over Javalin's socket. |
