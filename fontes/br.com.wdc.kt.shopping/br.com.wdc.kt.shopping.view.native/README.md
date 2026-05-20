# view-native

Views implementadas com recursos **nativos de cada plataforma**, sem frameworks de abstração como Compose Multiplatform.

## Motivação

A principal motivação para o desenvolvimento de interfaces nativas é demonstrar que, mesmo utilizando apenas os recursos nativos de cada plataforma, é possível construir interfaces de qualidade equivalente às versões com frameworks declarativos.

O custo é construir com os recursos disponíveis na plataforma de destino — que pode não oferecer uma solução reativa nativa, ou pode carecer de alguns utilitários que facilitam a montagem de views. Contudo, as versões nativas **sempre terão menor desgaste de bateria** e **estarão alinhadas com as evoluções da plataforma**, sem depender de atualizações de terceiros.

## Subprojetos

| Subprojeto | Plataforma | Tecnologia de UI |
|---|---|---|
| [native.web/](native.web/) | Web (Kotlin/JS) | DOM API + React/MUI |
| [native.android/](native.android/) | Android | Android Views (programático) |
| [native.ios/](native.ios/) | iOS | UIKit via Kotlin/Native |

## Arquitetura Comum

Os três subprojetos compartilham a mesma camada de **presenters**, **domínio** e **persistência** — apenas a camada de view é específica por plataforma. A arquitetura segue o padrão **Cube MVP**:

```mermaid
graph TD
    subgraph common["Camada Comum (KMP)"]
        domain["Domínio"]
        persistence["Persistência"]
        presenters["Presenters"]
    end

    presenters -->|CubeView interface| web["native.web<br/>React + MUI"]
    presenters -->|CubeView interface| android["native.android<br/>Android Views"]
    presenters -->|CubeView interface| ios["native.ios<br/>UIKit"]
```

### Padrões Transversais

- **View Factory**: cada presenter registra uma factory para sua view nativa no bootstrap da aplicação
- **ViewSlot**: container que gerencia a troca de views filhas por navegação
- **ListSlot**: sincronização eficiente de listas com reciclagem de views (grow/shrink/update-in-place)
- **FlowLayout**: grid responsivo que redistribui filhos conforme a largura disponível
- **Dirty-flag coalescing**: múltiplas chamadas `update()` são consolidadas em um único `doUpdate()` por frame
- **Layout responsivo**: breakpoints detectam largura disponível e alternam entre layouts compacto (mobile) e wide (tablet/desktop)

## Telas (versão Native Web como referência)

| Login | Home (Produtos + Compras) |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Home](screenshots/02-home.png) |

| Detalhes do Produto | Carrinho |
|:---:|:---:|
| ![Detalhes do Produto](screenshots/03-detalhes-produto.png) | ![Carrinho](screenshots/04-carrinho.png) |

| Recibo |
|:---:|
| ![Recibo](screenshots/05-recibo.png) |

> As três plataformas (Web, Android, iOS) produzem telas visualmente equivalentes, com pequenas nuances em função dos recursos nativos disponíveis.

## Referências

- [Arquitetura Cube](../../../docs/architecture-cube.md) — mecanismo de navegação e ciclo de vida
- [Visão geral da arquitetura](../../../docs/architecture.md) — contexto do projeto
