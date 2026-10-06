# framework-persistence

Transações sobre JDBC (JVM).

- `TransactionServiceImpl` — as seis propagações do `TransactionService`, numa conexão do `DataSource`. Convive com corrotinas: a transação acompanha o bloco entre suspensões.
- `TransactionScope` — a transação corrente da thread; é por ele que o acesso a dados acha a conexão.
- `RemoteTransactionCoordinatorImpl` — guarda transações abertas por clientes remotos, com dono, tetos, expiração por ociosidade e desfecho idempotente.

Ainda não publicado no Maven Central.

Veja a [arquitetura de persistência](../../../docs/architecture-persistence.md).
