# remote.react

Implementação da view remota usando **React** — os presenters executam no servidor e se comunicam com o cliente via WebSocket.

Veja a [documentação de arquitetura](../../../../docs/architecture-react.md) para detalhes completos.

## Subprojetos

- [remote.react.skeleton/](remote.react.skeleton/) — Bridge server-side (Kotlin/JVM) entre presenters e cliente React
- [remote.react.client/](remote.react.client/) — Aplicação React/TypeScript (renderização pura)

## Telas

| Login | Home (Produtos + Compras) |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Home](screenshots/02-home.png) |

| Detalhes do Produto | Carrinho |
|:---:|:---:|
| ![Detalhes do Produto](screenshots/03-detalhes-produto.png) | ![Carrinho](screenshots/04-carrinho.png) |

| Recibo |
|:---:|
| ![Recibo](screenshots/05-recibo.png) |
