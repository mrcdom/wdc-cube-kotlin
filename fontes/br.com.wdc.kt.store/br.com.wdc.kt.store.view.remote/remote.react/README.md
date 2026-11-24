# remote.react

Remote view implementation using **React** — the presenters run on the server and talk to the client over WebSocket.

See the [architecture documentation](../../../../docs/architecture-react.md) for the full details.

## Subprojects

- [remote.react.skeleton/](remote.react.skeleton/) — Server-side bridge (Kotlin/JVM) between the presenters and the React client
- [remote.react.client/](remote.react.client/) — React/TypeScript application (pure rendering)

## Screens

| Login | Home (Products + Purchases) |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Home](screenshots/02-home.png) |

| Product Details | Cart |
|:---:|:---:|
| ![Product Details](screenshots/03-detalhes-produto.png) | ![Cart](screenshots/04-carrinho.png) |

| Receipt |
|:---:|
| ![Receipt](screenshots/05-recibo.png) |
