# persistence.rest

Endpoints REST dos repositórios, sobre o [`framework-rest`](../../../br.com.wdc.kt.framework/br.com.wdc.kt.framework.rest/README.md) (Javalin).

| Caminho | O que é |
|---|---|
| `/api/repo/<entidade>/…` | As operações de repositório (`insert`, `update`, `delete`, `count`, `fetch`, `fetch-page`, `fetch-by-id`) |
| `/api/tx/…` | Transação remota dirigida pelo cliente (`begin`, `commit`, `rollback`, `status`) |
| `/api/auth/…` | Login por desafio e renovação de sessão — só com a segurança ligada |
| `/openapi.json` | A descrição OpenAPI da API, derivada do domínio |

Cada controlador configura o `RepositoryRpc` da sua entidade, que lê e escreve com o codec do domínio — o mesmo que o cliente usa. É no controlador que **o acesso é conferido** (`ApiSecurity`): a permissão de quem chama e, para quem não alcança os dados de todos, a restrição ao que é dele. Toda escrita é atômica; os erros são traduzidos em status (400, 401, 403, 409, 429).

`RepositoryApiRoutes.create()` monta a `RestApi`; `RepositoryApiRoutes.configure(config)` a instala num Javalin. É usado pelo backend e pelos testes. A segurança é ligada se houver `AuthenticationService` registrado no momento da montagem.

Veja a [arquitetura de persistência](../../../../docs/architecture-persistence.md#api-rest).
