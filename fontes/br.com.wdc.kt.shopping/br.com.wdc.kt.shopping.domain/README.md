# shopping-domain

O domínio da aplicação Shopping: entidades, critérios, contratos de repositório e os codecs com que tudo isso trafega.

**Plataformas:** JVM, Android, iOS, JS, wasmJs

Cada entidade ocupa um pacote — `product`, `user`, `purchase`, `purchaseitem` — com quatro classes:

| Classe | Papel |
|---|---|
| `Xxx` | A entidade. Também serve de projeção: campo preenchido = "traga este campo" |
| `XxxCriteria` | Os filtros e a ordenação de uma consulta |
| `XxxRepository` | O contrato, implementado no servidor (jOOQ) e no cliente (HTTP) |
| `XxxCodec` | Leitura e escrita em JSON — o mesmo no cliente e no servidor |

Também ficam aqui `ShoppingTransactions` (o serviço de transação da aplicação), a segurança (`SecurityContext`, `Role`, `AuthenticationService`) e a configuração.

Veja a [arquitetura de persistência](../../../docs/architecture-persistence.md).
