# shopping-tests

Testes de integração da aplicação Shopping: repositórios, API REST, transações, segurança, esquema e presenters.

```bash
cd fontes
./gradlew test                              # H2 em memória
./gradlew :shopping-tests:testPostgres      # a mesma suíte em PostgreSQL embutido
./gradlew check                             # as duas
```

- Os testes de repositório (`repository/Abstract*RepositoryTest`) rodam em dois modos: direto no repositório e pelo caminho REST completo.
- `transaction/` cobre o checkout atômico e a transação remota; `repository/SecuredRest*` cobre o controle de acesso da API (permissão e alcance por usuário); `schema/` cobre as migrações e as classes jOOQ; `doc/` confere a OpenAPI contra o domínio.
- `testPostgres` usa um PostgreSQL embutido, que sobe sozinho. Com `SHOPPING_TEST_PG_URL` (+ `_USER`, `_PASSWORD`) usa esse servidor **e apaga os dados dele**.
- `live/LiveBackendSmokeTest` só roda com `SHOPPING_LIVE_BACKEND_URL` definida: exercita um backend no ar e grava uma compra nele.

Detalhes na [arquitetura de persistência](../../../docs/architecture-persistence.md#testes).
