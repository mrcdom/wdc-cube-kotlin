# backend

Servidor da aplicação, em **Javalin** (porta 8080).

Reúne três responsabilidades:
- **API REST** (`/api/repo`, `/api/tx`, `/api/auth`, `/openapi.json`) — para os clientes Compose e nativos — [arquitetura](../../../docs/architecture-persistence.md#api-rest)
- **WebSocket** (`/dispatcher/{id}`) — para o cliente React (view remota) — [arquitetura](../../../docs/architecture-react.md)
- **Arquivos estáticos** — serve os clientes web

Na subida, monta o pool de conexões, cria as tabelas que faltam e roda as migrações.

```bash
cd fontes && ./gradlew :backend:run
```

## Configuração

O backend lê `work/config/application.toml` (diretório não versionado) ou o arquivo indicado por `-Dshopping.config.file=…`. Sem arquivo, sobe com os padrões: H2 em `work/data` e **sem segurança**.

[`application.example.toml`](application.example.toml) lista todas as chaves, com os padrões:

- **Banco** — H2 (padrão) ou PostgreSQL, escolhido pela `url`; pool de conexões; transações remotas.
- **Segurança** — com `security.jwt.secret`, a API exige autenticação. Sem ele, fica aberta: só para desenvolvimento.
- **Servidor** — porta e CORS.
