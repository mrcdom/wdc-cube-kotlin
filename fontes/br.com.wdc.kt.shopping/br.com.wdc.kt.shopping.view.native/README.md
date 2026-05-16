# view-native

Views implementadas com recursos **nativos de cada plataforma**, sem Compose Multiplatform.

Cada subprojeto usa a tecnologia de UI nativa da sua plataforma:

| Subprojeto | Plataforma | Tecnologia de UI |
|---|---|---|
| [native.web/](native.web/) | Web (Kotlin/JS) | DOM API |
| [native.android/](native.android/) | Android | Android Views (XML + ViewBinding) |
| [native.ios/](native.ios/) | iOS | UIKit via Kotlin/Native interop |

Os presenters, domínio e persistência são compartilhados — apenas a camada de view é nativa.

Veja a [arquitetura Cube](../../../docs/architecture-cube.md) para o mecanismo de navegação e [visão geral da arquitetura](../../../docs/architecture.md) para o contexto do projeto.
