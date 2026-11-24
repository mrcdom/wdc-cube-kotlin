# WDC Store

Modules of the WDC Store application, organized in layers: domain, persistence, presentation and views.

## Modules

### Domain and Data

- [br.com.wdc.kt.store.domain/](br.com.wdc.kt.store.domain/) — Entities and repository interfaces (KMP)
- [br.com.wdc.kt.store.persistence/](br.com.wdc.kt.store.persistence/) — Persistence layer, in three subprojects:
  - [persistence.impl/](br.com.wdc.kt.store.persistence/persistence.impl/) — Repositories on jOOQ, for H2 and PostgreSQL (JVM)
  - [persistence.rest/](br.com.wdc.kt.store.persistence/persistence.rest/) — REST API of the repositories (JVM)
  - [persistence.client/](br.com.wdc.kt.store.persistence/persistence.client/) — REST client of the repositories (KMP)
- [br.com.wdc.kt.store.scripts/](br.com.wdc.kt.store.scripts/) — Database creation and migration scripts (JVM)

### Presentation

- [br.com.wdc.kt.store.presentation/](br.com.wdc.kt.store.presentation/) — Shared presenters (KMP)

### Views

- [br.com.wdc.kt.store.view.compose/](br.com.wdc.kt.store.view.compose/) — Compose Multiplatform view (local) — [architecture](../../docs/architecture.md)
- [br.com.wdc.kt.store.view.remote/](br.com.wdc.kt.store.view.remote/) — Remote (server-side) views — React, Vaadin, ZK
- [br.com.wdc.kt.store.view.native/](br.com.wdc.kt.store.view.native/) — Native views per platform (Android Views, UIKit, React/MUI)

### Server and Tests

- [br.com.wdc.kt.store.backend/](br.com.wdc.kt.store.backend/) — Javalin server (HTTP + WebSocket)
- [br.com.wdc.kt.store.tests/](br.com.wdc.kt.store.tests/) — Integration tests
