# shopping-persistence

Implementação JVM dos repositórios usando **JDBI 3** com banco **H2**.

A arquitetura é baseada no padrão **Query Object (Critérios)** — cada operação de busca, contagem ou deleção recebe um objeto de critério com filtros opcionais, projeção de campos e paginação. Internamente, cada operação é encapsulada em um **Command** que traduz o critério em SQL usando CTEs e serialização JSON para carga eager de grafos de entidades em uma única query.

Veja a [documentação de arquitetura de persistência](../../../docs/architecture-persistence.md) para detalhes completos dos padrões e exemplos.

Acesso direto via JDBC — usado pelos presenters na [arquitetura React (view remota)](../../../docs/architecture-react.md), onde presenters executam no servidor.
