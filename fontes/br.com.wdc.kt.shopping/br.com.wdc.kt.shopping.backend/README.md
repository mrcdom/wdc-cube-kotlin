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

## Diretório de trabalho

O backend **exige** um diretório de trabalho: é dele que vêm a configuração e o log, e é nele que ficam o banco, os temporários e os frontends publicados. Informe-o com o argumento `--workdir=<pasta>` ou com a variável de ambiente `SHOPPING_WORKDIR`; sem isso o serviço não sobe.

- `./gradlew :backend:run` usa [`fontes/work`](../../work/) (outro: `-Pworkdir=/caminho`).
- Pela IDE: acrescente `--workdir=$PROJECT_DIR$/work` aos *Program arguments* da configuração de execução.
- Pela distribuição (`installDist`): `bin/backend --workdir=/caminho/work`.

A estrutura da pasta está descrita em [`fontes/work/README.md`](../../work/README.md).

## Configuração

O backend lê `config/application.toml` no diretório de trabalho. Sem o arquivo, sobe com os padrões: H2 em `data/` e **sem segurança**. [`work/config/application.example.toml`](../../work/config/application.example.toml) lista todas as chaves, com os padrões:

- **Banco** — H2 (padrão) ou PostgreSQL, escolhido pela `url`; pool de conexões; transações remotas.
- **Segurança** — com `security.jwt.secret`, a API exige autenticação. Sem ele, fica aberta: só para desenvolvimento.
- **Servidor** — porta e CORS.

O log é configurado por `config/logback.xml` e gravado em `log/`, com rotação.
