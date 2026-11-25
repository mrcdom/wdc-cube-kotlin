# Remote Cube

The remote view of Cube MVP: the presenters run on a server, and a client — in a browser, or anywhere that speaks the protocol — paints what they show and sends back what the user does. The protocol and its two ends do not depend on any UI or web technology; the bindings to one are projects of their own.

| Project | Gradle / npm | Platform | What it is |
|---|---|---|---|
| [remote.backend/](remote.backend/) | `framework-cube-remote-backend` | JVM | The host: sessions, one view per scope, dispatch and answer, signed history, the session cipher |
| [remote.backend.javalin/](remote.backend.javalin/) | `framework-cube-remote-backend-javalin` | JVM | The host's routes on Javalin: the WebSocket and the session cookies of the client page |
| [remote.bridge.kotlin/](remote.bridge.kotlin/) | `framework-cube-remote-bridge` | Kotlin/JS | The client of the protocol, with no UI technology |
| [remote.bridge.kotlin.react/](remote.bridge.kotlin.react/) | `framework-cube-remote-bridge-react` | Kotlin/JS | React bindings for it (kotlin-wrappers) |
| [remote.bridge.typescript/](remote.bridge.typescript/) | `@wdc/remote-bridge` | TypeScript | The client of the protocol, with no UI technology |
| [remote.bridge.typescript.react/](remote.bridge.typescript.react/) | `@wdc/remote-bridge-react` | TypeScript | React bindings for it |

The Kotlin modules are published on Maven Central from 0.5.0; the TypeScript packages are not published, and a shell imports them from source through an alias. The WDC Store's remote view uses all of them: its [host application](../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.host/) on the host, and a [TypeScript](../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript/) and a [Kotlin](../../br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.kotlin/) client. The [remote view documentation](../../../docs/architecture-react.md) describes the whole cycle.

**The protocol** is versioned (version 1; the client sends it in the handshake, and a host that does not speak it answers `unsupported-protocol`) and specified message by message in [conformance/](conformance/README.md), with **shared fixtures** that any host or client — in any language — runs to show it speaks it. The Kotlin host, the Kotlin client and the TypeScript client run them in their tests.
