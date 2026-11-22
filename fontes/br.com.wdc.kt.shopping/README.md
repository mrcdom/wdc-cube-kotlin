# Storefront

Modules of the Storefront application, organized in layers: domain, persistence, presentation and views.

## Modules

### Domain and Data

- [br.com.wdc.kt.shopping.domain/](br.com.wdc.kt.shopping.domain/) — Entities and repository interfaces (KMP)
- [br.com.wdc.kt.shopping.persistence/](br.com.wdc.kt.shopping.persistence/) — Persistence layer, in three subprojects:
  - [persistence.impl/](br.com.wdc.kt.shopping.persistence/persistence.impl/) — Repositories on jOOQ, for H2 and PostgreSQL (JVM)
  - [persistence.rest/](br.com.wdc.kt.shopping.persistence/persistence.rest/) — REST API of the repositories (JVM)
  - [persistence.client/](br.com.wdc.kt.shopping.persistence/persistence.client/) — REST client of the repositories (KMP)
- [br.com.wdc.kt.shopping.scripts/](br.com.wdc.kt.shopping.scripts/) — Database creation and migration scripts (JVM)

### Presentation

- [br.com.wdc.kt.shopping.presentation/](br.com.wdc.kt.shopping.presentation/) — Shared presenters (KMP)

### Views

- [br.com.wdc.kt.shopping.view.compose/](br.com.wdc.kt.shopping.view.compose/) — Compose Multiplatform view (local) — [architecture](../../docs/architecture.md)
- [br.com.wdc.kt.shopping.view.remote/](br.com.wdc.kt.shopping.view.remote/) — Remote (server-side) views — React, Vaadin, ZK
- [br.com.wdc.kt.shopping.view.native/](br.com.wdc.kt.shopping.view.native/) — Native views per platform (Android Views, UIKit, React/MUI)

### Server and Tests

- [br.com.wdc.kt.shopping.backend/](br.com.wdc.kt.shopping.backend/) — Javalin server (HTTP + WebSocket)
- [br.com.wdc.kt.shopping.tests/](br.com.wdc.kt.shopping.tests/) — Integration tests
