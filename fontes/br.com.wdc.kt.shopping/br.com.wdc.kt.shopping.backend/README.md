# backend

Servidor principal da aplicação, baseado em **Javalin** (porta 8080).

Combina três responsabilidades:
- **REST API** (`/api/repo/*`) — para clientes Compose Multiplatform e Native — [arquitetura](../../../docs/architecture.md)
- **WebSocket** (`/dispatcher/{id}`) — para o cliente React (view remota) — [arquitetura](../../../docs/architecture-react.md)
- **Arquivos estáticos** — serve os clientes React e Native Web

Também inicializa o banco H2 e executa migrações na inicialização.

```bash
cd fontes && ./gradlew :backend:run
```
