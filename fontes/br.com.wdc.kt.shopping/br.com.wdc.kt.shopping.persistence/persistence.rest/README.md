# persistence.rest

Endpoints REST dos repositórios, em **Javalin**.

| Caminho | O que é |
|---|---|
| `/api/repo/<entidade>/…` | As operações de repositório (`insert`, `update`, `delete`, `count`, `fetch`, `fetch-page`, `fetch-by-id`) |
| `/api/tx/…` | Transação remota dirigida pelo cliente (`begin`, `commit`, `rollback`, `status`) |
| `/api/auth/…` | Login por desafio e renovação de sessão — só com a segurança ligada |
| `/openapi.json` | A descrição OpenAPI da API, derivada do domínio |

Os controladores leem e escrevem com os codecs do domínio — os mesmos que o cliente usa — e **conferem o acesso** (`ApiSecurity`): a permissão de quem chama e, para quem não alcança os dados de todos, a restrição ao que é dele. Toda escrita é atômica; os erros são traduzidos em status (400, 401, 403, 409, 429).

`RepositoryApiRoutes.configure(config)` registra tudo. É usado pelo backend e pelos testes.

Veja a [arquitetura de persistência](../../../../docs/architecture-persistence.md#api-rest).
