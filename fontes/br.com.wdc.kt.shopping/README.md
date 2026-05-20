# Shopping

Módulos da aplicação Shopping, organizada em camadas: domínio, persistência, apresentação e views.

## Módulos

### Domínio e Dados

- [br.com.wdc.kt.shopping.domain/](br.com.wdc.kt.shopping.domain/) — Entidades e interfaces de repositório (KMP)
- [br.com.wdc.kt.shopping.persistence/](br.com.wdc.kt.shopping.persistence/) — Implementação JDBC/H2 dos repositórios (JVM)
- [br.com.wdc.kt.shopping.persistence.rest/](br.com.wdc.kt.shopping.persistence.rest/) — Endpoints REST dos repositórios (JVM)
- [br.com.wdc.kt.shopping.persistence.client/](br.com.wdc.kt.shopping.persistence.client/) — Cliente REST para os repositórios (KMP)
- [br.com.wdc.kt.shopping.scripts/](br.com.wdc.kt.shopping.scripts/) — Scripts de criação/migração do banco (JVM)

### Apresentação

- [br.com.wdc.kt.shopping.presentation/](br.com.wdc.kt.shopping.presentation/) — Presenters compartilhados (KMP)

### Views

- [br.com.wdc.kt.shopping.view.compose/](br.com.wdc.kt.shopping.view.compose/) — View Compose Multiplatform (local) — [arquitetura](../../docs/architecture.md)
- [br.com.wdc.kt.shopping.view.remote/](br.com.wdc.kt.shopping.view.remote/) — Views remotas (server-side) — React, Vaadin, ZK
- [br.com.wdc.kt.shopping.view.native/](br.com.wdc.kt.shopping.view.native/) — Views nativas por plataforma (Android Views, UIKit, React/MUI)

### Servidor e Testes

- [br.com.wdc.kt.shopping.backend/](br.com.wdc.kt.shopping.backend/) — Servidor Javalin (HTTP + WebSocket)
- [br.com.wdc.kt.shopping.tests/](br.com.wdc.kt.shopping.tests/) — Testes de integração
