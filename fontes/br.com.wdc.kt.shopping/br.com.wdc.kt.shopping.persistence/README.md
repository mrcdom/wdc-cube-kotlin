# shopping-persistence

Implementação JVM dos repositórios, sobre **jOOQ**, para **H2** e **PostgreSQL**.

- `repository/` — um `XxxRepositoryImpl` por entidade. Cada um declara o mapeamento entidade ↔ tabela (`JsonQueryBuilder`) e traduz o critério em condições; a consulta devolve o grafo pedido pela projeção numa única ida ao banco.
- `scheme/` — classes jOOQ **geradas** a partir do DDL de `DBCreate`. Não edite à mão: `./gradlew :shopping-scripts:generateJooqSchema`.
- `security/` — autenticação (JWT, sessões, segredos de intent) e os `SecuredXxxRepository`, que aplicam permissão e alcance por usuário.
- `ShoppingRepositoryBootstrap` — liga o módulo ao `DataSource`: registra o `DSLContext`, o serviço de transação e os repositórios; `initializeSecurity` decora-os quando há segredo JWT.

Os repositórios não demarcam transação: usam a conexão da transação corrente ou, fora dela, uma avulsa em autocommit.

Veja a [arquitetura de persistência](../../../docs/architecture-persistence.md).
