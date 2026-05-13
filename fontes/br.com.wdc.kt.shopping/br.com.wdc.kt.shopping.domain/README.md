# shopping-domain

Entidades de domínio e interfaces de repositório da aplicação Shopping.

**Plataformas:** JVM, Android, iOS, wasmJs

Define `User`, `Product`, `Purchase`, `PurchaseItem` e suas respectivas interfaces de repositório (`UserRepository`, `ProductRepository`, etc.). Inclui também as **classes de critério** (`ProductCriteria`, `UserCriteria`, etc.) usadas para compor queries dinâmicas com filtros opcionais, projeção e paginação.

Veja a [documentação de arquitetura de persistência](../../../docs/architecture-persistence.md) para detalhes dos padrões de critério e projeção.
