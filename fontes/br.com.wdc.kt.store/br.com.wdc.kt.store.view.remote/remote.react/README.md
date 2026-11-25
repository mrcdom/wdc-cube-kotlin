# remote.react

Remote view implementation using **React** — the presenters run on the server and talk to the client over WebSocket.

See the [architecture documentation](../../../../docs/architecture-react.md) for the full details.

## Subprojects

- [remote.react.skeleton/](remote.react.skeleton/) — Server-side bridge (Kotlin/JVM) between the presenters and the React client
- [remote.react.client/](remote.react.client/) — React/TypeScript application (pure rendering): the Store's views, theme and texts

The client's protocol machinery — WebSocket, security handshake, scope bindings, view collection — is the framework package [`@wdc/remote-bridge-react`](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote.react/), the browser side of the Kotlin module `framework-cube-remote`. The client imports it from source through an alias.

## Screens

| Login | Home (Products + Purchases) |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Home](screenshots/02-home.png) |

| Product Details | Cart |
|:---:|:---:|
| ![Product Details](screenshots/03-product-details.png) | ![Cart](screenshots/04-cart.png) |

| Receipt |
|:---:|
| ![Receipt](screenshots/05-receipt.png) |
