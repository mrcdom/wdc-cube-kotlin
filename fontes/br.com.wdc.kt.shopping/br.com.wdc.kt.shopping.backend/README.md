# backend

Servidor principal da aplicação, baseado em **Javalin** (porta 8080).

Combina duas responsabilidades:
- **REST API** (`/api/repo/*`) — para clientes Compose Multiplatform — [arquitetura](../../../docs/architecture.md)
- **WebSocket** (`/dispatcher/{id}`) — para o cliente React — [arquitetura](../../../docs/architecture-react.md)

Também serve os arquivos estáticos do cliente React e inicializa o banco H2.

```bash
cd fontes && ./gradlew :backend:run
```
